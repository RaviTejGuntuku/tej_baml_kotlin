//! OpenAI-format HTTP request builder.
//!
//! Supports: `OpenAi`, `OpenAiGeneric`, `AzureOpenAi`, Ollama, `OpenRouter`.

use baml_builtins::PromptAst;
use baml_type::Ty;
use indexmap::IndexMap;

use super::{BuildRequestError, LlmPrimitiveClient, LlmRequestBuilder, get_string_option};
use crate::LlmProvider;

/// Builder for OpenAI-compatible providers.
pub(crate) struct OpenAiBuilder<'a> {
    provider: &'a LlmProvider,
}

impl<'a> OpenAiBuilder<'a> {
    pub(crate) fn new(provider: &'a LlmProvider) -> Self {
        Self { provider }
    }
}

impl LlmRequestBuilder for OpenAiBuilder<'_> {
    fn provider_skip_keys(&self) -> &'static [&'static str] {
        &["resource_name", "api_version"]
    }

    fn build_url(&self, client: &LlmPrimitiveClient) -> Result<String, BuildRequestError> {
        let base_url = get_string_option(client, "base_url")
            .unwrap_or_else(|| "https://api.openai.com".to_string());

        // Azure uses a different URL pattern
        if *self.provider == LlmProvider::AzureOpenAi {
            let deployment = get_string_option(client, "resource_name")
                .ok_or_else(|| BuildRequestError::MissingOption("resource_name".into()))?;
            let model = get_string_option(client, "model")
                .ok_or_else(|| BuildRequestError::MissingOption("model".into()))?;
            let api_version = get_string_option(client, "api_version")
                .unwrap_or_else(|| "2024-02-15-preview".to_string());
            return Ok(format!(
                "https://{deployment}.openai.azure.com/openai/deployments/{model}/chat/completions?api-version={api_version}"
            ));
        }

        Ok(format!("{base_url}/v1/chat/completions"))
    }

    fn build_auth_headers(&self, client: &LlmPrimitiveClient) -> IndexMap<String, String> {
        let mut headers = IndexMap::new();
        if let Some(api_key) = get_string_option(client, "api_key") {
            if *self.provider == LlmProvider::AzureOpenAi {
                headers.insert("api-key".to_string(), api_key);
            } else {
                headers.insert("authorization".to_string(), format!("Bearer {api_key}"));
            }
        }
        headers
    }

    fn build_prompt_body(
        &self,
        prompt: bex_vm_types::PromptAst,
        output_type: &Ty,
    ) -> serde_json::Map<String, serde_json::Value> {
        let mut map = serde_json::Map::new();
        let messages = prompt_to_openai_messages(&prompt);
        map.insert("messages".to_string(), serde_json::Value::Array(messages));
        if requires_json_object_response(output_type) {
            map.insert(
                "response_format".to_string(),
                serde_json::json!({ "type": "json_object" }),
            );
        }
        map
    }
}

fn requires_json_object_response(output_type: &Ty) -> bool {
    !matches!(
        output_type,
        Ty::Null { .. }
            | Ty::Int { .. }
            | Ty::Float { .. }
            | Ty::Bool { .. }
            | Ty::String { .. }
    )
}

#[cfg(test)]
mod tests {
    use super::requires_json_object_response;

    #[test]
    fn enables_json_object_for_structured_outputs() {
        assert!(requires_json_object_response(&baml_type::Ty::List(
            Box::new(baml_type::Ty::String {
                attr: baml_type::TyAttr::default(),
            }),
            baml_type::TyAttr::default(),
        )));
        assert!(!requires_json_object_response(&baml_type::Ty::String {
            attr: baml_type::TyAttr::default(),
        }));
    }
}

