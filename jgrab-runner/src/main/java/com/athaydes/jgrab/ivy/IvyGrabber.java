package com.athaydes.jgrab.ivy;

import com.athaydes.jgrab.Dependency;
import com.athaydes.jgrab.runner.Grabber;
import org.apache.ivy.Ivy;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.report.ResolveReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * An Ivy-based grabber of dependencies.
 */
public class IvyGrabber implements Grabber {

    private static final Logger logger = LoggerFactory.getLogger( IvyGrabber.class );

    private final IvyFactory ivyFactory = new IvyFactory();

    private static final IvyGrabber INSTANCE = new IvyGrabber();

    public static IvyGrabber getInstance() {
        return INSTANCE;
    }

    private IvyGrabber() {
        // private
    }

    @Override
    public List<File> grab( Collection<Dependency> toGrab ) {
        Ivy ivy = null;
        try {
            ivy = getIvy();
        } catch ( Exception e ) {
            logger.error( "Error getting instance of Ivy", e );
        }

        if ( ivy == null ) {
            logger.warn( "Unable to get Ivy instance" );
            return Collections.emptyList();
        }

        List<File> result = new ArrayList<>( toGrab.size() * 2 );

        for (Dependency grab : toGrab) {
            logger.debug( "Grabbing {}", grab );
            try {
                ResolveReport resolveReport = new IvyResolver( ivy )
                        .includeTransitiveDependencies( true )
                        .downloadJarOnly( true )
                        .resolve( grab.group, grab.module, grab.version );
                addDependencies( resolveReport, result );
            } catch ( RuntimeException | IOException e ) {
                e.printStackTrace();
                System.err.println( e.toString() );
            }
        }

        return result;
    }

    private void addDependencies( ResolveReport resolveReport, List<File> files ) throws IOException {
        if ( resolveReport.hasError() ) {
            throw new RuntimeException( "Could not resolve dependencies: " + resolveReport.getAllProblemMessages() );
        } else for (ArtifactDownloadReport report : resolveReport.getAllArtifactsReports()) {
            files.add( report.getLocalFile() );
        }
    }

    private Ivy getIvy() {
        return ivyFactory.getIvy( null, true, System.err );
    }

}
