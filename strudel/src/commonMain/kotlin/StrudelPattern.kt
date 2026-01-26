package io.peekandpoke.klang.strudel

import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull
import io.peekandpoke.klang.strudel.lang.early
import io.peekandpoke.klang.strudel.lang.fast
import io.peekandpoke.klang.strudel.lang.late
import io.peekandpoke.klang.strudel.lang.strudelLib
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational
import io.peekandpoke.klang.strudel.pattern.StaticStrudelPattern
import io.peekandpoke.klang.strudel.pattern.createEventList
import kotlin.random.Random

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

            return compileRaw(code)
        }

        fun compileRaw(code: String): StrudelPattern? {
            val klangScriptEngine = klangScript {
                registerLibrary(strudelLib)
            }

            return klangScriptEngine.execute(code).toObjectOrNull<StrudelPattern>()
        }
    }

    /**
     * Query context which passed down the pattern hierarchy when querying events.
     *
     * Patterns can update the context to send information down the chain.
     */
    class QueryContext(data: Map<Key<*>, Any?> = emptyMap()) {
        companion object {
            val randomSeed = Key<Long>("randomSeed")

            val empty = QueryContext()
        }

        /**
         * Context updater.
         *
         * Takes care of only making copies of the context when necessary.
         */
        class Updater internal constructor(private val original: QueryContext) {
            /** The current context */
            private var ctx = original

            /** Runs the given block and returns the current context */
            internal fun runBlock(block: Updater.() -> Unit): QueryContext {
                block()
                return ctx
            }

            /**
             * Makes a copy of the context when the given condition is true and not copy has been made yet.
             */
            private fun cloneOriginalCtxWhen(condition: () -> Boolean) {
                if (ctx == original && condition()) {
                    ctx = ctx.clone()
                }
            }

            /**
             * Sets a new value for the given key.
             * If the new value differs from the current one, a new context is created.
             */
            fun <T> set(key: Key<T>, value: T) {
                cloneOriginalCtxWhen {
                    !ctx.has(key) || ctx.getOrNull(key) != value
                }
                ctx.data[key] = value
            }

            /**
             * Removes the value for the given key.
             */
            fun <T> remove(key: Key<T>) {
                cloneOriginalCtxWhen { ctx.has(key) }
                ctx.data.remove(key)
            }

            /**
             * Sets a new value for the given key if it is not already set.
             */
            fun <T> setIfAbsent(key: Key<T>, value: T) {
                if (!ctx.has(key)) {
                    set(key, value)
                }
            }

            /**
             * Sets a new value for the given key if the given condition is true.
             */
            fun <T> setWhen(key: Key<T>, value: T, condition: (ctx: QueryContext) -> Boolean) {
                if (condition(ctx)) {
                    set(key, value)
                }
            }
        }

        /** Key for storing data in the context. */
        data class Key<T>(val name: String)

        /** internal store */
        private val data = data.toMutableMap()

        /** Checks if the context has a value for the given key. */
        fun <T> has(key: Key<T>): Boolean = data.containsKey(key)

        /** Gets the value for the given key, or null if it is not set. */
        @Suppress("UNCHECKED_CAST")
        fun <T> getOrNull(key: Key<T>): T? = data[key] as? T

        /** Gets the value for the given key, or the given default value if it is not set. */
        fun <T> getOrDefault(key: Key<T>, default: T): T = getOrNull(key) ?: default

        /** Gets the value for the given key, or throws an error if it is not set. */
        fun <T> get(key: Key<T>): T = getOrNull(key) ?: error("Key not found: $key")

        /** Updates the context with the given block. */
        fun update(block: Updater.() -> Unit): QueryContext = Updater(this).runBlock(block)

        /** Gets the random generator for this context. */
        fun getRandom(): Random = getOrNull(randomSeed)?.let { Random(it) } ?: Random.Default

        /** Gets a new random generator seeded with the context's random seed and the given seed. */
        fun getSeededRandom(seed: Any, vararg seeds: Any): Random {
            val s = getRandom().nextInt() + seeds.fold(seed.hashCode()) { acc, it -> acc + it.hashCode() }

            return Random((s * 2862933555777941757L) + 3037000493L)
        }

        /** Makes a copy of the context. */
        private fun clone(): QueryContext = QueryContext(data)
    }

    /**
     * A helper interface for patterns with a fixed weight of 1.0.
     */
    interface FixedWeight : StrudelPattern {
        override val weight: Double get() = 1.0
    }

    /**
     * Weight for proportional time distribution in sequences.
     * Used by the @ operator in mini-notation (e.g., "bd@2" has weight 2.0).
     */
    val weight: Double

    /**
     * The number of steps per cycle for this pattern, if defined.
     * Used for aligning patterns in polymeter.
     */
    val steps: Rational?

    /**
     * Estimates the duration of a cycle for this pattern.
     * Used for alignment in stacking operations.
     * Defaults to 1.0 (Rational.ONE).
     */
    fun estimateCycleDuration(): Rational = Rational.ONE

    /**
     * Queries events from [from] and [to] cycles with an empty [QueryContext].
     */
    fun queryArc(from: Rational, to: Rational): List<StrudelPatternEvent> =
        queryArcContextual(from, to, QueryContext.empty)

    /**
     * Queries events from [from] and [to] cycles with an empty [QueryContext].
     */
    fun queryArc(from: Double, to: Double): List<StrudelPatternEvent> =
        queryArc(from.toRational(), to.toRational())

    /**
     * Queries events from [from] and [to] cycles with the given [ctx].
     */
    fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent>

    /**
     * Queries events from [from] and [to] cycles with the given [ctx].
     */
    fun queryArcContextual(from: Double, to: Double, ctx: QueryContext): List<StrudelPatternEvent> =
        queryArcContextual(from = from.toRational(), to = to.toRational(), ctx = ctx)
}

