package com.boundaryml.baml

import com.boundaryml.baml.cffi.*

/**
 * Encode/decode helpers for converting between Kotlin objects and protobuf messages.
 * Mirrors the Go SDK's serde/encode.go and serde/decode.go.
 */
object Serde {

    // -------------------------------------------------------------------------
    // Encoding: Kotlin → HostValue (inbound to engine)
    // -------------------------------------------------------------------------

    /**
     * Encode a Kotlin value to a protobuf HostValue.
     * Null → HostValue with no oneof set (represents null).
     */
    fun encodeValue(value: Any?): HostValue {
        if (value == null) {
            return HostValue.getDefaultInstance()
        }

        return when (value) {
            is BamlSerializable -> value.encode()
            is String -> hostValue { stringValue = value }
            is Int -> hostValue { intValue = value.toLong() }
            is Long -> hostValue { intValue = value }
            is Short -> hostValue { intValue = value.toLong() }
            is Byte -> hostValue { intValue = value.toLong() }
            is Double -> hostValue { floatValue = value }
            is Float -> hostValue { floatValue = value.toDouble() }
            is Boolean -> hostValue { boolValue = value }
            is List<*> -> hostValue {
                listValue = hostListValue {
                    values.addAll(value.map { encodeValue(it) })
                }
            }
            is Map<*, *> -> hostValue {
                mapValue = hostMapValue {
                    entries.addAll(value.map { (k, v) ->
                        encodeMapEntry(k, v)
                    })
                }
            }
            else -> throw BamlException("Cannot encode value of type ${value::class.simpleName}")
        }
    }

    /**
     * Encode a map entry with a typed key.
     */
    fun encodeMapEntry(key: Any?, value: Any?): HostMapEntry {
        val builder = HostMapEntry.newBuilder()
        when (key) {
            is String -> builder.stringKey = key
            is Int -> builder.intKey = key.toLong()
            is Long -> builder.intKey = key
            is Boolean -> builder.boolKey = key
            is BamlSerializable -> {
                // Enum key
                val encoded = key.encode()
                if (encoded.hasEnumValue()) {
                    builder.enumKey = encoded.enumValue
                } else {
                    throw BamlException("BamlSerializable map key must encode to enum, got: ${encoded.valueCase}")
                }
            }
            else -> throw BamlException("Unsupported map key type: ${key?.let { it::class.simpleName } ?: "null"}")
        }
        builder.value = encodeValue(value)
        return builder.build()
    }

    /**
     * Encode a class value with name and fields.
     */
    fun encodeClass(name: String, fields: Map<String, Any?>): HostValue {
        return hostValue {
            classValue = hostClassValue {
                this.name = name
                this.fields.addAll(fields.map { (k, v) ->
                    encodeMapEntry(k, v)
                })
            }
        }
    }

    /**
     * Encode an enum value with name and variant.
     */
    fun encodeEnum(name: String, value: String): HostValue {
        return hostValue {
            enumValue = hostEnumValue {
                this.name = name
                this.value = value
            }
        }
    }

    /**
     * Encode function arguments as protobuf bytes.
     */
    fun encodeArgs(kwargs: Map<String, Any?>, options: CallOptions? = null): ByteArray {
        val args = hostFunctionArguments {
            this.kwargs.addAll(kwargs.map { (k, v) ->
                encodeMapEntry(k, v)
            })
            if (options?.env != null) {
                this.env.addAll(options.env.map { (k, v) ->
                    hostEnvVar {
                        this.key = k
                        this.value = v
                    }
                })
            }
            if (options?.tags != null) {
                this.tags.addAll(options.tags.map { (k, v) ->
                    encodeMapEntry(k, v)
                })
            }
            if (options?.client != null) {
                this.clientRegistry = hostClientRegistry {
                    this.primary = options.client
                }
            }
        }
        return args.toByteArray()
    }

    // -------------------------------------------------------------------------
    // Decoding: CFFIValueHolder → Kotlin (outbound from engine)
    // -------------------------------------------------------------------------

