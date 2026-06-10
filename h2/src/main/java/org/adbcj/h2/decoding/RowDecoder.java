package org.adbcj.h2.decoding;

import io.netty.channel.Channel;
import org.adbcj.*;
import org.adbcj.h2.H2Connection;
import org.adbcj.h2.H2DbException;
import org.adbcj.h2.protocol.ReadUtils;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.List;


public class RowDecoder<T> implements DecoderState {
    private final ResultHandler<T> eventHandler;
    private final T accumulator;
    private final DbCallback<T> callback;
    private final H2Connection connection;
    private final List<Field> fields;
    private final int availableRows;
    private final int rowToRead;
    private final StackTraceElement[] entry;
    private DbException failure;


    public RowDecoder(
            H2Connection connection,
            ResultHandler<T> eventHandler,
            T accumulator,
            DbException failure,
            DbCallback<T> resultFuture,
            StackTraceElement[] entry,
            List<Field> fields,
            int availableRows,
            int rowToRead) {
        this.eventHandler = eventHandler;
        this.accumulator = accumulator;
        this.failure = failure;
        this.callback = resultFuture;
        this.connection = connection;
        this.fields = fields;
        this.availableRows = availableRows;
        this.rowToRead = rowToRead;
        this.entry = entry;
    }

    @Override
    public ResultAndState decode(DataInputStream stream, Channel channel) throws IOException {
        final ResultOrWait<Boolean> row = IoUtils.tryReadNextBoolean(stream, ResultOrWait.Start);
        if (0 == rowToRead) {
            try {
                eventHandler.startResults(accumulator);
            } catch (Exception any) {
                failure = DbException.wrap(any, entry);
            }
        }
        if (row.couldReadResult && !row.result) {
            return finishResultRead();
        }
        return decodeRow(stream, row);

    }

    private ResultAndState decodeRow(DataInputStream stream, ResultOrWait row) throws IOException {
        ResultOrWait<Value> lastValue = (ResultOrWait) row;
        ResultOrWait<Value> values[] = new ResultOrWait[fields.size()];
        for (int i = 0; i < fields.size(); i++) {
            final ResultOrWait<Integer> type = IoUtils.tryReadNextInt(stream, lastValue);
            lastValue = ReadUtils.tryReadValue(stream, type);
            values[i] = lastValue;
        }
        if (lastValue.couldReadResult) {
            try {
                eventHandler.startRow(accumulator);
                for (ResultOrWait<Value> value : values) {
                    eventHandler.value(value.result, accumulator);
                }
                eventHandler.endRow(accumulator);
            } catch (Exception any) {
                failure = DbException.attachSuppressedOrWrap(any, entry, failure);
            }

            if ((rowToRead + 1) == availableRows) {
                return finishResultRead();
            } else {
                return ResultAndState.newState(
                        new RowDecoder<T>(
                                connection,
                                eventHandler,
                                accumulator,
                                failure,
                                callback,
                                entry,
                                fields,
                                availableRows,
                                rowToRead + 1)
                );
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

    private ResultAndState finishResultRead() {
        try {
            eventHandler.endResults(accumulator);
        } catch (Exception any) {
            failure = DbException.attachSuppressedOrWrap(any, entry, failure);
        }
        if (failure == null) {
            callback.onComplete(accumulator, null);
        } else {
            callback.onComplete(null, failure);

        }
        return ResultAndState.newState(new AnswerNextRequest(connection, entry));
    }

}
