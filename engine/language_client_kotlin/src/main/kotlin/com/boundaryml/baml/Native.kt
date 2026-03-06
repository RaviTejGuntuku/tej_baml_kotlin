package com.boundaryml.baml

import com.sun.jna.*

/**
 * JNA callback type for result/error callbacks from Rust.
 * Signature: void(uint32_t id, int is_done, const int8_t *content, int length)
 */
interface ResultCallbackFn : Callback {
    fun invoke(id: Int, isDone: Int, content: Pointer?, length: Int)
}

/**
 * JNA callback type for on-tick callbacks from Rust (streaming progress).
 * Signature: void(uint32_t id)
 */
interface OnTickCallbackFn : Callback {
    fun invoke(id: Int)
}

/**
 * Represents the Buffer struct returned by FFI functions: { ptr: *const u8, len: usize }
 */
@Structure.FieldOrder("ptr", "len")
open class FfiBuffer : Structure(), Structure.ByValue {
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
 * JNA interface declaring all FFI functions exported by the bridge_cffi dylib.
 */
interface BamlFfiLib : Library {

    fun version(): FfiBuffer

    fun create_baml_runtime(
        root_path: String,
        src_files_json: String
    ): Pointer?

    fun destroy_baml_runtime(runtime: Pointer?)

    fun register_callbacks(
        result_callback: ResultCallbackFn,
        error_callback: ResultCallbackFn,
        on_tick_callback: OnTickCallbackFn
    )

    fun call_function_from_c(
        runtime: Pointer?,
        function_name: String,
        encoded_args: ByteArray,
        length: NativeLong,
        id: Int
    ): FfiBuffer

    fun call_function_stream_from_c(
        runtime: Pointer?,
        function_name: String,
        encoded_args: ByteArray,
        length: NativeLong,
        id: Int
    ): FfiBuffer

    fun call_function_parse_from_c(
        runtime: Pointer?,
        function_name: String,
        encoded_args: ByteArray,
        length: NativeLong,
        id: Int
    ): FfiBuffer

    fun cancel_function_call(id: Int): FfiBuffer

    fun clone_handle(key: Long): Long

    fun release_handle(key: Long)

    fun free_buffer(buffer: FfiBuffer)
}

/**
 * Singleton holder for the loaded FFI library.
 * Call [BamlFfi.load] to initialize, or access [BamlFfi.instance] after loading.
 */
object BamlFfi {
    @Volatile
    var instance: BamlFfiLib? = null
        private set

    /**
     * Load the bridge_cffi dynamic library.
     *
     * Resolution order:
     * 1. Explicit [path] parameter
     * 2. BAML_LIBRARY_PATH environment variable
     * 3. System library path (jna.library.path / java.library.path)
     */
    fun load(path: String? = null): BamlFfiLib {
        val libPath = path
            ?: System.getenv("BAML_LIBRARY_PATH")

        val lib = if (libPath != null) {
            Native.load(libPath, BamlFfiLib::class.java) as BamlFfiLib
        } else {
            val libName = platformLibraryName()
            Native.load(libName, BamlFfiLib::class.java) as BamlFfiLib
        }

        instance = lib
        return lib
    }

    /**
     * Returns the platform-specific library name for bridge_cffi.
     */
    private fun platformLibraryName(): String {
        val os = System.getProperty("os.name")?.lowercase() ?: ""
        return when {
            os.contains("mac") || os.contains("darwin") -> "baml_cffi"
            os.contains("win") -> "baml_cffi"
            os.contains("linux") -> "baml_cffi"
            else -> "baml_cffi"
        }
    }

    /**
     * Free a buffer returned by an FFI call.
     */
    fun freeBuffer(buffer: FfiBuffer) {
        instance?.free_buffer(buffer)
    }
}
