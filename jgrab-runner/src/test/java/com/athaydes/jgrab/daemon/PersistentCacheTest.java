package com.athaydes.jgrab.daemon;

import com.athaydes.jgrab.Dependency;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class PersistentCacheTest {

    @Test
    public void canLoadValidCache() throws IOException {
        File tempDir = Files.createTempDirectory( "jgrab-persistent-cache-test" ).toFile();

        File dep1 = new File( tempDir, "valid-dep.jar" );
        File dep2 = new File( tempDir, "other-dep.jar" );
        File dep3 = new File( tempDir, "guava-20.0.jar" );

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

        Set<Dependency> firstDeps = asDependencies( Stream.of(
                "some:valid-dep:1.0", "other-dep:name" ) );

        Set<Dependency> secondDeps = asDependencies( Stream.of( "com.guava:guava:20.0" ) );

        List<File> firstEntry = cache.libsFor( firstDeps, badSupplier );
        List<File> secondEntry = cache.libsFor( secondDeps, badSupplier );

        assertEquals( List.of( dep1, dep2 ), firstEntry );
        assertEquals( List.of( dep3 ), secondEntry );
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

        Set<Dependency> firstDeps = asDependencies( Stream.of(
                "some:valid-dep:1.0", "other-dep:name" ) );

        Set<Dependency> secondDeps = asDependencies( Stream.of( "com.guava:guava:20.0" ) );

        List<File> firstEntry = cache.libsFor( firstDeps, firstSupplier );
        List<File> secondEntry = cache.libsFor( secondDeps, secondSupplier );

        // the dependencies should be created and returned
        assertEquals( List.of( dep1, dep2 ), firstEntry );
        assertEquals( List.of( dep3 ), secondEntry );

        // saving the cache now, should write out the dependencies to the file
        cache.save();

        assertTrue( file.isFile() );

        Map<Set<Dependency>, List<File>> expectedCache = new HashMap<>( 2 );
        expectedCache.put( firstDeps, firstEntry );
        expectedCache.put( secondDeps, secondEntry );

        // when we load a new cache from the previous cache's file, the contents should be loaded as expected
        Map<Set<Dependency>, List<File>> newCache = new PersistentCache( file ).loadCache();

        assertEquals( expectedCache, newCache );
    }

    private Set<Dependency> asDependencies( Stream<String> declarations ) {
        return declarations.map( Dependency::of ).collect( Collectors.toSet() );
    }

}
