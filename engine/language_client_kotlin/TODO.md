# BAML Kotlin SDK — Remaining Work

## Status Summary

| Component | Status | Tests |
|-----------|--------|-------|
| Kotlin code generator | ✅ DONE | 159 Rust tests |
| Codegen validation | ✅ DONE | 22 Kotlin tests |
| Media types | ✅ DONE | 13 tests |
| Typed BamlStream | ✅ DONE | 5 tests |
| Integration tests | ✅ DONE | 14 tests |
| Test cleanup | ✅ DONE | 105 unit/codegen total |
| Client override in CallOptions | ✅ DONE | 1 test |
| call_function_parse | ✅ DONE | — |
| Maven Central publishing | ⬜ TODO | — |
| Native dylib bundling | ⬜ TODO | — |
| CLI integration | ✅ DONE (verified) | — |
| Collector/logging | ⬜ TODO | — |
| TypeBuilder | ⬜ TODO | — |

---

## Dependency Map

```
INDEPENDENT (can parallelize):
  ├── Task A: Client override in CallOptions
  ├── Task B: call_function_parse templates
  └── Task C: CLI integration verification

MUST BE SEQUENTIAL (shared build.gradle.kts):
  ├── Task D: Maven Central publishing
  └── Task E: Native dylib bundling

NEED RESEARCH FIRST (JNA FFI surface):
  ├── Task F: Collector/logging support
  └── Task G: TypeBuilder (dynamic types)
```

---

## Task A: Client override in CallOptions

**Status:** ✅ DONE
**Priority:** Medium
**Files touched:** `Serde.kt`, `function.kt.j2`, `function.stream.kt.j2`, `EncodeTest.kt`
**No overlap with:** Tasks B–G

### What exists
- `CallOptions` already has `val client: String? = null` in `Types.kt:44`
- `Serde.encodeArgs()` already handles `options.env` and `options.tags`

### What to do
1. Read `src/main/kotlin/com/boundaryml/baml/Types.kt` — see CallOptions definition
2. Read `src/main/kotlin/com/boundaryml/baml/Serde.kt` — see `encodeArgs()` (~line 104)
3. Read the proto definition for `HostFunctionArguments` in `proto/baml/cffi/v1/` to find the `client_registry` field name and type
4. Update `Serde.encodeArgs()` to pass `options.client` to `HostFunctionArguments.client_registry` (or whatever the proto field is). Look at how Go SDK does it in `engine/language_client_go/` for reference.
5. Update codegen templates to accept optional `CallOptions` and pass it through:
   - `engine/generators/languages/kotlin/src/_templates/function.kt.j2`
   - `engine/generators/languages/kotlin/src/_templates/function.stream.kt.j2`
6. Add a test in `src/test/kotlin/com/boundaryml/baml/unit/EncodeTest.kt` verifying client is encoded in HostFunctionArguments when `CallOptions.client` is set
7. Verify: `./gradlew compileTestKotlin && ./gradlew cleanTest test --tests "com.boundaryml.baml.unit.EncodeTest"`

---

## Task B: call_function_parse (parse mode)

**Status:** ✅ DONE
**Priority:** Medium
**Files touched:** new codegen template, new test files
**No overlap with:** Tasks A, C–G

### What exists
- Rust FFI `call_function_parse_from_c` already exists
- Kotlin `BamlClient.callFunctionParse()` already wired at `BamlClient.kt:128`
- Calls `ffi.call_function_parse_from_c(...)` via `Native.kt:73`

### What to do
1. Read `BamlClient.kt` to understand the existing `callFunctionParse()` implementation
2. Read the existing function codegen templates (`function.kt.j2`, `function.stream.kt.j2`) to understand the pattern
3. Create a new codegen template (or extend existing) for `b.parse.*` wrappers — these should call `client.callFunctionParse()` instead of `client.callFunction()`
4. Register the parse template in `src/lib.rs` file generation pipeline
5. Add an integration test:
   - Create `src/test/kotlin/.../integration/parse_test/` with `baml_src/` and `ParseTest.kt`
   - Test that parse mode calls work through the FFI
6. Regenerate codegen fixtures: `cargo test -p generators-kotlin write_codegen_fixture -- --ignored --nocapture`
7. Verify: full three-layer codegen validation (see README)

---

## Task C: CLI integration (end-to-end verification)

**Status:** ✅ DONE (verified — codegen pipeline works, no E2E test script added)
**Priority:** Low
**Files touched:** none (read-only verification), possibly new test script
**No overlap with:** Tasks A, B, D–G

### What exists
- Generator registered in all 4 places:
  1. `engine/baml-lib/baml-types/src/generator.rs` — `GeneratorOutputType::Kotlin`
  2. `baml_language/crates/baml_compiler_hir/src/generator.rs` — `"kotlin"` in `VALID_OUTPUT_TYPES`
  3. `engine/generators/utils/generators_lib/src/lib.rs` — `Kotlin` dispatch case
  4. `engine/generators/utils/generators_lib/Cargo.toml` — `generators-kotlin` dependency

