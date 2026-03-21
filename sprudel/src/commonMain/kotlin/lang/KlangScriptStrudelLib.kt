package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.script.builder.KlangScriptExtensionBuilder
import io.peekandpoke.klang.script.builder.registerType
import io.peekandpoke.klang.script.builder.registerVarargFunctionWithCallInfo
import io.peekandpoke.klang.script.klangScriptLibrary
import io.peekandpoke.klang.script.runtime.StringValue
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.lang.docs.registerSprudelDocs

/**
 * Create the Sprudel DSL library for KlangScript.
 */
val sprudelLib = klangScriptLibrary("sprudel") {
    registerSprudelDsl()
    docs { registerSprudelDocs(this) }
}

/**
 * Registers the Sprudel DSL to the KlangScript environment.
 *
 * This bridge leverages the existing [SprudelRegistry] which is populated
 * by the property delegates in `lang.kt` and all `lang_*.kt` files. It iterates
 * over all registered functions and methods and exposes them to the KlangScript runtime.
 */
internal fun KlangScriptExtensionBuilder.registerSprudelDsl() {

    // 1. Ensure all lang_*.kt files are initialized
    // This call forces the static initializers to run in all lang files,
    // populating SprudelRegistry with all the DSL definitions.
    initSprudelDsl()

    // 2. Register Global symbols / object
    SprudelRegistry.symbols.forEach { (name, instance) ->
        registerObject(name, instance)
    }

    // 3. Register Global Functions (e.g., note(), silence, s(), etc.)
    SprudelRegistry.patternCreationFunctions.forEach { (name, handler) ->
        registerVarargFunctionWithCallInfo<Any, SprudelPattern>(name) { args, callInfo ->
            // println("Function '$name' called with CallInfo: $callInfo")
            handler(args.asSprudelDslArgs(callInfo), callInfo)
        }
    }

    // 4. Register Global Functions (e.g., note(), silence, s(), etc.)
    SprudelRegistry.patternMapperFunctions.forEach { (name, handler) ->
        registerVarargFunctionWithCallInfo<Any, PatternMapperFn>(name) { args, callInfo ->
            // println("Function '$name' called with CallInfo: $callInfo")
            handler(args.asSprudelDslArgs(callInfo), callInfo)
        }
    }

    // 5. Register Pattern Extension Methods (e.g., pattern.slow(), pattern.note(), etc.)
    // These are registered specifically for the SprudelPattern type.
    registerType<SprudelPattern> {
        SprudelRegistry.patternExtensionMethods.forEach { (name, handler) ->
            registerVarargMethodWithCallInfo<Any, SprudelPattern>(name) { args, callInfo ->
                // println("Pattern method '$name' called with CallInfo: $callInfo")
                handler(this, args.asSprudelDslArgs(callInfo), callInfo)
            }
        }
    }

    // 5. Register String Extension Methods (e.g., "pattern".slow(), "pattern".note(), etc.)
    // These are registered specifically for the String type.
    registerType<StringValue> {
        SprudelRegistry.stringExtensionMethods.forEach { (name, handler) ->
            registerVarargMethodWithCallInfo<Any, SprudelPattern>(name) { args, callInfo ->
                // println("String method '$name' called with CallInfo: $callInfo")
                handler(value, args.asSprudelDslArgs(callInfo), callInfo)
            }
        }
    }

    // 6. Register PatternMapperFn Methods
    registerType<PatternMapperFn> {
        SprudelRegistry.patternMapperExtensionMethods.forEach { (name, handler) ->
            registerVarargMethodWithCallInfo<Any, PatternMapperFn>(name) { args, callInfo ->
                handler(this, args.asSprudelDslArgs(callInfo), callInfo)
            }
        }
    }
}
