package com.athaydes.jgrab.test;

import static org.junit.Assert.fail;

public class ProcessResult {
    public final int exitCode;
    public final String stdout;
    public final String stderr;

    public ProcessResult( int exitCode, String stdout, String stderr ) {
        this.exitCode = exitCode;
        this.stdout = stdout;
        this.stderr = stderr;
    }

    public void assertOk() {
        assertCode( 0 );
    }

    public void assertCode( int expectedCode ) {
        if ( exitCode != expectedCode ) {
            System.out.println( "===> Process stderr:\n" + stderr );
            System.out.println( "===> Process stdout:\n" + stdout );
            System.out.println( "----------------------------------" );
            fail( "Process exited with code " + exitCode );
        }
    }
}
