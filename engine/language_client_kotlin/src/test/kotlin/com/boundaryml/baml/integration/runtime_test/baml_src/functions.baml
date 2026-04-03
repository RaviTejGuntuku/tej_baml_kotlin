function ExtractName(input: string) -> string {
    client MyClient
    prompt #"
        Extract the name from: {{ input }}
    "#
}
