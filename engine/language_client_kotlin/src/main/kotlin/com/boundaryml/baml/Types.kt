package com.boundaryml.baml

import com.boundaryml.baml.cffi.HostValue

/**
 * Interface for types that can be serialized to protobuf HostValue for sending to the BAML engine.
 */
interface BamlSerializable {
    fun encode(): HostValue
    fun bamlTypeName(): String
}

/**
 * Interface for types that can be deserialized from the BAML engine's outbound values.
 */
interface BamlDeserializable<T> {
    fun decode(fields: Map<String, Any?>, typeMap: BamlTypeMap): T
}

/**
 * Result wrapper for callback delivery.
 */
data class BamlResult(
    val data: Any? = null,
    val streamData: Any? = null,
    val error: Throwable? = null,
    val hasData: Boolean = false,
    val hasStreamData: Boolean = false
)

/**
 * Base exception for all BAML errors.
 */
open class BamlException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Error from the BAML client/runtime.
 */
class BamlClientError(message: String, cause: Throwable? = null) : BamlException(message, cause)

/**
 * Options for function calls.
 */
data class CallOptions(
    val client: String? = null,
    val env: Map<String, String>? = null,
    val tags: Map<String, String>? = null
)

/**
 * Represents the streaming state of a value.
 */
sealed class StreamState<out T> {
    data object Pending : StreamState<Nothing>()
    data class Started<T>(val value: T) : StreamState<T>()
    data class Done<T>(val value: T) : StreamState<T>()
}

/**
 * A checked value with associated constraint check results.
 */
data class Checked<T>(
    val value: T,
    val checks: Map<String, CheckResult>
)

/**
 * Result of a constraint check.
 */
data class CheckResult(
    val name: String,
    val expression: String,
    val status: String
)
