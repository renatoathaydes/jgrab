use std::net::TcpStream;
use std::io::stdout;
use std::io::Read;
use std::io::Write;
use std::env;
use std::str;
use std::process::Command;
use std::path::PathBuf;
use std::thread::sleep;
use std::time::Duration;

fn main() {
    let max_retries: usize = 3;

    let args: Vec<String> = env::args().collect();
    let message = create_message(&args);

    send_message(message.as_bytes(), max_retries);
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

fn send_message(socket_message: &[u8], max_retries: usize) {
    if let Ok(mut stream) = TcpStream::connect("127.0.0.1:5002") {
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
    } else if max_retries > 0 {
        start_daemon();
        send_message(socket_message, max_retries - 1);
    } else {
        error("unable to start JGrab daemon. \
               Make sure JGrab's port [5002] is not already bound")
    }
}

fn start_daemon() {
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
                Ok(process) => {
                    log(&format!("Daemon has been started, pid={}", process.id()));
                    sleep(Duration::from_millis(500))
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

fn log(message: &str) {
    println!("==== JGrab Client ==== {}", message)
}

fn error(message: &str) -> ! {
    panic!("JGrab Client Error - {}", message)
}
