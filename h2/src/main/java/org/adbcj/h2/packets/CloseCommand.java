package org.adbcj.h2.packets;


import org.adbcj.support.SizeConstants;

import java.io.DataOutputStream;
import java.io.IOException;


public final class CloseCommand extends ClientToServerPacket {

    public CloseCommand() {
        super();
    }

    public static final int SESSION_CLOSE = 1;
    @Override
    public void writeToStream(DataOutputStream stream) throws IOException {
        stream.writeInt(SESSION_CLOSE);
    }

    @Override
    public int getLength() {
        return SizeConstants.INT_SIZE;
    }
}
