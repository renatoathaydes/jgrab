package com.athaydes.jgrab.runner;

import com.athaydes.jgrab.Classpath;
import com.athaydes.jgrab.code.JavaCode;
import com.athaydes.jgrab.code.StdinJavaCode;
import com.athaydes.jgrab.code.StringJavaCode;
import com.athaydes.jgrab.daemon.JGrabDaemon;
import com.athaydes.jgrab.jbuild.JBuildGrabber;
import com.athaydes.osgiaas.api.env.ClassLoaderContext;
import com.athaydes.osgiaas.javac.internal.compiler.OsgiaasJavaCompilerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;

/**
 * Runs a Java file, using the JGrab annotations to find its dependencies.
 */
public class JGrabRunner {

    private static final Logger logger = LoggerFactory.getLogger( JGrabRunner.class );

    private static final Grabber grabber = JBuildGrabber.INSTANCE;

    private static final Map<String, ClassLoaderContext> classLoaderCache = new ConcurrentHashMap<>();

    static void error( String reason ) {
        throw new JGrabError( reason + "\n\nUsage: jgrab (-e <java_source>) | java_file" );
    }

    public static void printVersion() {
        URL jarUrl = JGrabRunner.class.getProtectionDomain().getCodeSource().getLocation();
        String version = "UNKNOWN";

        try ( JarFile jar = new JarFile( new File( jarUrl.getFile() ) ) ) {
            String jarVersion = jar.getManifest().getMainAttributes().getValue( "Implementation-Version" );
            if ( jarVersion == null ) {
                logger.warn( "JGrab jar file's manifest does not contain Implementation-Version, " +
                        "unable to find out JGrab version." );
            } else {
                version = jarVersion;
            }
        } catch ( IOException e ) {
            logger.error( "Cannot access JGrab jar file to check version: " + e );
        }

        System.out.println( "JGrab Runner Version: " + version );
        System.out.println( "Java Version: " + System.getProperty( "java.version" ) );
    }

    private static void run( String currentDir, JGrabOptions options ) throws Exception {
        if ( options instanceof JGrabOptions.JavaFile ) {
            JGrabOptions.JavaFile javaFile = ( JGrabOptions.JavaFile ) options;
            Path rawPath = javaFile.file.toPath();
            Path canonicalPath = rawPath.isAbsolute() ? rawPath : Paths.get( currentDir ).resolve( rawPath );
            run( new FileJavaCode( canonicalPath ), javaFile.args );
        } else if ( options instanceof JGrabOptions.StdIn ) {
            run( new StdinJavaCode(), new String[ 0 ] );
        } else if ( options instanceof JGrabOptions.Snippet ) {
            run( new StringJavaCode( ( ( JGrabOptions.Snippet ) options ).code ), new String[ 0 ] );
        } else if ( options instanceof JGrabOptions.Daemon ) {
            JGrabDaemon.start( JGrabRunner::run );
        } else if ( options instanceof JGrabOptions.PrintVersion ) {
            printVersion();
        } else {
            error( "Unknown JGrab option: " + options );
        }
    }

    private static void run( JavaCode javaCode, String[] args ) {
        Path tempDir = getTempDir();

        logger.debug( "JGrab using directory: {}", tempDir );

        var toGrab = javaCode.extractDependencies();
        logger.debug( "Dependencies to grab: {}", toGrab );

        Classpath classpath;

        if ( !toGrab.isEmpty() ) {
            classpath = new Classpath( toGrab, grabber.grab( toGrab ) );
        } else {
            classpath = Classpath.empty();
        }

        run( javaCode, args, classpath );
    }

    private static void run( JavaCode javaCode,
                             String[] args,
                             Classpath classpath ) {
        var classLoaderContext = classLoaderFor( classpath );

        if ( javaCode.isSnippet() ) {
            runJavaSnippet( javaCode.getCode(), classLoaderContext );
        } else {
            runJavaClass( javaCode, classLoaderContext, args );
        }
    }

    public static void populateClassLoaderCache( Collection<Classpath> classpaths ) {
        logger.debug( "Populating ClassLoader cache with {} entries", classpaths.size() );
        for ( Classpath classpath : classpaths ) {
            classLoaderFor( classpath );
        }
    }

    private static ClassLoaderContext classLoaderFor( Classpath classpath ) {
        if ( classpath.isEmpty() ) {
            return EmptyClassLoaderContext.INSTANCE;
        }
        return classLoaderCache.computeIfAbsent( classpath.hash, ignore ->
                new JGrabClassLoaderContext( classpath.resolvedArtifacts ) );
    }

    private static void runJavaSnippet( String snippet, ClassLoaderContext classLoaderContext ) {
        logger.debug( "Running Java snippet" );

        snippet = snippet.trim();

        if ( snippet.endsWith( ";" ) ) {
            // user entered a statement, add a return statement after it if needed
            int index = snippet.lastIndexOf( '\n' );
            String lastLine = index < 0 ? snippet : snippet.substring( index + 1 );

            if ( !lastLine.contains( "return" ) ) {
                snippet += "\nreturn null;";
            }
        } else {
            // use entered an expression, return the value of that expression
            snippet = "return " + snippet + ";";
        }

        Callable<?> callable = new OsgiaasJavaCompilerService().compileJavaSnippet( snippet, classLoaderContext )
                .orElseThrow( () -> new JGrabError( "Java code compilation failed" ) );

        try {
            Object result = callable.call();
            if ( result != null ) {
                System.out.println( result );
            }
        } catch ( Throwable t ) {
            throw new JGrabError( t );
        }
    }

    private static void runJavaClass( JavaCode javaCode,
                                      ClassLoaderContext classLoaderContext,
                                      String[] args ) {
        logger.debug( "Running Java class" );

        Class<Object> compiledClass = new OsgiaasJavaCompilerService()
                .compileJavaClass( classLoaderContext, javaCode.getClassName(), javaCode.getCode(), System.err )
                .orElseThrow( () -> new JGrabError( "Java code compilation failed" ) );

        if ( Runnable.class.isAssignableFrom( compiledClass ) ) {
            try {
                Runnable runnable = ( Runnable ) compiledClass.getDeclaredConstructor().newInstance();
                runnable.run();
            } catch ( Throwable t ) {
                throw new JGrabError( t );
            }
        } else {
            try {
                Method method = compiledClass.getMethod( "main", String[].class );
                method.invoke( compiledClass, ( Object ) args );
            } catch ( Throwable t ) {
                throw new JGrabError( t );
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
            var options = JGrabOptions.parseOptions( args );
            if ( options != null ) {
                run( currentDir, options );
            }
        } catch ( JGrabError e ) {
            if ( e.getMessage() != null ) {
                logger.error( "{}", e.getMessage() );
            } else {
                logger.error( "JGrab Error", e.getCause() );
            }
            System.exit( 1 );
        } catch ( Exception e ) {
            logger.error( "Unexpected Error", e );
            System.exit( 2 );
        }
    }

    public static void main( String[] args ) {
        run( System.getProperty( "user.dir" ), args );
    }

}
