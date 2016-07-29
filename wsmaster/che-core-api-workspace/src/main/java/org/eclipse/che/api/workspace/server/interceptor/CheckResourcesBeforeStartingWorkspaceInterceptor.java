/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.api.workspace.server.interceptor;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.eclipse.che.api.core.ErrorCodes;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.rest.shared.dto.ExtendedError;
import org.everrest.core.impl.provider.json.JsonUtils;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.eclipse.che.plugin.docker.client.DockerConnector;
import org.everrest.websockets.WSConnectionContext;
import org.everrest.websockets.message.ChannelBroadcastMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.eclipse.che.dto.server.DtoFactory.newDto;

/**
 * @author Igor Vinokur
 *
 * Interceptor that chacks if there is more than 90% of RAM before starting workspace, otherwise exception will be throwed and
 * a message about ram limit reached will be sent to client by websocket. When RAM has been cleared to more than 90%
 * after ram limit reached status occured, a message about ram freed will be sent to client by websocket.
 */
public class CheckResourcesBeforeStartingWorkspaceInterceptor implements MethodInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(CheckResourcesBeforeStartingWorkspaceInterceptor.class);

    @Inject
    DockerConnector dockerConnector;

    @VisibleForTesting
    final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setNameFormat(this.getClass().getSimpleName()).setDaemon(true).build());

    @Override
    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
        if (ramLimitReached()) {
            sendMessage("ram_limit_reached");

            executor.scheduleAtFixedRate(() -> {
                try {
                    if (!ramLimitReached()) {
                        sendMessage("ram_freed");
                        executor.shutdown();
                    }
                } catch (IOException e) {
                    LOG.error("A problem occurred while getting system information from docker", e);
                }
            }, 0, 30, TimeUnit.SECONDS);

            throw new ServerException(newDto(ExtendedError.class).withMessage("Low Resources").withErrorCode(ErrorCodes.LOW_RESOURCES));
        }
        return methodInvocation.proceed();
    }

    private void sendMessage(String line) {
        final ChannelBroadcastMessage bm = new ChannelBroadcastMessage();
        bm.setChannel("resources_chanel");
        bm.setBody(JsonUtils.getJsonString(line));
        try {
            WSConnectionContext.sendMessage(bm);
        } catch (Exception e) {
            LOG.error("A problem occurred while sending websocket message", e);
        }
    }

    private boolean ramLimitReached() throws IOException {
        String[] ramUsage = dockerConnector.getSystemInfo().ramUsage().split("/ ");
        if (ramUsage.length < 2) {
            LOG.error("A problem occurred while parsing system information from docker");
            return false;
        }
        long ramUsed = getBytesAmountFromString(ramUsage[0]);
        long ramTotal = getBytesAmountFromString(ramUsage[1]);
        return ((ramUsed * 100) / ramTotal > 90);
    }

    private long getBytesAmountFromString(String string) {
        if (string.contains("KiB")) {
            return getValue(string) * 1024;
        }else if (string.contains("MiB")){
            return getValue(string) * 1024 * 1024 ;
        } else if (string.contains("GiB")) {
            return getValue(string) * 1024 * 1024 * 1024;
        } else {
            return getValue(string);
        }
    }

    private long getValue(String string) {
        return Math.round(Float.parseFloat(string.substring(0, string.indexOf(" "))));
    }
}
