package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.script.builder.KlangScriptExtensionBuilder
import io.peekandpoke.klang.script.klangScriptLibrary
import io.peekandpoke.klang.script.runtime.wrapAsRuntimeValue
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.patterns.ContinuousPattern

/**
 * Create the Strudel DSL library for KlangScript.
 */
val strudelLib = klangScriptLibrary("strudel") {
    registerStrudelDsl()
}

/**
 * Registers the Strudel DSL to the KlangScript environment.
 *
 * This bridge leverages the existing [StrudelRegistry] which is populated
 * by the property delegates in `lang.kt`. It iterates over all registered
 * functions and methods and exposes them to the KlangScript runtime.
 */
fun KlangScriptExtensionBuilder.registerStrudelDsl() {
    // 1. Ensure lang.kt is initialized
    // Accessing this property forces the static initializers to run,
    // populating StrudelRegistry with all the DSL definitions.
    strudelLangInit = true

    // 2. Register Global symbols / object
    StrudelRegistry.symbols.forEach { (name, instance) ->
        registerObject(name, instance)
    }

    // 3. Register Global Functions (e.g., note(), silence, s(), etc.)
    StrudelRegistry.functions.forEach { (name, handler) ->
        registerFunctionRaw(name) { args ->
            // Convert RuntimeValues back to native Kotlin values
            // (RuntimeValue.value handles unwrapping of numbers, strings, and NativeObjects like StrudelPattern)
            val nativeArgs = args.map { it.value }

            // Invoke the Strudel handler
            // We suppress the cast because Strudel handlers are dynamically typed (List<Any>)
            @Suppress("UNCHECKED_CAST")
            val result = handler(nativeArgs as List<Any>)

            // Wrap the result back to RuntimeValue
            wrapAsRuntimeValue(result)
        }
    }

    // 4. Register Extension Methods (e.g., pattern.slow(), pattern.note(), etc.)
    // These are registered specifically for the StrudelPattern type.
    StrudelRegistry.methods.forEach { (name, handler) ->
        registerExtensionMethod(StrudelPattern::class, name) { receiver, args ->
            val nativeArgs = args.map { it.value }

            @Suppress("UNCHECKED_CAST")
            val result = handler(receiver, nativeArgs as List<Any>)

            wrapAsRuntimeValue(result)
        }
    }

    // 5. Register specific methods
    // TODO: improve registration in KlangScript to allow auto-detection
    registerType(ContinuousPattern::class) {
        registerMethod("range") { min: Double, max: Double ->
            range(min, max)
        }
    }
}
