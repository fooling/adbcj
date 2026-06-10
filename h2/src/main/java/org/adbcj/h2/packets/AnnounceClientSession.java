package org.adbcj.h2.packets;

import org.adbcj.h2.decoding.IoUtils;
import org.adbcj.h2.protocol.CommandCodes;
import org.adbcj.support.SizeConstants;

import java.io.DataOutputStream;
import java.io.IOException;


public class AnnounceClientSession extends ClientToServerPacket {


    private final String sessionId;

    public AnnounceClientSession(String sessionId) {
        super();
        this.sessionId = sessionId;
    }

    @Override
    public void writeToStream(DataOutputStream stream) throws IOException {
        stream.writeInt(CommandCodes.SESSION_SET_ID.getCommandValue());
        IoUtils.writeString(stream, sessionId);
    }

    @Override
    public int getLength() {
        return SizeConstants.INT_SIZE +  // request type
                SizeConstants.sizeOf(sessionId)+
                0;
    }
}
