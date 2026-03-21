package io.peekandpoke.klang.sprudel

import kotlinx.coroutines.Deferred

/**
 * Sprudel pattern compiler.
 */
interface SprudelCompiler {
    fun compile(pattern: String): Deferred<SprudelPattern>
}
