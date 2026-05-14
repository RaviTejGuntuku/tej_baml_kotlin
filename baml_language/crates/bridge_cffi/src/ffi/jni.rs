//! Android JNI entry points implemented directly in Rust.
//!
//! This replaces the previous app-local C shim. Android now loads
//! `libbridge_cffi.so` directly for both the plain C ABI and the JNI exports.

#![cfg(target_os = "android")]

use std::{ffi::CString, sync::{Mutex, OnceLock}};

use jni::{
    JNIEnv, JavaVM,
    objects::{GlobalRef, JByteArray, JObject, JString, JValue},
    sys::{
        JNI_VERSION_1_6, JavaVM as JavaVmRaw, jbyteArray, jint, jlong, jobject, jstring,
    },
};

use crate::{
    Buffer,
    ffi::{
        callbacks::register_callbacks,
        functions::{
            call_function_from_c, call_function_parse_from_c, call_function_stream_from_c,
            cancel_function_call,
        },
        handle::{clone_handle, release_handle},
        objects::free_buffer,
        runtime::{create_baml_runtime, destroy_baml_runtime, version},
    },
};

static JVM: OnceLock<JavaVM> = OnceLock::new();
static RESULT_CALLBACK: Mutex<Option<GlobalRef>> = Mutex::new(None);
static ERROR_CALLBACK: Mutex<Option<GlobalRef>> = Mutex::new(None);
static TICK_CALLBACK: Mutex<Option<GlobalRef>> = Mutex::new(None);

fn buffer_to_jbyte_array(env: &mut JNIEnv<'_>, buffer: Buffer) -> jbyteArray {
    if buffer.ptr.is_null() || buffer.is_empty() {
        free_buffer(buffer);
        return std::ptr::null_mut();
    }

    let bytes = unsafe { std::slice::from_raw_parts(buffer.ptr as *const u8, buffer.len) };
    let array = match env.byte_array_from_slice(bytes) {
        Ok(array) => array,
        Err(_) => {
            free_buffer(buffer);
            return std::ptr::null_mut();
        }
    };
    free_buffer(buffer);
    array.into_raw()
}

fn invoke_callback(callback_ref: &Mutex<Option<GlobalRef>>, call_id: u32, is_done: i32, content: *const i8, length: usize) {
    let Some(jvm) = JVM.get() else {
        return;
    };
    let Some(callback) = callback_ref.lock().ok().and_then(|guard| guard.clone()) else {
        return;
    };

    let Ok(mut env) = jvm.attach_current_thread() else {
        return;
    };

    let payload = if !content.is_null() && length > 0 {
        let bytes = unsafe { std::slice::from_raw_parts(content as *const u8, length) };
        match env.byte_array_from_slice(bytes) {
            Ok(array) => JObject::from(array),
            Err(_) => JObject::null(),
        }
    } else {
        JObject::null()
    };

    let _ = env.call_method(
        callback.as_obj(),
        "invoke",
        "(II[B)V",
        &[
            JValue::Int(call_id as jint),
            JValue::Int(is_done as jint),
            JValue::Object(&payload),
        ],
    );

    if env.exception_check().unwrap_or(false) {
        let _ = env.exception_clear();
    }
}

extern "C" fn result_trampoline(call_id: u32, is_done: i32, content: *const i8, length: usize) {
    invoke_callback(&RESULT_CALLBACK, call_id, is_done, content, length);
}

extern "C" fn error_trampoline(call_id: u32, is_done: i32, content: *const i8, length: usize) {
    invoke_callback(&ERROR_CALLBACK, call_id, is_done, content, length);
}

