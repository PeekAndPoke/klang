package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.CycleTime
import io.peekandpoke.klang.common.math.CycleTimeSpan
import io.peekandpoke.klang.common.math.bjorklund
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.SprudelVoiceData
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue

/**
 * Euclidean Morph Pattern: Morphs between Euclidean rhythm and even distribution.
 *
 * @param pulsesProvider Control value provider for the number of pulses
 * @param stepsProvider Control value provider for the number of steps
 * @param groovePattern Pattern controlling the morph factor (0=euclidean, 1=even)
 */
@Suppress("DuplicatedCode")
internal class EuclideanMorphPattern(
    val pulsesProvider: ControlValueProvider,
    val stepsProvider: ControlValueProvider,
    val groovePattern: SprudelPattern,
) : SprudelPattern {

    override val weight: Double get() = groovePattern.weight

    override val numSteps: Double?
        get() = if (stepsProvider is ControlValueProvider.Static) {
            (stepsProvider.value.asInt ?: 0).toDouble()
        } else {
            null
        }

    override fun estimateCycleDuration(): Double = 1.0

    companion object {
        /**
         * Create an EuclideanMorphPattern with static pulses/steps values and a groove pattern.
         */
        fun static(
            pulses: Int,
            steps: Int,
            groovePattern: SprudelPattern,
        ): EuclideanMorphPattern {
            return EuclideanMorphPattern(
                pulsesProvider = ControlValueProvider.Static((pulses).asVoiceValue()),
                stepsProvider = ControlValueProvider.Static((steps).asVoiceValue()),
                groovePattern = groovePattern
            )
        }

        /**
         * Create an EuclideanMorphPattern with control patterns for pulses and steps.
         */
        fun control(
            pulsesPattern: SprudelPattern,
            stepsPattern: SprudelPattern,
            groovePattern: SprudelPattern,
        ): EuclideanMorphPattern {
            return EuclideanMorphPattern(
                pulsesProvider = ControlValueProvider.Pattern(pulsesPattern),
                stepsProvider = ControlValueProvider.Pattern(stepsPattern),
                groovePattern = groovePattern
            )
        }

        internal fun calculateMorphedArcs(pulses: Int, steps: Int, by: Double): List<Pair<Double, Double>> {
            // from: bjorklund(pulses, steps)
            val fromList = bjorklund(pulses, steps)
            // to: Array(pulses).fill(1)
            // We only need the "on" positions.

            // Helper to get positions of 1s in a list
            fun getPositions(list: List<Int>): List<Double> {
                val positions = mutableListOf<Double>()
                val len = list.size
                for ((index, value) in list.withIndex()) {
                    if (value == 1) {
                        positions.add(index.toDouble() / len)
                    }
                }
                return positions
            }

            val fromPositions = getPositions(fromList)
            // To list has length 'pulses' and all are 1s.
            // So positions are 0/pulses, 1/pulses, ...
            val toPositions = List(pulses) { i -> i.toDouble() / pulses }

            // fromList.size is 'steps'
            val dur = 1.0 / steps

            // zipWith logic from JS
            // const b = by.mul(posb - posa).add(posa);
            // const e = b.add(dur);

            // We assume fromPositions and toPositions have same length (pulses)
            // bjorklund returns 'pulses' ones. toPositions has 'pulses' entries.
            // So zip is safe.

            return fromPositions.zip(toPositions).map { (posA, posB) ->
                val b = by * (posB - posA) + posA
                val e = b + dur
                b to e
            }
        }
    }

    override fun queryArcContextual(
        from: CycleTime,
        to: CycleTime,
        ctx: QueryContext,
    ): List<SprudelPatternEvent> {
        // Query groove pattern for morph factor over time
        val grooveEvents = groovePattern.queryArcContextual(from, to, ctx)
        if (grooveEvents.isEmpty()) return emptyList()

        val pulsesEvents = pulsesProvider.queryEvents(from, to, ctx)
        val stepsEvents = stepsProvider.queryEvents(from, to, ctx)
        if (pulsesEvents.isEmpty() || stepsEvents.isEmpty()) return emptyList()

        val result = createEventList()

        // For each groove event, sample pulses/steps and generate morphed rhythm
        for (grooveEvent in grooveEvents) {
            // Get pulses and steps values for this groove event's timespan
            val pulses = pulsesEvents
                .firstOrNull { it.part.begin < grooveEvent.part.end && it.part.end > grooveEvent.part.begin }
                ?.data?.value?.asInt ?: 0

            val steps = stepsEvents
                .firstOrNull { it.part.begin < grooveEvent.part.end && it.part.end > grooveEvent.part.begin }
                ?.data?.value?.asInt ?: 0

            if (pulses <= 0 || steps <= 0) continue

            val perc = grooveEvent.data.value?.asDouble ?: 0.0

            // Generate the morphed rhythm arcs for these values
            val arcs = calculateMorphedArcs(pulses, steps, perc)

            // Generate events from arcs that intersect with the groove event
            val startCycle = grooveEvent.part.begin.cycleIndex()
            val endCycle = grooveEvent.part.end.ceilToCycle().cycleIndex()

            for (cycle in startCycle until endCycle) {
                val cycleStart = CycleTime.ofCycleIndex(cycle)
                val cycleEnd = cycleStart + CycleTime.ONE

                // Effective window for this cycle is intersection of cycle and groove event
                val windowStart = grooveEvent.part.begin.coerceAtLeast(cycleStart)
                val windowEnd = grooveEvent.part.end.coerceAtMost(cycleEnd)

                if (windowStart >= windowEnd) continue

                for (arc in arcs) {
                    // Arc is relative to cycle start (0..1)
                    val arcStartAbs = cycleStart + CycleTime.ofCycles(arc.first)
                    val arcEndAbs = cycleStart + CycleTime.ofCycles(arc.second)

                    // Intersect arc with the query window (which is constrained by groove event)
                    val intersectStart = windowStart.coerceAtLeast(arcStartAbs)
                    val intersectEnd = windowEnd.coerceAtMost(arcEndAbs)

                    if (intersectEnd > intersectStart) {
                        val timeSpan = CycleTimeSpan(begin = intersectStart, end = intersectEnd)

                        result.add(
                            SprudelPatternEvent(
                                part = timeSpan,
                                whole = timeSpan,
                                data = SprudelVoiceData(value = 1.asVoiceValue())
                            )
                        )
                    }
                }
            }
        }

        return result
    }
}
