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

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;



@Test(invocationCount = 10, threadPoolSize = 4, timeOut = 50000)
public class SelectTest extends AbstractWithConnectionManagerTest {


    public void testSelectWhichReturnsNothing() throws Exception {
        Connection connection = connectionManager.connect().get();
        final CountDownLatch latch = new CountDownLatch(1);
        ResultSet resultSet = connection.executeQuery("SELECT int_val, str_val " +
                "FROM simple_values where str_val " +
                "LIKE 'Not-In-Database-Value'")
                .whenComplete((res, err) -> {
                    Assert.assertNotNull(res);
                    Assert.assertNull(err);
                    latch.countDown();
                }).get();
        Iterator<Row> i = resultSet.iterator();
        Assert.assertFalse(i.hasNext());

        connection.close();
    }

    public void testSimpleSelect() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        Connection connection = connectionManager.connect().get();
        try {
            ResultSet resultSet = connection.executeQuery("SELECT int_val, str_val " +
                    "FROM simple_values " +
                    "ORDER BY int_val")
            .whenComplete((res, err) -> {
                Assert.assertNotNull(res);
                Assert.assertNull(err);
                latch.countDown();
            }).get();

            Assert.assertEquals(6, resultSet.size());

            Iterator<Row> i = resultSet.iterator();

            Row nullRow = null;
            Row row = i.next();
            if (row.get(0).isNull()) {
                nullRow = row;
                row = i.next();
            }
            Assert.assertEquals(row.get(0).getInt(), 0);
            Assert.assertEquals(row.get(1).getValue(), "Zero");
            row = i.next();
            Assert.assertEquals(row.get(0).getInt(), 1);
            Assert.assertEquals(row.get(1).getValue(), "One");
            row = i.next();
            Assert.assertEquals(row.get(0).getInt(), 2);
            Assert.assertEquals(row.get(1).getValue(), "Two");
            row = i.next();
            Assert.assertEquals(row.get(0).getInt(), 3);
            Assert.assertEquals(row.get(1).getValue(), "Three");
            row = i.next();
            Assert.assertEquals(row.get(0).getInt(), 4);
            Assert.assertEquals(row.get(1).getValue(), "Four");

            if (i.hasNext() && nullRow == null) {
                nullRow = i.next();
            }

            Assert.assertEquals(nullRow.get(0).getValue(), null);
            Assert.assertEquals(nullRow.get(1).getValue(), null);


            Assert.assertTrue(!i.hasNext(), "There were too many rows in result set");

            Assert.assertTrue(latch.await(5, TimeUnit.SECONDS), "Expect callback call");
        } finally {
            connection.close().get();
        }
    }

    public void testSelectWithNullFields() throws Exception {
        Connection connection = connectionManager.connect().get();

        ResultSet resultSet = connection.executeQuery("SELECT * FROM `table_with_some_values` WHERE `can_be_null_int` IS NULL").get();

        Assert.assertEquals(resultSet.get(0).get(1).getString(), null);
        Assert.assertEquals(resultSet.get(0).get(2).getString(), null);

        connection.close().get();

    }

    public void testMultipleSelectStatements() throws Exception {
        Connection connection = connectionManager.connect().get();

        List<Future<ResultSet>> futures = new LinkedList<Future<ResultSet>>();
        for (int i = 0; i < 50; i++) {
            futures.add(
                    connection.executeQuery(String.format("SELECT *, %d FROM simple_values", i))
            );
        }

        for (Future<ResultSet> future : futures) {
            try {
                future.get(5, TimeUnit.MINUTES);
            } catch (TimeoutException e) {
                throw new AssertionError("Timed out waiting on future: " + future);
            }
        }
    }

    public void testWorksWithCallback() throws Exception {
        Connection connection = connectionManager.connect().get();


        Future<StringBuilder> resultFuture = connection.executeQuery(
                "SELECT str_val FROM simple_values " +
                        "WHERE str_val LIKE 'Zero'", buildStringInCallback(), new StringBuilder());

        StringBuilder result = resultFuture.get();
        Assert.assertEquals(result.toString().toLowerCase(), expectedStringFromCallback());

        connection.close();

    }

    public void testExceptionInCallbackHandler() throws Exception {
        Connection connection = connectionManager.connect().get();

        int throwOn = 0;
        boolean stillHasToExplore = true;
        while (stillHasToExplore) {
            System.out.println("Testing failure on " + throwOn);
            ExceptionOnCall faultyHandler = new ExceptionOnCall(throwOn);

            Future<StringBuilder> resultFuture = connection.executeQuery(
                    "SELECT str_val FROM simple_values " +
                            "WHERE str_val LIKE 'Zero'",
                    faultyHandler,
                    new StringBuilder());
            try {
                resultFuture.get();
                Assert.fail("Expected exception to be propagated");
            } catch (ExecutionException e) {
                Assert.assertTrue(e.getCause() instanceof DbException);
                Assert.assertTrue(e.getMessage().contains("Failure call no " + throwOn));
            }

            stillHasToExplore = !faultyHandler.didThrowLastCall();
            throwOn++;
        }


    }

    public void testTwoSelectsAfterEachOther() throws Exception {
        Connection connection = connectionManager.connect().get();


        Future<ResultSet> firstCall = connection.executeQuery("SELECT str_val FROM simple_values " +
                "WHERE str_val LIKE 'One'");
        Future<ResultSet> nextCall = connection.executeQuery("SELECT str_val FROM simple_values " +
                "WHERE str_val LIKE 'Two'");

        String firstResult = firstCall.get().get(0).get(0).getString();
        Assert.assertEquals(firstResult, "One");
        String secondResult = nextCall.get().get(0).get(0).getString();
        Assert.assertEquals(secondResult, "Two");
    }

    public void testExceptionInCallbackHandlerDoesNotAffectNextCall() throws Exception {
        Connection connection = connectionManager.connect().get();

        Future<StringBuilder> errorCause = connection.executeQuery(
                "SELECT str_val FROM simple_values " +
                        "WHERE str_val LIKE 'Zero'", new ExceptionOnCall(0), new StringBuilder());
        ResultSet nextCall = connection.executeQuery(
                "SELECT str_val FROM simple_values " +
                        "WHERE str_val LIKE 'Two'").get();
        try {
            errorCause.get();
            Assert.fail("Expect failed result");
        } catch (ExecutionException ex) {
            Assert.assertTrue(ex.getCause() instanceof DbException);
            // expected
        }
        String result = nextCall.get(0).get(0).getString();
        Assert.assertEquals(result, "Two");
    }


    public void testBrokenSelect() throws Exception {
        Connection connection = connectionManager.connect().get();

        Future<ResultSet> future = connection.executeQuery("SELECT broken_query");
        try {
            future.get(5, TimeUnit.SECONDS);
            throw new AssertionError("Issues a bad query, future should have failed");
        } catch (ExecutionException e) {
            // Pass
            Assert.assertTrue(e.getCause() instanceof DbException);
        } finally {
            connection.close().get();
        }
    }

    public void testFollowBrokenSelectWithValid() throws Exception {
        Connection connection = connectionManager.connect().get();

        Future<ResultSet> future = connection.executeQuery("SELECT broken_query");
        try {
            future.get(5, TimeUnit.SECONDS);
            throw new AssertionError("Issues a bad query, future should have failed");
        } catch (ExecutionException e) {
            Assert.assertTrue(e.getCause() instanceof DbException);
            // Pass
            Future<ResultSet> followUp = connection.executeQuery(
                    "SELECT int_val AS number, str_val AS otherName " +
                            "FROM simple_values " +
                            "WHERE str_val LIKE 'Two'");
            final ResultSet values = followUp.get(5, TimeUnit.SECONDS);
            Assert.assertEquals(2, values.get(0).get("number").getInt());
            Assert.assertEquals("Two", values.get(0).get("otherName").getString());
        } finally {
            connection.close().get();
        }
    }


    public void testCanUseProjectedNames() throws Exception {
        Connection connection = connectionManager.connect().get();

        Future<ResultSet> future = connection.executeQuery(
                "SELECT int_val AS number, str_val AS otherName " +
                        "FROM simple_values " +
                        "WHERE str_val LIKE 'Two'");
        try {
            final ResultSet values = future.get(5, TimeUnit.SECONDS);
            Assert.assertEquals(2, values.get(0).get("number").getInt());
            Assert.assertEquals("Two", values.get(0).get("otherName").getString());
        } finally {
            connection.close().get();
        }
    }

    static String expectedStringFromCallback() {
        return "startFields-field(str_val)-endFields-startResults-startRow-value(Zero)-endRow-endResults".toLowerCase();
    }

    static class ExceptionOnCall implements ResultHandler<StringBuilder> {
        int callNo = 0;
        final int throwOn;
        boolean didNotFailYet = true;
        private boolean didThrowOnLast = false;

        public boolean didThrowLastCall() {
            return didThrowOnLast;
        }

        public ExceptionOnCall(int throwOn) {
            this.throwOn = throwOn;
        }

        @Override
        public void startFields(StringBuilder accumulator) {
            failIfRightCall("Expected failure: startFields");
        }

        @Override
        public void field(Field field, StringBuilder accumulator) {
            failIfRightCall("Expected failure: field");
        }

        @Override
        public void endFields(StringBuilder accumulator) {
            failIfRightCall("Expected failure: endFields");
        }

        @Override
        public void startResults(StringBuilder accumulator) {
            failIfRightCall("Expected failure: startResults");
        }

        @Override
        public void startRow(StringBuilder accumulator) {
            failIfRightCall("Expected failure: startRow");
        }

        @Override
        public void value(Value value, StringBuilder accumulator) {
            failIfRightCall("Expected failure: value");
        }

        @Override
        public void endRow(StringBuilder accumulator) {
            failIfRightCall("Expected failure: value");
        }

        @Override
        public void endResults(StringBuilder accumulator) {
            if (didNotFailYet) {
                didThrowOnLast = true;
                throw new RuntimeException("Expected failure: endResults, Failure call no " + throwOn);
            }
            callNo++;
        }

        private void failIfRightCall(String message) {
            if (callNo == throwOn && didNotFailYet) {
                didNotFailYet = false;
                throw new RuntimeException(message + ", Failure call no " + throwOn);
            }
            callNo++;
        }

    }

    static ResultHandler<StringBuilder> buildStringInCallback() {
        return new ResultHandler<StringBuilder>() {
            @Override
            public void startFields(StringBuilder accumulator) {
                accumulator.append("startFields-");
            }

            @Override
            public void field(Field field, StringBuilder accumulator) {
                accumulator.append("field(").append(field.getColumnLabel()).append(")-");
            }

            @Override
            public void endFields(StringBuilder accumulator) {
                accumulator.append("endFields-");
            }

            @Override
            public void startResults(StringBuilder accumulator) {
                accumulator.append("startResults-");
            }

            @Override
            public void startRow(StringBuilder accumulator) {
                accumulator.append("startRow-");
            }

            @Override
            public void value(Value value, StringBuilder accumulator) {
                accumulator.append("value(").append(value.getString()).append(")-");
            }

            @Override
            public void endRow(StringBuilder accumulator) {
                accumulator.append("endRow-");
            }

            @Override
            public void endResults(StringBuilder accumulator) {
                accumulator.append("endResults");
            }

        };
    }

}
