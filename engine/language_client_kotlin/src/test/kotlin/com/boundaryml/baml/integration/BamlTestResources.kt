package com.boundaryml.baml.integration

/**
 * Loads `.baml` files from `src/test/resources/baml/` for integration tests.
 *
 * Tests compose the files they need — e.g., an LLM test loads the client file
 * plus the function file(s), while a runtime lifecycle test loads the fake client.
 *
 * Usage:
 * ```
 * val srcFiles = BamlTestResources.load("openrouter_client.baml", "greeting_functions.baml")
 * val runtime = BamlRuntime.create(rootPath = ".", srcFiles = srcFiles)
 * ```
 */
object BamlTestResources {

    /**
     * Load one or more `.baml` files from the test resources directory.
     * Returns a map of filename → content suitable for [BamlRuntime.create].
     */
    fun load(vararg filenames: String): Map<String, String> {
        return filenames.associate { filename ->
            val content = javaClass.classLoader
                .getResource("baml/$filename")
                ?.readText()
                ?: error("Test resource not found: baml/$filename")
            filename to content
        }
    }
}
