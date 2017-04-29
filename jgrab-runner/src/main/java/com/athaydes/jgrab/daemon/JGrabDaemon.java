package com.athaydes.jgrab.daemon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.BiConsumer;

/**
 * JGrab daemon, a TCP socket server that runs in the background waiting for Java code to run.
 */
public class JGrabDaemon {

    private static final Logger logger = LoggerFactory.getLogger( JGrabDaemon.class );
    private static final String STOP_OPTION = "--stop";

    public static void start( BiConsumer<String, String[]> runArgs ) {
        new Thread( () -> {
            ServerSocket serverSocket;
            try {
                serverSocket = new ServerSocket( 5002 );
            } catch ( Exception e ) {
                logger.error( "Unable to start JGrab daemon!", e );
                return;
            }

            while ( true ) {
                try (
                        Socket clientSocket = serverSocket.accept();
                        final PrintStream out = new PrintStream( clientSocket.getOutputStream(), true );
                        BufferedReader in = new BufferedReader(
                                new InputStreamReader( clientSocket.getInputStream() ) );
                ) {
                    String inputLine;
                    String currentDir = null;
                    boolean isFirstArg = true;
                    StringBuilder firstArg = new StringBuilder( 64 );
                    StringBuilder secondArg = new StringBuilder( 512 );

                    while ( ( inputLine = in.readLine() ) != null ) {
                        inputLine = inputLine.trim();
                        logger.debug( "Got line: '" + inputLine + "'" );

                        // first line is expected to be the currentDir
                        if ( currentDir == null ) {
                            currentDir = inputLine;
                            continue;
                        }

                        if ( inputLine.isEmpty() ) {
                            continue;
                        }

                        if ( inputLine.equals( "JGRAB_END" ) ) {
                            break;
                        }

                        if ( isFirstArg ) {
                            int firstSpace = inputLine.indexOf( ' ' );
                            if ( firstSpace > 0 ) {
                                firstArg.append( inputLine.substring( 0, firstSpace ) );
                                secondArg.append( inputLine.substring( firstSpace + 1 ) ).append( '\n' );
                            } else {
                                firstArg.append( inputLine );
                            }

                            isFirstArg = false;
                        } else {
                            secondArg.append( inputLine ).append( '\n' );
                        }
                    }

                    String arg0 = firstArg.toString();
                    String arg1 = secondArg.toString();

                    logger.debug( "Daemon received options: '{}', '{}'", arg0, arg1 );
                    String[] args = arg1.isEmpty() ?
                            new String[]{ arg0 } :
                            new String[]{ arg0, arg1 };

                    System.setIn( clientSocket.getInputStream() );
                    System.setOut( out );
                    System.setErr( out );

                    if ( firstArg.substring( 0, Math.min( firstArg.length(), STOP_OPTION.length() ) )
                            .equals( STOP_OPTION ) ) {
                        logger.info( "--stop option received, stopping JGrab Daemon" );
                        out.println( "JGrab Daemon stopped" );
                        return;
                    }

                    // run this synchronously, which means only one program can run per daemon at a time
                    runArgs.accept( currentDir, args );
                } catch ( IOException e ) {
                    e.printStackTrace();
                } finally {
                    System.setIn( System.in );
                    System.setOut( System.out );
                    System.setErr( System.err );
                }
            }
        }, "jgrab-daemon" ).start();
    }

}
