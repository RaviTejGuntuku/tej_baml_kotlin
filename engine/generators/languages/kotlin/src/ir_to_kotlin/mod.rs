use baml_types::{
    baml_value::TypeLookups,
    ir_type::{TypeNonStreaming, TypeStreaming},
    BamlMediaType, ConstraintLevel, TypeValue,
};

use crate::{
    package::Package,
    r#type::{MediaTypeKotlin, TypeKotlin},
};

pub mod classes;
pub mod enums;
pub mod functions;
pub mod type_aliases;
pub mod unions;

pub(crate) fn stream_type_to_kotlin(
    field: &TypeStreaming,
    lookup: &impl TypeLookups,
) -> TypeKotlin {
    use TypeStreaming as T;
    let recursive_fn = |field| stream_type_to_kotlin(field, lookup);

    let field_has_checks = field
        .meta()
        .constraints
        .iter()
        .any(|c| matches!(c.level, ConstraintLevel::Check));

    let field_has_stream_state = field.meta().streaming_behavior.state;

    let types_pkg: Package = Package::types();
    let stream_pkg: Package = Package::stream_types();

    let type_kotlin: TypeKotlin = match field {
        T::Primitive(type_value, _) => type_value.into(),
        T::Enum { name, dynamic, .. } => TypeKotlin::Enum {
            package: types_pkg.clone(),
            name: name.clone(),
            dynamic: *dynamic,
        },
        T::Literal(literal_value, _) => match literal_value {
            baml_types::LiteralValue::String(val) => TypeKotlin::String(Some(val.clone())),
            baml_types::LiteralValue::Int(val) => TypeKotlin::Int(Some(*val)),
            baml_types::LiteralValue::Bool(val) => TypeKotlin::Bool(Some(*val)),
        },
        T::Class {
            name,
            dynamic,
            meta: cls_meta,
            ..
        } => TypeKotlin::Class {
            package: match cls_meta.streaming_behavior.done {
                true => types_pkg.clone(),
                false => stream_pkg.clone(),
            },
            name: name.clone(),
            dynamic: *dynamic,
        },
        T::List(type_generic, _) => TypeKotlin::List(Box::new(recursive_fn(type_generic))),
        T::Map(type_generic, type_generic1, _) => TypeKotlin::Map(
            Box::new(recursive_fn(type_generic)),
            Box::new(recursive_fn(type_generic1)),
        ),
        T::RecursiveTypeAlias {
            name,
            meta: alias_meta,
            ..
        } => {
            if lookup.expand_recursive_type(name).is_err() {
                TypeKotlin::Any {
                    reason: format!("Recursive type alias {name} is not supported in Kotlin"),
                }
            } else {
                TypeKotlin::TypeAlias {
                    package: match alias_meta.streaming_behavior.done {
                        true => types_pkg.clone(),
                        false => stream_pkg.clone(),
                    },
                    name: name.clone(),
                }
            }
        }
        T::Tuple(..) => TypeKotlin::Any {
            reason: "tuples are not supported in Kotlin".to_string(),
        },
        T::Arrow(..) => TypeKotlin::Any {
            reason: "arrow types are not supported in Kotlin".to_string(),
        },
        T::Union(union_type_generic, union_meta) => {
            let has_union_checks = union_meta
                .constraints
                .iter()
                .any(|c| matches!(c.level, ConstraintLevel::Check));
            let has_union_stream_state = union_meta.streaming_behavior.state;

            match union_type_generic.view() {
                baml_types::ir_type::UnionTypeViewGeneric::Null => TypeKotlin::Null,
                baml_types::ir_type::UnionTypeViewGeneric::Optional(type_generic) => {
                    let mut type_kotlin = recursive_fn(type_generic);
                    type_kotlin = type_kotlin.make_optional();
                    if has_union_checks {
                        type_kotlin = type_kotlin.make_checked();
                    }
                    if has_union_stream_state {
                        type_kotlin = type_kotlin.make_stream_state();
                    }
                    type_kotlin
                }
                baml_types::ir_type::UnionTypeViewGeneric::OneOf(type_generics) => {
                    let options: Vec<_> = type_generics.into_iter().map(&recursive_fn).collect();
                    let num_options = options.len();
                    let mut name = options
                        .iter()
                        .map(|t| t.default_name_within_union())
                        .collect::<Vec<_>>();
                    name.sort();
                    let name = name.join("Or");
                    let mut union_type = TypeKotlin::Union {
                        package: match field.mode(&baml_types::StreamingMode::Streaming, lookup, 1)
                        {
                            Ok(baml_types::StreamingMode::NonStreaming) => types_pkg.clone(),
                            Ok(baml_types::StreamingMode::Streaming) => stream_pkg.clone(),
                            Err(e) => {
                                return TypeKotlin::Any {
                                    reason: format!("Failed to get mode for field type: {e}"),
                                }
                            }
                        },
                        name: format!("Union{num_options}{name}"),
                    };
                    if has_union_checks {
                        union_type = union_type.make_checked();
                    }
                    if has_union_stream_state {
                        union_type = union_type.make_stream_state();
                    }
                    union_type
                }
                baml_types::ir_type::UnionTypeViewGeneric::OneOfOptional(type_generics) => {
                    let options: Vec<_> = type_generics.into_iter().map(recursive_fn).collect();
                    let num_options = options.len();
                    let mut name = options
                        .iter()
                        .map(|t| t.default_name_within_union())
                        .collect::<Vec<_>>();
                    name.sort();
                    let name = name.join("Or");
                    let mut union_type = TypeKotlin::Union {
                        package: match field.mode(&baml_types::StreamingMode::Streaming, lookup, 1)
                        {
                            Ok(baml_types::StreamingMode::NonStreaming) => types_pkg.clone(),
                            Ok(baml_types::StreamingMode::Streaming) => stream_pkg.clone(),
                            Err(e) => {
                                return TypeKotlin::Any {
                                    reason: format!("Failed to get mode for field type: {e}"),
                                }
                            }
                        },
                        name: format!("Union{num_options}{name}"),
                    };
                    if has_union_checks {
                        union_type = union_type.make_checked();
                    }
                    union_type = union_type.make_optional();
                    if has_union_stream_state {
                        union_type = union_type.make_stream_state();
                    }
                    union_type
                }
            }
        }
        T::Top(_) => panic!(
            "TypeGeneric::Top should have been resolved by the compiler before code generation. \
             This indicates a bug in the type resolution phase."
        ),
    };

    if matches!(field, T::Union(..)) {
        return type_kotlin;
    }

    let type_kotlin = if field_has_checks {
        type_kotlin.make_checked()
    } else {
        type_kotlin
    };

    if field_has_stream_state {
        type_kotlin.make_stream_state()
    } else {
        type_kotlin
    }
}

