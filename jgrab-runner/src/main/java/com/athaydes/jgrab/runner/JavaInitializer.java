package com.athaydes.jgrab.runner;

import java.io.File;
import java.util.Arrays;

class JavaInitializer {

    static class JavaInfo {
        final File javaHome;
        final File java;
        final File javac;

        private JavaInfo( File javaHome, File java, File javac ) {
            this.javaHome = javaHome;
            this.java = java;
            this.javac = javac;
        }
    }

    static JavaInfo getJavaInfo() {
        File javaHome = null;
        File java = null;
        File javac = null;

        String[] javaHomes = javaLocations();

        for (String home : javaHomes) {
            File candidateJava = new File( home, "/bin/java" );
            File candidateJavac = new File( home, "/bin/javac" );

            if ( candidateJava.isFile() && candidateJavac.isFile() ) {
                javaHome = new File( home );
                java = candidateJava;
                javac = candidateJavac;
                break;
            }
        }

        if ( java == null ) {
            throw new RuntimeException( "Cannot locate Java, tried locations: " +
                    Arrays.toString( javaHomes ) +
                    "\n\nSet the JAVA_HOME environment variable to point to a valid Java SDK directory." );
        }

        return new JavaInfo( javaHome, java, javac );
    }

    private static String[] javaLocations() {
        return new String[]{
                System.getenv( "JAVA_HOME" ),
                System.getProperty( "java.home" )
        };
    }

}
