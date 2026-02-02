package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
class StaticStrudelPattern(
    val events: List<StrudelPatternEvent>,
) : StrudelPattern.FixedWeight {
    companion object {
        private val codec = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        fun fromJson(json: String): StaticStrudelPattern =
            codec.decodeFromString(serializer(), json)
    }

    private val totalCycles: Rational = maxOf(
        Rational.ONE,
        events.maxOfOrNull { it.part.end } ?: Rational.ZERO
    ).ceil()

    override val numSteps: Rational = when (totalCycles) {
        Rational.ZERO -> Rational.ZERO
        else -> (events.size.toRational() / totalCycles)
    }

    override fun estimateCycleDuration(): Rational = totalCycles

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        if (events.isEmpty()) return emptyList()

        val fromMod = from % totalCycles
        val toMod = fromMod + (to - from)

        val offset = (from / totalCycles).floor() * totalCycles

        // println("from=$from, to=$to, offset=$offset, fromMod=$fromMod, toMod=$toMod")

        return events
            .filter { evt ->
                evt.part.begin >= fromMod && evt.part.begin < toMod
            }
            .map {
                val shiftedPart = it.part.shift(offset)
                val shiftedWhole = it.whole.shift(offset)
                it.copy(part = shiftedPart, whole = shiftedWhole)
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
