package ebandal.libreoffice.comp.tests.base;

import java.util.List;

import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.sun.star.frame.XDesktop;
import com.sun.star.lang.XMultiComponentFactory;
import com.sun.star.uno.UnoRuntime;
import com.sun.star.uno.XComponentContext;

public class UnoSuite extends Suite {

    private static XComponentContext componentContext;

    public UnoSuite(Class<?> klass, RunnerBuilder builder) throws InitializationError {
        super(klass, builder);
    }

    public UnoSuite(RunnerBuilder builder, Class<?>[] classes) throws InitializationError {
        super(builder, classes);
    }

    public UnoSuite(Class<?> klass, Class<?>[] suiteClasses) throws InitializationError {
        super(klass, suiteClasses);
    }

    public UnoSuite(Class<?> klass, List<Runner> runners) throws InitializationError {
        super(klass, runners);
    }

    public UnoSuite(RunnerBuilder builder, Class<?> klass, Class<?>[] suiteClasses) throws InitializationError {
        super(builder, klass, suiteClasses);
    }

    @Override
    public void run(RunNotifier arg0) {
        try {
            startOffice();
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.run(arg0);
        
        stopOffice();
    }

    private void startOffice() throws Exception {
        componentContext = com.sun.star.comp.helper.Bootstrap.bootstrap();
    }
    
    private void stopOffice() {
        try {
            if (componentContext != null) {
                // Only the uno test suite which started the office can stop it
                XMultiComponentFactory xMngr = componentContext.getServiceManager();
                Object oDesktop = xMngr.createInstanceWithContext("com.sun.star.frame.Desktop", componentContext);
                XDesktop xDesktop = (XDesktop)UnoRuntime.queryInterface(XDesktop.class, oDesktop);
                
                xDesktop.terminate();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static XComponentContext getComponentContext() {
        return componentContext;
    }

}
