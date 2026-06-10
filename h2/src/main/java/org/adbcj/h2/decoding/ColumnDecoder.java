package org.adbcj.h2.decoding;

import io.netty.channel.Channel;
import org.adbcj.DbCallback;
import org.adbcj.DbException;
import org.adbcj.Field;
import org.adbcj.ResultHandler;
import org.adbcj.h2.H2Connection;
import org.adbcj.h2.H2DbException;
import org.adbcj.support.DefaultField;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.List;

import static org.adbcj.h2.decoding.IoUtils.*;


public class ColumnDecoder<T> implements DecoderState {
    private final ResultHandler<T> eventHandler;
    private final T accumulator;
    private final DbCallback<T> callback;
    private final H2Connection connection;
    private final int rows;
    private final int columnsAvailable;
    private final List<Field> columnsBuildUp;
    private final StackTraceElement[] entry;
    private DbException failure;


    public ColumnDecoder(
            H2Connection connection,
            ResultHandler<T> eventHandler,
            T accumulator,
            DbException failure,
            DbCallback<T> callback,
            StackTraceElement[] entry,
            int rows,
            int columnsAvailable,
            List<Field> columnsBuildUp) {
        this.eventHandler = eventHandler;
        this.accumulator = accumulator;
        this.failure = failure;
        this.callback = callback;
        this.entry = entry;
        this.connection = connection;
        this.rows = rows;
        this.columnsAvailable = columnsAvailable;
        this.columnsBuildUp = columnsBuildUp;
    }

    @Override
    public ResultAndState decode(DataInputStream stream, Channel channel) throws IOException {
        ResultOrWait<String> alias = tryReadNextString(stream, ResultOrWait.Start);
        ResultOrWait<String> schemaName = tryReadNextString(stream, alias);
        ResultOrWait<String> tableName = tryReadNextString(stream, schemaName);
        ResultOrWait<String> columnName = tryReadNextString(stream, tableName);
        ResultOrWait<Integer> columnType = tryReadNextInt(stream, columnName);
        ResultOrWait<Long> precision = tryReadNextLong(stream, columnType);
        ResultOrWait<Integer> scale = tryReadNextInt(stream, precision);
        ResultOrWait<Integer> displaySize = tryReadNextInt(stream, scale);
        ResultOrWait<Boolean> autoIncrement = tryReadNextBoolean(stream, displaySize);
        ResultOrWait<Integer> nullable = tryReadNextInt(stream, autoIncrement);

        if (nullable.couldReadResult) {
            final DefaultField field = new DefaultField(columnsBuildUp.size(),
                    "",
                    schemaName.result,
                    tableName.result,
                    columnName.result,
                    H2Types.typeCodeToType(columnType.result).getType(),
                    alias.result,
                    columnName.result,
                    precision.result.intValue(),
                    scale.result,
                    autoIncrement.result,
                    false,
                    false,
                    1 == nullable.result,
                    true, true, "");

            try{
                eventHandler.field(field, accumulator);
            } catch (Exception any){
                failure = DbException.attachSuppressedOrWrap(any, entry, failure);
            }
            columnsBuildUp.add(field);
            if ((columnsBuildUp.size()) == columnsAvailable) {
                try{
                    eventHandler.endFields(accumulator);
                } catch (Exception any){
                    failure = DbException.attachSuppressedOrWrap(any, entry, failure);
                }
                return goToRowParsing();
            } else {
                return ResultAndState.newState(this);
            }
        } else {
            return ResultAndState.waitForMoreInput(this);
        }
    }

    @Override
    public ResultAndState handleException(H2DbException exception) {
        callback.onComplete(null, exception);
        return ResultAndState.newState(new AnswerNextRequest(connection, entry));
    }

    private ResultAndState goToRowParsing() {
        if (rows == 0) {
            eventHandler.startResults(accumulator);
            eventHandler.endResults(accumulator);
            callback.onComplete(accumulator, null);
            return ResultAndState.newState(new AnswerNextRequest(connection, entry));
        } else {
            return ResultAndState.newState(
                    new RowDecoder<T>(
                            connection,
                            eventHandler,
                            accumulator,
                            failure,
                            callback,
                            entry,
                            columnsBuildUp,
                            rows,
                            0)
            );

        }
    }
}
