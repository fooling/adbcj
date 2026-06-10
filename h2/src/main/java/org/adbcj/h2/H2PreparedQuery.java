package org.adbcj.h2;

import org.adbcj.*;


public class H2PreparedQuery extends AbstractStatement implements PreparedQuery {

    H2PreparedQuery(H2Connection connection, int sessionId, int paramsCount) {
        super(connection, sessionId, paramsCount);
    }


    public <T> void executeWithCallback(ResultHandler<T> eventHandler,
                                    T accumulator,
                                    DbCallback<T> callback,
                                    Object... params) {
        connection.checkClosed();
        if (paramsCount != params.length) {
            throw new IllegalArgumentException("Expect " + paramsCount + " parameters, but got: " + params.length);
        }
        StackTraceElement[] entry = connection.stackTraces.captureStacktraceAtEntryPoint();
        final Request request = connection.requestCreator().executeQueryStatement(
                eventHandler,
                accumulator,
                callback,
                entry,
                sessionId,
                params);
        connection.queRequest(request, entry);
    }

}
