package com.athaydes.jgrab;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A single dependency on an external module.
 */
public class Dependency {

    private static final Pattern JGRAB_PATTERN = Pattern.compile(
            "\\s*//\\s*#jgrab\\s+([a-zA-Z-_0-9:.]+)\\s*" );

    public final String group;
    public final String module;
    public final String version;

    private Dependency( String group, String module, String version ) {
        this.group = group;
        this.module = module;
        this.version = version;
    }

    private static Dependency of( String declaration ) {
        String[] parts = declaration.split( ":" );
        if ( 3 < parts.length || parts.length < 2 ) {
            throw new RuntimeException( "Bad declaration (not of the form group:module[:version]): " +
                    declaration );
        }

        return new Dependency( parts[ 0 ], parts[ 1 ], parts.length == 2 ? "latest" : parts[ 2 ] );
    }

    public static List<Dependency> extractDependencies( Path javaFile )
            throws IOException {
        return Files.lines( javaFile ).flatMap( line -> {
            Matcher matcher = JGRAB_PATTERN.matcher( line );
            if ( matcher.matches() ) {
                return Stream.of( Dependency.of( matcher.group( 1 ) ) );
            } else {
                return Stream.empty();
            }
        } ).collect( Collectors.toList() );
    }
}
