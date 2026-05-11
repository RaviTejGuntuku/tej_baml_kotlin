use baml_types::baml_value::TypeLookups;

use crate::package::{CurrentRenderPackage, Package};

#[derive(Clone, PartialEq, Debug)]
pub enum MediaTypeKotlin {
    Image,
    Audio,
    Pdf,
    Video,
}

#[derive(Clone, PartialEq, Debug)]
pub enum TypeKotlin {
    // Explicit null type
    Null,
    // Primitive types (with optional literal value)
    String(Option<String>),
    Int(Option<i64>),
    Float,
    Bool(Option<bool>),
    Media(MediaTypeKotlin),
    // Named types
    Class {
        package: Package,
        name: String,
        dynamic: bool,
    },
    Union {
        package: Package,
        name: String,
    },
    Enum {
        package: Package,
        name: String,
        dynamic: bool,
    },
    TypeAlias {
        name: String,
        package: Package,
    },
    // Container types
    List(Box<TypeKotlin>),
    Map(Box<TypeKotlin>, Box<TypeKotlin>),
    // Fallback type
    Any {
        reason: String,
    },
    // Wrapper types
    Optional(Box<TypeKotlin>),
    Checked(Box<TypeKotlin>),
    StreamState(Box<TypeKotlin>),
}

fn safe_name(name: &str) -> String {
    name.replace(|c: char| !c.is_alphanumeric(), "_")
}

impl TypeKotlin {
    pub fn make_optional(self) -> Self {
        TypeKotlin::Optional(Box::new(self))
    }

    pub fn make_checked(self) -> Self {
        TypeKotlin::Checked(Box::new(self))
    }

    pub fn make_stream_state(self) -> Self {
        TypeKotlin::StreamState(Box::new(self))
    }

    pub fn flatten_unions(self) -> Vec<TypeKotlin> {
        match self {
            TypeKotlin::Union { .. } => {
                vec![self]
            }
            TypeKotlin::Optional(inner) => inner.flatten_unions(),
            TypeKotlin::Checked(inner) => inner.flatten_unions(),
            TypeKotlin::StreamState(inner) => inner.flatten_unions(),
            _ => vec![],
        }
    }

    pub fn default_name_within_union(&self) -> String {
        match self {
            TypeKotlin::Null => "Null".to_string(),
            TypeKotlin::Optional(inner) => {
                format!("Optional{}", inner.default_name_within_union())
            }
            TypeKotlin::Checked(inner) => format!("Checked{}", inner.default_name_within_union()),
            TypeKotlin::StreamState(inner) => {
                format!("StreamState{}", inner.default_name_within_union())
            }
            TypeKotlin::String(val) => val.as_ref().map_or("String".to_string(), |v| {
                let safe_name = safe_name(v);
                format!("K{safe_name}")
            }),
            TypeKotlin::Int(val) => val.map_or("Int".to_string(), |v| format!("IntK{v}")),
            TypeKotlin::Float => "Float".to_string(),
            TypeKotlin::Bool(val) => val.map_or("Bool".to_string(), |v| {
                format!("BoolK{}", if v { "True" } else { "False" })
            }),
            TypeKotlin::Media(media) => match media {
                MediaTypeKotlin::Image => "Image".to_string(),
                MediaTypeKotlin::Audio => "Audio".to_string(),
                MediaTypeKotlin::Pdf => "PDF".to_string(),
                MediaTypeKotlin::Video => "Video".to_string(),
            },
            TypeKotlin::TypeAlias { name, .. } => name.clone(),
            TypeKotlin::Class { name, .. } => name.clone(),
            TypeKotlin::Union { name, .. } => name.clone(),
            TypeKotlin::Enum { name, .. } => name.clone(),
            TypeKotlin::List(inner) => format!("List{}", inner.default_name_within_union()),
            TypeKotlin::Map(key, value) => format!(
                "Map{}Key{}Value",
                key.default_name_within_union(),
                value.default_name_within_union()
            ),
            TypeKotlin::Any { .. } => "Any".to_string(),
        }
    }

    /// Returns the name for this type when used as a variant class inside a sealed union.
    /// Appends "Val" to primitive names that would shadow kotlin.String, kotlin.Int, etc.
    pub fn variant_class_name(&self) -> String {
        match self {
            TypeKotlin::String(None) => "StringVal".to_string(),
            TypeKotlin::Int(None) => "IntVal".to_string(),
            TypeKotlin::Float => "FloatVal".to_string(),
            TypeKotlin::Bool(None) => "BoolVal".to_string(),
            TypeKotlin::Null => "NullVal".to_string(),
            _ => self.default_name_within_union(),
        }
    }

