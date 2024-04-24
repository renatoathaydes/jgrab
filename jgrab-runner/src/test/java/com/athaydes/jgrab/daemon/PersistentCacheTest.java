package com.athaydes.jgrab.daemon;

import com.athaydes.jgrab.Classpath;
import com.athaydes.jgrab.Dependency;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class PersistentCacheTest {

    @Test
    public void canLoadValidCache() throws IOException {
        File tempDir = Files.createTempDirectory( "jgrab-persistent-cache-test" ).toFile();

        File dep1 = new File( tempDir, "1-valid-dep.jar" );
        File dep2 = new File( tempDir, "2-other-dep.jar" );
        File dep3 = new File( tempDir, "3-guava-20.0.jar" );

        for ( File file1 : List.of( dep1, dep2, dep3 ) ) {
            assertTrue( file1.createNewFile() );
        }

        byte[] contents = ( "some:valid-dep:1.0,other-dep:name " +
                dep1 + File.pathSeparator + dep2 + "\n" +
                "com.guava:guava:20.0 " + dep3 )
                .getBytes( StandardCharsets.UTF_8 );

        File file = new File( tempDir, "cache" );
        assertTrue( file.createNewFile() );
        Files.write( file.toPath(), contents, StandardOpenOption.WRITE );

        // create a cache with known contents
        PersistentCache cache = new PersistentCache( file );

        Supplier<List<File>> badSupplier = () -> {
            throw new RuntimeException( "Should not be called" );
        };

        var firstDeps = asDependencies( Stream.of(
                "some:valid-dep:1.0", "other-dep:name" ) );

        var secondDeps = asDependencies( Stream.of( "com.guava:guava:20.0" ) );

        var firstEntry = cache.classpathOf( firstDeps, badSupplier );
        var secondEntry = cache.classpathOf( secondDeps, badSupplier );

        assertEquals( List.of( dep1, dep2 ), firstEntry.resolvedArtifacts );
        assertEquals( List.of( dep3 ), secondEntry.resolvedArtifacts );
    }

    @Test
    public void newCacheCanBePopulatedThenSavedAsFile() throws IOException {
        File tempDir = Files.createTempDirectory( "jgrab-persistent-cache-test" ).toFile();

        File dep1 = new File( tempDir, "valid-dep.jar" );
        File dep2 = new File( tempDir, "other-dep.jar" );
        File dep3 = new File( tempDir, "guava-20.0.jar" );

        for ( File file1 : List.of( dep1, dep2, dep3 ) ) {
            assertTrue( file1.createNewFile() );
        }

        File file = new File( tempDir, "cache" );

        assertFalse( file.exists() );

        // create a cache with empty contents, non-existent file
        PersistentCache cache = new PersistentCache( file );

        Supplier<List<File>> firstSupplier = () -> List.of( dep1, dep2 );
        Supplier<List<File>> secondSupplier = () -> List.of( dep3 );

        var firstDeps = asDependencies( Stream.of(
                "some:valid-dep:1.0", "other-dep:name" ) );

        var secondDeps = asDependencies( Stream.of( "com.guava:guava:20.0" ) );

        var firstEntry = cache.classpathOf( firstDeps, firstSupplier );
        var secondEntry = cache.classpathOf( secondDeps, secondSupplier );

        // the dependencies should be created and returned
        assertEquals( List.of( dep1, dep2 ), firstEntry.resolvedArtifacts );
        assertEquals( List.of( dep3 ), secondEntry.resolvedArtifacts );

        // saving the cache now, should write out the dependencies to the file
        cache.save();

        assertTrue( file.isFile() );

        Map<String, Classpath> expectedCache = new HashMap<>( 2 );
        expectedCache.put( firstEntry.hash, firstEntry );
        expectedCache.put( secondEntry.hash, secondEntry );

        // when we load a new cache from the previous cache's file, the contents should be loaded as expected
        var newCache = new PersistentCache( file ).loadCache();

        assertEquals( expectedCache, newCache );
    }

    private SortedSet<Dependency> asDependencies( Stream<String> declarations ) {
        return declarations.map( Dependency::of ).collect( Collectors.toCollection( TreeSet::new ) );
    }

}
