package com.boundaryml.baml.integration

import java.io.File

/**
 * Loads a BAML project from the source tree for integration tests.
 *
 * Each integration test is a self-contained BAML project directory:
 * ```
 * structured_output_test/
 * ├── baml_src/
 * │   ├── clients.baml
 * │   ├── types.baml
 * │   └── functions.baml
 * └── StructuredOutputTest.kt
 * ```
 *
 * Usage:
 * ```
 * val project = BamlProject.load(this::class)
 * val runtime = BamlRuntime.create(rootPath = project.rootPath, srcFiles = project.srcFiles)
 * ```
 */
data class BamlProject(
    val rootPath: String,
    val srcFiles: Map<String, String>
) {
    companion object {
        private val INTEGRATION_DIR = File("src/test/kotlin/com/boundaryml/baml/integration")

        /**
         * Load the BAML project for the given test class.
         * Resolves the baml_src/ directory relative to the test's package directory.
         */
        fun load(testClass: kotlin.reflect.KClass<*>): BamlProject {
            val packageName = testClass.java.`package`?.name ?: error("No package for $testClass")
            val dirName = packageName.substringAfterLast(".")
            return loadByName(dirName)
        }

        /**
         * Load a BAML project by directory name.
         */
        fun loadByName(projectName: String): BamlProject {
            val projectDir = INTEGRATION_DIR.resolve(projectName)
            val bamlSrcDir = projectDir.resolve("baml_src")
            require(bamlSrcDir.isDirectory) { "No baml_src/ directory at ${bamlSrcDir.absolutePath}" }

            val srcFiles = bamlSrcDir.listFiles()
                ?.filter { it.extension == "baml" }
                ?.associate { it.name to it.readText() }
                ?: emptyMap()

            return BamlProject(
                rootPath = projectDir.absolutePath,
                srcFiles = srcFiles
            )
        }
    }
}
