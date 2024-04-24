package com.athaydes.jgrab;

import java.io.File;

public final class JGrabHome {
    private static final String envVar = System.getenv( "JGRAB_HOME" );

    private static final File home = envVar == null
            ? new File( System.getProperty( "user.home", "." ), ".jgrab" )
            : new File( envVar );

    public static File getDir() {
        return home;
    }
}
