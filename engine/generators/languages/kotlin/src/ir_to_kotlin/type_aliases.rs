use baml_types::baml_value::TypeLookups;
use internal_baml_core::ir::TypeAlias;

use crate::{generated_types::TypeAliasKotlin, ir_to_kotlin, package::CurrentRenderPackage};

struct LookupWithDrop<'a, T> {
    lookup: &'a T,
    drop_type: Option<&'a String>,
}

impl<'a, T: TypeLookups> TypeLookups for LookupWithDrop<'a, T> {
    fn expand_recursive_type(&self, type_alias: &str) -> anyhow::Result<&baml_types::TypeIR> {
        if self.drop_type.is_some() && self.drop_type.unwrap() == type_alias {
            Err(anyhow::anyhow!(
                "Recursive type alias {type_alias} is not supported in Kotlin"
            ))
        } else {
            self.lookup.expand_recursive_type(type_alias)
        }
    }
}

pub fn ir_type_alias_to_kotlin<'a>(
    alias: &TypeAlias,
    pkg: &'a CurrentRenderPackage,
    drop_type: Option<&String>,
) -> TypeAliasKotlin<'a> {
    let non_streaming = alias.elem.r#type.elem.to_non_streaming_type(pkg.lookup());
    let lookup = LookupWithDrop {
        lookup: pkg.lookup(),
        drop_type,
    };
    TypeAliasKotlin {
        name: alias.elem.name.clone(),
        type_: ir_to_kotlin::type_to_kotlin(&non_streaming, &lookup),
        docstring: alias
            .elem
            .docstring
            .clone()
            .map(|docstring| docstring.0.clone()),
        pkg,
    }
}

pub fn ir_type_alias_to_kotlin_stream<'a>(
    alias: &TypeAlias,
    pkg: &'a CurrentRenderPackage,
    drop_type: Option<&String>,
) -> TypeAliasKotlin<'a> {
    let partialized = alias.elem.r#type.elem.to_streaming_type(pkg.lookup());

    let lookup = LookupWithDrop {
        lookup: pkg.lookup(),
        drop_type,
    };
    TypeAliasKotlin {
        name: alias.elem.name.clone(),
        type_: ir_to_kotlin::stream_type_to_kotlin(&partialized, &lookup),
        docstring: alias
            .elem
            .docstring
            .clone()
            .map(|docstring| docstring.0.clone()),
        pkg,
    }
}
