package com.athaydes.jgrab.test;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class JGrabTest {

    @Test
    public void runRunnableClass() throws Exception {
        File java = new File( System.getProperty( "java.home" ), "bin/java" );

        String cp = System.getProperty( "tests.classpath" );
        System.out.println( "Test classpath: " + cp );
        String runnableClass = Objects.requireNonNull(
                getClass().getResource( "/RunnableClass.java" )
        ).getFile();

        Instant startTime = Instant.now();

        Process process = new ProcessBuilder()
                .command( java.getAbsolutePath(),
                        "-cp", cp,
                        "com.athaydes.jgrab.runner.JGrabRunner",
                        runnableClass )
                .start();

        Future<String> output = copyStream( process.getInputStream() );
        Future<String> errorOutput = copyStream( process.getErrorStream() );

        boolean ok = process.waitFor( 30, TimeUnit.SECONDS );

        if ( !ok ) {
            process.destroyForcibly();
            throw new RuntimeException( "Process timeout" );
        }

        System.out.println( "Process exited with value " + process.exitValue()
                + " in " + Duration.between( startTime, Instant.now() ).toMillis() + "ms" );

        if ( process.exitValue() != 0 ) {
            System.out.println( "===> Process stderr:\n" + errorOutput.get( 5, TimeUnit.SECONDS ) );
            System.out.println( "===> Process stdout:\n" + output.get( 5, TimeUnit.SECONDS ) );
            System.out.println( "----------------------------------" );
            fail( "Process exited with code " + process.exitValue() );
        }

        String processOutput = output.get( 5, TimeUnit.SECONDS );

        assertEquals( "error output: " + errorOutput.get( 1, TimeUnit.SECONDS ),
                "Hello interface com.athaydes.osgiaas.cli.core.CommandRunner\n" +
                        "Privyet interface com.athaydes.osgiaas.cli.CommandCompleter\n" +
                        "Tja class com.athaydes.osgiaas.api.ansi.Ansi\n" +
                        "Hi interface org.apache.felix.shell.Command\n" +
                        "Ola class jline.console.ConsoleReader\n", processOutput );
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
