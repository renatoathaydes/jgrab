package com.athaydes.jgrab;

import java.util.Comparator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A single dependency on an external module.
 */
public class Dependency implements Comparable<Dependency> {

    private static final Pattern JGRAB_PATTERN = Pattern.compile(
            "\\s*//\\s*#jgrab\\s+([a-zA-Z-_0-9:.]+)\\s*" );

    public static final Comparator<Dependency> COMPARATOR = Comparator.comparing( Dependency::canonicalNotation );

    public final String group;
    public final String module;
    public final String version;
    private final String canonicalNotation;

    private Dependency( String group, String module, String version ) {
        this.group = group;
        this.module = module;
        this.version = version;
        canonicalNotation = group + ":" + module + ":" + version;
    }

    public static Dependency of( String declaration ) {
        String[] parts = declaration.split( ":" );
        if ( 3 < parts.length || parts.length < 2 ) {
            throw new RuntimeException( "Bad declaration (not of the form group:module[:version]): " +
                    declaration );
        }

        return new Dependency( parts[ 0 ], parts[ 1 ], parts.length == 2 ? "latest" : parts[ 2 ] );
    }

    public static Set<Dependency> parseDependencies( Stream<String> codeLines ) {
        return codeLines.flatMap( line -> {
            Matcher matcher = JGRAB_PATTERN.matcher( line );
            if ( matcher.matches() ) {
                return Stream.of( Dependency.of( matcher.group( 1 ) ) );
            } else {
                return Stream.empty();
            }
        } ).collect( Collectors.toSet() );
    }

    @Override
    public boolean equals( Object other ) {
        if ( this == other ) return true;
        if ( other == null || getClass() != other.getClass() ) return false;

        Dependency that = ( Dependency ) other;

        return canonicalNotation.equals( that.canonicalNotation );
    }

    @Override
    public int hashCode() {
        return canonicalNotation.hashCode();
    }

    public String canonicalNotation() {
        return canonicalNotation;
    }

    @Override
    public int compareTo( Dependency other ) {
        return COMPARATOR.compare( this, other );
    }

    @Override
    public String toString() {
        return "{" +
                "group='" + group + '\'' +
                ", module='" + module + '\'' +
                ", version='" + version + '\'' +
                '}';
    }
}
