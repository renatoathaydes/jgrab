package com.athaydes.jgrab.jbuild;

import com.athaydes.jgrab.Dependency;
import com.athaydes.jgrab.runner.Grabber;
import com.athaydes.jgrab.runner.JGrabError;
import jbuild.artifact.Artifact;
import jbuild.artifact.file.ArtifactFileWriter;
import jbuild.artifact.file.FileArtifactRetriever;
import jbuild.artifact.http.HttpArtifactRetriever;
import jbuild.commands.FetchCommandExecutor;
import jbuild.commands.InstallCommandExecutor;
import jbuild.log.JBuildLog;
import jbuild.maven.MavenUtils;
import jbuild.maven.Scope;
import jbuild.util.Either;
import jbuild.util.NonEmptyCollection;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static jbuild.artifact.file.ArtifactFileWriter.WriteMode.FLAT_DIR;

public class JBuildGrabber implements Grabber {

    public static final JBuildGrabber INSTANCE = new JBuildGrabber();

    private static final FilenameFilter JAR_FILTER = ( dir, name ) -> name.endsWith( ".jar" );

    @Override
    public List<File> grab( Collection<Dependency> toGrab ) {
        var log = new JBuildLog( System.out, false );

        var fetchCommand = new FetchCommandExecutor<>( log,
                NonEmptyCollection.of( List.of(
                        new FileArtifactRetriever( MavenUtils.mavenHome() ),
                        new HttpArtifactRetriever( log, MavenUtils.MAVEN_CENTRAL_URL ) ) ) );
        var tempDir = outputDir();
        var writer = new ArtifactFileWriter( tempDir, FLAT_DIR );
        var result = new InstallCommandExecutor( log, fetchCommand, writer )
                .installDependencyTree( artifacts( toGrab ), EnumSet.of( Scope.RUNTIME ), false, true, Set.of(), true )
                .toCompletableFuture();
        waitAndCheck( result );
        return Arrays.stream( Objects.requireNonNull( tempDir.listFiles( JAR_FILTER ) ) ).collect( toList() );
    }

    private void waitAndCheck( CompletableFuture<Either<Long, NonEmptyCollection<Throwable>>> result ) {
        try {
            result.get().map( ok -> null, JBuildGrabber::throwErrors );
        } catch ( InterruptedException | ExecutionException e ) {
            throw new JGrabError( "Unable to grab dependencies: " + e );
        }
    }

    private static Void throwErrors( NonEmptyCollection<Throwable> errors ) {
        throw new JGrabError( "Errors occurred while grabbing dependencies: " + errors );
    }

    private static File outputDir() {
        try {
            return Files.createTempDirectory( "jgrab-cache" ).toFile();
        } catch ( IOException e ) {
            throw new JGrabError( "Cannot create temp dir: " + e );
        }
    }

    private Set<? extends Artifact> artifacts( Collection<Dependency> toGrab ) {
        return toGrab.stream()
                .map( JBuildGrabber::depToArtifact )
                .collect( Collectors.toSet() );
    }

    private static Artifact depToArtifact( Dependency dependency ) {
        return new Artifact( dependency.group, dependency.module, dependency.version );
    }
}
