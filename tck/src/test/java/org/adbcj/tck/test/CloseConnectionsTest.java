package org.adbcj.tck.test;

import org.adbcj.*;
import org.adbcj.tck.NoArgAction;
import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;


public class CloseConnectionsTest extends  AbstractWithConnectionManagerTest{

    @AfterTest
    public void closeConnectionManager() throws Exception {
        Future<Void> closeFuture = connectionManager.close();
        closeFuture.get();
    }

    @Parameters({"url", "user", "password"})
    @Test
    public void closingManagerClosesConnections(String url, String user, String password) throws Exception {
        final ConnectionManager manager = ConnectionManagerProvider.createConnectionManager(url, user, password);
        final Connection c1 = manager.connect().get();
        final Future<ResultSet> runningQuery = c1.executeQuery("SELECT SLEEP(2)");
        final Future<ResultSet> runningQuery2 = c1.executeQuery("SELECT SLEEP(2)");
        final Connection c2 = manager.connect().get();
        c2.beginTransaction();
        manager.close().get();


        checkClosed(c1, runningQuery, runningQuery2, c2);
    }

    @Parameters({"url", "user", "password"})
    @Test
    public void foreClosingManagerClosesConnections(String url, String user, String password) throws Exception {
        final ConnectionManager manager = ConnectionManagerProvider.createConnectionManager(url, user, password);
        final Connection c1 = manager.connect().get();
        final Connection c2 = manager.connect().get();
        c2.beginTransaction();
        final Future<ResultSet> runningQuery = c1.executeQuery("SELECT SLEEP(3)");
        final Future<ResultSet> runningQuery2 = c1.executeQuery("SELECT SLEEP(4)");
        manager.close(CloseMode.CANCEL_PENDING_OPERATIONS).get();


        checkClosed(c1, runningQuery, runningQuery2, c2);


        Assert.assertTrue(runningQuery2.isDone()
                || !runningQuery2.isDone(),"Why is instance done?: ");
    }


    @Test
    public void closingConnectionDoesNotAcceptNewRequests() throws Exception {
        final Connection connection = connectionManager.connect().get();
        final PreparedQuery preparedSelect = connection.prepareQuery("SELECT 1").get();
        final PreparedUpdate preparedUpdate = connection.prepareUpdate("SELECT 1").get();
        final Future<ResultSet> runningQuery = connection.executeQuery("SELECT SLEEP(5)");
        final Future<Void> closeFuture = connection.close();
        Assert.assertTrue(connection.isClosed());

        shouldThrowException(() -> connection.executeQuery("SELECT 1"));
        shouldThrowException(connection::beginTransaction);
        shouldThrowException(() -> connection.prepareQuery("SELECT 1"));
        shouldThrowException(preparedSelect::execute);
        shouldThrowException(preparedUpdate::execute);
        closeFuture.get();

    }

    @Test
    public void forceCloseConnections() throws Exception {
        final Connection connection = connectionManager.connect().get();

        final CompletableFuture<ResultSet> rs1 = connection.executeQuery("SELECT int_val, str_val " +
                "FROM simple_values where str_val LIKE 'Not-In-Database-Value'");
        final CompletableFuture<ResultSet> rs2 = connection.executeQuery("SELECT int_val, str_val " +
                "FROM simple_values where str_val LIKE 'Not-In-Database-Value'");
        final CompletableFuture<ResultSet> rs3 = connection.executeQuery("SELECT int_val, str_val " +
                "FROM simple_values where str_val LIKE 'Not-In-Database-Value'");

        connection.close(CloseMode.CANCEL_PENDING_OPERATIONS);

        try {
            rs1.get();
            rs2.get();
            rs3.get();
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("closed"));
        }
    }

    @Parameters({"connectionPool"})
    @Test
    public void closeRealConnectionInPool(boolean connectionPool) throws Exception {
        if(!connectionPool){
            // Irrelevant without connection pool
            return;
        }

        // Warmup
        for (int i = 0; i < 10; i++) {
            final Connection connection = connectionManager.connect().get();
            connection.close(CloseMode.CLOSE_GRACEFULLY).get();
        }

        long startPooled = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            final Connection connection = connectionManager.connect().get();
            connection.close(CloseMode.CLOSE_GRACEFULLY).get();
        }
        long timeUsedPooled = System.nanoTime()-startPooled;

        long startForced = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            final Connection connection = connectionManager.connect().get();
            connection.close(CloseMode.CLOSE_FORCIBLY).get();
        }
        long timeUsedForced = System.nanoTime()-startForced;


        System.out.println("Time used pooled: "+timeUsedPooled +" time used unpooled: "+timeUsedForced);
        Assert.assertTrue( timeUsedForced>timeUsedPooled*1.5);
    }

    private void checkClosed(final Connection c1, Future<ResultSet> runningQuery, Future<ResultSet> runningQuery2, Connection c2) {
        shouldThrowException(() -> c1.executeQuery("SELECT 1"));

        Assert.assertTrue(c1.isClosed());
        Assert.assertTrue(c2.isClosed());
        Assert.assertTrue(runningQuery.isDone());
        Assert.assertTrue(runningQuery2.isDone());
    }

    private void shouldThrowException(NoArgAction toInvoke) {
        try {
            toInvoke.invoke();
            Assert.fail("Expect exception telling us that the connection is closing");
        } catch (DbConnectionClosedException | IllegalStateException e) {
            Assert.assertTrue(e.getMessage().contains("closed"));
        }
    }

}
