package org.adbcj.tck.test;

import org.adbcj.*;
import org.testng.Assert;
import org.testng.annotations.Test;


public class PreparedUpdateTest extends AbstractWithConnectionManagerTest{
    @Test
    public void testCanInsert() throws Exception {
        Connection connection = connectionManager.connect().get();
        cleanUp(connection);
        PreparedUpdate insert
                = connection.prepareUpdate("INSERT INTO updates (id) VALUES (1)").get();
        insert.execute().get();


        PreparedUpdate update
                = connection.prepareUpdate("UPDATE updates SET id=? WHERE id=?").get();
        update.execute(42, 1);
        update.execute(4242, 42);


        PreparedQuery select
                = connection.prepareQuery("SELECT id FROM updates WHERE id=4242").get();

        final ResultSet rows = select.execute().get();
        Assert.assertEquals(1, rows.size());

        cleanUp(connection);
        connection.close();
    }

    private void cleanUp(Connection connection) throws Exception {
        connection.executeUpdate("DELETE FROM updates").get();
    }
}
