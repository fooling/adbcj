package org.adbcj.h2.packets;

import java.io.DataOutputStream;
import java.io.IOException;


public abstract class ClientToServerPacket {

    protected ClientToServerPacket() {
    }


    public abstract void writeToStream(DataOutputStream stream) throws IOException;

    public abstract int getLength();
}
