package org.adbcj.mysql.codec.packets;

import org.adbcj.mysql.codec.MysqlType;

import java.util.ArrayList;
import java.util.List;


public final class PreparedStatementToBuild extends ServerPacket {
    private final OkResponse.PreparedStatementOK preparedStatement;
    private final List<MysqlType> parametersTypes;

    public PreparedStatementToBuild(int packetLength, int packetNumber,
                                    OkResponse.PreparedStatementOK preparedStatement) {
        this(packetLength, packetNumber, preparedStatement, new ArrayList<MysqlType>());
    }
    public PreparedStatementToBuild(int packetLength, int packetNumber,
                                    OkResponse.PreparedStatementOK preparedStatement,
                                    List<MysqlType> parametersTypes) {
        super(packetLength, packetNumber);
        this.preparedStatement = preparedStatement;
        this.parametersTypes = parametersTypes;
    }

    public OkResponse.PreparedStatementOK getPreparedStatement() {
        return preparedStatement;
    }

    public int getHandlerId() {
        return preparedStatement.getHandlerId();
    }

    public List<MysqlType> getParametersTypes() {
        return parametersTypes;
    }

    public int getColumns() {
        return preparedStatement.getColumns();
    }

    public int getParams() {
        return preparedStatement.getParams();
    }
}
