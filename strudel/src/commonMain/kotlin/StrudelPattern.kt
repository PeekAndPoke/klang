package io.peekandpoke.klang.strudel

import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull
import io.peekandpoke.klang.strudel.lang.strudelLib

/**
 * Strudel pattern.
 */
interface StrudelPattern {
    companion object {
        fun compile(code: String): StrudelPattern? {
            val code = """
                import * from "stdlib"
                import * from "strudel"
                
                
            """.trimIndent() + code

            val klangScriptEngine = klangScript {
                registerLibrary(strudelLib)
            }

            return klangScriptEngine.execute(code).toObjectOrNull<StrudelPattern>()
        }
    }

    /**
     * A helper interface for patterns with a fixed weight of 1.0.
     */
    interface Fixed : StrudelPattern {
        override val weight: Double get() = 1.0
    }

    /**
     * Weight for proportional time distribution in sequences.
     * Used by the @ operator in mini-notation (e.g., "bd@2" has weight 2.0).
     */
    val weight: Double

    /**
     * Queries events from [from] and [to] cycles.
     */
    fun queryArc(from: Double, to: Double): List<StrudelPatternEvent>
}

/**
 * Converts the pattern into an event source.
 */
fun StrudelPattern.asEventSource(): StrudelEventSource = StrudelEventSource(this)

/**
 * Creates a static pattern, that can be stored and used for playback with
 * any life strudel event generator.
 *
 * Acts like recording the arc [from] - [to] for later playback.
 */
fun StrudelPattern.makeStatic(from: Double, to: Double): StaticStrudelPattern =
    StaticStrudelPattern(events = queryArc(from, to))
