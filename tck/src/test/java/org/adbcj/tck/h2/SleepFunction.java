package org.adbcj.tck.h2;

/**
 * Used in test suite to artificially delay a response
 */
public class SleepFunction {
    public static int sleep(int value) {
        long inSeconds = value*1000;
        try {
            Thread.sleep(inSeconds);
        } catch (InterruptedException e) {
            return 0;
        }
        return 0;
    }
}
