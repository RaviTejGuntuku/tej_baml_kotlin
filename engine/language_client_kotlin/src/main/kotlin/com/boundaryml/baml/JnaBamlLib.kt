package com.boundaryml.baml

import com.sun.jna.*

/**
 * JNA callback type for result/error callbacks from Rust.
 * Signature: void(uint32_t id, int is_done, const int8_t *content, int length)
 */
internal interface JnaResultCallbackFn : Callback {
    fun invoke(id: Int, isDone: Int, content: Pointer?, length: Int)
}

/**
 * JNA callback type for on-tick callbacks from Rust (streaming progress).
 * Signature: void(uint32_t id)
 */
internal interface JnaOnTickCallbackFn : Callback {
    fun invoke(id: Int)
}

/**
 * Represents the Buffer struct returned by FFI functions: { ptr: *const u8, len: usize }
 */
@Structure.FieldOrder("ptr", "len")
internal open class FfiBuffer : Structure(), Structure.ByValue {
    @JvmField var ptr: Pointer? = null
    @JvmField var len: NativeLong = NativeLong(0)

    fun toByteArray(): ByteArray? {
        val p = ptr ?: return null
        val size = len.toLong().toInt()
        if (size <= 0) return null
        return p.getByteArray(0, size)
    }
}

/**
 * Raw JNA interface declaring all FFI functions exported by the bridge_cffi dylib.
 */
internal interface JnaFfiLib : Library {
    fun version(): FfiBuffer
    fun create_baml_runtime(root_path: String, src_files_json: String): Pointer?
    fun destroy_baml_runtime(runtime: Pointer?)
    fun register_callbacks(
        result_callback: JnaResultCallbackFn,
        error_callback: JnaResultCallbackFn,
        on_tick_callback: JnaOnTickCallbackFn
    )
    fun call_function_from_c(runtime: Pointer?, function_name: String, encoded_args: ByteArray, length: NativeLong, id: Int): FfiBuffer
    fun call_function_stream_from_c(runtime: Pointer?, function_name: String, encoded_args: ByteArray, length: NativeLong, id: Int): FfiBuffer
    fun call_function_parse_from_c(runtime: Pointer?, function_name: String, encoded_args: ByteArray, length: NativeLong, id: Int): FfiBuffer
    fun cancel_function_call(id: Int): FfiBuffer
    fun clone_handle(key: Long): Long
    fun release_handle(key: Long)
    fun free_buffer(buffer: FfiBuffer)
}

/**
 * JNA-based implementation of [NativeBamlLib] for desktop JVM (macOS, Linux, Windows).
 * Wraps the raw JNA interface and converts JNA types to plain Kotlin types.
 */
