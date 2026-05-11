# BAML Kotlin SDK — Test Guide

All test commands below run from `engine/language_client_kotlin/`.

## Quick Reference

```bash
./gradlew test --tests "com.boundaryml.baml.unit.*"
./gradlew test --tests "com.boundaryml.baml.codegen.*"
./gradlew test --tests "com.boundaryml.baml.integration.**"
./gradlew clean test
```

`./gradlew test` now rebuilds `../../baml_language/target/release/libbridge_cffi.*` first via the `buildBridgeCffi` task, so the integration suite does not accidentally run against a stale native bridge after Rust-side changes.

Manual dylib refresh:

```bash
cd ../../baml_language
cargo build -p bridge_cffi --release --manifest-path Cargo.toml
```

## Unit Tests — no dylib, no API key

Verify SDK internals in isolation.

| Class | Tests | Covers |
|-------|-------|--------|
| `EncodeTest` | 18 | Kotlin -> protobuf for all types, function args, client override |
| `DecodeTest` | 20 | Protobuf -> Kotlin dispatch, recursive `Serde.coerce*` helpers, nested class-list coercion |
| `RoundTripTest` | 15 | Encode-then-decode roundtrip for each type |
| `CallbackRoutingTest` | 8 | Callback dispatch, streaming, error routing, concurrent delivery |
| `HandleTest` | 7 | Create, close, clone, double-close, AutoCloseable |
| `MediaTest` | 13 | Media types: construction, encoding, Serde integration |
| `BamlStreamTest` | 5 | Typed stream partials, final capture, error propagation |

### New regression coverage

These tests specifically cover the nested structured decode bug that surfaced in Android:

- `DecodeTest.coerce named type decodes nested list of maps into registered classes`
- `DecodeTest.coerce enum resolves dynamic enum into concrete enum`

They verify that SDK-side coercion turns map/list payloads into registered Kotlin types before app code consumes them.

## Codegen Tests — no dylib, no API key

Import **real generated types** from `codegen/generated/` (produced by the Rust `generators-kotlin` crate). Validates that codegen output compiles and round-trips correctly through the SDK's Serde layer.

| Class | Tests | Covers |
|-------|-------|--------|
| `GeneratedClassTest` | 7 | Data class encode/decode, including nested `ShoppingPlan -> List<LineItem>` coercion |
| `GeneratedEnumTest` | 6 | Enum encode/decode, `fromString()`, unknown variant fallback |
| `GeneratedUnionTest` | 5 | Sealed class decode, optional unions, dynamic fallback |
| `GeneratedFunctionTest` | 5 | Function arg encoding with generated types, CallOptions |

The checked-in codegen fixtures now include nested class collections so generator regressions show up in JVM tests instead of only in sample apps.

### Regenerating codegen fixtures

After changing the Rust code generator (`engine/generators/languages/kotlin/`):

```bash
cd engine
cargo test -p generators-kotlin write_codegen_fixture -- --ignored --nocapture

cd language_client_kotlin
./gradlew compileTestKotlin                                    # must compile
./gradlew test --tests "com.boundaryml.baml.codegen.*"         # must pass
```

## Integration Tests — requires dylib + API key

End-to-end through the FFI boundary with real LLM calls. Tests validate **SDK plumbing** (FFI, protobuf, callbacks, coroutine resumption), not LLM output — a `BamlException` from a parse failure is still a passing test.

### Setup

```bash
# Build the dylib (one-time, auto-detected at ../../baml_language/target/release/)
cargo build -p bridge_cffi --release --manifest-path ../../baml_language/Cargo.toml

# API key — either .env file or environment variable
echo "OPENROUTER_API_KEY=sk-or-v1-..." > .env
```

### Test inventory

| Class | Tests | Covers |
|-------|-------|--------|
| `RuntimeTest` | 4 | Dylib loading, version, runtime create/destroy |
| `FunctionCallTest` | 2 | String-returning function calls |
| `StreamingTest` | 1 | Streaming Flow emission |
| `ErrorHandlingTest` | 2 | Non-existent function, bad protobuf args |
| `ConcurrencyTest` | 1 | 10 concurrent coroutines |
| `StructuredOutputTest` | 4 | Class/enum return types, TypeMap dispatch, dynamic fallback |

Each test loads BAML sources from a `baml_src/` directory co-located with the test file.

If you see `Failed to initialize runtime: Bex is outdated`, it usually means the Kotlin tests are loading an older native bridge than the current Rust sources. The Gradle `buildBridgeCffi` dependency added to `test` is meant to eliminate that class of failure.

Success-path integration tests now fail on unexpected `BamlException`s instead of accepting any non-empty error message. The one exception currently tracked in-tree is the enum-returning `StructuredOutputTest` case, which is intentionally asserted as an expected failure because the upstream runtime/provider path currently returns an object payload for an enum schema (`{"value":"POSITIVE"}`) rather than the scalar enum string the Kotlin SDK expects.

## Gradle 9 readiness

The Kotlin SDK module now uses:

- Gradle Wrapper `9.0.0`
- Kotlin Gradle Plugin `2.0.21`

Validation command:

```bash
./gradlew --version
./gradlew clean test
```

## Rust-Side Codegen Tests (164 tests)

Verifies BAML type -> Kotlin type string mappings and template rendering.

```bash
cd engine && cargo test -p generators-kotlin --lib
```

## CLI End-to-End (26 pattern checks)

Runs `baml-cli generate` on a test project and verifies all expected files and code patterns.

```bash
./scripts/test_cli_generate.sh
```

## Running a single test

```bash
./gradlew test --tests "com.boundaryml.baml.unit.EncodeTest.encode string"
```
