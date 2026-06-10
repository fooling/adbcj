package org.adbcj.mysql.codec;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;


public abstract class ClientRequest {

    protected ClientRequest() {
    }


    public abstract int getLength() throws UnsupportedEncodingException;

	/**
	 * The packet number is sent as a byte so only the least significant byte will be used.
	 *
	 */
	public int getPacketNumber() {
		return 0;
	}

    protected abstract boolean hasPayload();

    public abstract void writeToOutputStream(OutputStream out) throws IOException;


}
