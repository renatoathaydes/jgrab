package com.athaydes.jgrab.log;

/**
 * Internal logger.
 */
public class Logger {

    private static final String XXX = "--------------";

    public static void log( String message ) {
        System.err.println( message );
    }

    public static void logPhase( String phase ) {
        System.err.println( XXX + " " + phase + " " + XXX );
    }

}
