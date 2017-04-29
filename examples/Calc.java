package com.athaydes.jgrab.examples;

import java.util.LinkedList;
import java.util.Scanner;
import java.util.stream.Collectors;

/**
 * A simple reverse polish notation calculator.
 * <p>
 * FIXME does not work when using the JGrab Daemon.
 */
public class Calc implements Runnable {

    private static final Operator PLUS = new Plus();
    private static final Operator MINUS = new Minus();
    private static final Operator MULT = new Mult();
    private static final Operator DIV = new Div();

    private final LinkedList<StackItem> stack = new LinkedList<>();

    private String getNextLine( Scanner scanner ) {
        System.out.print( "> " );
        System.out.flush(); // flush in case sysout is a daemon socket
        if ( scanner.hasNextLine() ) {
            return scanner.nextLine();
        } else {
            return null;
        }
    }

    /**
     * Handle user input.
     *
     * @param input user input
     * @return true to continue, false to exit.
     */
    private boolean handle( String input ) {
        switch ( input ) {
            case "+":
                System.out.println( PLUS.run( stack ) );
                return true;
            case "-":
                System.out.println( MINUS.run( stack ) );
                return true;
            case "*":
                System.out.println( MULT.run( stack ) );
                return true;
            case "/":
                System.out.println( DIV.run( stack ) );
                return true;
            case "?":
            case "h":
            case "help":
                printHelp();
                return true;
            case "q":
            case "quit":
                System.out.println( "Goodbye!" );
                return false;
            case "s":
            case "show":
                System.out.println( stack.stream()
                        .map( StackItem::name )
                        .collect( Collectors.toList() ) );
                return true;
            case "d":
            case "drop":
                if ( stack.isEmpty() ) {
                    System.out.println( "<Error - Empty stack>" );
                } else {
                    System.out.println( stack.pop().name() );
                }
                return true;
            default: // number
                try {
                    double d = Double.parseDouble( input );
                    stack.push( new Numeric( d ) );
                } catch ( NumberFormatException e ) {
                    System.out.println( "<Error - invalid input, not a number, operator or action>" );
                }
                return true;
        }
    }

    private void printHelp() {
        System.out.println( "----- JGrab Example Calculator ----" );
        System.out.println( "Enter numbers and operations in each line using reverse Polish notation." );
        System.out.println( "To show the whole stack, enter 's' or 'show'." );
        System.out.println( "To show and drop an item off the stack, enter 'd' or 'drop'." );
        System.out.println( "Enter 'q' or 'quit' to quit, '?', 'h' or 'help' to show this information." );
        System.out.println( "Example (calculate ( 2 * 2 ) + ( 6 * 10 ) ):" );
        System.out.println( "-------" );
        System.out.println( "> 2" );
        System.out.println( "> 2" );
        System.out.println( "> *" );
        System.out.println( "4.0" );
        System.out.println( "> 6" );
        System.out.println( "> 10" );
        System.out.println( "> *" );
        System.out.println( "60.0" );
        System.out.println( "> +" );
        System.out.println( "64.0" );
        System.out.println( "> show" );
        System.out.println( "[64.0]" );
        System.out.println( "-------" );
    }

    public void run() {
        System.out.println( "----- JGrab Example Calculator ----" );
        System.out.println( "Enter numbers and operations in each line using reverse Polish notation." );
        System.out.println( "To show the whole stack, enter 's' or 'show'" );
        System.out.println( "Enter 'q' or 'quit' to quit, '?', 'h' or 'help' to show help." );
        System.out.println( "-----------------------------------" );

        Scanner scanner = new Scanner( System.in );

        String line;
        while ( ( line = getNextLine( scanner ) ) != null ) {
            boolean continue_ = handle( line );
            if ( !continue_ ) {
                break;
            }
        }
    }
}

interface StackItem {
    String name();
}

class Numeric implements StackItem {
    final double value;

    Numeric( double number ) {
        this.value = number;
    }

    @Override
    public String name() {
        return Double.toString( value );
    }
}

interface Operator {
    String name();

    double apply( double n1, double n2 );

    default String run( LinkedList<StackItem> items ) {
        if ( items.size() < 2 ) {
            return "<Not enough inputs in the stack>";
        }

        StackItem item2 = items.pop();
        StackItem item1 = items.pop();

        if ( item1 instanceof Numeric && item2 instanceof Numeric ) {
            Numeric result = new Numeric( apply( ( ( Numeric ) item1 ).value, ( ( Numeric ) item2 ).value ) );
            items.push( result );
            return result.name();
        } else {
            items.push( item1 );
            items.push( item2 );
            return "<" + name() + " error - stack does not contain two numbers at the top>";
        }
    }
}

class Plus implements Operator {
    @Override
    public String name() {
        return "+";
    }

    @Override
    public double apply( double n1, double n2 ) {
        return n1 + n2;
    }
}

class Minus implements Operator {
    @Override
    public String name() {
        return "-";
    }

    @Override
    public double apply( double n1, double n2 ) {
        return n1 - n2;
    }
}

class Mult implements Operator {
    @Override
    public String name() {
        return "*";
    }

    @Override
    public double apply( double n1, double n2 ) {
        return n1 * n2;
    }
}

class Div implements Operator {
    @Override
    public String name() {
        return "/";
    }

    @Override
    public double apply( double n1, double n2 ) {
        return n1 / n2;
    }
}
