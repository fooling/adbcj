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
import org.adbcj.support.AbstractConnectionManager;
import org.adbcj.support.NoArgFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class JdbcConnectionManager extends AbstractConnectionManager implements ConnectionManager {
    private final ExecutorService executorService;

    private final Object lock = this;
    private final JDBCConnectionProvider connectionProvider;
    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcConnectionManager.class);


    public JdbcConnectionManager(JDBCConnectionProvider connectionProvider,
                                 Map<String, String> properties) {
        super(properties);
        this.executorService = createPool();
        this.connectionProvider = connectionProvider;

        if (useConnectionPool) {
            throw new DbException("JDBC connection provider does not support a connection pool." +
                    "The JDBC-ADBCJ connection bridge is for development purpose, to validate that ADBCJ returns the same results as JDBC");
        }
    }

    @Override
    public void connect(DbCallback<Connection> callback) {
        connect(callback, () -> {
            try {
                return connectionProvider.getConnection();
            } catch (SQLException e) {
                throw DbException.wrap(e);
            }
        });
    }

    @Override
    public void connect(final String user, final String password, DbCallback<Connection> callback) {
        connect(callback, () -> {
            try {
                return connectionProvider.getConnection(user, password);
            } catch (SQLException e) {
                throw DbException.wrap(e);
            }
        });
    }


    private void connect(
            DbCallback<Connection> callback,
            final NoArgFunction<java.sql.Connection> connectionGetter) throws DbException {
        LOGGER.warn("JDBC to ADBCJ is not intended for production use!");
        if (isClosed()) {
            throw new DbException("This connection manager is closed");
        }
        StackTraceElement[] entry = getStackTracingOption().captureStacktraceAtEntryPoint();
        executorService.execute(() -> {
            try {
                java.sql.Connection jdbcConnection = connectionGetter.apply();
                JdbcConnection connection = new JdbcConnection(
                        JdbcConnectionManager.this,
                        jdbcConnection,
                        getExecutorService(),
                        maxQueueLength(),
                        getStackTracingOption());
                synchronized (lock) {
                    if (isClosed()) {
                        connection.close(CloseMode.CANCEL_PENDING_OPERATIONS, (res, error) -> callback.onComplete(null, new DbException("Connection manager closed", error, entry)));
                    } else {
                        addConnection(connection);
                        callback.onComplete(connection, null);
                    }
                }
            } catch (Throwable e) {
                DbException ex = DbException.wrap(e);
                callback.onComplete(null, ex);
            }
        });
    }

    @Override
    protected void doCloseConnection(Connection connection, CloseMode mode, DbCallback<Void> callback) {
        connection.close(mode, callback);
    }

    @Override
    protected void doClose(DbCallback<Void> callback, StackTraceElement[] entry) {
        callback.onComplete(null, null);
    }


    void closedConnection(org.adbcj.jdbc.JdbcConnection jdbcConnection) {
        removeConnection(jdbcConnection);
    }

    private ExecutorService getExecutorService() {
        return executorService;
    }


    @Override
    public String toString() {
        return "JdbcConnectionManager with" + connectionProvider.toString();
    }

    private static ExecutorService createPool() {
        return Executors.newCachedThreadPool(new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = Executors.defaultThreadFactory().newThread(r);
                thread.setName("ADBC to JDBC bridge " + threadNumber.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        });
    }

}