### What to do
1. Build `baml-cli` if not already built
2. Create a test BAML project with a `generator` block:
   ```baml
   generator kotlin {
       output_type "kotlin"
       output_dir "./baml_client"
       default_client_mode "async"
   }
   ```
3. Run `baml-cli generate` on it
4. Verify the output directory contains expected files: `types/Classes.kt`, `types/Enums.kt`, `types/Unions.kt`, `BamlFunctions.kt`, `BamlTypeMap.kt`, etc.
5. Verify the generated code compiles with `kotlinc` or Gradle
6. Document any issues or missing features

---

## Task D: Maven Central publishing

**Status:** ⬜ TODO
**Priority:** Medium
**Files touched:** `build.gradle.kts`
**CONFLICTS WITH:** Task E (Native dylib bundling) — must run sequentially

### What to do
1. Add `maven-publish` plugin to `build.gradle.kts`
2. Configure POM metadata (groupId: `com.boundaryml`, artifactId: `baml-kotlin`, etc.)
3. Configure signing (requires GPG key — needs user input)
4. Add Sonatype/Maven Central repository configuration
5. Test with `./gradlew publishToMavenLocal`

---

## Task E: Native dylib bundling

**Status:** ⬜ TODO
**Priority:** Medium
**Files touched:** `build.gradle.kts`, `Native.kt`
**CONFLICTS WITH:** Task D (Maven Central) — must run sequentially

### What to do
1. Define JAR layout for native libraries:
   ```
   META-INF/native/
   ├── darwin-aarch64/libbridge_cffi.dylib
   ├── darwin-x86_64/libbridge_cffi.dylib
   ├── linux-x86_64/libbridge_cffi.so
   └── windows-x86_64/bridge_cffi.dll
   ```
2. Update `Native.kt` (`BamlFfi.load()`) to:
   - First try extracting from classpath JAR
   - Fall back to `BAML_LIBRARY_PATH` env var
   - Fall back to system library path
3. Add Gradle task to copy dylib into JAR resources
4. Test with `./gradlew jar` and verify the JAR contains the native library
5. Test loading from JAR in a clean environment (no `BAML_LIBRARY_PATH`)

---

## Task F: Collector/logging support

**Status:** ⬜ TODO
**Priority:** Low
**Files touched:** new Kotlin files, `Callbacks.kt`, `Native.kt`
**No overlap with:** Tasks A–E, G (but needs JNA FFI research)

### What exists
- `onTickCallback` exists as a no-op in `Callbacks.kt:201`
- Rust FFI has collector functions (need to identify exact signatures)

### What to do
1. **Research first:** Find the collector FFI functions in `baml_language/crates/bridge_cffi/src/ffi/functions.rs`
2. Add JNA bindings for collector functions to `Native.kt`
3. Wire `onTickCallback` to deliver log events
4. Build Kotlin API: `BamlCollector` or similar class for accessing LLM request/response logs
5. Add tests

---

## Task G: TypeBuilder (dynamic types)

**Status:** ⬜ TODO
**Priority:** Low
**Files touched:** new Kotlin files, `Native.kt`
**No overlap with:** Tasks A–F (but needs JNA FFI research)

### What exists
- Rust FFI has `call_object_constructor` and `call_object_method_function`
- Currently, decoded objects come back as `BamlObjectRef` (opaque handle)

### What to do
1. **Research first:** Find `call_object_constructor` and `call_object_method_function` signatures in `baml_language/crates/bridge_cffi/src/ffi/functions.rs`
2. Add JNA bindings to `Native.kt`
3. Build Kotlin `TypeBuilder` API for runtime type construction
4. Enable typed media decode (replace `BamlObjectRef` with real types)
5. Add tests

---

## Recommended Execution Order

```
Phase 1 (parallel):
  ├── Agent 1 (worktree): Task A — Client override in CallOptions
  ├── Agent 2 (worktree): Task B — call_function_parse templates
  └── Agent 3 (worktree): Task C — CLI integration verification

Phase 2 (sequential, after Phase 1 merged):
  ├── Task D — Maven Central publishing
  └── Task E — Native dylib bundling

Phase 3 (parallel, after research):
  ├── Agent 4 (worktree): Task F — Collector/logging
  └── Agent 5 (worktree): Task G — TypeBuilder
```

## How to Run Agents

Each agent should use git worktree isolation to avoid conflicts:

```
Agent 1: Files = Serde.kt, function.kt.j2, function.stream.kt.j2, EncodeTest.kt
Agent 2: Files = new template, new test dir, lib.rs (codegen pipeline)
Agent 3: Files = none (read-only) or new test script
```

After all Phase 1 agents complete, merge worktrees sequentially and run the full validation:

```bash
cd engine
cargo test -p generators-kotlin --lib && \
cargo test -p generators-kotlin write_codegen_fixture -- --ignored --nocapture && \
cd language_client_kotlin && \
./gradlew cleanTest test --tests "com.boundaryml.baml.unit.*" --tests "com.boundaryml.baml.codegen.*"
```
