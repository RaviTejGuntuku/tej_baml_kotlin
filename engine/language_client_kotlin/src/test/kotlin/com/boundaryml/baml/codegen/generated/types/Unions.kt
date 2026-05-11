package baml_client.types

import com.boundaryml.baml.*

/// Generated from: (int | string)
sealed class Union2IntOrString {
    data class IntVal(val value: Long) : Union2IntOrString()
    data class StringVal(val value: String) : Union2IntOrString()
    

    companion object : BamlDeserializable<Union2IntOrString> {
        override fun decode(fields: Map<String, Any?>, typeMap: BamlTypeMap): Union2IntOrString {
            val variantName = Serde.coerceString(Serde.requireField(fields, "variant_name"))
            val value = Serde.requireField(fields, "value")
            return when (variantName) {
                "int" -> IntVal(Serde.coerceLong(value))
                "string" -> StringVal(Serde.coerceString(value))
                else -> throw IllegalArgumentException("Unknown variant '$variantName' for union Union2IntOrString")
            }
        }
    }
}
