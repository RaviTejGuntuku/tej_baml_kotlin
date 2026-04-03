package com.boundaryml.baml.unit

import com.boundaryml.baml.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class BamlStreamTest {

    @Test
    fun `partials emits typed partial values`() = runBlocking {
        val rawFlow = flowOf(
            BamlResult(streamData = "partial1", hasStreamData = true),
            BamlResult(streamData = "partial2", hasStreamData = true),
            BamlResult(data = "final", hasData = true)
        )

        val stream = BamlStream<String, String>(rawFlow, { it as String }, { it as String })
        val partials = stream.partials.toList()

        assertEquals(listOf("partial1", "partial2"), partials)
        assertEquals("final", stream.getFinalResponse())
    }

    @Test
    fun `getFinalResponse throws if partials not collected`() {
        val rawFlow = flowOf(
            BamlResult(data = "final", hasData = true)
        )

        val stream = BamlStream<String, String>(rawFlow, { it as String }, { it as String })
        assertThrows<BamlException> { stream.getFinalResponse() }
    }

    @Test
    fun `stream with no partials still captures final`() = runBlocking {
        val rawFlow = flowOf(
            BamlResult(data = 42, hasData = true)
        )

        val stream = BamlStream<Int, Int>(rawFlow, { it as Int }, { it as Int })
        val partials = stream.partials.toList()

        assertEquals(emptyList(), partials)
        assertEquals(42, stream.getFinalResponse())
    }

    @Test
    fun `stream with typed class casts`() = runBlocking {
        data class PartialReceipt(val total: Double?)
        data class Receipt(val total: Double)

        val rawFlow = flowOf(
            BamlResult(streamData = mapOf("total" to null), hasStreamData = true),
            BamlResult(streamData = mapOf("total" to 19.99), hasStreamData = true),
            BamlResult(data = mapOf("total" to 19.99), hasData = true)
        )

        val stream = BamlStream<PartialReceipt, Receipt>(
            rawFlow,
            castPartial = {
                val map = it as Map<*, *>
                PartialReceipt(total = map["total"] as? Double)
            },
            castFinal = {
                val map = it as Map<*, *>
                Receipt(total = map["total"] as Double)
            }
        )

        val partials = stream.partials.toList()
        assertEquals(2, partials.size)
        assertEquals(null, partials[0].total)
        assertEquals(19.99, partials[1].total)
        assertEquals(Receipt(19.99), stream.getFinalResponse())
    }

    @Test
    fun `error in stream propagates`() {
        val rawFlow = flowOf(
            BamlResult(streamData = "partial1", hasStreamData = true),
            BamlResult(error = BamlException("LLM error"))
        )

        val stream = BamlStream<String, String>(rawFlow, { it as String }, { it as String })
        assertThrows<BamlException> {
            runBlocking { stream.partials.toList() }
        }
    }
}
