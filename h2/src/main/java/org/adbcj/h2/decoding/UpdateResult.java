package org.adbcj.h2.decoding;

import io.netty.channel.Channel;
import org.adbcj.*;
import org.adbcj.h2.H2Connection;
import org.adbcj.h2.H2DbException;
import org.adbcj.h2.H2Result;
import org.adbcj.h2.protocol.StatusCodes;
import org.adbcj.support.DefaultResultEventsHandler;
import org.adbcj.support.DefaultResultSet;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;


public class UpdateResult extends StatusReadingDecoder {
    private final DbCallback<? super Result> resultHandler;

    public UpdateResult(H2Connection connection, DbCallback<? super Result> resultHandler, StackTraceElement[] entry) {
        super(connection, entry);
        this.resultHandler = resultHandler;
    }

    @Override
    protected ResultAndState processFurther(DataInputStream stream, Channel channel, int status) throws IOException {
        StatusCodes.STATUS_OK.expectStatusOrThrow(status);

        final ResultOrWait<Integer> affected = IoUtils.tryReadNextInt(stream, ResultOrWait.Start);
        final ResultOrWait<Boolean> autoCommit = IoUtils.tryReadNextBoolean(stream, affected);
        if (autoCommit.couldReadResult) {
            DefaultResultEventsHandler handler = new DefaultResultEventsHandler();
            DefaultResultSet result = new DefaultResultSet();

            return ResultAndState.newState(
                    new QueryHeader<>(
                            connection,
                            handler,
                            result,
                            (success, failure) -> {
                                if (failure == null) {
                                    H2Result updateResult = new H2Result(success, affected.result.longValue(), new ArrayList<String>());
                                    resultHandler.onComplete(updateResult, null);
                                } else {
                                    resultHandler.onComplete(null, failure);
                                }
                            },
                            entry));
        } else {
            return ResultAndState.waitForMoreInput(this);
        }
    }

    @Override
    protected void requestFailedContinue(H2DbException exception) {
        resultHandler.onComplete(null, exception);
    }
}
