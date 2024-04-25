package com.athaydes.jgrab.test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;

final class JGrabTestRunner {

    public static ProcessResult jgrab( String... args )
            throws Exception {
        File java = new File( System.getProperty( "java.home" ), "bin/java" );

        String cp = System.getProperty( "tests.classpath" );

        var tempDir = Files.createTempDirectory( JGrabTest.class.getName() );

        Instant startTime = Instant.now();

        var procBuilder = new ProcessBuilder();
        procBuilder.environment().put( "JGRAB_HOME", tempDir.toString() );

        var command = new ArrayList<String>();
        command.addAll( List.of( java.getAbsolutePath(),
                "-cp", cp,
                "com.athaydes.jgrab.runner.JGrabRunner" ) );
        command.addAll( List.of( args ) );

        var process = new ProcessBuilder()
                .command( command )
                .start();

        Future<String> output = copyStream( process.getInputStream() );
        Future<String> errorOutput = copyStream( process.getErrorStream() );

        boolean ok = process.waitFor( 30, TimeUnit.SECONDS );

        if ( !ok ) {
            process.destroyForcibly();
            throw new RuntimeException( "Process timeout" );
        }

        var stdout = output.get( 5, TimeUnit.SECONDS );
        var stderr = errorOutput.get( 5, TimeUnit.SECONDS );
        var time = Duration.between( startTime, Instant.now() );

        System.out.println( "Process exited with value " + process.exitValue()
                + " in " + time.toMillis() + "ms" );

        return new ProcessResult( process.exitValue(), stdout, stderr );
    }

    private static Future<String> copyStream( InputStream input ) {
        return CompletableFuture.supplyAsync( () -> {
            byte[] buffer = new byte[ 256 ];
            ByteArrayOutputStream output = new ByteArrayOutputStream( 256 );

            int bytesRead;

            try {
                while ( ( bytesRead = input.read( buffer ) ) != -1 ) {
                    output.write( buffer, 0, bytesRead );
                }
                return output.toString( UTF_8 );
            } catch ( IOException e ) {
                return e.toString();
            }
        } );
    }
}

