package org.adbcj.mysql.codec.decoding;

import org.adbcj.mysql.codec.BoundedInputStream;
import org.adbcj.mysql.MySqlConnection;
import org.adbcj.mysql.codec.MySqlRequest;
import io.netty.channel.Channel;

import java.io.IOException;


public class AcceptNextResponse extends DecoderState {
    private final MySqlConnection connection;

    public AcceptNextResponse(MySqlConnection connection) {
        this.connection = connection;
    }

    @Override
    public ResultAndState parse(int length, int packetNumber, BoundedInputStream in, Channel channel) throws IOException {
        final MySqlRequest request = connection.dequeRequest();
        if(logger.isDebugEnabled()){
            logger.debug("Start parsing request: {}",request);
        }
        return request.startState.parse(length, packetNumber, in, channel);
    }
}
