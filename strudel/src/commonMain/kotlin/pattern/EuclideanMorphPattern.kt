package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.StrudelVoiceValue
import io.peekandpoke.klang.strudel.StrudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational
import io.peekandpoke.klang.strudel.math.bjorklund

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
    val groovePattern: StrudelPattern,
) : StrudelPattern {

    override val weight: Double get() = groovePattern.weight

    override val steps: Rational?
        get() = if (stepsProvider is ControlValueProvider.Static) {
            (stepsProvider.value.asInt ?: 0).toRational()
        } else {
            null
        }

    override fun estimateCycleDuration(): Rational = Rational.ONE

    companion object {
        /**
         * Create an EuclideanMorphPattern with static pulses/steps values and a groove pattern.
         */
        fun static(
            pulses: Int,
            steps: Int,
            groovePattern: StrudelPattern,
        ): EuclideanMorphPattern {
            return EuclideanMorphPattern(
                pulsesProvider = ControlValueProvider.Static(StrudelVoiceValue.Num(pulses.toDouble())),
                stepsProvider = ControlValueProvider.Static(StrudelVoiceValue.Num(steps.toDouble())),
                groovePattern = groovePattern
            )
        }

        /**
         * Create an EuclideanMorphPattern with control patterns for pulses and steps.
         */
        fun control(
            pulsesPattern: StrudelPattern,
            stepsPattern: StrudelPattern,
            groovePattern: StrudelPattern,
        ): EuclideanMorphPattern {
            return EuclideanMorphPattern(
                pulsesProvider = ControlValueProvider.Pattern(pulsesPattern),
                stepsProvider = ControlValueProvider.Pattern(stepsPattern),
                groovePattern = groovePattern
            )
        }

        internal fun calculateMorphedArcs(pulses: Int, steps: Int, by: Double): List<Pair<Rational, Rational>> {
            // from: bjorklund(pulses, steps)
            val fromList = bjorklund(pulses, steps)
            // to: Array(pulses).fill(1)
            // We only need the "on" positions.

            // Helper to get positions of 1s in a list
            fun getPositions(list: List<Int>): List<Rational> {
                val positions = mutableListOf<Rational>()
                val len = list.size
                for ((index, value) in list.withIndex()) {
                    if (value == 1) {
                        positions.add(Rational(index) / Rational(len))
                    }
                }
                return positions
            }

            val fromPositions = getPositions(fromList)
            // To list has length 'pulses' and all are 1s.
            // So positions are 0/pulses, 1/pulses, ...
            val toPositions = List(pulses) { i -> Rational(i) / Rational(pulses) }

            // fromList.size is 'steps'
            val dur = Rational.ONE / Rational(steps)
            val byRat = by.toRational()

            // zipWith logic from JS
            // const b = by.mul(posb - posa).add(posa);
            // const e = b.add(dur);

            // We assume fromPositions and toPositions have same length (pulses)
            // bjorklund returns 'pulses' ones. toPositions has 'pulses' entries.
            // So zip is safe.

            return fromPositions.zip(toPositions).map { (posA, posB) ->
                val b = byRat * (posB - posA) + posA
                val e = b + dur
                b to e
            }
        }
    }

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: QueryContext,
    ): List<StrudelPatternEvent> {
        // Query groove pattern for morph factor over time
        val grooveEvents = groovePattern.queryArcContextual(from, to, ctx)
        if (grooveEvents.isEmpty()) return emptyList()

        val pulsesEvents = pulsesProvider.queryEvents(from, to, ctx)
        val stepsEvents = stepsProvider.queryEvents(from, to, ctx)
        if (pulsesEvents.isEmpty() || stepsEvents.isEmpty()) return emptyList()

        val result = mutableListOf<StrudelPatternEvent>()

        // For each groove event, sample pulses/steps and generate morphed rhythm
        for (grooveEvent in grooveEvents) {
            // Get pulses and steps values for this groove event's timespan
            val pulses = pulsesEvents
                .firstOrNull { it.begin < grooveEvent.end && it.end > grooveEvent.begin }
                ?.data?.value?.asInt ?: 0

            val steps = stepsEvents
                .firstOrNull { it.begin < grooveEvent.end && it.end > grooveEvent.begin }
                ?.data?.value?.asInt ?: 0

            if (pulses <= 0 || steps <= 0) continue

            val perc = grooveEvent.data.value?.asDouble ?: 0.0

            // Generate the morphed rhythm arcs for these values
            val arcs = calculateMorphedArcs(pulses, steps, perc)

            // Generate events from arcs that intersect with the groove event
            val startCycle = grooveEvent.begin.floor().toInt()
            val endCycle = grooveEvent.end.ceil().toInt()

            for (cycle in startCycle until endCycle) {
                val cycleStart = Rational(cycle)
                val cycleEnd = cycleStart + Rational.ONE

                // Effective window for this cycle is intersection of cycle and groove event
                val windowStart = maxOf(grooveEvent.begin, cycleStart)
                val windowEnd = minOf(grooveEvent.end, cycleEnd)

                if (windowStart >= windowEnd) continue

                for (arc in arcs) {
                    // Arc is relative to cycle start (0..1)
                    val arcStartAbs = cycleStart + arc.first
                    val arcEndAbs = cycleStart + arc.second

                    // Intersect arc with the query window (which is constrained by groove event)
                    val intersectStart = maxOf(windowStart, arcStartAbs)
                    val intersectEnd = minOf(windowEnd, arcEndAbs)

                    if (intersectEnd > intersectStart) {
                        result.add(
                            StrudelPatternEvent(
                                begin = intersectStart,
                                end = intersectEnd,
                                dur = intersectEnd - intersectStart,
                                data = StrudelVoiceData.empty.copy(value = 1.asVoiceValue())
                            )
                        )
                    }
                }
            }
        }

        return result
    }
}
