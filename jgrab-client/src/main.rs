extern crate dirs;
extern crate wait_timeout;

use std::env;
use std::fs::{create_dir_all, File, read_to_string};
use std::io::{Cursor, Error, Read, Result, stdin, Stdin, stdout, Write};
use std::iter::Iterator;
use std::net::{Shutdown, TcpStream};
use std::option::Option;
use std::path::{Path, PathBuf};
use std::process::{Child, Command, exit};
use std::str;
use std::thread::sleep;
use std::time::Duration;

use dirs::home_dir;
use wait_timeout::ChildExt;

use Input::*;

const MAX_RETRIES: usize = 5;

const VERSION: &str = env!("CARGO_PKG_VERSION");

const JGRAB_INFO: &str = "\
=============== JGrab Client ================
 - https://github.com/renatoathaydes/jgrab -
=============================================
Jgrab can execute Java code from stdin (if not given any argument),
a Java file, or a Java snippet.

This is the native JGrab Client, written in Rust!

A Java daemon is started the first time the JGrab Client is run so
that subsequent runs are much faster.";

const JGRAB_USAGE: &str = "\
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
    Shows version information.";

/// All possible sources of input for the JGrab Client
enum Input {
    FileIn(File),
    StdinIn(Stdin),
    TextIn(Cursor<String>),
    Copy(Box<dyn Read>),
}

impl Read for Input {
    fn read(&mut self, buf: &mut [u8]) -> Result<usize> {
        match *self {
            FileIn(ref mut r) => r.read(buf),
            StdinIn(ref mut r) => r.read(buf),
            TextIn(ref mut r) => r.read(buf),
            Copy(ref mut r) => r.read(buf),
        }
    }
}

fn main() {
    let args: Vec<String> = env::args().skip(1).collect();

    let jgrab_home = find_jgrab_home();
    let token_path = token_path(&jgrab_home);
    let jar_path = jar_path(&jgrab_home);

    let input: Input;
    let mut ignore_read_error = false;

    if args.is_empty() {
        // no args, pipe stdin
        input = StdinIn(stdin());
    } else if args.len() == 1 && !args[0].starts_with('-') {
        // there's one argument and it is not an option, so it must be a file
        input = file_input(&args[0]);
    } else if args.len() == 1 {
        // one argument starting with -
        match args[0].trim() {
            "--help" | "-h" => {
                println!("{}\n\n{}", JGRAB_INFO, JGRAB_USAGE);
                return;
            }
            "--start" | "-t" => {
                input = TextIn(Cursor::new("-e null".to_string()))
            }
            "--stop" | "-s" => {
                if connect().is_err() {
                    log("daemon is not running");
                    return;
                }
                ignore_read_error = true;
                input = TextIn(Cursor::new("--stop".to_string()))
            }
            "--version" | "-v" => {
                println!("JGrab Client Version: {}", VERSION);
                show_daemon_version(&token_path);
                return;
            }
            "-e" => usage_error("-e option missing code snippet"),
            _ => {
                usage_error("invalid option");
            }
        }
    } else {
        // more than one argument given
        match args[0].trim() {
            "-e" => {
                // just pass on the arguments to JGrab
                input = create_message(&args)
            }
            _ => {
                // assume it's a file + java arguments
                let file = file_input(&args[0]);
                let java_args = create_wrapped_message("[", &args[1..], "]\n");
                input = Copy(Box::new(java_args.chain(file)))
            }
        }
    }

    send_message_retrying(input, &token_path, &jar_path, ignore_read_error);
}

fn file_input(file_name: &String) -> Input {
    match File::open(file_name) {
        Ok(file) => FileIn(file),
        Err(err) => error(&format!("unable to read file: {}", err)),
    }
}

fn create_message(args: &[String]) -> Input {
    TextIn(Cursor::new(args.join(" ")))
}

fn create_wrapped_message(prefix: &str, args: &[String], suffix: &str) -> Input {
    TextIn(Cursor::new(prefix.to_string() + &args.join(" ") + suffix))
}

fn token_path(home: &Path) -> PathBuf {
    let mut path = home.to_path_buf();
    path.push("token");
    path
}

fn jar_path(home: &Path) -> PathBuf {
    let mut path = home.to_path_buf();
    path.push("jgrab.jar");
    path
}

