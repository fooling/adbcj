package org.adbcj.mysql.codec.packets;

import org.adbcj.mysql.codec.IoUtils;
import org.adbcj.mysql.codec.MysqlType;
import org.adbcj.support.SizeConstants;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;


public class PreparedStatementRequest extends CommandRequest {
    private final int statementId;
    private final List<MysqlType> types;
    private final Object[] data;

    public PreparedStatementRequest(int statementId, List<MysqlType> types, Object[] data) {
        super(Command.STATEMENT_EXECUTE);
        this.statementId = statementId;
        this.types = types;
        this.data = data;
    }

    @Override
    public boolean hasPayload() {
        return true;
    }

    /**
     * Protocol see http://forge.mysql.com/wiki/MySQL_Internals_ClientServer_Protocol#Execute_Packet_.28Tentative_Description.29
     */
    @Override
    protected void writePayLoad(OutputStream out) throws IOException {
        try {
            IoUtils.writeInt(out, statementId);
            out.write((byte) 0); // flags: 0: CURSOR_TYPE_NO_CURSOR
            IoUtils.writeInt(out, 1); // reserved for future use. Currently always 1.
            out.write(IoUtils.nullMask(data));  //null_bit_map
            out.write(1); //  new_parameter_bound_flag
            for (MysqlType type : types) {
                IoUtils.writeShort(out, type.getId());
            }
            writeParameters(out);
        } catch (IOException e) {
            throw new RuntimeException("Unexpected IO Exception: "+e.getMessage(),e);
        }
    }

    @Override
    public int getLength()  {
        return 1 + packetLength();
    }

    @Override
    public String toString() {
        return "PreparedStatementRequest{" +
                "statementId=" + statementId +
                '}';
    }

    int packetLength() {
        int size = SizeConstants.sizeOf(statementId)
                + SizeConstants.BYTE_SIZE // flags: 0: CURSOR_TYPE_NO_CURSOR
                + SizeConstants.sizeOf(1)// reserved for future use. Currently always 1.
                + IoUtils.nullMaskSize(data)
                + SizeConstants.BYTE_SIZE       //  new_parameter_bound_flag
                +SizeConstants.CHAR_SIZE * types.size();
        for (int i = 0; i < data.length;i++) {
            Object param = data[i];
            MysqlType type = types.get(i);
            if(null!=param){
                if (MysqlType.VARCHAR == type) {
                    size+=IoUtils.writeLengthCodedStringLength(param.toString(), StandardCharsets.UTF_8);
                } if (MysqlType.VAR_STRING == type) {
                    size+=IoUtils.writeLengthCodedStringLength(param.toString(), StandardCharsets.UTF_8);
                }  else {
                    throw new UnsupportedOperationException("Not yet implemented:" + type);
                }
            }
        }
        // Sanity test: Serialized output's length should be same as calculated size
//        {
//            ByteArrayOutputStream tt = new ByteArrayOutputStream();
//            try {
//                writePayLoad(tt);
//            } catch (IOException e) {
//                throw new RuntimeException(e.getMessage(),e);
//            }
//            if(tt.size()!=size){
//                throw new AssertionError("Size calculation is wrong. Should be "+tt.size() +" but is " + size);
//            }
//        }
        return size;
    }

    private void writeParameters(OutputStream out) throws IOException {
        if (types.size() != data.length) {
            throw new IllegalStateException("Expect type and data length to match. Type length: " +
                    types.size() + " data length " + data.length);
        }
        for (int i = 0; i < data.length;i++) {
            Object param = data[i];
            MysqlType type = types.get(i);
            if(null!=param){
                if (MysqlType.VARCHAR == type) {
                    IoUtils.writeLengthCodedString(out, param.toString(), StandardCharsets.UTF_8);
                } if (MysqlType.VAR_STRING == type) {
                    IoUtils.writeLengthCodedString(out, param.toString(), StandardCharsets.UTF_8);
                }  else {
                    throw new UnsupportedOperationException("Not yet implemented:" + type);
                }
            }
        }
    }
}
