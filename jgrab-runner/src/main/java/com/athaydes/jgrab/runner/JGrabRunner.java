package com.athaydes.jgrab.runner;

import com.athaydes.jgrab.JGrab;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 *
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

        File selfJar = JarHandler.getJarOf( JGrabAnnotationProcessor.class ).orElseThrow( () ->
                new RuntimeException( "Unable to locate self jar, JGrab can only run from a jar file!" ) );

        File jgrabApiJar = JarHandler.getJarOf( JGrab.class ).orElseThrow( () ->
                new RuntimeException( "Unable to locate JGrab jar, please add the jgrab-api jar to the classpath!" ) );

        String cp = String.join( File.pathSeparator, Arrays.asList(
                selfJar.getAbsolutePath(),
                jgrabApiJar.getAbsolutePath() ) );

        Path tempDir = getTempDir();

        ProcessBuilder processBuilder = new ProcessBuilder().command(
                javaInfo.javac.getAbsolutePath(),
                "-cp", cp,
                options.arg
        ).inheritIO();

        processBuilder.environment().put( JGrabAnnotationProcessor.JGRAB_TEMP_DIR_ENV_VAR, tempDir.toString() );

        Process process = processBuilder.start();

        boolean ok = process.waitFor( 5, TimeUnit.SECONDS );

        if ( !ok ) {
            process.destroyForcibly();
            throw new RuntimeException( "javac process timeout" );
        }

        int exitCode = process.exitValue();

        if ( exitCode != 0 ) {
            throw new RuntimeException( "Error compiling Java code, see the process output for details." );
        }
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