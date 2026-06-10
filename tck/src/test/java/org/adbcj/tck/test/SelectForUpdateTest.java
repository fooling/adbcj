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

import org.adbcj.Connection;
import org.adbcj.ResultSet;
import org.testng.annotations.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

@Test(timeOut = 5000)
public class SelectForUpdateTest extends AbstractWithConnectionManagerTest{

	public void testSelectForUpdate() throws Exception {
		final AtomicBoolean locked = new AtomicBoolean(false);
		final AtomicBoolean error = new AtomicBoolean(false);
		final CountDownLatch latch1 = new CountDownLatch(1);
		final CountDownLatch latch2 = new CountDownLatch(1);
		
		Connection conn1 = connectionManager.connect().get();
		Connection conn2 = connectionManager.connect().get();
		
		// Get lock on locks table
		conn1.beginTransaction();
		selectForUpdate(conn1).thenApply(res->{
			locked.set(true);
			latch1.countDown();
			return res;
		}).get();
		
		// Try to get lock with second connection
		conn2.beginTransaction();
		CompletableFuture<ResultSet> future = selectForUpdate(conn2).thenApply(res->{
			if (!locked.get()) {
				error.set(true);
			}
			latch2.countDown();
			return res;
		});
		
		assertTrue(latch1.await(1, TimeUnit.SECONDS));
		assertTrue(locked.get(), "locked should be set");
		assertFalse(error.get());
		
		conn1.rollback().get();
		
		future.get();
		
		assertTrue(latch2.await(1, TimeUnit.SECONDS));
		assertFalse(error.get(), "An error occurred during SELECT FOR UPDATE");
		conn2.rollback().get();
		
		// Close connections
		conn1.close().get();
		conn2.close().get();
	}



	public static final String DEFAULT_LOCK_NAME = "lock";

	static CompletableFuture<ResultSet> selectForUpdate(Connection connection) throws InterruptedException {
		return selectForUpdate(connection, DEFAULT_LOCK_NAME);
	}


	static CompletableFuture<ResultSet> selectForUpdate(Connection connection, String lock) throws InterruptedException {
		if (!connection.isInTransaction()) {
			throw new IllegalStateException("You must be in a transaction for a select for update to work");
		}
		CompletableFuture<ResultSet> future = connection.executeQuery("SELECT name FROM locks WHERE name='lock' FOR UPDATE");
		return future;
	}
}
