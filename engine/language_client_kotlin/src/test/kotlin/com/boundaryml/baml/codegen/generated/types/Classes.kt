package baml_client.types

import com.boundaryml.baml.*


data class Address(
    
    val street: String,
    val city: String,
    val zip: String
    
) : BamlSerializable {

    override fun encode(): com.boundaryml.baml.cffi.HostValue {
        return Serde.encodeClass("Address", mapOf(
            "street" to street,"city" to city,"zip" to zip
        ))
    }

    override fun bamlTypeName(): String = "Address"

    companion object : BamlDeserializable<Address> {
        @Suppress("UNCHECKED_CAST")
        override fun decode(fields: Map<String, Any?>, typeMap: BamlTypeMap): Address {
            return Address(
                street = fields["street"] as String,city = fields["city"] as String,zip = fields["zip"] as String
            )
        }
    }
}

data class Person(
    
    val name: String,
    val age: Long,
    val email: String?
    
) : BamlSerializable {

    override fun encode(): com.boundaryml.baml.cffi.HostValue {
        return Serde.encodeClass("Person", mapOf(
            "name" to name,"age" to age,"email" to email
        ))
    }

    override fun bamlTypeName(): String = "Person"

    companion object : BamlDeserializable<Person> {
        @Suppress("UNCHECKED_CAST")
        override fun decode(fields: Map<String, Any?>, typeMap: BamlTypeMap): Person {
            return Person(
                name = fields["name"] as String,age = fields["age"] as Long,email = fields["email"] as String?
            )
        }
    }
}

data class Receipt(
    
    val store: String,
    val items: List<String>,
    val total: Double
    
) : BamlSerializable {

    override fun encode(): com.boundaryml.baml.cffi.HostValue {
        return Serde.encodeClass("Receipt", mapOf(
            "store" to store,"items" to items,"total" to total
        ))
    }

    override fun bamlTypeName(): String = "Receipt"

    companion object : BamlDeserializable<Receipt> {
        @Suppress("UNCHECKED_CAST")
        override fun decode(fields: Map<String, Any?>, typeMap: BamlTypeMap): Receipt {
            return Receipt(
                store = fields["store"] as String,items = fields["items"] as List<String>,total = fields["total"] as Double
            )
        }
    }
}

data class SearchResult(
    
    val query: String,
    val result: Union2IntOrString
    
) : BamlSerializable {

    override fun encode(): com.boundaryml.baml.cffi.HostValue {
        return Serde.encodeClass("SearchResult", mapOf(
            "query" to query,"result" to result
        ))
    }

    override fun bamlTypeName(): String = "SearchResult"

    companion object : BamlDeserializable<SearchResult> {
        @Suppress("UNCHECKED_CAST")
        override fun decode(fields: Map<String, Any?>, typeMap: BamlTypeMap): SearchResult {
            return SearchResult(
                query = fields["query"] as String,result = fields["result"] as Union2IntOrString
            )
        }
    }
}