    /**
     * Decode a CFFIValueHolder into a Kotlin value using the type map for class/enum dispatch.
     */
    fun decodeValue(holder: CFFIValueHolder, typeMap: BamlTypeMap = BamlTypeMap()): Any? {
        return when (holder.valueCase) {
            CFFIValueHolder.ValueCase.NULL_VALUE -> null
            CFFIValueHolder.ValueCase.STRING_VALUE -> holder.stringValue
            CFFIValueHolder.ValueCase.INT_VALUE -> holder.intValue
            CFFIValueHolder.ValueCase.FLOAT_VALUE -> holder.floatValue
            CFFIValueHolder.ValueCase.BOOL_VALUE -> holder.boolValue

            CFFIValueHolder.ValueCase.LIST_VALUE -> {
                holder.listValue.itemsList.map { decodeValue(it, typeMap) }
            }

            CFFIValueHolder.ValueCase.MAP_VALUE -> {
                val map = LinkedHashMap<String, Any?>()
                for (entry in holder.mapValue.entriesList) {
                    map[entry.key] = decodeValue(entry.value, typeMap)
                }
                map
            }

            CFFIValueHolder.ValueCase.CLASS_VALUE -> {
                decodeClassValue(holder.classValue, typeMap)
            }

            CFFIValueHolder.ValueCase.ENUM_VALUE -> {
                decodeEnumValue(holder.enumValue, typeMap)
            }

            CFFIValueHolder.ValueCase.LITERAL_VALUE -> {
                decodeLiteralValue(holder.literalValue)
            }

            CFFIValueHolder.ValueCase.OBJECT_VALUE -> {
                // Raw object handle - return as-is for now
                decodeObjectValue(holder.objectValue)
            }

            CFFIValueHolder.ValueCase.UNION_VARIANT_VALUE -> {
                decodeUnionVariant(holder.unionVariantValue, typeMap)
            }

            CFFIValueHolder.ValueCase.CHECKED_VALUE -> {
                decodeCheckedValue(holder.checkedValue, typeMap)
            }

            CFFIValueHolder.ValueCase.STREAMING_STATE_VALUE -> {
                decodeStreamingState(holder.streamingStateValue, typeMap)
            }

            CFFIValueHolder.ValueCase.VALUE_NOT_SET -> null
            else -> null
        }
    }

    /**
     * Decode a class value, dispatching to registered deserializer if available.
     */
    private fun decodeClassValue(classValue: CFFIValueClass, typeMap: BamlTypeMap): Any {
        val namespace = classValue.name.namespace.name
        val name = classValue.name.name

        // Decode fields into a map
        val fields = LinkedHashMap<String, Any?>()
        for (entry in classValue.fieldsList) {
            fields[entry.key] = decodeValue(entry.value, typeMap)
        }

        // Try to use registered deserializer
        val deserializer = typeMap.getDeserializer(namespace, name)
        if (deserializer != null) {
            @Suppress("UNCHECKED_CAST")
            return (deserializer as BamlDeserializable<Any>).decode(fields, typeMap)
        }

        // Fall back to a dynamic class representation
        return DynamicBamlClass(name, fields)
    }

    /**
     * Decode an enum value, dispatching to registered type if available.
     */
    private fun decodeEnumValue(enumValue: CFFIValueEnum, typeMap: BamlTypeMap): Any {
        val namespace = enumValue.name.namespace.name
        val name = enumValue.name.name
        val value = enumValue.value

        // Try to find registered enum type
        val klass = typeMap.getType(namespace, name)
        if (klass != null && klass.java.isEnum) {
            try {
                @Suppress("UNCHECKED_CAST")
                val enumClass = klass.java as Class<out Enum<*>>
                return enumClass.enumConstants?.firstOrNull { it.name == value }
                    ?: DynamicBamlEnum(name, value)
            } catch (_: Exception) {
                // Fall through to dynamic
            }
        }

        return DynamicBamlEnum(name, value)
    }

