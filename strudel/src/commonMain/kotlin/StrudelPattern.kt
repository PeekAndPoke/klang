package io.peekandpoke.klang.strudel

import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull
import io.peekandpoke.klang.strudel.lang.strudelLib
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

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

    @Serializable
    class Static(
        val events: List<StrudelPatternEvent>,
    ) : StrudelPattern {

        companion object {
            private val codec = Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }

            fun fromJson(json: String): Static = codec.decodeFromString(serializer(), json)
        }

        private val totalCycles = ceil(max(1.0, events.maxOfOrNull { it.end } ?: 0.0))

        override fun queryArc(from: Double, to: Double): List<StrudelPatternEvent> {
            if (events.isEmpty()) return emptyList()

            val fromMod = from % totalCycles
            val toMod = fromMod + (to - from)

            val offset = floor(from / totalCycles) * totalCycles

            // println("from=$from, to=$to, offset=$offset, fromMod=$fromMod, toMod=$toMod")

            return events
                .filter { evt ->

                    evt.begin >= fromMod && evt.begin < toMod
                }
                .map {
                    it.copy(begin = it.begin + offset, end = it.end + offset)
                }
        }

        fun toJson(): String {
            val lines = events.map { codec.encodeToString(it) }

            return buildString {
                appendLine("{")
                appendLine("  \"events\": [")
                lines.forEachIndexed { idx, line ->
                    appendLine("    $line${if (idx < lines.lastIndex) "," else ""}")
                }
                appendLine("  ]")
                appendLine("}")
            }
        }
    }

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
fun StrudelPattern.makeStatic(from: Double, to: Double): StrudelPattern.Static =
    StrudelPattern.Static(events = queryArc(from, to))
