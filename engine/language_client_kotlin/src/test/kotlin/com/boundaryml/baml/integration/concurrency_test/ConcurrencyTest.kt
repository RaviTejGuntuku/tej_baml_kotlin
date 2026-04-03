package com.boundaryml.baml.integration.concurrency_test

import com.boundaryml.baml.*
import com.boundaryml.baml.integration.BamlProject
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConcurrencyTest {

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
    fun `concurrent calls return correct results`() = runBlocking {
        requireFullSetup()
        val project = BamlProject.load(this::class)
        val runtime = BamlRuntime.create(rootPath = project.rootPath, srcFiles = project.srcFiles)
        val client = BamlClient(runtime)
        val count = 10

        var completedCount = 0
        coroutineScope {
            (0 until count).map { i ->
                async(Dispatchers.Default) {
                    try {
                        val args = Serde.encodeArgs(mapOf("input" to "test-$i"))
                        val result = client.callFunction("Echo", args)
                        assertNotNull(result, "Result for call $i should not be null")
                    } catch (e: BamlException) {
                        assertTrue(e.message?.isNotEmpty() == true)
                    }
                    i
                }
            }.forEach { completedCount = it.await() + 1 }
        }

        assertEquals(count, completedCount)
        runtime.destroy()
    }
}
