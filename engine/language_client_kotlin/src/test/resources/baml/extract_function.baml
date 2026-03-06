// Extract function for runtime lifecycle tests.
// Requires: fake_client.baml (or openrouter_client.baml)

function ExtractName(input: string) -> string {
    client MyClient
    prompt #"
        Extract the name from: {{ input }}
    "#
}
