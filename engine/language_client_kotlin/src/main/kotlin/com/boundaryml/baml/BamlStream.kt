package com.boundaryml.baml

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Typed wrapper around a raw streaming Flow.
 *
 * Generated function wrappers create a BamlStream with cast lambdas that convert
 * the raw `Any?` values from the FFI layer into the correct Partial and Final types.
 *
 * Usage from generated code:
 * ```kotlin
 * fun streamExtractReceipt(input: String, options: CallOptions? = null): BamlStream<PartialReceipt, Receipt> {
 *     val args = Serde.encodeArgs(mapOf("input" to input), options)
 *     val rawFlow = client.streamFunction("ExtractReceipt", args)
 *     return BamlStream(rawFlow, { it as PartialReceipt }, { it as Receipt })
 * }
 * ```
 *
 * @param PartialT The partial/streaming type (from stream_types package)
 * @param FinalT The final/complete type (from types package)
 */
class BamlStream<PartialT, FinalT>(
    private val rawFlow: Flow<BamlResult>,
    private val castPartial: (Any?) -> PartialT,
    private val castFinal: (Any?) -> FinalT
) {
    private var finalResult: FinalT? = null
    private var finalReceived = false
    private var finalError: Throwable? = null

    /**
     * Flow of typed partial results as they arrive from the LLM.
     * Completes when the final result arrives.
     * The final result is captured internally and available via [getFinalResponse].
     */
    val partials: Flow<PartialT> = rawFlow
        .onEach { result ->
            if (result.error != null) {
                finalError = result.error
                throw result.error
            }
            if (result.hasData) {
                finalResult = castFinal(result.data)
                finalReceived = true
            }
        }
        .filter { it.hasStreamData }
        .map { castPartial(it.streamData) }

    /**
     * Get the final response after the stream completes.
     * Must be called after collecting [partials] (or at least after the flow completes).
     *
     * @throws BamlException if the stream ended with an error or no final result was received
     */
    fun getFinalResponse(): FinalT {
        finalError?.let { throw it }
        if (!finalReceived) {
            throw BamlException("No final response received. Make sure to collect partials first.")
        }
        @Suppress("UNCHECKED_CAST")
        return finalResult as FinalT
    }
}
