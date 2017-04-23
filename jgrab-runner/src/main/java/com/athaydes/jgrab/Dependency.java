package com.athaydes.jgrab;

/**
 * A single dependency on an external module.
 */
public class Dependency {

    public final String group;
    public final String module;
    public final String version;

    private Dependency( String group, String module, String version ) {
        this.group = group;
        this.module = module;
        this.version = version;
    }

    public static Dependency of( String declaration ) {
        String[] parts = declaration.split( ":" );
        if ( 3 < parts.length || parts.length < 2 ) {
            throw new RuntimeException( "Bad declaration (not of the form group:module[:version]): " +
                    declaration );
        }

        return new Dependency( parts[ 0 ], parts[ 1 ], parts.length == 2 ? "latest" : parts[ 2 ] );
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
