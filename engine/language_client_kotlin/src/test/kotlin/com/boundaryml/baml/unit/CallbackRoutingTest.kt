package com.boundaryml.baml.unit

import com.boundaryml.baml.*
import com.boundaryml.baml.cffi.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CallbackRoutingTest {

    @BeforeEach
    fun setUp() {
        CallbackManager.clear()
        CallbackManager.typeMap = BamlTypeMap()
    }

    @AfterEach
    fun tearDown() {
        CallbackManager.clear()
    }

    @Test
    fun `result callback routes to correct deferred`() = runTest {
        val (id1, channel1) = CallbackManager.createCallbackId()
        val (id2, channel2) = CallbackManager.createCallbackId()
        val (id3, channel3) = CallbackManager.createCallbackId()

        // Fire callback for id2
        val holder2 = cFFIValueHolder { stringValue = "result2" }
        CallbackManager.fireResult(id2, 1, holder2.toByteArray())

        // Fire callback for id1
        val holder1 = cFFIValueHolder { intValue = 42L }
        CallbackManager.fireResult(id1, 1, holder1.toByteArray())

        // Fire callback for id3
        val holder3 = cFFIValueHolder { boolValue = true }
        CallbackManager.fireResult(id3, 1, holder3.toByteArray())

        // Verify each channel gets the correct value
        val result2 = channel2.receive()
        assertTrue(result2.hasData)
        assertEquals("result2", result2.data)

        val result1 = channel1.receive()
        assertTrue(result1.hasData)
        assertEquals(42L, result1.data)

        val result3 = channel3.receive()
        assertTrue(result3.hasData)
        assertEquals(true, result3.data)
    }

    @Test
    fun `error callback completes deferred exceptionally`() = runTest {
        val (id, channel) = CallbackManager.createCallbackId()

        CallbackManager.fireError(id, "Something went wrong")

        val result = channel.receive()
        val error = result.error
        assertNotNull(error)
        assertIs<BamlException>(error)
        assertEquals("Something went wrong", error.message)
    }

    @Test
    fun `abort error produces BamlClientError`() = runTest {
        val (id, channel) = CallbackManager.createCallbackId()

        CallbackManager.fireError(id, "AbortError")

        val result = channel.receive()
        val error = result.error
        assertNotNull(error)
        assertIs<BamlClientError>(error)
    }

    @Test
    fun `streaming receives multiple partials then final`() = runTest {
        val (id, channel) = CallbackManager.createStreamCallbackId()

        // Send 3 partials (isDone=0)
        val partial1 = cFFIValueHolder { stringValue = "chunk1" }
        CallbackManager.fireResult(id, 0, partial1.toByteArray())

        val partial2 = cFFIValueHolder { stringValue = "chunk2" }
        CallbackManager.fireResult(id, 0, partial2.toByteArray())

        val partial3 = cFFIValueHolder { stringValue = "chunk3" }
        CallbackManager.fireResult(id, 0, partial3.toByteArray())

        // Send final (isDone=1)
        val finalResult = cFFIValueHolder { stringValue = "complete" }
        CallbackManager.fireResult(id, 1, finalResult.toByteArray())

        // Receive all 4
        val r1 = channel.receive()
        assertTrue(r1.hasStreamData)
        assertEquals("chunk1", r1.streamData)

        val r2 = channel.receive()
        assertTrue(r2.hasStreamData)
        assertEquals("chunk2", r2.streamData)

        val r3 = channel.receive()
        assertTrue(r3.hasStreamData)
        assertEquals("chunk3", r3.streamData)

        val r4 = channel.receive()
        assertTrue(r4.hasData)
        assertEquals("complete", r4.data)
    }

    @Test
    fun `unknown call id does not crash`() {
        // Fire callbacks with non-existent IDs — should be silently ignored
        val holder = cFFIValueHolder { stringValue = "orphan" }
        CallbackManager.fireResult(999, 1, holder.toByteArray())
        CallbackManager.fireError(998, "orphan error")
        // No exception = success
    }

    @Test
    fun `concurrent callbacks on different threads`() = runTest {
        val count = 10
        val pairs = (0 until count).map { CallbackManager.createCallbackId() }

        // Fire callbacks concurrently from different coroutines
        coroutineScope {
            pairs.forEachIndexed { index, (id, _) ->
                launch(Dispatchers.Default) {
                    val holder = cFFIValueHolder { intValue = index.toLong() }
                    CallbackManager.fireResult(id, 1, holder.toByteArray())
                }
            }
        }

        // Verify all received correctly
        pairs.forEachIndexed { index, (_, channel) ->
            val result = channel.receive()
            assertTrue(result.hasData)
            assertEquals(index.toLong(), result.data)
        }
    }

    @Test
    fun `cleanup callback closes channel and removes entry`() = runTest {
        val (id, channel) = CallbackManager.createCallbackId()
        val initialCount = CallbackManager.pendingCount()

        CallbackManager.cleanupCallback(id, channel)

        assertEquals(initialCount - 1, CallbackManager.pendingCount())
        assertTrue(channel.receiveCatching().isClosed)
    }

    @Test
    fun `callback with complex protobuf decodes correctly`() = runTest {
        val (id, channel) = CallbackManager.createCallbackId()

        val holder = cFFIValueHolder {
            classValue = cFFIValueClass {
                name = cFFITypeName {
                    namespace = CFFITypeNamespace.TYPES
                    this.name = "Result"
                }
                fields.add(cFFIMapEntry {
                    key = "items"
                    value = cFFIValueHolder {
                        listValue = cFFIValueList {
                            items.add(cFFIValueHolder { stringValue = "item1" })
                            items.add(cFFIValueHolder { stringValue = "item2" })
                        }
                    }
                })
            }
        }
        CallbackManager.fireResult(id, 1, holder.toByteArray())

        val result = channel.receive()
        assertTrue(result.hasData)
        assertIs<DynamicBamlClass>(result.data)
        val cls = result.data as DynamicBamlClass
        assertEquals("Result", cls.name)
        val items = cls.fields["items"]
        assertIs<List<*>>(items)
        assertEquals(listOf("item1", "item2"), items)
    }
}