pub(crate) fn type_to_kotlin(field: &TypeNonStreaming, _lookup: &impl TypeLookups) -> TypeKotlin {
    use TypeNonStreaming as T;
    let recursive_fn = |field| type_to_kotlin(field, _lookup);

    let field_has_checks = field
        .meta()
        .constraints
        .iter()
        .any(|c| matches!(c.level, ConstraintLevel::Check));

    let type_pkg = Package::types();

    let type_kotlin = match field {
        T::Primitive(type_value, _) => type_value.into(),
        T::Enum { name, dynamic, .. } => TypeKotlin::Enum {
            package: type_pkg.clone(),
            name: name.clone(),
            dynamic: *dynamic,
        },
        T::Literal(literal_value, _) => match literal_value {
            baml_types::LiteralValue::String(val) => TypeKotlin::String(Some(val.clone())),
            baml_types::LiteralValue::Int(val) => TypeKotlin::Int(Some(*val)),
            baml_types::LiteralValue::Bool(val) => TypeKotlin::Bool(Some(*val)),
        },
        T::Class { name, dynamic, .. } => TypeKotlin::Class {
            package: type_pkg.clone(),
            name: name.clone(),
            dynamic: *dynamic,
        },
        T::List(type_generic, _) => TypeKotlin::List(Box::new(recursive_fn(type_generic))),
        T::Map(type_generic, type_generic1, _) => TypeKotlin::Map(
            Box::new(recursive_fn(type_generic)),
            Box::new(recursive_fn(type_generic1)),
        ),
        T::Tuple(..) => TypeKotlin::Any {
            reason: "tuples are not supported in Kotlin".to_string(),
        },
        T::Arrow(..) => TypeKotlin::Any {
            reason: "arrow types are not supported in Kotlin".to_string(),
        },
        T::RecursiveTypeAlias { name, .. } => {
            if _lookup.expand_recursive_type(name).is_err() {
                TypeKotlin::Any {
                    reason: format!("Recursive type alias {name} is not supported in Kotlin"),
                }
            } else {
                TypeKotlin::TypeAlias {
                    package: type_pkg.clone(),
                    name: name.clone(),
                }
            }
        }
        T::Union(union_type_generic, union_meta) => {
            let has_union_checks = union_meta
                .constraints
                .iter()
                .any(|c| matches!(c.level, ConstraintLevel::Check));

            match union_type_generic.view() {
                baml_types::ir_type::UnionTypeViewGeneric::Null => TypeKotlin::Null,
                baml_types::ir_type::UnionTypeViewGeneric::Optional(type_generic) => {
                    let mut type_kotlin = recursive_fn(type_generic);
                    type_kotlin = type_kotlin.make_optional();
                    if has_union_checks {
                        type_kotlin = type_kotlin.make_checked();
                    }
                    type_kotlin
                }
                baml_types::ir_type::UnionTypeViewGeneric::OneOf(type_generics) => {
                    let options: Vec<_> = type_generics.into_iter().map(&recursive_fn).collect();
                    let num_options = options.len();
                    let mut name = options
                        .iter()
                        .map(|t| t.default_name_within_union())
                        .collect::<Vec<_>>();
                    name.sort();
                    let name = name.join("Or");
                    let mut union_type = TypeKotlin::Union {
                        package: type_pkg.clone(),
                        name: format!("Union{num_options}{name}"),
                    };
                    if has_union_checks {
                        union_type = union_type.make_checked();
                    }
                    union_type
                }
                baml_types::ir_type::UnionTypeViewGeneric::OneOfOptional(type_generics) => {
                    let options: Vec<_> = type_generics.into_iter().map(recursive_fn).collect();
                    let num_options = options.len();
                    let mut name = options
                        .iter()
                        .map(|t| t.default_name_within_union())
                        .collect::<Vec<_>>();
                    name.sort();
                    let name = name.join("Or");
                    let mut union_type = TypeKotlin::Union {
                        package: type_pkg.clone(),
                        name: format!("Union{num_options}{name}"),
                    };
                    if has_union_checks {
                        union_type = union_type.make_checked();
                    }
                    union_type = union_type.make_optional();
                    union_type
                }
            }
        }
        T::Top(_) => panic!(
            "TypeGeneric::Top should have been resolved by the compiler before code generation. \
             This indicates a bug in the type resolution phase."
        ),
    };

    if field_has_checks && !matches!(field, T::Union(..)) {
        type_kotlin.make_checked()
    } else {
        type_kotlin
    }
}

