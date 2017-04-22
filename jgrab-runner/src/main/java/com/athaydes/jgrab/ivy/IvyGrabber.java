package com.athaydes.jgrab.ivy;

import com.athaydes.jgrab.Dependency;
import com.athaydes.jgrab.log.Logger;
import com.athaydes.jgrab.runner.Grabber;
import org.apache.ivy.Ivy;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.report.ResolveReport;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;

/**
 * An Ivy-based grabber of dependencies.
 */
public class IvyGrabber implements Grabber {

    private final IvyFactory ivyFactory = new IvyFactory();

    @Override
    public void grab( Collection<Dependency> toGrab, File dir ) {

        Ivy ivy = null;
        try {
            ivy = getIvy();
        } catch ( Exception e ) {
            Logger.log( "ERROR getting Ivy: " + e );
        }

        if ( ivy == null ) {
            Logger.log( "Unable to get Ivy instance" );
            return;
        }

        for (Dependency grab : toGrab) {
            Logger.log( "Grabbing " + grab );
            try {
                ResolveReport resolveReport = new IvyResolver( ivy )
                        .includeTransitiveDependencies( true )
                        .downloadJarOnly( true )
                        .resolve( grab.group, grab.module, grab.version );
                copyDependencies( resolveReport, dir );
            } catch ( RuntimeException | IOException e ) {
                e.printStackTrace();
                System.err.println( e.toString() );
            }
        }
    }

    private void copyDependencies( ResolveReport resolveReport, File dir ) throws IOException {
        if ( resolveReport.hasError() ) {
            throw new RuntimeException( "Could not resolve dependencies: " + resolveReport.getAllProblemMessages() );
        } else for (ArtifactDownloadReport report : resolveReport.getAllArtifactsReports()) {
            File jar = report.getLocalFile();
            Files.copy( jar.toPath(), new File( dir, jar.getName() ).toPath() );
        }
    }

    private Ivy getIvy() {
        return ivyFactory.getIvy( null, true, System.err );
    }

}
