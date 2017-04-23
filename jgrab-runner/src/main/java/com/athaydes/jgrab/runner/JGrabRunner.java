package com.athaydes.jgrab.runner;

import com.athaydes.jgrab.Dependency;
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

    private static void run( JGrabOptions options ) throws Exception {
        if ( options.jGrabRunnable == JGrabRunnable.JAVA_SOURCE_CODE ) {
            throw new JGrabError( "java_source not supported yet" );
        }

        Path tempDir = getTempDir();

        logger.debug( "JGrab using directory: {}", tempDir );

        JavaFileHandler javaFile = new JavaFileHandler( Paths.get( options.arg ) );

        List<Dependency> toGrab = javaFile.extractDependencies();
        logger.debug( "Dependencies to grab: {}", toGrab );

        File libDir = new File( tempDir.toFile(), JGRAB_LIB_DIR );

        if ( !toGrab.isEmpty() ) {
            libDir.mkdir();
            new IvyGrabber().grab( toGrab, libDir );
        }

        String className = javaFile.getClassName();
        File[] libs = libDir.listFiles();

        ClassLoaderContext classLoaderContext = libs == null || libs.length == 0 ?
                DefaultClassLoaderContext.INSTANCE :
                new JGrabClassLoaderContext( libs );

        Class<Object> compiledClass = new OsgiaasJavaCompilerService()
                .compileJavaClass( classLoaderContext, className, javaFile.getCode(), System.err )
                .orElseThrow( () -> new RuntimeException( "Java file compilation failed" ) );

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

    public static void main( String[] args ) {
        try {
            JGrabOptions options = parseOptions( args );
            run( options );
        } catch ( JGrabError e ) {
            logger.error( e.getMessage() );
        } catch ( Exception e ) {
            logger.error( "Unable to run Java class", e );
        }
    }

}

enum JGrabRunnable {
    JAVA_SOURCE_CODE, JAVA_FILE
}

class JGrabOptions {
    final JGrabRunnable jGrabRunnable;
    final String arg;

    JGrabOptions( JGrabRunnable jGrabRunnable, String arg ) {
        this.jGrabRunnable = jGrabRunnable;
        this.arg = arg;
    }
}