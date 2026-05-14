package com.boundaryml.baml

/**
 * JNI-based implementation of [NativeBamlLib] for Android.
 *
 * Uses System.loadLibrary to load `libbridge_cffi.so`, which now exports both:
 *   1. the plain C ABI used elsewhere in the SDK
 *   2. the Android JNI entrypoints used by this class
 */
internal class JniBamlLib : NativeBamlLib {

    companion object {
        private var loaded = false

        @Synchronized
        fun ensureLoaded() {
            if (!loaded) {
                System.loadLibrary("bridge_cffi")
                loaded = true
            }
        }

        // --- Raw JNI native methods ---
        // These map to JNI symbols exported directly from bridge_cffi:
        //   Java_com_boundaryml_baml_JniBamlLib_nativeXxx

        @JvmStatic external fun nativeVersion(): ByteArray
        @JvmStatic external fun nativeCreateBamlRuntime(rootPath: String, srcFilesJson: String, envVarsJson: String): Long
        @JvmStatic external fun nativeDestroyBamlRuntime(runtime: Long)
        @JvmStatic external fun nativeRegisterCallbacks(
            resultCallback: NativeResultCallback,
            errorCallback: NativeResultCallback,
            onTickCallback: NativeOnTickCallback
        )
        @JvmStatic external fun nativeCallFunctionFromC(runtime: Long, functionName: String, encodedArgs: ByteArray, id: Int): ByteArray?
        @JvmStatic external fun nativeCallFunctionStreamFromC(runtime: Long, functionName: String, encodedArgs: ByteArray, id: Int): ByteArray?
        @JvmStatic external fun nativeCallFunctionParseFromC(runtime: Long, functionName: String, encodedArgs: ByteArray, id: Int): ByteArray?
        @JvmStatic external fun nativeCancelFunctionCall(id: Int): ByteArray?
        @JvmStatic external fun nativeCloneHandle(key: Long): Long
        @JvmStatic external fun nativeReleaseHandle(key: Long)
    }

    init {
        ensureLoaded()
    }

    override fun version(): ByteArray = nativeVersion()

    override fun createBamlRuntime(rootPath: String, srcFilesJson: String, envVarsJson: String): Long {
        val ptr = nativeCreateBamlRuntime(rootPath, srcFilesJson, envVarsJson)
        if (ptr == 0L) throw BamlException("Failed to create BAML runtime")
        return ptr
    }

    override fun destroyBamlRuntime(runtime: Long) = nativeDestroyBamlRuntime(runtime)

    override fun registerCallbacks(
        resultCallback: NativeResultCallback,
        errorCallback: NativeResultCallback,
        onTickCallback: NativeOnTickCallback
    ) = nativeRegisterCallbacks(resultCallback, errorCallback, onTickCallback)

    override fun callFunctionFromC(runtime: Long, functionName: String, encodedArgs: ByteArray, id: Int): ByteArray? =
        nativeCallFunctionFromC(runtime, functionName, encodedArgs, id)

    override fun callFunctionStreamFromC(runtime: Long, functionName: String, encodedArgs: ByteArray, id: Int): ByteArray? =
        nativeCallFunctionStreamFromC(runtime, functionName, encodedArgs, id)

    override fun callFunctionParseFromC(runtime: Long, functionName: String, encodedArgs: ByteArray, id: Int): ByteArray? =
        nativeCallFunctionParseFromC(runtime, functionName, encodedArgs, id)

    override fun cancelFunctionCall(id: Int): ByteArray? = nativeCancelFunctionCall(id)

    override fun cloneHandle(key: Long): Long = nativeCloneHandle(key)

    override fun releaseHandle(key: Long) = nativeReleaseHandle(key)
}
