package com.athaydes.jgrab.test;

import com.athaydes.jgrab.JGrab;
import com.athaydes.osgiaas.cli.core.CommandRunner;

@JGrab( group = "com.athaydes.osgiaas", module = "osgiaas-cli-core", version = "0.7" )
public class RunnableClass {

    public static void main( String[] args ) {
        System.out.println( "Hello " + CommandRunner.class );
    }

}
