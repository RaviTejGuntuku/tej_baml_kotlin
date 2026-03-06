package com.boundaryml.baml.integration

import com.boundaryml.baml.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Integration tests for error handling in BAML function calls.
 * Requires the bridge_cffi dylib (but NOT a real API key).
 *
 * BAML sources: src/test/resources/baml/fake_client.baml, echo_function.baml
 */
class ErrorHandlingTest {

    companion object {
        private var ffiAvailable = false

        @BeforeAll
        @JvmStatic
        fun setup() {
            ffiAvailable = try {
                BamlFfi.load()
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    private fun requireFfi() {
        assumeTrue(ffiAvailable, "bridge_cffi dylib not available")
    }

    @Test
    fun `call non-existent function throws`() = runBlocking {
        requireFfi()

        val srcFiles = BamlTestResources.load("fake_client.baml")
        val runtime = BamlRuntime.create(rootPath = ".", srcFiles = srcFiles)
        val client = BamlClient(runtime)
        val args = Serde.encodeArgs(mapOf("input" to "test"))

        val exception = assertFailsWith<Exception> {
            client.callFunction("NonExistentFunction", args)
        }
        assertTrue(exception.message?.isNotEmpty() == true)

        runtime.destroy()
    }

    @Test
    fun `invalid protobuf args throws`() = runBlocking {
        requireFfi()

        val srcFiles = BamlTestResources.load("fake_client.baml", "echo_function.baml")
        val runtime = BamlRuntime.create(rootPath = ".", srcFiles = srcFiles)
        val client = BamlClient(runtime)
        // Send garbage bytes as encoded args
        val badArgs = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x01)

        val exception = assertFailsWith<Exception> {
            client.callFunction("Echo", badArgs)
        }
        assertTrue(exception.message?.isNotEmpty() == true)

        runtime.destroy()
    }
}