/**
 * Creates a static pattern, that can be stored and used for playback with
 * any life strudel event generator.
 *
 * Acts like recording the arc [from] - [to] for later playback.
 */
fun StrudelPattern.makeStatic(from: Rational, to: Rational): StaticStrudelPattern =
    StaticStrudelPattern(events = queryArc(from, to))

/**
 * Creates a static pattern, that can be stored and used for playback with
 * any life strudel event generator.
 *
 * Acts like recording the arc [from] - [to] for later playback.
 */
fun StrudelPattern.makeStatic(from: Double, to: Double): StaticStrudelPattern =
    makeStatic(from.toRational(), to.toRational())

/**
 * Flattens a pattern of patterns into a pattern, where the "whole" (duration) comes from the inner (picked) patterns.
 * This is the standard join operation for pattern-of-patterns.
 *
 * When you have a pattern that returns patterns, innerJoin uses the timing from the inner (returned) pattern.
 * The outer pattern acts as a selector that determines WHICH pattern to play, while the inner pattern
 * determines WHEN events happen.
 *
 * Used by: pick, pickmod, pickReset, pickmodReset
 */
fun StrudelPattern.innerJoin(): StrudelPattern {
    val outerPattern = this

    return object : StrudelPattern {
        override val weight: Double get() = outerPattern.weight
        override val steps: Rational? get() = outerPattern.steps
        override fun estimateCycleDuration(): Rational = outerPattern.estimateCycleDuration()

        override fun queryArcContextual(
            from: Rational,
            to: Rational,
            ctx: StrudelPattern.QueryContext,
        ): List<StrudelPatternEvent> {
            // Get events from outer pattern (which should have StrudelPattern values)
            val outerEvents = outerPattern.queryArcContextual(from, to, ctx)
            val result = createEventList()

            for (outerEvent in outerEvents) {
                // Extract the inner pattern from the outer event's value
                val innerPattern = when (val data = outerEvent.data) {
                    is StrudelPattern -> data
                    else -> continue // Skip if not a pattern
                }

                // Query the inner pattern for the timespan of the outer event
                val innerEvents = innerPattern.queryArcContextual(outerEvent.begin, outerEvent.end, ctx)

                // Combine events: use inner event's timing (whole), outer event's context
                for (innerEvent in innerEvents) {
                    result.add(
                        innerEvent.copy(
                            // Keep inner event's timing (begin, end, dur remain from inner)
                            data = innerEvent.data.copy(
                                // Merge contexts if needed
                            )
                        )
                    )
                }
            }

            return result
        }
    }
}

