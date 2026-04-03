# BAML Kitchen Sink — Android Demo App

A single-activity Jetpack Compose app that exercises every major BAML Kotlin SDK capability: basic calls, streaming, structured output, multimodal input, enums, unions, concurrency, and client overrides.

## Architecture

- Single-activity, Compose-only (no XML)
- Bottom nav with 5-6 tabs, each targeting a distinct BAML feature
- One ViewModel per tab, shared `BamlClient` singleton
- Coroutines throughout — `viewModelScope.launch` for blocking, `Flow.collect` for streaming
- Minimal dependencies: Compose, Navigation, BAML SDK

## Tabs

### Tab 1: Chat (Basic Calls + Streaming)

- Simple chat UI with message bubbles
- User types a prompt, BAML function returns a response
- Toggle between **blocking** (`b.SummarizeText(...)`) and **streaming** (tokens flow in live via `BamlStream.partials`)
- **Demonstrates:** basic function call, streaming, string output

### Tab 2: Structured Extract (Typed Output)

- User pastes or types a receipt/invoice description
- Calls `b.ExtractReceipt(text)` → returns a `Receipt` data class (merchant, total, line items)
- Renders the parsed fields in a card layout — proves TypeMap → data class deserialization
- **Demonstrates:** structured output, nested data classes, lists

### Tab 3: Vision (Multimodal Input)

- Camera button + gallery picker
- User takes/selects a photo → `BamlImage.fromBase64(bytes)`
- Calls `b.DescribeImage(image)` → returns structured `ImageDescription(objects: List<String>, scene: String, mood: Sentiment)`
- Also has a URL field to test `BamlImage.fromUrl(url)`
- **Demonstrates:** multimodal input (image), enum output (`Sentiment`), structured output

### Tab 4: Classify (Enums + Unions)

- User enters a support ticket message
- Calls `b.ClassifyTicket(text)` → returns `TicketCategory` enum (Billing, Technical, Account, Other)
- Below that, calls `b.RouteTicket(text)` → returns a union type (`EscalateToHuman | AutoReply(message: String)`)
- **Demonstrates:** enum output, union/sealed class output

### Tab 5: Parallel (Concurrency)

- "Analyze" button that fires 3-4 BAML calls concurrently on the same input text:
  - `b.Summarize(text)`
  - `b.ExtractKeywords(text)` → `List<String>`
  - `b.DetectLanguage(text)` → `Language` enum
  - `b.SentimentAnalysis(text)` → `Sentiment` enum
- All launched via `coroutineScope { async { ... } }`, results populate cards as they resolve
- Shows elapsed time per call and total wall time (proving parallelism)
- **Demonstrates:** concurrency, multiple return types

### Tab 6: Settings / Advanced

- **Client override** dropdown (select between "openai", "anthropic") — calls `b.withOptions(client = "...")`
- **Environment** — shows which BAML client/model is active
- **Error handling** demo — intentionally malformed input to show BAML error types
- **Parse** demo — raw LLM text → `b.parse.ExtractReceipt(rawText)` (once `call_function_parse` lands)

## BAML Source Files Needed

```
generators/
  generator kotlin {
    output_type "kotlin"
    output_dir "../app/src/main/java/com/demo/baml_client"
  }

functions/
  SummarizeText.baml        # string → string
  ExtractReceipt.baml       # string → Receipt class
  DescribeImage.baml        # BamlImage → ImageDescription
  ClassifyTicket.baml       # string → TicketCategory enum
  RouteTicket.baml          # string → EscalateToHuman | AutoReply (union)
  ExtractKeywords.baml      # string → string[]
  DetectLanguage.baml       # string → Language enum
  SentimentAnalysis.baml    # string → Sentiment enum

types/
  Receipt.baml              # class with nested LineItem
  ImageDescription.baml     # class + Sentiment enum
  TicketCategory.baml       # enum
  RoutingDecision.baml      # union type
  Language.baml             # enum
```

## Project Structure

```
app/
├── src/main/java/com/demo/bamlkitchensink/
│   ├── MainActivity.kt              # Single activity, NavHost
│   ├── BamlProvider.kt              # Singleton BamlClient init
│   ├── ui/
│   │   ├── chat/ChatScreen.kt       # Tab 1
│   │   ├── extract/ExtractScreen.kt # Tab 2
│   │   ├── vision/VisionScreen.kt   # Tab 3
│   │   ├── classify/ClassifyScreen.kt # Tab 4
│   │   ├── parallel/ParallelScreen.kt # Tab 5
│   │   └── settings/SettingsScreen.kt # Tab 6
│   └── viewmodel/
│       ├── ChatViewModel.kt
│       ├── ExtractViewModel.kt
│       ├── VisionViewModel.kt
│       ├── ClassifyViewModel.kt
│       └── ParallelViewModel.kt
├── src/main/baml_src/               # BAML source files
└── build.gradle.kts
```

## Feature Coverage Matrix

| Tab | BAML Feature | Kotlin SDK Surface |
|---|---|---|
| Chat | Basic call, streaming | `callFunction()`, `streamFunction()`, `Flow<Partial>` |
| Extract | Structured output | Data class deserialization, nested types |
| Vision | Multimodal input | `BamlImage.fromBase64()`, `BamlImage.fromUrl()` |
| Classify | Enums, unions | Enum classes, sealed classes |
| Parallel | Concurrency | `async/await` with multiple simultaneous calls |
| Settings | Client override, errors | `withOptions()`, error handling |

## Estimated Size

~800-1200 lines of Kotlin (excluding generated code), ~15 app source files.
