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
package org.eclipse.che.api.jdbc.jpa;

import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.persist.jpa.JpaPersistModule;

import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.jdbc.jpa.eclipselink.EntityListenerInjectionManagerInitializer;
import org.eclipse.che.api.core.jdbc.jpa.guice.JpaInitializer;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.factory.server.jpa.FactoryJpaModule;
import org.eclipse.che.api.factory.server.model.impl.AuthorImpl;
import org.eclipse.che.api.factory.server.model.impl.FactoryImpl;
import org.eclipse.che.api.factory.server.spi.FactoryDao;
import org.eclipse.che.api.machine.server.jpa.MachineJpaModule;
import org.eclipse.che.api.machine.server.model.impl.SnapshotImpl;
import org.eclipse.che.api.machine.server.spi.SnapshotDao;
import org.eclipse.che.api.ssh.server.jpa.SshJpaModule;
import org.eclipse.che.api.ssh.server.model.impl.SshPairImpl;
import org.eclipse.che.api.ssh.server.spi.SshDao;
import org.eclipse.che.api.user.server.jpa.UserJpaModule;
import org.eclipse.che.api.user.server.model.impl.ProfileImpl;
import org.eclipse.che.api.user.server.model.impl.UserImpl;
import org.eclipse.che.api.user.server.spi.PreferenceDao;
import org.eclipse.che.api.user.server.spi.ProfileDao;
import org.eclipse.che.api.user.server.spi.UserDao;
import org.eclipse.che.api.workspace.server.jpa.WorkspaceJpaModule;
import org.eclipse.che.api.workspace.server.model.impl.WorkspaceConfigImpl;
import org.eclipse.che.api.workspace.server.model.impl.WorkspaceImpl;
import org.eclipse.che.api.workspace.server.spi.WorkspaceDao;
import org.eclipse.che.commons.lang.Pair;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests top-level entities cascade removals.
 *
 * @author Yevhenii Voevodin
 */
public class JpaEntitiesCascadeRemovalTest {

    private PreferenceDao preferenceDao;
    private UserDao       userDao;
    private ProfileDao    profileDao;
    private WorkspaceDao  workspaceDao;
    private SnapshotDao   snapshotDao;
    private SshDao        sshDao;
    private FactoryDao    factoryDao;