/**
 * Flattens a pattern of patterns into a pattern, where the "whole" (duration) comes from the outer (selecting) pattern.
 *
 * When you have a pattern that returns patterns, outerJoin uses the timing from the outer (selecting) pattern.
 * The inner pattern provides the VALUES, but the outer pattern determines WHEN events happen.
 *
 * Used by: pickOut, pickmodOut
 */
fun StrudelPattern.outerJoin(): StrudelPattern {
    val outerPattern = this

    return object : StrudelPattern {
        override val weight: Double get() = outerPattern.weight
        override val steps: Rational? get() = outerPattern.steps
        override fun estimateCycleDuration(): Rational = outerPattern.estimateCycleDuration()

        override fun queryArcContextual(
            from: Rational,
            to: Rational,
            ctx: StrudelPattern.QueryContext,
        ): List<StrudelPatternEvent> {
            val outerEvents = outerPattern.queryArcContextual(from, to, ctx)
            val result = createEventList()

            for (outerEvent in outerEvents) {
                val innerPattern = when (val data = outerEvent.data) {
                    is StrudelPattern -> data
                    else -> continue
                }

                // Query the inner pattern
                val innerEvents = innerPattern.queryArcContextual(outerEvent.begin, outerEvent.end, ctx)

                // Use OUTER timing, inner values
                for (innerEvent in innerEvents) {
                    // Calculate intersection of outer and inner timespans
                    val newBegin = maxOf(outerEvent.begin, innerEvent.begin)
                    val newEnd = minOf(outerEvent.end, innerEvent.end)

                    if (newEnd > newBegin) {
                        result.add(
                            innerEvent.copy(
                                begin = newBegin,
                                end = newEnd,
                                dur = newEnd - newBegin
                            )
                        )
                    }
                }
            }

            return result
        }
    }
}

/**
 * Flattens a pattern of patterns, aligning inner pattern cycles to outer events.
 *
 * @param restart If true (restartJoin), align to global time (outer event's begin time).
 *                If false (resetJoin), align to cycle position (outer event's begin % 1).
 *
 * - restart=true (restartJoin): Each selection restarts the pattern from cycle 0 at the current global time
 * - restart=false (resetJoin): Each selection resets the pattern to the current position within the cycle
 *
 * Used by: pickRestart, pickmodRestart (restart=true), pickReset, pickmodReset (restart=false)
 */
