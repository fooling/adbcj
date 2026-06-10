package org.adbcj.tck.test;

import org.adbcj.*;
import org.testng.Assert;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class QueueLengthTests extends AbstractWithConnectionManagerTest {

    @Test
    public void reportQueueOverflowAsCallbackException() throws Exception {
        Connection connection = connectionManager.connect().get();


        CompletableFuture<DbException> ex = new CompletableFuture<>();
        for (int i = 0; i < StandardProperties.DEFAULT_QUEUE_LENGTH * 2; i++) {
            connection.executeQuery("SELECT 1", (result, failure) -> {
                if (failure != null) {
                    ex.complete(failure);
                }
            });
        }

        String msg = ex.get().getMessage();
        Assert.assertTrue(msg.contains(StandardProperties.MAX_QUEUE_LENGTH));
        Assert.assertTrue(msg.contains(String.valueOf(StandardProperties.MAX_QUEUE_LENGTH)));
    }

    @Parameters({"url", "user", "password",})
    @Test
    public void increaseQueueSize(String url,
                                  String user,
                                  String password) throws Exception {
        int limit = 512;
        Map<String, String> props = new HashMap<>();
        props.put(StandardProperties.MAX_QUEUE_LENGTH, String.valueOf(limit));
        ConnectionManager connectionManager = ConnectionManagerProvider.createConnectionManager(
                url,
                user,
                password,
                props
        );
        Connection connection = connectionManager.connect().get();

        try {
            CountDownLatch expectSuccess = new CountDownLatch(limit);
            AtomicReference<DbException> neverSet = new AtomicReference<>();
            for (int i = 0; i < limit; i++) {
                connection.executeQuery("SELECT 1", (result, failure) -> {
                    if (failure != null) {
                        neverSet.set(failure);
                    } else {
                        expectSuccess.countDown();
                    }
                });
            }

            expectSuccess.await(30, TimeUnit.SECONDS);
            Assert.assertNull(neverSet.get());

        } finally {
            connectionManager.close().get();

        }


    }
}
