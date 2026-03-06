// Fake client for tests that don't need real LLM calls
// (runtime lifecycle, error handling).

client<llm> MyClient {
    provider openai
    options {
        model "gpt-4o-mini"
        api_key "fake-key"
    }
}
