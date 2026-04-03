package baml_client

import com.boundaryml.baml.*
import kotlin.reflect.KClass

fun registerBamlTypes(typeMap: BamlTypeMap) {
typeMap.register("TYPES", "Address", baml_client.types.Address::class, baml_client.types.Address)
    typeMap.register("STREAM_TYPES", "Address", baml_client.stream_types.Address::class, baml_client.stream_types.Address)
typeMap.register("TYPES", "Person", baml_client.types.Person::class, baml_client.types.Person)
    typeMap.register("STREAM_TYPES", "Person", baml_client.stream_types.Person::class, baml_client.stream_types.Person)
typeMap.register("TYPES", "Receipt", baml_client.types.Receipt::class, baml_client.types.Receipt)
    typeMap.register("STREAM_TYPES", "Receipt", baml_client.stream_types.Receipt::class, baml_client.stream_types.Receipt)
typeMap.register("TYPES", "SearchResult", baml_client.types.SearchResult::class, baml_client.types.SearchResult)
    typeMap.register("STREAM_TYPES", "SearchResult", baml_client.stream_types.SearchResult::class, baml_client.stream_types.SearchResult)

typeMap.register("TYPES", "Sentiment", baml_client.types.Sentiment::class)

typeMap.register("TYPES", "int__string", baml_client.types.Union2IntOrString::class, baml_client.types.Union2IntOrString)


}