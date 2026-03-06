package com.boundaryml.baml.unit

import com.boundaryml.baml.*
import com.boundaryml.baml.cffi.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Round-trip tests: encode a Kotlin value to HostValue protobuf, then
 * construct the equivalent CFFIValueHolder and decode it back.
 * Verifies that the encoding and decoding logic are consistent.
 */
class RoundTripTest {

    @Test
    fun `roundtrip null`() {
        // Encode null → HostValue (value not set)
        val encoded = Serde.encodeValue(null)
        assertEquals(HostValue.ValueCase.VALUE_NOT_SET, encoded.valueCase)

        // Decode CFFIValueHolder null → null
        val outbound = cFFIValueHolder {
            nullValue = CFFIValueNull.getDefaultInstance()
        }
        val decoded = Serde.decodeValue(outbound)
        assertNull(decoded)
    }

    @Test
    fun `roundtrip string`() {
        val original = "hello world"
        val encoded = Serde.encodeValue(original)
        assertEquals(original, encoded.stringValue)

        val outbound = cFFIValueHolder { stringValue = original }
        val decoded = Serde.decodeValue(outbound)
        assertEquals(original, decoded)
    }

    @Test
    fun `roundtrip empty string`() {
        val original = ""
        val encoded = Serde.encodeValue(original)
        assertEquals(original, encoded.stringValue)

        val outbound = cFFIValueHolder { stringValue = original }
        val decoded = Serde.decodeValue(outbound)
        assertEquals(original, decoded)
    }

    @Test
    fun `roundtrip int`() {
        val original = 42
        val encoded = Serde.encodeValue(original)
        assertEquals(42L, encoded.intValue)

        val outbound = cFFIValueHolder { intValue = 42L }
        val decoded = Serde.decodeValue(outbound)
        assertEquals(42L, decoded)
    }

    @Test
    fun `roundtrip long max`() {
        val original = Long.MAX_VALUE
        val encoded = Serde.encodeValue(original)
        assertEquals(Long.MAX_VALUE, encoded.intValue)

        val outbound = cFFIValueHolder { intValue = Long.MAX_VALUE }
        val decoded = Serde.decodeValue(outbound)
        assertEquals(Long.MAX_VALUE, decoded)
    }

    @Test
    fun `roundtrip long min`() {
        val original = Long.MIN_VALUE
        val encoded = Serde.encodeValue(original)
        assertEquals(Long.MIN_VALUE, encoded.intValue)

        val outbound = cFFIValueHolder { intValue = Long.MIN_VALUE }
        val decoded = Serde.decodeValue(outbound)
        assertEquals(Long.MIN_VALUE, decoded)
    }

    @Test
    fun `roundtrip double`() {
        val original = 3.14159
        val encoded = Serde.encodeValue(original)
        assertEquals(original, encoded.floatValue)

        val outbound = cFFIValueHolder { floatValue = original }
        val decoded = Serde.decodeValue(outbound)
        assertEquals(original, decoded)
    }

    @Test
    fun `roundtrip boolean`() {
        for (original in listOf(true, false)) {
            val encoded = Serde.encodeValue(original)
            assertEquals(original, encoded.boolValue)

            val outbound = cFFIValueHolder { boolValue = original }
            val decoded = Serde.decodeValue(outbound)
            assertEquals(original, decoded)
        }
    }

    @Test
    fun `roundtrip list of primitives`() {
        val original = listOf("a", "b", "c")
        val encoded = Serde.encodeValue(original)
        assertEquals(3, encoded.listValue.valuesCount)

        val outbound = cFFIValueHolder {
            listValue = cFFIValueList {
                items.addAll(original.map { cFFIValueHolder { stringValue = it } })
            }
        }
        val decoded = Serde.decodeValue(outbound)
        assertEquals(original, decoded)
    }

