package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational
import kotlin.math.max

internal class TempoModifierPattern(
    val source: StrudelPattern,
    val factorPattern: StrudelPattern,
    val invertPattern: Boolean = false,
) : StrudelPattern {
    override val weight: Double get() = source.weight

    // 1/32 cycle grain for checking factor min/max
    private val coarseGrain = Rational(1 / 8.0)

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        // 1. Analyze the factor pattern to find min and max speed in this window
        var minRawFactor = Double.MAX_VALUE
        var maxRawFactor = Double.MIN_VALUE

        var t = from
        // Ensure we check at least one point even if arc is tiny
        if (to <= from) {
            val f = getFactorAt(from, ctx)
            minRawFactor = f; maxRawFactor = f
        } else {
            // Sampling loop
            while (t < to) {
                val f = getFactorAt(t, ctx)
                minRawFactor = minOf(minRawFactor, f)
                maxRawFactor = maxOf(maxRawFactor, f)
                t += coarseGrain
            }
            // Check end point too to capture slope
            val fEnd = getFactorAt(to, ctx)
            minRawFactor = minOf(minRawFactor, fEnd)
            maxRawFactor = maxOf(maxRawFactor, fEnd)
        }

        // Determine min/max scaling applied to OUTER time to get INNER time.
        // slow(f): inner = outer / f.  Scale factor range: [1/maxRaw, 1/minRaw]
        // fast(f): inner = outer * f.  Scale factor range: [minRaw, maxRaw]

        val scaleMin: Double
        val scaleMax: Double

        if (invertPattern) { // fast()
            scaleMin = minRawFactor
            scaleMax = maxRawFactor
        } else { // slow()
            scaleMin = 1.0 / max(0.001, maxRawFactor)
            scaleMax = 1.0 / max(0.001, minRawFactor)
        }

        // Bounding box for inner query
        val innerFrom = from * Rational(scaleMin)
        val innerTo = to * Rational(scaleMax)

        // 2. Query Source
        val innerEvents = source.queryArcContextual(innerFrom, innerTo, ctx)

        // 3. Map events back
        return innerEvents.mapNotNull { ev ->
            // Estimate the outer time where this event would start.
            // If mapping is linear T_in = T_out * scale, then T_out = T_in / scale.
            // We use the average scale as a guess to find the factor.
            val avgScale = (scaleMin + scaleMax) / 2.0
            val estOuterBegin = ev.begin / Rational(max(0.001, avgScale))

            // Refined factor at the estimated time
            val fAtStart = getFactorAt(estOuterBegin, ctx)

            // Effective scale at that moment
            val effectiveScale = if (invertPattern) fAtStart else (1.0 / max(0.001, fAtStart))
            val scaleRat = Rational(effectiveScale)

            // Map back: outer = inner / scale
            val mappedBegin = ev.begin / scaleRat
            val mappedEnd = ev.end / scaleRat
            val mappedDur = ev.dur / scaleRat

            if (mappedEnd > from && mappedBegin < to) {
                ev.copy(
                    begin = mappedBegin,
                    end = mappedEnd,
                    dur = mappedDur
                )
            } else {
                null
            }
        }
    }

    private fun getFactorAt(t: Rational, ctx: QueryContext): Double {
        val evs = factorPattern.queryArcContextual(t, t, ctx)
        val raw = evs.firstOrNull()?.data?.value?.asDouble ?: 1.0
        return max(0.001, raw)
    }
}
