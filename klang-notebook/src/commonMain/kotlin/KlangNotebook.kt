package io.peekandpoke.klang.notebook

/**
 * Convenience package for Kotlin Notebook usage.
 *
 * This module bundles all necessary dependencies for using Klang and Strudel
 * in Kotlin Notebooks, Jupyter, or other interactive environments.
 *
 * Usage in notebooks:
 * ```kotlin
 * @file:DependsOn("io.peekandpoke.klang:klang-notebook-jvm:0.1.0")
 *
 * import io.peekandpoke.klang.script.klangScript
 * import io.peekandpoke.klang.strudel.lang.strudelLib
 *
 * val engine = klangScript {
 *     registerLibrary(strudelLib)
 * }
 * ```
 */

// Re-export main APIs for convenience
typealias KlangScript = io.peekandpoke.klang.script.KlangScriptEngine
typealias StrudelPattern = io.peekandpoke.klang.strudel.StrudelPattern
