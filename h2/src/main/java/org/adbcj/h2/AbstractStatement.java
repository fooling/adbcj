package org.adbcj.h2;

import org.adbcj.DbCallback;
import org.adbcj.PreparedStatement;
import org.adbcj.support.CloseOnce;


public class AbstractStatement implements PreparedStatement {
    protected final H2Connection connection;
    protected final int sessionId;
    protected final int paramsCount;
    private final CloseOnce closer = new CloseOnce();

    public AbstractStatement(H2Connection connection, int sessionId, int paramsCount) {
        this.paramsCount = paramsCount;
        this.connection = connection;
        this.sessionId = sessionId;
    }

    @Override
    public boolean isClosed() {
        return closer.isClose();
    }

    @Override
    public void close(DbCallback<Void> callback) {
        synchronized (connection.connectionLock()) {
            StackTraceElement[] entry = connection.stackTraces.captureStacktraceAtEntryPoint();
            closer.requestClose(callback, () -> {
                Request<Void> req = connection.requestCreator().executeCloseStatement(
                        sessionId,
                        (res, error) -> closer.didClose(error),
                        entry);
                connection.forceQueRequest(req);
            });
        }
    }
}
