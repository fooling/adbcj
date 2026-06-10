# Asynchronous Database Connectivity in Java (ADBCJ)
ADBCJ allows you to access a relational database in a asynchronous, non-blocking fashion. 
The API is inspired by JDBC, but makes all calls asynchronous. 

The asynchronous access prevents any blocked threads, 
which just wait for the result of the database. 

It also allows to pipeline operations, which are independent.
 Depending on the application, this can give a significant performance gain.

ADBCJ is intended as low level foundation. 
Therefore it's is written in Java, so other languages like Scala, Groovy, Kotlin etc can cosume it to

# Getting Started
All the code of this section is in the 'adbcj-demo' directory. 
## Add the dependencies
First add the Maven dependency and repository. Currently only snapshots are available.

    <repository>
        <snapshots>
            <enabled>false</enabled>
        </snapshots>
        <id>central</id>
        <name>bintray</name>
        <url>https://jcenter.bintray.com</url>
    </repository>
    
Add you're dependencies now. For MySQL:

    <dependency>
        <groupId>org.adbcj</groupId>
        <artifactId>adbcj-api</artifactId>
        <version>0.9</version>
    </dependency>
    <dependency>
        <groupId>org.adbcj</groupId>
        <artifactId>mysql-async-driver</artifactId>
        <version>0.9</version>
    </dependency>
    
Or for H2:

    <dependency>
        <groupId>org.adbcj</groupId>
        <artifactId>adbcj-api</artifactId>
        <version>0.9</version>
    </dependency>
    <dependency>
        <groupId>org.adbcj</groupId>
        <artifactId>h2-async-driver</artifactId>
        <version>0.9</version>
    </dependency>


## Connect to Database
First we create a connection manager, which holds all connections and other resources, like thread pools.
Usually you have one connection manager in an application.

The connection URL have a similar format to JDBC connection strings. 
For Mysql:
    
    adbcj:mysql://localhost/the-database
    
For H2:

    adbcj:h2://localhost/the-database
    
An H2 example. Let's create the connection manager
    
    final ConnectionManager connectionManager = ConnectionManagerProvider.createConnectionManager(
            "adbcj:h2://localhost/the-datanbase",
            "user",
            "password"
    );
    
Then, connect. 


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

# Go Async
ADBCJ is built for asynchronous operations. If you want synchronous operations, use JDBC.

So, when we just do a blocking Future.get(), there is no use in ADBCJ.
For direct ADBCJ programming, operations return Java 8 completable future. 
Therefore, you can take advantage of all the completable future features to compose your async operations

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
    
In case Java 8 CompletableFutures don't fit you're needs, every ADBCJ operation also takes a low level DbCallback.
This allows you to plug it into your own futures or async handling style.
The DbCallback, on success is called with the value or null. 
On failure it is called with the exception.


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

## The first SQL statements
ADBCJ has executeUpdate, executeQuery, prepareUpdate, prepareQuery etc similar to JDBC. 
So you can use these operations to update the database.

