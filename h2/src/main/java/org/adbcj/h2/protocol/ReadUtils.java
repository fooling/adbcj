package org.adbcj.h2.protocol;

import org.adbcj.DbException;
import org.adbcj.Value;
import org.adbcj.h2.DateTimeUtils;
import org.adbcj.h2.decoding.H2Types;
import org.adbcj.h2.decoding.IoUtils;
import org.adbcj.h2.decoding.ResultOrWait;
import org.adbcj.support.DefaultValue;

import java.io.DataInputStream;
import java.io.IOException;
import java.math.BigDecimal;


public class ReadUtils {
    public static ResultOrWait<Value> tryReadValue(DataInputStream stream, ResultOrWait<Integer> maybeType) throws IOException {
        if (!maybeType.couldReadResult) {
            return ResultOrWait.WaitLonger;
        }
        int typeCode = maybeType.result;
        H2Types type = H2Types.typeCodeToType(typeCode);
        switch (type) {
            case NULL:
                return convertToValue(ResultOrWait.result(null));
            case LONG:
                return convertToValue(IoUtils.tryReadNextLong(stream, maybeType));
            case DATE:
                return convertToDateValue(IoUtils.tryReadNextLong(stream, maybeType));
            case TIME:
                return convertNanoToTime(IoUtils.tryReadNextLong(stream, maybeType));
            case TIMESTAMP:
                final ResultOrWait<Long> dateValues = IoUtils.tryReadNextLong(stream, maybeType);
                final ResultOrWait<Long> nanos = IoUtils.tryReadNextLong(stream, dateValues);
                return convertToTimestampValue(dateValues, nanos);
            case INTEGER:
                return convertToValue(IoUtils.tryReadNextInt(stream, maybeType));
            case DOUBLE:
                return convertToValue(IoUtils.tryReadNextDouble(stream, maybeType));
            case DECIMAL:
                return convertToDecimalValue(IoUtils.tryReadNextString(stream, maybeType));
            case STRING:
                return convertToValue(IoUtils.tryReadNextString(stream, maybeType));
            case CLOB:
                final ResultOrWait<Long> length = IoUtils.tryReadNextLong(stream, ResultOrWait.Start);
                if(length.couldReadResult){
                    if(length.result==-1)  {
                        throw new DbException("Cannot handle this CLOB, we only support inlined CLOBs");
                    }  else{
                        return directReadClob(stream, length);
                    }
                } else{
                    return ResultOrWait.WaitLonger;
                }
            default:
                throw new DbException("Cannot handle type: " + type);
        }
    }

    private static ResultOrWait<Value> directReadClob(DataInputStream stream, ResultOrWait<Long> length) throws IOException {
        final ResultOrWait<Value> value
                = convertToValue(IoUtils.readEncodedString(stream, length.result.intValue()));
        final ResultOrWait<Integer> lobMagicBits = IoUtils.tryReadNextInt(stream, value);
        if(lobMagicBits.couldReadResult){
            return value;
        } else{
            return ResultOrWait.WaitLonger;
        }
    }

    static <T> ResultOrWait<Value> convertToValue(ResultOrWait<T> maybeValue) {
        if (!maybeValue.couldReadResult) {
            return ResultOrWait.WaitLonger;
        } else {
            return ResultOrWait.result(new DefaultValue(maybeValue.result));
        }
    }

    static ResultOrWait<Value> convertToDecimalValue(ResultOrWait<String> maybeValue) {
        if (!maybeValue.couldReadResult) {
            return ResultOrWait.WaitLonger;
        } else {
            return ResultOrWait.result(new DefaultValue(new BigDecimal(maybeValue.result)));
        }
    }

    static ResultOrWait<Value> convertNanoToTime(ResultOrWait<Long> maybeValue) {
        if (!maybeValue.couldReadResult) {
            return ResultOrWait.WaitLonger;
        } else {
            return ResultOrWait.result(new DefaultValue(DateTimeUtils.convertNanoToTime(maybeValue.result)));
        }
    }

    static ResultOrWait<Value> convertToDateValue(ResultOrWait<Long> maybeValue) {
        if (!maybeValue.couldReadResult) {
            return ResultOrWait.WaitLonger;
        } else {
            return ResultOrWait.result(new DefaultValue(DateTimeUtils.convertDateValueToDate(maybeValue.result)));
        }
    }

    static ResultOrWait<Value> convertToTimestampValue(ResultOrWait<Long> dateValue,ResultOrWait<Long> nanos) {
        if (dateValue.couldReadResult && nanos.couldReadResult) {
            return ResultOrWait.result(new DefaultValue(
                    DateTimeUtils.convertDateValueToTimestamp(dateValue.result,nanos.result)));
        } else {
            return ResultOrWait.WaitLonger;
        }
    }
}
