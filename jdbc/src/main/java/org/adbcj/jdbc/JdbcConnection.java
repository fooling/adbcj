/*
 *   Copyright (c) 2007 Mike Heath.  All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package org.adbcj.jdbc;

import org.adbcj.*;
import org.adbcj.Connection;
import org.adbcj.support.CloseOnce;
import org.adbcj.support.stacktracing.StackTracingOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.sql.ResultSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class JdbcConnection implements Connection {
    private final static Logger LOGGER = LoggerFactory.getLogger(JdbcConnection.class);

    private final JdbcConnectionManager connectionManager;
    private final java.sql.Connection jdbcConnection;
    private final ExecutorService threadPool;
    private final int maxQueueSize;
    final StackTracingOptions strackTraces;

    final Object lock = new Object();
    private final ArrayDeque<Request> requestQueue;
    private boolean isInTransaction;
    private final CloseOnce closer = new CloseOnce();

    public JdbcConnection(JdbcConnectionManager connectionManager,
                          java.sql.Connection jdbcConnection,
                          ExecutorService threadPool,
                          int maxQueueSize,
                          StackTracingOptions strackTraces) {
        this.connectionManager = connectionManager;
        this.jdbcConnection = jdbcConnection;
        this.threadPool = threadPool;
        this.maxQueueSize = maxQueueSize;
        this.strackTraces = strackTraces;
        this.requestQueue = new ArrayDeque<>(maxQueueSize + 1);
    }


    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }


    @Override
    public void beginTransaction(DbCallback<Void> callback) {
        checkClosed();
        StackTraceElement[] entry = strackTraces.captureStacktraceAtEntryPoint();
        synchronized (lock) {
            if (isInTransaction()) {
                throw new DbException("Cannot begin new transaction. Current transaction needs to be committed or rolled back");
            }
            isInTransaction = true;
            queueRequestVoid(callback, entry, jdbc -> jdbc.setAutoCommit(false));

        }

    }

    @Override
    public void commit(DbCallback<Void> callback) {
        checkClosed();
        StackTraceElement[] entry = strackTraces.captureStacktraceAtEntryPoint();
        synchronized (lock) {
            if (!isInTransaction()) {
                throw new DbException("Cannot commit transaction. A transacition first needs to be started");
            }
            isInTransaction = false;
            queueRequestVoid(callback, entry, jdbc->{
                jdbc.commit();
                jdbc.setAutoCommit(true);
            });
        }
    }

    @Override
    public void rollback(DbCallback<Void> callback) {
        checkClosed();
        StackTraceElement[] entry = strackTraces.captureStacktraceAtEntryPoint();
        synchronized (lock) {
            if (!isInTransaction()) {
                throw new DbException("Cannot rollback transaction. A transacition first needs to be started");
            }
            isInTransaction = false;
            queueRequestVoid(callback, entry, jdbc->{
                jdbc.rollback();
                jdbc.setAutoCommit(true);
            });
        }
    }

    @Override
    public boolean isInTransaction() {
        return isInTransaction;
    }

    @Override
    public <T> void executeQuery(String sql, ResultHandler<T> eventHandler, T accumulator, DbCallback<T> callback) {
        checkClosed();
        StackTraceElement[] entry = strackTraces.captureStacktraceAtEntryPoint();
        synchronized (lock) {
            queueRequest(callback, entry, connection -> {
                try (ResultSet jdbcResult = connection.createStatement().executeQuery(sql)) {
                    ResultSetCopier.fillResultSet(
                            jdbcResult,
                            eventHandler,
                            accumulator);
                    return accumulator;
                }
            });
        }
    }

    @Override
    public void executeUpdate(String sql, DbCallback<Result> callback) {
        checkClosed();
        StackTraceElement[] entry = strackTraces.captureStacktraceAtEntryPoint();
        synchronized (lock) {
            queueRequest(callback, entry, connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
                    List<String> warnings = new ArrayList<>();
                    SQLWarning sqlWarnings = statement.getWarnings();
                    while (sqlWarnings != null) {
                        warnings.add(sqlWarnings.getLocalizedMessage());
                        sqlWarnings = sqlWarnings.getNextWarning();
                    }
                    return new JDBCResult(
                            (long) statement.getUpdateCount(),
                            warnings,
                            statement.getGeneratedKeys(),
                            (result, failure) -> {
                                if(failure!=null){
                                    callback.onComplete(null, failure);
                                }
                            },
                            entry);
                }
            });
        }

    }

    @Override
    public void prepareQuery(String sql, DbCallback<PreparedQuery> callback) {
        checkClosed();
        StackTraceElement[] entry = strackTraces.captureStacktraceAtEntryPoint();
        queueRequest(callback, entry, jdbc-> new JDBCPreparedQuery(JdbcConnection.this, jdbc.prepareStatement(sql)));
    }

    @Override
    public void prepareUpdate(String sql, DbCallback<PreparedUpdate> callback) {
        StackTraceElement[] entry = strackTraces.captureStacktraceAtEntryPoint();
        queueRequest(callback, entry, jdbc ->
                new JDBCPreparedUpdate(
                        JdbcConnection.this,
                        jdbc.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)));

    }

    @Override
    public void close(CloseMode closeMode, DbCallback<Void> callback) throws DbException {
        StackTraceElement[] entry = strackTraces.captureStacktraceAtEntryPoint();
        closer.requestClose(callback, ()->{
            synchronized (lock){
                if(closeMode==CloseMode.CANCEL_PENDING_OPERATIONS){
                    Request pending = requestQueue.poll();
                    while(pending!=null){
                        pending.callback.onComplete(null, new DbConnectionClosedException("This connection is closed", null, entry));
                        pending = requestQueue.poll();
                    }
                }
            }
            queueRequestVoid(callback,entry, jdbc->{
                connectionManager.closedConnection(JdbcConnection.this);
                jdbc.close();
            });
        });
    }

    @Override
    public boolean isClosed() throws DbException {
        return closer.isClose();
    }



    void checkClosed() {
        if (isClosed()) {
            throw new DbConnectionClosedException("This connection is closed");
        }
    }

    interface JdbcJobResult<T> {
        T apply(java.sql.Connection connection) throws SQLException;
    }

    interface JdbcJobVoid {
        void apply(java.sql.Connection connection) throws SQLException;
    }

    void queueRequestVoid(DbCallback<Void> callback, StackTraceElement[] entry, JdbcJobVoid toRun) {
        queueRequest(callback, entry, connection -> {
            toRun.apply(connection);
            return null;
        });
    }

    <T> void queueRequest(DbCallback<T> callback, StackTraceElement[] entry, JdbcJobResult<T> toRun) {
        synchronized (lock) {
            int requestsPending = requestQueue.size();
            if (requestsPending > maxQueueSize) {

                DbException ex = new DbException("To many pending requests. The current maximum is " + maxQueueSize +
                        ". Ensure that your not overloading the database with requests. " +
                        "Also check the " + StandardProperties.MAX_QUEUE_LENGTH + " property");
                callback.onComplete(null, ex);
                return;
            }
            try {
                boolean queueEmpty = requestQueue.isEmpty();
                requestQueue.add(new Request<T>(callback, toRun, entry));

                if(queueEmpty){
                    LOGGER.debug("Queue was empty. Add processing job");
                    threadPool.submit(this::processRequests);
                }
            } catch (Exception any) {
                callback.onComplete(null, DbException.wrap(any, entry));
            }
        }
    }

    private void processRequests() {
        Request request;
        synchronized (lock){
            request = this.requestQueue.poll();
        }

        while(request !=null){
            LOGGER.debug("Process JDBC request");
            synchronized (jdbcConnection) {
                try {
                    Object result = request.toRun.apply(jdbcConnection);
                    request.callback.onComplete(result, null);
                } catch (Exception e) {
                    try{
                        request.callback.onComplete(null, DbException.wrap(e, request.entry));
                    } catch (Exception omg){
                        LOGGER.error("Driver failure: Failed handlinge error", omg);
                    }
                }
            }
            synchronized (lock){
                request = this.requestQueue.poll();
            }
        }
        LOGGER.debug("Dequeue no new job. Stop processing requests for now");
    }

    class Request<T>{
        final DbCallback<T> callback;
        final JdbcJobResult<T> toRun;
        final StackTraceElement[] entry;

        public Request(DbCallback<T> callback, JdbcJobResult<T> toRun, StackTraceElement[] entry) {
            this.callback = callback;
            this.toRun = toRun;
            this.entry = entry;
        }
    }
}