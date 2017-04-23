package com.athaydes.jgrab.runner;

import com.athaydes.jgrab.Dependency;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A class that knows how to extract information from the source code of a Java file.
 */
class JavaFileHandler {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger( JavaFileHandler.class );

    private static final Pattern PACKAGE_PATTERN = Pattern.compile( "\\s*package\\s+([a-zA-Z_0-9.$]+)\\s*;?\\s*" );

    private static final Pattern JGRAB_PATTERN = Pattern.compile(
            "\\s*//\\s*#jgrab\\s+([a-zA-Z-_0-9:.]+)\\s*" );

    private final String className;
    private final List<String> lines;

    JavaFileHandler( Path filePath ) throws IOException {
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

    List<Dependency> extractDependencies()
            throws IOException {
        return lines.stream().flatMap( line -> {
            Matcher matcher = JGRAB_PATTERN.matcher( line );
            if ( matcher.matches() ) {
                return Stream.of( Dependency.of( matcher.group( 1 ) ) );
            } else {
                return Stream.empty();
            }
        } ).collect( Collectors.toList() );
    }

    String getClassName() {
        return className;
    }

    String getCode() {
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

}
