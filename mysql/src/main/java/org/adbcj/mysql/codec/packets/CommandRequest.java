package org.adbcj.mysql.codec.packets;

import org.adbcj.mysql.codec.ClientRequest;

import java.io.IOException;
import java.io.OutputStream;


public class CommandRequest extends ClientRequest {

    private final Command command;


    public CommandRequest(Command command) {
        super();
        this.command = command;
    }

    public Command getCommand() {
        return command;
    }

    @Override
    public int getLength() {
        return 1;
    }

    @Override
    protected boolean hasPayload() {
        return false;
    }

    @Override
    public final void writeToOutputStream(OutputStream out) throws IOException {
        out.write(command.getCommandCode());
        if (hasPayload()) {
            writePayLoad(out);
        }
    }

    protected void writePayLoad(OutputStream out) throws IOException {

    }

    @Override
    public String toString() {
        return "CommandRequest{" +
                "command=" + command +
                '}';
    }
}

