package org.adbcj;

import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;


public class PassesStandardPropertiesTest {

    @Test
    public void standardPropertiesArePassed() {
        final ConnectionManager connectionManager
                = ConnectionManagerProvider.createConnectionManager("adbcj:apimock:url", "sa", "pwd");
        final CheckConstructionManager check = CheckConstructionMock.lastInstanceRequestedOnThisThread();

        check.assertURL("adbcj:apimock:url");
        check.assertUserName("sa");
        check.assertPassword("pwd");
        check.assertProperty(StandardProperties.MAX_QUEUE_LENGTH, "64");

    }
    @Test
    public void canOverrideProperty() {
        Map<String,String> userProperties = new HashMap<String,String>();
        userProperties.put(StandardProperties.MAX_QUEUE_LENGTH,"128");
        final ConnectionManager connectionManager
                = ConnectionManagerProvider.createConnectionManager("adbcj:apimock:url", "sa", "pwd",userProperties);
        final CheckConstructionManager check = CheckConstructionMock.lastInstanceRequestedOnThisThread();

        check.assertProperty(StandardProperties.MAX_QUEUE_LENGTH, "128");

    }
    @Test
    public void propertiesDoNotChange() {
        Map<String,String> userProperties = new HashMap<String,String>();
        userProperties.put(StandardProperties.MAX_QUEUE_LENGTH,"128");
        final ConnectionManager connectionManager
                = ConnectionManagerProvider.createConnectionManager("adbcj:apimock:url", "sa", "pwd",userProperties);
        final CheckConstructionManager check = CheckConstructionMock.lastInstanceRequestedOnThisThread();

        userProperties.put(StandardProperties.MAX_QUEUE_LENGTH,"256");
        check.assertProperty(StandardProperties.MAX_QUEUE_LENGTH, "128");

    }
}
