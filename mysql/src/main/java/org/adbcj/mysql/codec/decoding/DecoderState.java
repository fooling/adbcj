package org.adbcj.mysql.codec.decoding;

import org.adbcj.DbCallback;
import org.adbcj.DbException;
import org.adbcj.mysql.codec.BoundedInputStream;
import org.adbcj.mysql.codec.IoUtils;
import org.adbcj.mysql.codec.ServerStatus;
import org.adbcj.mysql.codec.packets.EofResponse;
import org.adbcj.mysql.codec.packets.ErrorResponse;
import org.adbcj.mysql.codec.packets.ServerPacket;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public abstract class DecoderState {
	
    protected static final Logger logger = LoggerFactory.getLogger(DecoderState.class);
    
    public static final int RESPONSE_ERROR = 0xff;
    public static final int RESPONSE_EOF   = 0xfe;
    public static final int RESPONSE_OK    = 0x00;

    public abstract ResultAndState parse(int length,
                                         int packetNumber,
                                         BoundedInputStream in, Channel channel) throws IOException;


    public ResultAndState result( DecoderState newState,ServerPacket result){
        return new ResultAndState(newState,result);
    }
    
    protected EofResponse decodeEofResponse(InputStream in, int length, int packetNumber, EofResponse.Type type) throws IOException {
        int warnings = IoUtils.readUnsignedShort(in);
        Set<ServerStatus> serverStatus = IoUtils.readEnumSetShort(in, ServerStatus.class);

        return new EofResponse(length, packetNumber, warnings, serverStatus, type);
    }
    
    public static ErrorResponse decodeErrorResponse(BoundedInputStream in, int length, int packetNumber) throws IOException {
    	// The Payload of an ERR Packet
    	//Type	      Name	           Description
    	//-----------------------------------------------------------
    	//int<1>	  header	       0xFF ERR packet header
    	//int<2>	  error_code	   error-code
    	//if capabilities & CLIENT_PROTOCOL_41 {
    	//string[1]	  sql_state_marker # marker of the SQL state
    	//string[5]	  sql_state	       SQL state
    	//}
    	//string<EOF> error_message	   human readable error message
        final int errorNumber = IoUtils.readUnsignedShort(in);
        final boolean hasMarker;
        in.mark(Integer.MAX_VALUE);
        try {
        	hasMarker = ('#' == in.read());
        } finally {
        	in.reset();
        }
        final Charset CHARSET = StandardCharsets.UTF_8;
        final String sqlState, message;
        if (hasMarker) {
        	in.read(); // Throw away sqlstate marker
        	// fixbug: sql_state string[5] as null-terming-string.
            // @since 2017-08-27 little-pan
            //String sqlState = IoUtils.readNullTerminatedString(in, CHARSET);
        	sqlState = IoUtils.readFixedLengthString(in, 5, CHARSET);
        	message  = IoUtils.readNullTerminatedString(in, CHARSET);
        } else {
        	// Prev-4.1's message, still can be output 
        	//in newer versions(e.g 'Too many connections')
        	// @since 2017-09-01 little-pan
        	sqlState = "HY000";
        	message  = IoUtils.readFixedLengthString(in, length - 3, CHARSET);
        }
        return new ErrorResponse(length, packetNumber, errorNumber, sqlState, message);
    }

    /**
     * Sand-boxing the DbCallback for protecting ADBCJ kernel from being destroyed when the exception
     * occurs in user DbCallback.onComplete() code.
     *
     * @param cb the database callback
     * @return the sand-boxed callback
     * @since 2017-09-02 little-pan
     */
    public static <T> DbCallback<T> sandboxCallback(final DbCallback<T> cb){
    	if(cb.getClass() == SandboxDbCallback.class) {
    		return cb;
    	}
    	return (new SandboxDbCallback<T>(cb));
    }

    static class SandboxDbCallback<T> implements DbCallback<T> {
        final DbCallback<T> callback;

        SandboxDbCallback(final DbCallback<T> callback) {
            this.callback = callback;
        }

        @Override
        public void onComplete(final T result, final DbException failure) {
            try {
                callback.onComplete(result, failure);
            } catch (final Throwable cause) {
                logger.warn("Uncaught exception in DbCallback", cause);
            }
        }

    }
    
}

