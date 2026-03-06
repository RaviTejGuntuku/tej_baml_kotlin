# BAML Kotlin SDK — Test Descriptions

**114 tests total** across 14 test files in 3 categories.

---

## Testing Philosophy

Tests validate the **SDK plumbing** (JNA FFI, protobuf serialization, callback routing, coroutine integration), **not LLM output quality**. Integration tests that make real LLM calls accept `BamlException` (parse failures, refusals) as passing results — a `BamlException` means the engine round-tripped successfully through the full callback pipeline.

Only truly unexpected exceptions (not `BamlException`) indicate an SDK bug.

## BAML Test Sources

BAML definitions are standalone `.baml` files in `src/test/resources/baml/`, loaded by `BamlTestResources.load()`. Tests compose the client + function files they need:

```
src/test/resources/baml/
├── openrouter_client.baml   # OpenRouter client (real LLM calls, needs OPENROUTER_API_KEY)
├── fake_client.baml         # Fake client (no API key needed)
├── greeting_functions.baml  # GetGreeting, Translate
├── echo_function.baml       # Echo
├── story_function.baml      # TellStory
└── extract_function.baml    # ExtractName
```

---

## Unit Tests — `com.boundaryml.baml.unit.*`

No external dependencies. Test the SDK's internal logic using only protobuf serialization.

---

### `EncodeTest.kt` — 27 tests

Tests `Serde.encodeValue()` and `Serde.encodeArgs()`: converting Kotlin values to protobuf `HostValue` messages.

| Test | What it verifies |
|------|-----------------|
| `encode null produces empty HostValue` | `null` → `HostValue` with `VALUE_NOT_SET` (no oneof field set) |
| `encode string` | `"hello"` → `HostValue.string_value = "hello"` |
| `encode empty string` | `""` → `HostValue.string_value = ""` (not null) |
| `encode int` | `42` (Kotlin Int) → `HostValue.int_value = 42L` (widened to int64) |
| `encode long` | `Long.MAX_VALUE` → `HostValue.int_value = 9223372036854775807` |
| `encode long min value` | `Long.MIN_VALUE` → `HostValue.int_value = -9223372036854775808` |
| `encode double` | `3.14` → `HostValue.float_value = 3.14` |
| `encode float` | `2.5f` (Kotlin Float) → `HostValue.float_value = 2.5` (widened to double) |
| `encode boolean true` | `true` → `HostValue.bool_value = true` |
| `encode boolean false` | `false` → `HostValue.bool_value = false` |
| `encode list of strings` | `["a","b","c"]` → `HostListValue` with 3 string items |
| `encode empty list` | `[]` → `HostListValue` with 0 items |
| `encode nested list` | `[[1,2],[3,4]]` → nested `HostListValue` structures |
| `encode map of string to int` | `{"x":1,"y":2}` → `HostMapValue` with 2 entries, string keys |
| `encode empty map` | `{}` → `HostMapValue` with 0 entries |
| `encode nested map` | `{"outer":{"inner":"value"}}` → nested `HostMapValue` |
| `encode class value` | `encodeClass("Person", {"name":"Alice","age":30})` → `HostClassValue` with name="Person" and 2 field entries |
| `encode enum value` | `encodeEnum("Color","RED")` → `HostEnumValue` with name="Color", value="RED" |
| `encode function args with multiple kwargs` | `encodeArgs({"name":"Alice","age":30,"active":true})` → `HostFunctionArguments` with 3 kwargs, parseable from bytes |
| `encode function args with env vars` | `encodeArgs(input, CallOptions(env={"API_KEY":"secret"}))` → `HostFunctionArguments` with 1 env entry |
| `encode map entry with int key` | Map entry with key=42 → `HostMapEntry.int_key = 42` |
| `encode map entry with bool key` | Map entry with key=true → `HostMapEntry.bool_key = true` |
| `encode short and byte` | Kotlin `Short` and `Byte` both widen to `int_value` (int64) |
| `encode BamlSerializable class` | A data class implementing `BamlSerializable` encodes via its `encode()` method to `HostClassValue` |
| `encode BamlSerializable enum` | An enum implementing `BamlSerializable` encodes via its `encode()` method to `HostEnumValue` |
| `encode list with null elements` | `["a", null, "c"]` → list with 3 items, middle item has `VALUE_NOT_SET` |
| `encode protobuf bytes are parseable` | `encodeArgs({"x":[1,2,3]})` produces bytes that `HostFunctionArguments.parseFrom()` can parse back correctly |

