package com.athaydes.jgrab;

import java.io.File;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableSortedSet;

public final class Classpath {
    private static final Classpath EMPTY = new Classpath( new TreeSet<>(), List.of() );

    public final SortedSet<Dependency> dependencies;
    public final List<File> resolvedArtifacts;
    public final String hash;

    public Classpath( SortedSet<Dependency> dependencies, List<File> resolvedArtifacts ) {
        this( dependencies, resolvedArtifacts, Dependency.hashOf( dependencies ) );
    }

    public Classpath( SortedSet<Dependency> dependencies,
                      List<File> resolvedArtifacts,
                      String hash ) {
        this.dependencies = unmodifiableSortedSet( dependencies );
        this.resolvedArtifacts = unmodifiableList( resolvedArtifacts );
        this.hash = hash;
    }

    public static Classpath empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return resolvedArtifacts.isEmpty();
    }

    @Override
    public boolean equals( Object o ) {
        if ( this == o ) return true;
        if ( o == null || getClass() != o.getClass() ) return false;

        Classpath classpath = ( Classpath ) o;

        return hash.equals( classpath.hash );
    }

    @Override
    public int hashCode() {
        return hash.hashCode();
    }

    @Override
    public String toString() {
        return "Classpath{" +
                "dependencies=" + dependencies +
                ", resolvedArtifacts=" + resolvedArtifacts +
                '}';
    }
}
