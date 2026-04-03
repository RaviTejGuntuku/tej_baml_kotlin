use askama::Template;

use crate::{
    generated_types::{ClassKotlin, EnumKotlin, UnionKotlin},
    package::CurrentRenderPackage,
    r#type::{SerializeType, TypeKotlin},
};

pub struct FunctionKotlin {
    pub(crate) documentation: Option<String>,
    pub(crate) name: String,
    pub(crate) args: Vec<(String, TypeKotlin)>,
    pub(crate) return_type: TypeKotlin,
    #[allow(dead_code)]
    pub(crate) stream_return_type: TypeKotlin,
}

fn render_function(
    function: &FunctionKotlin,
    pkg: &CurrentRenderPackage,
) -> Result<String, askama::Error> {
    let template = FunctionTemplate {
        r#fn: function,
        pkg,
    };
    template.render()
}

fn render_function_stream(
    function: &FunctionKotlin,
    pkg: &CurrentRenderPackage,
) -> Result<String, askama::Error> {
    let template = FunctionStreamTemplate {
        r#fn: function,
        pkg,
    };
    template.render()
}

/// Functions file for Kotlin.
///
/// ```askama
/// package baml_client
///
/// import com.boundaryml.baml.*
/// import baml_client.types.*
/// import kotlinx.coroutines.flow.Flow
///
/// object BamlFunctions {
///
/// {% for function in functions %}
/// {{ crate::functions::render_function(function, pkg)? }}
/// {% endfor %}
///
/// }
/// ```
#[derive(askama::Template)]
#[template(in_doc = true, ext = "txt", escape = "none")]
struct FunctionsTemplate2<'a> {
    functions: &'a [FunctionKotlin],
    pkg: &'a CurrentRenderPackage,
}

pub fn render_functions(
    functions: &[FunctionKotlin],
    pkg: &CurrentRenderPackage,
) -> Result<String, askama::Error> {
    FunctionsTemplate2 { functions, pkg }.render()
}

/// Streaming functions file for Kotlin.
///
/// ```askama
/// package baml_client
///
/// import com.boundaryml.baml.*
/// import baml_client.types.*
/// import baml_client.stream_types.*
/// import kotlinx.coroutines.flow.Flow
///
/// object BamlStreamFunctions {
///
/// {% for function in functions %}
/// {{ crate::functions::render_function_stream(function, pkg)? }}
/// {% endfor %}
///
/// }
/// ```
#[derive(askama::Template)]
#[template(in_doc = true, ext = "txt", escape = "none")]
struct StreamFunctionsTemplate<'a> {
    functions: &'a [FunctionKotlin],
    pkg: &'a CurrentRenderPackage,
}

pub fn render_functions_stream(
    functions: &[FunctionKotlin],
    pkg: &CurrentRenderPackage,
) -> Result<String, askama::Error> {
    StreamFunctionsTemplate { functions, pkg }.render()
}

#[derive(askama::Template)]
#[template(path = "function.kt.j2", escape = "none")]
struct FunctionTemplate<'a> {
    r#fn: &'a FunctionKotlin,
    pkg: &'a CurrentRenderPackage,
}

#[derive(askama::Template)]
#[template(path = "function.stream.kt.j2", escape = "none")]
struct FunctionStreamTemplate<'a> {
    r#fn: &'a FunctionKotlin,
    pkg: &'a CurrentRenderPackage,
}

/// Type map for Kotlin — maps BAML type names to Kotlin KClass instances.
///
/// ```askama
/// package baml_client
///
/// import com.boundaryml.baml.*
/// import baml_client.types.*
/// import baml_client.stream_types.*
/// import kotlin.reflect.KClass
///
/// fun registerBamlTypes(typeMap: BamlTypeMap) {
/// {% for class in classes -%}
///     typeMap.register("TYPES.{{ class.name }}", {{ class.name }}::class)
///     typeMap.register("STREAM_TYPES.{{ class.name }}", stream_types.{{ class.name }}::class)
/// {% endfor %}
/// {% for enum_ in enums -%}
///     typeMap.register("TYPES.{{ enum_.name }}", {{ enum_.name }}::class)
/// {% endfor %}
/// {% for union_ in unions -%}
///     typeMap.register("TYPES.{{ union_.cffi_name }}", {{ union_.name }}::class)
/// {% endfor %}
/// {% for union_ in stream_unions -%}
///     typeMap.register("STREAM_TYPES.{{ union_.cffi_name }}", stream_types.{{ union_.name }}::class)
/// {% endfor %}
/// }
/// ```
#[derive(askama::Template)]
#[template(in_doc = true, escape = "none", ext = "txt")]
struct TypeMap<'a> {
    classes: &'a [ClassKotlin<'a>],
    enums: &'a [EnumKotlin<'a>],
    unions: &'a [UnionKotlin<'a>],
    stream_unions: &'a [UnionKotlin<'a>],
    #[allow(dead_code)]
    pkg: &'a CurrentRenderPackage,
}

pub fn render_type_map(
    classes: &[ClassKotlin],
    enums: &[EnumKotlin],
    unions: &[UnionKotlin],
    stream_unions: &[UnionKotlin],
    pkg: &CurrentRenderPackage,
) -> Result<String, askama::Error> {
    TypeMap {
        classes,
        enums,
        unions,
        stream_unions,
        pkg,
    }
    .render()
}

/// A map of file paths to their contents.
///
/// ```askama
/// package baml_client
///
/// val bamlSourceMap: Map<String, String> = mapOf(
/// {% for (path, contents) in file_map %}
///     {{ path }} to {{ contents }},
/// {%- endfor %}
/// )
///
/// fun getBamlFiles(): Map<String, String> = bamlSourceMap
/// ```
#[derive(askama::Template)]
#[template(in_doc = true, escape = "none", ext = "txt")]
struct SourceFiles<'a> {
    file_map: &'a [(String, String)],
}

pub fn render_source_files(file_map: Vec<(String, String)>) -> Result<String, askama::Error> {
    SourceFiles {
        file_map: &file_map,
    }
    .render()
}

pub fn render_runtime_code(_pkg: &CurrentRenderPackage) -> Result<String, askama::Error> {
    RuntimeCode {}.render()
}

#[derive(askama::Template)]
#[template(path = "runtime.kt.j2", escape = "none", ext = "txt")]
struct RuntimeCode {}
