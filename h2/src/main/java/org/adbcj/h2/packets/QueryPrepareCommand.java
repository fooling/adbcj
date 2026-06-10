package org.adbcj.h2.packets;

import org.adbcj.h2.decoding.IoUtils;
import org.adbcj.support.SizeConstants;

import java.io.DataOutputStream;
import java.io.IOException;


public class QueryPrepareCommand extends ClientToServerPacket {
    private static final int SESSION_PREPARE = 0;
    private int id;
    private String sql;

    public QueryPrepareCommand(int id, String sql) {
        super();
        this.id = id;
        this.sql = sql;
    }

    @Override
    public void writeToStream(DataOutputStream stream) throws IOException {
        stream.writeInt(SESSION_PREPARE);
        stream.writeInt(id);
        IoUtils.writeString(stream, sql);
    }

    @Override
    public int getLength() {
        return SizeConstants.sizeOf(SESSION_PREPARE)+
                SizeConstants.sizeOf(id) +
                SizeConstants.sizeOf(sql) +
                0;
    }

    @Override
    public String toString() {
        return "QueryPrepareCommand{" +
                "sql='" + sql + '\'' +
                '}';
    }
}
