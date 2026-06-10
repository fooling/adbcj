/*
	This file is part of ADBCJ.

	ADBCJ is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	ADBCJ is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with ADBCJ.  If not, see <http://www.gnu.org/licenses/>.

	Copyright 2008  Mike Heath
 */
package org.adbcj.mysql.codec;

import org.adbcj.support.SizeConstants;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Calendar;
import java.util.EnumSet;
import java.util.Set;

public final class IoUtils {

    public static final int NULL_VALUE = 0xfb;

    private IoUtils() {
        // Non-instantiable
    }

    /**
     * @return an unsigned byte
     * @throws IOException  if there is an error reading from the stream
     * @throws EOFException if the end of the stream is reached
     */
    public static int safeRead(InputStream in) throws IOException {
        int i = in.read();
        if (i < 0) {
            throw new EOFException();
        }
        return i;
    }

    public static int readShort(InputStream in) throws IOException {
        int b0 = safeRead(in);
        int b1 = safeRead(in);
        int i = b1 << 8 | b0;
        if ((b1 & 0x80) == 0x80) {
            i |= 0xffff0000;
        }
        return i;
    }

    public static int readUnsignedShort(InputStream in) throws IOException {
        int b0 = safeRead(in);
        int b1 = safeRead(in);
        return b1 << 8 | b0;
    }

    public static void writeShort(OutputStream out, int i) throws IOException {
        out.write(i);
        out.write(i >> 8);
    }

    public static void writeInt(OutputStream out, int i) throws IOException {
        out.write(i);
        out.write(i >> 8);
        out.write(i >> 16);
        out.write(i >> 24);
    }

    /**
     * Reads a little-endian 3-byte unsigned integer
     */
    public static int readUnsignedMediumInt(InputStream in) throws IOException {
        int b0 = safeRead(in);
        int b1 = safeRead(in);
        int b2 = safeRead(in);
        return b2 << 16 | b1 << 8 | b0;
    }

    public static int readMediumInt(InputStream in) throws IOException {
        int b0 = safeRead(in);
        int b1 = safeRead(in);
        int b2 = safeRead(in);
        int i = b2 << 16 | b1 << 8 | b0;
        if ((b2 & 0x80) == 0x80) {
            i |= 0xff000000;
        }
        return i;
    }

    public static int readInt(InputStream in) throws IOException {
        int b0 = safeRead(in);
        int b1 = safeRead(in);
        int b2 = safeRead(in);
        int b3 = safeRead(in);
        return b3 << 24 | b2 << 16 | b1 << 8 | b0;
    }

    public static long readUnsignedInt(InputStream in) throws IOException {
        long b0 = safeRead(in);
        long b1 = safeRead(in);
        long b2 = safeRead(in);
        long b3 = safeRead(in);
        return b3 << 24 | b2 << 16 | b1 << 8 | b0;
    }

    public static long readLong(InputStream in) throws IOException {
        long b0 = safeRead(in);
        long b1 = safeRead(in);
        long b2 = safeRead(in);
        long b3 = safeRead(in);
        long b4 = safeRead(in);
        long b5 = safeRead(in);
        long b6 = safeRead(in);
        long b7 = safeRead(in);
        return b7 << 56 | b6 << 48 | b5 << 40 | b4 << 32 | b3 << 24 | b2 << 16 | b1 << 8 | b0;
    }

    public static long readBinaryLengthEncoding(InputStream in) throws IOException {
        return readBinaryLengthEncoding(in, safeRead(in));
    }

    public static long readBinaryLengthEncoding(InputStream in, int firstByte) throws IOException {
        // This is documented at
        // http://forge.mysql.com/wiki/MySQL_Internals_ClientServer_Protocol#Elements
        if (firstByte <= 250) {
            return firstByte;
        }
        if (firstByte == NULL_VALUE) {
            return -1;
        }
        if (firstByte == 252) {
            return readUnsignedShort(in);
        }
        if (firstByte == 253) {
            return readMediumInt(in);
        }
        if (firstByte == 254) {
            long length = readLong(in);
            if (length < 0) {
                throw new RuntimeException("Received length too large to handle");
            }
            return length;
        }
        throw new IllegalStateException("Recieved a length value we don't know how to handle");
    }

    public static String readLengthCodedString(InputStream in, Charset charset) throws IOException {
        return readLengthCodedString(in, safeRead(in), charset);
    }

    public static String readLengthCodedString(InputStream in, int firstByte, Charset charset) throws IOException {
        long length = readBinaryLengthEncoding(in, firstByte);
        return readFixedLengthString(in, (int) length, charset);
    }

    public static void writeLengthCodedString(OutputStream out, String stringToWrite, Charset charset) throws IOException {
        if (stringToWrite == null) {
            out.write(251);
        } else {
            byte[] data = stringToWrite.getBytes(charset);
            if (data.length > 250) {
                if (data.length > 0xFFFFFF) {
                    out.write(254);
                    writeLong(out, data.length, 4);
                } else if (data.length > 0xFFFF) {
                    out.write((253));
                    writeLong(out, data.length, 3);
                } else {
                    out.write((252));
                    writeLong(out, data.length, 2);
                }
                out.write(data);
            } else {
                out.write((byte) data.length);
                out.write(data);
            }
        }

    }
    public static int writeLengthCodedStringLength(String stringToWrite, Charset charset) {
        if (stringToWrite == null) {
            return SizeConstants.CHAR_SIZE;
        } else {
            byte[] data = stringToWrite.getBytes(charset);
            if (data.length > 250) {
                int length = SizeConstants.BYTE_SIZE + data.length;
                if (data.length > 0xFFFFFF) {
                    length += 4;
                } else if (data.length > 0xFFFF) {
                    length += 3;
                } else {
                    length += 2;
                }
                return length;
            } else {
                return SizeConstants.BYTE_SIZE + data.length;
            }
        }

    }

