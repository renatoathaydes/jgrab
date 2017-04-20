package com.athaydes.jgrab.runner;

import com.athaydes.jgrab.JGrab;
import com.athaydes.jgrab.ivy.IvyGrabber;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * JGrab annotation processor.
 * <p>
 * This annotation processor will find all {@link JGrab} annotations and download the dependencies they
 * require into a directory given by the system property {@link JGrabAnnotationProcessor#JGRAB_TEMP_DIR_ENV_VAR}.
 */
public class JGrabAnnotationProcessor extends AbstractProcessor {

    static final String JGRAB_TEMP_DIR_ENV_VAR = "jgrab.temp.dir";

    private final Grabber grabber = chooseGrabber();
    private final File tempDir = jgragTempDir();

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> result = new HashSet<>( 2 );
        result.add( "com.athaydes.jgrab.JGrab" );
        result.add( "com.athaydes.jgrab.JGrabGroup" );
        return Collections.unmodifiableSet( result );
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process( Set<? extends TypeElement> annotations,
                            RoundEnvironment roundEnv ) {
        List<JGrab> grabs = new ArrayList<>();

        for (Element element : roundEnv.getElementsAnnotatedWith( JGrab.class )) {
            grabs.addAll( Arrays.asList( element.getAnnotationsByType( JGrab.class ) ) );
        }

        if ( !grabs.isEmpty() ) {
            grabber.grab( grabs, tempDir );
        }

        return true;
    }

    private static File jgragTempDir() {
        String dir = System.getenv( JGRAB_TEMP_DIR_ENV_VAR );
        boolean noDir = dir == null || dir.trim().isEmpty();
        File tempDir = noDir ? null : new File( dir );

        if ( tempDir != null && tempDir.isDirectory() ) {
            return tempDir;
        }

        String errorReason = noDir ?
                "The " + JGRAB_TEMP_DIR_ENV_VAR + " environment variable has not " +
                        "been set. JGrab cannot run without that." :
                "The " + JGRAB_TEMP_DIR_ENV_VAR + " environment variable is not pointing at a directory: " +
                        tempDir + ". JGrab cannot run!";

        throw new RuntimeException( errorReason );
    }

    private static Grabber chooseGrabber() {
        // TODO support other grabbers?
        return new IvyGrabber();
    }

}
