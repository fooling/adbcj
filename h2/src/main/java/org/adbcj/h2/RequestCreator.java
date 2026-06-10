package org.adbcj.h2;

import org.adbcj.*;
import org.adbcj.h2.decoding.*;
import org.adbcj.h2.packets.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class RequestCreator {
    private final H2Connection connection;
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestCreator.class);

    RequestCreator(H2Connection connection) {
        this.connection = connection;
    }


    Request createCloseRequest(DbCallback<Void> callback,
                               StackTraceElement[] entry) {
        return new Request<>("Close-Request",
                callback,
                new CloseConnection(connection, callback, entry),
                new CloseCommand());
    }

    <T> Request createQuery(String sql,
                            ResultHandler<T> eventHandler,
                            T accumulator,
                            DbCallback<T> callback,
                            StackTraceElement[] entry) {
        final int sessionId = connection.nextId();
        final int queryId = connection.nextId();

        QueryPrepareCommand queryPrepareCommand = new QueryPrepareCommand(sessionId, sql);
        QueryExecute queryExecute = new QueryExecute(sessionId, queryId);
        CommandClose commandClose = new CommandClose(queryId);

        Request<T> executeQuery = new Request<T>(
                "Query: " + sql,
                callback,
                new QueryHeader<>(
                        connection,
                        eventHandler,
                        accumulator,
                        callback,
                        entry),
                new CompoundCommand(queryExecute, commandClose)
        );


        ContinueSql<T> stmtPrepResponse = new ContinueSql<T>(connection, callback, entry, executeQuery);

        return new BlockingRequestInProgress<T>(
                connection,
                "Query: " + sql,
                callback,
                stmtPrepResponse,
                queryPrepareCommand,
                executeQuery
        );
    }


    Request createUpdate(String sql, DbCallback<Result> callback, StackTraceElement[] entry) {

        final int sessionId = connection.nextId();

        CompoundCommand executeStmt = new CompoundCommand(
                new UpdateExecute(sessionId),
                new QueryExecute(connection.idForAutoId(), connection.nextId()),
                new CommandClose(sessionId));
        Request<Result> executeQuery = new Request<>(
                "Update: " + sql,
                callback,
                new UpdateResult(connection, callback, entry),
                executeStmt);

        StatementPrepare<Result> stmtPrepResponse = new ContinueSql<Result>(connection, callback, entry, executeQuery);


        return new BlockingRequestInProgress<>(
                connection,
                "Update: " + sql,
                callback,
                stmtPrepResponse,
                new QueryPrepareCommand(sessionId, sql),
                executeQuery
        );
    }

    Request executePrepareQuery(String sql, DbCallback<PreparedQuery> callback, StackTraceElement[] entry) {
        final int sessionId = connection.nextId();
        return new Request<>("Prepare Query: " + sql,
                callback,
                createPrepareQuery(callback, sessionId, entry),
                new QueryPrepareCommand(sessionId, sql));
    }


    Request executePrepareUpdate(String sql, DbCallback<PreparedUpdate> callback, StackTraceElement[] entry) {
        final int sessionId = connection.nextId();
        return new Request<>(
                "Prepare Update: " + sql,
                callback,
                createPrepareUpdate(callback, sessionId, entry),
                new QueryPrepareCommand(sessionId, sql));
    }

    public <T> Request<T> executeQueryStatement(ResultHandler<T> eventHandler,
                                                T accumulator,
                                                DbCallback<T> callback,
                                                StackTraceElement[] entry,
                                                int sessionId,
                                                Object[] params) {
        int queryId = connection.nextId();
        return new Request<>(
                "ExecutePreparedQuery",
                callback,
                new QueryHeader<T>(
                        connection,
                        eventHandler,
                        accumulator,
                        callback,
                        entry
                ),
                new QueryExecute(sessionId, queryId, params));
    }

    Request<Result> executeUpdateStatement(int sessionId,
                                           Object[] params,
                                           DbCallback<Result> callback,
                                           StackTraceElement[] entry) {
        return new Request<>(
                "ExecutePreparedUpdate: ",
                callback,
                new UpdateResult(connection, callback, entry),
                new CompoundCommand(
                        new UpdateExecute(sessionId, params),
                        new QueryExecute(connection.idForAutoId(), connection.nextId())));
    }

    Request<Void> executeCloseStatement(int sessionId,
                                        DbCallback<Void> callback,
                                        StackTraceElement[] entry) {
        return new Request<>(
                "ExecuteCloseStatement: ",
                callback,
                new AnswerNextRequest(connection, entry),
                new CommandClose(sessionId, callback));
    }

    public Request<Connection> createGetAutoIdStatement(
            DbCallback<Connection> completeConnection,
            StackTraceElement[] entry) {
        final int sessionId = connection.idForAutoId();
        String sql = "SELECT SCOPE_IDENTITY() WHERE SCOPE_IDENTITY() IS NOT NULL";
        return new Request<>("Prepare Query: " + sql,
                completeConnection,
                StatementPrepare.createOnlyPassFailure(completeConnection, connection, entry),
                new QueryPrepareCommand(sessionId, sql));
    }

    public Request createCommitStatement(DbCallback<Connection> completeConnection, StackTraceElement[] entry) {
        final int sessionId = connection.idForCommit();
        String sql = "COMMIT";
        return new Request<>("Prepare Query: " + sql,
                completeConnection,
                StatementPrepare.createOnlyPassFailure(completeConnection, connection, entry),
                new QueryPrepareCommand(sessionId, sql));
    }

    public Request createRollbackStatement(DbCallback<Connection> completeConnection, StackTraceElement[] entry) {
        final int sessionId = connection.idForRollback();
        String sql = "ROLLBACK";
        return new Request<>("Prepare Query: " + sql,
                completeConnection,
                StatementPrepare.completeFuture(completeConnection, connection, entry),
                new QueryPrepareCommand(sessionId, sql));
    }

    Request beginTransaction(DbCallback<Void> callback, StackTraceElement[] entry) {
        return new Request<>("Begin Transaction",
                callback,
                new AwaitOk(connection, entry),
                new AutoCommitChangeCommand(AutoCommitChangeCommand.AutoCommit.AUTO_COMMIT_OFF));

    }

    Request commitTransaction(DbCallback<Void> completion, StackTraceElement[] entry) {
        return new Request<>("Commit Transaction", completion,
                new CompleteTransaction(completion, connection, entry),
                new CompoundCommand(
                        new UpdateExecute(connection.idForCommit()),
                        new AutoCommitChangeCommand(AutoCommitChangeCommand.AutoCommit.AUTO_COMMIT_ON)));

    }

    Request rollbackTransaction(DbCallback<Void> completion, StackTraceElement[] entry) {
        return new Request<>("Rollback Transaction",
                completion,
                new CompleteTransaction(completion, connection, entry),
                new CompoundCommand(
                        new UpdateExecute(connection.idForRollback()),
                        new AutoCommitChangeCommand(AutoCommitChangeCommand.AutoCommit.AUTO_COMMIT_ON)));

    }


    static final class ContinueSql<T> extends StatementPrepare<T> {

        private final Request<T> executeStmt;

        public ContinueSql(
                H2Connection connection,
                DbCallback<T> callback,
                StackTraceElement[] entry,
                Request<T> executeStmt) {
            super(callback, connection, entry);
            this.executeStmt = executeStmt;
        }

        @Override
        protected void handleCompletion(H2Connection connection, int paramsCount) {
            if (paramsCount != 0) {
                callback.onComplete(
                        null,
                        DbException.wrap(new DbException("Implementation error: Expect 0 parameters, but got: " + paramsCount), entry));
                connection.cancelBlockedRequest(executeStmt);
            } else {
                connection.forceQueRequest(executeStmt);
            }
        }

        @Override
        public ResultAndState handleException(H2DbException exception) {
            connection.cancelBlockedRequest(executeStmt);
            return super.handleException(exception);
        }
    }


    StatementPrepare<PreparedUpdate> createPrepareUpdate(final DbCallback<PreparedUpdate> resultFuture,
                                                         final int sessionId,
                                                         StackTraceElement[] entry) {
        return new StatementPrepare<PreparedUpdate>(resultFuture, connection, entry) {
            @Override
            protected void handleCompletion(H2Connection connection, int paramsCount) {
                H2PreparedUpdate query = new H2PreparedUpdate(connection, sessionId, paramsCount);
                resultFuture.onComplete(query, null);
            }
        };
    }


    StatementPrepare<PreparedQuery> createPrepareQuery(final DbCallback<PreparedQuery> resultFuture,
                                                       final int sessionId,
                                                       StackTraceElement[] entry) {
        return new StatementPrepare<PreparedQuery>(resultFuture, connection, entry) {
            @Override
            protected void handleCompletion(H2Connection connection, int paramsCount) {
                H2PreparedQuery query = new H2PreparedQuery(connection, sessionId, paramsCount);
                resultFuture.onComplete(query, null);
            }
        };
    }


}
