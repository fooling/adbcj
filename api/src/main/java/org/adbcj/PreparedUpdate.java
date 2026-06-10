package org.adbcj;

import org.adbcj.support.DbCompletableFuture;

import java.util.concurrent.CompletableFuture;

public interface PreparedUpdate extends PreparedStatement {

    default CompletableFuture<Result> execute(Object... params) {
        DbCompletableFuture<Result> future = new DbCompletableFuture<>();
        execute(future, params);
        return future;
    }

    void execute(DbCallback<Result> callback, Object... params);
}
