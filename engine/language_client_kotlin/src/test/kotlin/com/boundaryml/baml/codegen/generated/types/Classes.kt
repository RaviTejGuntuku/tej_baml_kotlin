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
        override fun decode(fields: Map<String, Any?>, typeMap: BamlTypeMap): Address {
            return Address(
                street = Serde.coerceString(Serde.requireField(fields, "street")),city = Serde.coerceString(Serde.requireField(fields, "city")),zip = Serde.coerceString(Serde.requireField(fields, "zip"))
            )
        }
    }
}

data class LineItem(
    
    val sku: String,
    val quantity: Long
    
) : BamlSerializable {

    override fun encode(): com.boundaryml.baml.cffi.HostValue {
        return Serde.encodeClass("LineItem", mapOf(
            "sku" to sku,"quantity" to quantity
        ))
    }

    override fun bamlTypeName(): String = "LineItem"

    companion object : BamlDeserializable<LineItem> {
        override fun decode(fields: Map<String, Any?>, typeMap: BamlTypeMap): LineItem {
            return LineItem(
                sku = Serde.coerceString(Serde.requireField(fields, "sku")),quantity = Serde.coerceLong(Serde.requireField(fields, "quantity"))
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
        override fun decode(fields: Map<String, Any?>, typeMap: BamlTypeMap): Person {
            return Person(
                name = Serde.coerceString(Serde.requireField(fields, "name")),age = Serde.coerceLong(Serde.requireField(fields, "age")),email = Serde.coerceNullable(fields["email"]) { value -> Serde.coerceString(value) }
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
        override fun decode(fields: Map<String, Any?>, typeMap: BamlTypeMap): Receipt {
            return Receipt(
                store = Serde.coerceString(Serde.requireField(fields, "store")),items = Serde.coerceList(Serde.requireField(fields, "items")) { item -> Serde.coerceString(item) },total = Serde.coerceDouble(Serde.requireField(fields, "total"))
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
        override fun decode(fields: Map<String, Any?>, typeMap: BamlTypeMap): SearchResult {
            return SearchResult(
                query = Serde.coerceString(Serde.requireField(fields, "query")),result = Serde.coerceNamedType<Union2IntOrString>(Serde.requireField(fields, "result"), typeMap, "TYPES", "Union2IntOrString")
            )
        }
    }
}

data class ShoppingPlan(
    
    val items: List<LineItem>,
    val note: String?
    
) : BamlSerializable {

    override fun encode(): com.boundaryml.baml.cffi.HostValue {
        return Serde.encodeClass("ShoppingPlan", mapOf(
            "items" to items,"note" to note
        ))
    }

    override fun bamlTypeName(): String = "ShoppingPlan"

    companion object : BamlDeserializable<ShoppingPlan> {
        override fun decode(fields: Map<String, Any?>, typeMap: BamlTypeMap): ShoppingPlan {
            return ShoppingPlan(
                items = Serde.coerceList(Serde.requireField(fields, "items")) { item -> Serde.coerceNamedType<LineItem>(item, typeMap, "TYPES", "LineItem") },note = Serde.coerceNullable(fields["note"]) { value -> Serde.coerceString(value) }
            )
        }
    }
}