    public static void writeLong(OutputStream out, long value, int length) throws IOException {
        for (int i = 0; i < length; i++) {
            out.write((byte) ((value >> (i * 8)) & 0xFF));
        }
    }


    public static String readNullTerminatedString(BoundedInputStream in, Charset charset) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if(in.getRemaining()<=0){
            return "";
        }
        for (int c = in.read(); c > 0; c = in.read()) {
        	out.write(c);
            // fixbug: lost the last char issue. The remaining decision should be placed here 
            //and not in for-condition!
            // @since 2017-09-01 little-pan
            if(in.getRemaining() <= 0) {
            	break;
            }
        }
        return new String(out.toByteArray(), charset);

    }

    public static String readFixedLengthString(InputStream in, int length, Charset charset) throws IOException {
        if (length == 0) {
            return "";
        }
        byte[] buffer = new byte[length];
        int readBytes = in.read(buffer);
        if (readBytes < length) {
            throw new IOException("Buffer overrun");
        }
        return new String(buffer, charset);
    }

    public static Set<FieldFlag> readEnumSet(InputStream in, Class<FieldFlag> enumClass) throws IOException {
        return toEnumSet(enumClass, safeRead(in) & 0xFFFFL);
    }

    public static <E extends Enum<E>> EnumSet<E> readEnumSetShort(InputStream in, Class<E> enumClass) throws IOException {
        return toEnumSet(enumClass, readUnsignedShort(in) & 0xFFFFL);
    }

    private static <E extends Enum<E>> EnumSet<E> toEnumSet(Class<E> enumClass, long vector) {
        EnumSet<E> set = EnumSet.noneOf(enumClass);
        long mask = 1;
        for (E e : enumClass.getEnumConstants()) {
            if ((mask & vector) == mask) {
                set.add(e);
            }
            mask <<= 1;
        }
        return set;
    }

    public static <E extends Enum<E>> void writeEnumSetShort(OutputStream out, Set<E> set) throws IOException {
        long vector = toLong(set);
        if ((vector & ~0x0000ffff) != 0) {
            throw new IllegalArgumentException(
                    "The enum set is too large to fit in a short: " + set);
        }
        writeShort(out, (int) vector);
    }

    private static <E extends Enum<E>> long toLong(Set<E> set) {
        long vector = 0;
        for (E e : set) {
            if (e.ordinal() >= Long.SIZE) {
                throw new IllegalArgumentException(
                        "The enum set is too large to fit in a bit vector: "
                                + set);
            }
            vector |= 1L << e.ordinal();
        }
        return vector;
    }

    /**
     * Creates a null value bit mask, according to http://forge.mysql.com/wiki/MySQL_Internals_ClientServer_Protocol#Execute_Packet_.28Tentative_Description.29
     */
    public static byte[] nullMask(Object[] arguments) {
        byte[] nullBitsBuffer = new byte[nullMaskSize(arguments)];
        for (int i = 0; i < arguments.length; i++) {
            if (arguments[i] == null) {
                nullBitsBuffer[i / 8] |= (1 << (i & 7));
            }
        }
        return nullBitsBuffer;
    }

    public static int nullMaskSize(Object[] arguments) {
        return (arguments.length + 7) / 8;
    }

    public static String readDate(BoundedInputStream in) throws IOException {
        int length = in.read();
        if(length<4){
            return String.format("%04d-%02d-%02d %02d:%02d:%02d",0,0,0,0,0,0);
        }
        byte[] data = new byte[length];
        in.readFully(data);

        int year =  ((data[1] & 0xFF) << 8)+ (data[0] & 0xFF);
        int month = (data[2] & 0xFF);
        int day = (data[3] & 0xFF);
        if(length==4){
            return String.format("%04d-%02d-%02d",year,month,day);
        } else if(length==7){
            int hours =  (data[4] & 0xFF);
            int minutes =  (data[5] & 0xFF);
            int seconds =  (data[6] & 0xFF);
            return String.format("%04d-%02d-%02d %02d:%02d:%02d",year,month,day,hours,minutes,seconds);
        } else if(length==8){
            int hours =  (data[5] & 0xFF);
            int minutes =  (data[6] & 0xFF);
            int seconds =  (data[7] & 0xFF);
            return String.format("%02d:%02d:%02d",hours,minutes,seconds);
        }  else{
            throw new UnsupportedOperationException("This date format is not yet implemented");
        }
    }

    public static void writeDate(OutputStream out,java.util.Date dateToWrite) throws IOException {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(dateToWrite);


        writeLong(out, calendar.get(Calendar.YEAR), 2);
        out.write((byte) (calendar.get(Calendar.MONTH) + 1));
        out.write((byte) calendar.get(Calendar.DAY_OF_MONTH));
    }

    public static void safeSkip(InputStream in, long amountToSkip) throws IOException {
        long actuallySkipped = in.skip(amountToSkip);
        while (actuallySkipped<amountToSkip){
            amountToSkip = amountToSkip- actuallySkipped;
            actuallySkipped = in.skip(amountToSkip);
        }
    }
}
