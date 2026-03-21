package io.peekandpoke.klang.sprudel

import kotlinx.coroutines.Deferred

/**
 * Strudel pattern compiler.
 */
interface StrudelCompiler {
    fun compile(pattern: String): Deferred<StrudelPattern>
}
