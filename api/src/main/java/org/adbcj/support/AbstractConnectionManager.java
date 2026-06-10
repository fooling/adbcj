package org.adbcj.support;

import org.adbcj.*;
import org.adbcj.support.stacktracing.StackTracingOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


public abstract class AbstractConnectionManager implements ConnectionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractConnectionManager.class);
    protected final Map<String, String> properties;
    private final StackTracingOptions stackTracingOption;
    private final HashSet<Connection> connections = new HashSet<Connection>();
    private final CloseOnce closer = new CloseOnce();
    protected final boolean useConnectionPool;

    public AbstractConnectionManager(Map<String, String> properties) {
        this.properties = Collections.unmodifiableMap(properties);
        this.stackTracingOption = readStackTracingOption(properties);
        this.useConnectionPool = readConnectionPoolEnabled(properties);
    }



    protected final void addConnection(Connection connection) {
        synchronized (connections) {
            connections.add(connection);
        }
    }

    protected final void removeConnection(Connection connection) {
        synchronized (connections) {
            connections.remove(connection);
        }
    }

    @Override
    public final void close(CloseMode mode, DbCallback<Void> callback) throws DbException {
        StackTraceElement[] entry = entryPointStack();
        closer.requestClose(callback, () -> {
            ArrayList<Connection> connectionsCopy;
            synchronized (connections) {
                connectionsCopy = new ArrayList<>(connections);
            }
            if (connectionsCopy.isEmpty()) {
                doClose((result, failure) -> closer.didClose(failure), entry);
                closer.didClose(null);
            } else {
                for (Connection connection : connectionsCopy) {
                    doCloseConnection(connection, mode, (success, failure) -> {
                        if(failure!=null){
                            LOGGER.info("Exception in connection close",failure);
                        }
                        boolean noConnectionLeft;
                        synchronized (connections) {
                            connections.remove(connection);
                            noConnectionLeft = connections.isEmpty();
                        }
                        if (noConnectionLeft) {
                            doClose((result, closeFailure) -> closer.didClose(closeFailure), entry);
                        }
                    });
                }

            }
        });
    }

    /**
     * Close the given connection. Do not return it to the pool. This is done when the connection manager is shutting down.
     */
    protected abstract void doCloseConnection(Connection connection, CloseMode mode, DbCallback<Void> callback);

    protected abstract void doClose(DbCallback<Void> callback, StackTraceElement[] entry);

    public final boolean isClosed() {
        return closer.isClose();
    }


    protected int maxQueueLength() {
        try {
            int maxConnections = Integer.parseInt(properties.get(StandardProperties.MAX_QUEUE_LENGTH));
            if (maxConnections <= 0) {
                throw new IllegalArgumentException("The property " + StandardProperties.MAX_QUEUE_LENGTH + " has to be positive number");
            }
            return maxConnections;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("The property " + StandardProperties.MAX_QUEUE_LENGTH + " has to be positive number");
        }
    }

    private static StackTracingOptions readStackTracingOption(Map<String, String> properties) {
        final String callStackEnabled = properties.get(StandardProperties.CAPTURE_CALL_STACK);
        if (null != callStackEnabled && callStackEnabled.equalsIgnoreCase("true")) {
            return StackTracingOptions.FORCED_BY_INSTANCE;
        } else {
            return StackTracingOptions.GLOBAL_DEFAULT;
        }
    }

    private boolean readConnectionPoolEnabled(Map<String, String> properties){
        String value = properties.get(StandardProperties.CONNECTION_POOL_ENABLE);
        return "true".equalsIgnoreCase(value);
    }

    protected StackTracingOptions getStackTracingOption() {
        return stackTracingOption;
    }

    protected StackTraceElement[] entryPointStack() {
        return stackTracingOption.captureStacktraceAtEntryPoint();
    }

}
