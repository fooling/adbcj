package org.adbcj.tck.test;

import org.adbcj.*;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;


@Test(invocationCount = 3, threadPoolSize = 5, timeOut = 500000)
public class PreparedStatementsTest extends AbstractWithConnectionManagerTest {

    public void testSimpleSelect() throws Exception {
        Connection connection = connectionManager.connect().get();
        PreparedQuery statement = connection.prepareQuery("SELECT * FROM simple_values" +
                " WHERE str_val LIKE ?").get();

        assertQueryFor(statement, "Zero");
        statement.close();
        connection.close();
    }

    public void testOrderIsCorrect() throws Exception {
        Connection connection = connectionManager.connect().get();
        PreparedQuery statement = connection.prepareQuery("SELECT * FROM simple_values" +
                " WHERE int_val > 0 ORDER BY int_val DESC").get();

        ResultSet resultSet = statement.execute().get();
        Assert.assertEquals(resultSet.size(), 4);
        Assert.assertEquals(resultSet.get(0).get(0).getInt(), 4);
        Assert.assertEquals(resultSet.get(1).get(0).getInt(), 3);
        Assert.assertEquals(resultSet.get(2).get(0).getInt(), 2);
        Assert.assertEquals(resultSet.get(3).get(0).getInt(), 1);


        statement.close();
        connection.close();
    }

    public void testSelectWithNull() throws Exception {
        Connection connection = connectionManager.connect().get();
        PreparedQuery statement = connection.prepareQuery("SELECT int_val,str_val,NULL FROM simple_values" +
                " WHERE str_val LIKE ?").get();

        ResultSet resultSet = statement.execute("Zero").get();

        Assert.assertEquals(resultSet.size(), 1);
        Assert.assertEquals(resultSet.get(0).get(1).getString(), "Zero");
        Assert.assertEquals(resultSet.get(0).get(2).getString(), null);
        statement.close();
        connection.close();
    }

    public void testErrorIsReported() throws Exception {
        Connection connection = connectionManager.connect().get();
        Future<PreparedQuery> future = connection.prepareQuery("SELECT * FROM this:is:an:invalid:query ");

        try {
            ResultSet rows = future.get().execute().get();
            Assert.fail("Expected a failure, and not " + rows);
        } catch (ExecutionException ex) {
            // expected
            Assert.assertTrue(ex.getCause() instanceof DbException);
        }
        connection.close();
    }

    public void testMultiErrorReported() throws Exception {
        Connection connection = connectionManager.connect().get();
        Future<PreparedUpdate> insert = connection.prepareUpdate("INSERT INTO this:is:an:invalid:query(name)" +
                " VALUES(42)");
        Future<PreparedUpdate> update = connection.prepareUpdate("UPDATE this:is:an:invalid:query SET name=42 WHERE name =1" +
                " VALUES(42)");
        Future<PreparedQuery> select = connection.prepareQuery("SELECT * FROM this:is:an:invalid:query ");

        try {
            Result result = insert.get().execute().get();
            Assert.fail("Expected a failure, and not " + result);
        } catch (ExecutionException ex) {
            // expected
            Assert.assertTrue(ex.getCause() instanceof DbException);
        }
        try {
            Result result = insert.get().execute().get();
            Assert.fail("Expected a failure, and not " + result);
        } catch (ExecutionException ex) {
            // expected
            Assert.assertTrue(ex.getCause() instanceof DbException);
        }
        try {
            ResultSet result = select.get().execute().get();
            Assert.fail("Expected a failure, and not " + select);
        } catch (ExecutionException ex) {
            // expected
            Assert.assertTrue(ex.getCause() instanceof DbException);
        }
        connection.close();
    }

    public void testCanReuseStatement() throws Exception {
        Connection connection = connectionManager.connect().get();
        PreparedQuery statement = connection.prepareQuery(
                "SELECT * FROM simple_values" +
                        " WHERE str_val LIKE ?").get();


        assertQueryFor(statement, "Zero");
        assertQueryFor(statement, "One");

        statement.close();
        connection.close();
    }

