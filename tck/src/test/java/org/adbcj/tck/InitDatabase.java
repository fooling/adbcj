package org.adbcj.tck;

import org.adbcj.jdbc.PlainJDBCConnection;
import org.testng.Assert;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Parameters;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public abstract class InitDatabase {

    protected InitDatabase() {
    }

    public final void prepareMySQL(String url, String user, String password) throws Exception {
        beforeSetupScript(url, user, password);
        runSQLScript(url, user, password, setupScript());
    }

    protected void beforeSetupScript(String url, String user, String password){

    }

    protected abstract String setupScript();

    public void cleanUp(String jdbcUrl, String user, String password) {
        runSQLScript(jdbcUrl, user, password, tearDownScript());
        afterCleanupScript();
    }

    protected void afterCleanupScript(){

    }

    protected abstract String tearDownScript();

    private void runSQLScript(String jdbcUrl, String user, String password, String script) {
        try {
            try (Connection connection = new PlainJDBCConnection(jdbcUrl, user, password, new HashMap<>()).getConnection()) {
                for (String line : setupSQL(script)) {
                    Statement stmt = connection.createStatement();
                    stmt.execute(line);
                    stmt.close();

                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage(), e);
        }
    }


    private List<String> setupSQL(String resourceName) {
        InputStream sqlData = getClass().getClassLoader().getResourceAsStream(resourceName);
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(sqlData,"UTF8"));
            StringBuilder wholeFile = new StringBuilder();
            String line = reader.readLine();
            while (null != line) {
                wholeFile.append(line);
                line = reader.readLine();
            }
            return Arrays.asList(wholeFile.toString().split(";"));
        } catch (IOException e) {
            throw new RuntimeException("Couldn't read resource " + resourceName);
        } finally {
            try {
                sqlData.close();
            } catch (IOException e) {
                throw new RuntimeException("Couldn't read resource " + resourceName);
            }
        }
    }

}
