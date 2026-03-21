/// FourMessenger Rust native library
/// Provides native performance utilities and JNI bridge functions
/// for the Android WebView wrapper.

use std::ffi::{CStr, CString};

/// Called from Java via JNI to get the app version string
#[no_mangle]
pub extern "C" fn Java_io_github_fourmessenger_NativeLib_getVersion(
    _env: *mut std::ffi::c_void,
    _class: *mut std::ffi::c_void,
) -> *mut std::os::raw::c_char {
    let version = CString::new("1.0.0").unwrap();
    version.into_raw()
}

/// Called from Java via JNI to perform any native initialization
#[no_mangle]
pub extern "C" fn Java_io_github_fourmessenger_NativeLib_initialize(
    _env: *mut std::ffi::c_void,
    _class: *mut std::ffi::c_void,
) -> bool {
    // Native initialization logic can go here
    // e.g., setting up crypto, local storage encryption, etc.
    true
}

/// Called from Java to compute a hash (example native utility)
#[no_mangle]
pub extern "C" fn Java_io_github_fourmessenger_NativeLib_computeHash(
    _env: *mut std::ffi::c_void,
    _class: *mut std::ffi::c_void,
    input: *const std::os::raw::c_char,
) -> u64 {
    if input.is_null() {
        return 0;
    }
    let c_str = unsafe { CStr::from_ptr(input) };
    let bytes = c_str.to_bytes();
    // Simple FNV-1a hash
    let mut hash: u64 = 14695981039346656037;
    for &byte in bytes {
        hash ^= byte as u64;
        hash = hash.wrapping_mul(1099511628211);
    }
    hash
}
