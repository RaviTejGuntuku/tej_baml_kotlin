# BAML Kotlin SDK — Test Guide

119 tests across 3 categories. All run from `engine/language_client_kotlin/`.

## Quick Reference

```bash
./gradlew test --tests "com.boundaryml.baml.unit.*"          # 82 unit tests
./gradlew test --tests "com.boundaryml.baml.codegen.*"        # 22 codegen tests
./gradlew test --tests "com.boundaryml.baml.integration.**"   # 15 integration tests
./gradlew clean test                                           # all 119
```

## Unit Tests (82 tests) — no dylib, no API key

Verify SDK internals in isolation.

| Class | Tests | Covers |
|-------|-------|--------|
| `EncodeTest` | 18 | Kotlin -> protobuf for all types, function args, client override |
| `DecodeTest` | 18 | Protobuf -> Kotlin dispatch (unions, literals, checked, streaming state) |
| `RoundTripTest` | 15 | Encode-then-decode roundtrip for each type |
| `CallbackRoutingTest` | 8 | Callback dispatch, streaming, error routing, concurrent delivery |
| `HandleTest` | 7 | Create, close, clone, double-close, AutoCloseable |
| `MediaTest` | 13 | Media types: construction, encoding, Serde integration |
| `BamlStreamTest` | 5 | Typed stream partials, final capture, error propagation |

## Codegen Tests (22 tests) — no dylib, no API key

Import **real generated types** from `codegen/generated/` (produced by the Rust `generators-kotlin` crate). Validates that codegen output compiles and round-trips correctly through the SDK's Serde layer.

| Class | Tests | Covers |
|-------|-------|--------|
| `GeneratedClassTest` | 6 | Data class encode/decode (Person, Address, Receipt) |
| `GeneratedEnumTest` | 6 | Enum encode/decode, `fromString()`, unknown variant fallback |
| `GeneratedUnionTest` | 5 | Sealed class decode, optional unions, dynamic fallback |
| `GeneratedFunctionTest` | 5 | Function arg encoding with generated types, CallOptions |

### Regenerating codegen fixtures

After changing the Rust code generator (`engine/generators/languages/kotlin/`):

```bash
cd engine
cargo test -p generators-kotlin write_codegen_fixture -- --ignored --nocapture

cd language_client_kotlin
./gradlew compileTestKotlin                                    # must compile
./gradlew test --tests "com.boundaryml.baml.codegen.*"         # must pass
```

## Integration Tests (15 tests) — requires dylib + API key

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

## Rust-Side Codegen Tests (160 tests)

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
