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

You also need a Kotlin-capable BAML CLI to generate Kotlin code from your `.baml` files.

### Clone the Kotlin fork and set `baml-cli` to that binary

If the globally installed `baml-cli` does not yet support Kotlin codegen, clone this fork and put its built CLI binary on `PATH`:

```bash
git clone https://github.com/RaviTejGuntuku/tej_baml_kotlin.git
cd tej_baml_kotlin
cargo build --manifest-path ./engine/cli/Cargo.toml
export PATH="$(pwd)/engine/target/debug:$PATH"
which baml-cli
baml-cli --version
```

That makes `baml-cli` resolve to the Kotlin-capable binary built from this fork.

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

After setting `PATH` to the binary from this fork:

```bash
baml-cli generate
```

This produces `src/main/kotlin/baml_client/` with typed data classes, enums, function wrappers, a type registry, and a generated runtime initializer — all generated from your `.baml` definitions.

### 3. Use in your app

```kotlin
import baml_client.*
import com.boundaryml.baml.*

fun main() = runBlocking {
    // Initialize once at startup. Generated runtime code wires in:
    // - embedded BAML source files
    // - the generated type registry
    // - env vars for your providers
    BamlRuntime.init(
        envVars = mapOf("OPENAI_API_KEY" to requireNotNull(System.getenv("OPENAI_API_KEY")))
    )

    // Call a function — returns a typed Person object
    val person = BamlFunctions.ExtractPerson("John is 30, john@example.com")
    println(person.name)   // "John"
    println(person.age)    // 30
    println(person.email)  // "john@example.com"

    // Returns a typed enum
    val sentiment = BamlFunctions.ClassifySentiment("I love this product!")
    println(sentiment)     // POSITIVE

    BamlRuntime.destroy()
}
```

### 4. Run it

```bash
export OPENAI_API_KEY=sk-...
./gradlew run
```

## More Examples

### Per-call client override

```kotlin
val person = BamlFunctions.ExtractPerson(
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

On desktop JVM, the JNA loader now extracts bundled native libraries under a content-addressed filename instead of a fixed `/tmp` name. That prevents stale `bridge_cffi` binaries from being reused after rebuilding the SDK.

### Generated Runtime Guarantees

The intended developer workflow is:

1. Define `.baml` files.
2. Run `baml-cli generate`.
3. Call `BamlRuntime.init(envVars)` once, then use the generated wrappers.

Generated Kotlin code no longer requires an app-local compatibility shim to:

- register generated classes/enums/unions with the SDK,
- inject provider env vars into runtime creation,
- or decode nested structured outputs.

Nested structured fields such as `List<Class>`, `Map<String, Class>`, optional nested classes, and checked values are recursively materialized by generated decode code through SDK `Serde.coerce*` helpers. This prevents JVM-erased casts like `List<LinkedHashMap> as List<MyType>` from leaking into app code.

### Features

- Async function calls via Kotlin coroutines (`suspend fun`)
- Structured output: classes, enums, unions, nested types, optionals, lists, maps
- Per-call client override (`CallOptions`)
- Parse mode (raw LLM text -> typed result)
- Cancellation propagation to the Rust engine
- Media types: `BamlImage`, `BamlAudio`, `BamlPdf`, `BamlVideo`

## Android

The SDK auto-detects Android at runtime and uses JNI instead of JNA.

The current Android packaging model is:

- the app depends on `io.github.ravitejguntuku:baml-kotlin`
- the app extracts `libbridge_cffi.so` from the SDK artifact during Gradle build
- the app does not need an app-local JNI C bridge or CMake shim anymore

```kotlin
dependencies {
    implementation("io.github.ravitejguntuku:baml-kotlin:0.1.0") {
        exclude(group = "net.java.dev.jna", module = "jna")
    }
}
```

This means Android app consumers no longer need to maintain:

- `baml_jni.c`
- `CMakeLists.txt`
- `externalNativeBuild`

They still need a published SDK artifact that contains the Android `libbridge_cffi.so` files for the required ABIs.

### Fresh-install Android demo flow

This is the current recommended public flow for a fresh machine:

1. clone your Android app repo
2. clone `RaviTejGuntuku/tej_baml_kotlin`
3. run Kotlin codegen from this repo
4. put the built fork binary on `PATH`
5. build/install the Android app

Example:

```bash
git clone https://github.com/RaviTejGuntuku/kitchen_baml_android_app.git
git clone https://github.com/RaviTejGuntuku/tej_baml_kotlin.git

cd tej_baml_kotlin
cargo build --manifest-path ./engine/cli/Cargo.toml
export PATH="$(pwd)/engine/target/debug:$PATH"

cd ../kitchen_baml_android_app
baml-cli generate --from ./baml_src
./gradlew clean :app:installDebug
```

That uses:

- Maven Central for runtime linking
- this public fork for Kotlin-capable codegen

This is reproducible on another laptop without relying on any private local paths.

## Tests

The SDK includes focused unit and generated-code regression tests for:

- runtime initialization and generated type registration,
- recursive decoding of nested structured outputs,
- generated wrappers calling through the generated runtime,
- and standard encode/decode and callback behavior.

See [TESTS.md](TESTS.md) for the current test inventory and commands.

## Build Tooling

The Kotlin SDK module is validated on Gradle 9.0.0 and uses Kotlin Gradle Plugin 2.0.21. The module still targets JVM toolchain 21 for compilation and testing.

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
cargo build -p bridge_cffi --release --target x86_64-linux-android
```

NDK toolchain paths are configured in `.cargo/config.toml` or via environment variables.

Even though Android apps no longer compile a JNI shim locally, SDK maintainers still need the Android NDK toolchains when rebuilding these Rust `.so` files for publication.

### Publish

```bash
./gradlew publishToMavenLocal                # local development
./gradlew publishCentralBundle               # Maven Central
```
