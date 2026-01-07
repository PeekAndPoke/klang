package io.peekandpoke.klang.script

import io.peekandpoke.klang.script.stdlib.KlangStdLib
import kotlin.reflect.KClass

/**
 * Shorthand for using the [KlangScriptEngine.Builder]
 */
fun klangScript(builder: KlangScriptEngine.Builder.() -> Unit = {}): KlangScriptEngine {
    val engineBuilder = KlangScriptEngine.Builder()
    // Always register the standard library
    engineBuilder.registerLibrary(KlangStdLib.create())
    engineBuilder.apply(builder)

    return engineBuilder.build()
}

/**
 * Shorthand factory method to create a KlangScript library.
 */
fun klangScriptLibrary(name: String, builder: KlangScriptLibrary.Builder.() -> Unit): KlangScriptLibrary {
    return KlangScriptLibrary.builder(name).apply(builder).build()
}

fun KClass<*>.getUniqueClassName() = ((simpleName ?: "Unknown") + "_${hashCode()}")
