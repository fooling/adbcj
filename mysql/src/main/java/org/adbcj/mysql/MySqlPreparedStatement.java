package org.adbcj.mysql;

import org.adbcj.*;
import org.adbcj.mysql.codec.MySqlRequests;
import org.adbcj.mysql.codec.packets.StatementPreparedEOF;
import org.adbcj.support.CloseOnce;
import org.adbcj.support.DbCompletableFuture;
import org.adbcj.support.DefaultResultEventsHandler;
import org.adbcj.support.DefaultResultSet;

import java.util.concurrent.CompletableFuture;


public class MySqlPreparedStatement implements PreparedQuery, PreparedUpdate {
    private final MySqlConnection connection;
    private final StatementPreparedEOF statementInfo;
    private final CloseOnce closeFuture = new CloseOnce();

    public MySqlPreparedStatement(MySqlConnection connection,
                                  StatementPreparedEOF statementInfo) {
        this.connection = connection;
        this.statementInfo = statementInfo;
    }

    public CompletableFuture execute(Object... params) {
        DbCompletableFuture<Result> future = new DbCompletableFuture<>();
        execute(future, params);
        return future;
    }

    @Override
    public void execute(DbCallback callback, Object... params) {
        executeWithCallback(new DefaultResultEventsHandler(), new DefaultResultSet(), callback, params);
    }

    @Override
    public <T> void executeWithCallback(ResultHandler<T> eventHandler, T accumulator, DbCallback<T> callback, Object... params) {
        connection.checkClosed();
        validateParameters(params);
        StackTraceElement[] entry = connection.strackTraces.captureStacktraceAtEntryPoint();
        connection.failIfQueueFull(
                MySqlRequests.executePreparedQuery(
                        connection,
                        statementInfo,
                        params,
                        eventHandler,
                        accumulator,
                        callback,
                        entry
                ));
    }

    @Override
    public boolean isClosed() {
        return closeFuture.isClose();
    }


    @Override
    public void close(DbCallback<Void> callback) {
        closeFuture.requestClose(callback, () -> {
            if(connection.failIfQueueFull(
                    MySqlRequests.closeStatemeent(
                            connection,
                            statementInfo,
                            (res, error) -> {
                            }
                    ))){
                closeFuture.didClose(null);
            }
        });
    }

    private void validateParameters(Object[] params) {
        if (isClosed()) {
            throw new IllegalStateException("Cannot execute closed statement");
        }
        if (params.length != statementInfo.getParametersTypes().size()) {
            throw new IllegalArgumentException("Expect " + statementInfo.getParametersTypes().size() + " paramenters " +
                    "but got " + params.length + " parameters");
        }
    }
}
