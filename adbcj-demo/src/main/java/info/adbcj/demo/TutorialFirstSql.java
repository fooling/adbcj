package info.adbcj.demo;

import org.adbcj.*;
import org.h2.tools.Server;

import java.util.concurrent.CompletableFuture;

public class TutorialFirstSql {

    public static void main(String[] args) throws Exception {
        // First, let's start a demo H2 database server
        Server demoH2Db = DemoServer.startServer();

        final ConnectionManager connectionManager = ConnectionManagerProvider.createConnectionManager(
                "adbcj:h2://localhost:14242/mem:db1;DB_CLOSE_DELAY=-1;MVCC=TRUE",
                "adbcj",
                "password1234"
        );

        connectionManager.connect().thenAccept(connection -> {
            CompletableFuture<Result> create = connection.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS posts(\n" +
                            "  id int NOT NULL AUTO_INCREMENT,\n" +
                            "  title varchar(255) NOT NULL,\n" +
                            "  content TEXT NOT NULL,\n" +
                            "  PRIMARY KEY (id)\n" +
                            ");");
            // NOTE: As long operation does not depend on the previous one, you can issue then right away.
            // ADBCJ will immediately send these requests to the database, when possible
            CompletableFuture<Result> firstInsert = connection.executeUpdate("INSERT INTO posts(title,content) VALUES('The Title','TheContent')");
            CompletableFuture<Result> secondInsert = connection.executeUpdate("INSERT INTO posts(title,content) VALUES('Another Title','More Content')");

            // Once you need to wait for previous requests, the Java 8 completable futures will help you
            CompletableFuture<Void> createAndInsertDone = CompletableFuture.allOf(create, firstInsert, secondInsert);

            // And then, a simple query
            createAndInsertDone.thenCompose(res -> connection.executeQuery("SELECT * FROM posts")).thenAccept(queryResult -> {
                // NOTE: ADBCJ default result sets are regular Java collections.
                // They start at index 0 (ZERO)
                // And interate like regular collections
                for (Row row : queryResult) {
                    System.out.println("ID: " + row.get("ID").getLong() + " with title " + row.get("title").getString());
                }
            }).whenComplete((res, failure) -> {
                if (failure != null) {
                    failure.printStackTrace();
                }
                connection.close();
                connectionManager.close();
            });
        });

    }

}
