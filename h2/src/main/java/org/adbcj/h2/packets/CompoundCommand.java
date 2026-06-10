package org.adbcj.h2.packets;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;


public class CompoundCommand extends ClientToServerPacket {
    private final ClientToServerPacket[] commands;

    public CompoundCommand(ClientToServerPacket...commands) {
        super();
        this.commands = commands;
    }

    @Override
    public void writeToStream(DataOutputStream stream) throws IOException {
        for (ClientToServerPacket command : commands) {
            command.writeToStream(stream);
        }
    }

    @Override
    public int getLength() {
        int length = 0;
        for (ClientToServerPacket command : commands) {
            length += command.getLength();
        }
        return length;
    }

    @Override
    public String toString() {
        return "Commands{" + (commands == null ? null : Arrays.asList(commands)) +
                '}';
    }
}
