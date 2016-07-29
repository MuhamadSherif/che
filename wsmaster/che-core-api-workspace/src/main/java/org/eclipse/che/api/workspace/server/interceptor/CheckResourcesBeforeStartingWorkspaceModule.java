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

import com.google.inject.AbstractModule;
import com.google.inject.matcher.Matchers;

import org.eclipse.che.api.workspace.server.WorkspaceManager;

import static org.eclipse.che.inject.Matchers.names;

/**
 * If installed intercepts docker machine creation and make everything needed to create
 * machine if base image of Dockerfile that should be pulled already cached
 * and network is down.
 *
 * @author Alexander Garagatyi
 */
public class CheckResourcesBeforeStartingWorkspaceModule extends AbstractModule {
    @Override
    protected void configure() {
        CheckResourcesBeforeStartingWorkspaceInterceptor interceptor =
                new CheckResourcesBeforeStartingWorkspaceInterceptor();
        requestInjection(interceptor);
        bindInterceptor(Matchers.subclassesOf(WorkspaceManager.class), names("startWorkspace"), interceptor);
    }
}
