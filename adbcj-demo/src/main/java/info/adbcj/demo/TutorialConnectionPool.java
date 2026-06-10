package info.adbcj.demo;

import org.adbcj.Connection;
import org.adbcj.ConnectionManager;
import org.adbcj.ConnectionManagerProvider;
import org.adbcj.StandardProperties;
import org.h2.tools.Server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class TutorialConnectionPool {


    private static final int TEST_CONNECTION_COUNT = 100;

    public static void main(String[] args) throws Exception {
        // First, let's start a demo H2 database server
        Server demoH2Db = DemoServer.startServer();

        Map<String, String> settings = new HashMap<>();
        settings.put(StandardProperties.CONNECTION_POOL_ENABLE, "true");
        final ConnectionManager connectionManager = ConnectionManagerProvider.createConnectionManager(
                "adbcj:h2://localhost:14242/mem:db1;DB_CLOSE_DELAY=-1;MVCC=TRUE",
                "adbcj",
                "password1234",
                settings
        );

        long firstTime = System.currentTimeMillis();
        openCloseBunchOfConnections(connectionManager);
        System.out.println("First time: Time to connect: " + ((System.currentTimeMillis()-firstTime)/ TEST_CONNECTION_COUNT )+"ms");


        long secondTime = System.currentTimeMillis();
        openCloseBunchOfConnections(connectionManager);
        System.out.println("First time, pooled: Time to connect: " + ((System.currentTimeMillis()-secondTime)/ TEST_CONNECTION_COUNT )+"ms");

        connectionManager.close().get();

        demoH2Db.shutdown();
    }

    private static void openCloseBunchOfConnections(ConnectionManager connectionManager) throws Exception {
        ArrayList<Connection> connections = new ArrayList<>();
        for (int i = 0; i < TEST_CONNECTION_COUNT; i++) {
            connections.add(connectionManager.connect().get());
        }
        for (Connection connection : connections) {
            connection.close().get();
        }
    }
}
