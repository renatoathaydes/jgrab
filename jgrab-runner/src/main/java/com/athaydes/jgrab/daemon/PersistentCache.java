package com.athaydes.jgrab.daemon;

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
class PersistentCache {

    private static final Logger logger = LoggerFactory.getLogger( PersistentCache.class );

    private final File cacheFile;
    private final AtomicBoolean isCacheLoaded = new AtomicBoolean( false );
    private final Map<Set<Dependency>, List<File>> cache = new HashMap<>();

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
            for (Map.Entry<Set<Dependency>, List<File>> entry : cache.entrySet()) {
                var dependencies = new ArrayList<>( entry.getKey() );
                dependencies.sort( Dependency.COMPARATOR );
                String deps = dependencies.stream()
                        .map( Dependency::canonicalNotation )
                        .collect( Collectors.joining( "," ) );
                String libs = entry.getValue().stream()
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

    List<File> libsFor( Set<Dependency> dependencies,
                        Supplier<List<File>> compute ) {
        if ( dependencies.isEmpty() ) {
            return Collections.emptyList();
        }

        if ( !isCacheLoaded.getAndSet( true ) ) {
            cache.putAll( loadCache() );
        }

        return cache.computeIfAbsent( dependencies, ( ignore ) -> compute.get() );
    }

    Map<Set<Dependency>, List<File>> loadCache() {
        logger.debug( "Initializing dependencies cache" );
        return cacheFrom( loadCacheEntries() );
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

    private Map<Set<Dependency>, List<File>> cacheFrom( List<String> cacheEntries ) {
        Map<Set<Dependency>, List<File>> cache = new HashMap<>();

        for (String entry : cacheEntries) {
            String[] parts = entry.split( " " );
            if ( parts.length == 2 ) {
                try {
                    Set<Dependency> deps = Stream.of( parts[ 0 ].split( "," ) )
                            .map( Dependency::of )
                            .collect( Collectors.toSet() );

                    List<File> libs = Stream.of( parts[ 1 ].split( File.pathSeparator ) )
                            .map( File::new )
                            .collect( Collectors.toList() );

                    if ( libs.stream().allMatch( File::isFile ) ) {
                        logger.debug( "Loading dependency entry from cache: {} -> {}", deps, libs );
                        cache.put( deps, libs );
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
