package com.boundaryml.baml.integration.runtime_test

import com.boundaryml.baml.BamlFfi
import com.boundaryml.baml.BamlRuntime
import com.boundaryml.baml.integration.BamlProject
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
        assertTrue(version.contains("."), "Version '$version' should be semver-like")
    }

    @Test
    fun `create runtime with valid BAML files`() {
        requireFfi()
        val project = BamlProject.load(this::class)
        val runtime = BamlRuntime.create(rootPath = project.rootPath, srcFiles = project.srcFiles)
        assertNotNull(runtime)
        runtime.destroy()
    }

    @Test
    fun `create runtime with empty files`() {
        requireFfi()
        val runtime = BamlRuntime.create(rootPath = ".", srcFiles = emptyMap())
        assertNotNull(runtime)
        runtime.destroy()
    }

    @Test
    fun `destroy runtime does not crash`() {
        requireFfi()
        val runtime = BamlRuntime.create(rootPath = ".", srcFiles = emptyMap())
        runtime.destroy()
    }
}
