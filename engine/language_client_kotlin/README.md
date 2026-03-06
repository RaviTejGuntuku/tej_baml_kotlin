# BAML Kotlin/JVM SDK

## Overview

A Kotlin/JVM SDK for calling BAML functions with full type safety. Uses JNA-based FFI to communicate with the Rust `bridge_cffi` dylib, protobuf for serialization, and Kotlin coroutines for async callback delivery.

## Architecture

```
User Kotlin code
    │ calls generated suspend fun
    ▼
Generated baml_client/ (hand-written for now)
    │ encodes args → protobuf, calls BamlClient
    ▼
┌─────────────────────────────────────────────────┐
│  engine/language_client_kotlin/  (THIS SDK)     │
│                                                 │
│  BamlClient.kt ─── callFunction / streamFunction│
│       │                                         │
│       ├── Serde.kt (encode args to protobuf)    │
│       ├── Callbacks.kt (register deferred)      │
│       └── Native.kt (JNA FFI call)              │
│              │                                  │
│              ▼                                  │
│  ════════ JNA FFI boundary ═══════════════════  │
│              │                                  │
│              ▼                                  │
│  Rust dylib (bridge_cffi)                       │
│       │ spawns async task on tokio runtime      │
│       │ calls LLM, runs VM                      │
│       ▼                                         │
│  Rust callback → Native.kt callback             │
│       │                                         │
│       ├── Callbacks.kt (route by call_id)       │
│       ├── Serde.kt (decode protobuf → Kotlin)   │
│       └── TypeMap.kt (class/enum dispatch)      │
│              │                                  │
│              ▼                                  │
│  BamlClient.kt ── resume coroutine with result  │
└─────────────────────────────────────────────────┘
    │
    ▼
Generated baml_client/ → returns typed data class to user
```

## File Descriptions

### Project Setup

| File | Purpose |
|------|---------|
| `build.gradle.kts` | Gradle build config: Kotlin 1.9, JNA 5.14, protobuf 3.25, coroutines 1.8, JUnit 5. Configures protobuf plugin to generate Java+Kotlin classes from `.proto` files. |
| `settings.gradle.kts` | Root project name declaration. |
| `proto/baml/cffi/v1/*.proto` | Protobuf definitions copied from `engine/language_client_cffi/types/`. Defines the wire format for host→engine (inbound) and engine→host (outbound) communication. |

### Core SDK — `src/main/kotlin/com/boundaryml/baml/`

#### `Native.kt` — JNA FFI Interface

Declares the JNA interface to the Rust `bridge_cffi` dynamic library.

- **`BamlFfiLib`** — JNA `Library` interface with all FFI function signatures:
  - `version()` → `FfiBuffer`
  - `create_baml_runtime(root_path, src_files_json, env_vars_json)` → `Pointer?`
  - `destroy_baml_runtime(runtime)`
  - `register_callbacks(result_cb, error_cb, on_tick_cb)`
  - `call_function_from_c(runtime, name, args, length, id)` → `FfiBuffer`
  - `call_function_stream_from_c(...)` → `FfiBuffer`
  - `call_function_parse_from_c(...)` → `FfiBuffer`
  - `cancel_function_call(id)` → `FfiBuffer`
  - `clone_handle(key)` / `release_handle(key)`
  - `free_buffer(buffer)`
- **`FfiBuffer`** — JNA `Structure` mapping the C `Buffer { ptr, len }` return type. Has `toByteArray()` helper.
- **`ResultCallbackFn`** / **`OnTickCallbackFn`** — JNA callback interfaces matching the C function pointer types.
- **`BamlFfi`** — Singleton that loads the dylib. Resolution order: explicit path → `BAML_LIBRARY_PATH` env var → system library path.

#### `Types.kt` — Base Types and Interfaces

