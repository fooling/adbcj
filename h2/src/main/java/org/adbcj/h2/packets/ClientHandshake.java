package org.adbcj.h2.packets;

import org.adbcj.DbException;
import org.adbcj.h2.decoding.Constants;
import org.adbcj.h2.decoding.IoUtils;
import org.adbcj.h2.h2.SHA256;
import org.adbcj.support.SizeConstants;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;

import static org.adbcj.support.SizeConstants.INT_SIZE;


public class ClientHandshake extends ClientToServerPacket{

    private String database;
    private String originalUrl;
    private String userName;
    private final Map<String, String> keys;
    private byte[] pwdHash;

    public ClientHandshake(String database,
                           String originalUrl,
                           String userName,
                           String password,
                           Map<String,String> keys) {
        super();
        this.database = database;
        this.originalUrl = originalUrl;
        this.userName = userName;
        this.keys = keys;
        pwdHash = hashPassword(false, userName, password.toCharArray());
    }

    @Override
    public void writeToStream(DataOutputStream stream) throws IOException {

        stream.writeInt(Constants.TCP_PROTOCOL_VERSION_12);
        stream.writeInt(Constants.TCP_PROTOCOL_VERSION_12);
        IoUtils.writeString(stream, database);
        IoUtils.writeString(stream, originalUrl);
        IoUtils.writeString(stream, userName);
        IoUtils.writeBytes(stream, pwdHash);
        writeFileHash(stream);

        writeKeys(stream,keys);
    }

    @Override
    public int getLength() {
       return INT_SIZE + // Low version
               INT_SIZE +  // High version
               SizeConstants.sizeOf(database) +
               SizeConstants.sizeOf(originalUrl) +
               SizeConstants.sizeOf(userName) +
               INT_SIZE + pwdHash.length +  // password
               INT_SIZE +  // empty file hash
               INT_SIZE + calculateSizeKeys(keys) +
               0;
    }

    private int calculateSizeKeys(Map<String, String> keys) {
        int size = 0;
        for (Map.Entry<String, String> key : keys.entrySet()) {
            size+= INT_SIZE + SizeConstants.lengthOfString( key.getKey());
            size+= INT_SIZE + SizeConstants.lengthOfString( key.getValue()) ;
        }
        return size;
    }

    private void writeFileHash(DataOutputStream stream) throws IOException {
        // File password hash, not in use
        stream.writeInt(-1);
    }

    private void writeKeys(DataOutputStream stream,Map<String,String> keys) throws IOException {
        stream.writeInt(keys.size());
        for (Map.Entry<String, String> key : keys.entrySet()) {
            IoUtils.writeString(stream, key.getKey());
            IoUtils.writeString(stream, key.getValue());
        }
    }


    private static byte[] hashPassword(boolean passwordHash, String userName, char[] password) {
        if (passwordHash) {
            return convertHexToBytes(new String(password));
        }
        if (userName.length() == 0 && password.length == 0) {
            return new byte[0];
        }
        return SHA256.getKeyPasswordHash(userName, password);
    }

    private static final int[] HEX_DECODE = new int['f' + 1];
    /**
     * Convert a hex encoded string to a byte array.
     *
     * @param s the hex encoded string
     * @return the byte array
     */
    public static byte[] convertHexToBytes(String s) {
        int len = s.length();
        if (len % 2 != 0) {
            throw new DbException("ERROR: 90003");
        }
        len /= 2;
        byte[] buff = new byte[len];
        int mask = 0;
        int[] hex = HEX_DECODE;
        try {
            for (int i = 0; i < len; i++) {
                int d = hex[s.charAt(i + i)] << 4 | hex[s.charAt(i + i + 1)];
                mask |= d;
                buff[i] = (byte) d;
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            throw DbException.wrap(e);
        }
        if ((mask & ~255) != 0) {
            throw new DbException("ERROR: 90004");
        }
        return buff;
    }
}


