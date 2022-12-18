extern crate core;

pub mod protoc;
pub mod server;
pub mod tcp;

pub mod built_info {
  include!(concat!(env!("OUT_DIR"), "/built.rs"));
}
