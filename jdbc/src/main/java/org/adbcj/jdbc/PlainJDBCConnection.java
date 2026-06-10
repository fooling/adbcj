package org.adbcj.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;


public final class PlainJDBCConnection implements JDBCConnectionProvider {

    private static final String USER = "user";
    private static final String PASSWORD = "password";

    private final String jdbcUrl;
    private final Properties properties;

    public PlainJDBCConnection(String jdbcUrl, String username,String password, Map<String,String> properties) {
        this.jdbcUrl = jdbcUrl;
        this.properties = new Properties();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            properties.put(entry.getKey(),entry.getValue());
        }

        this.properties.put(USER, username);
        this.properties.put(PASSWORD, password);
    }

    @Override
    public Connection getConnection() throws SQLException{
        return DriverManager.getConnection(jdbcUrl, properties);

    }

    @Override
    public Connection getConnection(String user,String password) throws SQLException {
        Properties withUsername = new Properties(properties);

        withUsername.put(USER, user);
        withUsername.put(PASSWORD, password);
        return DriverManager.getConnection(jdbcUrl, withUsername);
    }
}
