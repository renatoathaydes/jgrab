package com.athaydes.jgrab.test;

import com.athaydes.jgrab.JGrab;

@JGrab( group = "com.athaydes.osgi-run", module = "osgi-run-core", version = "1.0" )
public class RunnableClass {

    public static void main( String[] args ) {
        System.out.println( "Hello world" );
    }

}
