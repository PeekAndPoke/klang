package io.peekandpoke.klang.script

import io.peekandpoke.klang.script.builder.KlangScriptExtension
import io.peekandpoke.klang.script.builder.KlangScriptExtensionBuilder

/**
 * Shorthand factory method to create a KlangScript library.
 */
fun klangScriptLibrary(name: String, builder: KlangScriptLibrary.Builder.() -> Unit) =
    KlangScriptLibrary.builder(name).apply(builder).build()

/**
 * Immutable library definition for KlangScript
 *
 * A library bundles KlangScript source code with native Kotlin registrations (functions, types, extension methods).
 * Libraries are created using the builder pattern and become immutable after construction.
 *
 * **Lifecycle**:
 * 1. Create builder: `KlangScriptLibrary.builder("name")`
 * 2. Configure library: `.source()`, `.registerNativeFunction()`, etc.
 * 3. Build immutable library: `.build()`
 * 4. Register with engine: `engine.registerLibrary(library)`
 *
 * **Example**:
 * ```kotlin
 * val strudelLib = KlangScriptLibrary.builder("strudel")
 *     .source("""
 *         let sequence = (pattern) => note(pattern)
 *         export { sequence }
 *     """)
 *     .registerNativeFunction<String, StrudelPattern>("note") { pattern ->
 *         StrudelPattern(pattern)
 *     }
 *     .registerExtensionMethod1<StrudelPattern, String, StrudelPattern>("sound") { receiver, soundName ->
 *         receiver.sound(soundName)
 *     }
 *     .build()
 *
 * engine.registerLibrary(strudelLib)
 * ```
 *
 * @param name The library name (used in import statements)
 */
class KlangScriptLibrary internal constructor(
    val name: String,
    val sourceCode: String,
    val native: KlangScriptExtension,
) {
    companion object {
        /**
         * Create a new library builder
         *
         * @param name The library name (used in import statements)
         * @return A new builder for configuring the library
         *
         * Example:
         * ```kotlin
         * val lib = KlangScriptLibrary.builder("mylib")
         *     .source("...")
         *     .registerNativeFunction(...) { ... }
         *     .build()
         * ```
         */
        fun builder(name: String): Builder = Builder(name)
    }

    /**
     * Builder for creating immutable KlangScriptLibrary instances
     *
     * The builder is mutable during configuration, then produces an immutable library.
     * All registration methods return `this` for fluent chaining.
     *
     * @param name The library name
     */
    class Builder(
        private val name: String,
        private val registry: KlangScriptExtensionBuilder = KlangScriptExtensionBuilder(),
    ) : KlangScriptExtensionBuilder by registry {
        /** The KlangScript source code for this library (optional) */
        private val sourceCode = mutableListOf<String>()

        fun source(sourceCode: String) = apply { this.sourceCode.add(sourceCode) }

        /**
         * Build an immutable KlangScriptLibrary from this builder's configuration
         *
         * After calling build(), the library cannot be modified.
         * The library can be safely shared across multiple engines.
         *
         * @return An immutable KlangScriptLibrary
         */
        fun build(): KlangScriptLibrary {
            return KlangScriptLibrary(
                name = name,
                sourceCode = sourceCode.joinToString("\n\n"),
                native = registry.buildNativeRegistry(),
            )
        }
    }
}
