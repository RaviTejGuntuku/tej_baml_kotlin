use internal_baml_core::ir::FunctionNode;

use super::{stream_type_to_kotlin, type_to_kotlin};
use crate::{functions::FunctionKotlin, package::CurrentRenderPackage};

pub fn ir_function_to_kotlin(
    function: &FunctionNode,
    pkg: &CurrentRenderPackage,
) -> FunctionKotlin {
    FunctionKotlin {
        documentation: None,
        name: function.elem.name().to_string(),
        args: function
            .elem
            .inputs()
            .iter()
            .map(|(name, field_type)| {
                (
                    name.clone(),
                    type_to_kotlin(
                        &field_type.to_non_streaming_type(pkg.lookup()),
                        pkg.lookup(),
                    ),
                )
            })
            .collect(),
        return_type: type_to_kotlin(
            &function.elem.output().to_non_streaming_type(pkg.lookup()),
            pkg.lookup(),
        ),
        stream_return_type: stream_type_to_kotlin(
            &function.elem.output().to_streaming_type(pkg.lookup()),
            pkg.lookup(),
        ),
    }
}

#[cfg(test)]
mod tests {
    use internal_baml_core::ir::repr::make_test_ir;

    use super::*;
    use crate::r#type::{SerializeType, TypeKotlin};

    #[test]
    fn test_simple_function() {
        let ir = make_test_ir(
            r##"
            function GetGreeting(name: string) -> string {
                client "openai/gpt-4o"
                prompt #"Say hello to {{ name }}"#
            }
            "##,
        )
        .unwrap();
        let ir = std::sync::Arc::new(ir);
        let pkg = CurrentRenderPackage::new("baml_client", ir.clone());
        let func = ir_function_to_kotlin(&ir.functions[0], &pkg);

        assert_eq!(func.name, "GetGreeting");
        assert_eq!(func.args.len(), 1);
        assert_eq!(func.args[0].0, "name");
        assert!(matches!(func.args[0].1, TypeKotlin::String(None)));

        pkg.set("baml_client.types");
        assert_eq!(func.return_type.serialize_type(&pkg), "String");
    }

    #[test]
    fn test_multi_arg_function() {
        let ir = make_test_ir(
            r##"
            function Translate(text: string, language: string) -> string {
                client "openai/gpt-4o"
                prompt #"Translate {{ text }} to {{ language }}"#
            }
            "##,
        )
        .unwrap();
        let ir = std::sync::Arc::new(ir);
        let pkg = CurrentRenderPackage::new("baml_client", ir.clone());
        let func = ir_function_to_kotlin(&ir.functions[0], &pkg);

        assert_eq!(func.name, "Translate");
        assert_eq!(func.args.len(), 2);
        assert_eq!(func.args[0].0, "text");
        assert_eq!(func.args[1].0, "language");
    }

    #[test]
    fn test_function_with_class_return() {
        let ir = make_test_ir(
            r##"
            class Person { name string age int }
            function ExtractPerson(text: string) -> Person {
                client "openai/gpt-4o"
                prompt #"Extract: {{ text }}"#
            }
            "##,
        )
        .unwrap();
        let ir = std::sync::Arc::new(ir);
        let pkg = CurrentRenderPackage::new("baml_client", ir.clone());
        let func = ir_function_to_kotlin(&ir.functions[0], &pkg);

        assert_eq!(func.name, "ExtractPerson");
        pkg.set("baml_client.types");
        assert_eq!(func.return_type.serialize_type(&pkg), "Person");
    }

    #[test]
    fn test_function_with_enum_return() {
        let ir = make_test_ir(
            r##"
            enum Sentiment {
                POSITIVE
                NEGATIVE
                NEUTRAL
            }
            function ClassifySentiment(text: string) -> Sentiment {
                client "openai/gpt-4o"
                prompt #"Classify: {{ text }}"#
            }
            "##,
        )
        .unwrap();
        let ir = std::sync::Arc::new(ir);
        let pkg = CurrentRenderPackage::new("baml_client", ir.clone());
        let func = ir_function_to_kotlin(&ir.functions[0], &pkg);

        pkg.set("baml_client.types");
        assert_eq!(func.return_type.serialize_type(&pkg), "Sentiment");
    }

    #[test]
    fn test_function_with_list_return() {
        let ir = make_test_ir(
            r##"
            function ListNames(text: string) -> string[] {
                client "openai/gpt-4o"
                prompt #"List names in: {{ text }}"#
            }
            "##,
        )
        .unwrap();
        let ir = std::sync::Arc::new(ir);
        let pkg = CurrentRenderPackage::new("baml_client", ir.clone());
        let func = ir_function_to_kotlin(&ir.functions[0], &pkg);

        pkg.set("baml_client.types");
        assert_eq!(func.return_type.serialize_type(&pkg), "List<String>");
    }

    #[test]
    fn test_function_with_optional_return() {
        let ir = make_test_ir(
            r##"
            function MaybeName(text: string) -> string? {
                client "openai/gpt-4o"
                prompt #"Name in: {{ text }}"#
            }
            "##,
        )
        .unwrap();
        let ir = std::sync::Arc::new(ir);
        let pkg = CurrentRenderPackage::new("baml_client", ir.clone());
        let func = ir_function_to_kotlin(&ir.functions[0], &pkg);

        pkg.set("baml_client.types");
        assert_eq!(func.return_type.serialize_type(&pkg), "String?");
    }

    #[test]
    fn test_function_with_map_return() {
        let ir = make_test_ir(
            r##"
            function ExtractMap(text: string) -> map<string, int> {
                client "openai/gpt-4o"
                prompt #"Extract counts: {{ text }}"#
            }
            "##,
        )
        .unwrap();
        let ir = std::sync::Arc::new(ir);
        let pkg = CurrentRenderPackage::new("baml_client", ir.clone());
        let func = ir_function_to_kotlin(&ir.functions[0], &pkg);

        pkg.set("baml_client.types");
        assert_eq!(func.return_type.serialize_type(&pkg), "Map<String, Long>");
    }

    #[test]
    fn test_function_with_mixed_arg_types() {
        let ir = make_test_ir(
            r##"
            class Config { model string temperature float }
            function Generate(prompt: string, count: int, config: Config) -> string[] {
                client "openai/gpt-4o"
                prompt #"{{ prompt }} count={{ count }}"#
            }
            "##,
        )
        .unwrap();
        let ir = std::sync::Arc::new(ir);
        let pkg = CurrentRenderPackage::new("baml_client", ir.clone());
        let func = ir_function_to_kotlin(&ir.functions[0], &pkg);

        assert_eq!(func.args.len(), 3);
        assert_eq!(func.args[0].0, "prompt");
        assert_eq!(func.args[1].0, "count");
        assert_eq!(func.args[2].0, "config");
        assert!(matches!(func.args[0].1, TypeKotlin::String(None)));
        assert!(matches!(func.args[1].1, TypeKotlin::Int(None)));
        assert!(matches!(func.args[2].1, TypeKotlin::Class { .. }));
    }
}
