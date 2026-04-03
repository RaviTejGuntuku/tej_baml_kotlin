use internal_baml_core::ir::{Class, Field};

use crate::{
    generated_types::{ClassKotlin, FieldKotlin},
    package::CurrentRenderPackage,
};

pub fn ir_class_to_kotlin<'a>(class: &Class, pkg: &'a CurrentRenderPackage) -> ClassKotlin<'a> {
    ClassKotlin {
        name: class.elem.name.clone(),
        docstring: class
            .elem
            .docstring
            .clone()
            .map(|docstring| docstring.0.clone()),
        dynamic: class.attributes.dynamic(),
        pkg,
        fields: class
            .elem
            .static_fields
            .iter()
            .map(|field| ir_field_to_kotlin(field, pkg))
            .collect(),
    }
}

pub fn ir_class_to_kotlin_stream<'a>(
    class: &Class,
    pkg: &'a CurrentRenderPackage,
) -> ClassKotlin<'a> {
    ClassKotlin {
        name: class.elem.name.clone(),
        docstring: class
            .elem
            .docstring
            .clone()
            .map(|docstring| docstring.0.clone()),
        dynamic: class.attributes.dynamic(),
        pkg,
        fields: class
            .elem
            .static_fields
            .iter()
            .map(|field| ir_field_to_kotlin_stream(field, pkg))
            .collect(),
    }
}

fn ir_field_to_kotlin<'a>(field: &Field, pkg: &'a CurrentRenderPackage) -> FieldKotlin<'a> {
    let non_streaming = field.elem.r#type.elem.to_non_streaming_type(pkg.lookup());
    let kotlin_type = super::type_to_kotlin(&non_streaming, pkg.lookup());

    FieldKotlin {
        name: field.elem.name.clone(),
        r#type: kotlin_type,
        docstring: field
            .elem
            .docstring
            .clone()
            .map(|docstring| docstring.0.clone()),
        pkg,
    }
}

fn ir_field_to_kotlin_stream<'a>(field: &Field, pkg: &'a CurrentRenderPackage) -> FieldKotlin<'a> {
    let partialized = field.elem.r#type.elem.to_streaming_type(pkg.lookup());
    let kotlin_type = super::stream_type_to_kotlin(&partialized, pkg.lookup());

    FieldKotlin {
        name: field.elem.name.clone(),
        r#type: kotlin_type,
        docstring: field
            .elem
            .docstring
            .clone()
            .map(|docstring| docstring.0.clone()),
        pkg,
    }
}

#[cfg(test)]
mod tests {
    use internal_baml_core::ir::{repr::make_test_ir, IRHelper};

    use super::*;
    use crate::r#type::TypeKotlin;

    #[test]
    fn test_ir_class_to_kotlin() {
        let ir: dir_writer::IntermediateRepr = make_test_ir(
            r#"
            class SimpleClass {
                words string @stream.with_state
            }
        "#,
        )
        .unwrap();
        let ir = std::sync::Arc::new(ir);
        let class = ir.find_class("SimpleClass").unwrap().item;
        let pkg = CurrentRenderPackage::new("baml_client", ir.clone());
        let class_kotlin = ir_class_to_kotlin_stream(class, &pkg);
        assert_eq!(class_kotlin.name, "SimpleClass");
        assert_eq!(class_kotlin.fields.len(), 1);
        assert!(matches!(
            class_kotlin.fields[0].r#type,
            TypeKotlin::StreamState(_)
        ));
    }

    #[test]
    fn test_ir_class_to_kotlin_needed_field() {
        let ir = make_test_ir(
            r#"
            class ChildClass {
                digits int @stream.with_state @stream.not_null
            }
        "#,
        )
        .unwrap();
        let ir = std::sync::Arc::new(ir);
        let class = ir.find_class("ChildClass").unwrap().item;
        let pkg = CurrentRenderPackage::new("baml_client", ir.clone());
        let class_kotlin = ir_class_to_kotlin_stream(class, &pkg);
        let digits_field = class_kotlin
            .fields
            .iter()
            .find(|f| f.name == "digits")
            .unwrap();
        assert!(matches!(digits_field.r#type, TypeKotlin::StreamState(_)));
        assert_eq!(class_kotlin.name, "ChildClass");
        assert_eq!(class_kotlin.fields.len(), 1);
    }

    #[test]
    fn test_class_with_field_docstring() {
        let ir = make_test_ir(
            r#"
        class Foo {
            /// ds
            bar string @description("d")
        }
        "#,
        )
        .expect("Valid IR");
        let ir = std::sync::Arc::new(ir);
        let class = ir.find_class("Foo").unwrap().item;
        let pkg = CurrentRenderPackage::new("baml_client", ir.clone());
        let class_kotlin = ir_class_to_kotlin_stream(class, &pkg);
        assert_eq!(class_kotlin.fields[0].docstring, Some("ds".to_string()));
    }
}
