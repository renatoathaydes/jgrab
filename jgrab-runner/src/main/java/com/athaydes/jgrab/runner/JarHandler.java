package com.athaydes.jgrab.runner;

import org.apache.ivy.Ivy;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 *
 */
class JarHandler {

    private static Optional<File> getJarOf( Class<?> type ) {
        ClassLoader loader = type.getClassLoader();
        if ( loader instanceof URLClassLoader ) {
            URL[] jarUrls = ( ( URLClassLoader ) loader ).getURLs();

            String target = type.getName().replace( ".", "/" ) + ".class";

            for (URL jar : jarUrls) {
                if ( "file".equals( jar.getProtocol() ) ) {

                    try {
                        JarURLConnection jarConnection = ( JarURLConnection ) new URL( "jar:" + jar + "!/" + target ).openConnection();
                        jarConnection.connect();

                        // if no error, we got the jar
                        return Optional.of( new File( jar.getPath() ) );
                    } catch ( IOException e ) {
                        // no worries, keep trying
                    }
                }
            }
        }

        return Optional.empty();
    }

    static List<File> jgrabRunnerJars() {
        File selfJar = JarHandler.getJarOf( JGrabRunner.class ).orElseThrow( () ->
                new RuntimeException( "Unable to locate self jar, JGrab can only run from a jar file!" ) );

        File ivyJar = JarHandler.getJarOf( Ivy.class ).orElseThrow( () ->
                new RuntimeException( "Unable to locate Ivy jar, please add the ivy jar to the classpath!" ) );

        return Arrays.asList( selfJar, ivyJar );
    }

    static List<File> allJarsIn( Path tempDir ) {
        File[] files = tempDir.toFile()
                .listFiles( ( dir, name ) -> name.endsWith( ".jar" ) );

        return files == null ? Collections.emptyList() : Arrays.asList( files );
    }
}