impl From<&TypeValue> for TypeKotlin {
    fn from(type_value: &TypeValue) -> Self {
        match type_value {
            TypeValue::String => TypeKotlin::String(None),
            TypeValue::Int => TypeKotlin::Int(None),
            TypeValue::Float => TypeKotlin::Float,
            TypeValue::Bool => TypeKotlin::Bool(None),
            TypeValue::Null => TypeKotlin::Null,
            TypeValue::Media(baml_media_type) => TypeKotlin::Media(baml_media_type.into()),
        }
    }
}

impl From<&BamlMediaType> for MediaTypeKotlin {
    fn from(baml_media_type: &BamlMediaType) -> Self {
        match baml_media_type {
            BamlMediaType::Image => MediaTypeKotlin::Image,
            BamlMediaType::Audio => MediaTypeKotlin::Audio,
            BamlMediaType::Pdf => MediaTypeKotlin::Pdf,
            BamlMediaType::Video => MediaTypeKotlin::Video,
        }
    }
}

#[cfg(test)]
mod tests {
    use internal_baml_core::ir::{repr::make_test_ir, IRHelper};

    use crate::package::CurrentRenderPackage;
    use crate::r#type::SerializeType;

    use super::*;

    /// Helper: given BAML source, returns (non_streaming_type, streaming_type) strings
    /// for a field on a class.
    fn serialize_field(baml: &str, class_name: &str, field_name: &str) -> (String, String) {
        let ir = make_test_ir(baml).expect("Valid BAML");
        let ir = std::sync::Arc::new(ir);
        let class = ir.find_class(class_name).unwrap().item;
        let pkg = CurrentRenderPackage::new("baml_client", ir.clone());

        // Non-streaming
        pkg.set("baml_client.types");
        let class_kt = classes::ir_class_to_kotlin(class, &pkg);
        let field = class_kt.fields.iter().find(|f| f.name == field_name).unwrap();
        let non_streaming = field.r#type.serialize_type(&pkg);

        // Streaming
        pkg.set("baml_client.stream_types");
        let class_kt_stream = classes::ir_class_to_kotlin_stream(class, &pkg);
        let field = class_kt_stream
            .fields
            .iter()
            .find(|f| f.name == field_name)
            .unwrap();
        let streaming = field.r#type.serialize_type(&pkg);

        (non_streaming, streaming)
    }

