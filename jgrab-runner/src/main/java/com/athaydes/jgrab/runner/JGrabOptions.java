package com.athaydes.jgrab.runner;

import java.io.File;

public abstract class JGrabOptions {
    public static final String SNIPPET_OPTION = "-e";

    static class Snippet extends JGrabOptions {
        final String code;

        public Snippet( String code ) {
            this.code = code;
        }
    }

    static class JavaFile extends JGrabOptions {
        final File file;
        final String[] args;

        public JavaFile( File file, String[] args ) {
            this.file = file;
            this.args = args;
        }
    }

    static class StdIn extends JGrabOptions {
    }

    static class Daemon extends JGrabOptions {
    }

    static class PrintVersion extends JGrabOptions {
    }

    static JGrabOptions parseOptions( String[] args ) {
        if ( args.length == 0 ) {
            return new StdIn();
        }
        if ( args.length == 1 ) {
            if ( args[ 0 ].equals( "--daemon" ) || args[ 0 ].equals( "-d" ) ) {
                return new Daemon();
            }
            if ( args[ 0 ].equals( "--help" ) || args[ 0 ].equals( "-h" ) ) {
                return help();
            }
            if ( args[ 0 ].equals( "--version" ) || args[ 0 ].equals( "-v" ) ) {
                return new PrintVersion();
            }
        }

        String first = args[ 0 ];
        String[] rest = new String[ args.length - 1 ];
        System.arraycopy( args, 1, rest, 0, rest.length );

        if ( first.equals( SNIPPET_OPTION ) ) {
            String script = String.join( " ", rest );
            return new JGrabOptions.Snippet( script );
        }

        if ( first.startsWith( "-" ) ) {
            throw new JGrabError( "Unknown option: " + first );
        }

        return new JGrabOptions.JavaFile( new File( first ), rest );
    }

    private static JGrabOptions help() {
        System.out.println( "=================== JGrab ===================\n" +
                " - https://github.com/renatoathaydes/jgrab -\n" +
                "=============================================\n" +
                "Jgrab can execute Java code from stdin (if not given any argument),\n" +
                "a Java file, or a Java snippet.\n\n" +
                "Usage:\n" +
                "  jgrab [<option> | java_file [java-args*] | -e java_snippet]\n" +
                "Options:\n" +
                "  --daemon -d\n" +
                "    Starts up the JGrab daemon (used by the jgrab-client).\n" +
                "  --help -h\n" +
                "    Shows usage.\n" +
                "  --version -v\n" +
                "    Shows version information." );

        return null;
    }
}
