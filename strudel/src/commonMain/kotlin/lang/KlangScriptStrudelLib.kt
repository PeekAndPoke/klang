package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.script.builder.KlangScriptExtensionBuilder
import io.peekandpoke.klang.script.builder.registerVarargFunction
import io.peekandpoke.klang.script.klangScriptLibrary
import io.peekandpoke.klang.strudel.StrudelPattern

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
        registerVarargFunction(name) { args -> handler(args) }
    }

    // 4. Register Extension Methods (e.g., pattern.slow(), pattern.note(), etc.)
    // These are registered specifically for the StrudelPattern type.
    registerType(StrudelPattern::class) {
        StrudelRegistry.methods.forEach { (name, handler) ->
            registerVarargMethod(name) { args -> handler(this, args) }
        }
    }
}
