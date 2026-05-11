function ExtractPerson(input: string) -> Person {
    client MyClient
    prompt #"
        Extract a person from this text. Return JSON with "name" (string) and "age" (integer).
        Text: {{ input }}

        {{ ctx.output_format }}
    "#
}

function ClassifySentiment(text: string) -> Sentiment {
    client MyClient
    prompt #"
        Classify the sentiment of this text as POSITIVE, NEGATIVE, or NEUTRAL.
        Return JSON with a single "value" field containing the enum string.
        Text: {{ text }}

        {{ ctx.output_format }}
    "#
}

function ExtractReceipt(text: string) -> Receipt {
    client MyClient
    prompt #"
        Extract receipt details from this text.
        Return JSON with "store_name" (string), "total" (float), and "items" (list of strings).
        Text: {{ text }}

        {{ ctx.output_format }}
    "#
}
