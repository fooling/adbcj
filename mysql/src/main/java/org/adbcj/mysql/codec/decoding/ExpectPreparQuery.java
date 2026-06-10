package org.adbcj.mysql.codec.decoding;

import org.adbcj.DbCallback;
import org.adbcj.mysql.codec.BoundedInputStream;
import org.adbcj.mysql.MySqlConnection;
import org.adbcj.mysql.MySqlPreparedStatement;
import org.adbcj.mysql.codec.packets.ErrorResponse;
import org.adbcj.mysql.codec.packets.OkResponse;
import org.adbcj.mysql.codec.packets.PreparedStatementToBuild;
import org.adbcj.mysql.codec.packets.StatementPreparedEOF;
import io.netty.channel.Channel;

import java.io.IOException;


public class ExpectPreparQuery extends DecoderState {
    private final MySqlConnection connection;
    private final DbCallback<MySqlPreparedStatement> callback;
    private final StackTraceElement[] entry;

    public ExpectPreparQuery(
            MySqlConnection connection,
            DbCallback<MySqlPreparedStatement> callback,
            StackTraceElement[] entry) {
        super();
        this.callback = sandboxCallback(callback);
        this.entry = entry;
        this.connection = connection;
    }

    @Override
    public ResultAndState parse(int length, int packetNumber, BoundedInputStream in, Channel channel) throws IOException {
        int fieldCount = in.read();
        if (fieldCount == ResponseStart.RESPONSE_OK) {
            return handlePrepareQuery(length, packetNumber, OkResponse.interpretAsPreparedStatement(length, packetNumber, in));
        }
        if (fieldCount == ResponseStart.RESPONSE_ERROR) {
            return handleError(ResponseStart.decodeErrorResponse(in, length, packetNumber));
        } else {
            throw new IllegalStateException("Did not expect this response from the server");
        }
    }

    private ResultAndState handlePrepareQuery(int length, int packetNumber,
                                              OkResponse.PreparedStatementOK preparedStatement) {
        final PreparedStatementToBuild statement = new PreparedStatementToBuild(length, packetNumber, preparedStatement);
        final DecoderState decoderState = FinishPrepareStatement.create(connection, statement, callback);
        if (decoderState instanceof AcceptNextResponse) {
            final StatementPreparedEOF eof = new StatementPreparedEOF(length, packetNumber, statement);
            callback.onComplete(
                    new MySqlPreparedStatement(connection,
                            eof), null);
            return new ResultAndState(decoderState, eof);
        } else {
            return new ResultAndState(decoderState, preparedStatement);
        }
    }

    private ResultAndState handleError(ErrorResponse errorResponse) {
        callback.onComplete(null, errorResponse.toException(entry));
        return new ResultAndState(new AcceptNextResponse(connection), errorResponse);
    }
}
