package com.boundaryml.baml

import com.boundaryml.baml.cffi.CFFIValueHolder
import com.boundaryml.baml.cffi.InvocationResponse
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Response type constants for callback dispatch.
 */
private const val RESPONSE_TYPE_VALUE = "value"
private const val RESPONSE_TYPE_OBJECT_HANDLE = "object_handle"

/**
 * Entry in the callback map, holding either a deferred (for single-shot calls)
 * or a channel (for streaming calls).
 */
internal class CallbackEntry(
    val channel: Channel<BamlResult>,
    val responseType: String = RESPONSE_TYPE_VALUE
)

/**
 * Manages callback registration and routing between the Rust engine and Kotlin coroutines.
 *
 * The Rust engine fires callbacks on its own tokio threads. This class routes those callbacks
 * to the correct Kotlin coroutine via ConcurrentHashMap + Channel.
 */
object CallbackManager {
    private val callbacks = ConcurrentHashMap<Int, CallbackEntry>()
    private val nextId = AtomicInteger(0)

    internal var typeMap: BamlTypeMap = BamlTypeMap()

    /**
     * Create a unique callback ID and register a single-shot entry.
     * Returns (id, Channel) — the channel will receive exactly one BamlResult then close.
     */
    fun createCallbackId(): Pair<Int, Channel<BamlResult>> {
        val id = nextId.incrementAndGet()
        val channel = Channel<BamlResult>(capacity = 64)
        callbacks[id] = CallbackEntry(channel, RESPONSE_TYPE_VALUE)
        return id to channel
    }

    /**
     * Create a unique callback ID for streaming.
     * Returns (id, Channel) — the channel will receive multiple BamlResults.
     */
    fun createStreamCallbackId(): Pair<Int, Channel<BamlResult>> {
        val id = nextId.incrementAndGet()
        val channel = Channel<BamlResult>(capacity = 64)
        callbacks[id] = CallbackEntry(channel, RESPONSE_TYPE_VALUE)
        return id to channel
    }

    /**
     * Create a callback ID for object handle responses.
     */
    fun createObjectCallbackId(): Pair<Int, Channel<BamlResult>> {
        val id = nextId.incrementAndGet()
        val channel = Channel<BamlResult>(capacity = 1)
        callbacks[id] = CallbackEntry(channel, RESPONSE_TYPE_OBJECT_HANDLE)
        return id to channel
    }

    /**
     * Clean up a callback entry (used when the FFI call itself fails synchronously).
     */
    fun cleanupCallback(id: Int, channel: Channel<BamlResult>) {
        channel.close()
        callbacks.remove(id)
    }

    /**
     * The result callback — called from Rust tokio threads (via JNA or JNI bridge).
     * Content is already converted to ByteArray by the native bridge layer.
     */
    internal val resultCallback = object : NativeResultCallback {
        override fun invoke(id: Int, isDone: Int, content: ByteArray?) {
            val entry = callbacks[id] ?: return

            try {
                if (entry.responseType == RESPONSE_TYPE_OBJECT_HANDLE) {
                    handleObjectCallback(id, entry, content)
                    return
                }

                if (content == null || content.isEmpty()) {
                    safeSend(entry.channel, BamlResult(error = BamlException("Empty result callback")))
                    if (isDone == 1) {
                        safeClose(entry.channel)
                        callbacks.remove(id)
                    }
                    return
                }

                val holder = CFFIValueHolder.parseFrom(content)
                val decoded = Serde.decodeValue(holder, typeMap)

                val result = if (isDone == 1) {
                    BamlResult(data = decoded, hasData = true)
                } else {
                    BamlResult(streamData = decoded, hasStreamData = true)
                }

                safeSend(entry.channel, result)

                if (isDone == 1) {
                    safeClose(entry.channel)
                    callbacks.remove(id)
                }
            } catch (e: Exception) {
                safeSend(entry.channel, BamlResult(error = BamlException("Failed to decode result: ${e.message}", e)))
                safeClose(entry.channel)
                callbacks.remove(id)
            }
        }
    }

