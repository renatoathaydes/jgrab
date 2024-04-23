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
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.jar.JarFile;

/**
 * Runs a Java file, using the JGrab annotations to find its dependencies.
 */
@SuppressWarnings( "CallToPrintStackTrace" )
public class JGrabRunner {

    private static final Logger logger = LoggerFactory.getLogger( JGrabRunner.class );

    public static final String SNIPPET_OPTION = "-e";

    private static JGrabOptions parseOptions( String[] args ) {
        if ( args.length == 0 ) {
            return new JGrabOptions.StdIn();
        }
        if ( args.length == 1 ) {
            if ( args[ 0 ].equals( "--daemon" ) || args[ 0 ].equals( "-d" ) ) {
                return new JGrabOptions.Daemon();
            }
            if ( args[ 0 ].equals( "--help" ) || args[ 0 ].equals( "-h" ) ) {
                return help();
            }
            if ( args[ 0 ].equals( "--version" ) || args[ 0 ].equals( "-v" ) ) {
                return version();
            }
        }

        String first = args[ 0 ];
        String[] rest = new String[ args.length - 1 ];
        System.arraycopy( args, 1, rest, 0, rest.length );

        if ( first.equals( SNIPPET_OPTION ) ) {
            String script = String.join( " ", rest );
            return new JGrabOptions.Snippet( script );
        }

        return new JGrabOptions.JavaFile( new File( first ), rest );
    }

    static void error( String reason ) {
        throw new JGrabError( reason + "\n\nUsage: jgrab (-e <java_source>) | java_file" );
    }

    public static void printVersion() {
        version();
    }

    static JGrabOptions version() {
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

        System.out.println( "JGrab Version: " + version );
        System.out.println( "Java Version: " + System.getProperty( "java.version" ) );

        return new JGrabOptions.None();
    }

    private static JGrabOptions help() {
        System.out.println( "=================== JGrab ===================\n" +
                " - https://github.com/renatoathaydes/jgrab -\n" +
                "=============================================\n" +
                "Jgrab can execute Java code from stdin (if not given any argument),\n" +
                "a Java file, or a Java snippet.\n\n" +
                "Usage:\n" +
                "  jgrab [<option> | java_file [java-args*] | -e java_snippet]\n" +
                "Options:\n" +
                "  --daemon -d\n" +
                "    Starts up the JGrab daemon (used by the jgrab-client).\n" +
                "  --help -h\n" +
                "    Shows usage.\n" +
                "  --version -v\n" +
                "    Shows version information." );

        return new JGrabOptions.None();
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
        } else
            //noinspection StatementWithEmptyBody
            if ( options instanceof JGrabOptions.None ) {
                // nothing to do
            } else {
                error( "Unknown JGrab option: " + options );
            }
    }

    private static void run( JavaCode javaCode, String[] args ) {
        Path tempDir = getTempDir();

        logger.debug( "JGrab using directory: {}", tempDir );

        Set<Dependency> toGrab = javaCode.extractDependencies();
        logger.debug( "Dependencies to grab: {}", toGrab );

        List<File> libs;

        if ( !toGrab.isEmpty() ) {
            libs = IvyGrabber.getInstance().grab( toGrab );
        } else {
            libs = Collections.emptyList();
        }

        run( javaCode, args, libs );
    }

    private static void run( JavaCode javaCode,
                             String[] args,
                             List<File> libs ) {
        ClassLoaderContext classLoaderContext = libs.isEmpty() ?
                DefaultClassLoaderContext.INSTANCE :
                new JGrabClassLoaderContext( libs );

        if ( javaCode.isSnippet() ) {
            runJavaSnippet( javaCode.getCode(), classLoaderContext );
        } else {
            runJavaClass( javaCode, classLoaderContext, args );
        }
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
            System.err.println( "Problem running Java snippet: " + t );
            t.printStackTrace();
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
                System.err.println( "Problem running Java class: " + t );
                t.printStackTrace();
            }
        } else {
            try {
                Method method = compiledClass.getMethod( "main", String[].class );
                method.invoke( compiledClass, ( Object ) args );
            } catch ( Throwable t ) {
                System.err.println( "Problem running Java class: " + t );
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
            System.err.println( e.getMessage() );
        } catch ( Exception e ) {
            System.err.println( "Unable to run Java class due to " + e );
            e.printStackTrace();
        }
    }

    public static void main( String[] args ) {
        run( System.getProperty( "user.dir" ), args );
    }

}

abstract class JGrabOptions {
    static class Snippet extends JGrabOptions {
        final String code;

        public Snippet( String code ) {
            this.code = code;
        }
    }

    static class JavaFile extends JGrabOptions {
        final File file;
        final String[] args;

        public JavaFile( File file, String[] args ) {
            this.file = file;
            this.args = args;
        }
    }

    static class StdIn extends JGrabOptions {
    }

    static class Daemon extends JGrabOptions {
    }

    static class None extends JGrabOptions {
    }
}