- **`BamlSerializable`** — Interface for types that can encode themselves to `HostValue` protobuf. Generated classes/enums implement this.
- **`BamlDeserializable<T>`** — Interface for companion objects that can decode from a field map back to a typed Kotlin object.
- **`BamlResult`** — Wrapper delivered through callback channels. Contains `data` (final result), `streamData` (partial), `error`, and boolean flags.
- **`BamlException`** / **`BamlClientError`** — Exception hierarchy for BAML errors.
- **`CallOptions`** — Optional overrides for function calls (client name, env vars, tags).
- **`StreamState<T>`** — Sealed class with `Pending`, `Started(value)`, `Done(value)` variants for streaming.
- **`Checked<T>`** — Value with associated constraint check results (`CheckResult`).

#### `TypeMap.kt` — Type Registry

- **`BamlTypeMap`** — Maps `"NAMESPACE.TypeName"` keys to Kotlin `KClass<*>` and optional `BamlDeserializable<*>`.
- Set once during runtime initialization by generated code.
- Used by `Serde.decodeValue()` to dispatch class/enum decoding to the correct registered Kotlin type.
- Falls back to `DynamicBamlClass`/`DynamicBamlEnum` when no type is registered.

#### `Handles.kt` — Handle Lifecycle

- **`BamlHandle`** — Wraps a Rust-side handle key (`Long`) with a `BamlHandleType` discriminator.
- Implements `AutoCloseable` — calling `close()` invokes `release_handle(key)` via FFI.
- Supports Kotlin's `use {}` pattern to prevent handle leaks.
- `clone()` calls `clone_handle(key)` to get a new independent handle.
- Double-close is safe (no-op after first close).

#### `Serde.kt` — Encode/Decode Helpers

**Encoding (Kotlin → engine):**
- `encodeValue(value: Any?): HostValue` — Recursive encoder. Handles: null (empty oneof), String, Int/Long/Short/Byte, Double/Float, Boolean, List, Map, and `BamlSerializable` implementors.
- `encodeClass(name, fields): HostValue` — Builds `HostClassValue` with name + field entries.
- `encodeEnum(name, value): HostValue` — Builds `HostEnumValue`.
- `encodeMapEntry(key, value): HostMapEntry` — Typed key (string/int/bool/enum) + value.
- `encodeArgs(kwargs, options?): ByteArray` — Wraps kwargs in `HostFunctionArguments` protobuf, serializes to bytes.

**Decoding (engine → Kotlin):**
- `decodeValue(holder: CFFIValueHolder, typeMap): Any?` — Switches on the oneof variant (14 cases):
  - Primitives: null, string, int64, double, bool
  - Containers: list, map
  - Typed: class (→ registered deserializer or `DynamicBamlClass`), enum (→ registered enum or `DynamicBamlEnum`)
  - Literals: string/int/bool literal values
  - Complex: union variant (sealed class dispatch), checked value (`Checked<T>`), streaming state (`StreamState<T>`)
  - Objects: raw object handles (`BamlObjectRef`)

**Dynamic fallback types:**
- `DynamicBamlClass(name, fields)` — When no Kotlin class is registered for a BAML class.
- `DynamicBamlEnum(name, value)` — When no Kotlin enum is registered.
- `DynamicBamlUnion(name, variantName, value)` — When no sealed class is registered for a union.

#### `Callbacks.kt` — Callback Registration and Routing

- **`CallbackManager`** — Singleton managing the callback lifecycle:
  - `ConcurrentHashMap<Int, CallbackEntry>` keyed by monotonically-increasing call IDs.
  - `AtomicInteger` for thread-safe ID generation.
  - Each `CallbackEntry` holds a `Channel<BamlResult>` (buffered, capacity 64).
  - Three JNA callback implementations registered with Rust:
    - **`resultCallback`**: Deserializes protobuf `CFFIValueHolder`, decodes via `Serde`, sends `BamlResult` on the channel. Closes channel and removes entry when `isDone=1`.
    - **`errorCallback`**: Reads UTF-8 error string, creates `BamlException` (or `BamlClientError` for "AbortError"), sends on channel, closes.
    - **`onTickCallback`**: No-op placeholder for future collector-based streaming.
  - `safeSend` / `safeClose` — Ignore exceptions from already-closed channels (concurrent callback race protection).
  - `cleanupCallback(id, channel)` — Called when FFI call fails synchronously to prevent leaks.

