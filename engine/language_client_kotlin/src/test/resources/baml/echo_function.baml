// Echo function for concurrency and error-handling tests.
// Requires: openrouter_client.baml (or fake_client.baml)

function Echo(input: string) -> string {
    client MyClient
    prompt #"
        Reply with exactly this text, nothing else: {{ input }}
    "#
}
