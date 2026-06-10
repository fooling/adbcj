package org.adbcj.h2.packets;

import org.adbcj.DbCallback;
import org.adbcj.support.SizeConstants;

import java.io.DataOutputStream;
import java.io.IOException;


public class CommandClose extends ClientToServerPacket {
    public static final int COMMAND_CLOSE = 4;
    private int id;
    // Some close requests have no response, so we close as soon as we've sent the command
    private final DbCallback<Void> optionalCloseOnSent;

    public CommandClose(int id) {
        super();
        this.id = id;
        this.optionalCloseOnSent = null;
    }
    public CommandClose(int id, DbCallback<Void> optionalCloseOnSent) {
        super();
        this.id = id;
        this.optionalCloseOnSent = optionalCloseOnSent;
    }

    @Override
    public void writeToStream(DataOutputStream stream) throws IOException {
        stream.writeInt(COMMAND_CLOSE);
        stream.writeInt(id);
        if(null!=optionalCloseOnSent){
            optionalCloseOnSent.onComplete(null,null);
        }
    }

    @Override
    public int getLength() {
        return SizeConstants.INT_SIZE + // command close
                SizeConstants.INT_SIZE + // command id
                0;
    }
}
