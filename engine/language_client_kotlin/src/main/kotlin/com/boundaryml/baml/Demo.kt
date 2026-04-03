@file:JvmName("Demo")

package com.boundaryml.baml

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList

/**
 * Interactive demo of the BAML Kotlin SDK.
 *
 * Run with: ./gradlew demo
 *
 * Shows the full SDK pipeline in action:
 *   1. Runtime creation from .baml files
 *   2. Single function call (GetGreeting)
 *   3. Multi-arg function call (Translate)
 *   4. Streaming function call with live partials (TellStory)
 *   5. Concurrent calls with independent results (Echo x5)
 *   6. Error handling (non-existent function)
 */
fun main() = runBlocking {
    println()
    println("═══════════════════════════════════════════════════════════════")
    println("  BAML Kotlin SDK Demo")
    println("═══════════════════════════════════════════════════════════════")
    println()

    // ── Step 0: Load FFI ────────────────────────────────────────────
    print("[1/7] Loading bridge_cffi dylib... ")
    try {
        BamlFfi.load()
        println("OK")
    } catch (e: Throwable) {
        println("FAILED")
        println()
        println("  Could not load bridge_cffi dylib: ${e.message}")
        println("  Build it first:")
        println("    cargo build -p bridge_cffi --release --manifest-path ../../baml_language/Cargo.toml")
        println()
        return@runBlocking
    }

    // ── Step 1: Version ─────────────────────────────────────────────
    val version = BamlRuntime.version()
    println("[2/7] BAML engine version: $version")

    // ── Step 2: Create runtime ──────────────────────────────────────
    val apiKey = System.getenv("OPENROUTER_API_KEY") ?: ""
    if (apiKey.isEmpty()) {
        println()
        println("  OPENROUTER_API_KEY not set. Set it to run LLM demos:")
        println("    export OPENROUTER_API_KEY=sk-or-v1-...")
        println()
        return@runBlocking
    }

    val srcFiles = mapOf(
        "client.baml" to """
            client<llm> MyClient {
                provider openai
                options {
                    model "openai/gpt-4o-mini"
                    api_key env.OPENROUTER_API_KEY
                    base_url "https://openrouter.ai/api"
                }
            }
        """.trimIndent(),
        "functions.baml" to """
            function GetGreeting(name: string) -> string {
                client MyClient
                prompt #"
                    Say hello to {{ name }}. Reply with just the greeting, nothing else.
                "#
            }

            function Translate(text: string, language: string) -> string {
                client MyClient
                prompt #"
                    Translate "{{ text }}" to {{ language }}. Reply with just the translation, nothing else.
                "#
            }

            function TellStory(topic: string) -> string {
                client MyClient
                prompt #"
                    Tell a very short story (2-3 sentences) about {{ topic }}.
                "#
            }

            function Echo(input: string) -> string {
                client MyClient
                prompt #"
                    Reply with exactly this text, nothing else: {{ input }}
                "#
            }
        """.trimIndent()
    )

    print("[3/7] Creating BAML runtime (2 .baml files)... ")
    val runtime = BamlRuntime.create(rootPath = ".", srcFiles = srcFiles)
    val client = BamlClient(runtime)
    println("OK")
    println()

    // ── Demo 1: Simple function call ────────────────────────────────
    println("───────────────────────────────────────────────────────────────")
    println("  Demo 1: Simple function call")
    println("───────────────────────────────────────────────────────────────")
    println("  BAML:   function GetGreeting(name: string) -> string")
    println("  Input:  name = \"World\"")
    print("  Calling... ")
    try {
        val result = client.callFunction("GetGreeting", Serde.encodeArgs(mapOf("name" to "World")))
        println("OK")
        println("  Result: $result")
    } catch (e: BamlException) {
        println("BamlException (SDK worked, parse/LLM issue)")
        println("  Error:  ${e.message}")
    }
    println()

    // ── Demo 2: Multi-arg function call ─────────────────────────────
    println("───────────────────────────────────────────────────────────────")
    println("  Demo 2: Multi-argument function call")
    println("───────────────────────────────────────────────────────────────")
    println("  BAML:   function Translate(text: string, language: string) -> string")
    println("  Input:  text = \"Hello, world!\", language = \"Japanese\"")
    print("  Calling... ")
    try {
        val result = client.callFunction(
            "Translate",
            Serde.encodeArgs(mapOf("text" to "Hello, world!", "language" to "Japanese"))
        )
        println("OK")
        println("  Result: $result")
    } catch (e: BamlException) {
        println("BamlException (SDK worked, parse/LLM issue)")
        println("  Error:  ${e.message}")
    }
    println()

    // ── Demo 3: Streaming ───────────────────────────────────────────
    println("───────────────────────────────────────────────────────────────")
    println("  Demo 3: Streaming function call")
    println("───────────────────────────────────────────────────────────────")
    println("  BAML:   function TellStory(topic: string) -> string")
    println("  Input:  topic = \"a brave robot\"")
    println("  Stream: ", )
    try {
        val flow = client.streamFunction("TellStory", Serde.encodeArgs(mapOf("topic" to "a brave robot")))
        val results = flow.toList()
        var partialCount = 0
        for (result in results) {
            when {
                result.hasStreamData -> {
                    partialCount++
                    // Show first few partials, then summarize
                    if (partialCount <= 3) {
                        val preview = result.streamData?.toString()?.take(60) ?: "(null)"
                        println("    partial #$partialCount: $preview...")
                    }
                }
                result.hasData -> {
                    if (partialCount > 3) {
                        println("    ... (${partialCount - 3} more partials)")
                    }
                    println("    FINAL:  ${result.data}")
                }
                result.error != null -> {
                    println("    ERROR:  ${result.error!!.message}")
                }
            }
        }
        println("  Total stream events: ${results.size} ($partialCount partials + 1 final)")
    } catch (e: BamlException) {
        println("    BamlException (SDK worked, parse/LLM issue)")
        println("    Error: ${e.message}")
    }
    println()

    // ── Demo 4: Concurrency ─────────────────────────────────────────
    println("───────────────────────────────────────────────────────────────")
    println("  Demo 4: Concurrent calls (5 simultaneous)")
    println("───────────────────────────────────────────────────────────────")
    println("  BAML:   function Echo(input: string) -> string")
    println("  Inputs: \"alpha\", \"beta\", \"gamma\", \"delta\", \"epsilon\"")
    println("  Launching 5 coroutines on Dispatchers.Default...")
    val inputs = listOf("alpha", "beta", "gamma", "delta", "epsilon")
    val startTime = System.currentTimeMillis()
    val results = coroutineScope {
        inputs.map { input ->
            async(Dispatchers.Default) {
                val t0 = System.currentTimeMillis()
                try {
                    val result = client.callFunction("Echo", Serde.encodeArgs(mapOf("input" to input)))
                    val elapsed = System.currentTimeMillis() - t0
                    Triple(input, result?.toString(), elapsed)
                } catch (e: BamlException) {
                    val elapsed = System.currentTimeMillis() - t0
                    Triple(input, "(BamlException: ${e.message?.take(40)})", elapsed)
                }
            }
        }.map { it.await() }
    }
    val totalElapsed = System.currentTimeMillis() - startTime
    for ((input, result, elapsed) in results) {
        val preview = result?.take(50) ?: "(null)"
        println("    \"$input\" -> \"$preview\" (${elapsed}ms)")
    }
    println("  Total wall time: ${totalElapsed}ms (parallel, not sequential)")
    println()

    // ── Demo 5: Error handling ──────────────────────────────────────
    println("───────────────────────────────────────────────────────────────")
    println("  Demo 5: Error handling")
    println("───────────────────────────────────────────────────────────────")
    println("  Calling non-existent function \"DoesNotExist\"...")
    try {
        client.callFunction("DoesNotExist", Serde.encodeArgs(mapOf("x" to "y")))
        println("  Unexpected: no error thrown!")
    } catch (e: Exception) {
        println("  Caught ${e.javaClass.simpleName}: ${e.message?.take(80)}")
    }
    println()

    // ── Cleanup ─────────────────────────────────────────────────────
    runtime.destroy()

    println("═══════════════════════════════════════════════════════════════")
    println("  Demo complete. All calls went through the full pipeline:")
    println("    Kotlin -> protobuf -> JNA FFI -> Rust dylib -> LLM -> callback -> Kotlin")
    println("═══════════════════════════════════════════════════════════════")
    println()
}