    // ==================== PRIMITIVE TYPES ====================

    #[test]
    fn test_string_type() {
        let (ns, s) = serialize_field("class T { f string }", "T", "f");
        assert_eq!(ns, "String");
        assert_eq!(s, "String?");
    }

    #[test]
    fn test_int_type() {
        let (ns, s) = serialize_field("class T { f int }", "T", "f");
        assert_eq!(ns, "Long");
        assert_eq!(s, "Long?");
    }

    #[test]
    fn test_float_type() {
        let (ns, s) = serialize_field("class T { f float }", "T", "f");
        assert_eq!(ns, "Double");
        assert_eq!(s, "Double?");
    }

    #[test]
    fn test_bool_type() {
        let (ns, s) = serialize_field("class T { f bool }", "T", "f");
        assert_eq!(ns, "Boolean");
        assert_eq!(s, "Boolean?");
    }

    // ==================== OPTIONAL TYPES ====================

    #[test]
    fn test_optional_string() {
        let (ns, s) = serialize_field("class T { f string? }", "T", "f");
        assert_eq!(ns, "String?");
        assert_eq!(s, "String?");
    }

    #[test]
    fn test_optional_int() {
        let (ns, s) = serialize_field("class T { f int? }", "T", "f");
        assert_eq!(ns, "Long?");
        assert_eq!(s, "Long?");
    }

    #[test]
    fn test_optional_float() {
        let (ns, s) = serialize_field("class T { f float? }", "T", "f");
        assert_eq!(ns, "Double?");
        assert_eq!(s, "Double?");
    }

    #[test]
    fn test_optional_bool() {
        let (ns, s) = serialize_field("class T { f bool? }", "T", "f");
        assert_eq!(ns, "Boolean?");
        assert_eq!(s, "Boolean?");
    }

    // ==================== CONTAINER TYPES ====================

    #[test]
    fn test_list_of_strings() {
        let (ns, s) = serialize_field("class T { f string[] }", "T", "f");
        assert_eq!(ns, "List<String>");
        // Streaming: container stays, inner elements stream
        assert_eq!(s, "List<String>");
    }

    #[test]
    fn test_list_of_ints() {
        let (ns, s) = serialize_field("class T { f int[] }", "T", "f");
        assert_eq!(ns, "List<Long>");
        assert_eq!(s, "List<Long>");
    }

    #[test]
    fn test_optional_list() {
        let (ns, s) = serialize_field("class T { f string[]? }", "T", "f");
        assert_eq!(ns, "List<String>?");
        assert_eq!(s, "List<String>?");
    }

    #[test]
    fn test_list_of_optionals() {
        let (ns, s) = serialize_field("class T { f (string?)[] }", "T", "f");
        assert_eq!(ns, "List<String?>");
        assert_eq!(s, "List<String?>");
    }

    #[test]
    fn test_nested_list() {
        let (ns, s) = serialize_field("class T { f string[][] }", "T", "f");
        assert_eq!(ns, "List<List<String>>");
        assert_eq!(s, "List<List<String>>");
    }

