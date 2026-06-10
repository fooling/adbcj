package org.adbcj;

/**
 * Standard properties, which can be passed to {@link ConnectionManagerProvider#createConnectionManager}
 * via property map
 */
public final class StandardProperties {
    private StandardProperties(){}

    /**
     * ADBCJ allows to have multiple async operations open running.
     * This parameter limits how many operations can be open before the implementation throws.
     * It is a per connection setting.
     *
     * This prevents the situation where a connection or system gets over whelmed with open requests.
     * By default this is 64.
     */
    public final static String MAX_QUEUE_LENGTH= "adbcj.maxQueueLength";

    public final static long DEFAULT_QUEUE_LENGTH = 64;

    /**
     * ADBCJ allows you to capture the stack trace of the location which issues a request.
     * However this is a expensive operation, so it's optional. You can force a driver to capture
     * this stack by setting this option.
     *
     * If not set, the JVM global property 'org.adbcj.debug' set to true will force capturing stack trace.
     *
     * This is disabled by default
     */
    public final static String CAPTURE_CALL_STACK= "org.adbcj.debug.capture.callstack";


    /**
     * When set to true, enables the default confection pool.
     * If a driver does not support a connection pool, it should throw a {@link DbException}.
     *
     *
     */
    public final static String CONNECTION_POOL_ENABLE= "org.adbcj.connectionpool.enable";
}