fn send_message_retrying<R: Read>(
    mut reader: R,
    token_path: &Path,
    jar_path: &Path,
    ignore_read_error: bool) {
    if send_message(&mut reader, false, token_path, ignore_read_error).is_some() {
        // failed to connect, try to start the daemon, then retry
        let mut retries = MAX_RETRIES;

        let mut child = start_daemon(jar_path);
        check_status(&mut child);

        while retries > 0 {
            if let Some(err) = send_message(&mut reader, true, token_path, ignore_read_error) {
                check_status(&mut child);

                log(&format!("unable to connect to JGrab daemon: {}", err));
                log(&format!("will re-try {} more times", &mut retries));
                retries -= 1;
            } else {
                break; // success
            }
        }

        if retries == 0 {
            error(
                "unable to start JGrab daemon. \
                   Make sure JGrab's port [5002] is not already bound",
            );
        }
    }
}

fn connect() -> Result<TcpStream> {
    TcpStream::connect("127.0.0.1:5002")
}

fn send_message<R: Read>(
    reader: &mut R,
    is_retry: bool,
    token_path: &Path,
    ignore_read_error: bool) -> Option<Error> {
    match connect() {
        Ok(mut stream) => {
            if is_retry {
                log("Connected!");
            }

            match read_to_string(token_path) {
                Ok(token) => {
                    stream.write_all(token.as_bytes()).expect("socket write error (token)");
                    stream.write_all(&[b'\n']).expect("socket write error (newline)");
                }
                Err(err) => return Some(err),
            };

            let mut socket_message = [0u8; 4096];

            loop {
                match reader.read(&mut socket_message) {
                    Ok(n) => {
                        if n == 0 {
                            break;
                        } else {
                            stream.write_all(&socket_message[0..n])
                                .expect("socket write error (message)");
                        }
                    }
                    Err(err) => error(&err.to_string()),
                }
            }

            stream.shutdown(Shutdown::Write).expect("shutdown socket write");

            let mut client_buffer = socket_message;

            let stdout = stdout();
            let mut lock = stdout.lock();

            loop {
                match stream.read(&mut client_buffer) {
                    Ok(n) => {
                        if n == 0 {
                            break;
                        } else {
                            lock.write_all(&client_buffer[0..n]).expect("stdout write error");
                        }
                    }
                    Err(_) if ignore_read_error => break,
                    Err(err) => error(&err.to_string()),
                }
            }

            None
        }
        Err(err) => Some(err),
    }
}

fn find_jgrab_home() -> PathBuf {
    let jgrab_home = env::var("JGRAB_HOME");
    if let Ok(home) = jgrab_home {
        home.into()
    } else {
        let mut path = home_dir()
            .unwrap_or_else(|| env::current_dir().expect("must be able to access current dir!"));
        path.push(".jgrab");
        path
    }
}

fn start_daemon(jgrab_jar: &Path) -> Child {
    log("Starting daemon");
    if !jgrab_jar.is_file() {
        create_jgrab_jar(jgrab_jar);
    }
    let cmd = Command::new("java")
        .arg("-jar")
        .arg(jgrab_jar)
        .arg("--daemon")
        .spawn();

    match cmd {
        Ok(child) => {
            log(&format!("Daemon started, pid={}", child.id()));
            child
        }
        Err(err) => error(&err.to_string()),
    }
}

fn create_jgrab_jar(path: &Path) {
    if let Some(dir) = path.parent() {
        let _ = create_dir_all(dir);
    }
    let buf = include_bytes!("../../jgrab-runner/build/libs/jgrab.jar");
    match File::create(path) {
        Ok(mut jar) => match jar.write_all(buf) {
            Ok(_) => log(&format!("Created JGrab jar at: {}", path.display())),
            Err(err) => error(&format!("Cannot write to jgrab jar path: {}", err)),
        },
        Err(err) => error(&format!("Cannot create jgrab jar: {}", err)),
    }
}

fn check_status(child: &mut Child) {
    let timeout = Duration::from_secs(1);

    match child.wait_timeout(timeout) {
        Ok(Some(status)) => error(&format!(
            "The JGrab daemon has died prematurely, {}",
            status
        )),
        Ok(None) => {
            // give time for the socket server to become active
            sleep(Duration::from_secs(1));
        }
        Err(e) => error(&format!(
            "unable to wait for JGrab daemon process status: {}",
            e
        )),
    }
}

fn show_daemon_version(token_path: &Path) {
    if send_message(
        &mut TextIn(Cursor::new("--version".to_string())),
        false,
        token_path,
        false,
    ).is_some() {
        println!("(Run the JGrab daemon to see its version)");
    }
}

fn log(message: &str) {
    println!("=== JGrab Client - {} ===", message)
}

fn usage_error(message: &str) -> ! {
    println!("### {} ###\n\n{}", message, JGRAB_USAGE);
    exit(2)
}

fn error(message: &str) -> ! {
    println!("### JGrab Client Error - {} ###", message);
    exit(1)
}
