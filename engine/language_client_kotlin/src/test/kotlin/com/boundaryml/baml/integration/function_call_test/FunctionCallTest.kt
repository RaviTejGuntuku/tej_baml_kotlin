package com.boundaryml.baml.integration.function_call_test

import com.boundaryml.baml.*
import com.boundaryml.baml.integration.BamlProject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class FunctionCallTest {

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
                project = BamlProject.load(FunctionCallTest::class)
            }
        }
    }

    private fun requireFullSetup() {
        assumeTrue(ffiAvailable, "bridge_cffi dylib not available")
        assumeTrue(hasApiKey, "OPENROUTER_API_KEY not set")
    }

    private suspend fun assertCallCompletes(client: BamlClient, functionName: String, args: ByteArray) {
        val result = client.callFunction(functionName, args)
        assertNotNull(result, "$functionName should return a non-null result")
    }

    @Test
    fun `call function returning string`() = runBlocking {
        requireFullSetup()
        val project = Companion.project
        val runtime = BamlRuntime.create(rootPath = project.rootPath, srcFiles = project.srcFiles)
        val client = BamlClient(runtime)

        assertCallCompletes(client, "GetGreeting", Serde.encodeArgs(mapOf("name" to "World")))
        runtime.destroy()
    }

    @Test
    fun `call function with multiple args`() = runBlocking {
        requireFullSetup()
        val project = Companion.project
        val runtime = BamlRuntime.create(rootPath = project.rootPath, srcFiles = project.srcFiles)
        val client = BamlClient(runtime)

        assertCallCompletes(client, "Translate", Serde.encodeArgs(mapOf("text" to "Hello", "language" to "Spanish")))
        runtime.destroy()
    }
}
