package org.adbcj.h2.decoding;

import org.adbcj.DbCallback;
import org.adbcj.h2.H2Connection;
import org.adbcj.h2.H2DbException;
import org.adbcj.h2.protocol.StatusCodes;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;

import java.io.DataInputStream;
import java.io.IOException;


public class CloseConnection extends StatusReadingDecoder {
    private final DbCallback<Void> finishedClose;

    public CloseConnection(H2Connection connection, DbCallback<Void> finishedClose, StackTraceElement[] entry) {
        super(connection, entry);
        this.finishedClose = finishedClose;
    }

    @Override
    protected ResultAndState processFurther(DataInputStream stream, Channel channel, int status) throws IOException {
        StatusCodes.STATUS_OK.expectStatusOrThrow(status);
        channel.close().addListener((ChannelFutureListener) future -> finishedClose.onComplete(null, null));
        return ResultAndState.newState(new ClosedConnectionState(finishedClose, connection, entry));
    }

    @Override
    protected void requestFailedContinue(H2DbException exception) {
        finishedClose.onComplete(null, exception);
    }
}
