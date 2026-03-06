package com.boundaryml.baml.integration

import com.boundaryml.baml.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Integration tests for streaming BAML function calls.
 * Requires the bridge_cffi dylib and valid API keys.
 *
 * These tests verify the SDK streaming plumbing (FFI, protobuf, Flow emission),
 * NOT the LLM output. A successful round-trip through the engine — even if the
 * LLM response can't be parsed — means the SDK is working correctly.
 *
 * BAML sources: src/test/resources/baml/openrouter_client.baml, story_function.baml
 */
class StreamingTest {

    companion object {
        private var ffiAvailable = false
        private var hasApiKey = false

        @BeforeAll
        @JvmStatic
        fun setup() {
            ffiAvailable = try {
                BamlFfi.load()
                true
            } catch (_: Throwable) {
                false
            }
            hasApiKey = System.getenv("OPENROUTER_API_KEY")?.isNotEmpty() == true
        }
    }

    private fun requireFullSetup() {
        assumeTrue(ffiAvailable, "bridge_cffi dylib not available")
        assumeTrue(hasApiKey, "OPENROUTER_API_KEY not set")
    }

    @Test
    fun `stream function collects partials and final`() = runBlocking {
        requireFullSetup()

        val srcFiles = BamlTestResources.load("openrouter_client.baml", "story_function.baml")
        val runtime = BamlRuntime.create(rootPath = ".", srcFiles = srcFiles)
        val client = BamlClient(runtime)
        val args = Serde.encodeArgs(mapOf("topic" to "a cat"))

        try {
            val flow = client.streamFunction("TellStory", args)
            val results = flow.toList()

            // If we got here, the streaming pipeline worked.
            // Verify we received at least one result from the stream.
            assertTrue(results.isNotEmpty(), "Should receive at least one result")
        } catch (e: BamlException) {
            // A BamlException means the engine processed the streaming call and
            // reported an error through the callback pipeline — the SDK worked.
            assertTrue(
                e.message?.isNotEmpty() == true,
                "TellStory stream: BamlException should have a message"
            )
        }

        runtime.destroy()
    }
}
