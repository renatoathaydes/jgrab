package com.athaydes.jgrab.code;

import com.athaydes.jgrab.Dependency;

import java.util.SortedSet;
import java.util.stream.Stream;

/**
 * A Java code snippet that can be executed.
 */
public class StringJavaCode implements JavaCode {

    private final String code;
    private final String className;
    private final String[] codeLines;

    public StringJavaCode( String code ) {
        this.code = code;
        this.codeLines = code.split( "\n" );
        this.className = JavaCode.extractClassNameFrom( codeLines );
    }

    @Override
    public SortedSet<Dependency> extractDependencies() {
        return Dependency.parseDependencies( Stream.of( codeLines ) );
    }

    @Override
    public String getClassName() {
        return this.className;
    }

    @Override
    public boolean isSnippet() {
        return this.className == null;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String toString() {
        return getCode();
    }

}
