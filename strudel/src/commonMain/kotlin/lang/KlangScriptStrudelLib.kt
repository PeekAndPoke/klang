package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.script.builder.KlangScriptExtensionBuilder
import io.peekandpoke.klang.script.builder.registerType
import io.peekandpoke.klang.script.builder.registerVarargFunctionWithCallInfo
import io.peekandpoke.klang.script.klangScriptLibrary
import io.peekandpoke.klang.script.runtime.StringValue
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.lang.StrudelDslArg.Companion.asStrudelDslArgs

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
 * by the property delegates in `lang.kt` and all `lang_*.kt` files. It iterates
 * over all registered functions and methods and exposes them to the KlangScript runtime.
 */
fun KlangScriptExtensionBuilder.registerStrudelDsl() {

    // 1. Ensure all lang_*.kt files are initialized
    // This call forces the static initializers to run in all lang files,
    // populating StrudelRegistry with all the DSL definitions.
    initStrudelLang()

    // 2. Register Global symbols / object
    StrudelRegistry.symbols.forEach { (name, instance) ->
        registerObject(name, instance)
    }

    // 3. Register Global Functions (e.g., note(), silence, s(), etc.)
    StrudelRegistry.functions.forEach { (name, handler) ->
        registerVarargFunctionWithCallInfo<Any, StrudelPattern>(name) { args, callInfo ->
            // println("Function '$name' called with CallInfo: $callInfo")
            handler(args.asStrudelDslArgs(callInfo), callInfo)
        }
    }

    // 4. Register Pattern Extension Methods (e.g., pattern.slow(), pattern.note(), etc.)
    // These are registered specifically for the StrudelPattern type.
    registerType<StrudelPattern> {
        StrudelRegistry.patternExtensionMethods.forEach { (name, handler) ->
            registerVarargMethodWithCallInfo<Any, StrudelPattern>(name) { args, callInfo ->
                // println("Pattern method '$name' called with CallInfo: $callInfo")
                handler(this, args.asStrudelDslArgs(callInfo), callInfo)
            }
        }
    }

    // 5. Register String Extension Methods (e.g., "pattern".slow(), "pattern".note(), etc.)
    // These are registered specifically for the String type.
    registerType<StringValue> {
        StrudelRegistry.stringExtensionMethods.forEach { (name, handler) ->
            registerVarargMethodWithCallInfo<Any, StrudelPattern>(name) { args, callInfo ->
                // println("String method '$name' called with CallInfo: $callInfo")
                handler(value, args.asStrudelDslArgs(callInfo), callInfo)
            }
        }
    }
}
