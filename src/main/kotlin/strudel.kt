package io.peekandpoke.klang

import io.peekandpoke.klang.strudel.StrudelPattern
import kotlinx.coroutines.Deferred

/**
 * Strudel pattern compiler.
 */
interface StrudelCompiler {
    fun compile(pattern: String): Deferred<StrudelPattern>
}

