package com.athaydes.jgrab.test;

import com.athaydes.osgiaas.api.ansi.Ansi;
import com.athaydes.osgiaas.cli.CommandCompleter;
import com.athaydes.osgiaas.cli.core.CommandRunner;
import jline.console.ConsoleReader;
import org.apache.felix.shell.Command;

// #jgrab com.athaydes.osgiaas:osgiaas-cli-core:0.7
public class RunnableClass {

    public static void main( String[] args ) {
        // check that each dependency and transitive dependency has been collected

        // osgiaas-cli-core
        System.out.println( "Hello " + CommandRunner.class );
        // osgiaas-cli-api
        System.out.println( "Privyet " + CommandCompleter.class );
        // osgiaas-common
        System.out.println( "Tja " + Ansi.class );
        // apache.felix.shell
        System.out.println( "Hi " + Command.class );
        // jline
        System.out.println( "Ola " + ConsoleReader.class );
    }

}
