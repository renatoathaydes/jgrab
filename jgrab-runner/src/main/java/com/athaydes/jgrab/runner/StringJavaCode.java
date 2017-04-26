package com.athaydes.jgrab.runner;

import com.athaydes.jgrab.Dependency;

import java.util.List;
import java.util.stream.Stream;

/**
 * A Java code snippet that can be executed.
 */
class StringJavaCode implements JavaCode {

    private final String code;
    private final String className;
    private final String[] codeLines;

    public StringJavaCode( String code ) {
        this.code = code;
        this.codeLines = code.split( "\n" );
        this.className = JavaCode.extractClassNameFrom( codeLines );
    }

    @Override
    public List<Dependency> extractDependencies() {
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

}
