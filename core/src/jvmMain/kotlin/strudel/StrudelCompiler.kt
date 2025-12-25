package io.peekandpoke.klang.strudel

import kotlinx.coroutines.Deferred

/**
 * Strudel pattern compiler.
 */
interface StrudelCompiler {
    fun compile(pattern: String): Deferred<StrudelPattern>
}

