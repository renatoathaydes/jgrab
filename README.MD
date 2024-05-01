# JGrab

[![Actions Status](https://github.com/renatoathaydes/jgrab/workflows/Build%20And%20Test%20on%20All%20OSs/badge.svg)](https://github.com/renatoathaydes/jgrab/actions)
[![Maven Central](https://img.shields.io/maven-central/v/com.athaydes.jgrab/jgrab-runner.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/com.athaydes.jgrab/jgrab-runner)

Runs Java code without a build system, grabbing dependencies declared in the Java file itself.

To make it fast to run Java code, JGrab employs a daemon which runs in the background, ready to run
code once it is started up.

It also uses an in-memory compiler, 
[osgiaas-javac](https://github.com/renatoathaydes/osgiaas/blob/master/docs/lang/osgiaas-javac.md), which is
based on the [JavaCompiler](https://docs.oracle.com/javase/7/docs/api/javax/tools/JavaCompiler.html) mechanism.

❇️ Go to the [Getting Started](#getting-started-with-jgrab) section for installation instructions.

## Example usage

Run a Java file or code snippet:

```shell
# Run Java class
▶ jgrab MyJavaCode.java

# Run expression
▶ jgrab -e 'java.lang.Math.pow(2, 3)'

# Run statement (must end with semicolon)
▶ jgrab -e 'System.out.println("Hello JGrab");'
```

The Java class must either have a main function or implement `Runnable`.

Maven dependencies can be declared in the Java file itself
using comments like `// #jgrab org:module:version`, as shown in this example:

```java
// #jgrab com.google.guava:guava:33.1.0-jre
package example;

import com.google.common.collect.ImmutableMap;

public class UsesGuava {
    public static void main(String[] args) {
        var items = ImmutableMap.of("coin", 3, "glass", 4, "pencil", 1);
        for (var fruit: items.entrySet()) {
            System.out.println(fruit);
        }
    }
}
```

Running the above for the first time should print the following:

```
▶ jgrab UsesGuava.java
=== JGrab Client - Starting daemon ===
=== JGrab Client - Daemon started, pid=78018 ===
=== JGrab Client - Connected! ===
coin=3
glass=4
pencil=1
```

It starts a deamon, downloads the necessary dependencies and then compiles and runs the Java file.

> All dependencies are downloaded to `~/.jgrab/jbuild-cache/`.

The next time you run a Java file with the same dependencies, it reuses the deamon and the cached dependencies:

```
▶ time jgrab UsesGuava.java
coin=3
glass=4
pencil=1
jgrab UsesGuava.java   0.00s  user 0.01s system 5% cpu 0.179 total
```

To stop the deamon, run:

```
# -s stops the deamon, -t starts it. Use -h for help.
▶ jgrab -s
=== JGrab Daemon stopped ===
```

## Goals of this project

- [x] to make it extremely easy and fast to run a single Java file or snippet.
- [x] to allow the Java file to use any dependency without a build system by
  declaring dependencies directly in the source ([JBuild](https://github.com/renatoathaydes/jbuild) is used internally to download deps).
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

To get JGrab, run the following command:

```
▶ curl https://raw.githubusercontent.com/renatoathaydes/jgrab/master/releases/install.sh -sSf | sh
```

This will download and unpack the JGrab archive into `$HOME/.jgrab/`.

> To change JGrab's home directory, set the `JGRAB_HOME` env var to another directory.

Alternatively, download JGrab from the [Releases](https://github.com/renatoathaydes/jgrab/releases) page.

Install it somewhere in your `PATH` by linking it, for example:

```shell
▶ sudo ln -s ~/.jgrab/jgrab-client /usr/local/bin/jgrab
```

Make sure it's working:

```
▶ jgrab -e '2 + 2'
=== JGrab Client - Starting daemon ===
=== JGrab Client - Created JGrab jar at: /Users/renato/.jgrab/jgrab.jar ===
=== JGrab Client - Daemon started, pid=79341 ===
=== JGrab Client - Connected! ===
4
```

The daemon starts first time you run some code, or when you run `jgrab -t`.

While JGrab's daemon is running, it can run Java code much faster.

To stop the daemon, run `jgrab -s`.

### Uninstalling

To uninstall JGrab, delete its home directory:

```shell
▶ rm -rf ~/.jgrab
```

Also delete any symlinks or shell aliases you may have created.

### Running JGrab with just `java`

If you don't care too much about speed, or you have trouble using the daemon (e.g. you need access to `stdin`),
you can run JGrab directly with `java`:

```
▶ java -jar ~/.jgrab/jgrab.jar --help
```

> If the jar doesn't exist, run any Java code with jgrab first. That will create the JGrab jar
> at `~/.jgrab/jgrab.jar` (it is extracted from the executable itself).

If your shell supports aliases, add an alias like the following, so that you can 
just type `jgrab <args>` to run JGrab, similarly to the jgrab-client:

```
▶ alias jgrab='java -jar $HOME/.jgrab/jgrab.jar $@'
```

Now, this should work:

```
▶ jgrab -e 'System.out.println("Hello world!");'
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
▶ jgrab Hello.java
Hello JGrab
```

### Running Java snippets

JGrab can also run simple Java code snippets using the `-e` option:

```
# expressions (anything that returns a value)
# must not be terminated with ';'
▶ jgrab -e 2 + 2
4
# statements MUST be terminated with ';'
▶ jgrab -e 'System.out.println("Hello JGrab");'
Hello JGrab
```

> Hint: always use single-quotes around code snippets to stop the shell from interpreting double-quotes.

### Piping Java code as input

JGrab reads from stdin if not given any arguments.

This allows piping to work seamlessly:

```
▶ cat Hello.java | jgrab
Hello JGrab
```

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
so it may take a few seconds.

However, it will run very fast after that! 

## Debugging

### Enabling JGrab Logging

To enable JGrab logging, start the Java daemon using the following command:

```
▶ java -Dorg.slf4j.simpleLogger.defaultLogLevel=debug -jar ~/.jgrab/jgrab.jar -d
```

From another shell, just use JGrab normally. The daemon process will log pretty much everything it does.

For even more information, use the `trace` level instead of `debug`.

### Debugging Java classes run by JGrab

Just start the JGrab daemon with the Java debugger enabled, then attach to it via your favourite IDE with the
sources you will run added to the IDE's build path.

You can start the JGrab daemon with the Java debugger enabled on port 5005 with the following command: 

```
▶ java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -jar ~/.jgrab/jgrab.jar --daemon
```

To attach the debugger to this process from IntelliJ:
 
 * right-click on the folder containing your source code, then select `Mark directory as > Source Roots`.
 * select `Run > Attach to Local Process...` from the top menu, and select the JGrab daemon process.
 * from a shell, run your Java file containing breakpoints using JGrab.
 
The IntelliJ debugger should stop on all breakpoints marked in the Java file you ran.

## Building

Run the following command to build and test both the Java runner and the Rust client:

```shell
./gradlew build
```

To build only the Java code:

```shell
./gradlew fatJar
```

To build only the Rust client (requires the Java code to be built first):

```shell
cd jgrab-client
cargo build
```

Requirements:

* Java 11+
* Cargo
