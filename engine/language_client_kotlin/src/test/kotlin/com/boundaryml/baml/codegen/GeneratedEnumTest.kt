package com.boundaryml.baml.codegen

import com.boundaryml.baml.*
import com.boundaryml.baml.cffi.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tests the patterns that a future Kotlin code generator would produce for BAML enums.
 */

// --- Simulated generated code ---

enum class SentimentGenerated : BamlSerializable {
    POSITIVE,
    NEGATIVE,
    NEUTRAL;

    override fun encode(): HostValue = Serde.encodeEnum("Sentiment", name)
    override fun bamlTypeName(): String = "Sentiment"
}

// --- Tests ---

class GeneratedEnumTest {

    @Test
    fun `encode enum produces correct HostEnumValue`() {
        val encoded = SentimentGenerated.POSITIVE.encode()
        assertEquals(HostValue.ValueCase.ENUM_VALUE, encoded.valueCase)
        assertEquals("Sentiment", encoded.enumValue.name)
        assertEquals("POSITIVE", encoded.enumValue.value)
    }

    @Test
    fun `encode all enum variants`() {
        for (variant in SentimentGenerated.entries) {
            val encoded = variant.encode()
            assertEquals("Sentiment", encoded.enumValue.name)
            assertEquals(variant.name, encoded.enumValue.value)
        }
    }

    @Test
    fun `decode enum with registered type`() {
        val typeMap = BamlTypeMap()
        typeMap.register("TYPES", "Sentiment", SentimentGenerated::class)

        val holder = cFFIValueHolder {
            enumValue = cFFIValueEnum {
                name = cFFITypeName {
                    namespace = CFFITypeNamespace.TYPES
                    this.name = "Sentiment"
                }
                value = "NEGATIVE"
            }
        }

        val result = Serde.decodeValue(holder, typeMap)
        assertEquals(SentimentGenerated.NEGATIVE, result)
    }

    @Test
    fun `decode unknown enum variant falls back to dynamic`() {
        val typeMap = BamlTypeMap()
        typeMap.register("TYPES", "Sentiment", SentimentGenerated::class)

        val holder = cFFIValueHolder {
            enumValue = cFFIValueEnum {
                name = cFFITypeName {
                    namespace = CFFITypeNamespace.TYPES
                    this.name = "Sentiment"
                }
                value = "VERY_POSITIVE"
                isDynamic = true
            }
        }

        val result = Serde.decodeValue(holder, typeMap)
        assertIs<DynamicBamlEnum>(result)
        assertEquals("VERY_POSITIVE", result.value)
    }

    @Test
    fun `enum as function arg encodes correctly`() {
        val bytes = Serde.encodeArgs(mapOf(
            "sentiment" to SentimentGenerated.NEUTRAL,
            "text" to "hello"
        ))
        val parsed = HostFunctionArguments.parseFrom(bytes)
        assertEquals(2, parsed.kwargsCount)

        val sentimentArg = parsed.getKwargs(0)
        assertEquals("sentiment", sentimentArg.stringKey)
        assertEquals(HostValue.ValueCase.ENUM_VALUE, sentimentArg.value.valueCase)
        assertEquals("NEUTRAL", sentimentArg.value.enumValue.value)
    }
}
