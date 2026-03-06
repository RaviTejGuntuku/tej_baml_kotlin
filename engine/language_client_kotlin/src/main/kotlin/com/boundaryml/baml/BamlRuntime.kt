package com.boundaryml.baml

import com.google.gson.Gson

/**
 * BAML runtime lifecycle management.
 * Wraps the Rust bridge_cffi library initialization and runtime creation.
 */
class BamlRuntime private constructor(
    internal val runtimePtr: com.sun.jna.Pointer
) {
    companion object {
        private val gson = Gson()
        private var callbacksRegistered = false

        /**
         * Get the BAML engine version string.
         */
        fun version(): String {
            val ffi = BamlFfi.instance ?: throw BamlException("FFI not loaded. Call BamlFfi.load() first.")
            val buf = ffi.version()
            try {
                return buf.toByteArray()?.let { String(it, Charsets.UTF_8) } ?: ""
            } finally {
                BamlFfi.freeBuffer(buf)
            }
        }

        /**
         * Create a new BAML runtime from source files.
         *
         * Environment variables (e.g. API keys referenced via `env.VAR_NAME` in BAML)
         * are read from the process environment by the Rust engine.
         *
         * @param rootPath Root path for BAML file resolution
         * @param srcFiles Map of filename → BAML source content
         * @param typeMap Type map for decoding outbound values
         */
        fun create(
            rootPath: String,
            srcFiles: Map<String, String>,
            typeMap: BamlTypeMap = BamlTypeMap()
        ): BamlRuntime {
            val ffi = BamlFfi.instance ?: throw BamlException("FFI not loaded. Call BamlFfi.load() first.")

            // Register callbacks once
            if (!callbacksRegistered) {
                ffi.register_callbacks(
                    CallbackManager.resultCallback,
                    CallbackManager.errorCallback,
                    CallbackManager.onTickCallback
                )
                callbacksRegistered = true
            }

            // Set type map for decoding
            CallbackManager.typeMap = typeMap

            val srcFilesJson = gson.toJson(srcFiles)

            val runtimePtr = ffi.create_baml_runtime(rootPath, srcFilesJson)
                ?: throw BamlException("Failed to create BAML runtime")

            return BamlRuntime(runtimePtr)
        }
    }

    /**
     * Destroy this runtime. After this call, the runtime pointer is invalid.
     */
    fun destroy() {
        BamlFfi.instance?.destroy_baml_runtime(runtimePtr)
    }
}
