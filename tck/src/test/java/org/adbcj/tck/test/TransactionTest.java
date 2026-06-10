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
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

@Test(timeOut = 5000)
public class TransactionTest extends AbstractWithConnectionManagerTest{



	public void testBeginTransaction() throws Exception {
		Connection connection = connectionManager.connect().get();
		try {
			Assert.assertTrue(!connection.isInTransaction(), "Connections should not start with transaction started");
			connection.beginTransaction();
			Assert.assertTrue(connection.isInTransaction(), "Connection should be in transaction");
			try {
				connection.beginTransaction();
				Assert.fail("Should have thrown exception because connection is already in transaction");
			} catch (DbException e) {
				// Pass
			}
		} finally {
			connection.close();
		}
	}

	public void testCommitRollbackWithNoTransaction() throws Exception {
		Connection connection = connectionManager.connect().get();
		try {
			// Test commit with no transaction
			try {
				connection.commit();
				Assert.fail("Not in transaction, commit should have failed");
			} catch (DbException e) {
				// Pass
			}

			// Test rollback with no transaction
			try {
				connection.rollback();
				Assert.fail("Not in transaction, rollback should have failed");
			} catch (DbException e) {
				// Pass
			}

			connection.beginTransaction();
			connection.rollback().get();

			connection.beginTransaction();
			connection.commit().get();

			connection.beginTransaction();
		} finally {
			connection.close();
		}
	}

    public void testAfterCommitNotTransactionIsActive() throws Exception {
        Connection connection = connectionManager.connect().get();

        connection.beginTransaction();

        connection.executeQuery("SELECT * FROM updates ").get();

        connection.commit().get();

        Assert.assertFalse(connection.isInTransaction());
    }

    public void testAfterRollbackNotTransactionIsActive() throws Exception {
        Connection connection = connectionManager.connect().get();

        connection.beginTransaction();

        connection.executeQuery("SELECT * FROM updates").get();

        connection.rollback().get();

        Assert.assertFalse(connection.isInTransaction());
    }

	public void testRollback() throws Exception {
		Connection connection = connectionManager.connect().get();
		try {
			// Clear out updates table
			Result result = connection.executeUpdate("DELETE FROM updates").get();
			assertNotNull(result);

			// Make sure updates is empty
			ResultSet rs = connection.executeQuery("SELECT id FROM updates").get();
			assertNotNull(rs);
			assertEquals(rs.size(), 0);

			connection.beginTransaction();

			// Insert a row
			result = connection.executeUpdate("INSERT INTO updates (id) VALUES (1)").get();
			assertNotNull(result);
			assertEquals(result.getAffectedRows(), 1L);

			// Make sure we can select the row
			rs = connection.executeQuery("SELECT id FROM updates").get();
			assertNotNull(rs);
			assertEquals(rs.size(), 1);
			Value value = rs.get(0).get(0);
			assertEquals(value.getInt(), 1);

			// Rollback transaction
			connection.rollback().get();

			// select query should now be empty
			rs = connection.executeQuery("SELECT id FROM updates").get();
			assertNotNull(rs);
			assertEquals(rs.size(), 0);

		} finally {
			connection.close();
		}
	}

	public void testCommit() throws Exception {
		Connection connection = connectionManager.connect().get();
		Connection connection2 = connectionManager.connect().get();
		try {
			// Clear out updates table
			Result result = connection.executeUpdate("DELETE FROM updates").get();
			assertNotNull(result);

			connection.beginTransaction();

			// Insert a row
			result = connection.executeUpdate("INSERT INTO updates (id) VALUES (1)").get();
			assertNotNull(result);
			assertEquals(result.getAffectedRows(), 1L);

			// Make sure second connection can't see data
			ResultSet rs = connection2.executeQuery("SELECT id FROM updates").get();
			assertNotNull(rs);
			assertEquals(rs.size(), 0);

			connection.commit().get();

			// Make sure both connections can see data
			rs = connection.executeQuery("SELECT id FROM updates").get();
			assertNotNull(rs);
			assertEquals(rs.size(), 1);
			assertEquals(rs.get(0).get(0).getInt(), 1);

			rs = connection2.executeQuery("SELECT id FROM updates").get();
			assertNotNull(rs);
			assertEquals(rs.size(), 1);
			assertEquals(rs.get(0).get(0).getInt(), 1);

		} finally {
			connection.close();
			connection2.close();
		}
	}
    public void testAfterCommitAutoCommit() throws Exception {
        Connection connection = connectionManager.connect().get();
        Connection connection2 = connectionManager.connect().get();
        try {
            // Clear out updates table
            Result result = connection.executeUpdate("DELETE FROM updates").get();
            assertNotNull(result);

            connection.beginTransaction();

            // Insert a row
            result = connection.executeUpdate("INSERT INTO updates (id) VALUES (1)").get();
            assertNotNull(result);
            assertEquals(result.getAffectedRows(), 1L);

            connection.commit().get();


            connection.executeUpdate("INSERT INTO updates (id) VALUES (2)").get();

            // Make sure both connections can see data
            ResultSet rs = connection2.executeQuery("SELECT id FROM updates").get();
            assertNotNull(rs);
            assertEquals(rs.size(), 2);

        } finally {
            connection.close();
            connection2.close();
        }
    }
}
