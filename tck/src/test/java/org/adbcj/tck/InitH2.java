package org.adbcj.tck;

import org.adbcj.jdbc.PlainJDBCConnection;
import org.h2.tools.Server;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;


public class InitH2 extends InitDatabase {

    @Override
    protected String setupScript() {
        return "sql/setupH2.sql";
    }

    @Override
    protected String tearDownScript() {
        return "sql/cleanUpH2.sql";
    }

    @Override
    protected void beforeSetupScript(String jdbcUrl, String user, String password) {

        try {
            Connection connection = new PlainJDBCConnection(jdbcUrl,
                    user,
                    password,
                    new HashMap<>()).getConnection();
            if (null != connection) {
                connection.close();
                return;
            }
        } catch (SQLException e) {
            // expected, server not running
        }
        try {
            Server server = Server.createTcpServer(
                    "-tcpAllowOthers",
                    "-tcpDaemon",
                    "-tcpPort", "14242",
                    "-baseDir", "./h2testdb");
//            Server inspect = Server.createWebServer();
//            inspect.start();
            server.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    protected void afterCleanupScript() {
    }
}
