package org.adbcj;

import org.adbcj.support.DbCompletableFuture;

import java.util.concurrent.CompletableFuture;

public interface AsyncCloseable {

    /**
     * Close this ADBCJ resource, like connection, prepared statement etc.
     *
     * For callback style, use {@see #close()}
     */
    default CompletableFuture<Void> close() {
        DbCompletableFuture<Void> future = new DbCompletableFuture<>();
        close(future);
        return future;
    }

    /**
     * Close this ADBCJ resource, like connection, prepared statement etc
     */
    void close(DbCallback<Void> callback);

}
