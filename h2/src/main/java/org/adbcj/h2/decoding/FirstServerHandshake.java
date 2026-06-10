package org.adbcj.h2.decoding;

import org.adbcj.Connection;
import org.adbcj.DbCallback;
import org.adbcj.DbException;
import org.adbcj.h2.H2Connection;
import org.adbcj.h2.H2DbException;
import org.adbcj.h2.packets.AnnounceClientSession;
import org.adbcj.support.SizeConstants;
import org.adbcj.h2.protocol.StatusCodes;
import io.netty.channel.Channel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

import java.io.DataInputStream;
import java.io.IOException;


class FirstServerHandshake extends StatusReadingDecoder {
	final static AttributeKey<Integer> ATTR_CLI_VER = AttributeKey.valueOf("h2.clientVersion");
	
    private final DbCallback<Connection> callback;
    private final H2Connection connection;

    FirstServerHandshake(DbCallback<Connection> callback, H2Connection connection, StackTraceElement[] entry) {
        super(connection, entry);
        this.callback = callback;
        this.connection = connection;
    }


    protected ResultAndState processFurther(DataInputStream input,
                                            Channel channel,
                                            int status) throws IOException {
        StatusCodes.STATUS_OK.expectStatusOrThrow(status);
        if (input.available() < SizeConstants.INT_SIZE) {
            return ResultAndState.waitForMoreInput(this);
        }
        int clientVersion = input.readInt();
        if (clientVersion != Constants.TCP_PROTOCOL_VERSION_12) {
            throw new DbException("This version only supports version " + Constants.TCP_PROTOCOL_VERSION_12
                    + ", but server wants " + clientVersion);
        }
        // cache client-version in channel for following usage.
        // @since 2017-09-24 little-pan
        final Attribute<Integer> attr = channel.attr(ATTR_CLI_VER);
        attr.set(clientVersion);
        channel.writeAndFlush(new AnnounceClientSession(connection.getSessionId()));
        return ResultAndState.newState(new SessionIdReceived(callback, connection, entry));
    }

    @Override
    protected void requestFailedContinue(H2DbException exception) {
        callback.onComplete(null, exception);
    }
}

class SessionIdReceived extends StatusReadingDecoder {
    private final DbCallback<Connection> callback;

    SessionIdReceived(DbCallback<Connection> callback, H2Connection connection, StackTraceElement[] entry) {
        super(connection, entry);
        this.callback = callback;
    }

    @Override
    protected ResultAndState processFurther(DataInputStream input, Channel channel, int status) throws IOException {
        StatusCodes.STATUS_OK.expectStatusOrThrow(status);
        // response auto-commit only after version 15.
        // @since 2017-09-24 little-pan
        final ResultOrWait<Boolean> autoCommit;
        autoCommit = IoUtils.tryReadNextBoolean(input, ResultOrWait.Start);

        if(autoCommit.couldReadResult){
            connection.forceQueRequest(connection.requestCreator().createGetAutoIdStatement(callback, entry));
            connection.forceQueRequest(connection.requestCreator().createCommitStatement(callback, entry));
            connection.forceQueRequest(connection.requestCreator().createRollbackStatement(callback, entry));
            return ResultAndState.newState(new AnswerNextRequest(connection, entry));
        } else{
            return ResultAndState.waitForMoreInput(this);
        }
    }

    @Override
    protected void requestFailedContinue(H2DbException exception) {
        this.callback.onComplete(null, exception);
    }
}
