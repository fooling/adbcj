package org.adbcj.mysql.codec.decoding;

import io.netty.channel.Channel;
import org.adbcj.DbCallback;
import org.adbcj.DbException;
import org.adbcj.ResultHandler;
import org.adbcj.mysql.MySqlConnection;
import org.adbcj.mysql.codec.*;
import org.adbcj.mysql.codec.packets.ResultSetFieldResponse;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;


public class FieldDecodingState<T> extends DecoderState {
    private final int expectedAmountOfFields;
    private final List<MysqlField> fields;
    private final DbCallback<T> callback;
    private final StackTraceElement[] entry;
    private final MySqlConnection connection;
    private final ResultHandler<T> eventHandler;
    private final T accumulator;
    private Row.RowDecodingType decodingType;
    private DbException failure;

    public FieldDecodingState(
            MySqlConnection connection,
            Row.RowDecodingType decodingType,
            int expectedAmountOfFields,
            List<MysqlField> fields,
            ResultHandler<T> eventHandler,
            T accumulator,
            DbCallback<T> callback,
            StackTraceElement[] entry,
            DbException failure) {
        this.decodingType = decodingType;
        this.expectedAmountOfFields = expectedAmountOfFields;
        this.fields = fields;
        this.callback = sandboxCallback(callback);
        this.entry = entry;
        this.connection = connection;
        this.eventHandler = eventHandler;
        this.accumulator = accumulator;
        this.failure = failure;
    }

    @Override
    public ResultAndState parse(int length,
                                int packetNumber,
                                BoundedInputStream in, Channel channel) throws IOException {

        int fieldNo = fields.size();

        if (logger.isTraceEnabled()) {
            logger.trace("expectedAmountOfFields: {} current field {}", expectedAmountOfFields, fieldNo);

        }
        ResultSetFieldResponse resultSetFieldResponse = decodeFieldResponse(in, length, packetNumber, fieldNo);


        ArrayList<MysqlField> newFields = new ArrayList<MysqlField>(fieldNo + 1);
        newFields.addAll(fields);
        newFields.add(resultSetFieldResponse.getField());
        try{
            eventHandler.field(resultSetFieldResponse.getField(), accumulator);
        } catch (Exception any){
            failure = DbException.attachSuppressedOrWrap(any, entry, failure);
        }

        if (expectedAmountOfFields > (fieldNo + 1)) {
            return result(new FieldDecodingState<T>(
                    connection,
                    decodingType,
                    expectedAmountOfFields,
                    newFields,
                    eventHandler,
                    accumulator,
                    callback,
                    entry,
                    failure), resultSetFieldResponse);
        } else {
            return result(new FieldEof<T>(
                    connection,
                    decodingType,
                    newFields,
                    eventHandler,
                    accumulator,
                    callback,
                    entry,
                    failure), resultSetFieldResponse);
        }
    }


    private ResultSetFieldResponse decodeFieldResponse(InputStream in,
                                                       int packetLength,
                                                       int packetNumber,
                                                       int fieldNo) throws IOException {
        MysqlField field = parseField(in, fieldNo);
        return new ResultSetFieldResponse(packetLength, packetNumber, field);
    }

    public static MysqlField parseField(InputStream in, int fieldNo) throws IOException {
        String catalogName = IoUtils.readLengthCodedString(in, StandardCharsets.UTF_8);
        String schemaName = IoUtils.readLengthCodedString(in, StandardCharsets.UTF_8);
        String tableLabel = IoUtils.readLengthCodedString(in, StandardCharsets.UTF_8);
        String tableName = IoUtils.readLengthCodedString(in, StandardCharsets.UTF_8);
        String columnLabel = IoUtils.readLengthCodedString(in, StandardCharsets.UTF_8);
        String columnName = IoUtils.readLengthCodedString(in, StandardCharsets.UTF_8);
        // Skip filler
        if(in.read()<0){
            throw new EOFException("Unexpected EOF. Expected to read 1 more byte");
        }
        int characterSetNumber = IoUtils.readUnsignedShort(in);
        MysqlCharacterSet charSet = MysqlCharacterSet.findById(characterSetNumber);
        long length = IoUtils.readUnsignedInt(in);
        int fieldTypeId = in.read();
        MysqlType fieldType = MysqlType.findById(fieldTypeId);
        Set<FieldFlag> flags = IoUtils.readEnumSet(in, FieldFlag.class);
        int decimals = in.read();
        IoUtils.safeSkip(in, 2); // Skip filler
        long fieldDefault = IoUtils.readBinaryLengthEncoding(in);
        return new MysqlField(fieldNo, catalogName, schemaName, tableLabel, tableName, fieldType, columnLabel,
                columnName, 0, // Figure out precision
                decimals, charSet, length, flags, fieldDefault);
    }

}
