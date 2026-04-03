package baml_client.types

import com.boundaryml.baml.*

/// Generated from: (int | string)
sealed class Union2IntOrString {
    data class IntVal(val value: Long) : Union2IntOrString()
    data class StringVal(val value: String) : Union2IntOrString()
    

    companion object : BamlDeserializable<Union2IntOrString> {
        override fun decode(fields: Map<String, Any?>, typeMap: BamlTypeMap): Union2IntOrString {
            val variantName = fields["variant_name"] as? String
                ?: throw IllegalArgumentException("Missing variant_name in union Union2IntOrString")
            val value = fields["value"]
            return when (variantName) {
                "int" -> IntVal(value as Long)
                "string" -> StringVal(value as String)
                else -> throw IllegalArgumentException("Unknown variant '$variantName' for union Union2IntOrString")
            }
        }
    }
}
