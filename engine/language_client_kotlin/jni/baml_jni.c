/**
 * baml_jni.c — Thin JNI bridge between Android/Kotlin and the Rust bridge_cffi C API.
 *
 * This file is compiled into libbaml_jni.so and linked against libbridge_cffi.so.
 * It translates JNI calls to the plain C functions exported by bridge_cffi,
 * and routes Rust callbacks back into Kotlin via stored global references.
 *
 * Build: see the accompanying CMakeLists.txt
 */

#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

/* ------------------------------------------------------------------ */
/*  bridge_cffi C API declarations (from Rust #[no_mangle] exports)   */
/* ------------------------------------------------------------------ */

typedef struct {
    const char *ptr;
    size_t len;
} Buffer;

typedef void (*CallbackFn)(uint32_t call_id, int32_t is_done, const int8_t *content, size_t length);
typedef void (*OnTickCallbackFn)(uint32_t call_id);

extern Buffer version(void);
extern void *create_baml_runtime(const char *root_path, const char *src_files_json, const char *env_vars_json);
extern void destroy_baml_runtime(void *runtime);
extern Buffer call_object_constructor(const char *encoded_args, size_t length);
extern void register_callbacks(CallbackFn result_cb, CallbackFn error_cb, OnTickCallbackFn on_tick_cb);
extern Buffer call_function_from_c(void *runtime, const char *function_name,
                                   const char *encoded_args, size_t length, uint32_t id);
extern Buffer call_function_stream_from_c(void *runtime, const char *function_name,
                                          const char *encoded_args, size_t length, uint32_t id);
extern Buffer call_function_parse_from_c(void *runtime, const char *function_name,
                                         const char *encoded_args, size_t length, uint32_t id);
extern Buffer cancel_function_call(uint32_t id);
extern uint64_t clone_handle(uint64_t key);
extern void release_handle(uint64_t key);
extern void free_buffer(Buffer buf);

/* ------------------------------------------------------------------ */
/*  Global state for callback routing                                  */
/* ------------------------------------------------------------------ */

static JavaVM *g_jvm = NULL;

/* Global refs to the Kotlin callback objects */
static jobject g_result_callback  = NULL;
static jobject g_error_callback   = NULL;
static jobject g_on_tick_callback = NULL;

/* Cached method IDs */
static jmethodID g_result_invoke  = NULL;
static jmethodID g_error_invoke   = NULL;
static jmethodID g_on_tick_invoke = NULL;

/* ------------------------------------------------------------------ */
/*  JNI_OnLoad — called when System.loadLibrary("baml_jni") runs      */
/* ------------------------------------------------------------------ */

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)reserved;
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

/* ------------------------------------------------------------------ */
/*  Helper: get JNIEnv, attaching the thread if needed                 */
/* ------------------------------------------------------------------ */

static JNIEnv *get_env(int *did_attach) {
    JNIEnv *env = NULL;
    *did_attach = 0;
    jint status = (*g_jvm)->GetEnv(g_jvm, (void **)&env, JNI_VERSION_1_6);
    if (status == JNI_EDETACHED) {
        (*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL);
        *did_attach = 1;
    }
    return env;
}

static void maybe_detach(int did_attach) {
    if (did_attach) {
        (*g_jvm)->DetachCurrentThread(g_jvm);
    }
}

/* ------------------------------------------------------------------ */
/*  Helper: convert Buffer → jbyteArray (and free the Buffer)          */
/* ------------------------------------------------------------------ */

static jbyteArray buffer_to_jbytearray(JNIEnv *env, Buffer buf) {
    if (buf.ptr == NULL || buf.len == 0) {
        free_buffer(buf);
        return NULL;
    }
    jbyteArray arr = (*env)->NewByteArray(env, (jsize)buf.len);
    if (arr != NULL) {
        (*env)->SetByteArrayRegion(env, arr, 0, (jsize)buf.len, (const jbyte *)buf.ptr);
    }
    free_buffer(buf);
    return arr;
}

/* ------------------------------------------------------------------ */
/*  C callback trampolines — called from Rust tokio threads            */
/* ------------------------------------------------------------------ */

static void result_trampoline(uint32_t call_id, int32_t is_done, const int8_t *content, size_t length) {
    if (g_result_callback == NULL || g_result_invoke == NULL) return;

    int did_attach = 0;
    JNIEnv *env = get_env(&did_attach);
    if (env == NULL) return;

    jbyteArray arr = NULL;
    if (content != NULL && length > 0) {
        arr = (*env)->NewByteArray(env, (jsize)length);
        if (arr != NULL) {
            (*env)->SetByteArrayRegion(env, arr, 0, (jsize)length, (const jbyte *)content);
        }
    }

    (*env)->CallVoidMethod(env, g_result_callback, g_result_invoke,
                           (jint)call_id, (jint)is_done, arr);

    if (arr != NULL) (*env)->DeleteLocalRef(env, arr);
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);

    maybe_detach(did_attach);
}

