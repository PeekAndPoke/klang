package io.peekandpoke.klang.script

import io.peekandpoke.klang.script.stdlib.KlangStdLib
import kotlin.reflect.KClass

/** Singleton standard library instance. */
val stdlibLib: KlangScriptLibrary = KlangStdLib.create()

/**
 * Create a [KlangScriptEngine] with the standard library pre-registered.
 *
 * @param builder Optional configuration block for additional registrations
 * @return A fully configured engine
 */
fun klangScript(builder: KlangScriptEngine.Builder.() -> Unit = {}): KlangScriptEngine {
    val engineBuilder = KlangScriptEngine.Builder()
    // Always register the standard library
    engineBuilder.registerLibrary(stdlibLib)
    engineBuilder.apply(builder)

    return engineBuilder.build()
}

/**
 * Create a [KlangScriptLibrary] using the builder DSL.
 *
 * @param name Library name (used in import statements)
 * @param builder Configuration block for source code and native registrations
 * @return An immutable library instance
 */
fun klangScriptLibrary(name: String, builder: KlangScriptLibrary.Builder.() -> Unit): KlangScriptLibrary {
    return KlangScriptLibrary.builder(name).apply(builder).build()
}

/**
 * Generate a unique class name by appending the hash code to the simple name.
 *
 * @return A string like `MyClass_12345` that is unique per KClass instance
 */
fun KClass<*>.getUniqueClassName() = ((simpleName ?: "Unknown") + "_${hashCode()}")
