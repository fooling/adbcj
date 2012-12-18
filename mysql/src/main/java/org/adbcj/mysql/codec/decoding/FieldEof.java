package org.adbcj.mysql.codec.decoding;

import org.adbcj.mysql.codec.BoundedInputStream;
import org.adbcj.mysql.codec.MysqlField;
import org.adbcj.mysql.codec.packets.EofResponse;

import java.io.IOException;
import java.util.List;

/**
* @author roman.stoffel@gamlor.info
* @since 12.04.12
*/
class FieldEof extends DecoderState {
    private final List<MysqlField> fields;

    public FieldEof(List<MysqlField> fields) {
        this.fields = fields;
    }

    @Override
    public ResultAndState parse(int length, int packetNumber, BoundedInputStream in) throws IOException {
        int fieldCount = in.read();

        if (fieldCount != RESPONSE_EOF) {
            throw new IllegalStateException("Expected an EOF response from the server");
        }
        EofResponse fieldEof = decodeEofResponse(in, length, packetNumber, EofResponse.Type.FIELD);
        return result(ROW(fields),fieldEof);
    }

    @Override
    public String toString() {
        return "FIELD-EOF";
    }


}
