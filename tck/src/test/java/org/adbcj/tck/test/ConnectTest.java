/*
 *   Copyright (c) 2007 Mike Heath.  All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package org.adbcj.tck.test;

import org.adbcj.*;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.testng.Assert.assertTrue;

@Test(invocationCount = 5, threadPoolSize = 5, timeOut = 30000)
public class ConnectTest extends AbstractWithConnectionManagerTest {


    public void testConnectImmediateClose() throws Exception {
        final CountDownLatch latch = new CountDownLatch(2);

        Future<Connection> connectFuture = connectionManager.connect()
                .thenApply(res -> {
                    latch.countDown();
                    return res;
                });
        Connection connection = connectFuture.get(10, TimeUnit.SECONDS);
        assertTrue(!connection.isClosed());
        Future<Void> closeFuture = connection.close()
                .thenApply(res -> {
                    latch.countDown();
                    return res;
                });
        closeFuture.get(10, TimeUnit.SECONDS);
        assertTrue(connection.isClosed());
        latch.await(1, TimeUnit.SECONDS);
    }

    public void testConnectNonImmediateClose() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        Connection connection = connectionManager.connect().get();
        assertTrue(!connection.isClosed());
        connection.close()
                .thenApply(res -> {
                    latch.countDown();
                    return res;
                }).get();
        assertTrue(connection.isClosed());
        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }

    public void testConnectAndDisconnect() throws Exception {
        final Future<Connection> future = connectionManager.connect();
        Connection connection = future.get();
        final Future<Void> closeFuture = connection.close();
        closeFuture.get();
    }

    public void testNonImmediateClose() throws Exception {
        Connection connection = connectionManager.connect().get();

        List<Future<ResultSet>> futures = new ArrayList<Future<ResultSet>>();

        for (int i = 0; i < 5; i++) {
            futures.add(connection.executeQuery(String.format("SELECT *, %d FROM simple_values", i)));
        }
        try {
            connection.close().get(100, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            for (Future<ResultSet> future : futures) {
                if (future.isDone()) {
                    future.get(); // Will throw exception if failed
                } else {
                    throw new AssertionError("future " + future + " did not complete in time");
                }
            }
            throw new AssertionError("finalizeClose future failed to complete");
        }
        assertTrue(connection.isClosed(), "Connection should be closed");
    }

}
