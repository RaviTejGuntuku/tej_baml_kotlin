package com.boundaryml.baml.codegen

import baml_client.registerBamlTypes
import baml_client.types.Sentiment
import com.boundaryml.baml.*
import com.boundaryml.baml.cffi.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tests that real code-generator enum output (baml_client.types.Sentiment)
 * correctly encodes and decodes through the SDK.
 */
class GeneratedEnumTest {

    private fun typeMap(): BamlTypeMap {
        val tm = BamlTypeMap()
        registerBamlTypes(tm)
        return tm
    }

    @Test
    fun `encode enum produces correct HostEnumValue`() {
        val encoded = Sentiment.POSITIVE.encode()
        assertEquals(HostValue.ValueCase.ENUM_VALUE, encoded.valueCase)
        assertEquals("Sentiment", encoded.enumValue.name)
        assertEquals("POSITIVE", encoded.enumValue.value)
    }

    @Test
    fun `encode all enum variants`() {
        for (variant in Sentiment.entries) {
            val encoded = variant.encode()
            assertEquals("Sentiment", encoded.enumValue.name)
            assertEquals(variant.value, encoded.enumValue.value)
        }
    }

    @Test
    fun `decode enum with registered type`() {
        val tm = typeMap()
        val holder = cFFIValueHolder {
            enumValue = cFFIValueEnum {
                name = cFFITypeName {
                    namespace = CFFITypeNamespace.TYPES
                    this.name = "Sentiment"
                }
                value = "NEGATIVE"
            }
        }
        val result = Serde.decodeValue(holder, tm)
        assertEquals(Sentiment.NEGATIVE, result)
    }

    @Test
    fun `decode unknown enum variant falls back to dynamic`() {
        val tm = typeMap()
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
        val result = Serde.decodeValue(holder, tm)
        assertIs<DynamicBamlEnum>(result)
        assertEquals("VERY_POSITIVE", result.value)
    }

    @Test
    fun `enum as function arg encodes correctly`() {
        val bytes = Serde.encodeArgs(mapOf(
            "sentiment" to Sentiment.NEUTRAL,
            "text" to "hello"
        ))
        val parsed = HostFunctionArguments.parseFrom(bytes)
        assertEquals(2, parsed.kwargsCount)

        val sentimentArg = parsed.getKwargs(0)
        assertEquals("sentiment", sentimentArg.stringKey)
        assertEquals(HostValue.ValueCase.ENUM_VALUE, sentimentArg.value.valueCase)
        assertEquals("NEUTRAL", sentimentArg.value.enumValue.value)
    }

    @Test
    fun `fromString resolves known variant`() {
        assertEquals(Sentiment.POSITIVE, Sentiment.fromString("POSITIVE"))
        assertEquals(Sentiment.NEGATIVE, Sentiment.fromString("NEGATIVE"))
        assertEquals(Sentiment.NEUTRAL, Sentiment.fromString("NEUTRAL"))
    }
}
