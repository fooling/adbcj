package org.adbcj.h2.decoding;

import io.netty.channel.Channel;
import org.adbcj.Connection;
import org.adbcj.DbCallback;
import org.adbcj.h2.H2Connection;
import org.adbcj.h2.H2DbException;
import org.adbcj.support.SizeConstants;
import org.adbcj.h2.protocol.StatusCodes;

import java.io.DataInputStream;
import java.io.IOException;


public abstract class StatementPrepare<T> extends StatusReadingDecoder {
    protected final DbCallback<T> callback;

    public StatementPrepare(DbCallback<T> callback, H2Connection connection, StackTraceElement[] entry) {
        super(connection, entry);
        this.callback = callback;
    }

    @Override
    protected ResultAndState processFurther(DataInputStream stream, Channel channel, int status) throws IOException {
        StatusCodes.STATUS_OK.expectStatusOrThrow(status);
        if (stream.available() >= (SizeConstants.BYTE_SIZE + SizeConstants.BYTE_SIZE + SizeConstants.INT_SIZE)) {
            boolean isQuery = IoUtils.readBoolean(stream);
            boolean readonly = IoUtils.readBoolean(stream);
            int paramsCount = stream.readInt();
            handleCompletion(connection, paramsCount);
            return ResultAndState.newState(new AnswerNextRequest(connection, entry));
        } else {
            return ResultAndState.waitForMoreInput(this);
        }
    }

    @Override
    public ResultAndState handleException(H2DbException exception) {
        super.handleException(exception);
        return ResultAndState.newState(new AnswerNextRequest(connection, entry));
    }

    protected abstract void handleCompletion(H2Connection connection, int paramsCount);


    @Override
    protected void requestFailedContinue(H2DbException exception) {
        callback.onComplete(null, exception);
    }

    public static StatementPrepare<Connection> createOnlyPassFailure(
            final DbCallback<Connection> resultFuture,
            final H2Connection connection,
            StackTraceElement[] entry) {
        return new StatementPrepare<Connection>(resultFuture, connection, entry) {
            @Override
            protected void handleCompletion(H2Connection connection, int paramsCount) {
            }
        };
    }

    public static StatementPrepare<Connection> completeFuture(
            final DbCallback<Connection> resultFuture,
            final H2Connection connection,
            StackTraceElement[] entry) {
        return new StatementPrepare<Connection>(resultFuture, connection, entry) {
            @Override
            protected void handleCompletion(H2Connection connection, int paramsCount) {
                resultFuture.onComplete(connection, null);
            }
        };
    }
}
