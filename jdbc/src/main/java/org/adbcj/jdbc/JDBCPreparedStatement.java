package org.adbcj.jdbc;

import org.adbcj.*;
import org.adbcj.support.CloseOnce;

import java.sql.SQLException;
import java.util.Collections;


abstract class JDBCPreparedStatement<T> implements PreparedStatement {
    protected final java.sql.PreparedStatement sqlStatement;
    protected final JdbcConnection connection;
    private final int paramenterCount;
    private final CloseOnce closer = new CloseOnce();

    JDBCPreparedStatement(JdbcConnection connection, java.sql.PreparedStatement sqlStatement) {
        this.connection = connection;
        this.sqlStatement = sqlStatement;
        synchronized (this.connection.lock) {
            try {
                paramenterCount = sqlStatement.getParameterMetaData().getParameterCount();
            } catch (SQLException e) {
                throw new DbException("Expect that PreparedStatement.getParameterMetaData() works", e);
            }
        }
    }


    void validateParameters(Object[] params) {
        if (params.length != paramenterCount) {
            throw new IllegalArgumentException("Wrong amount of arguments." +
                    "This statement expects " + paramenterCount + " but received " + params.length + " arguments");
        }
    }

    @Override
    public void close(DbCallback<Void> callback) {
        StackTraceElement[] entry = connection.strackTraces.captureStacktraceAtEntryPoint();
        closer.requestClose(callback, () -> connection.queueRequestVoid(callback, entry, java.sql.Connection::close));
    }

    @Override
    public boolean isClosed() {
        return closer.isClose();
    }

    protected void checkClosed() {
        if (isClosed()) {
            throw new DbConnectionClosedException("This statement is closed");
        }
        connection.checkClosed();
    }
}

class JDBCPreparedQuery extends JDBCPreparedStatement<ResultSet> implements PreparedQuery {

    public JDBCPreparedQuery(JdbcConnection connection, java.sql.PreparedStatement sqlStatement) {
        super(connection, sqlStatement);
    }

    @Override
    public <T> void executeWithCallback(ResultHandler<T> eventHandler, T accumulator, DbCallback<T> callback, Object... params) {
        checkClosed();
        validateParameters(params);
        StackTraceElement[] entry = connection.strackTraces.captureStacktraceAtEntryPoint();
        connection.queueRequest(callback, entry, jdbc -> {
            int index = 1;
            synchronized (connection.lock) {
                for (Object param : params) {
                    sqlStatement.setObject(index, param);
                    index++;
                }
                ResultSetCopier.fillResultSet(
                        sqlStatement.executeQuery(),
                        eventHandler,
                        accumulator);
                return accumulator;
            }
        });
    }
}

class JDBCPreparedUpdate extends JDBCPreparedStatement<Result> implements PreparedUpdate {

    public JDBCPreparedUpdate(JdbcConnection connection, java.sql.PreparedStatement sqlStatement) {
        super(connection, sqlStatement);
    }

    @Override
    public void execute(DbCallback<Result> callback, Object... params) {
        checkClosed();
        validateParameters(params);
        StackTraceElement[] entry = connection.strackTraces.captureStacktraceAtEntryPoint();
        connection.queueRequest(callback, entry, jdbc -> {
            int index = 1;
            synchronized (connection.lock) {
                for (Object param : params) {
                    sqlStatement.setObject(index, param);
                    index++;
                }
                int updated = sqlStatement.executeUpdate();
                return new JDBCResult(updated,
                        Collections.emptyList(),
                        sqlStatement.getGeneratedKeys(),
                        (res, failure) -> {
                            if (failure != null) {
                                callback.onComplete(null, failure);
                            }
                        },
                        entry);
            }
        });
    }
}