    @BeforeMethod
    public void setUp() {
        final Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(EventService.class).in(Singleton.class);

                bind(JpaInitializer.class).asEagerSingleton();
                bind(EntityListenerInjectionManagerInitializer.class).asEagerSingleton();

                install(new JpaPersistModule("test"));
                install(new UserJpaModule());
                install(new WorkspaceJpaModule());
                install(new MachineJpaModule());
                install(new SshJpaModule());
                install(new FactoryJpaModule());
            }
        });

        preferenceDao = injector.getInstance(PreferenceDao.class);
        userDao = injector.getInstance(UserDao.class);
        profileDao = injector.getInstance(ProfileDao.class);
        snapshotDao = injector.getInstance(SnapshotDao.class);
        workspaceDao = injector.getInstance(WorkspaceDao.class);
        sshDao = injector.getInstance(SshDao.class);
        factoryDao = injector.getInstance(FactoryDao.class);
    }

    @Test
    public void shouldDeleteAllTheEntitiesWhenUserIsDeleted() throws Exception {
        // User is a root of dependency tree
        final UserImpl user = createUser("bobby");
        userDao.create(user);

        // Create profile, profile depends on user
        final ProfileImpl profile = createProfile(user.getId());
        profileDao.create(profile);

        // Create preferences, preferences depend on user
        final Map<String, String> preferences = createPreferences();
        preferenceDao.setPreferences(user.getId(), preferences);

        // Create 2 workspaces, workspaces depend on user
        final WorkspaceImpl workspace1 = createWorkspace("workspace1", user.getId());
        final WorkspaceImpl workspace2 = createWorkspace("workspace2", user.getId());
        workspaceDao.create(workspace1);
        workspaceDao.create(workspace2);

        // Create 2 ssh keys, ssh keys depend on user
        final SshPairImpl pair1 = createSshPair(user.getId(), "service", "name1");
        final SshPairImpl pair2 = createSshPair(user.getId(), "service", "name2");
        sshDao.create(pair1);
        sshDao.create(pair2);

        // Create 2 factories, factory depend on user
        final FactoryImpl factory1 = createFactory("factory1", user.getId());
        final FactoryImpl factory2 = createFactory("factory2", user.getId());
        factoryDao.create(factory1);
        factoryDao.create(factory2);

        // Create 4 Snapshots, each snapshot depend on workspace
        final SnapshotImpl snapshot1 = createSnapshot("snapshot1", workspace1.getId());
        final SnapshotImpl snapshot2 = createSnapshot("snapshot2", workspace1.getId());
        final SnapshotImpl snapshot3 = createSnapshot("snapshot3", workspace2.getId());
        final SnapshotImpl snapshot4 = createSnapshot("snapshot4", workspace2.getId());
        snapshotDao.saveSnapshot(snapshot1);
        snapshotDao.saveSnapshot(snapshot2);
        snapshotDao.saveSnapshot(snapshot3);
        snapshotDao.saveSnapshot(snapshot4);


        // Remove the user, all entries must be removed along with the user
        userDao.remove(user.getId());


        // Check all the entities are removed
        assertNull(notFoundToNull(() -> userDao.getById(user.getId())));
        assertNull(notFoundToNull(() -> profileDao.getById(user.getId())));
        assertTrue(preferenceDao.getPreferences(user.getId()).isEmpty());
        assertTrue(workspaceDao.getByNamespace(user.getId()).isEmpty());
        assertTrue(sshDao.get(user.getId()).isEmpty());
        assertTrue(factoryDao.getByAttribute(0, 0, singletonList(Pair.of("creator.userId", user.getId()))).isEmpty());
        assertTrue(snapshotDao.findSnapshots(workspace1.getId()).isEmpty());
        assertTrue(snapshotDao.findSnapshots(workspace2.getId()).isEmpty());
    }

    public static UserImpl createUser(String id) {
        return new UserImpl(id,
                            id + "@eclipse.org",
                            id + "_name",
                            "password",
                            asList(id + "_alias1", id + "_alias2"));
    }

    public static ProfileImpl createProfile(String userId) {
        return new ProfileImpl(userId, new HashMap<>(ImmutableMap.of("attribute1", "value1",
                                                                     "attribute2", "value2",
                                                                     "attribute3", "value3")));
    }

    public static Map<String, String> createPreferences() {
        return new HashMap<>(ImmutableMap.of("preference1", "value1",
                                             "preference2", "value2",
                                             "preference3", "value3"));
    }

    public static WorkspaceImpl createWorkspace(String id, String namespace) {
        return new WorkspaceImpl(id,
                                 namespace,
                                 new WorkspaceConfigImpl(id + "_name",
                                                         id + "description",
                                                         "default-env",
                                                         null,
                                                         null,
                                                         null));
    }

    public static SshPairImpl createSshPair(String owner, String service, String name) {
        return new SshPairImpl(owner, service, name, "public-key", "private-key");
    }

    public static FactoryImpl createFactory(String id, String creator) {
        return new FactoryImpl(id,
                               id + "-name",
                               "4.0",
                               createWorkspace(id, creator).getConfig(),
                               new AuthorImpl(creator, System.currentTimeMillis()),
                               null,
                               null,
                               null,
                               null);
    }

    public static SnapshotImpl createSnapshot(String snapshotId, String workspaceId) {
        return new SnapshotImpl(snapshotId,
                                "type",
                                null,
                                System.currentTimeMillis(),
                                workspaceId,
                                snapshotId + "_description",
                                true,
                                "dev-machine",
                                snapshotId + "env-name");
    }

    public static <T> T notFoundToNull(Callable<T> action) throws Exception {
        try {
            return action.call();
        } catch (NotFoundException x) {
            return null;
        }
    }
}