    /**
     * Handle object-handle type callbacks (e.g., from build_request).
     */
    private fun handleObjectCallback(id: Int, entry: CallbackEntry, bytes: ByteArray?) {
        try {
            if (bytes == null || bytes.isEmpty()) {
                safeSend(entry.channel, BamlResult(error = BamlException("Empty object callback")))
                safeClose(entry.channel)
                callbacks.remove(id)
                return
            }

            val response = InvocationResponse.parseFrom(bytes)
            when {
                response.hasError() -> {
                    safeSend(entry.channel, BamlResult(error = BamlException(response.error)))
                }
                response.hasSuccess() -> {
                    val success = response.success
                    when {
                        success.hasValue() -> {
                            val decoded = Serde.decodeValue(success.value, typeMap)
                            safeSend(entry.channel, BamlResult(data = decoded, hasData = true))
                        }
                        success.hasObject() -> {
                            safeSend(entry.channel, BamlResult(data = BamlObjectRef(success.`object`), hasData = true))
                        }
                        else -> {
                            safeSend(entry.channel, BamlResult(error = BamlException("Unexpected InvocationResponse result type")))
                        }
                    }
                }
                else -> {
                    safeSend(entry.channel, BamlResult(error = BamlException("Empty InvocationResponse")))
                }
            }
        } catch (e: Exception) {
            safeSend(entry.channel, BamlResult(error = BamlException("Failed to decode object response: ${e.message}", e)))
        } finally {
            safeClose(entry.channel)
            callbacks.remove(id)
        }
    }

    /**
     * The error callback — called from Rust tokio threads (via JNA or JNI bridge).
     */
    internal val errorCallback = object : NativeResultCallback {
        override fun invoke(id: Int, isDone: Int, content: ByteArray?) {
            val entry = callbacks[id] ?: return

            val errorMessage = if (content != null && content.isNotEmpty()) {
                String(content, Charsets.UTF_8)
            } else {
                "Unknown error"
            }

            val error = if (errorMessage == "AbortError") {
                BamlClientError("Operation was cancelled")
            } else {
                BamlException(errorMessage)
            }

            safeSend(entry.channel, BamlResult(error = error))
            safeClose(entry.channel)
            callbacks.remove(id)
        }
    }

    /**
     * The on-tick callback — called from Rust for streaming progress.
     */
    internal val onTickCallback = object : NativeOnTickCallback {
        override fun invoke(id: Int) {
            // On-tick is used for collector-based streaming in Go.
            // For Kotlin, streaming data arrives via the result callback.
            // This is a no-op placeholder for future collector support.
        }
    }

    /**
     * Safely send a result on a channel, ignoring if the channel is already closed.
     */
    private fun safeSend(channel: Channel<BamlResult>, result: BamlResult) {
        try {
            channel.trySend(result)
        } catch (_: Exception) {
            // Channel already closed by concurrent callback
        }
    }

    /**
     * Safely close a channel, ignoring if already closed.
     */
    private fun safeClose(channel: Channel<BamlResult>) {
        try {
            channel.close()
        } catch (_: Exception) {
            // Already closed
        }
    }

    /** Visible for testing — get current callback map size */
    internal fun pendingCount(): Int = callbacks.size

    /** Visible for testing — clear all callbacks */
    internal fun clear() {
        callbacks.values.forEach { it.channel.close() }
        callbacks.clear()
    }

    /** Visible for testing — fire a result callback directly */
    internal fun fireResult(id: Int, isDone: Int, bytes: ByteArray) {
        resultCallback.invoke(id, isDone, bytes)
    }

    /** Visible for testing — fire an error callback directly */
    internal fun fireError(id: Int, message: String) {
        val bytes = message.toByteArray(Charsets.UTF_8)
        errorCallback.invoke(id, 1, bytes)
    }
}
