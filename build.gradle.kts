plugins {
    kotlin("jvm") version "2.2.21"
    id("org.jetbrains.kotlinx.atomicfu") version "0.30.0-beta"
}

group = "io.peekandpoke"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    // GraalVM
    implementation("org.graalvm.polyglot:polyglot:24.2.2")
    implementation("org.graalvm.polyglot:js:24.2.2")
    // tests
    testImplementation(kotlin("test"))

}

kotlin {
    jvmToolchain(23)
}

tasks.test {
    useJUnitPlatform()
}
