use baml_types::{
    ir_type::{TypeGeneric, TypeNonStreaming, TypeStreaming},
    ToUnionName,
};

use crate::{package::CurrentRenderPackage, r#type::TypeKotlin};

pub fn ir_union_to_kotlin<'a>(
    union: &TypeNonStreaming,
    pkg: &'a CurrentRenderPackage,
) -> impl Iterator<Item = crate::generated_types::UnionKotlin<'a>> {
    let kotlin_type = crate::ir_to_kotlin::type_to_kotlin(union, pkg.lookup());
    let result: std::vec::IntoIter<crate::generated_types::UnionKotlin<'a>> = kotlin_type
        .flatten_unions()
        .into_iter()
        .filter_map(|kotlin_type| {
            if let TypeKotlin::Union { name, .. } = kotlin_type {
                let TypeNonStreaming::Union(union_type_generic, _) = union else {
                    panic!("ir_union_to_kotlin expects a union. Got: {union}");
                };
                let variants = union_type_generic
                    .iter_skip_null()
                    .iter()
                    .map(|t| {
                        let kotlin_type = crate::ir_to_kotlin::type_to_kotlin(t, pkg.lookup());
                        crate::generated_types::VariantKotlin {
                            name: kotlin_type.default_name_within_union(),
                            cffi_name: t.to_union_name(false),
                            literal_repr: match t {
                                TypeGeneric::Literal(l, ..) => match l {
                                    baml_types::LiteralValue::String(s) => Some(format!(
                                        "\"{}\"",
                                        s.replace('\\', "\\\\").replace('\"', "\\\"")
                                    )),
                                    baml_types::LiteralValue::Int(i) => Some(i.to_string()),
                                    baml_types::LiteralValue::Bool(true) => {
                                        Some("true".to_string())
                                    }
                                    baml_types::LiteralValue::Bool(false) => {
                                        Some("false".to_string())
                                    }
                                },
                                _ => None,
                            },
                            type_: kotlin_type,
                        }
                    })
                    .collect::<Vec<_>>();
                Some(crate::generated_types::UnionKotlin {
                    name: name.clone(),
                    cffi_name: union.to_union_name(false),
                    docstring: Some(format!("Generated from: {union}")),
                    variants,
                    pkg,
                })
            } else {
                None
            }
        })
        .collect::<Vec<_>>()
        .into_iter();
    result
}

pub fn ir_union_to_kotlin_stream<'a>(
    stream_union: &TypeStreaming,
    pkg: &'a CurrentRenderPackage,
) -> impl Iterator<Item = crate::generated_types::UnionKotlin<'a>> {
    if matches!(
        stream_union.mode(&baml_types::StreamingMode::Streaming, pkg.lookup(), 1),
        Ok(baml_types::StreamingMode::NonStreaming) | Err(_)
    ) {
        return Vec::new().into_iter();
    }
    let kotlin_type = crate::ir_to_kotlin::stream_type_to_kotlin(stream_union, pkg.lookup());
    let result: Vec<crate::generated_types::UnionKotlin<'a>> = kotlin_type
        .flatten_unions()
        .into_iter()
        .filter_map(|kotlin_type| {
            if let TypeKotlin::Union { name, .. } = kotlin_type {
                let TypeStreaming::Union(union_type_generic, _) = stream_union else {
                    panic!("ir_union_to_kotlin expects a union. Got: {stream_union}");
                };
                let variants = union_type_generic
                    .iter_skip_null()
                    .iter()
                    .map(|t| {
                        let kotlin_type =
                            crate::ir_to_kotlin::stream_type_to_kotlin(t, pkg.lookup());
                        crate::generated_types::VariantKotlin {
                            name: kotlin_type.default_name_within_union(),
                            cffi_name: t.to_union_name(false),
                            literal_repr: match t {
                                TypeGeneric::Literal(l, ..) => match l {
                                    baml_types::LiteralValue::String(s) => Some(format!(
                                        "\"{}\"",
                                        s.replace('\\', "\\\\").replace('\"', "\\\"")
                                    )),
                                    baml_types::LiteralValue::Int(i) => Some(i.to_string()),
                                    baml_types::LiteralValue::Bool(true) => {
                                        Some("true".to_string())
                                    }
                                    baml_types::LiteralValue::Bool(false) => {
                                        Some("false".to_string())
                                    }
                                },
                                _ => None,
                            },
                            type_: kotlin_type,
                        }
                    })
                    .collect::<Vec<_>>();
                Some(crate::generated_types::UnionKotlin {
                    name,
                    cffi_name: stream_union.to_union_name(false),
                    docstring: Some(format!("Generated from: {stream_union}")),
                    variants,
                    pkg,
                })
            } else {
                None
            }
        })
        .collect::<Vec<_>>();

    result.into_iter()
}