static void error_trampoline(uint32_t call_id, int32_t is_done, const int8_t *content, size_t length) {
    if (g_error_callback == NULL || g_error_invoke == NULL) return;

    int did_attach = 0;
    JNIEnv *env = get_env(&did_attach);
    if (env == NULL) return;

    jbyteArray arr = NULL;
    if (content != NULL && length > 0) {
        arr = (*env)->NewByteArray(env, (jsize)length);
        if (arr != NULL) {
            (*env)->SetByteArrayRegion(env, arr, 0, (jsize)length, (const jbyte *)content);
        }
    }

    (*env)->CallVoidMethod(env, g_error_callback, g_error_invoke,
                           (jint)call_id, (jint)is_done, arr);

    if (arr != NULL) (*env)->DeleteLocalRef(env, arr);
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);

    maybe_detach(did_attach);
}

static void on_tick_trampoline(uint32_t call_id) {
    if (g_on_tick_callback == NULL || g_on_tick_invoke == NULL) return;

    int did_attach = 0;
    JNIEnv *env = get_env(&did_attach);
    if (env == NULL) return;

    (*env)->CallVoidMethod(env, g_on_tick_callback, g_on_tick_invoke, (jint)call_id);

    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);

    maybe_detach(did_attach);
}

/* ------------------------------------------------------------------ */
/*  JNI method implementations                                         */
/*  Class: com.boundaryml.baml.JniBamlLib (Kotlin companion → static) */
/* ------------------------------------------------------------------ */

/*
 * Kotlin companion object @JvmStatic methods compile to static methods
 * on the enclosing class. JNI name: Java_com_boundaryml_baml_JniBamlLib_<name>
 */

JNIEXPORT jbyteArray JNICALL
Java_com_boundaryml_baml_JniBamlLib_nativeVersion(JNIEnv *env, jclass cls) {
    (void)cls;
    Buffer buf = version();
    return buffer_to_jbytearray(env, buf);
}

JNIEXPORT jlong JNICALL
Java_com_boundaryml_baml_JniBamlLib_nativeCreateBamlRuntime(JNIEnv *env, jclass cls,
                                                             jstring rootPath, jstring srcFilesJson, jstring envVarsJson) {
    (void)cls;
    const char *root = (*env)->GetStringUTFChars(env, rootPath, NULL);
    const char *src  = (*env)->GetStringUTFChars(env, srcFilesJson, NULL);
    const char *env_vars = (*env)->GetStringUTFChars(env, envVarsJson, NULL);

    void *ptr = create_baml_runtime(root, src, env_vars);

    (*env)->ReleaseStringUTFChars(env, rootPath, root);
    (*env)->ReleaseStringUTFChars(env, srcFilesJson, src);
    (*env)->ReleaseStringUTFChars(env, envVarsJson, env_vars);

    return (jlong)(intptr_t)ptr;
}

JNIEXPORT void JNICALL
Java_com_boundaryml_baml_JniBamlLib_nativeDestroyBamlRuntime(JNIEnv *env, jclass cls, jlong runtime) {
    (void)env; (void)cls;
    destroy_baml_runtime((void *)(intptr_t)runtime);
}

JNIEXPORT jbyteArray JNICALL
Java_com_boundaryml_baml_JniBamlLib_nativeCallObjectConstructor(JNIEnv *env, jclass cls,
                                                                 jbyteArray encodedArgs) {
    (void)cls;
    jsize len = (*env)->GetArrayLength(env, encodedArgs);
    jbyte *args = (*env)->GetByteArrayElements(env, encodedArgs, NULL);

    Buffer buf = call_object_constructor((const char *)args, (size_t)len);

    (*env)->ReleaseByteArrayElements(env, encodedArgs, args, JNI_ABORT);

    return buffer_to_jbytearray(env, buf);
}

