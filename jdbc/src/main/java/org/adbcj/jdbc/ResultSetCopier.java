package org.adbcj.jdbc;

import org.adbcj.Field;
import org.adbcj.ResultHandler;
import org.adbcj.Type;
import org.adbcj.support.DefaultField;
import org.adbcj.support.DefaultValue;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;


class ResultSetCopier {
    static <T> void fillResultSet(
            java.sql.ResultSet jdbcResultSet,
            ResultHandler<T> eventHandler,
            T accumulator) throws SQLException {
        ResultSetMetaData metaData = jdbcResultSet.getMetaData();
        int columnCount = metaData.getColumnCount();
        List<Field> fields = new ArrayList<Field>(columnCount);
        eventHandler.startFields(accumulator);

        for (int i = 1; i <= columnCount; i++) {
            Field field = new DefaultField(
                    i - 1,
                    metaData.getCatalogName(i),
                    metaData.getSchemaName(i),
                    metaData.getTableName(i),
                    metaData.getTableName(i),
                    Type.fromJdbcType(metaData.getColumnType(i)),
                    metaData.getColumnLabel(i),
                    metaData.getCatalogName(i),
                    metaData.getPrecision(i),
                    metaData.getScale(i),
                    metaData.isAutoIncrement(i),
                    metaData.isCaseSensitive(i),
                    metaData.isCurrency(i),
                    metaData.isNullable(i) == 1,
                    metaData.isReadOnly(i),
                    metaData.isSigned(i),
                    metaData.getColumnClassName(i)
            );
            fields.add(field);
            eventHandler.field(field, accumulator);
        }

        eventHandler.endFields(accumulator);

        eventHandler.startResults(accumulator);
        while (jdbcResultSet.next()) {
            eventHandler.startRow(accumulator);
            for (int i = 1; i <= columnCount; i++) {
                Field field = fields.get(i - 1);
                Object value;
                switch (field.getColumnType()) {
                    case BIGINT:
                        value = jdbcResultSet.getLong(i);
                        break;
                    case INTEGER:
                        value = jdbcResultSet.getInt(i);
                        break;
                    case VARCHAR:
                        value = jdbcResultSet.getString(i);
                        break;
                    case DECIMAL:
                        value = jdbcResultSet.getString(i);
                        break;
                    case DATE:
                        value = jdbcResultSet.getString(i);
                        break;
                    case TIME:
                        value = jdbcResultSet.getString(i);
                        break;
                    case TIMESTAMP:
                        value = jdbcResultSet.getString(i);
                        break;
                    case DOUBLE:
                        value = jdbcResultSet.getDouble(i);
                        break;
                    case LONGVARCHAR:
                        value = jdbcResultSet.getString(i);
                        break;
                    case CLOB:
                        value = jdbcResultSet.getString(i);
                        break;
                    case NULL:
                        value = null;
                        break;
                    default:
                        throw new IllegalStateException("Don't know how to handle field to type " + field.getColumnType());
                }
                if (jdbcResultSet.wasNull()) {
                    value = null;
                }
                eventHandler.value(new DefaultValue(value), accumulator);
            }
            eventHandler.endRow(accumulator);
        }
        eventHandler.endResults(accumulator);
    }

}
