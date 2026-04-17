package com.boundaryml.baml

/**
 * Types of handles that can be held.
 */
enum class BamlHandleType {
    COLLECTOR,
    FUNCTION_LOG,
    USAGE,
    TIMING,
    STREAM_TIMING,
    LLM_CALL,
    LLM_STREAM_CALL,
    HTTP_REQUEST,
    HTTP_RESPONSE,
    HTTP_BODY,
    SSE_RESPONSE,
    MEDIA_IMAGE,
    MEDIA_AUDIO,
    MEDIA_PDF,
    MEDIA_VIDEO,
    TYPE_BUILDER,
    TYPE,
    ENUM_BUILDER,
    ENUM_VALUE_BUILDER,
    CLASS_BUILDER,
    CLASS_PROPERTY_BUILDER
}

/**
 * A handle to a BAML object managed by the Rust engine.
 * Implements AutoCloseable so it can be used with Kotlin's `use {}` pattern.
 */
class BamlHandle(
    val key: Long,
    val type: BamlHandleType
) : AutoCloseable {

    @Volatile
    private var closed = false

    override fun close() {
        if (!closed) {
            closed = true
            try {
                BamlFfi.instance?.releaseHandle(key)
            } catch (_: Exception) {
                // Best-effort release; ignore errors on close
            }
        }
    }

    fun clone(): BamlHandle {
        check(!closed) { "Cannot clone a closed handle" }
        val ffi = BamlFfi.instance ?: throw BamlException("FFI not initialized")
        val newKey = ffi.cloneHandle(key)
        return BamlHandle(newKey, type)
    }

    val isClosed: Boolean get() = closed
}