---

### `DecodeTest.kt` — 25 tests

Tests `Serde.decodeValue()`: converting protobuf `CFFIValueHolder` messages back to Kotlin values.

| Test | What it verifies |
|------|-----------------|
| `decode null value` | `CFFIValueNull` → Kotlin `null` |
| `decode string value` | `string_value = "hello world"` → `"hello world"` |
| `decode int value` | `int_value = 42` → `42L` (Long) |
| `decode float value` | `float_value = 3.14` → `3.14` (Double) |
| `decode bool value true` | `bool_value = true` → `true` |
| `decode bool value false` | `bool_value = false` → `false` |
| `decode list value` | `CFFIValueList` with ["a","b",3] → `List` of mixed types |
| `decode map value` | `CFFIValueMap` with {"name":"Alice","age":30} → `Map<String, Any?>` |
| `decode class value without registered type` | `CFFIValueClass(name="Person")` with no TypeMap entry → `DynamicBamlClass("Person", fields)` |
| `decode class value with registered deserializer` | `CFFIValueClass(name="Person")` with TypeMap entry → `TestPersonOut("Charlie", 35)` via companion deserializer |
| `decode enum value without registered type` | `CFFIValueEnum(name="Color", value="BLUE")` with no TypeMap → `DynamicBamlEnum("Color", "BLUE")` |
| `decode enum value with registered type` | `CFFIValueEnum(name="Color", value="GREEN")` with TypeMap → `TestColorOut.GREEN` Kotlin enum constant |
| `decode dynamic enum for unknown value` | `CFFIValueEnum(value="UNKNOWN_VALUE")` when enum constant doesn't exist → falls back to `DynamicBamlEnum` |
| `decode literal string` | `CFFIFieldTypeLiteral.string_literal("exact")` → `"exact"` |
| `decode literal int` | `CFFIFieldTypeLiteral.int_literal(99)` → `99L` |
| `decode literal bool` | `CFFIFieldTypeLiteral.bool_literal(true)` → `true` |
| `decode union variant with single pattern` | `is_single_pattern=true` → unwraps inner value directly (represents `T \| null`) |
| `decode union variant without registered type` | Multi-variant union with no TypeMap → `DynamicBamlUnion("StringOrInt", "string", "hello")` |
| `decode checked value` | `CFFIValueChecked` with 2 checks → `Checked("valid data", {"length_check": succeeded, "format_check": failed})` |
| `decode streaming state PENDING` | `CFFIStreamState.PENDING` → `StreamState.Pending` |
| `decode streaming state STARTED` | `CFFIStreamState.STARTED` with value → `StreamState.Started("partial data")` |
| `decode streaming state DONE` | `CFFIStreamState.DONE` with value → `StreamState.Done("final data")` |
| `decode nested class with list field` | `CFFIValueClass("Team")` containing a list of `CFFIValueClass("Person")` → nested `DynamicBamlClass` structures |
| `decode map of classes` | `CFFIValueMap` where values are `CFFIValueClass("User")` → `Map<String, DynamicBamlClass>` |
| `decode value not set` | Default `CFFIValueHolder` (no oneof set) → `null` |

---

### `RoundTripTest.kt` — 14 tests

Tests that encoding a Kotlin value and then decoding the equivalent outbound protobuf produces the same logical value. Each test encodes via `Serde.encodeValue()`, then constructs the matching `CFFIValueHolder` and decodes via `Serde.decodeValue()`.

