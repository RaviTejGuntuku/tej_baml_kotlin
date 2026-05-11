import com.google.protobuf.gradle.*
import java.io.ByteArrayOutputStream

plugins {
    kotlin("jvm") version "2.0.21"
    id("com.google.protobuf") version "0.9.4"
    `maven-publish`
    signing
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

fun runCommand(
    workingDir: File,
    vararg command: String,
    env: Map<String, String> = emptyMap()
) {
    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()
    val result = providers.exec {
        this.workingDir = workingDir
        commandLine(*command)
        environment(env)
        standardOutput = stdout
        errorOutput = stderr
    }.result.get()

    if (result.exitValue != 0) {
        throw GradleException(
            buildString {
                append("Command failed (${command.joinToString(" ")}), exit code ${result.exitValue}")
                val err = stderr.toString().trim()
                val out = stdout.toString().trim()
                if (err.isNotEmpty()) {
                    append("\nSTDERR:\n")
                    append(err)
                }
                if (out.isNotEmpty()) {
                    append("\nSTDOUT:\n")
                    append(out)
                }
            }
        )
    }
}

group = "io.github.ravitejguntuku"
version = "0.1.0"

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

val buildBridgeCffi by tasks.registering(Exec::class) {
    group = "build"
    description = "Build the desktop bridge_cffi library used by integration tests"
    workingDir = file("../../baml_language")
    commandLine(
        "cargo",
        "build",
        "-p",
        "bridge_cffi",
        "--release",
        "--manifest-path",
        "Cargo.toml"
    )
}

tasks.test {
    dependsOn(buildBridgeCffi)
}

// ./gradlew demo — runs the interactive demo showing the full SDK pipeline
tasks.register<JavaExec>("demo") {
    group = "application"
    description = "Run the BAML Kotlin SDK interactive demo"
    mainClass.set("com.boundaryml.baml.Demo")
    classpath = sourceSets["main"].runtimeClasspath

    // Resolve dylib path (same logic as test task)
    val explicitPath = System.getenv("BAML_LIBRARY_PATH")
        ?.let { file(it).absolutePath }
    val dylibFile = file("../../baml_language/target/release/libbridge_cffi.dylib")
    val soFile = file("../../baml_language/target/release/libbridge_cffi.so")
    val autoPath = dylibFile.takeIf { it.exists() }?.absolutePath
        ?: soFile.takeIf { it.exists() }?.absolutePath
    val libPath = explicitPath ?: autoPath ?: ""
    environment("BAML_LIBRARY_PATH", libPath)
    environment("OPENROUTER_API_KEY", envOrDotenv("OPENROUTER_API_KEY"))

    // Show output directly in terminal
    standardInput = System.`in`
}

kotlin {
    jvmToolchain(21)
}

sourceSets {
    main {
        proto {
            srcDir("proto")
        }
        // Bundle native libraries into the JAR
        resources {
            srcDir("native-libs")
        }
    }
}

// Copy native libraries into the resource directory structure that JNA expects.
// JNA looks for: /com/sun/jna/{platform}/libname.so|dylib|dll
// We use a simpler layout: /native/{platform}/libbridge_cffi.so|dylib
val copyNativeLibs by tasks.registering(Copy::class) {
    group = "build"
    description = "Copy native bridge_cffi libraries for bundling in JAR"

    val bamlLangTarget = file("../../baml_language/target")

    // macOS arm64 (Apple Silicon)
    from(bamlLangTarget.resolve("release/libbridge_cffi.dylib")) {
        into("native/darwin-aarch64")
    }
    // Android arm64
    from(bamlLangTarget.resolve("aarch64-linux-android/release/libbridge_cffi.so")) {
        into("native/android-arm64")
    }
    // Android x86_64 (emulator)
    from(bamlLangTarget.resolve("x86_64-linux-android/release/libbridge_cffi.so")) {
        into("native/android-x86_64")
    }

    into(layout.buildDirectory.dir("native-libs"))
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.named("processResources") {
    dependsOn(copyNativeLibs)
}

// Also include the copied native libs as resources
sourceSets.main {
    resources.srcDir(layout.buildDirectory.dir("native-libs"))
}

// Maven Central requires sources + javadoc JARs
val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().kotlin)
    from(sourceSets.main.get().proto)
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    from(tasks.named("javadoc"))
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifact(sourcesJar)
            artifact(javadocJar)

            groupId = "io.github.ravitejguntuku"
            artifactId = "baml-kotlin"
            version = project.version.toString()

            pom {
                name.set("BAML Kotlin SDK")
                description.set("Kotlin/JVM SDK for calling BAML functions with full type safety")
                url.set("https://github.com/BoundaryML/baml")

                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }

                developers {
                    developer {
                        id.set("boundaryml")
                        name.set("BoundaryML")
                        url.set("https://github.com/BoundaryML")
                    }
                }

                scm {
                    url.set("https://github.com/BoundaryML/baml")
                    connection.set("scm:git:git://github.com/BoundaryML/baml.git")
                    developerConnection.set("scm:git:ssh://github.com/BoundaryML/baml.git")
                }
            }
        }
    }

}

signing {
    sign(publishing.publications["maven"])
    isRequired = findProperty("signing.keyId") != null
}

// Publish to Maven Central:
//   ./gradlew publishToMavenLocal   (builds + signs artifacts)
//   ./gradlew publishCentralBundle  (zips + uploads to Central Portal)
tasks.register("publishCentralBundle") {
    group = "publishing"
    description = "Upload signed artifacts to Maven Central Portal"
    dependsOn("publishToMavenLocal")

    doLast {
        val groupPath = project.group.toString().replace('.', '/')
        val artifactId = "baml-kotlin"
        val ver = project.version.toString()
        val homeDir = System.getProperty("user.home")
        val repoDir = file("$homeDir/.m2/repository/$groupPath/$artifactId/$ver")

        require(repoDir.exists()) { "Local Maven repo not found at $repoDir" }

        // Generate MD5 and SHA1 checksums for all artifacts
        runCommand(repoDir,
            "bash", "-c", """
                for f in *.jar *.pom *.module *.asc; do
                    [ -f "${'$'}f" ] || continue
                    md5 -q "${'$'}f" > "${'$'}f.md5"
                    shasum -a 1 "${'$'}f" | cut -d' ' -f1 > "${'$'}f.sha1"
                done
            """.trimIndent()
        )

        val bundleZip = layout.buildDirectory.file("central-bundle.zip").get().asFile
        bundleZip.delete()

        // Create ZIP with correct directory structure
        ant.withGroovyBuilder {
            "zip"("destfile" to bundleZip) {
                "zipfileset"("dir" to repoDir, "prefix" to "$groupPath/$artifactId/$ver") {
                    "include"("name" to "*.jar")
                    "include"("name" to "*.pom")
                    "include"("name" to "*.asc")
                    "include"("name" to "*.module")
                    "include"("name" to "*.md5")
                    "include"("name" to "*.sha1")
                }
            }
        }

        val username = findProperty("ossrhUsername") as String? ?: ""
        val password = findProperty("ossrhPassword") as String? ?: ""

        // Upload via curl
        runCommand(
            project.projectDir,
            "curl", "--fail", "-X", "POST",
            "https://central.sonatype.com/api/v1/publisher/upload?publishingType=AUTOMATIC",
            "-u", "$username:$password",
            "-F", "bundle=@${bundleZip.absolutePath}",
            "-v"
        )

        println("\nUploaded to Maven Central! Track status at: https://central.sonatype.com/publishing")
    }
}
