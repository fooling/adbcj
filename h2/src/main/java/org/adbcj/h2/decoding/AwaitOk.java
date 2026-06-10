package org.adbcj.h2.decoding;

import io.netty.channel.Channel;
import org.adbcj.h2.H2Connection;
import org.adbcj.h2.H2DbException;

import java.io.DataInputStream;
import java.io.IOException;


public class AwaitOk extends StatusReadingDecoder {
    public AwaitOk(H2Connection connection, StackTraceElement[] entry) {
        super(connection, entry);
    }

    @Override
    protected ResultAndState processFurther(DataInputStream stream, Channel channel, int status) throws IOException {
        return ResultAndState.newState(new AnswerNextRequest(connection, entry));
    }

    @Override
    protected void requestFailedContinue(H2DbException exception) {
        throw exception;
    }
}

