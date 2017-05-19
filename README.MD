# JGrab

Runs Java code without a build system, grabbing dependencies declared in the Java file itself.

To make it extremely fast to run Java code, JGrab employs a daemon which runs in the background, ready to run
code once it is started up.

It also uses an in-memory compiler, 
[osgiaas-javac](https://github.com/renatoathaydes/osgiaas/blob/master/docs/lang/osgiaas-javac.md), which is
based on the [JavaCompiler](https://docs.oracle.com/javase/7/docs/api/javax/tools/JavaCompiler.html) mechanism.

## Goals of this project

- [x] to make it extremely easy and fast to run a single Java file or snippet.
- [x] to allow the Java file to use any dependency without a build system by 
  declaring dependencies directly in the source (Apache Ivy is used internally to download deps).
- [x] to provide a daemon that circumvents the JVM startup and warmup slowness. 
  This is why Rust is used for the jgrab-client.
- [x] to make downloading and installing JGrab a one-command process.  

> This project is inspired by the awesome Groovy 
  [@Grab](http://docs.groovy-lang.org/latest/html/documentation/grape.html) annotation.
  The Rust client is also inspired by efforts from the Groovy community such as 
  [GroovyServ](https://kobo.github.io/groovyserv/).

It is **NOT a goal** of this project:

* to become a full build system.
* to accept more than one Java file or snippet as input. That's what build systems are for.

## Getting Started with JGrab

Install JGrab:

```
> curl https://raw.githubusercontent.com/renatoathaydes/jgrab/master/releases/install.sh -sSf | sh
```

JGrab is installed in the `$HOME/.jgrab/` directory.

> If for some reason you don't want to use `curl` to download and install JGrab,
  just download the JGrab Client from [GitHub](https://github.com/renatoathaydes/jgrab/releases),
  and the Java jar from [Jcenter](http://jcenter.bintray.com/com/athaydes/jgrab/jgrab-runner/).

### Running the native, Rust client

Run the [Rust](https://www.rust-lang.org/) client with:

```
> ~/.jgrab/jgrab-client --help
```

To make it easier to run JGrab in Linux/Mac, for example, you can add a link to it as shown below:

```
> sudo ln -s $HOME/.jgrab/jgrab-client /usr/local/bin/jgrab
```

Now, you should be able to run the `jgrab` command from anywhere:

```
> jgrab -e 2 + 2
4
```

### Running JGrab with just `java`

If you don't care too much about speed, you can run JGrab directly with `java`, without a daemon
(it still runs small programs in the order of one or two hundred milliseconds in modern laptops).

```
> java -jar ~/.jgrab/jgrab.jar --help
```

If your shell supports aliases, add an alias like the following, so that you can 
just type `jgrab <args>` to run JGrab, similarly to the jgrab-client:

```
> alias jgrab='java -jar $HOME/.jgrab/jgrab.jar $@'
```

Now, this should work:

```
jgrab -e 'System.out.println("Hello world!");'
```

## General Usage

### JGrab Client

> Note: The Rust client currently expects the jgrab jar to be located at
  `$HOME/.jgrab/jgrab.jar`.
  See the previous section for information on how to install JGrab.

```
=============== JGrab Client ================
 - https://github.com/renatoathaydes/jgrab -
=============================================
Jgrab can execute Java code from stdin (if not given any argument),
a Java file, or a Java snippet.

This is the native JGrab Client, written in Rust!

A Java daemon is started the first time the JGrab Client is run so
that subsequent runs are much faster.

Usage:
  jgrab [<option> | java_file [java-args*] | -e java_snippet]
Options:
  --stop -s
    Stops the JGrab daemon.
  --start -t
    Starts the JGrab daemon (if not yet running).
  --help -h
    Shows usage.
  --version -v
    Shows version information.
```

### JGrab Jar

```
=================== JGrab ===================
 - https://github.com/renatoathaydes/jgrab -
=============================================
Jgrab can execute Java code from stdin (if not given any argument),
a Java file, or a Java snippet.

Usage:
  jgrab [<option> | java_file [java-args*] | -e java_snippet]
Options:
  --daemon -d
    Starts up the JGrab daemon (used by the jgrab-client).
  --help -h
    Shows usage.
  --version -v
    Shows version information.
```

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

```
# statements must not be terminated with ';'
> jgrab -e 2 + 2
4
# expressions must be terminated with ';'
> jgrab -e 'System.out.println("Hello JGrab");'
Hello JGrab
```

> Hint: always use single-quotes around code snippets to stop the shell from interpreting double-quotes.

### Declaring external dependencies

JGrab lets you declare external dependencies within Java files using a comment processor of the form 
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

## Debugging

To debug, start the Java daemon using the following command:

```
java -Dorg.slf4j.simpleLogger.defaultLogLevel=debug -jar ~/.jgrab/jgrab.jar -d
```

From another shell, just use JGrab normally. The daemon process will log pretty much everything it does.
