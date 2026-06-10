package org.adbcj.mysql.codec.decoding;

import org.adbcj.DbCallback;
import org.adbcj.ResultHandler;
import org.adbcj.mysql.MySqlConnection;
import org.adbcj.mysql.codec.packets.OkResponse;
import org.adbcj.support.OneArgFunction;


public class ExpectStatementResult<T> extends ExpectQueryResult<T> {

    public ExpectStatementResult(
            MySqlConnection connection,
            Row.RowDecodingType decodingType,
            ResultHandler<T> eventHandler,
            T accumulator,
            DbCallback<T> callback,
            StackTraceElement[] entry) {
        super(connection, decodingType, eventHandler, accumulator, callback, entry);
    }


    @Override
    protected ResultAndState handleOk(OkResponse.RegularOK regularOK) {
        return ExpectUpdateResult.handleUpdateResult(connection, regularOK, callback, OneArgFunction.ID_FUNCTION);
    }
}
