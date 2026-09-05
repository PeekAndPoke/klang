/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script

import kotlin.reflect.KClass

/**
 * Create a bare [KlangScriptEngine]: the language and runtime only, no libraries registered.
 *
 * The standard library lives in the `:klangscript-libs` module; hosts that want it call
 * `klangScript()` from there (it registers `stdlibLib` and then applies [builder]). Use this
 * factory for engines that assemble their own libraries, and in the language's own tests.
 *
 * @param builder Optional configuration block for registrations
 * @return A fully configured engine
 */
fun klangScriptEngine(
    builder: KlangScriptEngine.Builder.() -> Unit = {},
): KlangScriptEngine {
    return KlangScriptEngine.Builder().apply(builder).build()
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
