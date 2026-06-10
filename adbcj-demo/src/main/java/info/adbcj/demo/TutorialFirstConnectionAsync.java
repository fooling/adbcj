package info.adbcj.demo;

import org.adbcj.ConnectionManager;
import org.adbcj.ConnectionManagerProvider;
import org.h2.tools.Server;

public class TutorialFirstConnectionAsync {

    public static void main(String[] args) {
        // First, let's start a demo H2 database server
        Server demoH2Db = DemoServer.startServer();

        final ConnectionManager connectionManager = ConnectionManagerProvider.createConnectionManager(
                "adbcj:h2://localhost:14242/mem:db1;DB_CLOSE_DELAY=-1;MVCC=TRUE",
                "adbcj",
                "password1234"
        );

        // Aysync with raw callbacks.
        connectionManager.connect((connection, connectionFailure) -> {
            if (connectionFailure == null) {
                System.out.println("Connected!");
                // No failure, continue the operations
                connection.close((connectionClosed, closeFailure) -> {
                    if (closeFailure != null) {
                        closeFailure.printStackTrace();
                    } else {
                        System.out.println("Closed!");
                    }

                    // At the end, close the connection manger
                    connectionManager.close((managerClosed, managerCloseFailure) -> {
                        if (managerCloseFailure != null) {
                            managerCloseFailure.printStackTrace();
                        }
                        System.exit(0);
                    });
                });
            } else {
                // Otherwise, back out and print the error
                connectionFailure.printStackTrace();
                System.exit(-1);
            }
        });


        demoH2Db.shutdown();
    }
}
