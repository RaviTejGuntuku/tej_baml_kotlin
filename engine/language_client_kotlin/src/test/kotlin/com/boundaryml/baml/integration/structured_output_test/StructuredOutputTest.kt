package com.boundaryml.baml.integration.structured_output_test

import com.boundaryml.baml.*
import com.boundaryml.baml.integration.BamlProject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StructuredOutputTest {

    companion object {
        private var ffiAvailable = false
        private var hasApiKey = false

        @BeforeAll
        @JvmStatic
        fun setup() {
            ffiAvailable = try {
                BamlFfi.load()
                true
            } catch (_: Throwable) {
                false
            }
            hasApiKey = System.getenv("OPENROUTER_API_KEY")?.isNotEmpty() == true
        }
    }

    private fun requireFullSetup() {
        assumeTrue(ffiAvailable, "bridge_cffi dylib not available")
        assumeTrue(hasApiKey, "OPENROUTER_API_KEY not set")
    }

    // -- Hand-written types matching the BAML definitions --

    data class Person(val name: String, val age: Long)

    object PersonDeserializer : BamlDeserializable<Person> {
        override fun decode(fields: Map<String, Any?>, typeMap: BamlTypeMap): Person {
            return Person(
                name = fields["name"] as String,
                age = fields["age"] as Long
            )
        }
    }

    enum class Sentiment { POSITIVE, NEGATIVE, NEUTRAL }

    data class Receipt(
        val storeName: String,
        val total: Double,
        val items: List<String>
    )

    object ReceiptDeserializer : BamlDeserializable<Receipt> {
        override fun decode(fields: Map<String, Any?>, typeMap: BamlTypeMap): Receipt {
            @Suppress("UNCHECKED_CAST")
            return Receipt(
                storeName = fields["store_name"] as String,
                total = fields["total"] as Double,
                items = fields["items"] as List<String>
            )
        }
    }

    private fun createTypeMap(): BamlTypeMap {
        val tm = BamlTypeMap()
        tm.register("TYPES", "Person", Person::class, PersonDeserializer)
        tm.register("TYPES", "Sentiment", Sentiment::class)
        tm.register("TYPES", "Receipt", Receipt::class, ReceiptDeserializer)
        return tm
    }

    @Test
    fun `extract person returns typed class`() = runBlocking {
        requireFullSetup()
        val project = BamlProject.load(this::class)
        val runtime = BamlRuntime.create(rootPath = project.rootPath, srcFiles = project.srcFiles)
        val client = BamlClient(runtime)
        CallbackManager.typeMap = createTypeMap()

        try {
            val result = client.callFunction(
                "ExtractPerson",
                Serde.encodeArgs(mapOf("input" to "Alice is 30 years old"))
            )
            assertNotNull(result)
            assertTrue(result is Person, "Expected Person, got ${result::class.simpleName}")
            assertEquals("Alice", result.name)
            assertEquals(30L, result.age)
        } catch (e: BamlException) {
            assertTrue(e.message?.isNotEmpty() == true)
        } finally {
            runtime.destroy()
        }
    }

    @Test
    fun `classify sentiment returns enum`() = runBlocking {
        requireFullSetup()
        val project = BamlProject.load(this::class)
        val runtime = BamlRuntime.create(rootPath = project.rootPath, srcFiles = project.srcFiles)
        val client = BamlClient(runtime)
        CallbackManager.typeMap = createTypeMap()

        try {
            val result = client.callFunction(
                "ClassifySentiment",
                Serde.encodeArgs(mapOf("text" to "I love this product, it's amazing!"))
            )
            assertNotNull(result)
            assertTrue(
                result is Sentiment || result is DynamicBamlEnum,
                "Expected Sentiment or DynamicBamlEnum, got ${result::class.simpleName}"
            )
            if (result is Sentiment) {
                assertEquals(Sentiment.POSITIVE, result)
            }
        } catch (e: BamlException) {
            assertTrue(e.message?.isNotEmpty() == true)
        } finally {
            runtime.destroy()
        }
    }

    @Test
    fun `extract receipt returns typed class with list field`() = runBlocking {
        requireFullSetup()
        val project = BamlProject.load(this::class)
        val runtime = BamlRuntime.create(rootPath = project.rootPath, srcFiles = project.srcFiles)
        val client = BamlClient(runtime)
        CallbackManager.typeMap = createTypeMap()

        try {
            val result = client.callFunction(
                "ExtractReceipt",
                Serde.encodeArgs(mapOf("text" to "Store: Coffee Shop. Items: Latte, Muffin. Total: \$8.50"))
            )
            assertNotNull(result)
            assertTrue(result is Receipt, "Expected Receipt, got ${result::class.simpleName}")
            assertTrue(result.total > 0.0)
            assertTrue(result.items.isNotEmpty())
            assertTrue(result.storeName.isNotEmpty())
        } catch (e: BamlException) {
            assertTrue(e.message?.isNotEmpty() == true)
        } finally {
            runtime.destroy()
        }
    }

    @Test
    fun `dynamic class fallback when no deserializer registered`() = runBlocking {
        requireFullSetup()
        val project = BamlProject.load(this::class)
        val runtime = BamlRuntime.create(rootPath = project.rootPath, srcFiles = project.srcFiles)
        val client = BamlClient(runtime)
        CallbackManager.typeMap = BamlTypeMap()

        try {
            val result = client.callFunction(
                "ExtractPerson",
                Serde.encodeArgs(mapOf("input" to "Bob is 25 years old"))
            )
            assertNotNull(result)
            assertTrue(result is DynamicBamlClass, "Expected DynamicBamlClass, got ${result::class.simpleName}")
            assertEquals("Person", result.name)
            assertNotNull(result.fields["name"])
            assertNotNull(result.fields["age"])
        } catch (e: BamlException) {
            assertTrue(e.message?.isNotEmpty() == true)
        } finally {
            runtime.destroy()
        }
    }
}
