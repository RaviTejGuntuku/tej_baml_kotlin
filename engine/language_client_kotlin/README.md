# BAML Kotlin SDK

Kotlin/JVM SDK for calling [BAML](https://docs.boundaryml.com/) functions with full type safety. Supports desktop JVM and Android.

## Installation

Add to your `build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.ravitejguntuku:baml-kotlin:0.1.0")
}
```

You also need the BAML CLI to generate Kotlin code from your `.baml` files:

```bash
# macOS
brew install boundaryml/baml/baml

# or via npm
npm install -g @boundaryml/baml
```

## Quickstart

### 1. Create a BAML project

```bash
mkdir my-project && cd my-project
mkdir baml_src
```

Create your BAML files:

```baml
// baml_src/clients.baml
client<llm> MyClient {
    provider openai
    options {
        model "gpt-4o-mini"
        api_key env.OPENAI_API_KEY
    }
}

generator kotlin {
    output_type "kotlin"
    output_dir "../src/main/kotlin/baml_client"
}
```

```baml
// baml_src/functions.baml
class Person {
    name  string
    age   int
    email string?
}

enum Sentiment {
    POSITIVE
    NEGATIVE
    NEUTRAL
}

function ExtractPerson(text: string) -> Person {
    client MyClient
    prompt #"
        Extract the person's info from this text:
        {{ text }}

        Return their name, age, and email (if present).
    "#
}

function ClassifySentiment(text: string) -> Sentiment {
    client MyClient
    prompt #"Classify the sentiment of: {{ text }}"#
}
```

### 2. Generate Kotlin code

```bash
baml-cli generate
```

This produces `src/main/kotlin/baml_client/` with typed data classes, enums, function wrappers, and a type registry — all generated from your `.baml` definitions.

### 3. Use in your app

```kotlin
import baml_client.*
import com.boundaryml.baml.*

fun main() = runBlocking {
    // Initialize (once at startup)
    BamlFfi.load()
    val runtime = BamlRuntime.create(
        rootPath = ".",
        srcFiles = BamlSourceMap.files,
        typeMap = BamlTypeMap.create()
    )
    val b = BamlFunctions(BamlClient(runtime))

    // Call a function — returns a typed Person object
    val person = b.ExtractPerson("John is 30, john@example.com")
    println(person.name)   // "John"
    println(person.age)    // 30
    println(person.email)  // "john@example.com"

    // Returns a typed enum
    val sentiment = b.ClassifySentiment("I love this product!")
    println(sentiment)     // POSITIVE

    runtime.destroy()
}
```

### 4. Run it

```bash
export OPENAI_API_KEY=sk-...
./gradlew run
```

## More Examples

### Streaming

```kotlin
val stream = BamlStreamFunctions(BamlClient(runtime))
    .ExtractPersonStream("John is 30, john@example.com")

stream.collect { result ->
    if (result.hasStreamData) println("Partial: ${result.streamData}")
    if (result.hasData) println("Final: ${result.data}")
}
```

### Per-call client override

```kotlin
val person = b.ExtractPerson(
    "Alice is 25",
    options = CallOptions(client = "GPT4Turbo")
)
```

### Type mappings

| BAML | Kotlin |
|------|--------|
| `string` | `String` |
| `int` | `Long` |
| `float` | `Double` |
| `bool` | `Boolean` |
| `T?` | `T?` |
| `T[]` | `List<T>` |
| `map<K, V>` | `Map<K, V>` |
| `class` | `data class` |
| `enum` | `enum class` |
| `A \| B` | `sealed class` |

## Architecture

```
.baml files
    |  baml-cli generate
    v
Generated Kotlin (baml_client/)
    |  typed suspend funs, data classes, enums, sealed classes
    v
BAML Kotlin SDK (this library)
    |  BamlClient: protobuf encode/decode, async callback management
    v
FFI boundary
    |  Desktop: JNA        Android: JNI + C bridge
    v
Rust engine (bridge_cffi)
    |  tokio async runtime -> LLM call -> parse -> validate types
    v
Callback -> Channel -> resumes Kotlin coroutine with typed result
```

The SDK communicates with a Rust runtime engine via FFI. On desktop JVM, it uses JNA; on Android, it uses JNI with a C bridge layer. Function arguments are protobuf-encoded, sent across the FFI boundary, and results are delivered asynchronously via callbacks that resume Kotlin coroutines.

### Features

- Async function calls via Kotlin coroutines (`suspend fun`)
- Streaming via `Flow<BamlResult>` with partial results
- Structured output: classes, enums, unions, nested types, optionals, lists, maps
- Per-call client override (`CallOptions`)
- Parse mode (raw LLM text -> typed result)
- Cancellation propagation to the Rust engine
- Media types: `BamlImage`, `BamlAudio`, `BamlPdf`, `BamlVideo`

## Android

The SDK auto-detects Android at runtime and uses JNI instead of JNA. Setup requires copying the cross-compiled native library and a C JNI bridge into your Android app.

```kotlin
// app/build.gradle.kts
android {
    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt") }
    }
    defaultConfig {
        ndk { abiFilters += listOf("arm64-v8a") }
    }
}

dependencies {
    implementation("io.github.ravitejguntuku:baml-kotlin:0.1.0") {
        exclude(group = "net.java.dev.jna", module = "jna")
    }
}
```

See [jni/](jni/) for the C bridge source and CMake config. See the [Building from Source](#building-from-source) section for cross-compilation instructions.

## Tests

119 tests across unit (82), codegen (22), and integration (15). See [TESTS.md](TESTS.md) for details.

```bash
./gradlew clean test   # all tests
```

## Building from Source

### Prerequisites

- Java 21+
- Rust toolchain

### Build + test

```bash
cd baml_language && cargo build -p bridge_cffi --release
cd ../engine/language_client_kotlin && ./gradlew clean test
```

### Cross-compile for Android

```bash
cd baml_language
cargo build -p bridge_cffi --release --target aarch64-linux-android
```

NDK toolchain paths are configured in `.cargo/config.toml`.

### Publish

```bash
./gradlew publishToMavenLocal                # local development
./gradlew publishCentralBundle               # Maven Central
```
