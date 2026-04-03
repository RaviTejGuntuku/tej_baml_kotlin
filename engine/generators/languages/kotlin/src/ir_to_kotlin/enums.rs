use internal_baml_core::ir::Enum;

use crate::package::CurrentRenderPackage;

pub fn ir_enum_to_kotlin<'a>(
    enum_: &Enum,
    pkg: &'a CurrentRenderPackage,
) -> crate::generated_types::EnumKotlin<'a> {
    crate::generated_types::EnumKotlin {
        name: enum_.elem.name.clone(),
        values: enum_
            .elem
            .values
            .iter()
            .map(|(val, doc_string)| (val.elem.0.clone(), doc_string.as_ref().map(|d| d.0.clone())))
            .collect(),
        docstring: enum_.elem.docstring.as_ref().map(|d| d.0.clone()),
        dynamic: enum_.attributes.dynamic(),
        pkg,
    }
}

#[cfg(test)]
mod tests {
    use internal_baml_core::ir::{repr::make_test_ir, IRHelper};

    use super::*;

    #[test]
    fn test_enum_basic() {
        let ir = make_test_ir(
            "enum Color {\n  Red\n  Green\n  Blue\n}",
        )
        .unwrap();
        let ir = std::sync::Arc::new(ir);
        let enm = ir.find_enum("Color").unwrap().item;
        let pkg = CurrentRenderPackage::new("baml_client", ir.clone());
        let enum_kt = ir_enum_to_kotlin(enm, &pkg);
        assert_eq!(enum_kt.name, "Color");
        let values: Vec<&str> = enum_kt.values.iter().map(|(v, _)| v.as_str()).collect();
        assert_eq!(values, vec!["Red", "Green", "Blue"]);
    }

    #[test]
    fn test_enum_with_docstring() {
        let ir = make_test_ir(
            "/// Sentiment analysis result\nenum Sentiment {\n  POSITIVE\n  NEGATIVE\n  NEUTRAL\n}",
        )
        .unwrap();
        let ir = std::sync::Arc::new(ir);
        let enm = ir.find_enum("Sentiment").unwrap().item;
        let pkg = CurrentRenderPackage::new("baml_client", ir.clone());
        let enum_kt = ir_enum_to_kotlin(enm, &pkg);
        assert_eq!(enum_kt.docstring, Some("Sentiment analysis result".to_string()));
        assert_eq!(enum_kt.values.len(), 3);
    }

    #[test]
    fn test_dynamic_enum() {
        let ir = make_test_ir(
            "enum Category {\n  Tech\n  Science\n  @@dynamic\n}",
        )
        .unwrap();
        let ir = std::sync::Arc::new(ir);
        let enm = ir.find_enum("Category").unwrap().item;
        let pkg = CurrentRenderPackage::new("baml_client", ir.clone());
        let enum_kt = ir_enum_to_kotlin(enm, &pkg);
        assert!(enum_kt.dynamic, "Expected dynamic enum");
        assert_eq!(enum_kt.values.len(), 2);
    }

    #[test]
    fn test_single_value_enum() {
        let ir = make_test_ir(
            "enum YesNo {\n  Yes\n}",
        )
        .unwrap();
        let ir = std::sync::Arc::new(ir);
        let enm = ir.find_enum("YesNo").unwrap().item;
        let pkg = CurrentRenderPackage::new("baml_client", ir.clone());
        let enum_kt = ir_enum_to_kotlin(enm, &pkg);
        assert_eq!(enum_kt.values.len(), 1);
        assert_eq!(enum_kt.values[0].0, "Yes");
    }
}
