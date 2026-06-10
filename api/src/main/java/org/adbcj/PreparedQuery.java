package org.adbcj;

import org.adbcj.support.DbCompletableFuture;
import org.adbcj.support.DefaultResultEventsHandler;
import org.adbcj.support.DefaultResultSet;

import java.util.concurrent.CompletableFuture;

public interface PreparedQuery extends PreparedStatement {

    default CompletableFuture<ResultSet> execute(Object... params){
        DefaultResultEventsHandler handler = new DefaultResultEventsHandler();
        DefaultResultSet acc = new DefaultResultSet();
        DbCompletableFuture<DefaultResultSet> future = new DbCompletableFuture<>();
        executeWithCallback(handler, acc, future, params);
        return (CompletableFuture)future;
    }

    default <T> CompletableFuture<T> executeWithCallback(
            ResultHandler<T> handler,
            T accumulator,
            Object... params){
        DbCompletableFuture<T> future = new DbCompletableFuture<>();
        executeWithCallback(handler, accumulator, future, params);
        return future;
    }

    <T> void executeWithCallback(
            ResultHandler<T> handler,
            T accumulator,
            DbCallback<T> callback,
            Object... params);
}