**Thread safety:** Callbacks fire on Rust tokio threads. `ConcurrentHashMap` provides lock-free reads. Channel operations are thread-safe.

#### `BamlRuntime.kt` — Runtime Lifecycle

- **`BamlRuntime`** — Wraps a Rust runtime pointer.
  - `create(rootPath, srcFiles, envVars, typeMap)` — JSON-encodes source files and env vars, calls `create_baml_runtime` via FFI. Registers callbacks once on first call.
  - `version()` — Returns the BAML engine version string.
  - `destroy()` — Calls `destroy_baml_runtime` via FFI.

#### `BamlClient.kt` — Function Call API

- **`BamlClient(runtime)`** — The main entry point for calling BAML functions.

  - **`suspend fun callFunction(name, args): Any?`**
    1. Creates unique callback ID + channel via `CallbackManager`.
    2. Launches a child coroutine for cancellation monitoring (calls `cancel_function_call` on cancel).
    3. Calls `call_function_from_c` via JNA (synchronous — just spawns async task in Rust).
    4. Checks the `FfiBuffer` ack for immediate errors.
    5. Suspends on `channel.receive()` until the callback delivers the result.
    6. Returns decoded value or throws on error.

  - **`fun streamFunction(name, args): Flow<BamlResult>`**
    1. Creates streaming callback ID + channel.
    2. Calls `call_function_stream_from_c` via JNA.
    3. Returns a `callbackFlow` that forwards channel results to the flow.
    4. Partials have `hasStreamData=true`, final result has `hasData=true`.
    5. Flow cancellation triggers `cancel_function_call`.

  - **`suspend fun callFunctionParse(name, args): Any?`** — Same pattern as `callFunction`, for parse-mode calls.

## Protobuf Wire Format

### Inbound (Kotlin → Rust)

- **`HostValue`** — Oneof: string, int64, double, bool, list, map, class, enum, handle. Absent oneof = null.
- **`HostFunctionArguments`** — kwargs (repeated `HostMapEntry`) + optional client registry, env vars, collectors, type builder, tags.

### Outbound (Rust → Kotlin)

- **`CFFIValueHolder`** — Oneof with 14 variants including null, primitives, class, enum, literal, list, map, union variant, checked, streaming state, raw object.
- **`CFFITypeName`** — Namespace (`TYPES`, `STREAM_TYPES`, etc.) + name string. Used as TypeMap lookup key.

## Prerequisites

- Java 21+
- Gradle 8.5+ (wrapper included)
- For integration tests: built `bridge_cffi` dylib (see below)

## Running

