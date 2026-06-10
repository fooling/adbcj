/*
 *   Copyright (c) 2007 Mike Heath.  All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package org.adbcj;

/**
 * Root class for all ADBCJ failures.
 * <p>
 * Due to the async nature, stack traces of a failure often do not contain the location of the root cause.
 * For debugging use, you can enable the 'org.adbcj.debug.capture.callstack=true' property.
 * Either by setting it problematically, or at JVM startup with -Dorg.adbcj.debug.capture.callstack=true.
 * This is a debug feature, it reduces the performance of the application drastically.
 */
public class DbException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public static DbException wrap(Throwable cause) {
        return wrap(cause, null);
    }

    public static DbException wrap(Throwable cause, StackTraceElement[] entryPoint) {
        assert cause != null;
        if (cause instanceof DbException) {
            return (DbException) cause;
        } else if (entryPoint != null) {
            return new DbException(cause.getMessage(), cause, entryPoint);
        } else {
            return new DbException(cause.getMessage(), cause);
        }
    }

    public DbException(String message) {
        super(message);
    }

    public DbException(String message, Throwable cause) {
        super(message, cause);
    }

    public DbException(String message, Throwable cause, StackTraceElement[] entryPoint) {
        super(message, new DbException(message, cause));
        if (entryPoint != null) {
            setStackTrace(entryPoint);
        }
    }


    /**
     * If we have a series of exception, for example in callbacks, we preserve the first exception. It is the most likely root cause.
     * Other exceptions we add as suppressed ones, to not loose that info
     *
     * @param current       The current error
     * @param entry         Entry stack trace, nor null
     * @param previousIfAny The previous exception or null. If not null, will return this exception, with the current one attacted
     */
    public static DbException attachSuppressedOrWrap(Exception current, StackTraceElement[] entry, DbException previousIfAny) {
        if (previousIfAny == null) {
            return wrap(current, entry);
        } else {
            previousIfAny.addSuppressed(current);
            return previousIfAny;
        }
    }
}
