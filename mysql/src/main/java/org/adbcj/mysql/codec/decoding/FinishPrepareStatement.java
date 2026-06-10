package org.adbcj.mysql.codec.decoding;

import io.netty.channel.Channel;
import org.adbcj.DbCallback;
import org.adbcj.mysql.codec.BoundedInputStream;
import org.adbcj.mysql.MySqlConnection;
import org.adbcj.mysql.MySqlPreparedStatement;
import org.adbcj.mysql.codec.MysqlType;
import org.adbcj.mysql.codec.packets.EofResponse;
import org.adbcj.mysql.codec.packets.PreparedStatementToBuild;
import org.adbcj.mysql.codec.packets.StatementPreparedEOF;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


abstract class FinishPrepareStatement extends DecoderState {

    protected final PreparedStatementToBuild statement;
    protected final DbCallback<MySqlPreparedStatement> callback;
    protected final MySqlConnection connection;

    FinishPrepareStatement(
            MySqlConnection connection,
            PreparedStatementToBuild statement,
            DbCallback<MySqlPreparedStatement> callback) {
        this.statement = statement;
        this.callback  = sandboxCallback(callback);
        this.connection = connection;
    }

    protected void readAllAndIgnore(BoundedInputStream in) throws IOException {
        in.readFully(new byte[in.getRemaining()]);
    }

    public static DecoderState create(MySqlConnection connection,
                                      PreparedStatementToBuild statement,
                                      DbCallback<MySqlPreparedStatement> toComplete
    ) {
        if (statement.getParams() > 0) {
            return new ReadParameters(connection, statement.getParams(), statement, toComplete);
        } else if (statement.getColumns() > 0) {
            return new ReadColumns(connection, statement.getColumns(), statement, toComplete);
        } else {
            return new AcceptNextResponse(connection);
        }
    }

    private static class ReadParameters extends FinishPrepareStatement {
        private final int parametersToParse;

        public ReadParameters(
                MySqlConnection connection,
                int parametersToParse,
                PreparedStatementToBuild statement,
                DbCallback<MySqlPreparedStatement> callback) {
            super(connection, statement, callback);

            this.parametersToParse = parametersToParse;
        }

        @Override
        public ResultAndState parse(int length, int packetNumber,
                                    BoundedInputStream in, Channel channel) throws IOException {
            int typesCount = statement.getParametersTypes().size();
            MysqlType newType = FieldDecodingState.parseField(in, typesCount).getMysqlType();
            List<MysqlType> types = new ArrayList<MysqlType>(typesCount + 1);
            types.addAll(statement.getParametersTypes());
            types.add(newType);
            PreparedStatementToBuild newStatement = new PreparedStatementToBuild(length, packetNumber,
                    statement.getPreparedStatement(), types);
            int restOfParams = parametersToParse - 1;
            if (restOfParams > 0) {
                return result(new ReadParameters(connection, restOfParams, newStatement, callback), statement);
            } else {
                return result(new EofAndColumns(connection, newStatement, callback), statement);
            }
        }

        @Override
        public String toString() {
            return "PREPARED-STATEMENT-READ-PARAMETERS";
        }
    }

    private static class EofAndColumns extends FinishPrepareStatement {

        public EofAndColumns(
                MySqlConnection connection,
                PreparedStatementToBuild statement,
                DbCallback<MySqlPreparedStatement> toComplete) {
            super(connection, statement, toComplete);
        }

        @Override
        public ResultAndState parse(int length, int packetNumber, BoundedInputStream in, Channel channel) throws IOException {
            if (in.read() == RESPONSE_EOF) {
                EofResponse eof = decodeEofResponse(in, length, packetNumber, EofResponse.Type.STATEMENT);
                if (statement.getColumns() == 0) {
                    final StatementPreparedEOF preparedEOF = new StatementPreparedEOF(packetNumber, packetNumber, statement);
                    callback.onComplete(new MySqlPreparedStatement(connection, preparedEOF), null);
                    return result(new AcceptNextResponse(connection), preparedEOF);
                } else {
                    return result(new ReadColumns(connection, statement.getColumns(), statement, callback), statement);
                }
            } else {
                throw new IllegalStateException("Did not expect a EOF from the server");
            }
        }

        @Override
        public String toString() {
            return "PREPARED-STATEMENT-COLUMNS-EOF";
        }
    }

    private static class ReadColumns extends FinishPrepareStatement {
        private final int restOfColumns;

        public ReadColumns(
                MySqlConnection connection,
                int restOfColumns,
                PreparedStatementToBuild statement,
                DbCallback<MySqlPreparedStatement> callback) {
            super(connection, statement, callback);
            this.restOfColumns = restOfColumns;
        }

        @Override
        public ResultAndState parse(int length, int packetNumber, BoundedInputStream in, Channel channel) throws IOException {
            readAllAndIgnore(in);
            int restOfParams = restOfColumns - 1;
            if (restOfParams > 0) {
                return result(new ReadColumns(connection, restOfParams, statement, callback), statement);
            } else {
                return result(new EofStatement(connection, statement, callback), statement);
            }
        }

        @Override
        public String toString() {
            return "PREPARED-STATEMENT-READ-COLUMNS";
        }
    }

    private static class EofStatement extends FinishPrepareStatement {

        public EofStatement(
                MySqlConnection connection,
                PreparedStatementToBuild statement,
                DbCallback<MySqlPreparedStatement> toComplete) {
            super(connection, statement, toComplete);
        }

        @Override
        public ResultAndState parse(int length, int packetNumber, BoundedInputStream in, Channel channel) throws IOException {
            if (in.read() == RESPONSE_EOF) {
                EofResponse eof = decodeEofResponse(in, length, packetNumber, EofResponse.Type.STATEMENT);

                final StatementPreparedEOF preparedEOF = new StatementPreparedEOF(packetNumber, packetNumber, statement);
                callback.onComplete(new MySqlPreparedStatement(connection, preparedEOF), null);
                return result(new AcceptNextResponse(connection), preparedEOF);
            } else {
                throw new IllegalStateException("Did not expect a EOF from the server");
            }
        }
    }
}
