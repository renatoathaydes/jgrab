package com.athaydes.jgrab.runner;

import com.athaydes.jgrab.JGrab;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 */
public class JGrabAnnotationProcessor extends AbstractProcessor {

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

        grabAll( grabs );

        return true;
    }

    private static void grabAll( List<JGrab> grabs ) {
        System.out.println( "Grabbing " + grabs );
    }

}
