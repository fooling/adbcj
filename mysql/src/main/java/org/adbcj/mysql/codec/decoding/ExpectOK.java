package org.adbcj.mysql.codec.decoding;

import org.adbcj.DbCallback;
import org.adbcj.mysql.MySqlConnection;
import org.adbcj.mysql.codec.packets.ErrorResponse;
import org.adbcj.mysql.codec.packets.OkResponse;


public class ExpectOK<T> extends ResponseStart {

    protected final DbCallback<T> callback;
    private final StackTraceElement[] entry;

    public ExpectOK(MySqlConnection connection, DbCallback<T> callback, StackTraceElement[] entry) {
        super(connection);
        this.callback = sandboxCallback(callback);
        this.entry = entry;
    }

    @Override
    protected ResultAndState handleError(ErrorResponse errorResponse) {
        callback.onComplete(null, errorResponse.toException(entry));
        return new ResultAndState(new AcceptNextResponse(connection), errorResponse);
    }

    @Override
    protected ResultAndState handleOk(OkResponse.RegularOK regularOK) {
        callback.onComplete(null, null);
        return new ResultAndState(new AcceptNextResponse(connection), regularOK);
    }
}
