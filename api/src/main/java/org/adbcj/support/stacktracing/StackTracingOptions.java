package org.adbcj.support.stacktracing;

import org.adbcj.support.ConnectionManagerFactory;

public enum StackTracingOptions{
    /**
     * Only trace when the JVM "org.adbcj.debug" flag is set to true.
     *
     * Activate tracing at start up with it with -Dorg.adbcj.debug=true
     */
    GLOBAL_DEFAULT{
        private final boolean isOn = Boolean.getBoolean("org.adbcj.debug");
        @Override
        public StackTraceElement[] captureStacktraceAtEntryPoint() {
            if(isOn){
                return Thread.currentThread().getStackTrace();
            } else{
                return null;
            }
        }

    },
    /**
     * This {@link ConnectionManagerFactory} or connection wants to have a stack-trace captured,
     * no mather what.
     */
    FORCED_BY_INSTANCE{
        @Override
        public StackTraceElement[] captureStacktraceAtEntryPoint() {
            return Thread.currentThread().getStackTrace();
        }
    };

    public abstract StackTraceElement[] captureStacktraceAtEntryPoint();
}