ADBCJ tries it's best to send any query immediately to the database when possible. 
It tries the best to avoid waiting on any round trips.
So, if you're queries and statements do not depend on each other, send them right away.
Then wait for the result of all sent queries.

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
        createAndInsertDone.thenCompose(res -> {
            return connection.executeQuery("SELECT * FROM posts");
        }).thenAccept(queryResult -> {
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

## Dealing with Failures
Let's create a error in our SQL. Example:

    private static void oupsError(ConnectionManager connectionManager) {
        connectionManager.connect().thenAccept(connection -> {
            connection.executeQuery(
                    "SELECT * FROM not-existing-table")
                    .thenAccept(queryResult -> {
                        // No result, since wrong SQL.
                    }).whenComplete((res, failure) -> {
                if (failure != null) {
                    failure.printStackTrace();
                }
                connection.close();
                connectionManager.close();
            });
        });
    }

This leads to this exception or similar stack trace. Notice something? 
The stack trace has no where the stack of our application in it. 
Nothing points us at the origin of the error in the `oupsError` method.
We only have internal stack traces.

This is a problem from the asynchronous nature of the ADBCJ: 
The handling of the connection is done on a event loop of the driver. 

    java.util.concurrent.CompletionException: org.adbcj.h2.H2DbException: Syntax error in SQL statement "SELECT * FROM NOT[*]-EXISTING-TABLE "; expected "identifier"
        at java.util.concurrent.CompletableFuture.encodeThrowable(CompletableFuture.java:292)
        at java.util.concurrent.CompletableFuture.completeThrowable(CompletableFuture.java:308)
        at java.util.concurrent.CompletableFuture.uniAccept(CompletableFuture.java:647)
        at java.util.concurrent.CompletableFuture$UniAccept.tryFire(CompletableFuture.java:632)
        at java.util.concurrent.CompletableFuture.postComplete(CompletableFuture.java:474)
        at java.util.concurrent.CompletableFuture.completeExceptionally(CompletableFuture.java:1977)
        at org.adbcj.support.DbCompletableFuture.onComplete(DbCompletableFuture.java:21)
        at org.adbcj.h2.decoding.StatementPrepare.requestFailedContinue(StatementPrepare.java:48)
        at org.adbcj.h2.decoding.StatusReadingDecoder.handleException(StatusReadingDecoder.java:55)
        at org.adbcj.h2.decoding.StatementPrepare.handleException(StatementPrepare.java:39)
        at org.adbcj.h2.RequestCreator$ContinueSql.handleException(RequestCreator.java:238)
        at org.adbcj.h2.decoding.AnswerNextRequest.handleException(AnswerNextRequest.java:28)
        at org.adbcj.h2.decoding.StatusReadingDecoder.decode(StatusReadingDecoder.java:36)
        at org.adbcj.h2.decoding.Decoder.decode(Decoder.java:31)
        at io.netty.handler.codec.ByteToMessageDecoder.callDecode(ByteToMessageDecoder.java:411)
        at io.netty.handler.codec.ByteToMessageDecoder.channelRead(ByteToMessageDecoder.java:248)
        at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:373)
        at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:359)
        at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:351)
        at io.netty.channel.DefaultChannelPipeline$HeadContext.channelRead(DefaultChannelPipeline.java:1334)
        at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:373)
        at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:359)
        at io.netty.channel.DefaultChannelPipeline.fireChannelRead(DefaultChannelPipeline.java:926)
        at io.netty.channel.nio.AbstractNioByteChannel$NioByteUnsafe.read(AbstractNioByteChannel.java:129)
        at io.netty.channel.nio.NioEventLoop.processSelectedKey(NioEventLoop.java:651)
        at io.netty.channel.nio.NioEventLoop.processSelectedKeysOptimized(NioEventLoop.java:574)
        at io.netty.channel.nio.NioEventLoop.processSelectedKeys(NioEventLoop.java:488)
        at io.netty.channel.nio.NioEventLoop.run(NioEventLoop.java:450)
        at io.netty.util.concurrent.SingleThreadEventExecutor$5.run(SingleThreadEventExecutor.java:873)
        at io.netty.util.concurrent.DefaultThreadFactory$DefaultRunnableDecorator.run(DefaultThreadFactory.java:144)
        at java.lang.Thread.run(Thread.java:745)
    Caused by: org.adbcj.h2.H2DbException: Syntax error in SQL statement "SELECT * FROM NOT[*]-EXISTING-TABLE "; expected "identifier"
        at org.adbcj.h2.H2DbException.create(H2DbException.java:38)
        at org.adbcj.h2.decoding.StatusReadingDecoder.decode(StatusReadingDecoder.java:37)
        ... 18 more
    
Luckily, ADBCJ has a debug option to capture the entry point of every operation.
Add `-Dorg.adbcj.debug=true` as a JVM start up parameter. 
This trigger ADBCJ to capture all entry points into the driver and add it to errors.
This is a development and debug feature. It has a high overhead.
As an alternative to the JVM flag, you can it on a per connection basis, 
by adding `"org.adbcj.debug"="true"` to the properties map when connecting.

The stack trace now containes the origin of the error. 
Do you spot 'TutorialDealingWithErrors.oupsError' in the second stack trace? 
That's the true origin where the error started.

    java.util.concurrent.CompletionException: org.adbcj.h2.H2DbException: Syntax error in SQL statement "SELECT * FROM NOT[*]-EXISTING-TABLE "; expected "identifier"
        at java.util.concurrent.CompletableFuture.encodeThrowable(CompletableFuture.java:292)
        at java.util.concurrent.CompletableFuture.completeThrowable(CompletableFuture.java:308)
        at java.util.concurrent.CompletableFuture.uniAccept(CompletableFuture.java:647)
        at java.util.concurrent.CompletableFuture$UniAccept.tryFire(CompletableFuture.java:632)
        // SNIP
    Caused by: org.adbcj.h2.H2DbException: Syntax error in SQL statement "SELECT * FROM NOT[*]-EXISTING-TABLE "; expected "identifier"
        at java.lang.Thread.getStackTrace(Thread.java:1556)
        at org.adbcj.support.stacktracing.StackTracingOptions$1.captureStacktraceAtEntryPoint(StackTracingOptions.java:16)
        at org.adbcj.support.AbstractConnectionManager.entryPointStack(AbstractConnectionManager.java:102)
        at org.adbcj.h2.H2ConnectionManager.connect(H2ConnectionManager.java:69)
        at org.adbcj.h2.H2ConnectionManager.connect(H2ConnectionManager.java:60)
        at org.adbcj.ConnectionManager.connect(ConnectionManager.java:48)
        at info.adbcj.demo.TutorialDealingWithErrors.oupsError(TutorialDealingWithErrors.java:33)
        at info.adbcj.demo.TutorialDealingWithErrors.main(TutorialDealingWithErrors.java:19)
    Caused by: org.adbcj.DbException: Syntax error in SQL statement "SELECT * FROM NOT[*]-EXISTING-TABLE "; expected "identifier"
        at org.adbcj.h2.H2DbException.<init>(H2DbException.java:22)
        at org.adbcj.h2.H2DbException.create(H2DbException.java:40)
        // SNIP

