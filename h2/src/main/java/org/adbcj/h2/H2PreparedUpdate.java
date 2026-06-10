package org.adbcj.h2;

import org.adbcj.DbCallback;
import org.adbcj.PreparedUpdate;
import org.adbcj.Result;


public class H2PreparedUpdate extends AbstractStatement implements PreparedUpdate {
    H2PreparedUpdate(H2Connection connection, int sessionId, int paramsCount) {
        super(connection, sessionId, paramsCount);
    }

    @Override
    public void execute(DbCallback<Result> callback, Object... params) {
        connection.checkClosed();
        if(paramsCount!=params.length){
            throw new IllegalArgumentException("Expect "+paramsCount+" parameters, but got: "+params.length);
        }
        StackTraceElement[] entry = connection.stackTraces.captureStacktraceAtEntryPoint();
        final Request request = connection.requestCreator().executeUpdateStatement(sessionId, params, callback, entry);
        connection.queRequest(request, entry);
    }
}
