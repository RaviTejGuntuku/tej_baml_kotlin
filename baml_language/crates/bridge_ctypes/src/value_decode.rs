//! Decode C FFI / protobuf host values into `BexExternalValue`.
//!
//! Converts `InboundValue` (from the C bridge) to the engine's `BexExternalValue` representation
//! so the BEX engine can use them as function arguments.

use std::collections::HashMap;

use bex_project::{BexExternalAdt, BexExternalValue, MediaContent, MediaValue, Ty};
use baml_type::MediaKind;
use indexmap::IndexMap;

use crate::{
    baml::cffi::{
        InboundClassValue, InboundEnumValue, InboundListValue, InboundMapEntry, InboundMapValue,
        InboundValue, inbound_value::Value as InboundValueVariant,
    },
    error::CtypesError,
    handle_table::HandleTable,
};

/// Decode a protobuf `InboundValue` into a `BexExternalValue` for use by the BEX engine.
///
/// Handles are resolved via `handle_table`; an unknown key returns `InvalidHandleKey`.
pub fn inbound_to_external(
    value: InboundValue,
    handle_table: &HandleTable,
) -> Result<BexExternalValue, CtypesError> {
    match value.value {
        None => Ok(BexExternalValue::Null),
        Some(variant) => match variant {
            InboundValueVariant::StringValue(s) => Ok(BexExternalValue::String(s)),
            InboundValueVariant::IntValue(i) => Ok(BexExternalValue::Int(i)),
            InboundValueVariant::FloatValue(f) => Ok(BexExternalValue::Float(f)),
            InboundValueVariant::BoolValue(b) => Ok(BexExternalValue::Bool(b)),
            InboundValueVariant::ListValue(list) => convert_list(list, handle_table),
            InboundValueVariant::MapValue(map) => convert_map(map, handle_table),
            InboundValueVariant::ClassValue(class) => convert_class(class, handle_table),
            InboundValueVariant::EnumValue(e) => Ok(convert_enum(e)),
            InboundValueVariant::Handle(handle) => {
                let value = handle_table
                    .resolve(handle.key)
                    .ok_or(CtypesError::InvalidHandleKey(handle.key))?;
                Ok(BexExternalValue::from((*value).clone()))
            }
        },
    }
}

/// Build the default "any scalar" union type for untyped inbound values.
fn default_scalar_union_ty() -> Ty {
    let d = baml_type::TyAttr::default();
    Ty::Union(
        vec![
            Ty::Int { attr: d.clone() },
            Ty::Float { attr: d.clone() },
            Ty::String { attr: d.clone() },
            Ty::Bool { attr: d.clone() },
            Ty::Null { attr: d.clone() },
        ],
        d,
    )
}

fn convert_list(
    list: InboundListValue,
    handle_table: &HandleTable,
) -> Result<BexExternalValue, CtypesError> {
    let items: Result<Vec<BexExternalValue>, CtypesError> = list
        .values
        .into_iter()
        .map(|v| inbound_to_external(v, handle_table))
        .collect();
    Ok(BexExternalValue::Array {
        element_type: default_scalar_union_ty(),
        items: items?,
    })
}

fn convert_map(
    map: InboundMapValue,
    handle_table: &HandleTable,
) -> Result<BexExternalValue, CtypesError> {
    let mut entries = IndexMap::new();
    for entry in map.entries {
        let key = extract_string_key(&entry)?;
        let value = entry
            .value
            .map(|v| inbound_to_external(v, handle_table))
            .transpose()?
            .unwrap_or(BexExternalValue::Null);
        entries.insert(key, value);
    }
    Ok(BexExternalValue::Map {
        key_type: Ty::String {
            attr: baml_type::TyAttr::default(),
        },
        value_type: default_scalar_union_ty(),
        entries,
    })
}

fn convert_class(
    class: InboundClassValue,
    handle_table: &HandleTable,
) -> Result<BexExternalValue, CtypesError> {
    let mut fields = IndexMap::new();
    for entry in class.fields {
        let key = extract_string_key(&entry)?;
        let value = entry
            .value
            .map(|v| inbound_to_external(v, handle_table))
            .transpose()?
            .unwrap_or(BexExternalValue::Null);
        fields.insert(key, value);
    }
    if let Some(media) = convert_media_class(&class.name, &fields) {
        return Ok(media);
    }
    Ok(BexExternalValue::Instance {
        class_name: class.name,
        fields,
    })
}

fn convert_enum(e: InboundEnumValue) -> BexExternalValue {
    BexExternalValue::Variant {
        enum_name: e.name,
        variant_name: e.value,
    }
}

fn extract_string_key(entry: &InboundMapEntry) -> Result<String, CtypesError> {
    use crate::baml::cffi::inbound_map_entry::Key;
    match &entry.key {
        Some(Key::StringKey(s)) => Ok(s.clone()),
        Some(Key::IntKey(i)) => Ok(i.to_string()),
        Some(Key::BoolKey(b)) => Ok(b.to_string()),
        Some(Key::EnumKey(e)) => Ok(format!("{}::{}", e.name, e.value)),
        None => Err(CtypesError::MapEntryMissingKey),
    }
}

fn convert_media_class(
    class_name: &str,
    fields: &IndexMap<String, BexExternalValue>,
) -> Option<BexExternalValue> {
    let kind = match class_name {
        "Image" => MediaKind::Image,
        "Audio" => MediaKind::Audio,
        "Pdf" => MediaKind::Pdf,
        "Video" => MediaKind::Video,
        _ => match get_string_field(fields, "media_type")?.as_str() {
            "image" => MediaKind::Image,
            "audio" => MediaKind::Audio,
            "pdf" => MediaKind::Pdf,
            "video" => MediaKind::Video,
            _ => return None,
        },
    };

    let mime_type = get_optional_string_field(fields, "mime_type");
    let content = if let Some(url) = get_string_field(fields, "url") {
        MediaContent::Url {
            url,
            base64_data: get_optional_string_field(fields, "base64"),
        }
    } else if let Some(base64_data) = get_string_field(fields, "base64") {
        MediaContent::Base64 { base64_data }
    } else {
        return None;
    };

    Some(BexExternalValue::Adt(BexExternalAdt::Media(
        MediaValue::new(kind, content, mime_type).into(),
    )))
}

fn get_string_field(
    fields: &IndexMap<String, BexExternalValue>,
    key: &str,
) -> Option<String> {
    match fields.get(key) {
        Some(BexExternalValue::String(value)) => Some(value.clone()),
        _ => None,
    }
}

fn get_optional_string_field(
    fields: &IndexMap<String, BexExternalValue>,
    key: &str,
) -> Option<String> {
    match fields.get(key) {
        Some(BexExternalValue::String(value)) => Some(value.clone()),
        Some(BexExternalValue::Null) | None => None,
        _ => None,
    }
}

/// Decode protobuf kwargs into a `HashMap<String, BexExternalValue>` for engine call arguments.
pub fn kwargs_to_bex_values(
    kwargs: Vec<InboundMapEntry>,
    handle_table: &HandleTable,
) -> Result<HashMap<String, BexExternalValue>, CtypesError> {
    let mut result = HashMap::new();
    for entry in kwargs {
        let key = extract_string_key(&entry)?;
        let value = entry
            .value
            .map(|v| inbound_to_external(v, handle_table))
            .transpose()?
            .unwrap_or(BexExternalValue::Null);
        result.insert(key, value);
    }
    Ok(result)
}
