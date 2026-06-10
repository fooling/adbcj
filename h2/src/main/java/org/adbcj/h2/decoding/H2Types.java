package org.adbcj.h2.decoding;

import org.adbcj.Type;

import java.math.BigDecimal;
import java.sql.Time;
import java.sql.Timestamp;

public enum H2Types {


    NULL(0, Type.OTHER,Object.class),
    INTEGER(4, Type.INTEGER,Integer.class),
    LONG(5, Type.BIGINT,Long.class),
    DECIMAL(6, Type.DECIMAL,BigDecimal.class),
    DOUBLE(7, Type.DOUBLE,Double.class),
    TIME(9, Type.TIME,Time.class),
    DATE(10, Type.DATE, Time.class),
    TIMESTAMP(11, Type.TIMESTAMP, Timestamp.class),
    STRING(13, Type.VARCHAR, String.class),
    CLOB(16, Type.CLOB, String.class);


    public static H2Types typeCodeToType(int typeCode) {
        for (H2Types type : values()) {
            if (typeCode == type.id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Could not find type for "+typeCode);
    }



    private final int id;
    private final Type type;
    private final Class className;

    H2Types(int id, Type type, Class className) {
        this.id = id;
        this.type = type;
        this.className = className;
    }

    public Type getType() {
        return type;
    }

    public int id() {
        return id;
    }
}
