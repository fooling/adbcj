package org.adbcj.h2.decoding;

import org.adbcj.support.SizeConstants;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;


public final class IoUtils {
    private IoUtils(){}


    /**
     * Write a string. The maximum string length is Integer.MAX_VALUE.
     *
     * @param theString the value
     */
    public static void writeString(DataOutputStream out,String theString) throws IOException {
        if (theString == null) {
            out.writeInt(-1);
        } else {
            int len = theString.length();
            out.writeInt(len);
            writeCharsOfString(out, theString);
        }
    }

    public static void writeCharsOfString(DataOutputStream out, String theString) throws IOException {

        int len = theString.length();
        for (int i = 0; i < len; i++) {
            out.writeChar(theString.charAt(i));
        }
    }

    /**
     * Write a byte array.
     *
     * @param data the value
     */
    public static void writeBytes(DataOutputStream out,byte[] data) throws IOException {
        if (data == null) {
            out.writeInt(-1);
        } else {
            out.writeInt(data.length);
            out.write(data);
        }
    }
    public static void writeBoolean(DataOutputStream out, boolean value) throws IOException {
        out.writeByte((byte) (value ? 1 : 0));
    }

    public static boolean readBoolean(DataInputStream stream) throws IOException {
        return stream.readByte() == 1;
    }

    public static ResultOrWait<Boolean> tryReadNextBoolean(DataInputStream stream, ResultOrWait previousResult) throws IOException {
        if(!previousResult.couldReadResult){
            return previousResult;
        }
        if(stream.available()< SizeConstants.BYTE_SIZE){
            return ResultOrWait.WaitLonger;
        }
        return ResultOrWait.result(stream.readByte()==1);
    }

    public static ResultOrWait<Integer> tryReadNextInt(DataInputStream stream, ResultOrWait previousResult) throws IOException {
        if(!previousResult.couldReadResult){
            return previousResult;
        }
        if(stream.available()< SizeConstants.INT_SIZE){
            return ResultOrWait.WaitLonger;
        }
        return ResultOrWait.result(stream.readInt());
    }

    public static ResultOrWait<Long> tryReadNextLong(DataInputStream stream, ResultOrWait previousResult) throws IOException {
        if(!previousResult.couldReadResult){
            return previousResult;
        }
        if(stream.available()< SizeConstants.LONG_SIZE){
            return ResultOrWait.WaitLonger;
        }
        return ResultOrWait.result(stream.readLong());
    }

    public static ResultOrWait<Double> tryReadNextDouble(DataInputStream stream, ResultOrWait previousResult) throws IOException {
        if(!previousResult.couldReadResult){
            return previousResult;
        }
        if(stream.available()< SizeConstants.DOUBLE_SIZE){
            return ResultOrWait.WaitLonger;
        }
        return ResultOrWait.result(stream.readDouble());
    }

    public static ResultOrWait<String> tryReadNextString(DataInputStream stream, ResultOrWait previousResult) throws IOException {
        if(!previousResult.couldReadResult){
            return previousResult;
        }
        if(stream.available()< SizeConstants.INT_SIZE){
            return ResultOrWait.WaitLonger;
        }
        int stringLength = stream.readInt();
        if(stringLength<0){
            return ResultOrWait.result(null);
        }

        if(stream.available()< stringLength*SizeConstants.CHAR_SIZE){
            return ResultOrWait.WaitLonger;
        } else{
            char[] stringChars = new char[stringLength];
            for (int i = 0; i < stringLength; i++) {
                stringChars[i] = stream.readChar();
            }
            return ResultOrWait.result(new String(stringChars));
        }
    }

    public static ResultOrWait<String> readEncodedString(DataInputStream stream, int stringLength) throws IOException {
        if(stream.available()< stringLength){
            return ResultOrWait.WaitLonger;
        } else{
            char[] buff = new char[stringLength];
            int i = 0;
            try {
                for (; i < stringLength; i++) {
                    buff[i] = readChar(stream);
                }
                return ResultOrWait.result(new String(buff));
            } catch (EOFException e) {
                return ResultOrWait.WaitLonger;
            }
        }
    }
    private static char readChar(DataInputStream stream) throws IOException {
        int x = stream.readByte() & 0xff;
        if (x < 0x80) {
            return (char) x;
        } else if (x >= 0xe0) {
            return (char) (((x & 0xf) << 12) + ((stream.readByte() & 0x3f) << 6) + (stream.readByte() & 0x3f));
        } else {
            return (char) (((x & 0x1f) << 6) + (stream.readByte() & 0x3f));
        }
    }

    public static ResultOrWait<byte[]> tryReadNextBytes(DataInputStream stream,
                                                        ResultOrWait<?> previousResult) throws IOException {
        if(!previousResult.couldReadResult){
            return ResultOrWait.WaitLonger;
        }
        if(stream.available()< SizeConstants.INT_SIZE){
            return ResultOrWait.WaitLonger;
        }
        int byteLength = stream.readInt();
        if (byteLength == -1) {
            return ResultOrWait.result(null);
        }
        if(stream.available()< byteLength){
            return ResultOrWait.WaitLonger;
        }
        byte[] b = new byte[byteLength];
        final int readAmount = stream.read(b);
        if(readAmount!=byteLength){
            throw new IllegalStateException("Expect to read "+byteLength+" but read "+readAmount);
        }
        return ResultOrWait.result(b);
    }
}
