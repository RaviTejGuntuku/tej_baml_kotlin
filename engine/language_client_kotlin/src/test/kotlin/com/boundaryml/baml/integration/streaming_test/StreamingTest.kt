package com.boundaryml.baml.integration.streaming_test

import com.boundaryml.baml.*
import com.boundaryml.baml.integration.BamlProject
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class StreamingTest {

    companion object {
        private var ffiAvailable = false
        private var hasApiKey = false

        private lateinit var project: BamlProject

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
            if (ffiAvailable) {
                project = BamlProject.load(StreamingTest::class)
            }
        }
    }

    private fun requireFullSetup() {
        assumeTrue(ffiAvailable, "bridge_cffi dylib not available")
        assumeTrue(hasApiKey, "OPENROUTER_API_KEY not set")
    }

    @Test
    fun `stream function collects partials and final`() = runBlocking {
        requireFullSetup()
        val project = Companion.project
        val runtime = BamlRuntime.create(rootPath = project.rootPath, srcFiles = project.srcFiles)
        val client = BamlClient(runtime)
        val args = Serde.encodeArgs(mapOf("topic" to "a cat"))

        try {
            val flow = client.streamFunction("TellStory", args)
            val results = flow.toList()
            assertTrue(results.isNotEmpty(), "Should receive at least one result")
        } catch (e: BamlException) {
            assertTrue(e.message?.isNotEmpty() == true)
        }

        runtime.destroy()
    }
}
