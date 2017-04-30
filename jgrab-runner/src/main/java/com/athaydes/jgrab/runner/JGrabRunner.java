package com.athaydes.jgrab.runner;

import com.athaydes.jgrab.Dependency;
import com.athaydes.jgrab.daemon.JGrabDaemon;
import com.athaydes.jgrab.ivy.IvyGrabber;
import com.athaydes.osgiaas.api.env.ClassLoaderContext;
import com.athaydes.osgiaas.javac.internal.DefaultClassLoaderContext;
import com.athaydes.osgiaas.javac.internal.compiler.OsgiaasJavaCompilerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Runs a Java file, using the JGrab annotations to find its dependencies.
 */
public class JGrabRunner {

    private static final Logger logger = LoggerFactory.getLogger( JGrabRunner.class );

    private static final String JGRAB_LIB_DIR = "jgrab-libs";

    private static JGrabOptions parseOptions( String[] args ) {
        if ( args.length == 0 ) {
            return error( "No arguments provided" );
        }
        if ( args.length == 1 ) {
            if ( args[ 0 ].equals( "--daemon" ) || args[ 0 ].equals( "-d" ) ) {
                return new JGrabOptions( JGrabRunnable.START_DAEMON, "" );
            }

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
        throw new JGrabError( reason + "\n\nUsage: jgrab (-e <java_source>) | java_file" );
    }

    private static void run( String currentDir, JGrabOptions options ) throws Exception {
        switch ( options.jGrabRunnable ) {
            case JAVA_FILE:
                Path rawPath = Paths.get( options.arg );
                Path canonicalPath = rawPath.isAbsolute() ? rawPath : Paths.get( currentDir ).resolve( rawPath );
                run( new FileJavaCode( canonicalPath ) );
                break;
            case JAVA_SOURCE_CODE:
                run( new StringJavaCode( options.arg ) );
                break;
            case START_DAEMON:
                JGrabDaemon.start( JGrabRunner::run );
                break;
        }
    }

    private static void run( JavaCode javaCode ) {
        Path tempDir = getTempDir();

        logger.debug( "JGrab using directory: {}", tempDir );

        List<Dependency> toGrab = javaCode.extractDependencies();
        logger.debug( "Dependencies to grab: {}", toGrab );

        File libDir = new File( tempDir.toFile(), JGRAB_LIB_DIR );

        if ( !toGrab.isEmpty() ) {
            libDir.mkdir();
            new IvyGrabber().grab( toGrab, libDir );
        }

        File[] libs = libDir.listFiles();

        ClassLoaderContext classLoaderContext = libs == null || libs.length == 0 ?
                DefaultClassLoaderContext.INSTANCE :
                new JGrabClassLoaderContext( libs );

        if ( javaCode.isSnippet() ) {
            runJavaSnippet( javaCode.getCode(), classLoaderContext );
        } else {
            runJavaClass( javaCode, classLoaderContext );
        }
    }

    private static void runJavaSnippet( String snippet, ClassLoaderContext classLoaderContext ) {
        logger.debug( "Running Java snippet" );

        snippet = snippet.trim();

        if ( snippet.endsWith( ";" ) ) {
            // user entered a statement, add a return statement after it
            snippet += "\nreturn null;";
        } else {
            // use entered an expression, return the value of that expression
            snippet = "return " + snippet + ";";
        }

        Callable<?> callable = new OsgiaasJavaCompilerService().compileJavaSnippet( snippet, classLoaderContext )
                .orElseThrow( () -> new JGrabError( "Java code compilation failed" ) );

        try {
            Object result = callable.call();
            if ( result != null ) {
                System.out.println( result.toString() );
            }
        } catch ( Throwable t ) {
            logger.warn( "Problem running Java snippet", t );
        }
    }

    private static void runJavaClass( JavaCode javaCode, ClassLoaderContext classLoaderContext ) {
        logger.debug( "Running Java class" );

        Class<Object> compiledClass = new OsgiaasJavaCompilerService()
                .compileJavaClass( classLoaderContext, javaCode.getClassName(), javaCode.getCode(), System.err )
                .orElseThrow( () -> new JGrabError( "Java code compilation failed" ) );

        if ( Runnable.class.isAssignableFrom( compiledClass ) ) {
            try {
                Runnable runnable = ( Runnable ) compiledClass.getDeclaredConstructor().newInstance();
                runnable.run();
            } catch ( Throwable t ) {
                logger.warn( "Problem running Java class", t );
            }
        } else {
            try {
                Method method = compiledClass.getMethod( "main", String[].class );
                method.invoke( compiledClass, ( Object ) new String[ 0 ] );
            } catch ( Throwable t ) {
                logger.warn( "Problem running Java class", t );
            }
        }
    }

    private static Path getTempDir() {
        try {
            return Files.createTempDirectory( "jgrab" );
        } catch ( IOException e ) {
            throw new RuntimeException( "Unable to create a temp dir for JGrab resources! Cannot run JGrab." );
        }
    }

    private static void run( String currentDir, String[] args ) {
        try {
            JGrabOptions options = parseOptions( args );
            run( currentDir, options );
        } catch ( JGrabError e ) {
            logger.error( e.getMessage() );
        } catch ( Exception e ) {
            logger.error( "Unable to run Java class", e );
        }
    }

    public static void main( String[] args ) {
        run( System.getProperty( "user.dir" ), args );
    }

}

enum JGrabRunnable {
    JAVA_SOURCE_CODE, JAVA_FILE, START_DAEMON
}

class JGrabOptions {
    final JGrabRunnable jGrabRunnable;
    final String arg;

    JGrabOptions( JGrabRunnable jGrabRunnable, String arg ) {
        this.jGrabRunnable = jGrabRunnable;
        this.arg = arg;
    }
}