    public void testCanSelectNull() throws Exception {
        Connection connection = connectionManager.connect().get();
        PreparedQuery statement = connection.prepareQuery("SELECT * FROM table_with_some_values " +
                "WHERE `can_be_null_int` IS NULL").get();

        ResultSet resultSet = statement.execute().get();
        Assert.assertEquals(resultSet.get(0).get(1).getString(), null);
        Assert.assertEquals(resultSet.get(0).get(2).getString(), null);


        statement.close();
        connection.close();
    }

    public void testCanCloseStatement() throws Exception {
        Connection connection = connectionManager.connect().get();
        PreparedQuery statement = connection.prepareQuery("SELECT * FROM `table_with_some_values` " +
                "WHERE `can_be_null_int` IS NULL").get();


        statement.close().get();
        Assert.assertTrue(statement.isClosed());
        connection.close();
    }

    public void testThrowsOnInvalidArgumentCount() throws Exception {
        Connection connection = connectionManager.connect().get();
        PreparedQuery statement = connection.prepareQuery("SELECT * FROM simple_values" +
                " WHERE str_val LIKE ?").get();
        try {
            statement.execute("1", "2", "3").get();
            Assert.fail("Expect exception");
        } catch (DbException e) {
            Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
        } catch (IllegalArgumentException e) {
            // expected
        }
        connection.close();
    }

    public void testWorksWithCallback() throws Exception {

        Connection connection = connectionManager.connect().get();

        PreparedQuery query = connection.prepareQuery("SELECT str_val FROM simple_values " +
                " WHERE str_val LIKE ?").get();


        Future<StringBuilder> resultFuture = query.executeWithCallback(
                SelectTest.buildStringInCallback(),
                new StringBuilder(),
                "Zero");

        StringBuilder result = resultFuture.get();
        Assert.assertEquals(result.toString().toLowerCase(), SelectTest.expectedStringFromCallback());

        connection.close();

    }

    public void testExceptionInCallbackHandler() throws Exception {
        Connection connection = connectionManager.connect().get();


        PreparedQuery query = connection.prepareQuery("SELECT str_val FROM simple_values " +
                " WHERE str_val LIKE ?").get();
        Future<StringBuilder> resultFuture = query.executeWithCallback(new AbstractResultHandler<StringBuilder>() {
            @Override
            public void startFields(StringBuilder accumulator) {
                throw new RuntimeException("Failure here");
            }

        }, new StringBuilder(), "Zero");
        try {
            resultFuture.get();
            Assert.fail("Expected exception to be propagated");
        } catch (ExecutionException e) {
            Assert.assertTrue(e.getCause() instanceof DbException);
            Assert.assertTrue(e.getMessage().contains("Failure here"));

        }
    }

    public void testExceptionInCallbackHandlerDoesNotAffectOtherOperatins() throws Exception {
        Connection connection = connectionManager.connect().get();

        PreparedQuery query = connection.prepareQuery("SELECT str_val FROM simple_values " +
                " WHERE str_val LIKE ?").get();
        Future<StringBuilder> causeOfError = query.executeWithCallback(new AbstractResultHandler<StringBuilder>() {
            @Override
            public void startFields(StringBuilder accumulator) {
                throw new RuntimeException("Failure here");
            }
        }, new StringBuilder(), "Zero");
        final Future<ResultSet> future = query.execute("Two");
        ResultSet resultSet = future.get();
        String result = resultSet.get(0).get(0).getString();
        Assert.assertEquals(result, "Two");
    }


    private void assertQueryFor(PreparedQuery statement, String valueToQueryFor) throws Exception {
        ResultSet resultSet = statement.execute(valueToQueryFor).get();

        Assert.assertEquals(resultSet.size(), 1);
        Assert.assertEquals(resultSet.get(0).get(1).getString(), valueToQueryFor);
    }
}
