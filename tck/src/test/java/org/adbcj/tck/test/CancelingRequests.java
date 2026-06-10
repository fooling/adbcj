package org.adbcj.tck.test;

import org.adbcj.Connection;
import org.adbcj.ResultSet;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.concurrent.*;


public class CancelingRequests extends AbstractWithConnectionManagerTest{


    @Test
    public void canCancelSelect() throws Exception {
        CountDownLatch newerCalled = new CountDownLatch(1);
        final Connection connection = connectionManager.connect().get();

        final CompletableFuture<ResultSet> result = connection.executeQuery("SELECT SLEEP(2)");
        Thread.sleep(500);
        boolean cannotBeCanceled = result.cancel(true);
        result.handle( (r,ex)->{
            Assert.assertTrue(ex instanceof CancellationException);
            newerCalled.countDown();
            return r;
        });

        Assert.assertTrue(cannotBeCanceled);

        Assert.assertTrue(newerCalled.await(2, TimeUnit.SECONDS));

        connection.close();

    }
    @Test
    public void mayCanChancelNotYetRunningStatement() throws Exception {
        final Connection connection = connectionManager.connect().get();


        final Future<ResultSet> runningStatment = connection.executeQuery("SELECT SLEEP(1)");
        final Future<ResultSet> toCancel = connection.executeQuery("SELECT SLEEP(2)");
        boolean canCancel = toCancel.cancel(true);

        if(canCancel){
            try{
                toCancel.get();
                Assert.fail("Should throw CancellationException");
            } catch (CancellationException expected){
                //expected
            }
        }

        connection.close();

    }
}
