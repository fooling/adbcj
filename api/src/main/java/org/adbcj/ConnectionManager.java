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

import java.util.concurrent.CompletableFuture;

/**
 * Manages a set of {@link Connection} instances.  A database connection is established by invoking {@link #connect()}.
 * All the connections managed by this connection manager can be closed using {@link #close(CloseMode)}.
 * <p>
 * The connection manager holds heavy wait resources like connections, thread pools together in one place.
 * You typically create one connection manager for you're application.
 *
 * @author Mike Heath
 */
public interface ConnectionManager extends AsyncCloseable {

    /**
     * Establishes a new database connection.
     * <p>
     * Uses the user name and password given when the connection manager was created
     */
    void connect(DbCallback<Connection> connected);

    /**
     * Future returning version of {@link #connect(DbCallback)}
     *
     * @return a future object that will complete when the database connection has been established or failed.
     */
    default CompletableFuture<Connection> connect() {
        DbCompletableFuture<Connection> future = new DbCompletableFuture<>();
        connect(future);
        return future;
    }

    /**
     * Establishes a new database connection, with the given user and password
     */
    void connect(String user, String password, DbCallback<Connection> connected);

    /**
     * Future returning version of {@link #connect(String, String, DbCallback)}
     *
     * @return a future object that will complete when the database connection has been established or failed.
     */
    default CompletableFuture<Connection> connect(String user, String password) {
        DbCompletableFuture<Connection> future = new DbCompletableFuture<>();
        connect(user, password, future);
        return future;

    }

    /**
     * Closes all the database connections managed by this {@code ConnectionManager} and releases any resources
     * used for managing asynchronous database connections.
     * <p>
     * This will close the connections gracefully {@link CloseMode#CLOSE_GRACEFULLY}. Running operations
     * will finish before the manager is closed. The behavior is the same as calling close(CloseMode.LOSE_GRACEFULLY)
     *
     * @throws DbException if there's an error closing all the database connections
     */
    default void close(DbCallback<Void> callback) {
        close(CloseMode.CLOSE_GRACEFULLY, callback);
    }

    /**
     * Closes all the database connections managed by this {@code ConnectionManager} and releases any resources
     * used for managing asynchronous database connections.
     * <p>
     * Closes according to the given {@link CloseMode}.
     *
     * {@code ConnectionManager} have closed.
     * @throws DbException if there's an error closing all the database connections
     */
    void close(CloseMode mode, DbCallback<Void> callback) throws DbException;

    /**
     * Future returning version of {@link #close(CloseMode, DbCallback)}
     *
     * @return a future object that will complete when all database connections managed by this
     * {@code ConnectionManager} have closed.
     */
    default CompletableFuture<Void> close(CloseMode mode) throws DbException {
        DbCompletableFuture<Void> future = new DbCompletableFuture<>();
        close(mode, future);
        return future;
    }

    /**
     * Indicates if this {@code ConnectionManager} is closed.
     *
     * @return true if this {@code ConnectionManager} is closed, false otherwise.
     */
    boolean isClosed();


}
