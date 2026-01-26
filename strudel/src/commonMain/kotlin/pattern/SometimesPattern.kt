package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

/**
 * A pattern that probabilistically routes events to a transformation or keeps/discards them.
 * This unifies logic for `degrade`, `undegrade`, `sometimes`, and `someCycles`.
 */
internal class SometimesPattern private constructor(
    val source: StrudelPattern,
    val probabilityPattern: StrudelPattern? = null,
    val probabilityValue: Double = 0.5,
    val seedStrategy: (StrudelPatternEvent) -> Any = { it.begin },
    val onMatch: ((StrudelPattern) -> StrudelPattern)? = null,
    val onMiss: ((StrudelPattern) -> StrudelPattern)? = null,
) : StrudelPattern {

    companion object {
        private val EPS = 1e-7.toRational()

        // Sentinel function to indicate discarding events.
        // We use a property to ensure stable reference for equality checks.
        private val DISCARD: (StrudelPattern) -> StrudelPattern = { EmptyPattern }

        /**
         * Creates a pattern that applies [onMatch] if the probability check succeeds.
         * If the check fails, events are kept as is (identity).
         */
        fun applyOnMatch(
            source: StrudelPattern,
            probabilityPattern: StrudelPattern? = null,
            probabilityValue: Double = 0.5,
            seedStrategy: (StrudelPatternEvent) -> Any = { it.begin },
            onMatch: (StrudelPattern) -> StrudelPattern,
        ) = SometimesPattern(
            source = source,
            probabilityPattern = probabilityPattern,
            probabilityValue = probabilityValue,
            seedStrategy = seedStrategy,
            onMatch = onMatch
        )

        /**
         * Creates a pattern that discards events if the probability check succeeds (Match).
         * If the check fails (Miss), events are kept as is.
         * Used for `degradeBy`.
         */
        fun discardOnMatch(
            source: StrudelPattern,
            probabilityPattern: StrudelPattern? = null,
            probabilityValue: Double = 0.5,
            seedStrategy: (StrudelPatternEvent) -> Any = { it.begin },
        ) = SometimesPattern(
            source = source,
            probabilityPattern = probabilityPattern,
            probabilityValue = probabilityValue,
            seedStrategy = seedStrategy,
            onMatch = DISCARD
        )

        /**
         * Creates a pattern that discards events if the probability check fails (Miss).
         * If the check succeeds (Match), events are kept as is.
         * Used for `undegradeBy` (with inverse probability logic).
         */
        fun discardOnMiss(
            source: StrudelPattern,
            probabilityPattern: StrudelPattern? = null,
            probabilityValue: Double = 0.5,
            seedStrategy: (StrudelPatternEvent) -> Any = { it.begin },
        ) = SometimesPattern(
            source = source,
            probabilityPattern = probabilityPattern,
            probabilityValue = probabilityValue,
            seedStrategy = seedStrategy,
            onMiss = DISCARD
        )
    }

    override val weight: Double get() = source.weight

    override val steps: Rational? get() = source.steps

    override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val sourceEvents = source.queryArcContextual(from, to, ctx)
        if (sourceEvents.isEmpty()) return emptyList()

        val result = createEventList()

        var matchEvents: MutableList<StrudelPatternEvent>? = null
        var missEvents: MutableList<StrudelPatternEvent>? = null

        for (event in sourceEvents) {
            val p = if (probabilityPattern != null) {
                val pEvents = probabilityPattern.queryArcContextual(event.begin, event.begin + EPS, ctx)
                pEvents.firstOrNull()?.data?.value?.asDouble ?: probabilityValue
            } else {
                probabilityValue
            }

            val seed = seedStrategy(event)
            val random = ctx.getSeededRandom(seed, "SometimesPattern")

            if (random.nextDouble() < p) {
                // MATCH
                if (onMatch === DISCARD) {
                    // Discard (do nothing)
                } else if (onMatch != null) {
                    if (matchEvents == null) matchEvents = mutableListOf()
                    matchEvents.add(event)
                } else {
                    // Keep
                    result.add(event)
                }
            } else {
                // MISS
                if (onMiss === DISCARD) {
                    // Discard (do nothing)
                } else if (onMiss != null) {
                    if (missEvents == null) missEvents = mutableListOf()
                    missEvents.add(event)
                } else {
                    // Keep
                    result.add(event)
                }
            }
        }

        if (matchEvents != null && onMatch != null) {
            result.addAll(applyTransform(onMatch, matchEvents, from, to, ctx))
        }

        if (missEvents != null && onMiss != null) {
            result.addAll(applyTransform(onMiss, missEvents, from, to, ctx))
        }

        return result
    }

    private fun applyTransform(
        transform: (StrudelPattern) -> StrudelPattern,
        events: List<StrudelPatternEvent>,
        from: Rational,
        to: Rational,
        ctx: QueryContext,
    ): List<StrudelPatternEvent> {
        val tempPattern = object : StrudelPattern {
            override val weight = 1.0
            override val steps: Rational = Rational.ONE

            override fun estimateCycleDuration(): Rational = Rational.ONE

            override fun queryArcContextual(
                from: Rational,
                to: Rational,
                ctx: QueryContext,
            ): List<StrudelPatternEvent> {
                return events.filter { it.begin < to && it.end > from }
            }
        }

        return transform(tempPattern).queryArcContextual(from, to, ctx)
    }
}
