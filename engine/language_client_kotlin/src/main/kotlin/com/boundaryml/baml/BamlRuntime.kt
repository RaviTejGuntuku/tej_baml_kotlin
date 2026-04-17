package com.boundaryml.baml

import com.google.gson.Gson

/**
 * BAML runtime lifecycle management.
 * Wraps the Rust bridge_cffi library initialization and runtime creation.
 */
class BamlRuntime private constructor(
    internal val runtimePtr: Long
) {
    companion object {
        private val gson = Gson()
        private var callbacksRegistered = false

        /**
         * Get the BAML engine version string.
         */
        fun version(): String {
            val ffi = BamlFfi.instance ?: throw BamlException("FFI not loaded. Call BamlFfi.load() first.")
            val bytes = ffi.version()
            return if (bytes.isNotEmpty()) String(bytes, Charsets.UTF_8) else ""
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
                ffi.registerCallbacks(
                    CallbackManager.resultCallback,
                    CallbackManager.errorCallback,
                    CallbackManager.onTickCallback
                )
                callbacksRegistered = true
            }

            // Set type map for decoding
            CallbackManager.typeMap = typeMap

            val srcFilesJson = gson.toJson(srcFiles)

            val runtimePtr = ffi.createBamlRuntime(rootPath, srcFilesJson)
            return BamlRuntime(runtimePtr)
        }
    }

    /**
     * Destroy this runtime. After this call, the runtime pointer is invalid.
     */
    fun destroy() {
        BamlFfi.instance?.destroyBamlRuntime(runtimePtr)
    }
}
