import com.google.protobuf.gradle.*

plugins {
    kotlin("jvm") version "1.9.22"
    id("com.google.protobuf") version "0.9.4"
}

// Load .env file if it exists (KEY=VALUE format, one per line)
val dotenv = file(".env").takeIf { it.exists() }?.readLines()
    ?.filter { it.isNotBlank() && !it.startsWith("#") && it.contains("=") }
    ?.associate { line ->
        val (key, value) = line.split("=", limit = 2)
        key.trim() to value.trim()
    } ?: emptyMap()

fun envOrDotenv(key: String): String =
    System.getenv(key) ?: dotenv[key] ?: ""

group = "com.boundaryml"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // JNA for FFI
    implementation("net.java.dev.jna:jna:5.14.0")

    // Protobuf
    implementation("com.google.protobuf:protobuf-kotlin:3.25.3")
    implementation("com.google.protobuf:protobuf-java:3.25.3")

    // Kotlin coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    // JSON serialization (for src_files/env_vars encoding)
    implementation("com.google.code.gson:gson:2.10.1")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.3"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                id("kotlin")
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
    // Resolve BAML_LIBRARY_PATH to an absolute path (JNA needs absolute paths)
    val explicitPath = System.getenv("BAML_LIBRARY_PATH")
        ?.let { file(it).absolutePath }  // resolve relative paths to absolute
    val dylibFile = file("../../baml_language/target/release/libbridge_cffi.dylib")
    val soFile = file("../../baml_language/target/release/libbridge_cffi.so")
    val autoPath = dylibFile.takeIf { it.exists() }?.absolutePath
        ?: soFile.takeIf { it.exists() }?.absolutePath
    val libPath = explicitPath ?: autoPath ?: ""
    if (libPath.isEmpty()) {
        println("WARNING: bridge_cffi dylib not found. Integration tests will be skipped.")
        println("  Checked: ${dylibFile.absolutePath} (exists=${dylibFile.exists()})")
        println("  Build it with: cargo build -p bridge_cffi --release --manifest-path ../../baml_language/Cargo.toml")
    }
    environment("BAML_LIBRARY_PATH", libPath)
    environment("OPENROUTER_API_KEY", envOrDotenv("OPENROUTER_API_KEY"))
    environment("BAML_LOG", envOrDotenv("BAML_LOG").ifEmpty { "info" })
    environment("RUST_LOG", "debug")
    // Show individual test results in console
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT
    }
}

kotlin {
    jvmToolchain(21)
}

sourceSets {
    main {
        proto {
            srcDir("proto")
        }
    }
}
