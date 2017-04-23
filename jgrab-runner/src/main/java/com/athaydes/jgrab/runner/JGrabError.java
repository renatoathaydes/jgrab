package com.athaydes.jgrab.runner;

/**
 * Expected Error when running JGrab.
 */
public class JGrabError extends RuntimeException {

    public JGrabError( String message ) {
        super( message );
    }
}