JNIEXPORT void JNICALL
Java_com_boundaryml_baml_JniBamlLib_nativeRegisterCallbacks(JNIEnv *env, jclass cls,
                                                             jobject resultCb, jobject errorCb, jobject onTickCb) {
    (void)cls;

    /* Release any previous global refs */
    if (g_result_callback)  (*env)->DeleteGlobalRef(env, g_result_callback);
    if (g_error_callback)   (*env)->DeleteGlobalRef(env, g_error_callback);
    if (g_on_tick_callback) (*env)->DeleteGlobalRef(env, g_on_tick_callback);

    /* Create new global refs */
    g_result_callback  = (*env)->NewGlobalRef(env, resultCb);
    g_error_callback   = (*env)->NewGlobalRef(env, errorCb);
    g_on_tick_callback = (*env)->NewGlobalRef(env, onTickCb);

    /* Cache method IDs: NativeResultCallback.invoke(int, int, byte[]) */
    jclass resultClass = (*env)->GetObjectClass(env, resultCb);
    g_result_invoke = (*env)->GetMethodID(env, resultClass, "invoke", "(II[B)V");
    (*env)->DeleteLocalRef(env, resultClass);

    jclass errorClass = (*env)->GetObjectClass(env, errorCb);
    g_error_invoke = (*env)->GetMethodID(env, errorClass, "invoke", "(II[B)V");
    (*env)->DeleteLocalRef(env, errorClass);

    jclass tickClass = (*env)->GetObjectClass(env, onTickCb);
    g_on_tick_invoke = (*env)->GetMethodID(env, tickClass, "invoke", "(I)V");
    (*env)->DeleteLocalRef(env, tickClass);

    /* Register C trampolines with Rust */
    register_callbacks(result_trampoline, error_trampoline, on_tick_trampoline);
}

JNIEXPORT jbyteArray JNICALL
Java_com_boundaryml_baml_JniBamlLib_nativeCallFunctionFromC(JNIEnv *env, jclass cls,
                                                             jlong runtime, jstring functionName,
                                                             jbyteArray encodedArgs, jint id) {
    (void)cls;
    const char *name = (*env)->GetStringUTFChars(env, functionName, NULL);
    jsize len = (*env)->GetArrayLength(env, encodedArgs);
    jbyte *args = (*env)->GetByteArrayElements(env, encodedArgs, NULL);

    Buffer buf = call_function_from_c((void *)(intptr_t)runtime, name, (const char *)args, (size_t)len, (uint32_t)id);

    (*env)->ReleaseByteArrayElements(env, encodedArgs, args, JNI_ABORT);
    (*env)->ReleaseStringUTFChars(env, functionName, name);

    return buffer_to_jbytearray(env, buf);
}

JNIEXPORT jbyteArray JNICALL
Java_com_boundaryml_baml_JniBamlLib_nativeCallFunctionStreamFromC(JNIEnv *env, jclass cls,
                                                                   jlong runtime, jstring functionName,
                                                                   jbyteArray encodedArgs, jint id) {
    (void)cls;
    const char *name = (*env)->GetStringUTFChars(env, functionName, NULL);
    jsize len = (*env)->GetArrayLength(env, encodedArgs);
    jbyte *args = (*env)->GetByteArrayElements(env, encodedArgs, NULL);

    Buffer buf = call_function_stream_from_c((void *)(intptr_t)runtime, name, (const char *)args, (size_t)len, (uint32_t)id);

    (*env)->ReleaseByteArrayElements(env, encodedArgs, args, JNI_ABORT);
    (*env)->ReleaseStringUTFChars(env, functionName, name);

    return buffer_to_jbytearray(env, buf);
}

JNIEXPORT jbyteArray JNICALL
Java_com_boundaryml_baml_JniBamlLib_nativeCallFunctionParseFromC(JNIEnv *env, jclass cls,
                                                                  jlong runtime, jstring functionName,
                                                                  jbyteArray encodedArgs, jint id) {
    (void)cls;
    const char *name = (*env)->GetStringUTFChars(env, functionName, NULL);
    jsize len = (*env)->GetArrayLength(env, encodedArgs);
    jbyte *args = (*env)->GetByteArrayElements(env, encodedArgs, NULL);

    Buffer buf = call_function_parse_from_c((void *)(intptr_t)runtime, name, (const char *)args, (size_t)len, (uint32_t)id);

    (*env)->ReleaseByteArrayElements(env, encodedArgs, args, JNI_ABORT);
    (*env)->ReleaseStringUTFChars(env, functionName, name);

    return buffer_to_jbytearray(env, buf);
}

JNIEXPORT jbyteArray JNICALL
Java_com_boundaryml_baml_JniBamlLib_nativeCancelFunctionCall(JNIEnv *env, jclass cls, jint id) {
    (void)cls;
    Buffer buf = cancel_function_call((uint32_t)id);
    return buffer_to_jbytearray(env, buf);
}

JNIEXPORT jlong JNICALL
Java_com_boundaryml_baml_JniBamlLib_nativeCloneHandle(JNIEnv *env, jclass cls, jlong key) {
    (void)env; (void)cls;
    return (jlong)clone_handle((uint64_t)key);
}

JNIEXPORT void JNICALL
Java_com_boundaryml_baml_JniBamlLib_nativeReleaseHandle(JNIEnv *env, jclass cls, jlong key) {
    (void)env; (void)cls;
    release_handle((uint64_t)key);
}
