package com.boundaryml.baml.unit

import com.boundaryml.baml.*
import com.boundaryml.baml.cffi.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DecodeTest {

    // Primitive decode tests (null, string, int, float, bool) removed —
    // already covered by RoundTripTest.

    @Test
    fun `decode list value`() {
        val holder = cFFIValueHolder {
            listValue = cFFIValueList {
                items.add(cFFIValueHolder { stringValue = "a" })
                items.add(cFFIValueHolder { stringValue = "b" })
                items.add(cFFIValueHolder { intValue = 3L })
            }
        }
        val result = Serde.decodeValue(holder)
        assertIs<List<*>>(result)
        assertEquals(3, result.size)
        assertEquals("a", result[0])
        assertEquals("b", result[1])
        assertEquals(3L, result[2])
    }

    @Test
    fun `decode map value`() {
        val holder = cFFIValueHolder {
            mapValue = cFFIValueMap {
                entries.add(cFFIMapEntry {
                    key = "name"
                    value = cFFIValueHolder { stringValue = "Alice" }
                })
                entries.add(cFFIMapEntry {
                    key = "age"
                    value = cFFIValueHolder { intValue = 30L }
                })
            }
        }
        val result = Serde.decodeValue(holder)
        assertIs<Map<*, *>>(result)
        assertEquals("Alice", result["name"])
        assertEquals(30L, result["age"])
    }

    @Test
    fun `decode class value without registered type`() {
        val holder = cFFIValueHolder {
            classValue = cFFIValueClass {
                name = cFFITypeName {
                    namespace = CFFITypeNamespace.TYPES
                    this.name = "Person"
                }
                fields.add(cFFIMapEntry {
                    key = "name"
                    value = cFFIValueHolder { stringValue = "Bob" }
                })
                fields.add(cFFIMapEntry {
                    key = "age"
                    value = cFFIValueHolder { intValue = 25L }
                })
            }
        }
        val result = Serde.decodeValue(holder)
        assertIs<DynamicBamlClass>(result)
        assertEquals("Person", result.name)
        assertEquals("Bob", result.fields["name"])
        assertEquals(25L, result.fields["age"])
    }

    @Test
    fun `decode class value with registered deserializer`() {
        val typeMap = BamlTypeMap()
        typeMap.register("TYPES", "Person", TestPersonOut::class, TestPersonOut.Companion)

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
            }
        }
        val result = Serde.decodeValue(holder, typeMap)
        assertIs<TestPersonOut>(result)
        assertEquals("Charlie", result.name)
        assertEquals(35, result.age)
    }

    @Test
    fun `decode enum value without registered type`() {
        val holder = cFFIValueHolder {
            enumValue = cFFIValueEnum {
                name = cFFITypeName {
                    namespace = CFFITypeNamespace.TYPES
                    this.name = "Color"
                }
                value = "BLUE"
            }
        }
        val result = Serde.decodeValue(holder)
        assertIs<DynamicBamlEnum>(result)
        assertEquals("Color", result.name)
        assertEquals("BLUE", result.value)
    }

    @Test
    fun `decode enum value with registered type`() {
        val typeMap = BamlTypeMap()
        typeMap.register("TYPES", "Color", TestColorOut::class)

        val holder = cFFIValueHolder {
            enumValue = cFFIValueEnum {
                name = cFFITypeName {
                    namespace = CFFITypeNamespace.TYPES
                    this.name = "Color"
                }
                value = "GREEN"
            }
        }
        val result = Serde.decodeValue(holder, typeMap)
        assertEquals(TestColorOut.GREEN, result)
    }

    @Test
    fun `decode dynamic enum for unknown value`() {
        val typeMap = BamlTypeMap()
        typeMap.register("TYPES", "Color", TestColorOut::class)

        val holder = cFFIValueHolder {
            enumValue = cFFIValueEnum {
                name = cFFITypeName {
                    namespace = CFFITypeNamespace.TYPES
                    this.name = "Color"
                }
                value = "UNKNOWN_VALUE"
                isDynamic = true
            }
        }
        val result = Serde.decodeValue(holder, typeMap)
        // Falls back to DynamicBamlEnum since UNKNOWN_VALUE doesn't match any enum constant
        assertIs<DynamicBamlEnum>(result)
        assertEquals("UNKNOWN_VALUE", result.value)
    }

    @Test
    fun `decode literal string`() {
        val holder = cFFIValueHolder {
            literalValue = cFFIFieldTypeLiteral {
                stringLiteral = cFFILiteralString { value = "exact" }
            }
        }
        val result = Serde.decodeValue(holder)
        assertEquals("exact", result)
    }

    @Test
    fun `decode literal int`() {
        val holder = cFFIValueHolder {
            literalValue = cFFIFieldTypeLiteral {
                intLiteral = cFFILiteralInt { value = 99L }
            }
        }
        val result = Serde.decodeValue(holder)
        assertEquals(99L, result)
    }

    @Test
    fun `decode literal bool`() {
        val holder = cFFIValueHolder {
            literalValue = cFFIFieldTypeLiteral {
                boolLiteral = cFFILiteralBool { value = true }
            }
        }
        val result = Serde.decodeValue(holder)
        assertEquals(true, result)
    }

    @Test
    fun `decode union variant with single pattern`() {
        val holder = cFFIValueHolder {
            unionVariantValue = cFFIValueUnionVariant {
                name = cFFITypeName {
                    namespace = CFFITypeNamespace.TYPES
                    this.name = "StringOrNull"
                }
                isSinglePattern = true
                isOptional = false
                value = cFFIValueHolder { stringValue = "present" }
            }
        }
        val result = Serde.decodeValue(holder)
        assertEquals("present", result)
    }

    @Test
    fun `decode union variant without registered type`() {
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
        val result = Serde.decodeValue(holder)
        assertIs<DynamicBamlUnion>(result)
        assertEquals("StringOrInt", result.name)
        assertEquals("string", result.variantName)
        assertEquals("hello", result.value)
    }

    @Test
    fun `decode checked value`() {
        val holder = cFFIValueHolder {
            checkedValue = cFFIValueChecked {
                name = cFFITypeName {
                    namespace = CFFITypeNamespace.CHECKED_TYPES
                    this.name = "CheckedString"
                }
                value = cFFIValueHolder { stringValue = "valid data" }
                checks.add(cFFICheckValue {
                    this.name = "length_check"
                    expression = "len(value) > 0"
                    status = "succeeded"
                })
                checks.add(cFFICheckValue {
                    this.name = "format_check"
                    expression = "matches(value, '[a-z]+')"
                    status = "failed"
                })
            }
        }
        val result = Serde.decodeValue(holder)
        assertIs<Checked<*>>(result)
        assertEquals("valid data", result.value)
        assertEquals(2, result.checks.size)
        assertEquals("succeeded", result.checks["length_check"]?.status)
        assertEquals("failed", result.checks["format_check"]?.status)
    }

    @Test
    fun `decode streaming state PENDING`() {
        val holder = cFFIValueHolder {
            streamingStateValue = cFFIValueStreamingState {
                state = CFFIStreamState.PENDING
                value = CFFIValueHolder.getDefaultInstance()
                name = cFFITypeName {
                    namespace = CFFITypeNamespace.STREAM_STATE_TYPES
                    this.name = "MyStream"
                }
            }
        }
        val result = Serde.decodeValue(holder)
        assertIs<StreamState.Pending>(result)
    }

    @Test
    fun `decode streaming state STARTED`() {
        val holder = cFFIValueHolder {
            streamingStateValue = cFFIValueStreamingState {
                state = CFFIStreamState.STARTED
                value = cFFIValueHolder { stringValue = "partial data" }
                name = cFFITypeName {
                    namespace = CFFITypeNamespace.STREAM_STATE_TYPES
                    this.name = "MyStream"
                }
            }
        }
        val result = Serde.decodeValue(holder)
        assertIs<StreamState.Started<*>>(result)
        assertEquals("partial data", result.value)
    }

    @Test
    fun `decode streaming state DONE`() {
        val holder = cFFIValueHolder {
            streamingStateValue = cFFIValueStreamingState {
                state = CFFIStreamState.DONE
                value = cFFIValueHolder { stringValue = "final data" }
                name = cFFITypeName {
                    namespace = CFFITypeNamespace.STREAM_STATE_TYPES
                    this.name = "MyStream"
                }
            }
        }
        val result = Serde.decodeValue(holder)
        assertIs<StreamState.Done<*>>(result)
        assertEquals("final data", result.value)
    }

    @Test
    fun `decode nested class with list field`() {
        val holder = cFFIValueHolder {
            classValue = cFFIValueClass {
                name = cFFITypeName {
                    namespace = CFFITypeNamespace.TYPES
                    this.name = "Team"
                }
                fields.add(cFFIMapEntry {
                    key = "members"
                    value = cFFIValueHolder {
                        listValue = cFFIValueList {
                            items.add(cFFIValueHolder {
                                classValue = cFFIValueClass {
                                    name = cFFITypeName {
                                        namespace = CFFITypeNamespace.TYPES
                                        this.name = "Person"
                                    }
                                    fields.add(cFFIMapEntry {
                                        key = "name"
                                        value = cFFIValueHolder { stringValue = "Alice" }
                                    })
                                }
                            })
                        }
                    }
                })
            }
        }
        val result = Serde.decodeValue(holder)
        assertIs<DynamicBamlClass>(result)
        assertEquals("Team", result.name)
        val members = result.fields["members"]
        assertIs<List<*>>(members)
        assertEquals(1, members.size)
        val member = members[0]
        assertIs<DynamicBamlClass>(member)
        assertEquals("Alice", member.fields["name"])
    }

    @Test
    fun `decode map of classes`() {
        val holder = cFFIValueHolder {
            mapValue = cFFIValueMap {
                entries.add(cFFIMapEntry {
                    key = "user1"
                    value = cFFIValueHolder {
                        classValue = cFFIValueClass {
                            name = cFFITypeName {
                                namespace = CFFITypeNamespace.TYPES
                                this.name = "User"
                            }
                            fields.add(cFFIMapEntry {
                                key = "email"
                                value = cFFIValueHolder { stringValue = "user1@test.com" }
                            })
                        }
                    }
                })
            }
        }
        val result = Serde.decodeValue(holder)
        assertIs<Map<*, *>>(result)
        val user1 = result["user1"]
        assertIs<DynamicBamlClass>(user1)
        assertEquals("user1@test.com", user1.fields["email"])
    }

}

// Test types for decode

data class TestPersonOut(val name: String, val age: Int) {
    companion object : BamlDeserializable<TestPersonOut> {
        override fun decode(fields: Map<String, Any?>, typeMap: BamlTypeMap): TestPersonOut {
            return TestPersonOut(
                name = fields["name"] as String,
                age = (fields["age"] as Long).toInt()
            )
        }
    }
}

enum class TestColorOut {
    RED, GREEN, BLUE
}
