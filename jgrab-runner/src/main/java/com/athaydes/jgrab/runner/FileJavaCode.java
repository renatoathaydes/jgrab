package com.athaydes.jgrab.runner;

import com.athaydes.jgrab.Dependency;
import com.athaydes.jgrab.code.JavaCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.SortedSet;
import java.util.regex.Matcher;

/**
 * A class that knows how to extract information from the source code of a Java file.
 */
class FileJavaCode implements JavaCode {

    private static final Logger logger = LoggerFactory.getLogger( FileJavaCode.class );

    private final String className;
    private final List<String> lines;

    FileJavaCode( Path filePath ) throws IOException {
        if ( !filePath.toFile().exists() ) {
            throw new JGrabError( "File does not exist: " + filePath );
        }
        if ( !filePath.toFile().isFile() ) {
            throw new JGrabError( "Not a file: " + filePath + "!\n" +
                    "JGrab can only run a single Java file or a Java snippet with the -e option." );
        }

        this.lines = Files.readAllLines( filePath );
        this.className = extractClassName( lines, filePath );
        logger.debug( "Class name: {}", className );
    }

    @Override
    public boolean isSnippet() {
        return false; // Java files must be classes
    }

    @Override
    public SortedSet<Dependency> extractDependencies() {
        return Dependency.parseDependencies( lines.stream() );
    }

    @Override
    public String getClassName() {
        return className;
    }

    @Override
    public String getCode() {
        return String.join( "\n", lines );
    }

    private static String extractClassName( List<String> codeLines, Path filePath ) {
        for (String line : codeLines) {
            Matcher matcher = PACKAGE_PATTERN.matcher( line );

            if ( matcher.matches() ) {
                String packageName = matcher.group( 1 );
                return packageName.replace( File.separator, "." ) +
                        "." + withoutExtension( filePath.getFileName().toString() );
            }
        }

        // no package found
        return withoutExtension( filePath.getFileName().toString() );
    }

    private static String withoutExtension( String fileName ) {
        int index = fileName.lastIndexOf( "." );
        if ( index > 0 ) {
            return fileName.substring( 0, index );
        }
        return fileName;
    }

    @Override
    public String toString() {
        return getCode();
    }
}
