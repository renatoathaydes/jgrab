package com.athaydes.jgrab.test;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class JGrabTest {

    @Test
    public void runRunnableClass() throws Exception {
        File java = new File( System.getProperty( "java.home" ), "bin/java" );

        String cp = System.getProperty( "tests.classpath" );
        System.out.println( "Test classpath: " + cp );
        String runnableClass = getClass().getResource( "/RunnableClass.java" ).getFile();

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

        System.out.println( "Process exited with value " + process.exitValue() );
        System.out.println( errorOutput.get( 5, TimeUnit.SECONDS ) );

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
                return output.toString( "UTF-8" );
            } catch ( IOException e ) {
                return e.toString();
            }
        } );
    }


}
