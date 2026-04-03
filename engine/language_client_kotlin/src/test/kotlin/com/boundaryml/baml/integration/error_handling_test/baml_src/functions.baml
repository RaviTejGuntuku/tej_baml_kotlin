function Echo(input: string) -> string {
    client MyClient
    prompt #"
        Reply with exactly this text, nothing else: {{ input }}
    "#
}
