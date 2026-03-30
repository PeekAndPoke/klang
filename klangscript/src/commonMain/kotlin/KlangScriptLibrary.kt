package io.peekandpoke.klang.script

import io.peekandpoke.klang.common.SourceLocation
import io.peekandpoke.klang.script.builder.KlangScriptExtension
import io.peekandpoke.klang.script.builder.KlangScriptExtensionBuilder
import io.peekandpoke.klang.script.builder.NativeObjectExtensionsBuilder
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.runtime.RuntimeValue
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangProperty
import io.peekandpoke.klang.script.types.KlangSymbol
import kotlin.reflect.KClass

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
    val docs: KlangDocsRegistry,
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

        /** Per-library documentation registry */
        private val docs = KlangDocsRegistry()

        /**
         * Append KlangScript source code to this library.
         *
         * @param sourceCode KlangScript source to include
         * @return This builder for chaining
         */
        fun source(sourceCode: String) = apply { this.sourceCode.add(sourceCode) }

        /**
         * Configure the documentation registry for this library.
         *
         * @param block Configuration block applied to the [KlangDocsRegistry]
         * @return This builder for chaining
         */
        fun docs(block: KlangDocsRegistry.() -> Unit) = apply { docs.block() }

        // ── Docs-aware registration overloads ─────────────────────────────

        /** Register a [KlangCallable] as documentation for a manually registered function. */
        fun registerDocs(callable: KlangCallable) = apply {
            docs.register(callable.toSymbol())
        }

        /** Register an extension method with inline documentation. */
        fun <T : Any> registerExtensionMethod(
            receiver: KClass<T>,
            name: String,
            docs: KlangCallable,
            fn: (T, List<RuntimeValue>, SourceLocation?) -> RuntimeValue,
        ) {
            registry.registerExtensionMethod(receiver, name, fn)
            this.docs.register(docs.toSymbol())
        }

        /** Register an extension method (with engine access) with inline documentation. */
        fun <T : Any> registerExtensionMethodWithEngine(
            receiver: KClass<T>,
            name: String,
            docs: KlangCallable,
            fn: (T, List<RuntimeValue>, SourceLocation?, KlangScriptEngine) -> RuntimeValue,
        ) {
            registry.registerExtensionMethodWithEngine(receiver, name, fn)
            this.docs.register(docs.toSymbol())
        }

        /** Register a raw function with inline documentation. */
        fun registerFunctionRaw(
            name: String,
            docs: KlangCallable,
            fn: (List<RuntimeValue>, SourceLocation?) -> RuntimeValue,
        ) {
            registry.registerFunctionRaw(name, fn)
            this.docs.register(docs.toSymbol())
        }

        /** Register a native object with inline documentation. */
        fun <T : Any> registerObject(
            name: String,
            obj: T,
            docs: KlangProperty,
            block: NativeObjectExtensionsBuilder<T>.() -> Unit = {},
        ) {
            registry.registerObject(name, obj, block)
            this.docs.register(KlangSymbol(name = name, category = "object", variants = listOf(docs)))
        }

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
                docs = docs.snapshot(),
            )
        }
    }
}

/** Convert a [KlangCallable] to a [KlangSymbol] with receiver type as category. */
fun KlangCallable.toSymbol(): KlangSymbol {
    return KlangSymbol(
        name = name,
        category = receiver?.simpleName ?: "",
        library = library,
        variants = listOf(this),
    )
}
