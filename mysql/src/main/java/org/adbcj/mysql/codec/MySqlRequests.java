package org.adbcj.mysql.codec;

import org.adbcj.DbCallback;
import org.adbcj.Result;
import org.adbcj.ResultHandler;
import org.adbcj.mysql.MySqlConnection;
import org.adbcj.mysql.MySqlPreparedStatement;
import org.adbcj.mysql.codec.decoding.*;
import org.adbcj.mysql.codec.packets.*;
import org.adbcj.support.*;


public final class MySqlRequests {
    private static final OneArgFunction<MysqlResult, Void> TO_VOID = arg -> null;

    public static MySqlRequest createCloseRequest(
            MySqlConnection connection,
            DbCallback<Void> callback,
            StackTraceElement[] entry) {
        return new MySqlRequest<>(
                "Close",
                new ExpectOK<>(connection, callback, entry),
                new CommandRequest(Command.QUIT),
                callback);
    }

    public static <T> MySqlRequest executeQuery(
            MySqlConnection connection,
            String query,
            ResultHandler<T> eventHandler,
            T accumulator,
            DbCallback<T> callback,
            StackTraceElement[] entry) {
        return new MySqlRequest<>(
                "Query: " + query,
                new ExpectQueryResult<T>(
                        connection,
                        Row.RowDecodingType.STRING_BASED,
                        eventHandler,
                        accumulator,
                        callback,
                        entry),
                new StringCommandRequest(Command.QUERY, query),
                callback);
    }

    public static <T> MySqlRequest executePreparedQuery(
            MySqlConnection connection,
            StatementPreparedEOF stmp,
            Object[] data,
            ResultHandler<T> eventHandler,
            T accumulator,
            DbCallback<T> callback,
            StackTraceElement[] entry) {
        return new MySqlRequest<>(
                "Execute-Statement",
                new ExpectStatementResult<>(
                        connection,
                        Row.RowDecodingType.BINARY,
                        eventHandler,
                        accumulator,
                        callback,
                        entry),
                new PreparedStatementRequest(stmp.getHandlerId(), stmp.getParametersTypes(), data),
                callback);
    }

    public static MySqlRequest executeUpdate(
            MySqlConnection connection,
            String sql,
            DbCallback<Result> callback,
            StackTraceElement[] entry) {
        return new MySqlRequest<>(
                "Update: " + sql,
                new ExpectUpdateResult<>(connection, callback, entry),
                new StringCommandRequest(Command.QUERY, sql),
                callback);
    }

    public static MySqlRequest prepareQuery(
            MySqlConnection connection,
            String sql,
            DbCallback<MySqlPreparedStatement> callback,
            StackTraceElement[] entry) {

        return new MySqlRequest<>("Prepare-Query: " + sql,
                new ExpectPreparQuery(connection, callback, entry),
                new StringCommandRequest(Command.STATEMENT_PREPARE, sql),
                callback);
    }

    public static MySqlRequest closeStatemeent(
            MySqlConnection connection,
            StatementPreparedEOF statementInfo,
            DbCallback<Void> callback) {
        return new MySqlRequest<>(
                "Close-Statement: ",
                new AcceptNextResponse(connection),
                new ClosePreparedStatementRequest(statementInfo.getHandlerId()), callback);
    }

    public static MySqlRequest beginTransaction(MySqlConnection connection,
                                                DbCallback<Void> callback,
                                                StackTraceElement[] entry) {

        return new MySqlRequest<>(
                "Begin-Transaction: ",
                new ExpectUpdateResult<>(connection, callback, entry, TO_VOID),
                new StringCommandRequest(Command.QUERY, "begin"),
                callback);
    }

    public static MySqlRequest commitTransaction(MySqlConnection connection,
                                                 DbCallback<Void> callback,
                                                 StackTraceElement[] entry) {
        return new MySqlRequest<>(
                "Commit-Transaction: ",
                new ExpectUpdateResult<>(connection, callback, entry, TO_VOID),
                new StringCommandRequest(Command.QUERY, "commit"),
                callback);
    }

    public static MySqlRequest rollbackTransaction(MySqlConnection connection,
                                                   DbCallback<Void> callback,
                                                   StackTraceElement[] entry) {
        return new MySqlRequest<>(
                "Rollback-Transaction: ",
                new ExpectUpdateResult<>(connection, callback, entry, TO_VOID),
                new StringCommandRequest(Command.QUERY, "rollback"),
                callback);
    }
}
