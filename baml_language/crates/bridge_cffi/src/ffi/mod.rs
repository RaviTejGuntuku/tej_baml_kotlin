//! FFI module - C-compatible entry points.

pub mod callbacks;
pub mod functions;
pub mod handle;
#[cfg(target_os = "android")]
pub mod jni;
pub mod objects;
pub mod runtime;
