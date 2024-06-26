package com.athaydes.jgrab.daemon;

import com.athaydes.jgrab.Classpath;
import com.athaydes.jgrab.code.JavaCode;
import com.athaydes.jgrab.code.StringJavaCode;
import com.athaydes.jgrab.jbuild.JBuildGrabber;
import com.athaydes.jgrab.runner.Grabber;
import com.athaydes.jgrab.runner.JGrabOptions;
import com.athaydes.jgrab.runner.JGrabRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * JGrab daemon, a TCP socket server that runs in the background waiting for Java code to run.
 */
public final class JGrabDaemon {

    private static final Logger logger = LoggerFactory.getLogger( JGrabDaemon.class );
    private static final String STOP_OPTION = "--stop";
    private static final String VERSION_OPTION = "--version";

    private static final PersistentCache libsCache = new PersistentCache();
    private static final Grabber grabber = JBuildGrabber.INSTANCE;

    private static final Pattern JAVA_ARGUMENTS_LINE = Pattern.compile( "\\[.+]" );

    static {
        Runtime.getRuntime().addShutdownHook( new Thread( JGrabDaemon::saveCache ) );
        var executor = Executors.newSingleThreadScheduledExecutor( ( runnable ) -> {
            var thread = new Thread( runnable, "jgrab-cache-saver" );
            thread.setDaemon( true );
            return thread;
        } );
        executor.scheduleAtFixedRate( JGrabDaemon::saveCache, 1, 1, TimeUnit.HOURS );
    }

    private static void saveCache() {
        try {
            libsCache.save();
        } catch ( IOException e ) {
            logger.warn( "Failed to save dependencies cache", e );
        }
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

            JGrabRunner.populateClassLoaderCache( libsCache.loadCache().values() );

            String token;
            try {
                token = Authorizer.generateRandomToken();
            } catch ( IOException e ) {
                logger.error( "Unable to create file for authorization token" );
                System.exit( 1 );
                return;
            }
            var running = true;
            while ( running ) {
                try ( Socket clientSocket = serverSocket.accept();
                      final PrintStream out = new PrintStream( clientSocket.getOutputStream(), true );
                      BufferedReader in = new BufferedReader(
                              new InputStreamReader( clientSocket.getInputStream() ) ) ) {
                    // the first line sent must be the current token
                    if ( token.equals( in.readLine() ) ) {
                        running = handleClient( in, out, runArgs );
                    } else {
                        logger.info( "Rejecting client as it did not present the current token" );
                        out.println( "=== JGrab authorization error ===" );
                        running = false;
                    }
                } catch ( IOException e ) {
                    logger.warn( "Problem handling client message", e );
                }
            }
            logger.info( "Stopped JGrab daemon" );
            try {
                // sleep a bit to allow the client to receive the response without errors
                Thread.sleep( 100 );
                serverSocket.close();
            } catch ( IOException e ) {
                logger.debug( "Exception closing server", e );
            } catch ( InterruptedException e ) {
                logger.trace( "Interrupted while closing server" );
            }
        }, "jgrab-daemon" ).start();
    }

    /**
     * Handle the client synchronously.
     *
     * @param in      client input
     * @param out     client output
     * @param runArgs args
     * @return true to continue, false to stop accepting connections
     * @throws IOException on IO errors
     */
    private static boolean handleClient(
            BufferedReader in,
            PrintStream out,
            RunArgs runArgs ) throws IOException {
        var request = parseRequest( in, out );
        if ( request == StatelessRequest.DO_NOTHING ) return true;
        if ( request == StatelessRequest.DIE ) return false;
        var codeRequest = ( CodeRunRequest ) request;

        logSourceCode( codeRequest.code );

        var deps = codeRequest.code.extractDependencies();

        logger.debug( "Dependencies to grab: {}", deps );

        var classpath = libsCache.classpathOf( deps, () -> grabber.grab( deps ) );

        System.setOut( out );
        System.setErr( out );

        // run this synchronously, which means only one program can run per daemon at a time
        try {
            runArgs.accept( codeRequest.code, codeRequest.args, classpath );
        } catch ( Throwable t ) {
            t.printStackTrace( out );
        } finally {
            System.setOut( System.out );
            System.setErr( System.err );
        }

        return true;
    }

    private static Request parseRequest( BufferedReader in, PrintStream out ) throws IOException {
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
            return StatelessRequest.DO_NOTHING;
        }

        JavaCode code;
        String[] args;

        // check for Java file contents preceded by Java arguments
        if ( messageBuilder.length() > 0 && JAVA_ARGUMENTS_LINE.matcher( firstLine ).matches() ) {
            // remove the [] demarkers
            String javaArgs = firstLine.substring( 1, firstLine.length() - 1 );
            logger.debug( "Java arguments: {}", javaArgs );
            code = new StringJavaCode( messageBuilder.toString() );
            args = javaArgs.split( " " );
        } else {
            String input = ( firstLine + "\n" + messageBuilder ).trim();

            if ( input.equals( STOP_OPTION ) ) {
                logger.info( "--stop option received, stopping JGrab Daemon" );
                out.println( "=== JGrab Daemon stopped ===" );
                return StatelessRequest.DIE;
            }

            if ( input.equals( VERSION_OPTION ) ) {
                logger.info( "--version option received" );
                System.setOut( out );
                System.setErr( out );
                JGrabRunner.printVersion();
                return StatelessRequest.DO_NOTHING;
            }

            if ( input.startsWith( JGrabOptions.SNIPPET_OPTION ) ) {
                String snippet = input.substring( JGrabOptions.SNIPPET_OPTION.length() );
                if ( snippet.isEmpty() ) {
                    out.println( "ERROR: no snippet provided to execute" );
                    return StatelessRequest.DO_NOTHING;
                } else {
                    code = new StringJavaCode( snippet );
                    args = new String[ 0 ];
                }
            } else {
                code = new StringJavaCode( input );
                args = new String[ 0 ];
            }
        }
        return new CodeRunRequest( code, args );
    }

    private static void logSourceCode( Object source ) {
        logger.trace( "Source code:\n------------------------------------\n" +
                "{}\n------------------------------------\n", source );
    }

    @FunctionalInterface
    public interface RunArgs {
        void accept( JavaCode javaCode,
                     String[] args,
                     Classpath classpath );
    }

}
