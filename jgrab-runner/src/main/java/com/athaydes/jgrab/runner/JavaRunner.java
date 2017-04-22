package com.athaydes.jgrab.runner;

import com.athaydes.jgrab.log.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The java process runner.
 */
class JavaRunner {

    static void run( Path tempDir,
                     JavaInitializer.JavaInfo javaInfo ) throws Exception {
        List<String> commandParts = new ArrayList<>();

        commandParts.add( javaInfo.java.getAbsolutePath() );

        List<String> cpEntries = new ArrayList<>();

        cpEntries.add( Javac.CLASSES_DIR );

        File libDir = new File( tempDir.toFile(), Javac.JGRAB_LIB_DIR );

        if ( libDir.isDirectory() ) {
            List<String> libs = JarHandler.allJarsIn( libDir.toPath() ).stream()
                    .map( File::getName )
                    .collect( Collectors.toList() );
            Logger.log( "java libs: " + libs );
            cpEntries.add( Javac.JGRAB_LIB_DIR + File.separator + "*" );
        }

        String cp = String.join( File.pathSeparator, cpEntries );

        if ( !cp.isEmpty() ) {
            Logger.log( "java using cp: " + cp );
            commandParts.add( "-cp" );
            commandParts.add( cp );
        }

        commandParts.addAll( getRunnable( tempDir.resolve( Javac.CLASSES_DIR ) ) );

        ProcessBuilder processBuilder = new ProcessBuilder()
                .command( commandParts )
                .directory( tempDir.toFile() );

        ProcessHandler processHandler = new ProcessHandler( "java", processBuilder, true );

        int exitCode = processHandler.run();

        if ( exitCode != 0 ) {
            throw new RuntimeException( "Java process exited with non-zero exit code: " +
                    exitCode + ". See the process output for details." );
        }
    }

    private static List<String> getRunnable( Path dir ) {

        List<String> classes = listFilesUnder( dir )
                .filter( it -> it.toFile().getName().endsWith( ".class" ) )
                .map( p -> {
                    String type = dir.relativize( p ).toFile().getPath().replace( File.separator, "." );
                    return type.substring( 0, type.length() - ".class".length() );
                } )
                .collect( Collectors.toList() );

        Logger.log( "Class names: " + classes );

        for (String className : classes) {
            // ignore nested classes
            if ( className.contains( "$" ) ) {
                continue;
            }

            return Collections.singletonList( className );
        }

        throw new RuntimeException( "No class file found to run. TempDir=" + dir );
    }

    private static Stream<Path> listFilesUnder( Path dir ) {
        try {
            return Files.list( dir ).flatMap( p -> {
                if ( p.toFile().isDirectory() ) {
                    return Stream.concat( Stream.of( p ), listFilesUnder( p ) );
                } else {
                    return Stream.of( p );
                }
            } );
        } catch ( IOException e ) {
            return Stream.empty();
        }
    }


}
