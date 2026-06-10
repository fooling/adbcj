package org.adbcj.h2;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;


final class MathUtils {
    /**
     * The secure random object.
     */
    static SecureRandom secureRandom;

    static {
        try{
            secureRandom = SecureRandom.getInstance("SHA1PRNG");
        } catch (NoSuchAlgorithmException e){
            warn("SecureRandom", e);
            secureRandom = new SecureRandom();
        }
    }

    /**
     * Get a number of cryptographically secure pseudo random bytes.
     *
     * @param len the number of bytes
     * @return the random bytes
     */
    static byte[] secureRandomBytes(int len) {
        if (len <= 0) {
            len = 1;
        }
        byte[] buff = new byte[len];
        secureRandom.nextBytes(buff);
        return buff;
    }

    /**
     * Print a message to system output if there was a problem initializing the
     * random number generator.
     *
     * @param s the message to print
     * @param t the stack trace
     */
    static void warn(String s, Throwable t) {
        // not a fatal problem, but maybe reduced security
        System.out.println("Warning: " + s);
        if (t != null) {
            t.printStackTrace();
        }
    }


}
