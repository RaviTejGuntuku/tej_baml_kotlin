# BAML Kotlin SDK

Kotlin/JVM SDK for calling [BAML](https://docs.boundaryml.com/) functions with full type safety. Supports desktop JVM and Android.

## Getting Started

### Step 1: Add the dependency

```kotlin
// build.gradle.kts
repositories {
    mavenCentral()
}

dependencies {
    implementation("com.boundaryml:baml-kotlin:0.1.0")
}
```

### Step 2: Define your BAML types and functions

Create `.baml` files in your project:

```baml
// baml_src/clients.baml
client<llm> MyClient {
    provider openai
    options {
        model "gpt-4o-mini"
        api_key env.OPENAI_API_KEY
    }
}
```

```baml
// baml_src/types.baml
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
```

```baml
// baml_src/functions.baml
function ExtractPerson(text: string) -> Person {
    client MyClient
    prompt #"Extract person info from: {{ text }}"#
}

function ClassifySentiment(text: string) -> Sentiment {
    client MyClient
    prompt #"Classify the sentiment of: {{ text }}"#
}
```

### Step 3: Generate Kotlin code

Add a generator block to any `.baml` file:

```baml
generator kotlin {
    output_type "kotlin"
    output_dir "../src/main/kotlin/baml_client"
}
```

Run the code generator:

```bash
baml-cli generate
```

This produces a `baml_client/` directory containing:
- **Data classes** (`Person`), **enum classes** (`Sentiment`), **sealed classes** (union types)
- **Function wrappers** — typed `suspend fun` for each BAML function
- **Stream wrappers** — `Flow`-based streaming variants
- **Type registry** — maps BAML type names to Kotlin classes for deserialization

### Step 4: Call BAML functions from Kotlin

```kotlin
import baml_client.*
import com.boundaryml.baml.*

// Initialize (once, at app startup)
BamlFfi.load()
val runtime = BamlRuntime.create(
    rootPath = ".",
    srcFiles = BamlSourceMap.files,    // generated
    typeMap = BamlTypeMap.create()      // generated
)
val b = BamlFunctions(BamlClient(runtime))

// Call a function — returns typed Person
val person: Person = b.ExtractPerson("John is 30, john@example.com")
println(person.name)   // "John"
println(person.email)  // "john@example.com"

// Classify sentiment — returns typed enum
val sentiment: Sentiment = b.ClassifySentiment("I love this product!")
println(sentiment)     // POSITIVE

// Stream a function — returns Flow with partial results
val flow = BamlStreamFunctions(BamlClient(runtime))
    .ExtractPersonStream("John is 30")
flow.collect { result ->
    if (result.hasStreamData) println("Partial: ${result.streamData}")
    if (result.hasData) println("Final: ${result.data}")
}

// Override which LLM client to use per call
val result = b.ExtractPerson(
    "...",
    options = CallOptions(client = "FastClient")
)
```

### BAML -> Kotlin type mappings

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
    |  baml-cli generate (Rust code generator)
    v
Generated Kotlin (baml_client/)
    |  typed suspend funs, data classes, enums, sealed classes
    v
BAML Kotlin SDK (this library)
    |  BamlClient: encodes args to protobuf, manages async callbacks
    v
FFI boundary
    |  Desktop: JNA        Android: JNI + C bridge
    v
Rust engine (bridge_cffi)
    |  tokio runtime -> LLM call -> parse response -> fire callback
    v
Callback -> Channel -> resumes Kotlin coroutine with typed result
```

### Key components

| Component | What it does |
|-----------|-------------|
| `BamlFfi` | Loads the native library; auto-detects Android (JNI) vs desktop (JNA) |
| `BamlRuntime` | Creates/destroys the Rust engine instance from BAML source files |
| `BamlClient` | `callFunction` (suspend), `streamFunction` (Flow), `callFunctionParse` |
| `Serde` | Protobuf encode (Kotlin -> engine) and decode (engine -> typed Kotlin) |
| `CallbackManager` | Routes async results from Rust threads to Kotlin coroutines via `Channel` |
| `TypeMap` | Registry mapping BAML type names to Kotlin `KClass` + deserializer |

### Features

- Async function calls via Kotlin coroutines
- Streaming via `Flow<BamlResult>` with partial results
- Structured output: classes, enums, unions, nested types, optional fields, lists, maps
- Per-call client override (`CallOptions`)
- Parse mode (raw LLM text -> typed result)
- Cancellation propagation (coroutine cancel -> Rust `cancel_function_call`)
- Media types: `BamlImage`, `BamlAudio`, `BamlPdf`, `BamlVideo`

## Tests

119 tests across unit (82), codegen (22), and integration (15). See [TESTS.md](TESTS.md) for full details.

```bash
./gradlew clean test                                           # all 119
./gradlew test --tests "com.boundaryml.baml.unit.*"            # unit only
./gradlew test --tests "com.boundaryml.baml.codegen.*"         # codegen only
./gradlew test --tests "com.boundaryml.baml.integration.**"    # integration (needs dylib + API key)
```

## Building from Source

### Prerequisites

- Java 21+
- Rust toolchain

### Build the native library

```bash
cd baml_language
cargo build -p bridge_cffi --release
```

### Run the SDK tests

```bash
cd engine/language_client_kotlin
./gradlew clean test
```

### Publish to Maven Local (for local development)

```bash
./gradlew publishToMavenLocal
# -> ~/.m2/repository/com/boundaryml/baml-kotlin/0.1.0-SNAPSHOT/
```

## Android

Android requires JNI instead of JNA. The SDK auto-detects Android at runtime.

### 1. Cross-compile the Rust library

```bash
cd baml_language
cargo build -p bridge_cffi --release --target aarch64-linux-android
```

(NDK toolchain is configured in `.cargo/config.toml`)

### 2. Copy into your Android app

```bash
mkdir -p app/src/main/jniLibs/arm64-v8a
cp baml_language/target/aarch64-linux-android/release/libbridge_cffi.so \
   app/src/main/jniLibs/arm64-v8a/

mkdir -p app/src/main/cpp
cp engine/language_client_kotlin/jni/baml_jni.c app/src/main/cpp/
cp engine/language_client_kotlin/jni/CMakeLists.txt app/src/main/cpp/
```

### 3. Configure your app's build.gradle.kts

```kotlin
android {
    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt") }
    }
    defaultConfig {
        ndk { abiFilters += listOf("arm64-v8a") }
    }
}

dependencies {
    implementation("com.boundaryml:baml-kotlin:0.1.0") {
        exclude(group = "net.java.dev.jna", module = "jna")
    }
}
```
