package info.adbcj.demo;

import org.adbcj.Connection;
import org.adbcj.ConnectionManager;
import org.adbcj.ConnectionManagerProvider;
import org.h2.tools.Server;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class TutorialFirstConnectionBlocking {

    public static void main(String[] args) {
        // First, let's start a demo H2 database server
        Server demoH2Db = DemoServer.startServer();

        final ConnectionManager connectionManager = ConnectionManagerProvider.createConnectionManager(
                "adbcj:h2://localhost:14242/mem:db1;DB_CLOSE_DELAY=-1;MVCC=TRUE",
                "adbcj",
                "password1234"
        );

        // BLOCKING! Not recommended. Just example to get started
        CompletableFuture<Connection> connectionFuture = connectionManager.connect();
        try {
            Connection connection = connectionFuture.get();
            System.out.println("Connected!");
            connection.close().get();
            System.out.println("Closed!");
            connectionManager.close();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            e.printStackTrace();
            System.exit(-1);
        }


        demoH2Db.shutdown();
    }
}
