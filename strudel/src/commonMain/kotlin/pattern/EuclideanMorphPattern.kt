package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.StrudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational
import io.peekandpoke.klang.strudel.math.bjorklund

internal class EuclideanMorphPattern(
    val nPulses: Int,
    val nSteps: Int,
    val groove: StrudelPattern,
) : StrudelPattern {

    companion object {
        fun calculateMorphedArcs(pulses: Int, steps: Int, by: Double): List<Pair<Rational, Rational>> {
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

    override val weight: Double get() = groove.weight

    override val steps: Rational get() = nSteps.toRational()

    override fun estimateCycleDuration(): Rational = Rational.ONE

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: QueryContext,
    ): List<StrudelPatternEvent> {
        // 1. Query the groove pattern to get the morph factor (perc) over time
        val grooveEvents = groove.queryArcContextual(from, to, ctx)
        val result = mutableListOf<StrudelPatternEvent>()

        for (ev in grooveEvents) {
            val perc = ev.data.value?.asDouble ?: 0.0

            // 2. For each groove event, generate the morphed rhythm arcs
            val arcs = calculateMorphedArcs(nPulses, nSteps, perc)

            // 3. Generate events from arcs that intersect with the current cycle and the groove event
            // Note: The arcs are within a single cycle (0..1). We need to repeat them for the duration of the groove event.
            // But wait, Strudel patterns are cyclic.
            // The morph logic calculates arcs for *one cycle*.
            // We need to check intersection of these arcs (repeated) with the groove event span (ev.begin..ev.end).

            val startCycle = ev.begin.floor().toInt()
            val endCycle = ev.end.ceil().toInt()

            for (cycle in startCycle until endCycle) {
                val cycleStart = Rational(cycle)
                val cycleEnd = cycleStart + Rational.ONE

                // Effective window for this cycle is intersection of cycle and event
                val windowStart = maxOf(ev.begin, cycleStart)
                val windowEnd = minOf(ev.end, cycleEnd)

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
                                data = StrudelVoiceData.empty.copy(value = 1.asVoiceValue()) // Gate open
                            )
                        )
                    }
                }
            }
        }

        return result
    }
}