**Important:** All commands must be run from `engine/language_client_kotlin/`. Running from the repo root will cause integration tests to skip (dylib path won't resolve).

```bash
cd engine/language_client_kotlin

# Set once per shell session (required if your default Java is not 21)
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
```

### Unit + codegen tests (no dylib, no API key needed)

```bash
./gradlew clean test --tests "com.boundaryml.baml.unit.*"
./gradlew clean test --tests "com.boundaryml.baml.codegen.*"

# Both together
./gradlew clean test --tests "com.boundaryml.baml.unit.*" --tests "com.boundaryml.baml.codegen.*"
```

### Integration tests

```bash
# Step 1: Build the bridge_cffi dylib (one-time, ~1 min)
cargo build -p bridge_cffi --release --manifest-path ../../baml_language/Cargo.toml

# Step 2: Run integration tests (dylib is auto-detected, no env var needed)
./gradlew clean test --tests "com.boundaryml.baml.integration.*"

# Some integration tests also need an API key:
export OPENROUTER_API_KEY=sk-or-v1-...
./gradlew clean test --tests "com.boundaryml.baml.integration.*"
```

### All tests at once

```bash
# Integration tests skip gracefully if dylib / API key are not available
./gradlew clean test
```

## Testing Philosophy

### Unit and codegen tests

Unit tests (`com.boundaryml.baml.unit.*`) and codegen tests (`com.boundaryml.baml.codegen.*`) verify the SDK internals in isolation — protobuf encoding/decoding, callback routing, handle lifecycle, and generated-code patterns. They require no dylib or network access.

### Integration tests — SDK plumbing, not LLM output

Integration tests (`com.boundaryml.baml.integration.*`) verify that the full SDK pipeline works end-to-end: JNA FFI calls, protobuf serialization, callback delivery, coroutine resumption, and streaming Flow emission. They make real LLM calls via OpenRouter.

**These tests validate the SDK, not the model.** An LLM call that round-trips through the engine successfully — even if the response can't be parsed into the expected BAML type — is a passing test. Specifically:

- A non-null result means the full pipeline worked (call + parse succeeded).
- A `BamlException` means the engine processed the call and reported an error through the callback pipeline (e.g., parse failure, LLM refusal). **This is still a passing test** because the SDK plumbing functioned correctly.
- Only truly unexpected exceptions (not `BamlException`) indicate an SDK bug and cause test failure.

This approach ensures integration tests are stable regardless of LLM output variability, model availability, or quota limits. The tests use `assumeTrue` guards to skip gracefully when the dylib or API key is unavailable.

### BAML test sources

BAML definitions live in `src/test/resources/baml/` as standalone `.baml` files, separate from the Kotlin test code. Tests compose the files they need via `BamlTestResources.load()`:

```
src/test/resources/baml/
├── openrouter_client.baml   # Shared OpenRouter client (real LLM calls)
├── fake_client.baml         # Fake client (no API key needed)
├── greeting_functions.baml  # GetGreeting, Translate
├── echo_function.baml       # Echo
├── story_function.baml      # TellStory
└── extract_function.baml    # ExtractName
```

Each integration test loads the files it needs:

```kotlin
// LLM test: real client + functions
val srcFiles = BamlTestResources.load("openrouter_client.baml", "greeting_functions.baml")

// Error test: fake client + functions (no API key needed)
val srcFiles = BamlTestResources.load("fake_client.baml", "echo_function.baml")
```

### Running specific tests

```bash
# Single test class
./gradlew clean test --tests "com.boundaryml.baml.unit.EncodeTest"

# Single test method
./gradlew clean test --tests "com.boundaryml.baml.unit.EncodeTest.encode string"
```

### Gradle tips

| Command | What it does |
|---------|-------------|
| `./gradlew clean` | Delete all build artifacts (`make clean` equivalent) |
| `./gradlew clean test` | Full rebuild + run tests (safest, ~10s) |
| `./gradlew test --rerun` | Re-run tests without rebuilding (skip Gradle's up-to-date cache) |
| `./gradlew test` | Run tests, but Gradle skips if it thinks nothing changed |

Use `./gradlew clean test` when in doubt — it prevents stale builds from affecting results.

## Design Decisions

1. **JNA over JNI** — No C glue code needed. JNA maps Java interfaces directly to native functions. Performance overhead is negligible relative to LLM call latency.
2. **protobuf-kotlin (Google official)** — Best proto3 compatibility. Generates both Java classes (for parsing) and Kotlin DSL extensions (for building).
3. **Kotlin coroutines** — `Channel<BamlResult>` bridges Rust tokio callbacks to Kotlin suspend functions. `callbackFlow` for streaming.
4. **ConcurrentHashMap** — Lock-free reads for callback routing. Callbacks fire on Rust tokio threads concurrently.
5. **AutoCloseable handles** — Kotlin's `use {}` pattern prevents handle leaks from the Rust engine.
6. **Dynamic fallback types** — Unregistered types decode to `DynamicBamlClass`/`DynamicBamlEnum`/`DynamicBamlUnion` instead of failing.
