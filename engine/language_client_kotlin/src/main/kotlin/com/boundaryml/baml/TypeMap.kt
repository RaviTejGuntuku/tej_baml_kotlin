package com.boundaryml.baml

import kotlin.reflect.KClass

/**
 * Maps BAML type names (namespace-qualified) to Kotlin classes for runtime decoding.
 * Key format: "NAMESPACE.TypeName" (e.g., "TYPES.MyClass", "TYPES.MyEnum").
 */
class BamlTypeMap {
    private val types = mutableMapOf<String, KClass<*>>()
    private val deserializers = mutableMapOf<String, BamlDeserializable<*>>()

    fun register(namespace: String, name: String, klass: KClass<*>, deserializer: BamlDeserializable<*>? = null) {
        val key = "$namespace.$name"
        types[key] = klass
        if (deserializer != null) {
            deserializers[key] = deserializer
        }
    }

    fun getType(namespace: String, name: String): KClass<*>? {
        return types["$namespace.$name"]
    }

    fun getDeserializer(namespace: String, name: String): BamlDeserializable<*>? {
        return deserializers["$namespace.$name"]
    }

    fun getType(key: String): KClass<*>? {
        return types[key]
    }

    fun getDeserializer(key: String): BamlDeserializable<*>? {
        return deserializers[key]
    }
}
