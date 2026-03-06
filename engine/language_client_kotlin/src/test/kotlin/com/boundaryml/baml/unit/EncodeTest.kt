package com.boundaryml.baml.unit

import com.boundaryml.baml.*
import com.boundaryml.baml.cffi.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EncodeTest {

    @Test
    fun `encode null produces empty HostValue`() {
        val result = Serde.encodeValue(null)
        assertEquals(HostValue.ValueCase.VALUE_NOT_SET, result.valueCase)
    }

    @Test
    fun `encode string`() {
        val result = Serde.encodeValue("hello")
        assertEquals(HostValue.ValueCase.STRING_VALUE, result.valueCase)
        assertEquals("hello", result.stringValue)
    }

    @Test
    fun `encode empty string`() {
        val result = Serde.encodeValue("")
        assertEquals(HostValue.ValueCase.STRING_VALUE, result.valueCase)
        assertEquals("", result.stringValue)
    }

    @Test
    fun `encode int`() {
        val result = Serde.encodeValue(42)
        assertEquals(HostValue.ValueCase.INT_VALUE, result.valueCase)
        assertEquals(42L, result.intValue)
    }

    @Test
    fun `encode long`() {
        val result = Serde.encodeValue(Long.MAX_VALUE)
        assertEquals(HostValue.ValueCase.INT_VALUE, result.valueCase)
        assertEquals(Long.MAX_VALUE, result.intValue)
    }

    @Test
    fun `encode long min value`() {
        val result = Serde.encodeValue(Long.MIN_VALUE)
        assertEquals(HostValue.ValueCase.INT_VALUE, result.valueCase)
        assertEquals(Long.MIN_VALUE, result.intValue)
    }

    @Test
    fun `encode double`() {
        val result = Serde.encodeValue(3.14)
        assertEquals(HostValue.ValueCase.FLOAT_VALUE, result.valueCase)
        assertEquals(3.14, result.floatValue)
    }

    @Test
    fun `encode float`() {
        val result = Serde.encodeValue(2.5f)
        assertEquals(HostValue.ValueCase.FLOAT_VALUE, result.valueCase)
        assertEquals(2.5, result.floatValue)
    }

    @Test
    fun `encode boolean true`() {
        val result = Serde.encodeValue(true)
        assertEquals(HostValue.ValueCase.BOOL_VALUE, result.valueCase)
        assertTrue(result.boolValue)
    }

    @Test
    fun `encode boolean false`() {
        val result = Serde.encodeValue(false)
        assertEquals(HostValue.ValueCase.BOOL_VALUE, result.valueCase)
        assertFalse(result.boolValue)
    }

    @Test
    fun `encode list of strings`() {
        val result = Serde.encodeValue(listOf("a", "b", "c"))
        assertEquals(HostValue.ValueCase.LIST_VALUE, result.valueCase)
        assertEquals(3, result.listValue.valuesCount)
        assertEquals("a", result.listValue.getValues(0).stringValue)
        assertEquals("b", result.listValue.getValues(1).stringValue)
        assertEquals("c", result.listValue.getValues(2).stringValue)
    }

    @Test
    fun `encode empty list`() {
        val result = Serde.encodeValue(emptyList<Any>())
        assertEquals(HostValue.ValueCase.LIST_VALUE, result.valueCase)
        assertEquals(0, result.listValue.valuesCount)
    }

    @Test
    fun `encode nested list`() {
        val result = Serde.encodeValue(listOf(listOf(1, 2), listOf(3, 4)))
        assertEquals(HostValue.ValueCase.LIST_VALUE, result.valueCase)
        assertEquals(2, result.listValue.valuesCount)
        val inner0 = result.listValue.getValues(0)
        assertEquals(HostValue.ValueCase.LIST_VALUE, inner0.valueCase)
        assertEquals(2, inner0.listValue.valuesCount)
    }

    @Test
    fun `encode map of string to int`() {
        val result = Serde.encodeValue(mapOf("x" to 1, "y" to 2))
        assertEquals(HostValue.ValueCase.MAP_VALUE, result.valueCase)
        assertEquals(2, result.mapValue.entriesCount)
        val entry0 = result.mapValue.getEntries(0)
        assertEquals("x", entry0.stringKey)
        assertEquals(1L, entry0.value.intValue)
    }

    @Test
    fun `encode empty map`() {
        val result = Serde.encodeValue(emptyMap<String, Any>())
        assertEquals(HostValue.ValueCase.MAP_VALUE, result.valueCase)
        assertEquals(0, result.mapValue.entriesCount)
    }

    @Test
    fun `encode nested map`() {
        val result = Serde.encodeValue(mapOf("outer" to mapOf("inner" to "value")))
        assertEquals(HostValue.ValueCase.MAP_VALUE, result.valueCase)
        val outerEntry = result.mapValue.getEntries(0)
        assertEquals("outer", outerEntry.stringKey)
        assertEquals(HostValue.ValueCase.MAP_VALUE, outerEntry.value.valueCase)
        val innerEntry = outerEntry.value.mapValue.getEntries(0)
        assertEquals("inner", innerEntry.stringKey)
        assertEquals("value", innerEntry.value.stringValue)
    }

    @Test
    fun `encode class value`() {
        val result = Serde.encodeClass("Person", mapOf("name" to "Alice", "age" to 30))
        assertEquals(HostValue.ValueCase.CLASS_VALUE, result.valueCase)
        assertEquals("Person", result.classValue.name)
        assertEquals(2, result.classValue.fieldsCount)
        assertEquals("name", result.classValue.getFields(0).stringKey)
        assertEquals("Alice", result.classValue.getFields(0).value.stringValue)
        assertEquals("age", result.classValue.getFields(1).stringKey)
        assertEquals(30L, result.classValue.getFields(1).value.intValue)
    }

    @Test
    fun `encode enum value`() {
        val result = Serde.encodeEnum("Color", "RED")
        assertEquals(HostValue.ValueCase.ENUM_VALUE, result.valueCase)
        assertEquals("Color", result.enumValue.name)
        assertEquals("RED", result.enumValue.value)
    }

    @Test
    fun `encode function args with multiple kwargs`() {
        val bytes = Serde.encodeArgs(
            mapOf("name" to "Alice", "age" to 30, "active" to true)
        )
        // Parse back to verify
        val parsed = HostFunctionArguments.parseFrom(bytes)
        assertEquals(3, parsed.kwargsCount)
        assertEquals("name", parsed.getKwargs(0).stringKey)
        assertEquals("Alice", parsed.getKwargs(0).value.stringValue)
        assertEquals("age", parsed.getKwargs(1).stringKey)
        assertEquals(30L, parsed.getKwargs(1).value.intValue)
        assertEquals("active", parsed.getKwargs(2).stringKey)
        assertTrue(parsed.getKwargs(2).value.boolValue)
    }

    @Test
    fun `encode function args with env vars`() {
        val bytes = Serde.encodeArgs(
            mapOf("input" to "test"),
            CallOptions(env = mapOf("API_KEY" to "secret123"))
        )
        val parsed = HostFunctionArguments.parseFrom(bytes)
        assertEquals(1, parsed.envCount)
        assertEquals("API_KEY", parsed.getEnv(0).key)
        assertEquals("secret123", parsed.getEnv(0).value)
    }

    @Test
    fun `encode map entry with int key`() {
        val entry = Serde.encodeMapEntry(42, "value")
        assertEquals(42L, entry.intKey)
        assertEquals("value", entry.value.stringValue)
    }

    @Test
    fun `encode map entry with bool key`() {
        val entry = Serde.encodeMapEntry(true, "yes")
        assertTrue(entry.boolKey)
        assertEquals("yes", entry.value.stringValue)
    }

    @Test
    fun `encode short and byte`() {
        val shortResult = Serde.encodeValue(42.toShort())
        assertEquals(42L, shortResult.intValue)

        val byteResult = Serde.encodeValue(7.toByte())
        assertEquals(7L, byteResult.intValue)
    }

    @Test
    fun `encode BamlSerializable class`() {
        val person = TestPerson("Bob", 25)
        val result = Serde.encodeValue(person)
        assertEquals(HostValue.ValueCase.CLASS_VALUE, result.valueCase)
        assertEquals("Person", result.classValue.name)
    }

    @Test
    fun `encode BamlSerializable enum`() {
        val color = TestColor.RED
        val result = Serde.encodeValue(color)
        assertEquals(HostValue.ValueCase.ENUM_VALUE, result.valueCase)
        assertEquals("Color", result.enumValue.name)
        assertEquals("RED", result.enumValue.value)
    }

    @Test
    fun `encode list with null elements`() {
        val result = Serde.encodeValue(listOf("a", null, "c"))
        assertEquals(3, result.listValue.valuesCount)
        assertEquals(HostValue.ValueCase.VALUE_NOT_SET, result.listValue.getValues(1).valueCase)
    }

    @Test
    fun `encode protobuf bytes are parseable`() {
        val bytes = Serde.encodeArgs(mapOf("x" to listOf(1, 2, 3)))
        val parsed = HostFunctionArguments.parseFrom(bytes)
        assertEquals(1, parsed.kwargsCount)
        assertEquals("x", parsed.getKwargs(0).stringKey)
        val list = parsed.getKwargs(0).value.listValue
        assertEquals(3, list.valuesCount)
    }
}

// Test helpers

private data class TestPerson(val name: String, val age: Int) : BamlSerializable {
    override fun encode(): HostValue = Serde.encodeClass("Person", mapOf("name" to name, "age" to age))
    override fun bamlTypeName(): String = "Person"
}

private enum class TestColor : BamlSerializable {
    RED, GREEN, BLUE;

    override fun encode(): HostValue = Serde.encodeEnum("Color", name)
    override fun bamlTypeName(): String = "Color"
}
