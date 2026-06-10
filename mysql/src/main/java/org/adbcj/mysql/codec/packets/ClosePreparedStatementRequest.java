package org.adbcj.mysql.codec.packets;

import org.adbcj.mysql.codec.IoUtils;

import java.io.IOException;
import java.io.OutputStream;


public class ClosePreparedStatementRequest extends CommandRequest {
    private final int statementId;

    public ClosePreparedStatementRequest(int statementId) {
        super(Command.STATEMENT_CLOSE);
        this.statementId = statementId;
    }


    @Override
    public boolean hasPayload() {
        return true;
    }
    @Override
    public int getLength() {
        return 1+4;
    }
    @Override
    protected void writePayLoad(OutputStream out) throws IOException {
        IoUtils.writeInt(out, statementId);
    }

    @Override
    public String toString() {
        return "ClosePreparedStatementRequest{" +
                "statementId=" + statementId +
                '}';
    }
}
