package com.athaydes.jgrab.runner;

import com.athaydes.jgrab.Dependency;
import com.athaydes.jgrab.code.JavaCode;
import com.athaydes.jgrab.code.StdinJavaCode;
import com.athaydes.jgrab.code.StringJavaCode;
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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Runs a Java file, using the JGrab annotations to find its dependencies.
 */
public class JGrabRunner {

    private static final Logger logger = LoggerFactory.getLogger( JGrabRunner.class );

    private static final String JGRAB_LIB_DIR = "jgrab-libs";

    public static final String SNIPPET_OPTION = "-e";

    private static final Grabber ivyGrabber = new IvyGrabber();

    private static JGrabOptions parseOptions( String[] args ) {
        if ( args.length == 0 ) {
            return new JGrabOptions( JGrabRunnable.JAVA_FILE_STD_IN, "" );
        }
        if ( args.length == 1 ) {
            if ( args[ 0 ].equals( "--daemon" ) || args[ 0 ].equals( "-d" ) ) {
                return new JGrabOptions( JGrabRunnable.START_DAEMON, "" );
            }
            if ( args[ 0 ].equals( "--help" ) || args[ 0 ].equals( "-h" ) ) {
                return help();
            }

            return new JGrabOptions( JGrabRunnable.JAVA_FILE_NAME, args[ 0 ] );
        }
        if ( args.length == 2 ) {
            if ( args[ 0 ].equals( SNIPPET_OPTION ) ) {
                return new JGrabOptions( JGrabRunnable.JAVA_SOURCE_CODE, args[ 1 ] );
            }
        }

        return error( "Too many options" );
    }

    private static JGrabOptions error( String reason ) {
        throw new JGrabError( reason + "\n\nUsage: jgrab (-e <java_source>) | java_file" );
    }

    private static JGrabOptions help() {
        System.out.println( "=================== JGrab ===================\n" +
                " - https://github.com/renatoathaydes/jgrab -\n" +
                "=============================================\n" +
                "Jgrab can execute Java code from stdin (if not given any argument),\n" +
                "a Java file, or a Java snippet.\n\n" +
                "Usage:\n" +
                "  jgrab [<option> | java_file | -e java_snippet]\n" +
                "Options:\n" +
                "  --daemon -d\n" +
                "    Starts up the JGrab daemon (used by the jgrab-client).\n" +
                "  --help -h\n" +
                "    Shows this usage help." );

        return new JGrabOptions( JGrabRunnable.NONE, "" );
    }

    private static void run( String currentDir, JGrabOptions options ) throws Exception {
        switch ( options.jGrabRunnable ) {
            case JAVA_FILE_NAME:
                Path rawPath = Paths.get( options.arg );
                Path canonicalPath = rawPath.isAbsolute() ? rawPath : Paths.get( currentDir ).resolve( rawPath );
                run( new FileJavaCode( canonicalPath ) );
                break;
            case JAVA_FILE_STD_IN:
                run( new StdinJavaCode() );
                break;
            case JAVA_SOURCE_CODE:
                run( new StringJavaCode( options.arg ) );
                break;
            case START_DAEMON:
                JGrabDaemon.start( JGrabRunner::run );
                break;
            case NONE:
                // nothing to do
                break;
        }
    }

    private static void run( JavaCode javaCode ) {
        Path tempDir = getTempDir();

        logger.debug( "JGrab using directory: {}", tempDir );

        List<Dependency> toGrab = javaCode.extractDependencies();
        logger.debug( "Dependencies to grab: {}", toGrab );

        // FIXME don't need no lib dir
        File libDir = new File( tempDir.toFile(), JGRAB_LIB_DIR );

        List<File> libs;

        if ( !toGrab.isEmpty() ) {
            libDir.mkdir();
            libs = ivyGrabber.grab( toGrab );
        } else {
            libs = Collections.emptyList();
        }

        ClassLoaderContext classLoaderContext = libs.isEmpty() ?
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
            t.printStackTrace();
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
                t.printStackTrace();
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
    JAVA_SOURCE_CODE, JAVA_FILE_NAME, JAVA_FILE_STD_IN, START_DAEMON, NONE
}

class JGrabOptions {
    final JGrabRunnable jGrabRunnable;
    final String arg;

    JGrabOptions( JGrabRunnable jGrabRunnable, String arg ) {
        this.jGrabRunnable = jGrabRunnable;
        this.arg = arg;
    }
}