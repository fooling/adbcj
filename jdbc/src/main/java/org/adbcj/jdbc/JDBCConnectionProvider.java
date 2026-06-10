package org.adbcj.jdbc;

import java.sql.Connection;
import java.sql.SQLException;


public interface JDBCConnectionProvider {
    Connection getConnection() throws SQLException;
    Connection getConnection(String user,String password) throws SQLException;

}
