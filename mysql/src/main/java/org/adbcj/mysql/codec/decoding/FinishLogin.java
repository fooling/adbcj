package org.adbcj.mysql.codec.decoding;

import org.adbcj.Connection;
import org.adbcj.DbCallback;
import org.adbcj.DbException;
import org.adbcj.mysql.MySqlConnection;
import org.adbcj.mysql.codec.packets.ErrorResponse;
import org.adbcj.mysql.codec.packets.OkResponse;

public class FinishLogin extends ResponseStart {
    private final DbCallback<Connection> connected;
    private final StackTraceElement[] entry;

    public FinishLogin(DbCallback<Connection> connected, StackTraceElement[] entry, MySqlConnection connectionToBuild) {
        super(connectionToBuild);
        this.connected = sandboxCallback(connected);
        this.entry = entry;
    }

    @Override
    protected ResultAndState handleError(ErrorResponse errorResponse) {
        connected.onComplete(null, DbException.wrap(errorResponse.toException(entry), entry));
        return new ResultAndState(acceptNextResponse(), errorResponse);
    }

    @Override
    protected ResultAndState handleOk(OkResponse.RegularOK regularOK) {
        connected.onComplete(connection, null);
        return new ResultAndState(acceptNextResponse(), regularOK);
    }

    protected AcceptNextResponse acceptNextResponse() {
        return new AcceptNextResponse(connection);
    }
}