## Simple Connection Pool

You can enable a connection pool by setting the `org.adbcj.connectionpool.enable` property to true.

When set to true, when you close a connection, it is returned to a connection pool.
So, when connecting the next time, a initial connection handshakes can be skipped.

    Map<String, String> settings = new HashMap<>();
    settings.put(StandardProperties.CONNECTION_POOL_ENABLE, "false");
    final ConnectionManager connectionManager = ConnectionManagerProvider.createConnectionManager(
            "adbcj:h2://localhost:14242/mem:db1;DB_CLOSE_DELAY=-1;MVCC=TRUE",
            "adbcj",
            "password1234",
            settings
    );
    
Current implementation limitations:
    
- At the moment there are no limits like timeouts, maximum connections.
- If prepared statements or similar things were not closed, they are not closed when the connection returns to the pool.
  The prepared statements will leak on the database side.
  
##  Request Queue Length
With ADBCJ it is very easy to send hundreds to queries to the database, without waiting for results. 
You might queue up more queries than you expect and unexpectedly overload the database.

To protect from database overloads, ADBCJ as a default maximum queue length of 64 per connection
That means if 64 requests are pending, and you issue another one, that one is aborted and a exception is returned.
 
An example:
 
    // Blocking code to keep demo code short
    Connection connection = connectionManager.connect().get();
    PreparedQuery stmt = connection.prepareQuery("SELECT ?").get();
    
    // Issue 200 requests, without waiting for any response
    for (int i = 0; i < 200; i++) {
        stmt.execute(i).handle((rows, queryFailure) -> {
            if (queryFailure == null) {
                System.out.println(" - Yeah, Result: " + rows.get(0).get(0));
            } else {
                failures.incrementAndGet();
                System.err.println(" - Oups, we send to many queries");
                queryFailure.printStackTrace(System.err);
            }
            waitForCompletion.countDown();
            return null;
        });
    }
    
This will almost certainly hit the que maximum, creating this error:

    org.adbcj.DbException: To many pending requests. The current maximum is 64. Ensure that your not overloading the database with requests. Also check the adbcj.maxQueueLength property
        at org.adbcj.h2.H2Connection.failIfQueueFull(H2Connection.java:192)
        at org.adbcj.h2.H2Connection.queRequest(H2Connection.java:182)
        at org.adbcj.h2.H2PreparedQuery.executeWithCallback(H2PreparedQuery.java:29)
        at org.adbcj.PreparedQuery.execute(PreparedQuery.java:15)
        at info.adbcj.demo.TutorialQueueLimit.runQueries(TutorialQueueLimit.java:69)
        at info.adbcj.demo.TutorialQueueLimit.firstRunToSmallQueue(TutorialQueueLimit.java:39)
        at info.adbcj.demo.TutorialQueueLimit.main(TutorialQueueLimit.java:19)
    Caused by: org.adbcj.DbException: To many pending requests. The current maximum is 64. Ensure that your not overloading the database with requests. Also check the adbcj.maxQueueLength property
        ... 7 more
        
You can increase the queue limit by setting the max queue length:

        int maxQueueLength = 200;
        Map<String, String> props = new HashMap<>();
        props.put(StandardProperties.MAX_QUEUE_LENGTH, String.valueOf(maxQueueLength));
        final ConnectionManager connectionManager = ConnectionManagerProvider.createConnectionManager(
                "adbcj:h2://localhost:14242/mem:db1;DB_CLOSE_DELAY=-1;MVCC=TRUE",
                "adbcj",
                "password1234",
                props
        );


    