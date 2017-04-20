package com.athaydes.jgrab.runner;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Optional;

/**
 *
 */
class JarHandler {

    static Optional<File> getJarOf( Class<?> type ) {
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

}
