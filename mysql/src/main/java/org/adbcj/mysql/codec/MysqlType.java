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

import org.adbcj.Type;

import java.math.BigDecimal;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Date;

// TODO Make sure all the types are mapped up properly - Most of these are guesses
public enum MysqlType {
	DECIMAL(0x00, Type.DECIMAL, BigDecimal.class),
	TINY(0x01, Type.TINYINT,Short.class),
	SHORT(0x02, Type.SMALLINT,Short.class),
	LONG(0x03, Type.INTEGER,Long.class),
	FLOAT(0x04, Type.FLOAT,Float.class),
	DOUBLE(0x05, Type.DOUBLE,Double.class),
	NULL(0x06, Type.NULL,Object.class),
	TIMESTAMP(0x07, Type.TIMESTAMP,Timestamp.class),
	LONGLONG(0x08, Type.BIGINT,Long.class),
	INT24(0x09, Type.INTEGER,Long.class),
	DATE(0x0a, Type.DATE,Date.class),
	TIME(0x0b, Type.TIME,Time.class),
	DATETIME(0x0c, Type.TIMESTAMP,Date.class),
	YEAR(0x0d, Type.INTEGER,Date.class),
	NEWDATE(0x0e, Type.DATE,Date.class),
	VARCHAR(0x0f, Type.VARCHAR,String.class),
	BIT(0x10, Type.BIT,Long.class),
	NEWDECIMAL(0xf6, Type.DECIMAL,BigDecimal.class),
	ENUM(0xf7, Type.INTEGER,String.class),
	SET(0xf8, Type.ARRAY,Object.class),
	TINY_BLOB(0xf9, Type.BLOB,byte[].class),
	MEDIUM_BLOB(0xfa, Type.BLOB,byte[].class),
	LONG_BLOB(0xfb, Type.BLOB,byte[].class),
	BLOB(0xfc, Type.BLOB,byte[].class),
	VAR_STRING(0xfd, Type.VARCHAR,String.class),
	STRING(0xfe, Type.VARCHAR,String.class),
	GEOMETRY(0xff, Type.STRUCT,Object.class);

	private final int id;
	private final Type type;
	private final String className;
	/** Performance optimisation. Enum.values() does a internal clone, allocating new objects
	 * It clones the array[], so that you cannot accidentally modify the original
	 * To avoid that, we have our own copy, which does not need to be cloned
	 */
	private static final MysqlType[] ALL_VALUES = values();

	MysqlType(int id, Type type, Class javaType) {
		this.id = id;
		this.type = type;
        this.className = javaType.getName();
	}

	public int getId() {
		return id;
	}

	public Type getType() {
		return type;
	}

	public static MysqlType findById(int id) {
		for (MysqlType type : ALL_VALUES) {
			if (id == type.id) {
				return type;
			}
		}
		return null;
	}

	// TODO We need to reverse-engineer all these values

	public boolean isCaseSensitive() {
		return false;
	}

	public boolean isCurrency() {
		return false;
	}

	public String getClassName() {
		return className;
	}
}
