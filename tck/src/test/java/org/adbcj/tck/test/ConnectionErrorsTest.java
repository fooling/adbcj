package org.adbcj.tck.test;

import org.adbcj.Connection;
import org.adbcj.ConnectionManager;
import org.adbcj.ConnectionManagerProvider;
import org.adbcj.DbException;
import org.testng.Assert;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import java.util.concurrent.ExecutionException;


public class ConnectionErrorsTest {
    @Parameters({"url"})
    @Test
    public void expectTimeoutWhenDatabaseNotAvailable(String url) throws Exception {
        assumeIsOnLocalhost(url);
        String unconnectableUrl = unconnectableURL(url);
        ConnectionManager connectionManager = ConnectionManagerProvider.createConnectionManager(unconnectableUrl, "root", "");
        try {
            Connection connection = connectionManager.connect().get();
            Assert.fail("should not be able to connect, but got" + connection);
        } catch (ExecutionException e) {
            // expected
            Assert.assertTrue(e.getCause() instanceof DbException);
        }
    }


    @Parameters({"url", "user", "password"})
    @Test
    public void expectErrorWithWrongSchema(String url, String user, String pwd) throws Exception {
        assumeIsOnLocalhost(url);
        String unconnectableUrl = wrongSchema(url);
        ConnectionManager connectionManager = ConnectionManagerProvider.createConnectionManager(unconnectableUrl, user, pwd);
        try {
            Connection connection = connectionManager.connect().get();
            Assert.fail("should not be able to connect, but got" + connection);
        } catch (ExecutionException e) {
            // expected
            Assert.assertTrue(e.getCause() instanceof DbException);
        }
    }

    private void assumeIsOnLocalhost(String url) {
        if (!url.contains("localhost")) {
            Assert.fail("This test assumes that the database is on localhost");
        }

    }

    private String unconnectableURL(String url) throws Exception {
        return url.replace("localhost", "not.reachable.localhost");
    }

    private String wrongSchema(String url) throws Exception {
        if (url.contains("h2")) {
            return url.replace("adbcjtck", "invalidschema;IFEXISTS=TRUE");
        }
        return url.replace("adbcjtck", "invalidschema");
    }

}

