package org.adbcj.h2.packets;

import org.adbcj.support.SizeConstants;

import java.io.DataOutputStream;
import java.io.IOException;


public class QueryExecute extends ClientToServerPacket {
    public static final int COMMAND_EXECUTE_QUERY = 2;
    public static final int RESULT_CLOSE = 7;
    private static final Object[] NO_PARAMS = new Object[0];
    private int id;
    private int queryId;
    private final Object[] params;

    public QueryExecute(int id, int queryId, Object[] params) {
        super();
        this.id = id;
        this.queryId = queryId;
        this.params = params;
    }
    public QueryExecute(int id, int queryId) {
        super();
        this.id = id;
        this.queryId = queryId;
        this.params = NO_PARAMS;
    }

    @Override
    public void writeToStream(DataOutputStream stream) throws IOException {
        stream.writeInt(COMMAND_EXECUTE_QUERY);
        stream.writeInt(id);
        stream.writeInt(queryId);
        stream.writeInt(Integer.MAX_VALUE); // max rows size
        stream.writeInt(Integer.MAX_VALUE); // fetch size
        ParametersSerialisation.writeParams(stream, params);
        stream.writeInt(RESULT_CLOSE);
        stream.writeInt(queryId);
    }

    @Override
    public int getLength() {
        return SizeConstants.INT_SIZE + // Query command
                SizeConstants.INT_SIZE + // command id
                SizeConstants.INT_SIZE + // query id
                SizeConstants.INT_SIZE + // max rows size
                SizeConstants.INT_SIZE + // fetch size
                ParametersSerialisation.calculateParameterSize(params) +
                SizeConstants.INT_SIZE + // result close
                SizeConstants.INT_SIZE + // result id
                0;
    }

}
