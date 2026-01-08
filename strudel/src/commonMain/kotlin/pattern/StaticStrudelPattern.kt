package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

@Serializable
class StaticStrudelPattern(
    val events: List<StrudelPatternEvent>,
) : StrudelPattern.Fixed {
    companion object {
        private val codec = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        fun fromJson(json: String): StaticStrudelPattern = codec.decodeFromString(serializer(), json)
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