internal class JnaBamlLib private constructor(
    private val ffi: JnaFfiLib
) : NativeBamlLib {

    companion object {
        fun create(path: String? = null): JnaBamlLib {
            val libPath = path ?: System.getenv("BAML_LIBRARY_PATH")

            val ffi = if (libPath != null) {
                Native.load(libPath, JnaFfiLib::class.java) as JnaFfiLib
            } else {
                val extracted = extractBundledLibrary()
                if (extracted != null) {
                    Native.load(extracted, JnaFfiLib::class.java) as JnaFfiLib
                } else {
                    Native.load("baml_cffi", JnaFfiLib::class.java) as JnaFfiLib
                }
            }

            return JnaBamlLib(ffi)
        }

        private fun extractBundledLibrary(): String? {
            val (resourcePath, fileName) = nativeResourcePath() ?: return null
            val stream = JnaBamlLib::class.java.getResourceAsStream(resourcePath) ?: return null

            return try {
                val tmpDir = java.io.File(System.getProperty("java.io.tmpdir"), "baml-native")
                tmpDir.mkdirs()
                val tmpFile = java.io.File(tmpDir, fileName)
                if (!tmpFile.exists() || tmpFile.length() == 0L) {
                    stream.use { input ->
                        tmpFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    tmpFile.setExecutable(true)
                }
                tmpFile.absolutePath
            } catch (_: Throwable) {
                null
            }
        }

        private fun nativeResourcePath(): Pair<String, String>? {
            val os = System.getProperty("os.name")?.lowercase() ?: ""
            val arch = System.getProperty("os.arch")?.lowercase() ?: ""
            return when {
                os.contains("mac") && arch.contains("aarch64") ->
                    "/native/darwin-aarch64/libbridge_cffi.dylib" to "libbridge_cffi.dylib"
                os.contains("mac") && arch.contains("x86_64") ->
                    "/native/darwin-x86_64/libbridge_cffi.dylib" to "libbridge_cffi.dylib"
                os.contains("linux") && arch.contains("aarch64") ->
                    "/native/linux-aarch64/libbridge_cffi.so" to "libbridge_cffi.so"
                os.contains("linux") && arch.contains("amd64") ->
                    "/native/linux-x86_64/libbridge_cffi.so" to "libbridge_cffi.so"
                else -> null
            }
        }
    }

    /** Convert a runtime Pointer to Long for the platform-agnostic API. */
    private var runtimePointers = java.util.concurrent.ConcurrentHashMap<Long, Pointer>()

    override fun version(): ByteArray {
        val buf = ffi.version()
        try {
            return buf.toByteArray() ?: ByteArray(0)
        } finally {
            ffi.free_buffer(buf)
        }
    }

    override fun createBamlRuntime(rootPath: String, srcFilesJson: String): Long {
        val ptr = ffi.create_baml_runtime(rootPath, srcFilesJson)
            ?: throw BamlException("Failed to create BAML runtime")
        val key = Pointer.nativeValue(ptr)
        runtimePointers[key] = ptr
        return key
    }

    override fun destroyBamlRuntime(runtime: Long) {
        val ptr = runtimePointers.remove(runtime) ?: Pointer(runtime)
        ffi.destroy_baml_runtime(ptr)
    }

    override fun registerCallbacks(
        resultCallback: NativeResultCallback,
        errorCallback: NativeResultCallback,
        onTickCallback: NativeOnTickCallback
    ) {
        // Wrap platform-agnostic callbacks as JNA callbacks
        val jnaResult = object : JnaResultCallbackFn {
            override fun invoke(id: Int, isDone: Int, content: Pointer?, length: Int) {
                val bytes = if (content != null && length > 0) content.getByteArray(0, length) else null
                resultCallback.invoke(id, isDone, bytes)
            }
        }
        val jnaError = object : JnaResultCallbackFn {
            override fun invoke(id: Int, isDone: Int, content: Pointer?, length: Int) {
                val bytes = if (content != null && length > 0) content.getByteArray(0, length) else null
                errorCallback.invoke(id, isDone, bytes)
            }
        }
        val jnaTick = object : JnaOnTickCallbackFn {
            override fun invoke(id: Int) {
                onTickCallback.invoke(id)
            }
        }
        // Hold strong references so GC doesn't collect them
        _jnaResultCb = jnaResult
        _jnaErrorCb = jnaError
        _jnaTickCb = jnaTick
        ffi.register_callbacks(jnaResult, jnaError, jnaTick)
    }

    // Strong references to prevent GC of JNA callbacks
    private var _jnaResultCb: JnaResultCallbackFn? = null
    private var _jnaErrorCb: JnaResultCallbackFn? = null
    private var _jnaTickCb: JnaOnTickCallbackFn? = null

    override fun callFunctionFromC(runtime: Long, functionName: String, encodedArgs: ByteArray, id: Int): ByteArray? {
        val ptr = runtimePointers[runtime] ?: Pointer(runtime)
        val buf = ffi.call_function_from_c(ptr, functionName, encodedArgs, NativeLong(encodedArgs.size.toLong()), id)
        try {
            return buf.toByteArray()
        } finally {
            ffi.free_buffer(buf)
        }
    }

    override fun callFunctionStreamFromC(runtime: Long, functionName: String, encodedArgs: ByteArray, id: Int): ByteArray? {
        val ptr = runtimePointers[runtime] ?: Pointer(runtime)
        val buf = ffi.call_function_stream_from_c(ptr, functionName, encodedArgs, NativeLong(encodedArgs.size.toLong()), id)
        try {
            return buf.toByteArray()
        } finally {
            ffi.free_buffer(buf)
        }
    }

    override fun callFunctionParseFromC(runtime: Long, functionName: String, encodedArgs: ByteArray, id: Int): ByteArray? {
        val ptr = runtimePointers[runtime] ?: Pointer(runtime)
        val buf = ffi.call_function_parse_from_c(ptr, functionName, encodedArgs, NativeLong(encodedArgs.size.toLong()), id)
        try {
            return buf.toByteArray()
        } finally {
            ffi.free_buffer(buf)
        }
    }

    override fun cancelFunctionCall(id: Int): ByteArray? {
        val buf = ffi.cancel_function_call(id)
        try {
            return buf.toByteArray()
        } finally {
            ffi.free_buffer(buf)
        }
    }

    override fun cloneHandle(key: Long): Long = ffi.clone_handle(key)

    override fun releaseHandle(key: Long) = ffi.release_handle(key)
}
