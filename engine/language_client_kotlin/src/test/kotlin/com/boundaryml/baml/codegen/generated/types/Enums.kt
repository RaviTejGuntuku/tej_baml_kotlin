package baml_client.types

import com.boundaryml.baml.*


enum class Sentiment(val value: String) : BamlSerializable {
    
    POSITIVE("POSITIVE"),
    NEGATIVE("NEGATIVE"),
    NEUTRAL("NEUTRAL");

    override fun encode(): com.boundaryml.baml.cffi.HostValue {
        return Serde.encodeEnum("Sentiment", this.value)
    }

    override fun bamlTypeName(): String = "Sentiment"

    companion object {
        fun fromString(value: String): Sentiment =
            entries.find { it.value == value }
                ?: throw IllegalArgumentException("Invalid Sentiment: $value")

        fun values_(): List<Sentiment> = entries
    }
}