    /// Returns the Kotlin default value for this type.
    #[allow(dead_code)]
    pub fn default_value(&self, pkg: &CurrentRenderPackage) -> String {
        match self {
            TypeKotlin::Null => "null".to_string(),
            TypeKotlin::Optional(_) => "null".to_string(),
            TypeKotlin::Checked(_) | TypeKotlin::StreamState(_) => {
                format!("{}()", self.serialize_type(pkg))
            }
            TypeKotlin::String(val) => val.as_ref().map_or("\"\"".to_string(), |v| {
                format!("\"{}\"", v.replace('\"', "\\\""))
            }),
            TypeKotlin::Int(val) => val.map_or("0L".to_string(), |v| format!("{v}L")),
            TypeKotlin::Float => "0.0".to_string(),
            TypeKotlin::Bool(val) => val.map_or("false".to_string(), |v| {
                if v { "true" } else { "false" }.to_string()
            }),
            TypeKotlin::Media(..) | TypeKotlin::Class { .. } | TypeKotlin::Union { .. } => {
                "null".to_string()
            }
            TypeKotlin::Enum { .. } => "null".to_string(),
            TypeKotlin::TypeAlias { name, package, .. } => {
                let lookup = pkg.lookup();
                match lookup.expand_recursive_type(name) {
                    Ok(expansion) => {
                        if package == &Package::types() {
                            crate::ir_to_kotlin::type_to_kotlin(
                                &expansion.to_non_streaming_type(lookup),
                                lookup,
                            )
                            .default_value(pkg)
                        } else {
                            crate::ir_to_kotlin::stream_type_to_kotlin(
                                &expansion.to_streaming_type(lookup),
                                lookup,
                            )
                            .default_value(pkg)
                        }
                    }
                    Err(_) => "null".to_string(),
                }
            }
            TypeKotlin::List(..) => "emptyList()".to_string(),
            TypeKotlin::Map(..) => "emptyMap()".to_string(),
            TypeKotlin::Any { .. } => "null".to_string(),
        }
    }

    fn namespace_literal_for_package(package: &Package) -> &'static str {
        match package.current().as_str() {
            "types" => "TYPES",
            "stream_types" => "STREAM_TYPES",
            other => panic!("Unsupported package namespace for generated decode expression: {other}"),
        }
    }

    pub fn decode_field_expr(&self, field_name: &str, pkg: &CurrentRenderPackage) -> String {
        let field_access = match self {
            TypeKotlin::Optional(_) => format!("fields[\"{field_name}\"]"),
            _ => format!("Serde.requireField(fields, \"{field_name}\")"),
        };

        self.decode_expr(&field_access, pkg)
    }

    pub fn decode_expr(&self, value_expr: &str, pkg: &CurrentRenderPackage) -> String {
        match self {
            TypeKotlin::Null => {
                format!("Serde.expectNull({value_expr})")
            }
            TypeKotlin::Optional(inner) => {
                format!(
                    "Serde.coerceNullable({value_expr}) {{ value -> {} }}",
                    inner.decode_expr("value", pkg)
                )
            }
            TypeKotlin::Checked(inner) => {
                format!(
                    "Serde.coerceChecked({value_expr}) {{ value -> {} }}",
                    inner.decode_expr("value", pkg)
                )
            }
            TypeKotlin::StreamState(inner) => {
                format!(
                    "Serde.coerceStreamState({value_expr}) {{ value -> {} }}",
                    inner.decode_expr("value", pkg)
                )
            }
            TypeKotlin::String(..) => format!("Serde.coerceString({value_expr})"),
            TypeKotlin::Int(..) => format!("Serde.coerceLong({value_expr})"),
            TypeKotlin::Float => format!("Serde.coerceDouble({value_expr})"),
            TypeKotlin::Bool(..) => format!("Serde.coerceBoolean({value_expr})"),
            TypeKotlin::Media(..) => {
                format!("Serde.coerceInstance<{}>({value_expr})", self.serialize_type(pkg))
            }
            TypeKotlin::Class { package, name, .. }
            | TypeKotlin::Union { package, name, .. } => {
                let namespace = Self::namespace_literal_for_package(package);
                format!(
                    "Serde.coerceNamedType<{}>({value_expr}, typeMap, \"{namespace}\", \"{name}\")",
                    self.serialize_type(pkg)
                )
            }
            TypeKotlin::Enum { package, name, .. } => {
                let namespace = Self::namespace_literal_for_package(package);
                format!(
                    "Serde.coerceEnum<{}>({value_expr}, \"{namespace}\", \"{name}\")",
                    self.serialize_type(pkg)
                )
            }
            TypeKotlin::TypeAlias { name, package } => {
                let lookup = pkg.lookup();
                match lookup.expand_recursive_type(name) {
                    Ok(expansion) => {
                        let expanded = if package == &Package::types() {
                            crate::ir_to_kotlin::type_to_kotlin(
                                &expansion.to_non_streaming_type(lookup),
                                lookup,
                            )
                        } else {
                            crate::ir_to_kotlin::stream_type_to_kotlin(
                                &expansion.to_streaming_type(lookup),
                                lookup,
                            )
                        };
                        expanded.decode_expr(value_expr, pkg)
                    }
                    Err(_) => format!("Serde.coerceInstance<{}>({value_expr})", self.serialize_type(pkg)),
                }
            }
            TypeKotlin::List(inner) => {
                format!(
                    "Serde.coerceList({value_expr}) {{ item -> {} }}",
                    inner.decode_expr("item", pkg)
                )
            }
            TypeKotlin::Map(key, value) => {
                format!(
                    "Serde.coerceMap({value_expr}, {{ key -> {} }}, {{ entryValue -> {} }})",
                    key.decode_expr("key", pkg),
                    value.decode_expr("entryValue", pkg)
                )
            }
            TypeKotlin::Any { .. } => value_expr.to_string(),
        }
    }
}

