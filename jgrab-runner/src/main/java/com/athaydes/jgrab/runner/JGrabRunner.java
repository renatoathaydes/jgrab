package com.athaydes.jgrab.runner;

import com.athaydes.jgrab.Dependency;
import com.athaydes.jgrab.ivy.IvyGrabber;
import com.athaydes.jgrab.log.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * Runs a Java file, using the JGrab annotations to find its dependencies.
 */
public class JGrabRunner {

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

    private static void run( JavaInitializer.JavaInfo javaInfo,
                             JGrabOptions options ) throws Exception {
        if ( options.jGrabRunnable == JGrabRunnable.JAVA_SOURCE_CODE ) {
            throw new RuntimeException( "java_source not supported yet" );
        }

        Path tempDir = getTempDir();

        Logger.log( "JGrab using directory: " + tempDir );

        List<Dependency> toGrab = Dependency.extractDependencies( Paths.get( options.arg ) );
        if ( !toGrab.isEmpty() ) {
            File libDir = new File( tempDir.toFile(), Javac.JGRAB_LIB_DIR );
            libDir.mkdir();
            new IvyGrabber().grab( toGrab, libDir );
        }

        Javac.compile( tempDir, javaInfo, new File( options.arg ) );
        JavaRunner.run( tempDir, javaInfo );
    }

    private static Path getTempDir() {
        try {
            return Files.createTempDirectory( "jgrab" );
        } catch ( IOException e ) {
            throw new RuntimeException( "Unable to create a temp dir for JGrab resources! Cannot run JGrab." );
        }
    }

    public static void main( String[] args ) {
        JGrabOptions options = parseOptions( args );
        JavaInitializer.JavaInfo javaInfo = JavaInitializer.getJavaInfo();

        try {
            run( javaInfo, options );
        } catch ( Exception e ) {
            Logger.log( "ERROR: " + e.toString() );
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