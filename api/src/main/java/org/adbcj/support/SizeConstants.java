package org.adbcj.support;


public final class SizeConstants {
    public static final int CHAR_SIZE = 2;
    public static final int INT_SIZE = 4;
    public static final int BYTE_SIZE = 1;
    public static final int LONG_SIZE = 8;
    public static final int DOUBLE_SIZE = 8;
    public static final int BOOLEAN_SIZE = 1;

    private SizeConstants(){}

    public static int sizeOf(boolean param) {
        return BOOLEAN_SIZE;
    }
    public static int sizeOf(int param) {
        return INT_SIZE;
    }
    public static int sizeOf(long param) {
        return LONG_SIZE;
    }
    public static int sizeOf(String param) {
        return INT_SIZE + (null==param ? 0 : CHAR_SIZE * param.length());
    }
    public static int lengthOfString(String param) {
        return CHAR_SIZE * param.length();
    }
}
