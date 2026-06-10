package org.adbcj.mysql.codec.decoding;

import java.io.IOException;

import org.adbcj.mysql.MySqlConnection;
import org.adbcj.mysql.codec.BoundedInputStream;
import org.adbcj.mysql.codec.packets.ErrorResponse;
import org.adbcj.mysql.codec.packets.OkResponse;

import io.netty.channel.Channel;

public abstract class ResponseStart extends DecoderState {
	
    protected final MySqlConnection connection;

    protected ResponseStart(MySqlConnection connection) {
        this.connection = connection;
    }

    @Override
    public ResultAndState parse(int length, int packetNumber, BoundedInputStream in, Channel channel) throws IOException {

        int fieldCount = in.read();
        if (fieldCount == RESPONSE_OK) {
            return handleOk(OkResponse.interpretAsRegularOk(length, packetNumber, in));
        }
        if (fieldCount == RESPONSE_ERROR) {
            return handleError(decodeErrorResponse(in, length, packetNumber));
        }
        if (fieldCount == RESPONSE_EOF) {
            throw new IllegalStateException("Did not expect an EOF response from the server");
        }
        return parseAsResult(length, packetNumber, in, fieldCount);
    }

    protected abstract ResultAndState handleError(ErrorResponse errorResponse);

    protected abstract ResultAndState handleOk(OkResponse.RegularOK regularOK);

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }

    protected ResultAndState parseAsResult(int length,
                                         int packetNumber,
                                         BoundedInputStream in,
                                         int fieldCount) throws IOException {
        throw new IllegalStateException("This state: "+this+" does not expect a result which can be interpreted as " +
                "query result");
    }
    
}
