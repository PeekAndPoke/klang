package io.peekandpoke.klang.script

import io.peekandpoke.klang.script.stdlib.KlangStdLib
import io.peekandpoke.ultra.common.MutableTypedAttributes
import kotlin.reflect.KClass

/** Singleton standard library instance. */
val stdlibLib: KlangScriptLibrary = KlangStdLib.create()

/**
 * Create a [KlangScriptEngine] with the standard library pre-registered.
 *
 * @param attrs Mutable typed attributes for engine-level state. The app can set
 *   values (e.g. ExciterRegistrar) on it before or after building.
 * @param builder Optional configuration block for additional registrations
 * @return A fully configured engine
 */
fun klangScript(
    attrs: MutableTypedAttributes = MutableTypedAttributes.empty(),
    builder: KlangScriptEngine.Builder.() -> Unit = {},
): KlangScriptEngine {
    val engineBuilder = KlangScriptEngine.Builder()
    // Always register the standard library
    engineBuilder.registerLibrary(stdlibLib)
    engineBuilder.apply(builder)

    return engineBuilder.build(attrs)
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
