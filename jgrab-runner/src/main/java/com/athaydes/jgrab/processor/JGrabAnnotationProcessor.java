package com.athaydes.jgrab.processor;

import com.athaydes.jgrab.JGrab;
import com.athaydes.jgrab.ivy.IvyGrabber;
import com.athaydes.jgrab.runner.Grabber;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * JGrab annotation processor.
 * <p>
 * This annotation processor will find all {@link JGrab} annotations and download the dependencies they
 * require into a directory given by the system property {@link JGrabAnnotationProcessor#JGRAB_TEMP_DIR_ENV_VAR}.
 */
@SupportedAnnotationTypes( {
        "com.athaydes.jgrab.JGrab",
        "com.athaydes.jgrab.JGrabGroup"
} )
@SupportedSourceVersion( SourceVersion.RELEASE_8 )
public class JGrabAnnotationProcessor extends AbstractProcessor {

    public static final String JGRAB_TEMP_DIR_ENV_VAR = "jgrab.temp.dir";
    public static final String JGRAB_LIB_DIR = "jgrab-libs";

    private final File tempDir = jgrabTempDir();

    @Override
    public boolean process( Set<? extends TypeElement> annotations,
                            RoundEnvironment roundEnv ) {
        try {
            List<JGrab> grabs = new ArrayList<>();

            for (Element element : roundEnv.getElementsAnnotatedWith( JGrab.class )) {
                grabs.addAll( Arrays.asList( element.getAnnotationsByType( JGrab.class ) ) );
            }

            if ( !grabs.isEmpty() ) {
                File libDir = new File( tempDir, JGRAB_LIB_DIR );
                libDir.mkdir();

                chooseGrabber().grab( grabs, libDir );
            }
        } catch ( Exception e ) {
            e.printStackTrace();
        }

        return true;
    }

    private static File jgrabTempDir() {
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
