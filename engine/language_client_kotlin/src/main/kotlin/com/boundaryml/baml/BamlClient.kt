package com.boundaryml.baml

import com.boundaryml.baml.cffi.InvocationResponse
import com.sun.jna.NativeLong
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException

/**
 * Client for calling BAML functions via the Rust engine.
 * Uses JNA FFI + protobuf encoding + Kotlin coroutines for async delivery.
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

        // Launch a coroutine to handle cancellation
        val cancelJob = launch {
            try {
                // This coroutine will be cancelled when the parent scope is cancelled
                suspendCancellableCoroutine<Unit> { cont ->
                    cont.invokeOnCancellation {
                        ffi.cancel_function_call(callbackId)
                    }
                }
            } catch (_: Exception) {
                // Expected when cancelled
            }
        }

        try {
            // Call the FFI function (this spawns an async task in Rust and returns immediately)
            val ackBuf = ffi.call_function_from_c(
                runtime.runtimePtr,
                name,
                args,
                NativeLong(args.size.toLong()),
                callbackId
            )

            // Check for synchronous errors in the ack
            try {
                checkAck(ackBuf)
            } catch (e: Exception) {
                CallbackManager.cleanupCallback(callbackId, channel)
                throw e
            }

            // Wait for the callback result
            val result = channel.receive()
            if (result.error != null) {
                throw result.error
            }
            result.data
        } finally {
            cancelJob.cancel()
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
        val ackBuf = ffi.call_function_stream_from_c(
            runtime.runtimePtr,
            name,
            args,
            NativeLong(args.size.toLong()),
            callbackId
        )

        try {
            checkAck(ackBuf)
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
            ffi.cancel_function_call(callbackId)
        }
    }

    /**
     * Call a BAML function for parsing (e.g., parse raw response).
     */
    suspend fun callFunctionParse(name: String, args: ByteArray): Any? = coroutineScope {
        val ffi = BamlFfi.instance ?: throw BamlException("FFI not loaded")
        val (callbackId, channel) = CallbackManager.createCallbackId()

        val cancelJob = launch {
            try {
                suspendCancellableCoroutine<Unit> { cont ->
                    cont.invokeOnCancellation {
                        ffi.cancel_function_call(callbackId)
                    }
                }
            } catch (_: Exception) {}
        }

        try {
            val ackBuf = ffi.call_function_parse_from_c(
                runtime.runtimePtr,
                name,
                args,
                NativeLong(args.size.toLong()),
                callbackId
            )

            try {
                checkAck(ackBuf)
            } catch (e: Exception) {
                CallbackManager.cleanupCallback(callbackId, channel)
                throw e
            }

            val result = channel.receive()
            if (result.error != null) {
                throw result.error
            }
            result.data ?: result.streamData
        } finally {
            cancelJob.cancel()
        }
    }

    /**
     * Check the synchronous ack buffer from an FFI call.
     * Empty buffer = success (task spawned). Non-empty = InvocationResponse with potential error.
     */
    private fun checkAck(buf: FfiBuffer) {
        try {
            val bytes = buf.toByteArray() ?: return // Empty = success

            val response = InvocationResponse.parseFrom(bytes)
            if (response.hasError()) {
                throw BamlException(response.error)
            }
        } finally {
            BamlFfi.freeBuffer(buf)
        }
    }
}
