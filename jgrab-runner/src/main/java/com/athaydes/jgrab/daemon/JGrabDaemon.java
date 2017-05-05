package com.athaydes.jgrab.daemon;

import com.athaydes.jgrab.Dependency;
import com.athaydes.jgrab.code.JavaCode;
import com.athaydes.jgrab.code.StringJavaCode;
import com.athaydes.jgrab.ivy.IvyGrabber;
import com.athaydes.jgrab.runner.JGrabRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * JGrab daemon, a TCP socket server that runs in the background waiting for Java code to run.
 */
public class JGrabDaemon {

    private static final Logger logger = LoggerFactory.getLogger( JGrabDaemon.class );
    private static final String STOP_OPTION = "--stop";

    private static final Map<Set<Dependency>, List<File>> libsCache = new HashMap<>();

    public static void start( BiConsumer<JavaCode, List<File>> runArgs ) {
        new Thread( () -> {
            ServerSocket serverSocket;
            try {
                serverSocket = new ServerSocket( 5002 );
            } catch ( Exception e ) {
                logger.error( "Unable to start JGrab daemon!", e );
                return;
            }

            while ( true ) {
                try ( Socket clientSocket = serverSocket.accept();
                      final PrintStream out = new PrintStream( clientSocket.getOutputStream(), true );
                      BufferedReader in = new BufferedReader(
                              new InputStreamReader( clientSocket.getInputStream() ) ) ) {
                    String inputLine;
                    StringBuilder messageBuilder = new StringBuilder( 1024 );

                    while ( ( inputLine = in.readLine() ) != null ) {
                        logger.debug( "Got line {}", inputLine );
                        messageBuilder.append( inputLine ).append( '\n' );
                    }

                    String[] args = messageBuilder.toString().split( " ", 2 );

                    logger.debug( "Daemon received options: {}", Arrays.toString( args ) );

                    JavaCode code = null;

                    if ( args.length == 1 ) {
                        switch ( args[ 0 ].trim() ) {
                            case STOP_OPTION:
                                logger.info( "--stop option received, stopping JGrab Daemon" );
                                out.println( "=== JGrab Daemon stopped ===" );
                                return;
                            default: // try to execute the argument
                                logger.debug( "Received code to execute" );
                                code = new StringJavaCode( args[ 0 ] );
                        }
                    } else if ( args.length == 2 ) {
                        if ( args[ 0 ].trim().equals( JGrabRunner.SNIPPET_OPTION ) ) {
                            code = new StringJavaCode( args[ 1 ] );
                        } else {
                            logger.debug( "Received code to execute" );
                            code = new StringJavaCode( String.join( " ", args ) );
                        }
                    }

                    // if we got here, there must be JavaCode to run, or it's an error
                    if ( code == null ) {
                        out.println( "ERROR: Invalid options. Use -h or --help to see usage." );
                    } else {
                        Set<Dependency> deps = code.extractDependencies();

                        List<File> libs = libsCache.computeIfAbsent( deps,
                                ( dependencies ) -> IvyGrabber.getInstance().grab( dependencies ) );

                        System.setIn( clientSocket.getInputStream() );
                        System.setOut( out );
                        System.setErr( out );

                        // run this synchronously, which means only one program can run per daemon at a time
                        try {
                            runArgs.accept( code, libs );
                        } catch ( Throwable t ) {
                            out.println( t.toString() );
                            t.printStackTrace( out );
                        }
                    }
                } catch ( IOException e ) {
                    logger.warn( "Problem handling client message", e );
                } finally {
                    System.setIn( System.in );
                    System.setOut( System.out );
                    System.setErr( System.err );
                }
            }
        }, "jgrab-daemon" ).start();
    }

}