    #[test]
    fn test_map_string_to_int() {
        let (ns, s) = serialize_field("class T { f map<string, int> }", "T", "f");
        assert_eq!(ns, "Map<String, Long>");
        assert_eq!(s, "Map<String, Long>");
    }

    #[test]
    fn test_map_string_to_string() {
        let (ns, s) = serialize_field("class T { f map<string, string> }", "T", "f");
        assert_eq!(ns, "Map<String, String>");
        assert_eq!(s, "Map<String, String>");
    }

    #[test]
    fn test_optional_map() {
        let (ns, s) = serialize_field("class T { f map<string, int>? }", "T", "f");
        assert_eq!(ns, "Map<String, Long>?");
        assert_eq!(s, "Map<String, Long>?");
    }

    #[test]
    fn test_map_of_lists() {
        let (ns, s) = serialize_field("class T { f map<string, int[]> }", "T", "f");
        assert_eq!(ns, "Map<String, List<Long>>");
        assert_eq!(s, "Map<String, List<Long>>");
    }

    #[test]
    fn test_list_of_maps() {
        let (ns, s) = serialize_field("class T { f map<string, int>[] }", "T", "f");
        assert_eq!(ns, "List<Map<String, Long>>");
        assert_eq!(s, "List<Map<String, Long>>");
    }

    // ==================== NAMED TYPE REFERENCES ====================

    #[test]
    fn test_class_reference() {
        let (ns, s) = serialize_field(
            "class Inner { x int }\nclass T { f Inner }",
            "T",
            "f",
        );
        assert_eq!(ns, "Inner");
        assert_eq!(s, "Inner?");
    }

    #[test]
    fn test_enum_reference() {
        let (ns, s) = serialize_field(
            "enum Color {\n  Red\n  Green\n  Blue\n}\nclass T { f Color }",
            "T",
            "f",
        );
        assert_eq!(ns, "Color");
        assert_eq!(s, "baml_client.types.Color?");
    }

    #[test]
    fn test_optional_class_reference() {
        let (ns, s) = serialize_field(
            "class Inner { x int }\nclass T { f Inner? }",
            "T",
            "f",
        );
        assert_eq!(ns, "Inner?");
        assert_eq!(s, "Inner?");
    }

    #[test]
    fn test_optional_enum_reference() {
        let (ns, s) = serialize_field(
            "enum Color {\n  Red\n  Green\n  Blue\n}\nclass T { f Color? }",
            "T",
            "f",
        );
        assert_eq!(ns, "Color?");
        assert_eq!(s, "baml_client.types.Color?");
    }

    #[test]
    fn test_list_of_classes() {
        let (ns, s) = serialize_field(
            "class Item { name string }\nclass T { f Item[] }",
            "T",
            "f",
        );
        assert_eq!(ns, "List<Item>");
        assert_eq!(s, "List<Item>");
    }

    #[test]
    fn test_map_of_classes() {
        let (ns, s) = serialize_field(
            "class Item { name string }\nclass T { f map<string, Item> }",
            "T",
            "f",
        );
        assert_eq!(ns, "Map<String, Item>");
        assert_eq!(s, "Map<String, Item>");
    }

    // ==================== UNION TYPES ====================

    #[test]
    fn test_union_int_string() {
        let (ns, _s) = serialize_field(
            "class T { f int | string }",
            "T",
            "f",
        );
        assert_eq!(ns, "Union2IntOrString");
    }

    #[test]
    fn test_union_with_null() {
        // int | string | null becomes (int | string)?
        let (ns, _s) = serialize_field(
            "class T { f int | string | null }",
            "T",
            "f",
        );
        assert_eq!(ns, "Union2IntOrString?");
    }

    #[test]
    fn test_union_with_class() {
        let (ns, _s) = serialize_field(
            "class A { x int }\nclass B { y string }\nclass T { f A | B }",
            "T",
            "f",
        );
        assert_eq!(ns, "Union2AOrB");
    }

    // ==================== MEDIA TYPES ====================

