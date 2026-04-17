package com.boundaryml.baml

/**
 * Platform-agnostic callback for result/error delivery from the Rust engine.
 * Content is already converted to ByteArray (the JNA or JNI layer handles raw pointer conversion).
 */
interface NativeResultCallback {
    fun invoke(id: Int, isDone: Int, content: ByteArray?)
}

/**
 * Platform-agnostic callback for on-tick (streaming progress) from the Rust engine.
 */
interface NativeOnTickCallback {
    fun invoke(id: Int)
}

/**
 * Platform-agnostic interface for all BAML native library operations.
 * Implementations exist for JNA (desktop JVM) and JNI (Android).
 */
interface NativeBamlLib {
    fun version(): ByteArray
    fun createBamlRuntime(rootPath: String, srcFilesJson: String): Long
    fun destroyBamlRuntime(runtime: Long)
    fun registerCallbacks(
        resultCallback: NativeResultCallback,
        errorCallback: NativeResultCallback,
        onTickCallback: NativeOnTickCallback
    )
    fun callFunctionFromC(runtime: Long, functionName: String, encodedArgs: ByteArray, id: Int): ByteArray?
    fun callFunctionStreamFromC(runtime: Long, functionName: String, encodedArgs: ByteArray, id: Int): ByteArray?
    fun callFunctionParseFromC(runtime: Long, functionName: String, encodedArgs: ByteArray, id: Int): ByteArray?
    fun cancelFunctionCall(id: Int): ByteArray?
    fun cloneHandle(key: Long): Long
    fun releaseHandle(key: Long)
}
