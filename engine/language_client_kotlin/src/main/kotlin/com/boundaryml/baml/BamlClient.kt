package com.boundaryml.baml

import com.boundaryml.baml.cffi.InvocationResponse
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Client for calling BAML functions via the Rust engine.
 * Uses FFI + protobuf encoding + Kotlin coroutines for async delivery.
 */
class BamlClient(
    private val runtime: BamlRuntime
) {

    /**
     * Call a BAML function and suspend until the result is available.
     *
     * @param name The BAML function name
     * @param args Encoded protobuf bytes of HostFunctionArguments
     * @return The decoded result value
     */
    suspend fun callFunction(name: String, args: ByteArray): Any? = coroutineScope {
        val ffi = BamlFfi.instance ?: throw BamlException("FFI not loaded")
        val (callbackId, channel) = CallbackManager.createCallbackId()

        try {
            // Call the FFI function (this spawns an async task in Rust and returns immediately)
            val ackBytes = ffi.callFunctionFromC(
                runtime.runtimePtr,
                name,
                args,
                callbackId
            )

            // Check for synchronous errors in the ack
            try {
                checkAck(ackBytes)
            } catch (e: Exception) {
                CallbackManager.cleanupCallback(callbackId, channel)
                throw e
            }

            suspendCancellableCoroutine<Any?> { cont ->
                cont.invokeOnCancellation {
                    ffi.cancelFunctionCall(callbackId)
                    CallbackManager.cleanupCallback(callbackId, channel)
                }

                launch {
                    try {
                        val result = channel.receive()
                        if (result.error != null) {
                            cont.resumeWith(Result.failure(result.error))
                        } else {
                            cont.resumeWith(Result.success(result.data))
                        }
                    } catch (e: Throwable) {
                        if (cont.isActive) {
                            cont.resumeWith(Result.failure(e))
                        }
                    }
                }
            }
        } finally {
            CallbackManager.cleanupCallback(callbackId, channel)
        }
    }

    /**
     * Call a BAML function with streaming, returning a Flow of partial results.
     *
     * @param name The BAML function name
     * @param args Encoded protobuf bytes of HostFunctionArguments
     * @return A Flow emitting partial results (StreamState) and the final result
     */
    fun streamFunction(name: String, args: ByteArray): Flow<BamlResult> = callbackFlow {
        val ffi = BamlFfi.instance ?: throw BamlException("FFI not loaded")
        val (callbackId, channel) = CallbackManager.createStreamCallbackId()

        // Call the FFI function
        val ackBytes = ffi.callFunctionStreamFromC(
            runtime.runtimePtr,
            name,
            args,
            callbackId
        )

        try {
            checkAck(ackBytes)
        } catch (e: Exception) {
            CallbackManager.cleanupCallback(callbackId, channel)
            throw e
        }

        // Forward results from the callback channel to the flow
        launch {
            try {
                for (result in channel) {
                    trySend(result)
                    if (result.hasData) {
                        break
                    }
                }
            } finally {
                this@callbackFlow.channel.close()
            }
        }

        awaitClose {
            ffi.cancelFunctionCall(callbackId)
        }
    }

    /**
     * Call a BAML function for parsing (e.g., parse raw response).
     */
    suspend fun callFunctionParse(name: String, args: ByteArray): Any? = coroutineScope {
        val ffi = BamlFfi.instance ?: throw BamlException("FFI not loaded")
        val (callbackId, channel) = CallbackManager.createCallbackId()

        try {
            val ackBytes = ffi.callFunctionParseFromC(
                runtime.runtimePtr,
                name,
                args,
                callbackId
            )

            try {
                checkAck(ackBytes)
            } catch (e: Exception) {
                CallbackManager.cleanupCallback(callbackId, channel)
                throw e
            }

            suspendCancellableCoroutine<Any?> { cont ->
                cont.invokeOnCancellation {
                    ffi.cancelFunctionCall(callbackId)
                    CallbackManager.cleanupCallback(callbackId, channel)
                }

                launch {
                    try {
                        val result = channel.receive()
                        if (result.error != null) {
                            cont.resumeWith(Result.failure(result.error))
                        } else {
                            cont.resumeWith(Result.success(result.data ?: result.streamData))
                        }
                    } catch (e: Throwable) {
                        if (cont.isActive) {
                            cont.resumeWith(Result.failure(e))
                        }
                    }
                }
            }
        } finally {
            CallbackManager.cleanupCallback(callbackId, channel)
        }
    }

    /**
     * Check the synchronous ack bytes from an FFI call.
     * Null/empty = success (task spawned). Non-empty = InvocationResponse with potential error.
     */
    private fun checkAck(bytes: ByteArray?) {
        if (bytes == null || bytes.isEmpty()) return
        val response = InvocationResponse.parseFrom(bytes)
        if (response.hasError()) {
            throw BamlException(response.error)
        }
    }
}
