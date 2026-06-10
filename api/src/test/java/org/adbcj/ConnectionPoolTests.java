package org.adbcj;

import org.adbcj.support.ConnectionPool;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ConnectionPoolTests {

    @Test
    public void startsEmpty() {
        ConnectionPool<String, String> toTest = new ConnectionPool<>();
        String noValue = toTest.tryAquire("test");

        Assert.assertNull(noValue);
    }

    @Test
    public void addAndReturnAConnetion() {
        ConnectionPool<String, String> toTest = new ConnectionPool<>();

        toTest.release("test", "test-conn");

        String conn = toTest.tryAquire("test");
        Assert.assertEquals("test-conn", conn);
    }


    @Test
    public void addAndReturnMultipleConnections() {
        ConnectionPool<String, String> toTest = new ConnectionPool<>();

        toTest.release("test", "test-conn-1");
        toTest.release("test", "test-conn-2");

        String conn1 = toTest.tryAquire("test");
        String conn2 = toTest.tryAquire("test");
        String noMoreConn = toTest.tryAquire("test");
        Assert.assertNull(noMoreConn);
        Assert.assertTrue(conn1.startsWith("test-conn"));
        Assert.assertTrue(conn2.startsWith("test-conn"));
        Assert.assertNotEquals(conn1, conn2);
    }

    @Test
    public void differentKeysHaveDifferentPools() {
        ConnectionPool<String, String> toTest = new ConnectionPool<>();

        toTest.release("test", "test-conn-1");
        toTest.release("other", "other-conn-2");

        String testConn = toTest.tryAquire("test");
        String otherConn = toTest.tryAquire("other");
        String noMoreConn = toTest.tryAquire("test");
        Assert.assertNull(noMoreConn);
        Assert.assertEquals("test-conn-1", testConn);
        Assert.assertEquals("other-conn-2", otherConn);
    }


}
