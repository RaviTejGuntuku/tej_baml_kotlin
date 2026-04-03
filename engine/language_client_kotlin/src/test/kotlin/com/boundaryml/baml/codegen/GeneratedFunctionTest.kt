package com.boundaryml.baml.codegen

import baml_client.types.Person
import baml_client.types.Sentiment
import com.boundaryml.baml.*
import com.boundaryml.baml.cffi.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests that real code-generator types encode correctly as function arguments.
 * Validates the Serde.encodeArgs contract that generated function wrappers depend on.
 */
class GeneratedFunctionTest {

    @Test
    fun `function args encode string correctly`() {
        val bytes = Serde.encodeArgs(mapOf("input" to "John is 30 years old"))
        val parsed = HostFunctionArguments.parseFrom(bytes)
        assertEquals(1, parsed.kwargsCount)
        assertEquals("input", parsed.getKwargs(0).stringKey)
        assertEquals("John is 30 years old", parsed.getKwargs(0).value.stringValue)
    }

    @Test
    fun `function args encode generated class argument`() {
        val person = Person("Alice", 30, "alice@test.com")
        val bytes = Serde.encodeArgs(mapOf("person" to person))
        val parsed = HostFunctionArguments.parseFrom(bytes)
        assertEquals(1, parsed.kwargsCount)
        assertEquals("person", parsed.getKwargs(0).stringKey)
        val personValue = parsed.getKwargs(0).value
        assertEquals(HostValue.ValueCase.CLASS_VALUE, personValue.valueCase)
        assertEquals("Person", personValue.classValue.name)
    }

    @Test
    fun `function args encode generated enum argument`() {
        val bytes = Serde.encodeArgs(mapOf(
            "sentiment" to Sentiment.NEUTRAL,
            "text" to "hello"
        ))
        val parsed = HostFunctionArguments.parseFrom(bytes)
        assertEquals(2, parsed.kwargsCount)
        assertEquals(HostValue.ValueCase.ENUM_VALUE, parsed.getKwargs(0).value.valueCase)
        assertEquals("NEUTRAL", parsed.getKwargs(0).value.enumValue.value)
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

    @Test
    fun `multiple args encode in order`() {
        val bytes = Serde.encodeArgs(mapOf(
            "name" to "Alice",
            "greeting" to "Hello"
        ))
        val parsed = HostFunctionArguments.parseFrom(bytes)
        assertEquals(2, parsed.kwargsCount)
        assertEquals("name", parsed.getKwargs(0).stringKey)
        assertEquals("greeting", parsed.getKwargs(1).stringKey)
    }
}