    /**
     * Decode a literal value.
     */
    private fun decodeLiteralValue(literal: CFFIFieldTypeLiteral): Any? {
        return when (literal.literalCase) {
            CFFIFieldTypeLiteral.LiteralCase.STRING_LITERAL -> literal.stringLiteral.value
            CFFIFieldTypeLiteral.LiteralCase.INT_LITERAL -> literal.intLiteral.value
            CFFIFieldTypeLiteral.LiteralCase.BOOL_LITERAL -> literal.boolLiteral.value
            CFFIFieldTypeLiteral.LiteralCase.LITERAL_NOT_SET -> null
            else -> null
        }
    }

    /**
     * Decode a raw object value.
     * Media and type builder objects come as opaque BamlObjectHandle references.
     */
    private fun decodeObjectValue(objectValue: CFFIValueRawObject): Any? {
        return when (objectValue.objectCase) {
            CFFIValueRawObject.ObjectCase.MEDIA -> BamlObjectRef(objectValue.media)
            CFFIValueRawObject.ObjectCase.TYPE -> BamlObjectRef(objectValue.type)
            CFFIValueRawObject.ObjectCase.OBJECT_NOT_SET -> null
            else -> null
        }
    }

    /**
     * Decode a union variant value.
     */
    private fun decodeUnionVariant(variant: CFFIValueUnionVariant, typeMap: BamlTypeMap): Any? {
        val innerValue = decodeValue(variant.value, typeMap)

        // If is_single_pattern, this is just T | null (optional) — return inner directly
        if (variant.isSinglePattern) {
            return if (variant.isOptional && innerValue == null) null else innerValue
        }

        val namespace = variant.name.namespace.name
        val name = variant.name.name

        // Try registered deserializer for the union type
        val deserializer = typeMap.getDeserializer(namespace, name)
        if (deserializer != null) {
            val fields = mapOf(
                "variant_name" to variant.valueOptionName,
                "value" to innerValue
            )
            @Suppress("UNCHECKED_CAST")
            return (deserializer as BamlDeserializable<Any>).decode(fields, typeMap)
        }

        // Return dynamic union representation
        return DynamicBamlUnion(name, variant.valueOptionName, innerValue)
    }

    /**
     * Decode a checked value.
     */
    private fun decodeCheckedValue(checked: CFFIValueChecked, typeMap: BamlTypeMap): Checked<Any?> {
        val value = decodeValue(checked.value, typeMap)
        val checks = LinkedHashMap<String, CheckResult>()
        for (check in checked.checksList) {
            checks[check.name] = CheckResult(
                name = check.name,
                expression = check.expression,
                status = check.status
            )
        }
        return Checked(value, checks)
    }

    /**
     * Decode a streaming state value.
     */
    private fun decodeStreamingState(state: CFFIValueStreamingState, typeMap: BamlTypeMap): StreamState<Any?> {
        val value = decodeValue(state.value, typeMap)
        return when (state.state) {
            CFFIStreamState.PENDING -> StreamState.Pending
            CFFIStreamState.STARTED -> StreamState.Started(value)
            CFFIStreamState.DONE -> StreamState.Done(value)
            else -> StreamState.Pending
        }
    }
}

/**
 * Dynamic representation of a BAML class when no registered Kotlin type is available.
 */
data class DynamicBamlClass(
    val name: String,
    val fields: Map<String, Any?>
)

/**
 * Dynamic representation of a BAML enum when no registered Kotlin type is available.
 */
data class DynamicBamlEnum(
    val name: String,
    val value: String
)

/**
 * Dynamic representation of a BAML union when no registered Kotlin type is available.
 */
data class DynamicBamlUnion(
    val name: String,
    val variantName: String,
    val value: Any?
)

/**
 * Reference to a BAML object handle (media, type builder, etc.).
 */
data class BamlObjectRef(
    val handle: BamlObjectHandle
)
