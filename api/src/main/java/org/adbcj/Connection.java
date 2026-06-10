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
package org.adbcj;

import org.adbcj.support.DbCompletableFuture;
import org.adbcj.support.DefaultResultEventsHandler;
import org.adbcj.support.DefaultResultSet;

import java.util.concurrent.CompletableFuture;

/**
 * Connection to the database.
 * <p>
 * You get a connection via a {@see ConnectionManager}. Like so:
 * <pre>
 *     ConnectionManager connections = ConnectionManagerProvider.createConnectionManager("adbcj:db-specific/url", "user", "password");
 *     Connection connection = connections.connect();
 *
 *     // Then use the connection
 * </pre>
 */
public interface Connection extends AsyncCloseable {
    /**
     * Begin's a transaction using the default transaction isolation level.
     * <p>
     * The transaction is started immediately and all subsequent operations are executed
     * in a transaction, until {@link #commit()} or {@link #rollback()} is called. This call
     * also doesn't block: The communication to establish the transaction are executed in the background
     * before the next call on the connection object.
     */
    default CompletableFuture<Void> beginTransaction() {
        DbCompletableFuture<Void> future = new DbCompletableFuture<>();
        beginTransaction(future);
        return future;
    }

    /**
     * Same as {@link #beginTransaction()}. The callback is called when confirmation arrived for the transaction begin.
     * However, right after calling this method, other commands can be sends and will be part of the transactions
     */
    void beginTransaction(DbCallback<Void> callback);


    default CompletableFuture<Void> commit() {
        DbCompletableFuture<Void> future = new DbCompletableFuture<>();
        commit(future);
        return future;
    }

    /**
     * Commits this transaction.
     * Callback will complete if the transaction has been committed successfully,
     * or contain the error when the commit failed.
     * <p>
     * After calling commit, the connection is again in auto-commit mode.
     */
    void commit(DbCallback<Void> callback);


    default CompletableFuture<Void> rollback() {
        DbCompletableFuture<Void> future = new DbCompletableFuture<>();
        rollback(future);
        return future;
    }


    /**
     * Commits this transaction.
     * The callback completes when the transaction has been rolled back successfully.
     * Rollbacks should not fail, however it it does the future will contain the error.
     * <p>
     * Rolling back a transaction might cancel all requests which have been made for this transaction.
     * <p>
     * After calling rollback, the connection is again in auto-commit mode.
     */
    void rollback(DbCallback<Void> callback);

    /**
     * Indicates whether or not the current session is involved in a transaction.
     *
     * @return true if the session is in a transaction, false otherwise
     */
    boolean isInTransaction();


    default CompletableFuture<ResultSet> executeQuery(String sql) {
        DefaultResultEventsHandler handler = new DefaultResultEventsHandler();
        DefaultResultSet accumulator = new DefaultResultSet();
        DbCompletableFuture<DefaultResultSet> result = new DbCompletableFuture<>();
        executeQuery(sql, handler, accumulator, result);
        return (DbCompletableFuture) result;
    }


    default <T> CompletableFuture<T> executeQuery(
            String sql,
            ResultHandler<T> eventHandler,
            T accumulator) {
        DbCompletableFuture<T> result = new DbCompletableFuture<>();
        executeQuery(sql, eventHandler, accumulator, result);
        return result;
    }

    default void executeQuery(String sql,
                              DbCallback<ResultSet> callback) {
        DefaultResultEventsHandler handler = new DefaultResultEventsHandler();
        DefaultResultSet accumulator = new DefaultResultSet();
        executeQuery(sql, handler, accumulator, (DbCallback) callback);
    }

    <T> void executeQuery(String sql,
                          ResultHandler<T> eventHandler,
                          T accumulator,
                          DbCallback<T> callback);

    default CompletableFuture<Result> executeUpdate(String sql) {
        DbCompletableFuture<Result> future = new DbCompletableFuture<>();
        executeUpdate(sql, future);
        return future;
    }

    void executeUpdate(String sql, DbCallback<Result> callback);

    default CompletableFuture<PreparedQuery> prepareQuery(String sql) {
        DbCompletableFuture<PreparedQuery> future = new DbCompletableFuture<>();
        prepareQuery(sql, future);
        return future;
    }

    void prepareQuery(String sql, DbCallback<PreparedQuery> callback);

    default CompletableFuture<PreparedUpdate> prepareUpdate(String sql) {
        DbCompletableFuture<PreparedUpdate> future = new DbCompletableFuture<>();
        prepareUpdate(sql, future);
        return future;

    }

    void prepareUpdate(String sql, DbCallback<PreparedUpdate> callback);


    /**
     * {@link #close(CloseMode)} with {@link CloseMode#CLOSE_GRACEFULLY}
     */
    default void close(DbCallback<Void> callback) {
        close(CloseMode.CLOSE_GRACEFULLY, callback);
    }

    /**
     * Closes this connection releases its resources.
     * <p>
     * This will close the connection gracefully {@link CloseMode#CLOSE_GRACEFULLY}. Running operations
     * will finish before the manager is closed. However no new requests will be accepted
     *
     * @return a future object that will complete when the connection has closed
     * @throws DbException if there's an error closing all the database connections
     */
    default CompletableFuture<Void> close(CloseMode closeMod) {
        DbCompletableFuture<Void> future = new DbCompletableFuture<>();
        close(closeMod, future);
        return future;
    }

    /**
     * Closes this connection releases its resources.
     * <p>
     * This will close the according to the given {@link CloseMode}.
     *
     * @throws DbException if there's an error closing all the database connections
     */
    void close(CloseMode closeMode, DbCallback<Void> callback);

    boolean isClosed();

    default boolean isOpen() {
        return !isClosed();
    }

    /**
     * Returns the instance of the connection manager that created this connection.
     *
     * @return The connection manager instance that created this connection.
     */
    ConnectionManager getConnectionManager();
}
