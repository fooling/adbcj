package org.adbcj.h2;

import org.adbcj.DbException;


public class H2DbException extends DbException {
    public final String sqlstate;
    public final String message;
    public final String sql;
    public final int errorCode;
    private final String stackTrace;

    public H2DbException(String sqlstate, String message, String sql, int errorCode, String stackTrace) {
        super(message);
        this.sqlstate = sqlstate;
        this.message = message;
        this.sql = sql;
        this.errorCode = errorCode;
        this.stackTrace = stackTrace;
    }
    public H2DbException(String sqlstate, String message, String sql, int errorCode, String stackTrace, StackTraceElement[] entry) {
        super(message,null, entry);
        this.sqlstate = sqlstate;
        this.message = message;
        this.sql = sql;
        this.errorCode = errorCode;
        this.stackTrace = stackTrace;
    }

    public static H2DbException create(
            String sqlstate,
            String message,
            String sql,
            int errorCode,
            String stackTrace,
            StackTraceElement[] entry){
        if(entry==null){
            return new H2DbException(sqlstate, message, sql, errorCode, stackTrace);
        } else{
            return new H2DbException(sqlstate, message, sql, errorCode, stackTrace, entry);
        }
    }

}
