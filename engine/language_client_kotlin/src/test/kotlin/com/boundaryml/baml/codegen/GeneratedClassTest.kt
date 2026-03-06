package com.boundaryml.baml.codegen

import com.boundaryml.baml.*
import com.boundaryml.baml.cffi.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tests the patterns that a future Kotlin code generator would produce for BAML classes.
 * Hand-writes a generated data class to validate the SDK's encode/decode works end-to-end.
 */

// --- Simulated generated code ---

data class PersonGenerated(
    val name: String,
    val age: Int,
    val email: String? = null
) : BamlSerializable {
    override fun encode(): HostValue = Serde.encodeClass(
        "Person",
        buildMap {
            put("name", name)
            put("age", age)
            if (email != null) put("email", email)
        }
    )
    override fun bamlTypeName(): String = "Person"

    companion object : BamlDeserializable<PersonGenerated> {
        override fun decode(fields: Map<String, Any?>, typeMap: BamlTypeMap): PersonGenerated {
            return PersonGenerated(
                name = fields["name"] as String,
                age = (fields["age"] as Long).toInt(),
                email = fields["email"] as? String
            )
        }
    }
}

data class AddressGenerated(
    val street: String,
    val city: String,
    val zip: String
) : BamlSerializable {
    override fun encode(): HostValue = Serde.encodeClass(
        "Address",
        mapOf("street" to street, "city" to city, "zip" to zip)
    )
    override fun bamlTypeName(): String = "Address"

    companion object : BamlDeserializable<AddressGenerated> {
        override fun decode(fields: Map<String, Any?>, typeMap: BamlTypeMap): AddressGenerated {
            return AddressGenerated(
                street = fields["street"] as String,
                city = fields["city"] as String,
                zip = fields["zip"] as String
            )
        }
    }
}

// --- Tests ---

class GeneratedClassTest {

    @Test
    fun `encode person produces correct HostClassValue`() {
        val person = PersonGenerated("Alice", 30, "alice@test.com")
        val encoded = person.encode()
        assertEquals(HostValue.ValueCase.CLASS_VALUE, encoded.valueCase)
        assertEquals("Person", encoded.classValue.name)
        assertEquals(3, encoded.classValue.fieldsCount)
    }

    @Test
    fun `encode person with null email omits email field`() {
        val person = PersonGenerated("Bob", 25)
        val encoded = person.encode()
        assertEquals(2, encoded.classValue.fieldsCount)
    }

    @Test
    fun `decode person from CFFIValueHolder`() {
        val typeMap = BamlTypeMap()
        typeMap.register("TYPES", "Person", PersonGenerated::class, PersonGenerated)

        val holder = cFFIValueHolder {
            classValue = cFFIValueClass {
                name = cFFITypeName {
                    namespace = CFFITypeNamespace.TYPES
                    this.name = "Person"
                }
                fields.add(cFFIMapEntry {
                    key = "name"
                    value = cFFIValueHolder { stringValue = "Charlie" }
                })
                fields.add(cFFIMapEntry {
                    key = "age"
                    value = cFFIValueHolder { intValue = 35L }
                })
                fields.add(cFFIMapEntry {
                    key = "email"
                    value = cFFIValueHolder { stringValue = "charlie@test.com" }
                })
            }
        }

        val result = Serde.decodeValue(holder, typeMap)
        assertIs<PersonGenerated>(result)
        assertEquals("Charlie", result.name)
        assertEquals(35, result.age)
        assertEquals("charlie@test.com", result.email)
    }

    @Test
    fun `decode person with null optional field`() {
        val typeMap = BamlTypeMap()
        typeMap.register("TYPES", "Person", PersonGenerated::class, PersonGenerated)

        val holder = cFFIValueHolder {
            classValue = cFFIValueClass {
                name = cFFITypeName {
                    namespace = CFFITypeNamespace.TYPES
                    this.name = "Person"
                }
                fields.add(cFFIMapEntry {
                    key = "name"
                    value = cFFIValueHolder { stringValue = "Dave" }
                })
                fields.add(cFFIMapEntry {
                    key = "age"
                    value = cFFIValueHolder { intValue = 40L }
                })
            }
        }

        val result = Serde.decodeValue(holder, typeMap)
        assertIs<PersonGenerated>(result)
        assertEquals("Dave", result.name)
        assertEquals(40, result.age)
        assertEquals(null, result.email)
    }

    @Test
    fun `encode then decode roundtrip`() {
        val typeMap = BamlTypeMap()
        typeMap.register("TYPES", "Person", PersonGenerated::class, PersonGenerated)

        val original = PersonGenerated("Eve", 28, "eve@test.com")
        val encoded = original.encode()

        // Simulate what the engine would return
        val outbound = cFFIValueHolder {
            classValue = cFFIValueClass {
                name = cFFITypeName {
                    namespace = CFFITypeNamespace.TYPES
                    this.name = "Person"
                }
                // Copy fields from encoded
                fields.addAll(encoded.classValue.fieldsList.map { entry ->
                    cFFIMapEntry {
                        key = entry.stringKey
                        value = convertHostToCffi(entry.value)
                    }
                })
            }
        }

        val decoded = Serde.decodeValue(outbound, typeMap)
        assertIs<PersonGenerated>(decoded)
        assertEquals(original.name, decoded.name)
        assertEquals(original.age, decoded.age)
        assertEquals(original.email, decoded.email)
    }

    @Test
    fun `nested class encode and decode`() {
        val typeMap = BamlTypeMap()
        typeMap.register("TYPES", "Address", AddressGenerated::class, AddressGenerated)

        val address = AddressGenerated("123 Main St", "NYC", "10001")
        val encoded = address.encode()

        assertEquals("Address", encoded.classValue.name)
        assertEquals(3, encoded.classValue.fieldsCount)

        val holder = cFFIValueHolder {
            classValue = cFFIValueClass {
                name = cFFITypeName {
                    namespace = CFFITypeNamespace.TYPES
                    this.name = "Address"
                }
                fields.add(cFFIMapEntry {
                    key = "street"
                    value = cFFIValueHolder { stringValue = "123 Main St" }
                })
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
        val decoded = Serde.decodeValue(holder, typeMap)
        assertIs<AddressGenerated>(decoded)
        assertEquals(address, decoded)
    }
}

// Helper to convert HostValue to CFFIValueHolder (for test simulation)
private fun convertHostToCffi(hostValue: HostValue): CFFIValueHolder {
    return when (hostValue.valueCase) {
        HostValue.ValueCase.STRING_VALUE -> cFFIValueHolder { stringValue = hostValue.stringValue }
        HostValue.ValueCase.INT_VALUE -> cFFIValueHolder { intValue = hostValue.intValue }
        HostValue.ValueCase.FLOAT_VALUE -> cFFIValueHolder { floatValue = hostValue.floatValue }
        HostValue.ValueCase.BOOL_VALUE -> cFFIValueHolder { boolValue = hostValue.boolValue }
        HostValue.ValueCase.VALUE_NOT_SET -> cFFIValueHolder { nullValue = CFFIValueNull.getDefaultInstance() }
        else -> CFFIValueHolder.getDefaultInstance()
    }
}
