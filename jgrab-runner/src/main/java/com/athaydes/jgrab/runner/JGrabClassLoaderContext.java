package com.athaydes.jgrab.runner;

import com.athaydes.osgiaas.api.env.ClassLoaderContext;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * ClassLoaderContext for the JGrab application.
 */
class JGrabClassLoaderContext implements ClassLoaderContext {

    private final URLClassLoader dependenciesClassLoader;
    private final List<JarFile> jars;
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger( JGrabClassLoaderContext.class );

    JGrabClassLoaderContext( List<File> dependencyJars ) {
        URL[] jarUrls = new URL[ dependencyJars.size() ];
        this.jars = new ArrayList<>( dependencyJars.size() );

        for (int i = 0; i < dependencyJars.size(); i++) {
            File jar = dependencyJars.get( i );

            try {
                jars.add( new JarFile( jar ) );
                jarUrls[ i ] = jar.toURI().toURL();
            } catch ( IOException e ) {
                throw new RuntimeException( e );
            }
        }

        this.dependenciesClassLoader = new URLClassLoader( jarUrls );
    }

    @Override
    public ClassLoader getClassLoader() {
        return dependenciesClassLoader;
    }

    @Override
    public Collection<String> getClassesIn( String packageName ) {
        logger.debug( "Getting classes in package {}", packageName );

        if ( packageName.startsWith( "java." ) ) {
            // not our packages
            return Collections.emptyList();
        }

        List<String> classes = jars.stream()
                .flatMap( jar -> filterEntriesByPackage( jar, packageName ) )
                .collect( Collectors.toList() );

        logger.debug( "Total {} classes found in package {}", classes.size(), packageName );

        return classes;
    }

    private static Stream<String> filterEntriesByPackage( JarFile jarFile, String packageName ) {
        logger.debug( "Searching jar {} for package {}", jarFile.getName(), packageName );

        Enumeration<JarEntry> entries = jarFile.entries();
        List<String> result = new ArrayList<>( 4 );

        while ( entries.hasMoreElements() ) {
            JarEntry entry = entries.nextElement();
            if ( entry.isDirectory() ) {
                continue;
            }

            String entryName = entry.getName();

            if ( entryName.endsWith( ".class" ) ) {
                int lastPartIndex = entryName.lastIndexOf( '/' );

                String entryPackage = lastPartIndex > 0 ?
                        entryName.substring( 0, lastPartIndex ).replace( "/", "." ) :
                        "";

                if ( entryPackage.equals( packageName ) ) {
                    result.add( entryName );
                }
            }
        }

        return result.stream();
    }
}
