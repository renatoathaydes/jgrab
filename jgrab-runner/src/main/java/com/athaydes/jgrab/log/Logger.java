package com.athaydes.jgrab.log;

/**
 * Internal logger.
 */
public class Logger {

    private static final String XXX = "--------------";

    private static boolean enabled = System.getProperty( "jgrab.debug" ) != null;

    public static void log( String message ) {
        if ( enabled ) System.err.println( message );
    }

    public static void logPhase( String phase ) {
        if ( enabled ) System.err.println( XXX + " " + phase + " " + XXX );
    }

    public static void error( String errorMessage ) {
        System.err.println( "ERROR: " + errorMessage );
    }
}
