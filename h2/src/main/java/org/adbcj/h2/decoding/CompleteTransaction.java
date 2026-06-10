package org.adbcj.h2.decoding;

import io.netty.channel.Channel;
import org.adbcj.DbCallback;
import org.adbcj.h2.H2Connection;
import org.adbcj.h2.H2DbException;
import org.adbcj.h2.protocol.StatusCodes;

import java.io.DataInputStream;
import java.io.IOException;


public class CompleteTransaction extends StatusReadingDecoder {
    private final DbCallback<Void> toComplete;

    public CompleteTransaction(DbCallback<Void> toComplete, H2Connection connection, StackTraceElement[] entry) {
        super(connection, entry);
        this.toComplete = toComplete;
    }

    @Override
    protected ResultAndState processFurther(DataInputStream stream, Channel channel, int status) throws IOException {
        StatusCodes.STATUS_OK.expectStatusOrThrow(status);

        final ResultOrWait<Integer> affected = IoUtils.tryReadNextInt(stream, ResultOrWait.Start);
        final ResultOrWait<Boolean> autoCommit = IoUtils.tryReadNextBoolean(stream, affected);
        final ResultOrWait<Integer> okStatus = IoUtils.tryReadNextInt(stream, autoCommit);
        if (okStatus.couldReadResult) {
            StatusCodes.STATUS_OK.expectStatusOrThrow(okStatus.result);
            toComplete.onComplete(null, null);
            return ResultAndState.newState(new AnswerNextRequest(connection, entry));
        } else {
            return ResultAndState.waitForMoreInput(this);
        }
    }

    @Override
    protected void requestFailedContinue(H2DbException exception) {
        assert exception!=null;
        throw exception;
    }
}
