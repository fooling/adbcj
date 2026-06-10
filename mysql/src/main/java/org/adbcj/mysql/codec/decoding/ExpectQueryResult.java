package org.adbcj.mysql.codec.decoding;

import org.adbcj.DbCallback;
import org.adbcj.DbException;
import org.adbcj.ResultHandler;
import org.adbcj.mysql.MySqlConnection;
import org.adbcj.mysql.codec.*;
import org.adbcj.mysql.codec.packets.ErrorResponse;
import org.adbcj.mysql.codec.packets.OkResponse;

import java.io.IOException;
import java.util.ArrayList;


public class ExpectQueryResult<T> extends ResponseStart {
    private final ResultHandler<T> eventHandler;
    private final T accumulator;
    protected final DbCallback<T> callback;
    private Row.RowDecodingType decodingType;
    private final StackTraceElement[] entry;
    private DbException failure;

    public ExpectQueryResult(
            MySqlConnection connection,
            Row.RowDecodingType decodingType,
            ResultHandler<T> eventHandler,
            T accumulator,
            DbCallback<T> callback,
            StackTraceElement[] entry) {
        super(connection);
        this.decodingType = decodingType;
        this.eventHandler = eventHandler;
        this.accumulator = accumulator;
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
        throw new Error("Not supported for query results");
    }

    @Override
    protected ResultAndState parseAsResult(int length, int packetNumber, BoundedInputStream in, int fieldCount) throws IOException {
        // Get the number of fields. The largest this can be is a 24-bit
        // integer so cast to int is ok
        int expectedFieldPackets = (int) IoUtils.readBinaryLengthEncoding(in, fieldCount);
        logger.trace("Field count {}", expectedFieldPackets);

        Long extra = null;
        if (in.getRemaining() > 0) {
            extra = IoUtils.readBinaryLengthEncoding(in);
        }
        try{
            eventHandler.startFields(accumulator);
        } catch (Exception any){
            failure = DbException.wrap(any, entry);
        }
        return result(new FieldDecodingState(
                        connection,
                        decodingType,
                        expectedFieldPackets,
                        new ArrayList<>(),
                        eventHandler,
                        accumulator,
                        callback,
                        entry,
                        failure),
                new ResultSetResponse(length, packetNumber, expectedFieldPackets, extra));
    }
}
