package com.boundaryml.baml.codegen

import com.boundaryml.baml.*
import com.boundaryml.baml.cffi.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Tests the patterns that a future Kotlin code generator would produce for BAML union types.
 * Uses sealed classes to represent union variants.
 */

// --- Simulated generated code ---

sealed class StringOrIntGenerated {
    data class StringVariant(val value: String) : StringOrIntGenerated()
    data class IntVariant(val value: Long) : StringOrIntGenerated()

    companion object : BamlDeserializable<StringOrIntGenerated> {
        override fun decode(fields: Map<String, Any?>, typeMap: BamlTypeMap): StringOrIntGenerated {
            val variantName = fields["variant_name"] as String
            val value = fields["value"]
            return when (variantName) {
                "string" -> StringVariant(value as String)
                "int" -> IntVariant(value as Long)
                else -> throw BamlException("Unknown variant: $variantName")
            }
        }
    }
}

// --- Tests ---

class GeneratedUnionTest {

    @Test
    fun `decode union string variant`() {
        val typeMap = BamlTypeMap()
        typeMap.register("TYPES", "StringOrInt", StringOrIntGenerated::class, StringOrIntGenerated)

        val holder = cFFIValueHolder {
            unionVariantValue = cFFIValueUnionVariant {
                name = cFFITypeName {
                    namespace = CFFITypeNamespace.TYPES
                    this.name = "StringOrInt"
                }
                isSinglePattern = false
                valueOptionName = "string"
                value = cFFIValueHolder { stringValue = "hello" }
            }
        }

        val result = Serde.decodeValue(holder, typeMap)
        assertIs<StringOrIntGenerated.StringVariant>(result)
        assertEquals("hello", result.value)
    }

    @Test
    fun `decode union int variant`() {
        val typeMap = BamlTypeMap()
        typeMap.register("TYPES", "StringOrInt", StringOrIntGenerated::class, StringOrIntGenerated)

        val holder = cFFIValueHolder {
            unionVariantValue = cFFIValueUnionVariant {
                name = cFFITypeName {
                    namespace = CFFITypeNamespace.TYPES
                    this.name = "StringOrInt"
                }
                isSinglePattern = false
                valueOptionName = "int"
                value = cFFIValueHolder { intValue = 42L }
            }
        }

        val result = Serde.decodeValue(holder, typeMap)
        assertIs<StringOrIntGenerated.IntVariant>(result)
        assertEquals(42L, result.value)
    }

    @Test
    fun `decode optional union (single pattern) with value`() {
        val holder = cFFIValueHolder {
            unionVariantValue = cFFIValueUnionVariant {
                name = cFFITypeName {
                    namespace = CFFITypeNamespace.TYPES
                    this.name = "OptionalString"
                }
                isSinglePattern = true
                isOptional = true
                value = cFFIValueHolder { stringValue = "present" }
            }
        }

        val result = Serde.decodeValue(holder)
        assertEquals("present", result)
    }

    @Test
    fun `decode optional union (single pattern) with null`() {
        val holder = cFFIValueHolder {
            unionVariantValue = cFFIValueUnionVariant {
                name = cFFITypeName {
                    namespace = CFFITypeNamespace.TYPES
                    this.name = "OptionalString"
                }
                isSinglePattern = true
                isOptional = true
                value = cFFIValueHolder { nullValue = CFFIValueNull.getDefaultInstance() }
            }
        }

        val result = Serde.decodeValue(holder)
        assertNull(result)
    }

    @Test
    fun `decode union without registered type falls back to dynamic`() {
        val holder = cFFIValueHolder {
            unionVariantValue = cFFIValueUnionVariant {
                name = cFFITypeName {
                    namespace = CFFITypeNamespace.TYPES
                    this.name = "UnknownUnion"
                }
                isSinglePattern = false
                valueOptionName = "some_variant"
                value = cFFIValueHolder { stringValue = "data" }
            }
        }

        val result = Serde.decodeValue(holder)
        assertIs<DynamicBamlUnion>(result)
        assertEquals("UnknownUnion", result.name)
        assertEquals("some_variant", result.variantName)
        assertEquals("data", result.value)
    }
}
