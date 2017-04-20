package com.athaydes.jgrab.ivy;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.report.ResolveReport;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thin wrapper around {@link Ivy} that makes it easy to resolve a dependency.
 */
class IvyResolver {

    private static final Pattern DEPENDENCY_LINE = Pattern.compile( "(\\s*)(\\$\\{DEPENDENCY})\\s*" );

    private final Ivy ivy;

    private boolean includeTransitive = true;
    private boolean downloadJarOnly = true;

    IvyResolver( Ivy ivy ) {
        this.ivy = ivy;
    }

    IvyResolver includeTransitiveDependencies( boolean include ) {
        this.includeTransitive = include;
        return this;
    }

    IvyResolver downloadJarOnly( boolean downloadJarOnly ) {
        this.downloadJarOnly = downloadJarOnly;
        return this;
    }

    ResolveReport resolve( String group, String module, String version ) {
        AtomicBoolean dependencyLineFound = new AtomicBoolean( false );

        File tempModule = null;

        try {
            tempModule = File.createTempFile( "ivy-module-", ".xml" );

            try ( FileWriter writer = new FileWriter( tempModule );
                  BufferedReader buffer = new BufferedReader(
                          new InputStreamReader( getClass().getResourceAsStream( "/ivy-module-template.xml" ) ) ) ) {

                buffer.lines().map( it -> {
                    if ( !dependencyLineFound.get() ) {
                        Matcher matcher = DEPENDENCY_LINE.matcher( it );
                        if ( matcher.matches() ) {
                            dependencyLineFound.set( true );
                            return matcher.replaceFirst( "$1" + xmlForDependency( group, module, version ) );
                        }
                    }

                    return it;
                } ).forEach( line -> {
                    try {
                        writer.append( line ).append( "\n" );
                    } catch ( IOException e ) {
                        e.printStackTrace();
                    }
                } );
            }

            return ivy.resolve( tempModule );
        } catch ( ParseException | IOException e ) {
            throw new RuntimeException( e );
        } finally {
            if ( tempModule != null ) {
                //noinspection ResultOfMethodCallIgnored
                tempModule.delete();
            }
        }
    }

    private String xmlForDependency( String group, String module, String version ) {
        String transitive = includeTransitive ? "" : " transitive=\"false\"";

        // to download only the main jar, we use the default conf.
        // Not specifying a conf means downloading all confs.
        String conf = downloadJarOnly ? " conf=\"default\"" : "";

        return String.format( "<dependency org=\"%s\" name=\"%s\" rev=\"%s\"%s%s/>",
                group, module, version, conf, transitive );
    }

}