/// Convert `PromptAst` to `OpenAI` message format.
///
/// `OpenAI` format:
/// ```json
/// [{"role": "system", "content": "..."}, {"role": "user", "content": [{"type": "text", "text": "..."}]}]
/// ```
fn prompt_to_openai_messages(prompt: &bex_vm_types::PromptAst) -> Vec<serde_json::Value> {
    match prompt.as_ref() {
        PromptAst::Vec(items) => {
            let mut messages = Vec::new();
            let mut pending_non_messages: Vec<bex_vm_types::PromptAst> = Vec::new();

            for item in items {
                if let Some(message) = prompt_node_to_message(item) {
                    if !pending_non_messages.is_empty() {
                        let role = if messages.is_empty() { "system" } else { "user" };
                        messages.push(wrap_as_message(
                            &std::sync::Arc::new(PromptAst::Vec(pending_non_messages)),
                            role,
                        ));
                        pending_non_messages = Vec::new();
                    }
                    messages.push(message);
                } else {
                    pending_non_messages.push(item.clone());
                }
            }

            if !pending_non_messages.is_empty() {
                messages.push(wrap_as_message(
                    &std::sync::Arc::new(PromptAst::Vec(pending_non_messages)),
                    "user",
                ));
            }

            messages
        }
        PromptAst::Message { .. } => prompt_node_to_message(prompt).into_iter().collect(),
        PromptAst::Simple(_) => {
            // Plain text prompt without role markers — wrap as a user message
            vec![wrap_as_message(prompt, "user")]
        }
    }
}

/// Wrap a non-Message PromptAst as a message with provider content parts.
fn wrap_as_message(prompt: &bex_vm_types::PromptAst, role: &str) -> serde_json::Value {
    let mut msg = serde_json::Map::new();
    msg.insert("role".to_string(), serde_json::Value::String(role.to_string()));
    msg.insert(
        "content".to_string(),
        serde_json::Value::Array(prompt_node_to_openai_content_parts(prompt)),
    );
    serde_json::Value::Object(msg)
}

fn prompt_node_to_openai_content_parts(node: &bex_vm_types::PromptAst) -> Vec<serde_json::Value> {
    match node.as_ref() {
        PromptAst::Simple(content) => prompt_to_openai_content_parts_simple(content),
        PromptAst::Vec(items) => items
            .iter()
            .flat_map(|item| prompt_node_to_openai_content_parts(item))
            .collect(),
        PromptAst::Message { content, .. } => prompt_to_openai_content_parts_simple(content.as_ref()),
    }
}

fn prompt_node_to_message(node: &bex_vm_types::PromptAst) -> Option<serde_json::Value> {
    match node.as_ref() {
        PromptAst::Message {
            role,
            content,
            metadata,
        } => {
            let content_parts = prompt_to_openai_content_parts_simple(content.as_ref());
            let mut msg = serde_json::Map::new();
            msg.insert("role".to_string(), serde_json::Value::String(role.clone()));

            // Always use array format for content parts.
            msg.insert(
                "content".to_string(),
                serde_json::Value::Array(content_parts),
            );

            // TODO: Add metadata (e.g., cache_control) when metadata is available.
            // PromptAst::Message has metadata: Value; would need heap resolution to read map.
            let _ = metadata;

            Some(serde_json::Value::Object(msg))
        }
        _ => None, // Skip non-message nodes at top level
    }
}

fn prompt_to_openai_content_parts_simple(
    content: &baml_builtins::PromptAstSimple,
) -> Vec<serde_json::Value> {
    match content {
        baml_builtins::PromptAstSimple::String(s) => {
            vec![serde_json::json!({"type": "text", "text": s})]
        }
        baml_builtins::PromptAstSimple::Media(media) => {
            media.read_content(|f| match f {
                baml_builtins::MediaContent::Url { url, .. } => {
                    vec![serde_json::json!({"type": "image_url", "image_url": {"url": url}})]
                }
                baml_builtins::MediaContent::Base64 { base64_data, .. } => {
                    vec![serde_json::json!({
                        "type": "image_url",
                        "image_url": {
                            "url": format!(
                                "data:{};base64,{}",
                                media.mime_type.as_deref().unwrap_or("image/png"),
                                base64_data
                            )
                        }
                    })]
                }
                baml_builtins::MediaContent::File { file, .. } => {
                    vec![serde_json::json!({"type": "file", "file_id": file})]
                }
            })
        }
        baml_builtins::PromptAstSimple::Multiple(parts) => parts
            .iter()
            .flat_map(|part| prompt_to_openai_content_parts_simple(part.as_ref()))
            .collect(),
    }
}
