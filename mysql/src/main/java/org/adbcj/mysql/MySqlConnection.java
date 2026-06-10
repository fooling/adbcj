package org.adbcj.mysql;

import org.adbcj.*;
import org.adbcj.mysql.codec.ClientCapabilities;
import org.adbcj.mysql.codec.ExtendedClientCapabilities;
import org.adbcj.mysql.codec.MySqlRequest;
import org.adbcj.mysql.codec.MySqlRequests;
import io.netty.channel.Channel;
import org.adbcj.support.CloseOnce;
import org.adbcj.support.LoginCredentials;
import org.adbcj.support.stacktracing.StackTracingOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.Set;

public class MySqlConnection implements Connection {

    private static final Logger logger = LoggerFactory.getLogger(MySqlConnection.class);

    private final LoginCredentials login;
    private final int maxQueueSize;
    private final MysqlConnectionManager connectionManager;
    private final Channel channel;

    protected final int id;
    final StackTracingOptions strackTraces;

    private final ArrayDeque<MySqlRequest> requestQueue;

    private final Object lock = new Object();
    private final CloseOnce closer = new CloseOnce();
    private volatile boolean isInTransaction = false;

    public MySqlConnection(
            LoginCredentials login,
            int maxQueueSize,
            MysqlConnectionManager connectionManager,
            Channel channel,
            StackTracingOptions strackTraces) {
        this.login = login;
        this.maxQueueSize = maxQueueSize;
        this.connectionManager = connectionManager;
        this.channel = channel;
        this.id = connectionManager.nextId();
        this.strackTraces = strackTraces;

        synchronized (lock) {
            requestQueue = new ArrayDeque<>(maxQueueSize + 1);
        }
    }


    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }


    @Override
    public void beginTransaction(DbCallback<Void> callback) {
        if (isInTransaction()) {
            throw new DbException("This connection is already in a transaction");
        }
        checkClosed();
        StackTraceElement[] entry = strackTraces.captureStacktraceAtEntryPoint();
        synchronized (lock) {
            forceQueRequest(MySqlRequests.beginTransaction(this, callback, entry));
            isInTransaction = true;
        }
    }

    @Override
    public void commit(DbCallback<Void> callback) {
        if (!isInTransaction()) {
            throw new DbException("No transaction has been started to commit");
        }
        checkClosed();
        StackTraceElement[] entry = strackTraces.captureStacktraceAtEntryPoint();
        synchronized (lock) {
            if (failIfQueueFull(MySqlRequests.commitTransaction(this, callback, entry))) {
                isInTransaction = false;
            }
        }
    }


    @Override
    public void rollback(DbCallback<Void> callback) {
        if (!isInTransaction()) {
            throw new DbException("No transaction has been started to rollback");
        }
        checkClosed();
        StackTraceElement[] entry = strackTraces.captureStacktraceAtEntryPoint();
        doRollback(entry, callback);
    }

    private void doRollback(StackTraceElement[] entry, DbCallback<Void> callback) {
        synchronized (lock) {
            if (failIfQueueFull(MySqlRequests.rollbackTransaction(this, callback, entry))) {
                isInTransaction = false;
            }
        }
    }

    @Override
    public boolean isInTransaction() {
        return isInTransaction;
    }


    @Override
    public <T> void executeQuery(String sql, ResultHandler<T> eventHandler, T accumulator, DbCallback<T> callback) {
        checkClosed();
        StackTraceElement[] entry = strackTraces.captureStacktraceAtEntryPoint();
        failIfQueueFull(MySqlRequests.executeQuery(
                this,
                sql,
                eventHandler,
                accumulator,
                callback,
                entry));
    }


    @Override
    public void executeUpdate(String sql, DbCallback<Result> callback) {
        checkClosed();
        StackTraceElement[] entry = strackTraces.captureStacktraceAtEntryPoint();
        failIfQueueFull(
                MySqlRequests.executeUpdate(this,
                        sql,
                        callback,
                        entry
                ));

    }


    @Override
    public void prepareQuery(String sql, DbCallback<PreparedQuery> callback) {
        checkClosed();
        StackTraceElement[] entry = strackTraces.captureStacktraceAtEntryPoint();
        failIfQueueFull(MySqlRequests.prepareQuery(this, sql, (DbCallback) callback, entry));
    }

    @Override
    public void prepareUpdate(String sql, DbCallback<PreparedUpdate> callback) {
        checkClosed();
        StackTraceElement[] entry = strackTraces.captureStacktraceAtEntryPoint();
        failIfQueueFull(MySqlRequests.prepareQuery(this, sql, (DbCallback) callback, entry));
    }

    @Override
    public void close(CloseMode closeMode, DbCallback<Void> callback) throws DbException {
        StackTraceElement[] entry = strackTraces.captureStacktraceAtEntryPoint();
        synchronized (lock) {
            closer.requestClose(callback, () -> {
            	// Close the connection forcibly when IO error such as 'Too many connections' 
            	//even if using connection-pool.
            	// @since 2017-09-02 little-pan
                if (connectionManager.connectionPool == null || CloseMode.CLOSE_FORCIBLY == closeMode) {
                    doActualClose(closeMode, entry);
                } else {
                    doRollback(entry, (result, failure) -> {
                        if (failure == null) {
                            if (this.connectionManager.isClosed()) {
                                doActualClose(closeMode, entry);
                            } else {
                                channel.pipeline().remove(MysqlConnectionManager.DECODER);
                                connectionManager.connectionPool.release(login, channel);
                                callback.onComplete(result, null);
                            }
                        } else {
                            callback.onComplete(null,
                                    new DbException("Failed to rollback transaction and return connection to pool", failure));
                        }
                    });
                }
            });
        }
    }

    private void doActualClose(CloseMode closeMode, StackTraceElement[] entry) {
    	if (closeMode == CloseMode.CLOSE_FORCIBLY) {
        	// Close the connection forcibly
        	forceCloseOnPendingRequests();
        	realClose(entry);
        	return;
        }
        if (closeMode == CloseMode.CANCEL_PENDING_OPERATIONS) {
            forceCloseOnPendingRequests();
        }
        final MySqlRequest closeRequest = MySqlRequests.createCloseRequest(
                this,
                (res, error) -> tryCompleteClose(error),
                entry);
        forceQueRequest(closeRequest);
    }
    
    private void realClose(final StackTraceElement[] entry) {
    	final Channel ch = channel;
    	ch.close().addListener((f)->{
    		logger.debug("Real close channel#{}", ch.id());
    		final DbException failure;
    		if(f.cause() == null) {
    			failure = null;
    		}else {
    			failure = DbException.wrap(f.cause(), entry);
    		}
    		tryCompleteClose(failure);
    	});
    }

    void tryCompleteClose(DbException error) {
        synchronized (lock) {
            connectionManager.closedConnect(this);
            closer.didClose(error);
        }
    }


    @Override
    public boolean isClosed() throws DbException {
        return closer.isClose();
    }


    void checkClosed() {
        if (isClosed()) {
            throw new DbConnectionClosedException("This connection is closed");
        }
    }

    private static final Set<ClientCapabilities> CLIENT_CAPABILITIES = EnumSet.of(
            ClientCapabilities.LONG_PASSWORD,
            ClientCapabilities.FOUND_ROWS,
            ClientCapabilities.LONG_COLUMN_FLAG,
            ClientCapabilities.CONNECT_WITH_DB,
            ClientCapabilities.LOCAL_FILES,
            ClientCapabilities.PROTOCOL_4_1,
            ClientCapabilities.TRANSACTIONS,
            ClientCapabilities.SECURE_AUTHENTICATION);

    public Set<ClientCapabilities> getClientCapabilities() {
        return CLIENT_CAPABILITIES;
    }

    private static final Set<ExtendedClientCapabilities> EXTENDED_CLIENT_CAPABILITIES = EnumSet.of(
            ExtendedClientCapabilities.MULTI_RESULTS
    );

    public Set<ExtendedClientCapabilities> getExtendedClientCapabilities() {
        return EXTENDED_CLIENT_CAPABILITIES;
    }

    boolean failIfQueueFull(MySqlRequest request) {
        synchronized (lock) {

            int requestsPending = requestQueue.size();
            if (requestsPending > maxQueueSize) {
                DbException ex = new DbException("To many pending requests. The current maximum is " + maxQueueSize
                        + ". Ensure that your not overloading the database with requests. " +
                        "Also check the " + StandardProperties.MAX_QUEUE_LENGTH + " property");
                request.callback.onComplete(null, ex);
                return false;
            }
            forceQueRequest(request);
            return true;
        }
    }

    MySqlRequest forceQueRequest(MySqlRequest request) {
        synchronized (lock) {
            requestQueue.add(request);
            channel.writeAndFlush(request.request);
            return request;
        }
    }

    public MySqlRequest dequeRequest() {
        synchronized (lock) {
            final MySqlRequest request = requestQueue.poll();
            if (logger.isDebugEnabled()) {
                logger.debug("Dequeued request: {}", request);
            }
            return request;
        }
    }


    private void forceCloseOnPendingRequests() {
        DbConnectionClosedException closed = new DbConnectionClosedException("Connection is closed");
        for (MySqlRequest request : requestQueue) {
            request.callback.onComplete(null, closed);

        }
    }
}