| Test | What it verifies |
|------|-----------------|
| `roundtrip null` | null → encode (VALUE_NOT_SET) + decode (CFFIValueNull) → null |
| `roundtrip string` | "hello world" → encode → decode → "hello world" |
| `roundtrip empty string` | "" → encode → decode → "" |
| `roundtrip int` | 42 → encode (int64) → decode → 42L |
| `roundtrip long max` | Long.MAX_VALUE → encode → decode → Long.MAX_VALUE |
| `roundtrip long min` | Long.MIN_VALUE → encode → decode → Long.MIN_VALUE |
| `roundtrip double` | 3.14159 → encode → decode → 3.14159 |
| `roundtrip boolean` | true and false both survive roundtrip |
| `roundtrip list of primitives` | ["a","b","c"] → encode → decode → ["a","b","c"] |
| `roundtrip empty list` | [] → encode → decode → [] |
| `roundtrip map` | {"key1":"value1","key2":"value2"} → encode → decode → same map |
| `roundtrip empty map` | {} → encode → decode → {} |
| `roundtrip nested class` | encodeClass("Address", {city,zip}) → decode → DynamicBamlClass with same fields |
| `roundtrip enum` | encodeEnum("Status","ACTIVE") → decode → DynamicBamlEnum("Status","ACTIVE") |
| `roundtrip list with null elements` | ["a", null, "c"] → encode → decode → ["a", null, "c"] |

---

### `CallbackRoutingTest.kt` — 8 tests

Tests `CallbackManager`: the callback registration, routing, and channel delivery logic. Uses `CallbackManager.fireResult()` and `fireError()` to simulate Rust callbacks without the actual dylib.

| Test | What it verifies |
|------|-----------------|
| `result callback routes to correct deferred` | Register 3 callbacks with IDs 1,2,3. Fire results out-of-order (2,1,3). Each channel receives the correct value for its ID. |
| `error callback completes deferred exceptionally` | Fire error "Something went wrong" → channel receives `BamlResult` with `BamlException` |
| `abort error produces BamlClientError` | Fire error "AbortError" (special Rust cancellation signal) → channel receives `BamlClientError` (subclass) |
| `streaming receives multiple partials then final` | Fire 3 results with isDone=0 then 1 with isDone=1. Channel receives 4 results: 3 with `hasStreamData=true`, 1 with `hasData=true`. |
| `unknown call id does not crash` | Fire result/error for non-existent IDs 999/998. No exception thrown, silently ignored. |
| `concurrent callbacks on different threads` | Register 10 callbacks. Fire all 10 concurrently from `Dispatchers.Default`. All 10 channels receive correct values without cross-contamination. |
| `cleanup callback closes channel and removes entry` | Call `cleanupCallback(id, channel)`. Channel is closed, pending count decreases by 1. |
| `callback with complex protobuf decodes correctly` | Fire a CFFIValueHolder containing a class with a list field. Channel receives correctly decoded `DynamicBamlClass` with nested list. |

---

### `HandleTest.kt` — 7 tests

Tests `BamlHandle`: the AutoCloseable wrapper for Rust-side handle keys. These tests do NOT require the FFI dylib (they catch expected exceptions from missing FFI).

