// Story function for streaming tests.
// Requires: openrouter_client.baml

function TellStory(topic: string) -> string {
    client MyClient
    prompt #"
        Tell a very short story (2 sentences) about {{ topic }}.
    "#
}
