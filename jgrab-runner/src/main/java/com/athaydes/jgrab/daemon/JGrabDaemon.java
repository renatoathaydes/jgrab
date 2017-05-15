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
import java.util.List;
import java.util.Set;

/**
 * JGrab daemon, a TCP socket server that runs in the background waiting for Java code to run.
 */
public class JGrabDaemon {

    private static final Logger logger = LoggerFactory.getLogger( JGrabDaemon.class );
    private static final String STOP_OPTION = "--stop";

    private static final PersistentCache libsCache = new PersistentCache();

    static {
        Runtime.getRuntime().addShutdownHook( new Thread( () -> {
            try {
                libsCache.save();
            } catch ( IOException e ) {
                logger.warn( "Failed to save dependencies cache", e );
            }
        } ) );
    }

    public static void start( RunArgs runArgs ) {
        new Thread( () -> {
            logger.debug( "Starting JGrab Daemon" );
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
                    String firstLine = null;
                    String inputLine;
                    StringBuilder messageBuilder = new StringBuilder( 1024 );

                    while ( ( inputLine = in.readLine() ) != null ) {
                        if ( firstLine == null ) {
                            firstLine = inputLine;
                        } else {
                            messageBuilder.append( inputLine ).append( '\n' );
                        }
                    }

                    if ( firstLine == null ) {
                        out.println( "Communication error (input line is null)" );
                        continue;
                    }

                    String[] args = firstLine.split( " ", 2 );

                    logger.debug( "Daemon received options: {}", Arrays.toString( args ) );

                    JavaCode code = null;

                    if ( messageBuilder.length() > 0 ) {
                        // if more than one line was given, the only allowed option is -e
                        if ( args.length > 0 && args[ 0 ].equals( JGrabRunner.SNIPPET_OPTION ) ) {
                            if ( args.length == 2 ) {
                                messageBuilder.insert( 0, args[ 1 ] );
                            }

                            code = new StringJavaCode( messageBuilder.toString() );
                        } else {
                            messageBuilder.insert( 0, firstLine );
                            code = new StringJavaCode( messageBuilder.toString() );
                        }
                    } else if ( args.length == 1 ) {
                        switch ( args[ 0 ].trim() ) {
                            case STOP_OPTION:
                                logger.info( "--stop option received, stopping JGrab Daemon" );
                                out.println( "=== JGrab Daemon stopped ===" );
                                return;
                            case JGrabRunner.SNIPPET_OPTION:
                                out.println( "ERROR: no snippet provided to execute" );
                                continue;
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
                        out.println( "ERROR: Invalid options." );
                    } else {
                        Set<Dependency> deps = code.extractDependencies();

                        List<File> libs = libsCache.libsFor( deps,
                                () -> IvyGrabber.getInstance().grab( deps ) );

                        System.setIn( clientSocket.getInputStream() );
                        System.setOut( out );
                        System.setErr( out );

                        // run this synchronously, which means only one program can run per daemon at a time
                        try {
                            runArgs.accept( code, args, libs );
                        } catch ( Throwable t ) {
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

    @FunctionalInterface
    public interface RunArgs {
        void accept( JavaCode javaCode,
                     String[] args,
                     List<File> libs );
    }

}
