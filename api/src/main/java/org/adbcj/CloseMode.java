package org.adbcj;


public enum CloseMode {
    /**
     * Closes when pending operations have completed. All enqueued requests will be finished, before the resource is closed.
     *
     * However, no new requests / commands will be accepted.
     */
    CLOSE_GRACEFULLY,
    /**
     * Cancels all pending requests and the closes the resource.
     * No new requests / commands will be accepted.
     */
    CANCEL_PENDING_OPERATIONS,
	/**
     * Cancels all pending requests and then closes the resource forcibly even if using connection pool.
     * No new requests / commands will be accepted.
     * @since 2017-09-02 little-pan
     */
    CLOSE_FORCIBLY;
}
