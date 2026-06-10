package info.adbcj.demo;

import org.adbcj.ConnectionManager;
import org.adbcj.ConnectionManagerProvider;
import org.h2.tools.Server;

public class TutorialFirstConnectionFuture {

    public static void main(String[] args) {
        // First, let's start a demo H2 database server
        Server demoH2Db = DemoServer.startServer();

        final ConnectionManager connectionManager = ConnectionManagerProvider.createConnectionManager(
                "adbcj:h2://localhost:14242/mem:db1;DB_CLOSE_DELAY=-1;MVCC=TRUE",
                "adbcj",
                "password1234"
        );

        connectionManager.connect().thenCompose(connection -> {
            System.out.println("Connected!");
            return connection.close();
        }).thenCompose(closeComplete -> {
            System.out.println("Close complete!");
            return connectionManager.close();
        }).whenComplete((complete, error) -> {
            if (error != null) {
                error.printStackTrace();
            }
            System.exit(-1);
        });

    }
}
