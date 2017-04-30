# JGrab

Runs Java code without a build system, grabbing dependencies declared in the Java file itself.

## Goals of this project

- [x] to make it extremely easy and fast to run a single Java file or snippet.
- [x] to allow the Java file to use any dependency without a build system by 
  declaring dependencies directly in the source (Apache Ivy is used internally to download deps).
- [x] to provide a daemon that circumvents the JVM startup and warmup slowness. 
  This is why Rust is used for the jgrab-client.
- [ ] to make downloading and installing JGrab a one-command process.  

> This project is inspired by the awesome Groovy 
  [@Grab](http://docs.groovy-lang.org/latest/html/documentation/grape.html) annotation.
  The Rust client is also inspired by efforts from the Groovy community such as 
  [GroovyServ](https://kobo.github.io/groovyserv/).

It is **NOT a goal** of this project:

* to become a full build system.
* to accept more than one Java file or snippet as input. That's what build systems are for.

## Running JGrab

To make it extremely fast to run Java code, JGrab employs a daemon which runs in the background, ready to run
code once it is started up.

It also uses an in-memory compiler, 
[osgiaas-javac](https://github.com/renatoathaydes/osgiaas/blob/master/docs/lang/osgiaas-javac.md), which is
based on the [JavaCompiler](https://docs.oracle.com/javase/7/docs/api/javax/tools/JavaCompiler.html) mechanism.

You can use the [Rust](https://www.rust-lang.org/) client to take care of the Java daemon automatically for you.

If you don't care too much about speed, you can run JGrab directly with `java`, without a daemon
(it still runs small programs in the order of one or two hundred milliseconds in modern laptops).

### Using the Rust client

Download the JGrab client's Rust binary:

```
curl https://raw.githubusercontent.com/renatoathaydes/jgrab/master/releases/jgrab-client-0.5 > jgrab
```

> Note: The Rust client currently expects the jgrab jar to be located at
  `${user.home}/.jgrab/jgrab.jar`. In the future, it should be able to download it 
  from Bintray automatically.

Run the jgrab client to execute your Java files and snippets:

```
jgrab -e 'System.out.println("Hello world!");'
```

> The first time the jgrab-client runs, it will start up the JGrab daemon before running the provided command.
  Further runs will re-use the daemon, so run much faster.

### Using JGrab's runnable jar

Get the JGrab jar from Bintray:

TBD

Run your Java file or snippet using JGrab's runnable jar:

```
java -jar jgrab.jar -e 'System.out.println("Hello world!");'
```

If your shell supports aliases, add an alias like the following, so that you can 
just type `jgrab <args>` to run JGrab, similarly to the jgrab-client:

```
alias jgrab='java -jar <full-path-to-jar>/jgrab.jar $@'
```

Now, this should work:

```
jgrab -e 'System.out.println("Hello world!");'
```

## General Usage

### Running Java classes

JGrab can run any class containing a standard main method (`public static void main(String[] args)`)
or that implements the `java.lang.Runnable` interface.

For example, create the following file with name `Hello.java`:

```java
public class Hello implements Runnable {
    public void run() {
        System.out.println("Hello JGrab");
    }
}
```

To run this file with JGrab, just pass the file name to it as an argument:

> The class name must match the file name, as with any valid public Java class.
  The package, however, does not matter, so any package can be declared regardless of the file location.

``` 
> jgrab Hello.java
Hello JGrab
```

### Running Java snippets

JGrab can also run simple Java code snippets using the `-e` option:

> Snippets passed with the `-e` option can be an expression, in which case it should 
  not be terminated with a semi-colon, or a statement, in which case, it should.

```
> jgrab -e '2 + 2'
4
> jgrab -e 'System.out.println("Hello JGrab");'
Hello JGrab
```

### Declaring external dependencies

JGrab lets you declare external dependencies using a comment processor of the form 
`// #jgrab groupId:artifactId[:version]`.
 
For example, you can create a Java class that requires Guava:

```java
// #jgrab com.google.guava:guava:19.0
import com.google.common.collect.ImmutableMap;

public class UsesGuava {

    public static void main(String[] args) {
        ImmutableMap<String, Integer> items = ImmutableMap.of(
                "one", 1, "two", 2, "three", 3);
        
        items.entrySet().stream().forEach(System.out::println);
    }
}
```

The first time you run this class, it will download Guava if necessary before compiling and running it,
so it may take a while.

However, it will run very fast after that! 


### Options

* `-d` `--deamon` starts the JGrab daemon.
* `-e` expects a Java snippet as an argument. Use single-quotes to encapsulate the code, as shown in the examples. 
* `--stop` stops the JGrab daemon.

