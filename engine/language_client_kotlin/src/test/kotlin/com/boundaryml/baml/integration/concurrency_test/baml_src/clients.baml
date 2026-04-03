client<llm> MyClient {
    provider openai
    options {
        model "openai/gpt-4o-mini"
        api_key env.OPENROUTER_API_KEY
        base_url "https://openrouter.ai/api"
    }
}
