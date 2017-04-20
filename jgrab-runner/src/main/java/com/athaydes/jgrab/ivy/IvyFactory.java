package com.athaydes.jgrab.ivy;

import org.apache.ivy.Ivy;
import org.apache.ivy.util.AbstractMessageLogger;
import org.apache.ivy.util.Message;
import org.apache.ivy.util.MessageLogger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Creates or return a previously created {@link Ivy} instance depending on which repositories are used.
 */
class IvyFactory {

    static final String JCENTER = "https://jcenter.bintray.com";

    private static final String LOCAL_M2_REPOSITORY = "<ibiblio name=\"localm2\" " +
            "root=\"file:${user.home}/.m2/repository/\" checkmodified=\"true\" " +
            "changingPattern=\".*\" changingMatcher=\"regexp\" m2compatible=\"true\"/>";

    private static final Pattern REPOSITORIES_PATTERN = Pattern.compile( "(\\s*)\\$(\\{REPOSITORIES})\\s*" );

    private final Set<URL> defaultRepositories;
    private final Map<RepositoryConfig, Ivy> ivyByConfig = new ConcurrentHashMap<>( 4 );
    private final AtomicBoolean verbose = new AtomicBoolean( false );

    IvyFactory() {
        URL jcenterURL;
        try {
            jcenterURL = new URL( JCENTER );
        } catch ( MalformedURLException e ) {
            throw new IllegalStateException( "JCenter URL constant has an invalid value", e );
        }

        defaultRepositories = Collections.singleton( jcenterURL );

        RepositoryConfig defaultConfig = new RepositoryConfig( defaultRepositories, true );
        ivyByConfig.put( defaultConfig, createIvyWith( defaultConfig, null ) );
    }

    public void setVerbose( boolean verbose ) {
        this.verbose.set( verbose );
    }

    /**
     * Create or re-use an Ivy instance using the provided repositories.
     *
     * @param repositories      URL to Maven repositories or null to use the default repositories (JCenter).
     * @param includeMavenLocal include the Maven local repository
     * @param out               stream to be used to log information
     * @return Ivy instance (may be re-used) or null if this factory is not ready yet.
     */
    Ivy getIvy( Set<URL> repositories, boolean includeMavenLocal, PrintStream out ) {
        if ( repositories == null ) {
            repositories = defaultRepositories;
        }

        RepositoryConfig config = new RepositoryConfig( repositories, includeMavenLocal );

        if ( !ivyByConfig.containsKey( config ) ) {
            ivyByConfig.put( config, createIvyWith( config, out ) );
        }

        return ivyByConfig.get( config );
    }

    private Ivy createIvyWith( RepositoryConfig config, PrintStream out ) {
        Ivy ivy = Ivy.newInstance();

        MessageLogger defaultLogger = Message.getDefaultLogger();

        ivy.getLoggerEngine().setDefaultLogger( new AbstractMessageLogger() {
            @Override
            protected void doProgress() {
                if ( verbose.get() && out != null ) out.print( "-" );
            }

            @Override
            protected void doEndProgress( String msg ) {
                if ( verbose.get() && out != null ) out.println( "." );
            }

            @Override
            public void log( String msg, int level ) {
                if ( verbose.get() ) defaultLogger.log( msg, level );
            }

            @Override
            public void rawlog( String msg, int level ) {
                if ( verbose.get() ) defaultLogger.rawlog( msg, level );
            }
        } );

        AtomicBoolean repositoriesFound = new AtomicBoolean( false );

        try {
            File tempSettings = File.createTempFile( "ivy-settings-", ".xml" );

            try ( FileWriter writer = new FileWriter( tempSettings );
                  BufferedReader buffer = new BufferedReader(
                          new InputStreamReader( getClass().getResourceAsStream( "/ivy-settings-template.xml" ) ) ) ) {

                buffer.lines().map( line -> {
                    if ( !repositoriesFound.get() ) {
                        Matcher matcher = REPOSITORIES_PATTERN.matcher( line );
                        if ( matcher.matches() ) {
                            repositoriesFound.set( true );

                            String repositoriesXml = xmlForRepositories( config )
                                    .map( repo -> "$1" + Matcher.quoteReplacement( repo ) )
                                    .collect( Collectors.joining( "\n" ) );

                            return matcher.replaceFirst( repositoriesXml );
                        }
                    }

                    return line;
                } ).forEach( line -> {
                    try {
                        writer.append( line ).append( "\n" );
                    } catch ( IOException e ) {
                        e.printStackTrace();
                    }
                } );
            }

            ivy.configure( tempSettings );
        } catch ( ParseException | IOException e ) {
            e.printStackTrace();
        }

        return ivy;
    }

    private Stream<String> xmlForRepositories( RepositoryConfig config ) {
        Stream<String> localRepo = config.useMavenLocal ? Stream.of( LOCAL_M2_REPOSITORY ) : Stream.empty();
        return Stream.concat( localRepo, config.configuredIvyRepos.stream()
                .map( it -> String.format(
                        "<ibiblio name=\"%s\" root=\"%s\" m2compatible=\"true\"/>",
                        it.getHost(), it.toString() ) ) );
    }

    private static class RepositoryConfig {
        Set<URL> configuredIvyRepos;
        boolean useMavenLocal;

        RepositoryConfig( Set<URL> configuredIvyRepos, boolean useMavenLocal ) {
            this.configuredIvyRepos = configuredIvyRepos;
            this.useMavenLocal = useMavenLocal;
        }

        @Override
        public boolean equals( Object o ) {
            if ( this == o ) return true;
            if ( o == null || getClass() != o.getClass() ) return false;

            RepositoryConfig that = ( RepositoryConfig ) o;

            return useMavenLocal == that.useMavenLocal &&
                    configuredIvyRepos.equals( that.configuredIvyRepos );
        }

        @Override
        public int hashCode() {
            int result = configuredIvyRepos.hashCode();
            result = 31 * result + ( useMavenLocal ? 1 : 0 );
            return result;
        }

        @Override
        public String toString() {
            return "RepositoryConfig{" +
                    "configuredIvyRepos=" + configuredIvyRepos +
                    ", useMavenLocal=" + useMavenLocal +
                    '}';
        }
    }

}