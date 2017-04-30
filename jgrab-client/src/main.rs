use std::net::TcpStream;
use std::io::stdout;
use std::io::Read;
use std::io::Write;
use std::env;
use std::str;
use std::option::Option;
use std::process::Command;
use std::process::Child;
use std::path::PathBuf;
use std::thread::sleep;
use std::time::Duration;
use wait_timeout::ChildExt;
use std::io::Error;

extern crate wait_timeout;

const MAX_RETRIES: usize = 5;

fn main() {
    let args: Vec<String> = env::args().collect();
    let message = create_message(&args);

    send_message_retrying(message.as_bytes());
}

fn create_message(args: &Vec<String>) -> String {
    let args_str = args[1..].join(" ");
    let curr_dir = env::current_dir().unwrap().into_os_string().into_string().unwrap();

    let mut message: String = String::with_capacity(args_str.len() + curr_dir.len() + 12);

    message.push_str(&curr_dir);
    message.push('\n');
    message.push_str(&args_str);
    message.push_str("\nJGRAB_END\n");

    message
}

fn send_message_retrying(message: &[u8]) {
    if let Some(_) = send_message(message, false) {
        // failed to connect, try to start the daemon, then retry
        let mut retries = MAX_RETRIES;

        let mut child = start_daemon();
        check_status(&mut child);

        while retries > 0 {
            if let Some(err) = send_message(message, true) {
                check_status(&mut child);

                log(&format!("unable to connect to JGrab daemon: {}", err));
                log(&format!("will re-try {} more times", &mut retries));
                retries -= 1;
            } else {
                break // success
            }
        }

        if retries == 0 {
            error("unable to start JGrab daemon. \
                   Make sure JGrab's port [5002] is not already bound");
        }
    }
}

fn send_message(socket_message: &[u8],
                is_retry: bool) -> Option<Error> {
    match TcpStream::connect("127.0.0.1:5002") {
        Ok(mut stream) => {
            if is_retry {
                log("Connected!");
            }

            stream.write(socket_message).unwrap();

            let mut client_buffer = [0u8; 1024];

            loop {
                match stream.read(&mut client_buffer) {
                    Ok(n) => {
                        if n == 0 {
                            break;
                        } else {
                            stdout().write(&client_buffer[0..n]).unwrap();
                        }
                    }
                    Err(err) => error(&err.to_string())
                }
            }

            Option::None
        }
        Err(err) => Option::Some(err)
    }
}

fn start_daemon() -> Child {
    log("Starting daemon");

    if let Some(user_home) = env::home_dir() {
        let mut jgrab_jar: PathBuf = user_home;
        jgrab_jar.push(".jgrab");
        jgrab_jar.push("jgrab.jar");

        if jgrab_jar.as_path().is_file() {
            let cmd = Command::new("java")
                .arg("-jar")
                .arg(jgrab_jar.into_os_string().into_string().unwrap())
                .arg("--daemon")
                .spawn();

            match cmd {
                Ok(child) => {
                    log(&format!("Daemon started, pid={}", child.id()));
                    child
                }
                Err(err) => error(&err.to_string())
            }
        } else {
            error("The JGrab jar is not installed! Please install it as explained \
                   in https://github.com/renatoathaydes/jgrab");
        }
    } else {
        error("user.home could not be found, making it impossible to start the JGrab Daemon");
    }
}

fn check_status(child: &mut Child) {
    let timeout = Duration::from_secs(1);

    match child.wait_timeout(timeout) {
        Ok(Some(status)) => error(&format!(
            "The JGrab daemon has died prematurely, exit code was {}", status)),
        Ok(None) => {
            // give time for the socket server to become active
            sleep(Duration::from_secs(1));
        }
        Err(e) => error(&format!(
            "Could not wait for JGrab daemon process status: {}", e)),
    }
}

fn log(message: &str) {
    println!("==== JGrab Client ==== {}", message)
}

fn error(message: &str) -> ! {
    panic!("JGrab Client Error - {}", message)
}