pub trait SerializeType {
    fn serialize_type(&self, pkg: &CurrentRenderPackage) -> String;
}

impl SerializeType for TypeKotlin {
    fn serialize_type(&self, pkg: &CurrentRenderPackage) -> String {
        match self {
            TypeKotlin::Null => "Nothing?".to_string(),
            // Wrapper types
            TypeKotlin::Optional(inner) => format!("{}?", inner.serialize_type(pkg)),
            TypeKotlin::Checked(inner) => format!(
                "{}Checked<{}>",
                Package::checked().relative_from(pkg),
                inner.serialize_type(pkg)
            ),
            TypeKotlin::StreamState(inner) => format!(
                "{}StreamState<{}>",
                Package::stream_state().relative_from(pkg),
                inner.serialize_type(pkg)
            ),
            // Primitive types
            TypeKotlin::String(..) => "String".to_string(),
            TypeKotlin::Int(..) => "Long".to_string(),
            TypeKotlin::Float => "Double".to_string(),
            TypeKotlin::Bool(..) => "Boolean".to_string(),
            TypeKotlin::Media(media) => media.serialize_type(pkg),
            // Named types
            TypeKotlin::Class { package, name, .. } => {
                format!("{}{}", package.relative_from(pkg), name)
            }
            TypeKotlin::TypeAlias { package, name, .. } => {
                format!("{}{}", package.relative_from(pkg), name)
            }
            TypeKotlin::Union { package, name, .. } => {
                format!("{}{}", package.relative_from(pkg), name)
            }
            TypeKotlin::Enum { package, name, .. } => {
                format!("{}{}", package.relative_from(pkg), name)
            }
            // Container types
            TypeKotlin::List(inner) => format!("List<{}>", inner.serialize_type(pkg)),
            TypeKotlin::Map(key, value) => {
                format!(
                    "Map<{}, {}>",
                    key.serialize_type(pkg),
                    value.serialize_type(pkg)
                )
            }
            TypeKotlin::Any { .. } => "Any?".to_string(),
        }
    }
}

impl SerializeType for MediaTypeKotlin {
    fn serialize_type(&self, pkg: &CurrentRenderPackage) -> String {
        match self {
            MediaTypeKotlin::Image => format!("{}Image", Package::types().relative_from(pkg)),
            MediaTypeKotlin::Audio => format!("{}Audio", Package::types().relative_from(pkg)),
            MediaTypeKotlin::Pdf => format!("{}PDF", Package::types().relative_from(pkg)),
            MediaTypeKotlin::Video => format!("{}Video", Package::types().relative_from(pkg)),
        }
    }
}
