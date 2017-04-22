package com.athaydes.jgrab.runner;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.athaydes.jgrab.processor.JGrabAnnotationProcessor.JGRAB_LIB_DIR;
import static com.athaydes.jgrab.processor.JGrabAnnotationProcessor.JGRAB_TEMP_DIR_ENV_VAR;
import static java.util.Collections.singletonList;

/**
 * The javac process runner.
 */
class Javac {

    static final String CLASSES_DIR = "jgrab-classes";

    static void compile( Path tempDir,
                         JavaInitializer.JavaInfo javaInfo,
                         File toCompile,
                         boolean useJGrabProcessor ) throws Exception {

        List<String> cpEntries = new ArrayList<>( useJGrabProcessor ?
                JarHandler.jgrabRunnerJars().stream()
                        .map( File::getAbsolutePath )
                        .collect( Collectors.toList() ) :
                singletonList( JarHandler.jgrabApiJar().getAbsolutePath() ) );

        cpEntries.add( JGRAB_LIB_DIR + File.separator + "*" );

        String cp = String.join( File.pathSeparator, cpEntries );

        File classesDir = new File( tempDir.toFile(), CLASSES_DIR );
        classesDir.mkdir();

        ProcessBuilder processBuilder = new ProcessBuilder().command(
                javaInfo.javac.getAbsolutePath(),
                "-cp", cp,
                "-d", CLASSES_DIR,
                toCompile.getAbsolutePath() );

        processBuilder.directory( tempDir.toFile() )
                .environment().put( JGRAB_TEMP_DIR_ENV_VAR, tempDir.toString() );

        ProcessHandler processHandler = new ProcessHandler( "javac", processBuilder, false );

        int exitCode = processHandler.run( Duration.ofSeconds( 60 ) );

        if ( exitCode != 0 ) {
            throw new RuntimeException( "Error compiling Java code, see the process output for details." );
        }
    }

}
