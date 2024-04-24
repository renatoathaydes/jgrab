package com.athaydes.jgrab.daemon;

import com.athaydes.jgrab.Classpath;
import com.athaydes.jgrab.Dependency;
import com.athaydes.jgrab.JGrabHome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A persistent cache for resolved dependencies
 */
final class PersistentCache {

    private static final Logger logger = LoggerFactory.getLogger( PersistentCache.class );

    private final File cacheFile;
    private final AtomicBoolean isCacheLoaded = new AtomicBoolean( false );
    private final Map<String, Classpath> cache = new HashMap<>();

    PersistentCache() {
        this( new File( JGrabHome.getDir(), "deps-cache" ) );
    }

    PersistentCache( File cacheFile ) {
        this.cacheFile = cacheFile;
    }

    void save() throws IOException {
        if ( !isCacheLoaded.get() ) {
            logger.debug( "The cache was not loaded, will not save the current cache" );
            return;
        }

        if ( isCacheLoaded.get() && cache.isEmpty() ) {
            logger.debug( "The cache is empty, will delete the cache file" );
            cacheFile.deleteOnExit();
            return;
        }

        logger.debug( "Saving cache to {}", cacheFile );

        File dir = cacheFile.getParentFile();
        if ( dir == null ) {
            dir = new File( "." );
        }

        dir.mkdirs();

        // write to this file, then move it to the actual cache file, so we don't lose the original in case of error
        File tempCacheFile = new File( dir, "temp-cache" );
        tempCacheFile.createNewFile();

        try ( BufferedWriter writer = Files.newBufferedWriter( tempCacheFile.toPath(), StandardOpenOption.WRITE ) ) {
            for ( var classpath : cache.values() ) {
                String deps = classpath.dependencies.stream()
                        .map( Dependency::canonicalNotation )
                        .collect( Collectors.joining( "," ) );
                String libs = classpath.resolvedArtifacts.stream()
                        .map( File::getAbsolutePath )
                        .collect( Collectors.joining( File.pathSeparator ) );

                writer.write( deps );
                writer.write( ' ' );
                writer.write( libs );
                writer.newLine();
            }
        }

        boolean canMove = !cacheFile.exists() || cacheFile.delete();

        if ( canMove ) {
            Files.move( tempCacheFile.toPath(), cacheFile.toPath() );
            logger.info( "Dependencies cache saved at {}", cacheFile );
        } else {
            logger.warn( "Unable to save cache to {}. The file could not be re-created. Cache was saved at {}",
                    cacheFile, tempCacheFile );
        }
    }

    Classpath classpathOf( SortedSet<Dependency> dependencies,
                           Supplier<List<File>> compute ) {
        if ( dependencies.isEmpty() ) {
            return Classpath.empty();
        }

        if ( !isCacheLoaded.getAndSet( true ) ) {
            cache.putAll( loadCache() );
        }

        return cache.computeIfAbsent( Dependency.hashOf( dependencies ), ( hash ) ->
                new Classpath( dependencies, compute.get(), hash ) );
    }

    Map<String, Classpath> loadCache() {
        logger.debug( "Loading dependencies cache" );
        var result = cacheFrom( loadCacheEntries() );
        isCacheLoaded.set( true );
        return result;
    }

    private List<String> loadCacheEntries() {
        List<String> cacheEntries = Collections.emptyList();

        if ( cacheFile.isFile() ) {
            try {
                cacheEntries = Files.readAllLines( cacheFile.toPath(), StandardCharsets.UTF_8 );
            } catch ( IOException e ) {
                logger.warn( "Unable to read dependencies cache entries", e );
            }
        } else {
            logger.debug( "The provided path is not a file: {}", cacheFile );
        }

        logger.debug( "Loaded {} cache entries", cacheEntries.size() );

        return cacheEntries;
    }

    private Map<String, Classpath> cacheFrom( List<String> cacheEntries ) {
        Map<String, Classpath> cache = new HashMap<>();

        for ( String entry : cacheEntries ) {
            String[] parts = entry.split( " " );
            if ( parts.length == 2 ) {
                try {
                    var deps = Stream.of( parts[ 0 ].split( "," ) )
                            .map( Dependency::of )
                            .collect( Collectors.toCollection( TreeSet::new ) );

                    List<File> libs = Stream.of( parts[ 1 ].split( File.pathSeparator ) )
                            .map( File::new )
                            .collect( Collectors.toList() );

                    if ( libs.stream().allMatch( File::isFile ) ) {
                        logger.debug( "Loading dependency entry from cache: {} -> {}", deps, libs );
                        cache.put( Dependency.hashOf( deps ), new Classpath( deps, libs ) );
                    } else {
                        logger.info( "Ignoring cache entry because not all lib files exist" );
                    }
                } catch ( Exception e ) {
                    logger.warn( "Invalid cache entry: {}", entry, e );
                }
            } else if ( !entry.trim().isEmpty() ) {
                logger.info( "Ignoring cache entry because it has an unexpected format: {}", entry );
            }
        }

        return cache;
    }

}