    @Test
    fun `roundtrip empty list`() {
        val original = emptyList<Any>()
        val encoded = Serde.encodeValue(original)
        assertEquals(0, encoded.listValue.valuesCount)

        val outbound = cFFIValueHolder {
            listValue = cFFIValueList {}
        }
        val decoded = Serde.decodeValue(outbound)
        assertIs<List<*>>(decoded)
        assertEquals(0, decoded.size)
    }

    @Test
    fun `roundtrip map`() {
        val original = mapOf("key1" to "value1", "key2" to "value2")
        val encoded = Serde.encodeValue(original)
        assertEquals(2, encoded.mapValue.entriesCount)

        val outbound = cFFIValueHolder {
            mapValue = cFFIValueMap {
                entries.add(cFFIMapEntry {
                    key = "key1"
                    value = cFFIValueHolder { stringValue = "value1" }
                })
                entries.add(cFFIMapEntry {
                    key = "key2"
                    value = cFFIValueHolder { stringValue = "value2" }
                })
            }
        }
        val decoded = Serde.decodeValue(outbound)
        assertIs<Map<*, *>>(decoded)
        assertEquals("value1", decoded["key1"])
        assertEquals("value2", decoded["key2"])
    }

    @Test
    fun `roundtrip empty map`() {
        val original = emptyMap<String, Any>()
        val encoded = Serde.encodeValue(original)
        assertEquals(0, encoded.mapValue.entriesCount)

        val outbound = cFFIValueHolder {
            mapValue = cFFIValueMap {}
        }
        val decoded = Serde.decodeValue(outbound)
        assertIs<Map<*, *>>(decoded)
        assertEquals(0, decoded.size)
    }

    @Test
    fun `roundtrip nested class`() {
        val encoded = Serde.encodeClass("Address", mapOf("city" to "NYC", "zip" to "10001"))
        assertEquals("Address", encoded.classValue.name)

        val outbound = cFFIValueHolder {
            classValue = cFFIValueClass {
                name = cFFITypeName {
                    namespace = CFFITypeNamespace.TYPES
                    this.name = "Address"
                }
                fields.add(cFFIMapEntry {
                    key = "city"
                    value = cFFIValueHolder { stringValue = "NYC" }
                })
                fields.add(cFFIMapEntry {
                    key = "zip"
                    value = cFFIValueHolder { stringValue = "10001" }
                })
            }
        }
        val decoded = Serde.decodeValue(outbound)
        assertIs<DynamicBamlClass>(decoded)
        assertEquals("Address", decoded.name)
        assertEquals("NYC", decoded.fields["city"])
        assertEquals("10001", decoded.fields["zip"])
    }

    @Test
    fun `roundtrip enum`() {
        val encoded = Serde.encodeEnum("Status", "ACTIVE")
        assertEquals("Status", encoded.enumValue.name)
        assertEquals("ACTIVE", encoded.enumValue.value)

        val outbound = cFFIValueHolder {
            enumValue = cFFIValueEnum {
                name = cFFITypeName {
                    namespace = CFFITypeNamespace.TYPES
                    this.name = "Status"
                }
                value = "ACTIVE"
            }
        }
        val decoded = Serde.decodeValue(outbound)
        assertIs<DynamicBamlEnum>(decoded)
        assertEquals("Status", decoded.name)
        assertEquals("ACTIVE", decoded.value)
    }

    @Test
    fun `roundtrip list with null elements`() {
        val encoded = Serde.encodeValue(listOf("a", null, "c"))
        assertEquals(3, encoded.listValue.valuesCount)
        assertEquals(HostValue.ValueCase.VALUE_NOT_SET, encoded.listValue.getValues(1).valueCase)

        val outbound = cFFIValueHolder {
            listValue = cFFIValueList {
                items.add(cFFIValueHolder { stringValue = "a" })
                items.add(cFFIValueHolder { nullValue = CFFIValueNull.getDefaultInstance() })
                items.add(cFFIValueHolder { stringValue = "c" })
            }
        }
        val decoded = Serde.decodeValue(outbound)
        assertIs<List<*>>(decoded)
        assertEquals("a", decoded[0])
        assertNull(decoded[1])
        assertEquals("c", decoded[2])
    }
}