    #[test]
    fn test_image_type() {
        let (ns, s) = serialize_field("class T { f image }", "T", "f");
        assert_eq!(ns, "BamlImage");
        assert_eq!(s, "BamlImage?");
    }

    #[test]
    fn test_audio_type() {
        let (ns, s) = serialize_field("class T { f audio }", "T", "f");
        assert_eq!(ns, "BamlAudio");
        assert_eq!(s, "BamlAudio?");
    }

    // ==================== STREAMING ATTRIBUTES ====================

    #[test]
    fn test_stream_with_state() {
        let (ns, s) = serialize_field(
            "class T { f string @stream.with_state }",
            "T",
            "f",
        );
        assert_eq!(ns, "String");
        // streaming: StreamState wrapping
        assert!(s.contains("StreamState"), "Expected StreamState wrapper, got: {}", s);
    }

    #[test]
    fn test_stream_with_state_int() {
        let (ns, s) = serialize_field(
            "class T { f int @stream.with_state }",
            "T",
            "f",
        );
        assert_eq!(ns, "Long");
        assert!(s.contains("StreamState"), "Expected StreamState wrapper, got: {}", s);
    }

    // ==================== CHECK CONSTRAINTS ====================

    #[test]
    fn test_check_constraint() {
        let (ns, _s) = serialize_field(
            r#"class T { f int @check(valid, {{ this >= 0 }}) }"#,
            "T",
            "f",
        );
        assert!(ns.contains("Checked"), "Expected Checked wrapper, got: {}", ns);
    }

    #[test]
    fn test_check_and_stream_state() {
        let (ns, s) = serialize_field(
            r#"class T { f int @check(valid, {{ this >= 0 }}) @stream.with_state }"#,
            "T",
            "f",
        );
        // Non-streaming: Checked<Long>
        assert!(ns.contains("Checked"), "Expected Checked wrapper in ns, got: {}", ns);
        // Streaming: StreamState<Checked<Long?>>  (or similar nesting)
        assert!(s.contains("StreamState"), "Expected StreamState in streaming, got: {}", s);
        assert!(s.contains("Checked"), "Expected Checked in streaming, got: {}", s);
    }

    // ==================== MULTI-FIELD CLASSES ====================

