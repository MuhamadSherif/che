package org.eclipse.che.api.workspace.server.interceptor;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.name.Names;
import com.google.inject.spi.ConstructorBinding;

import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.machine.server.MachineManager;
import org.eclipse.che.api.user.server.UserManager;
import org.eclipse.che.api.workspace.server.WorkspaceManager;
import org.eclipse.che.api.workspace.server.WorkspaceRuntimes;
import org.eclipse.che.api.workspace.server.model.impl.WorkspaceImpl;
import org.eclipse.che.api.workspace.server.spi.WorkspaceDao;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.commons.subject.SubjectImpl;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import java.lang.reflect.Method;

import static com.google.inject.matcher.Matchers.subclassesOf;
import static org.eclipse.che.inject.Matchers.names;
import static org.mockito.Mockito.mock;

/**
 * @author Igor Vinokur
 */
@Listeners(MockitoTestNGListener.class)
public class CheckResourcesBeforeStartingWorkspaceInterceptorTest {
    private Injector         injector;
    private WorkspaceManager workspaceManager;

    private static final String USER_ID   = "user12";
    private static final String USER_NAME = "username";

    @Mock
    private WorkspaceImpl    workspaceImpl;

    @BeforeMethod
    private void setup() throws Exception {
        Module module = new AbstractModule() {
            public void configure() {
                //Bind manager and his dep-s. To bind interceptor, guice must create intercepted class by himself.
                bind(WorkspaceDao.class).toInstance(mock(WorkspaceDao.class));
                bind(WorkspaceRuntimes.class).toInstance(mock(WorkspaceRuntimes.class));
                bind(EventService.class).toInstance(mock(EventService.class));
                bind(MachineManager.class).toInstance(mock(MachineManager.class));
                bind(UserManager.class).toInstance(mock(UserManager.class));
                bindConstant().annotatedWith(Names.named("workspace.runtime.auto_restore")).to(false);
                bindConstant().annotatedWith(Names.named("workspace.runtime.auto_snapshot")).to(false);
                bindConstant().annotatedWith(Names.named("docker.api.version")).to("1.20");
                bind(WorkspaceManager.class);

                //Main injection
                install(new CheckResourcesBeforeStartingWorkspaceModule());

//                 To prevent real methods of manager calling
                bindInterceptor(subclassesOf(WorkspaceManager.class), names("startWorkspace"),
                                invocation -> workspaceImpl);
                EnvironmentContext.setCurrent(new EnvironmentContext() {
                    @Override
                    public Subject getSubject() {
                        return new SubjectImpl(USER_NAME, USER_ID, "token", false);
                    }
                });
            }
        };

        injector = Guice.createInjector(module);
        workspaceManager = injector.getInstance(WorkspaceManager.class);
    }

    @Test
    public void checkAllInterceptedMethodsArePresent() throws Throwable {
        ConstructorBinding<?> interceptedBinding = (ConstructorBinding<?>)injector.getBinding(WorkspaceManager.class);

        for (Method method : interceptedBinding.getMethodInterceptors().keySet()) {
            workspaceManager.getClass().getMethod(method.getName(), method.getParameterTypes());
        }
    }
}
