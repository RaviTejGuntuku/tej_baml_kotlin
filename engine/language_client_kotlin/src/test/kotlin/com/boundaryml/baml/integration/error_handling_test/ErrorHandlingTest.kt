package com.boundaryml.baml.integration.error_handling_test

import com.boundaryml.baml.*
import com.boundaryml.baml.integration.BamlProject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
        val project = BamlProject.load(this::class)
        val runtime = BamlRuntime.create(rootPath = project.rootPath, srcFiles = project.srcFiles)
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
        val project = BamlProject.load(this::class)
        val runtime = BamlRuntime.create(rootPath = project.rootPath, srcFiles = project.srcFiles)
        val client = BamlClient(runtime)
        val badArgs = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x01)

        val exception = assertFailsWith<Exception> {
            client.callFunction("Echo", badArgs)
        }
        assertTrue(exception.message?.isNotEmpty() == true)
        runtime.destroy()
    }
}
