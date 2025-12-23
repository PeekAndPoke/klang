package io.peekandpoke

import io.peekandpoke.player.StrudelPattern
import kotlinx.coroutines.Deferred

/**
 * Strudel pattern compiler.
 */
interface StrudelCompiler {
    fun compile(pattern: String): Deferred<StrudelPattern>
}