    #[test]
    fn test_class_multiple_fields() {
        let ir = make_test_ir(
            r#"
            class Person {
                name string
                age int
                email string?
                tags string[]
                metadata map<string, string>
            }
            "#,
        )
        .unwrap();
        let ir = std::sync::Arc::new(ir);
        let class = ir.find_class("Person").unwrap().item;
        let pkg = CurrentRenderPackage::new("baml_client", ir.clone());
        pkg.set("baml_client.types");
        let class_kt = classes::ir_class_to_kotlin(class, &pkg);

        assert_eq!(class_kt.fields.len(), 5);
        assert_eq!(class_kt.fields[0].name, "name");
        assert_eq!(class_kt.fields[0].r#type.serialize_type(&pkg), "String");
        assert_eq!(class_kt.fields[1].name, "age");
        assert_eq!(class_kt.fields[1].r#type.serialize_type(&pkg), "Long");
        assert_eq!(class_kt.fields[2].name, "email");
        assert_eq!(class_kt.fields[2].r#type.serialize_type(&pkg), "String?");
        assert_eq!(class_kt.fields[3].name, "tags");
        assert_eq!(class_kt.fields[3].r#type.serialize_type(&pkg), "List<String>");
        assert_eq!(class_kt.fields[4].name, "metadata");
        assert_eq!(class_kt.fields[4].r#type.serialize_type(&pkg), "Map<String, String>");
    }

    #[test]
    fn test_class_streaming_all_fields_nullable() {
        let ir = make_test_ir(
            r#"
            class Person {
                name string
                age int
                active bool
            }
            "#,
        )
        .unwrap();
        let ir = std::sync::Arc::new(ir);
        let class = ir.find_class("Person").unwrap().item;
        let pkg = CurrentRenderPackage::new("baml_client", ir.clone());
        pkg.set("baml_client.stream_types");
        let class_kt = classes::ir_class_to_kotlin_stream(class, &pkg);

        // In streaming mode, ALL fields become nullable
        assert_eq!(class_kt.fields[0].r#type.serialize_type(&pkg), "String?");
        assert_eq!(class_kt.fields[1].r#type.serialize_type(&pkg), "Long?");
        assert_eq!(class_kt.fields[2].r#type.serialize_type(&pkg), "Boolean?");
    }

    // ==================== DYNAMIC CLASSES ====================

    #[test]
    fn test_dynamic_class() {
        let ir = make_test_ir(
            r#"
            class DynClass {
                name string
                @@dynamic
            }
            "#,
        )
        .unwrap();
        let ir = std::sync::Arc::new(ir);
        let class = ir.find_class("DynClass").unwrap().item;
        let pkg = CurrentRenderPackage::new("baml_client", ir.clone());
        pkg.set("baml_client.types");
        let class_kt = classes::ir_class_to_kotlin(class, &pkg);

        assert!(class_kt.dynamic, "Expected dynamic class");
        assert_eq!(class_kt.fields.len(), 1);
    }

    // ==================== DYNAMIC ENUMS ====================

    #[test]
    fn test_dynamic_enum() {
        let ir = make_test_ir(
            "enum Category {\n  Tech\n  Science\n  @@dynamic\n}",
        )
        .unwrap();
        let ir = std::sync::Arc::new(ir);
        let enm = ir.find_enum("Category").unwrap().item;
        let pkg = CurrentRenderPackage::new("baml_client", ir.clone());
        let enum_kt = enums::ir_enum_to_kotlin(enm, &pkg);

        assert!(enum_kt.dynamic, "Expected dynamic enum");
        assert_eq!(enum_kt.values.len(), 2);
    }

    // ==================== TYPE ALIASES ====================

    #[test]
    fn test_type_alias_simple() {
        let ir = make_test_ir(
            r#"
            type Name = string
            "#,
        )
        .unwrap();
        let ir = std::sync::Arc::new(ir);
        let alias = ir.find_type_alias("Name").unwrap().item;
        let pkg = CurrentRenderPackage::new("baml_client", ir.clone());
        pkg.set("baml_client.types");
        let alias_kt = type_aliases::ir_type_alias_to_kotlin(alias, &pkg, None);

        assert_eq!(alias_kt.name, "Name");
        assert_eq!(alias_kt.type_.serialize_type(&pkg), "String");
    }

    #[test]
    fn test_type_alias_list() {
        let ir = make_test_ir(
            r#"
            type Names = string[]
            "#,
        )
        .unwrap();
        let ir = std::sync::Arc::new(ir);
        let alias = ir.find_type_alias("Names").unwrap().item;
        let pkg = CurrentRenderPackage::new("baml_client", ir.clone());
        pkg.set("baml_client.types");
        let alias_kt = type_aliases::ir_type_alias_to_kotlin(alias, &pkg, None);

        assert_eq!(alias_kt.name, "Names");
        assert_eq!(alias_kt.type_.serialize_type(&pkg), "List<String>");
    }

    // ==================== NESTED CLASS HIERARCHIES ====================

    #[test]
    fn test_nested_class_reference() {
        let (ns, s) = serialize_field(
            r#"
            class Address { city string zip string }
            class Company { name string address Address }
            class T { f Company }
            "#,
            "T",
            "f",
        );
        assert_eq!(ns, "Company");
        assert_eq!(s, "Company?");
    }

    // ==================== COMPLEX CONTAINERS ====================

    #[test]
    fn test_list_of_optional_ints() {
        let (ns, s) = serialize_field("class T { f (int?)[] }", "T", "f");
        assert_eq!(ns, "List<Long?>");
        assert_eq!(s, "List<Long?>");
    }

    #[test]
    fn test_deeply_nested_list() {
        let (ns, s) = serialize_field("class T { f int[][][] }", "T", "f");
        assert_eq!(ns, "List<List<List<Long>>>");
        assert_eq!(s, "List<List<List<Long>>>");
    }

    #[test]
    fn test_map_with_list_values() {
        let (ns, s) = serialize_field("class T { f map<string, string[]> }", "T", "f");
        assert_eq!(ns, "Map<String, List<String>>");
        assert_eq!(s, "Map<String, List<String>>");
    }
}
