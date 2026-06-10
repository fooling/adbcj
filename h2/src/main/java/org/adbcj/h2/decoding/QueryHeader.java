package org.adbcj.h2.decoding;

import io.netty.channel.Channel;
import org.adbcj.DbCallback;
import org.adbcj.DbException;
import org.adbcj.ResultHandler;
import org.adbcj.h2.H2Connection;
import org.adbcj.h2.H2DbException;
import org.adbcj.support.SizeConstants;
import org.adbcj.h2.protocol.StatusCodes;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;


public class QueryHeader<T> extends StatusReadingDecoder {
    private final ResultHandler<T> eventHandler;
    private final T accumulator;
    private final DbCallback<T> callback;
    private DbException failure;

    public QueryHeader(H2Connection connection,
                       ResultHandler<T> eventHandler,
                       T accumulator,
                       DbCallback<T> callback,
                       StackTraceElement[] entry) {
        super(connection, entry);
        this.eventHandler = eventHandler;
        this.accumulator = accumulator;
        this.callback = callback;
    }

    @Override
    protected ResultAndState processFurther(final DataInputStream stream,
                                            Channel channel,
                                            int status) throws IOException {
        StatusCodes.STATUS_OK.expectStatusOrThrow(status);

        if (stream.available() < (SizeConstants.INT_SIZE + SizeConstants.INT_SIZE)) {
            return ResultAndState.waitForMoreInput(this);
        } else {
            int columnCount = stream.readInt();
            int rowCount = stream.readInt();
            try{
                eventHandler.startFields(accumulator);
            } catch (Exception any){
                failure = DbException.wrap(any, entry);
            }
            return ResultAndState.newState(
                    new ColumnDecoder<T>(
                            connection,
                            eventHandler,
                            accumulator,
                            failure,
                            callback,
                            entry,
                            rowCount,
                            columnCount,
                            new ArrayList<>())
            );
        }

    }

    @Override
    protected void requestFailedContinue(H2DbException exception) {
        callback.onComplete(null, exception);
    }
}

