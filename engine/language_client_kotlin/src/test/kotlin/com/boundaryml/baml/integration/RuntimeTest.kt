package com.boundaryml.baml.integration

import com.boundaryml.baml.BamlFfi
import com.boundaryml.baml.BamlRuntime
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests that require the actual bridge_cffi dylib.
 * These tests are skipped if the library is not available.
 *
 * Build the dylib first: cargo build -p bridge_cffi
 * Set BAML_LIBRARY_PATH to the built library path.
 *
 * BAML sources: src/test/resources/baml/fake_client.baml, extract_function.baml
 */
class RuntimeTest {

    companion object {
        private var ffiAvailable = false

        @BeforeAll
        @JvmStatic
        fun loadFfi() {
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
    fun `version returns valid semver string`() {
        requireFfi()
        val version = BamlRuntime.version()
        assertNotNull(version)
        assertTrue(version.isNotEmpty(), "Version should not be empty")
        // Basic semver check: should contain at least one dot
        assertTrue(version.contains("."), "Version '$version' should be semver-like")
    }

    @Test
    fun `create runtime with valid BAML files`() {
        requireFfi()
        val srcFiles = BamlTestResources.load("fake_client.baml", "extract_function.baml")

        val runtime = BamlRuntime.create(rootPath = ".", srcFiles = srcFiles)
        assertNotNull(runtime)
        runtime.destroy()
    }

    @Test
    fun `create runtime with empty files`() {
        requireFfi()
        val runtime = BamlRuntime.create(
            rootPath = ".",
            srcFiles = emptyMap(),
        )
        assertNotNull(runtime)
        runtime.destroy()
    }

    @Test
    fun `destroy runtime does not crash`() {
        requireFfi()
        val runtime = BamlRuntime.create(
            rootPath = ".",
            srcFiles = emptyMap(),
        )
        runtime.destroy()
        // No exception = success
    }
}
