package org.adbcj;

/**
 * The core callback for all operations in ADBCJ. A operaition in ADBCJ ends in a call of {@see #onComplete}.
 * On a success, the result is passed, and the failure null. A result can be null.
 * On a failure, the result is null and failure an exception.
 *
 * The callback is called on an thread of the ADBCJ drivers thread pool.
 * You most not do any blocking operations in the callback.
 * You can issue new operations on the {@see org.adbcj.Connection} in the callback.
 *
 * @param <T>
 */
public interface DbCallback<T> {

    /**
     * Called when a ADBCJ operation completes.
     * On a success, the result is passed, and the failure null. A result can be null.
     * On a failure, the result is null and failure an exception.
     * @param result if the {@param failure} is null, it contains the result. Some operaitions may return null as result
     * @param failure if not null, a failure occured. The exception contains more information
     */
    void onComplete(T result, DbException failure);
}
