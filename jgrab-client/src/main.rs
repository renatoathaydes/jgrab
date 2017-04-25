use std::net::TcpStream;
use std::io::stdout;
use std::io::Read;
use std::io::Write;
use std::env;
use std::str;

fn main() {
    if let Ok(mut stream) = TcpStream::connect("127.0.0.1:5002") {
        let args: Vec<String> = env::args().collect();
        let all_args: String = args[1..].join(" ") + "\n";
        stream.write(all_args.as_bytes()).unwrap();

        let mut client_buffer = [0u8; 1024];

        loop {
            match stream.read(&mut client_buffer) {
                Ok(n) => {
                    if n == 0 {
                        break;
                    } else {
                        stdout().write(&client_buffer).unwrap();
                    }
                }
                Err(error) => panic!(error.to_string()),
            }
        }
    } else {
        println!("JGrab Error - couldn't connect to JGrab process...");
    }
}
