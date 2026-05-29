package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.CycleTime

import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.floor

@Serializable
class StaticSprudelPattern(
    val events: List<SprudelPatternEvent>,
) : SprudelPattern.FixedWeight {
    companion object {
        private val codec = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        fun fromJson(json: String): StaticSprudelPattern =
            codec.decodeFromString(serializer(), json)
    }

    /** Number of whole cycles this recording spans (at least 1). */
    private val totalCyclesInt: Int = maxOf(
        1,
        (events.maxOfOrNull { it.part.end } ?: CycleTime.ZERO).ceilToCycle().cycleIndex()
    )

    override val numSteps: Double = events.size.toDouble() / totalCyclesInt.toDouble()

    override fun estimateCycleDuration(): Double = totalCyclesInt.toDouble()

    override fun queryArcContextual(from: CycleTime, to: CycleTime, ctx: QueryContext): List<SprudelPatternEvent> {
        if (events.isEmpty()) return emptyList()

        val totalSpan = CycleTime.ofCycleIndex(totalCyclesInt)
        val fromMod = from.modCycles(totalSpan)
        val toMod = fromMod + (to - from)

        // Which full repetition of the recording does `from` fall into.
        val loopIndex = floor(from.toCycles() / totalCyclesInt).toInt()
        val offset = CycleTime.ofCycleIndex(loopIndex * totalCyclesInt)

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
