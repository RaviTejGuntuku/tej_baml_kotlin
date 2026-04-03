use crate::{
    package::CurrentRenderPackage,
    r#type::{SerializeType, TypeKotlin},
};

mod class {
    use super::*;

    #[derive(askama::Template)]
    #[template(path = "class.kt.j2", escape = "none", ext = "txt")]
    pub struct ClassKotlin<'a> {
        pub name: String,
        pub docstring: Option<String>,
        pub fields: Vec<FieldKotlin<'a>>,
        pub dynamic: bool,
        pub pkg: &'a CurrentRenderPackage,
    }

    /// A field in a Kotlin data class.
    ///
    /// ```askama
    /// {% if let Some(docstring) = docstring -%}
    /// {{ crate::utils::prefix_lines(docstring, "    /// ") }}
    /// {%- endif %}
    ///     val {{ name }}: {{ type.serialize_type(pkg) }}
    /// ```
    #[derive(askama::Template, Clone)]
    #[template(in_doc = true, escape = "none", ext = "txt")]
    pub struct FieldKotlin<'a> {
        pub docstring: Option<String>,
        pub name: String,
        pub r#type: TypeKotlin,
        pub pkg: &'a CurrentRenderPackage,
    }
    impl std::fmt::Debug for FieldKotlin<'_> {
        fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
            write!(
                f,
                "FieldKotlin {{docstring: {:?}, name: {}, type: {:?}, pkg: <<Mutex>> }}",
                self.docstring, self.name, self.r#type
            )
        }
    }
}

mod enums {
    use super::*;

    #[derive(askama::Template)]
    #[template(path = "enums.kt.j2", escape = "none")]
    pub struct EnumKotlin<'a> {
        pub name: String,
        pub docstring: Option<String>,
        pub values: Vec<(String, Option<String>)>,
        pub dynamic: bool,
        #[allow(dead_code)]
        pub pkg: &'a CurrentRenderPackage,
    }
}

mod union {
    use super::*;

    #[derive(askama::Template)]
    #[template(path = "unions.kt.j2", escape = "none")]
    pub struct UnionKotlin<'a> {
        pub name: String,
        pub cffi_name: String,
        pub docstring: Option<String>,
        pub variants: Vec<VariantKotlin>,
        pub pkg: &'a CurrentRenderPackage,
    }

    #[derive(Clone)]
    pub struct VariantKotlin {
        pub name: String,
        pub cffi_name: String,
        #[allow(dead_code)]
        pub literal_repr: Option<String>,
        pub type_: TypeKotlin,
    }
}

mod type_aliases {
    use super::*;

    /// A type alias in Kotlin.
    ///
    /// ```askama
    /// {% if let Some(docstring) = docstring -%}
    /// {{ crate::utils::prefix_lines(docstring, "/// ") }}
    /// {%- endif %}
    /// typealias {{ name }} = {{ type_.serialize_type(pkg) }}
    /// ```
    #[derive(askama::Template)]
    #[template(in_doc = true, escape = "none", ext = "txt")]
    pub struct TypeAliasKotlin<'a> {
        pub name: String,
        pub type_: TypeKotlin,
        pub docstring: Option<String>,
        pub pkg: &'a CurrentRenderPackage,
    }
}

/// Kotlin types file header.
///
/// ```askama
/// package baml_client.types
///
/// import com.boundaryml.baml.*
///
/// {% for item in items -%}
/// {{ item.render()? }}
/// {% endfor %}
/// ```
#[derive(askama::Template)]
#[template(in_doc = true, escape = "none", ext = "txt")]
struct KotlinTypes<'ir, T: askama::Template> {
    items: &'ir [T],
}

pub(crate) fn render_kotlin_types<T: askama::Template>(
    items: &[T],
    _pkg: &CurrentRenderPackage,
) -> Result<String, askama::Error> {
    use askama::Template;

    KotlinTypes { items }.render()
}

/// Kotlin stream types file header.
///
/// ```askama
/// package baml_client.stream_types
///
/// import com.boundaryml.baml.*
/// import baml_client.types.*
///
/// {% for item in items -%}
/// {{ item.render()? }}
/// {%- endfor %}
/// ```
#[derive(askama::Template)]
#[template(in_doc = true, escape = "none", ext = "txt")]
struct KotlinStreamTypes<'ir, T: askama::Template> {
    items: &'ir [T],
}

pub(crate) fn render_kotlin_stream_types<T: askama::Template>(
    items: &[T],
    _pkg: &CurrentRenderPackage,
) -> Result<String, askama::Error> {
    use askama::Template;

    KotlinStreamTypes { items }.render()
}

pub use class::{ClassKotlin, FieldKotlin};
pub use enums::EnumKotlin;
pub use type_aliases::TypeAliasKotlin;
pub use union::{UnionKotlin, VariantKotlin};
