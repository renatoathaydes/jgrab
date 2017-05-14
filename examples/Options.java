public class Options {

    public static void main( String[] args ) {
        if ( args.length != 1 ) {
            System.err.println( "One argument expected, found " + args.length );
            return;
        }

        switch ( args[ 0 ] ) {
            case "-h":
            case "--help":
                System.out.println( "This little program shows how to use arguments!\n" +
                        "Usage:\n" +
                        "  -v --version\n" +
                        "    Show the version of this program.\n" +
                        "  -h --help\n" +
                        "    Show this help message." );
                break;
            case "-v":
            case "--version":
                System.out.println( "Options Version 1.0" );
                break;
            default:
                System.out.println( "Invalid option: " + args[ 0 ] );
        }
    }

}