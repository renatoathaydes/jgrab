package com.athaydes.jgrab.runner;

import com.athaydes.jgrab.log.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.time.Duration;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * A handler of native processes.
 */
class ProcessHandler {

    private final String name;
    private final ProcessBuilder processBuilder;
    private final boolean inheritSysout;

    ProcessHandler( String name, ProcessBuilder processBuilder, boolean inheritSysout ) {
        this.name = name;
        this.processBuilder = processBuilder;
        this.inheritSysout = inheritSysout;
    }

    private static void consumeStream( InputStream input, PrintStream output ) {
        CompletableFuture.runAsync( () -> {
            Scanner scanner = new Scanner( input );
            while ( scanner.hasNextLine() ) {
                output.println( scanner.nextLine() );
            }
        } );
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

    int run() throws Exception {
        return run( null );
    }

    int run( Duration timeout ) throws Exception {
        Logger.logPhase( name + " started" );
        Process process = processBuilder.start();

        Future<String> sysout = inheritSysout ?
                CompletableFuture.supplyAsync( () -> "Sysout was consumed" ) :
                copyStream( process.getInputStream() );

        Future<String> syserr = copyStream( process.getErrorStream() );

        if ( inheritSysout ) {
            consumeStream( process.getInputStream(), System.out );
        }

        boolean ok = timeout == null ?
                process.waitFor() == 0 :
                process.waitFor( timeout.toMillis(), TimeUnit.MILLISECONDS );

        Logger.logPhase( name + " sysout" );
        Logger.log( sysout.get() );
        Logger.logPhase( name + " syserr" );
        Logger.log( syserr.get() );
        Logger.logPhase( name + " exit code = " + process.exitValue() );

        if ( !ok ) {
            process.destroyForcibly();
            throw new RuntimeException( name + " process timeout" );
        }

        return process.exitValue();
    }


}
