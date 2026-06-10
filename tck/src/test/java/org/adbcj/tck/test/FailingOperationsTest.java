package org.adbcj.tck.test;

import org.adbcj.Connection;
import org.adbcj.DbException;
import org.adbcj.StandardProperties;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.concurrent.ExecutionException;


public class FailingOperationsTest extends AbstractWithConnectionManagerTest {

    @Test
    public void failingQuery() throws Exception {
        Connection connection = connectionManager.connect().get();
        try {
            connection.executeQuery("SELECT invalid query so it will throw").get();
            Assert.fail("Expect failure");
        } catch (ExecutionException ex) {
            Assert.assertTrue(ex.getCause() instanceof DbException);
            boolean foundThisTestInStack = false;
            for (StackTraceElement element : ex.getStackTrace()) {
                if (element.getMethodName().equals("failingQuery")) {
                    foundThisTestInStack = true;
                }
            }
            Assert.assertTrue(foundThisTestInStack);

        }

    }

    @Override
    protected Map<String, String> properties() {
        final Map<String, String> config = super.properties();
        config.put(StandardProperties.CAPTURE_CALL_STACK, "true");
        return config;
    }
}
