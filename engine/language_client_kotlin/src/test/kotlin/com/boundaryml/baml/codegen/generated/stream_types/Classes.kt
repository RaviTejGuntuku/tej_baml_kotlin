package baml_client.stream_types

import com.boundaryml.baml.*
import baml_client.types.*


data class Address(
    
    val street: String?,
    val city: String?,
    val zip: String?
    
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
                street = Serde.coerceNullable(fields["street"]) { value -> Serde.coerceString(value) },city = Serde.coerceNullable(fields["city"]) { value -> Serde.coerceString(value) },zip = Serde.coerceNullable(fields["zip"]) { value -> Serde.coerceString(value) }
            )
        }
    }
}
data class LineItem(
    
    val sku: String?,
    val quantity: Long?
    
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
                sku = Serde.coerceNullable(fields["sku"]) { value -> Serde.coerceString(value) },quantity = Serde.coerceNullable(fields["quantity"]) { value -> Serde.coerceLong(value) }
            )
        }
    }
}
data class Person(
    
    val name: String?,
    val age: Long?,
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
                name = Serde.coerceNullable(fields["name"]) { value -> Serde.coerceString(value) },age = Serde.coerceNullable(fields["age"]) { value -> Serde.coerceLong(value) },email = Serde.coerceNullable(fields["email"]) { value -> Serde.coerceString(value) }
            )
        }
    }
}
data class Receipt(
    
    val store: String?,
    val items: List<String>,
    val total: Double?
    
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
                store = Serde.coerceNullable(fields["store"]) { value -> Serde.coerceString(value) },items = Serde.coerceList(Serde.requireField(fields, "items")) { item -> Serde.coerceString(item) },total = Serde.coerceNullable(fields["total"]) { value -> Serde.coerceDouble(value) }
            )
        }
    }
}
data class SearchResult(
    
    val query: String?,
    val result: baml_client.types.Union2IntOrString?
    
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
                query = Serde.coerceNullable(fields["query"]) { value -> Serde.coerceString(value) },result = Serde.coerceNullable(fields["result"]) { value -> Serde.coerceNamedType<baml_client.types.Union2IntOrString>(value, typeMap, "TYPES", "Union2IntOrString") }
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
                items = Serde.coerceList(Serde.requireField(fields, "items")) { item -> Serde.coerceNamedType<LineItem>(item, typeMap, "STREAM_TYPES", "LineItem") },note = Serde.coerceNullable(fields["note"]) { value -> Serde.coerceString(value) }
            )
        }
    }
}