| Test | What it verifies |
|------|-----------------|
| `handle starts not closed` | Newly created handle has `isClosed = false` |
| `close marks handle as closed` | After `close()`, `isClosed = true` (even if FFI call fails because dylib isn't loaded) |
| `double close does not crash` | Calling `close()` twice does not throw — second call is a no-op |
| `use block calls close` | Kotlin's `handle.use { ... }` automatically calls `close()` after the block |
| `handle type is preserved` | Every `BamlHandleType` enum variant is correctly stored and retrieved |
| `handle key is preserved` | Handle key `Long.MAX_VALUE` is stored and retrieved correctly |
| `clone on closed handle throws` | Calling `clone()` after `close()` throws `IllegalStateException` |

---

## Integration Tests — `com.boundaryml.baml.integration.*`

Require the actual `bridge_cffi` dylib. All tests use `assumeTrue()` to skip gracefully when the dylib or API key is not available. BAML sources are loaded from `src/test/resources/baml/` via `BamlTestResources.load()`.

**SDK-focused testing:** Integration tests that make real LLM calls catch `BamlException` and treat it as a pass — the engine processed the call and delivered an error through the callback pipeline, proving the SDK works. Only non-`BamlException` errors fail.

---

### `RuntimeTest.kt` — 4 tests

Tests BAML runtime lifecycle via FFI. Requires: dylib. BAML sources: `fake_client.baml`, `extract_function.baml`.

| Test | What it verifies |
|------|-----------------|
| `version returns valid semver string` | `BamlRuntime.version()` returns a non-empty string containing "." (semver-like) |
| `create runtime with valid BAML files` | Load `fake_client.baml` + `extract_function.baml`. `create()` returns a non-null runtime. |
| `create runtime with empty files` | Pass empty srcFiles. `create()` still succeeds (runtime with no functions). |
| `destroy runtime does not crash` | `create()` then `destroy()` completes without exception. |

---

### `FunctionCallTest.kt` — 2 tests

Tests calling BAML functions end-to-end. Requires: dylib + `OPENROUTER_API_KEY`. BAML sources: `openrouter_client.baml`, `greeting_functions.baml`.

| Test | What it verifies |
|------|-----------------|
| `call function returning string` | Call `GetGreeting(name="World")`. Returns non-null result OR throws `BamlException` (both = SDK worked). |
| `call function with multiple args` | Call `Translate(text="Hello", language="Spanish")`. Returns non-null result OR throws `BamlException`. |

---

### `StreamingTest.kt` — 1 test

Tests streaming function calls. Requires: dylib + `OPENROUTER_API_KEY`. BAML sources: `openrouter_client.baml`, `story_function.baml`.

| Test | What it verifies |
|------|-----------------|
| `stream function collects partials and final` | Call `TellStory(topic="a cat")` via `streamFunction()`. Collect the Flow. At least one result received OR `BamlException` thrown (both = SDK streaming pipeline worked). |

---

### `ErrorHandlingTest.kt` — 2 tests

Tests error paths. Requires: dylib (no API key needed). BAML sources: `fake_client.baml`, `echo_function.baml`.

| Test | What it verifies |
|------|-----------------|
| `call non-existent function throws` | Load only `fake_client.baml` (no functions). Call "NonExistentFunction". Exception is thrown with non-empty message. |
| `invalid protobuf args throws` | Load `fake_client.baml` + `echo_function.baml`. Call `Echo` with garbage bytes `[0xFF, 0xFE, 0x00, 0x01]`. Exception is thrown. |

---

### `ConcurrencyTest.kt` — 1 test

Tests concurrent call safety. Requires: dylib + `OPENROUTER_API_KEY`. BAML sources: `openrouter_client.baml`, `echo_function.baml`.

| Test | What it verifies |
|------|-----------------|
| `concurrent calls return correct results` | Launch 10 coroutines on `Dispatchers.Default`, each calling `Echo(input="test-N")`. All 10 complete (non-null result or `BamlException`). No cross-contamination by call_id. |

---

## Codegen Validation Tests — `com.boundaryml.baml.codegen.*`

Test the patterns that a future Kotlin code generator will produce. Each test hand-writes a generated data class/enum/sealed class and validates it works with the SDK's encode/decode.

---

### `GeneratedClassTest.kt` — 6 tests

Hand-writes `PersonGenerated(name, age, email?)` and `AddressGenerated(street, city, zip)` implementing `BamlSerializable` + `BamlDeserializable`.

| Test | What it verifies |
|------|-----------------|
| `encode person produces correct HostClassValue` | `PersonGenerated("Alice", 30, "alice@test.com").encode()` → `HostClassValue` with name="Person", 3 fields |
| `encode person with null email omits email field` | `PersonGenerated("Bob", 25).encode()` → only 2 fields (email omitted) |
| `decode person from CFFIValueHolder` | `CFFIValueClass(name="Person", fields=[name,age,email])` with TypeMap → `PersonGenerated("Charlie", 35, "charlie@test.com")` |
| `decode person with null optional field` | `CFFIValueClass` without email field → `PersonGenerated("Dave", 40, null)` |
| `encode then decode roundtrip` | `PersonGenerated("Eve", 28, "eve@test.com")` → encode → build matching CFFI → decode → same field values |
| `nested class encode and decode` | `AddressGenerated("123 Main St", "NYC", "10001")` encode and decode both produce correct values |

---

### `GeneratedEnumTest.kt` — 5 tests

Hand-writes `SentimentGenerated { POSITIVE, NEGATIVE, NEUTRAL }` implementing `BamlSerializable`.

| Test | What it verifies |
|------|-----------------|
| `encode enum produces correct HostEnumValue` | `POSITIVE.encode()` → `HostEnumValue(name="Sentiment", value="POSITIVE")` |
| `encode all enum variants` | All 3 variants encode with correct name and value |
| `decode enum with registered type` | `CFFIValueEnum(value="NEGATIVE")` with TypeMap → `SentimentGenerated.NEGATIVE` |
| `decode unknown enum variant falls back to dynamic` | `CFFIValueEnum(value="VERY_POSITIVE", is_dynamic=true)` → `DynamicBamlEnum` (no matching constant) |
| `enum as function arg encodes correctly` | `encodeArgs({"sentiment": NEUTRAL, "text": "hello"})` → kwargs[0] has enum_value with "NEUTRAL" |

---

### `GeneratedUnionTest.kt` — 5 tests

Hand-writes `StringOrIntGenerated` sealed class with `StringVariant(String)` and `IntVariant(Long)` implementing `BamlDeserializable`.

| Test | What it verifies |
|------|-----------------|
| `decode union string variant` | `CFFIValueUnionVariant(valueOptionName="string")` with TypeMap → `StringOrIntGenerated.StringVariant("hello")` |
| `decode union int variant` | `CFFIValueUnionVariant(valueOptionName="int")` with TypeMap → `StringOrIntGenerated.IntVariant(42L)` |
| `decode optional union (single pattern) with value` | `is_single_pattern=true`, inner value="present" → unwraps to `"present"` directly |
| `decode optional union (single pattern) with null` | `is_single_pattern=true, is_optional=true`, inner=null → returns `null` |
| `decode union without registered type falls back to dynamic` | No TypeMap entry → `DynamicBamlUnion("UnknownUnion", "some_variant", "data")` |

---

### `GeneratedFunctionTest.kt` — 6 tests

Hand-writes function arg encoders (`ExtractPersonArgs`, `ClassifyTextArgs`, `CreatePersonArgs`) matching what the code generator would produce.

| Test | What it verifies |
|------|-----------------|
| `function args encode string correctly` | `ExtractPersonArgs.encodeArgs("John is 30")` → kwargs with key="input", value="John is 30" |
| `function args encode optional int` | `encodeArgs("test", maxResults=5)` → 2 kwargs including max_results=5 |
| `function args encode without optional` | `encodeArgs("test")` → 1 kwarg (optional omitted) |
| `function args encode list of strings` | `ClassifyTextArgs.encodeArgs("hello", ["greeting","farewell","question"])` → kwargs with list of 3 strings |
| `function args encode class argument` | `CreatePersonArgs.encodeArgs(PersonGenerated(...))` → kwarg with class_value, name="Person" |
| `function args with call options` | `encodeArgs(input, CallOptions(env={...}, tags={...}))` → HostFunctionArguments with env and tags populated |
