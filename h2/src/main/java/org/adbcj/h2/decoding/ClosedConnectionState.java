package org.adbcj.h2.decoding;

import org.adbcj.DbCallback;
import org.adbcj.h2.H2Connection;
import org.adbcj.h2.H2DbException;
import io.netty.channel.Channel;

import java.io.DataInputStream;
import java.io.IOException;


public final class ClosedConnectionState extends StatusReadingDecoder {
    private final DbCallback<Void> finishedClose;

    ClosedConnectionState(DbCallback<Void> finishedClose, H2Connection connection, StackTraceElement[] entry) {
        super(connection, entry);
        this.finishedClose = finishedClose;
    }

    @Override
    protected ResultAndState processFurther(DataInputStream stream, Channel channel, int status) throws IOException {
        finishedClose.onComplete(null, null);
        return ResultAndState.waitForMoreInput(this);
    }

    @Override
    protected void requestFailedContinue(H2DbException exception) {
        finishedClose.onComplete(null, exception);
    }
}
