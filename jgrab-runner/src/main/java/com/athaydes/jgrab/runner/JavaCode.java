package com.athaydes.jgrab.runner;

import com.athaydes.jgrab.Dependency;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java code that can be executed.
 * <p>
 * It may be just a snippet or a Runnable class or a class containing a main function.
 */
interface JavaCode {

    Pattern PACKAGE_PATTERN = Pattern.compile( "\\s*package\\s+([a-zA-Z_0-9.$]+)\\s*;?\\s*" );

    Pattern CLASS_PATTERN = Pattern.compile(
            "\\s*((public|static|private|abstract|final)\\s+)*\\s*class\\s+(?<name>[a-zA-Z_0-9.$]+).*" );

    List<Dependency> extractDependencies();

    String getClassName();

    String getCode();

    /**
     * @return if this code is just a snippet, it will be wrapped into a class' main function and run.
     * Otherwise, this code is expected to be a class implementing Runnable or containing a main function.
     */
    boolean isSnippet();

    static String extractClassNameFrom( String[] codeLines ) {
        boolean packageFound = false;
        String packageName = "";

        for (String line : codeLines) {
            Matcher matcher = PACKAGE_PATTERN.matcher( line );

            if ( !packageFound && matcher.matches() ) {
                packageName = matcher.group( 1 );
                packageFound = true;
            } else {
                Matcher classMatcher = CLASS_PATTERN.matcher( line );
                if ( classMatcher.matches() ) {
                    String className = classMatcher.group( "name" );
                    if ( !packageName.isEmpty() ) {
                        className = packageName + "." + className;
                    }
                    return className;
                }
            }
        }

        return null;
    }
}
