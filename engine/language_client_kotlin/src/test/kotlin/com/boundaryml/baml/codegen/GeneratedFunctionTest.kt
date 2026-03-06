package com.boundaryml.baml.codegen

import com.boundaryml.baml.*
import com.boundaryml.baml.cffi.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Tests the patterns that a future Kotlin code generator would produce for BAML function wrappers.
 * Validates that args are correctly encoded to protobuf format.
 */

// --- Simulated generated code ---

/**
 * A hand-written function wrapper matching what the code generator would produce.
 * In a real generated client, this would call BamlClient.callFunction().
 */
object ExtractPersonArgs {
    fun encodeArgs(input: String, maxResults: Int? = null): ByteArray {
        val kwargs = buildMap<String, Any?> {
            put("input", input)
            if (maxResults != null) put("max_results", maxResults)
        }
        return Serde.encodeArgs(kwargs)
    }
}

object ClassifyTextArgs {
    fun encodeArgs(text: String, categories: List<String>): ByteArray {
        return Serde.encodeArgs(mapOf(
            "text" to text,
            "categories" to categories
        ))
    }
}

object CreatePersonArgs {
    fun encodeArgs(person: PersonGenerated): ByteArray {
        return Serde.encodeArgs(mapOf("person" to person))
    }
}

// --- Tests ---

class GeneratedFunctionTest {

    @Test
    fun `function args encode string correctly`() {
        val bytes = ExtractPersonArgs.encodeArgs("John is 30 years old")
        val parsed = HostFunctionArguments.parseFrom(bytes)
        assertEquals(1, parsed.kwargsCount)
        assertEquals("input", parsed.getKwargs(0).stringKey)
        assertEquals("John is 30 years old", parsed.getKwargs(0).value.stringValue)
    }

    @Test
    fun `function args encode optional int`() {
        val bytes = ExtractPersonArgs.encodeArgs("test", maxResults = 5)
        val parsed = HostFunctionArguments.parseFrom(bytes)
        assertEquals(2, parsed.kwargsCount)
        assertEquals("max_results", parsed.getKwargs(1).stringKey)
        assertEquals(5L, parsed.getKwargs(1).value.intValue)
    }

    @Test
    fun `function args encode without optional`() {
        val bytes = ExtractPersonArgs.encodeArgs("test")
        val parsed = HostFunctionArguments.parseFrom(bytes)
        assertEquals(1, parsed.kwargsCount)
    }

    @Test
    fun `function args encode list of strings`() {
        val bytes = ClassifyTextArgs.encodeArgs("hello", listOf("greeting", "farewell", "question"))
        val parsed = HostFunctionArguments.parseFrom(bytes)
        assertEquals(2, parsed.kwargsCount)
        assertEquals("text", parsed.getKwargs(0).stringKey)
        assertEquals("hello", parsed.getKwargs(0).value.stringValue)
        assertEquals("categories", parsed.getKwargs(1).stringKey)
        val list = parsed.getKwargs(1).value.listValue
        assertEquals(3, list.valuesCount)
        assertEquals("greeting", list.getValues(0).stringValue)
    }

    @Test
    fun `function args encode class argument`() {
        val person = PersonGenerated("Alice", 30, "alice@test.com")
        val bytes = CreatePersonArgs.encodeArgs(person)
        val parsed = HostFunctionArguments.parseFrom(bytes)
        assertEquals(1, parsed.kwargsCount)
        assertEquals("person", parsed.getKwargs(0).stringKey)
        val personValue = parsed.getKwargs(0).value
        assertEquals(HostValue.ValueCase.CLASS_VALUE, personValue.valueCase)
        assertEquals("Person", personValue.classValue.name)
    }

    @Test
    fun `function args with call options`() {
        val bytes = Serde.encodeArgs(
            mapOf("input" to "test"),
            CallOptions(
                env = mapOf("API_KEY" to "secret"),
                tags = mapOf("version" to "v2")
            )
        )
        val parsed = HostFunctionArguments.parseFrom(bytes)
        assertEquals(1, parsed.kwargsCount)
        assertEquals(1, parsed.envCount)
        assertEquals("API_KEY", parsed.getEnv(0).key)
        assertEquals("secret", parsed.getEnv(0).value)
        assertEquals(1, parsed.tagsCount)
        assertEquals("version", parsed.getTags(0).stringKey)
        assertEquals("v2", parsed.getTags(0).value.stringValue)
    }
}