extern "C" fn on_tick_trampoline(call_id: u32) {
    let Some(jvm) = JVM.get() else {
        return;
    };
    let Some(callback) = TICK_CALLBACK.lock().ok().and_then(|guard| guard.clone()) else {
        return;
    };
    let Ok(mut env) = jvm.attach_current_thread() else {
        return;
    };

    let _ = env.call_method(
        callback.as_obj(),
        "invoke",
        "(I)V",
        &[JValue::Int(call_id as jint)],
    );

    if env.exception_check().unwrap_or(false) {
        let _ = env.exception_clear();
    }
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn JNI_OnLoad(vm: *mut JavaVmRaw, _reserved: *mut core::ffi::c_void) -> jint {
    if let Ok(java_vm) = unsafe { JavaVM::from_raw(vm) } {
        let _ = JVM.set(java_vm);
    }
    JNI_VERSION_1_6
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_boundaryml_baml_JniBamlLib_nativeVersion(
    env: *mut jni::sys::JNIEnv,
    _class: jni::sys::jclass,
) -> jbyteArray {
    let Ok(mut env) = (unsafe { JNIEnv::from_raw(env) }) else {
        return std::ptr::null_mut();
    };
    buffer_to_jbyte_array(&mut env, version())
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_boundaryml_baml_JniBamlLib_nativeCreateBamlRuntime(
    env: *mut jni::sys::JNIEnv,
    _class: jni::sys::jclass,
    root_path: jstring,
    src_files_json: jstring,
    env_vars_json: jstring,
) -> jlong {
    let Ok(mut env) = (unsafe { JNIEnv::from_raw(env) }) else {
        return 0;
    };

    let root_path = unsafe { JString::from_raw(root_path) };
    let Ok(root_path) = env.get_string(&root_path) else {
        return 0;
    };
    let src_files_json = unsafe { JString::from_raw(src_files_json) };
    let Ok(src_files_json) = env.get_string(&src_files_json) else {
        return 0;
    };
    let env_vars_json = unsafe { JString::from_raw(env_vars_json) };
    let Ok(env_vars_json) = env.get_string(&env_vars_json) else {
        return 0;
    };

    let Ok(root_path) = CString::new(root_path.to_string_lossy().into_owned()) else {
        return 0;
    };
    let Ok(src_files_json) = CString::new(src_files_json.to_string_lossy().into_owned()) else {
        return 0;
    };
    let Ok(env_vars_json) = CString::new(env_vars_json.to_string_lossy().into_owned()) else {
        return 0;
    };

    create_baml_runtime(
        root_path.as_ptr().cast(),
        src_files_json.as_ptr().cast(),
        env_vars_json.as_ptr().cast(),
    ) as jlong
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_boundaryml_baml_JniBamlLib_nativeDestroyBamlRuntime(
    _env: *mut jni::sys::JNIEnv,
    _class: jni::sys::jclass,
    runtime: jlong,
) {
    destroy_baml_runtime(runtime as *const libc::c_void);
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_boundaryml_baml_JniBamlLib_nativeRegisterCallbacks(
    env: *mut jni::sys::JNIEnv,
    _class: jni::sys::jclass,
    result_callback: jobject,
    error_callback: jobject,
    on_tick_callback: jobject,
) {
    let Ok(env) = (unsafe { JNIEnv::from_raw(env) }) else {
        return;
    };

    let Ok(result_ref) = env.new_global_ref(unsafe { JObject::from_raw(result_callback) }) else {
        return;
    };
    let Ok(error_ref) = env.new_global_ref(unsafe { JObject::from_raw(error_callback) }) else {
        return;
    };
    let Ok(tick_ref) = env.new_global_ref(unsafe { JObject::from_raw(on_tick_callback) }) else {
        return;
    };

    if let Ok(mut slot) = RESULT_CALLBACK.lock() {
        *slot = Some(result_ref);
    }
    if let Ok(mut slot) = ERROR_CALLBACK.lock() {
        *slot = Some(error_ref);
    }
    if let Ok(mut slot) = TICK_CALLBACK.lock() {
        *slot = Some(tick_ref);
    }

    register_callbacks(result_trampoline, error_trampoline, on_tick_trampoline);
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_boundaryml_baml_JniBamlLib_nativeCallFunctionFromC(
    env: *mut jni::sys::JNIEnv,
    _class: jni::sys::jclass,
    runtime: jlong,
    function_name: jstring,
    encoded_args: jbyteArray,
    id: jint,
) -> jbyteArray {
    let Ok(mut env) = (unsafe { JNIEnv::from_raw(env) }) else {
        return std::ptr::null_mut();
    };

    let function_name = unsafe { JString::from_raw(function_name) };
    let Ok(function_name) = env.get_string(&function_name) else {
        return std::ptr::null_mut();
    };
    let Ok(encoded_args) = env.convert_byte_array(unsafe { JByteArray::from_raw(encoded_args) }) else {
        return std::ptr::null_mut();
    };

    let Ok(function_name) = CString::new(function_name.to_string_lossy().into_owned()) else {
        return std::ptr::null_mut();
    };

    let buffer = call_function_from_c(
        runtime as *const libc::c_void,
        function_name.as_ptr().cast(),
        encoded_args.as_ptr().cast(),
        encoded_args.len(),
        id as u32,
    );
    buffer_to_jbyte_array(&mut env, buffer)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_boundaryml_baml_JniBamlLib_nativeCallFunctionStreamFromC(
    env: *mut jni::sys::JNIEnv,
    _class: jni::sys::jclass,
    runtime: jlong,
    function_name: jstring,
    encoded_args: jbyteArray,
    id: jint,
) -> jbyteArray {
    let Ok(mut env) = (unsafe { JNIEnv::from_raw(env) }) else {
        return std::ptr::null_mut();
    };

    let function_name = unsafe { JString::from_raw(function_name) };
    let Ok(function_name) = env.get_string(&function_name) else {
        return std::ptr::null_mut();
    };
    let Ok(encoded_args) = env.convert_byte_array(unsafe { JByteArray::from_raw(encoded_args) }) else {
        return std::ptr::null_mut();
    };

    let Ok(function_name) = CString::new(function_name.to_string_lossy().into_owned()) else {
        return std::ptr::null_mut();
    };

    let buffer = call_function_stream_from_c(
        runtime as *const libc::c_void,
        function_name.as_ptr().cast(),
        encoded_args.as_ptr().cast(),
        encoded_args.len(),
        id as u32,
    );
    buffer_to_jbyte_array(&mut env, buffer)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_boundaryml_baml_JniBamlLib_nativeCallFunctionParseFromC(
    env: *mut jni::sys::JNIEnv,
    _class: jni::sys::jclass,
    runtime: jlong,
    function_name: jstring,
    encoded_args: jbyteArray,
    id: jint,
) -> jbyteArray {
    let Ok(mut env) = (unsafe { JNIEnv::from_raw(env) }) else {
        return std::ptr::null_mut();
    };

    let function_name = unsafe { JString::from_raw(function_name) };
    let Ok(function_name) = env.get_string(&function_name) else {
        return std::ptr::null_mut();
    };
    let Ok(encoded_args) = env.convert_byte_array(unsafe { JByteArray::from_raw(encoded_args) }) else {
        return std::ptr::null_mut();
    };

    let Ok(function_name) = CString::new(function_name.to_string_lossy().into_owned()) else {
        return std::ptr::null_mut();
    };

    let buffer = call_function_parse_from_c(
        runtime as *const libc::c_void,
        function_name.as_ptr().cast(),
        encoded_args.as_ptr().cast(),
        encoded_args.len(),
        id as u32,
    );
    buffer_to_jbyte_array(&mut env, buffer)
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_boundaryml_baml_JniBamlLib_nativeCancelFunctionCall(
    env: *mut jni::sys::JNIEnv,
    _class: jni::sys::jclass,
    id: jint,
) -> jbyteArray {
    let Ok(mut env) = (unsafe { JNIEnv::from_raw(env) }) else {
        return std::ptr::null_mut();
    };
    buffer_to_jbyte_array(&mut env, cancel_function_call(id as u32))
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_boundaryml_baml_JniBamlLib_nativeCloneHandle(
    _env: *mut jni::sys::JNIEnv,
    _class: jni::sys::jclass,
    key: jlong,
) -> jlong {
    clone_handle(key as u64) as jlong
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_boundaryml_baml_JniBamlLib_nativeReleaseHandle(
    _env: *mut jni::sys::JNIEnv,
    _class: jni::sys::jclass,
    key: jlong,
) {
    release_handle(key as u64);
}
