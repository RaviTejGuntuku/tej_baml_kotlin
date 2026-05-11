package com.boundaryml.baml.codegen

import baml_client.registerBamlTypes
import baml_client.types.Address
import baml_client.types.LineItem
import baml_client.types.Person
import baml_client.types.Receipt
import baml_client.types.ShoppingPlan
import com.boundaryml.baml.*
import com.boundaryml.baml.cffi.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Tests that real code-generator output (in baml_client.types) correctly
 * encodes and decodes through the SDK's Serde layer.
 */
class GeneratedClassTest {

    private fun typeMap(): BamlTypeMap {
        val tm = BamlTypeMap()
        registerBamlTypes(tm)
        return tm
    }

    // --- Person ---

    @Test
    fun `encode person produces correct HostClassValue`() {
        val person = Person("Alice", 30, "alice@test.com")
        val encoded = person.encode()
        assertEquals(HostValue.ValueCase.CLASS_VALUE, encoded.valueCase)
        assertEquals("Person", encoded.classValue.name)
        assertEquals(3, encoded.classValue.fieldsCount)
    }

    @Test
    fun `encode person with null email includes null field`() {
        val person = Person("Bob", 25, null)
        val encoded = person.encode()
        // Generated code includes all fields (null encoded as default HostValue)
        assertEquals(3, encoded.classValue.fieldsCount)
    }

    @Test
    fun `decode person from CFFIValueHolder`() {
        val tm = typeMap()
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

        val result = Serde.decodeValue(holder, tm)
        assertIs<Person>(result)
        assertEquals("Charlie", result.name)
        assertEquals(35L, result.age)
        assertEquals("charlie@test.com", result.email)
    }

    @Test
    fun `decode person with null optional field`() {
        val tm = typeMap()
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

        val result = Serde.decodeValue(holder, tm)
        assertIs<Person>(result)
        assertEquals("Dave", result.name)
        assertEquals(40L, result.age)
        assertNull(result.email)
    }

    // --- Address ---

    @Test
    fun `address encode and decode roundtrip`() {
        val tm = typeMap()
        val address = Address("123 Main St", "NYC", "10001")
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
        val decoded = Serde.decodeValue(holder, tm)
        assertIs<Address>(decoded)
        assertEquals(address.street, decoded.street)
        assertEquals(address.city, decoded.city)
        assertEquals(address.zip, decoded.zip)
    }

    // --- Receipt (class with list field) ---

    @Test
    fun `receipt with list field encodes and decodes`() {
        val tm = typeMap()
        val receipt = Receipt("Store A", listOf("milk", "bread"), 12.50)
        val encoded = receipt.encode()

        assertEquals("Receipt", encoded.classValue.name)

        val holder = cFFIValueHolder {
            classValue = cFFIValueClass {
                name = cFFITypeName {
                    namespace = CFFITypeNamespace.TYPES
                    this.name = "Receipt"
                }
                fields.add(cFFIMapEntry {
                    key = "store"
                    value = cFFIValueHolder { stringValue = "Store A" }
                })
                fields.add(cFFIMapEntry {
                    key = "items"
                    value = cFFIValueHolder {
                        listValue = cFFIValueList {
                            items.add(cFFIValueHolder { stringValue = "milk" })
                            items.add(cFFIValueHolder { stringValue = "bread" })
                        }
                    }
                })
                fields.add(cFFIMapEntry {
                    key = "total"
                    value = cFFIValueHolder { floatValue = 12.50 }
                })
            }
        }
        val decoded = Serde.decodeValue(holder, tm)
        assertIs<Receipt>(decoded)
        assertEquals("Store A", decoded.store)
        assertEquals(listOf("milk", "bread"), decoded.items)
        assertEquals(12.50, decoded.total)
    }

    @Test
    fun `shopping plan decodes nested class list`() {
        val tm = typeMap()
        val holder = cFFIValueHolder {
            classValue = cFFIValueClass {
                name = cFFITypeName {
                    namespace = CFFITypeNamespace.TYPES
                    this.name = "ShoppingPlan"
                }
                fields.add(cFFIMapEntry {
                    key = "items"
                    value = cFFIValueHolder {
                        listValue = cFFIValueList {
                            items.add(cFFIValueHolder {
                                mapValue = cFFIValueMap {
                                    entries.add(cFFIMapEntry {
                                        key = "sku"
                                        value = cFFIValueHolder { stringValue = "eggs" }
                                    })
                                    entries.add(cFFIMapEntry {
                                        key = "quantity"
                                        value = cFFIValueHolder { intValue = 12L }
                                    })
                                }
                            })
                            items.add(cFFIValueHolder {
                                mapValue = cFFIValueMap {
                                    entries.add(cFFIMapEntry {
                                        key = "sku"
                                        value = cFFIValueHolder { stringValue = "spinach" }
                                    })
                                    entries.add(cFFIMapEntry {
                                        key = "quantity"
                                        value = cFFIValueHolder { intValue = 1L }
                                    })
                                }
                            })
                        }
                    }
                })
                fields.add(cFFIMapEntry {
                    key = "note"
                    value = cFFIValueHolder { stringValue = "buy fresh" }
                })
            }
        }

        val decoded = Serde.decodeValue(holder, tm)
        assertIs<ShoppingPlan>(decoded)
        assertEquals("buy fresh", decoded.note)
        assertEquals(2, decoded.items.size)
        assertIs<LineItem>(decoded.items[0])
        assertEquals("eggs", decoded.items[0].sku)
        assertEquals(12L, decoded.items[0].quantity)
    }
}
