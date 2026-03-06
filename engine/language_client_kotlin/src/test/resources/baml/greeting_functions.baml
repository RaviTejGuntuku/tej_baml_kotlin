// Functions for testing basic function calls with string args and returns.
// Requires: openrouter_client.baml

function GetGreeting(name: string) -> string {
    client MyClient
    prompt #"
        Say hello to {{ name }}. Reply with just the greeting.
    "#
}

function Translate(text: string, language: string) -> string {
    client MyClient
    prompt #"
        Translate "{{ text }}" to {{ language }}. Reply with just the translation.
    "#
}