fun StrudelPattern.resetJoin(restart: Boolean = false): StrudelPattern {
    val outerPattern = this

    return object : StrudelPattern {
        override val weight: Double get() = outerPattern.weight
        override val steps: Rational? get() = outerPattern.steps
        override fun estimateCycleDuration(): Rational = outerPattern.estimateCycleDuration()

        override fun queryArcContextual(
            from: Rational,
            to: Rational,
            ctx: StrudelPattern.QueryContext,
        ): List<StrudelPatternEvent> {
            val outerEvents = outerPattern.queryArcContextual(from, to, ctx)
            val result = createEventList()

            for (outerEvent in outerEvents) {
                val innerPattern = when (val data = outerEvent.data) {
                    is StrudelPattern -> data
                    else -> continue
                }

                // Calculate the time offset for alignment
                val offset = if (restart) {
                    // Restart: align to global time (pattern starts at outerEvent.begin)
                    outerEvent.begin
                } else {
                    // Reset: align to cycle position (pattern cycles match outer event's cycle position)
                    // This keeps the pattern synchronized with cycle boundaries
                    outerEvent.begin - outerEvent.begin.floor()
                }

                // Shift the inner pattern by the offset
                val alignedPattern = innerPattern.late(offset)

                // Query the aligned pattern
                val innerEvents = alignedPattern.queryArcContextual(outerEvent.begin, outerEvent.end, ctx)

                for (innerEvent in innerEvents) {
                    // Calculate intersection
                    val newBegin = maxOf(outerEvent.begin, innerEvent.begin)
                    val newEnd = minOf(outerEvent.end, innerEvent.end)

                    if (newEnd > newBegin) {
                        result.add(
                            innerEvent.copy(
                                begin = newBegin,
                                end = newEnd,
                                dur = newEnd - newBegin
                            )
                        )
                    }
                }
            }

            return result
        }
    }
}

/**
 * Convenience method for resetJoin(true).
 * Restarts inner patterns from their beginning at the global time of each outer event.
 */
fun StrudelPattern.restartJoin(): StrudelPattern = resetJoin(true)

/**
 * Flattens a pattern of patterns, compressing entire inner pattern cycles into outer event durations.
 *
 * This is the "squeeze" or "inhabit" join - it takes the entire first cycle (0 to 1) of the inner pattern
 * and compresses it to fit within the duration of each outer event. This is fundamentally different from
 * innerJoin which preserves the temporal resolution of the inner pattern.
 *
 * Think of it like this:
 * - innerJoin: "Play the pattern at its normal speed within this window"
 * - squeezeJoin: "Squeeze the entire pattern cycle to fit this window"
 *
 * Used by: inhabit, pickSqueeze, inhabitmod, pickmodSqueeze, squeeze
 */
fun StrudelPattern.squeezeJoin(): StrudelPattern {
    val outerPattern = this

    return object : StrudelPattern {
        override val weight: Double get() = outerPattern.weight
        override val steps: Rational? get() = outerPattern.steps
        override fun estimateCycleDuration(): Rational = outerPattern.estimateCycleDuration()

        override fun queryArcContextual(
            from: Rational,
            to: Rational,
            ctx: StrudelPattern.QueryContext,
        ): List<StrudelPatternEvent> {
            val outerEvents = outerPattern.queryArcContextual(from, to, ctx)
            val result = createEventList()

            for (outerEvent in outerEvents) {
                val innerPattern = when (val data = outerEvent.data) {
                    is StrudelPattern -> data
                    else -> continue
                }

                // Compress the inner pattern's first cycle to fit the outer event's timespan
                // This is equivalent to: inner._focusSpan(outerEvent.wholeOrPart())
                // Which does: inner.early(begin.floor()).fast(1/(end-begin)).late(begin)

                val span = outerEvent.end - outerEvent.begin
                if (span <= Rational.ZERO) continue

                // Apply the focus transformation to compress cycle 0-1 into the outer event's span
                val compressed = innerPattern
                    .early(outerEvent.begin.floor()) // Align to cycle start
                    .fast(Rational.ONE / span)        // Speed up to fit the span
                    .late(outerEvent.begin)           // Shift to outer event's position

                // Query the compressed pattern
                val innerEvents = compressed.queryArcContextual(outerEvent.begin, outerEvent.end, ctx)

                for (innerEvent in innerEvents) {
                    // Calculate intersection
                    val newBegin = maxOf(outerEvent.begin, innerEvent.begin)
                    val newEnd = minOf(outerEvent.end, innerEvent.end)

                    if (newEnd > newBegin) {
                        result.add(
                            innerEvent.copy(
                                begin = newBegin,
                                end = newEnd,
                                dur = newEnd - newBegin
                            )
                        )
                    }
                }
            }

            return result
        }
    }
}
