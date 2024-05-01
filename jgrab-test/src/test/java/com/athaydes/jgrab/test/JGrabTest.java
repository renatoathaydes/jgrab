package com.athaydes.jgrab.test;

import org.junit.Test;

import java.util.List;
import java.util.Objects;

import static com.athaydes.jgrab.test.JGrabTestRunner.jgrab;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class JGrabTest {

    @Test
    public void runRunnableClass() throws Exception {
        String runnableClass = Objects.requireNonNull(
                getClass().getResource( "/RunnableClass.java" )
        ).getFile();

        var result = jgrab( runnableClass );

        result.assertOk();

        assertEquals( "Result: " + result,
                List.of(
                        "Hello interface com.athaydes.osgiaas.cli.core.CommandRunner",
                        "Privyet interface com.athaydes.osgiaas.cli.CommandCompleter",
                        "Tja class com.athaydes.osgiaas.api.ansi.Ansi",
                        "Hi interface org.apache.felix.shell.Command",
                        "Ola class jline.console.ConsoleReader" ),
                result.stdout.lines().collect( toList() ) );

        assertEquals( "", result.stderr.trim() );
    }

    @Test
    public void runScript() throws Exception {
        var result = jgrab( "-e", "2 + 3" );

        result.assertOk();

        assertEquals( "Result: " + result,
                List.of( "5" ),
                result.stdout.lines().collect( toList() ) );
        assertEquals( "", result.stderr.trim() );
    }

    @Test
    public void runVersion() throws Exception {
        var result = jgrab( "-v" );
        result.assertOk();
        assertThat( result.stdout, startsWith( "JGrab Runner Version" ) );
        assertEquals( "", result.stderr.trim() );
    }

    @Test
    public void unknownOption() throws Exception {
        var result = jgrab( "-f" );
        result.assertCode( 1 );
        assertEquals( "[main] ERROR com.athaydes.jgrab.runner.JGrabRunner - " +
                "Unknown option: -f", result.stderr.trim() );
    }

    @Test
    public void codeCannotSeeJGrabClasses() throws Exception {
        var result = jgrab( "-e", "com.athaydes.jgrab.runner.JGrabRunner.class" );
        result.assertCode( 1 );
        assertThat( result.stderr.trim(),
                containsString(
                        "java.lang.NoClassDefFoundError: " +
                                "com/athaydes/jgrab/runner/JGrabRunner" ) );
        assertEquals( "", result.stdout.trim() );
    }
}
