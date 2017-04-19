package com.athaydes.jgrab.runner;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class JGrabRunner {

    private static String[] javaLocations() {
        return new String[]{
                System.getenv( "JAVA_HOME" ),
                System.getProperty( "java.home" )
        };
    }

    private static JGrabOptions parseOptions( String[] args ) {
        if ( args.length == 0 ) {
            return error( "No arguments provided" );
        }
        if ( args.length == 1 ) {
            return new JGrabOptions( JGrabRunnable.JAVA_FILE, args[ 0 ] );
        }
        if ( args.length == 2 ) {
            if ( args[ 0 ].equals( "-e" ) ) {
                return new JGrabOptions( JGrabRunnable.JAVA_SOURCE_CODE, args[ 1 ] );
            }
        }

        return error( "Unrecognized options: " + Arrays.toString( args ) );
    }

    private static JGrabOptions error( String reason ) {
        throw new RuntimeException( reason + "\n\nUsage: jgrab (-e <java_source>) | java_file" );
    }

    private static File getJava() {
        File java = null;

        for (String javaHome : javaLocations()) {
            File candidateJava = new File( javaHome, "/bin/java" );

            if ( candidateJava.isFile() ) {
                java = candidateJava;
                break;
            }
        }

        if ( java == null ) {
            throw new RuntimeException( "Cannot locate Java, tried locations: " + Arrays.toString( javaLocations() ) );
        }

        return java;
    }

    private static void run( File java, String classpath, JGrabOptions options ) throws Exception {
        if ( options.jGrabRunnable == JGrabRunnable.JAVA_SOURCE_CODE ) {
            throw new RuntimeException( "java_source not supported yet" );
        }

        if ( classpath == null || classpath.trim().isEmpty() ) {
            throw new RuntimeException( "classpath was not provided" );
        }

        System.out.println( "JGrab running " + options.arg + " with cp = " + classpath );

        Process process = new ProcessBuilder().command(
                java.getAbsolutePath(), "-cp", classpath, options.arg
        ).inheritIO().start();

        boolean ok = process.waitFor( 3, TimeUnit.SECONDS );

        if ( !ok ) {
            process.destroyForcibly();
            throw new RuntimeException( "Process timeout" );
        }

        System.out.println( "Process exited with value " + process.exitValue() );
    }

    public static void main( String[] args ) {
        JGrabOptions options = parseOptions( args );
        File java = getJava();
        String cp = System.getProperty( "tests.classpath" );

        try {
            run( java, cp, options );
        } catch ( Exception e ) {
            System.err.println( "ERROR: " + e.toString() );
        }
    }

}

enum JGrabRunnable {
    JAVA_SOURCE_CODE, JAVA_FILE
}

class JGrabOptions {
    final JGrabRunnable jGrabRunnable;
    final String arg;

    JGrabOptions( JGrabRunnable jGrabRunnable, String arg ) {
        this.jGrabRunnable = jGrabRunnable;
        this.arg = arg;
    }
}