package io.peekandpoke.klang.strudel

import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull
import io.peekandpoke.klang.strudel.lang.strudelLib
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational
import io.peekandpoke.klang.strudel.pattern.*
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
    val numSteps: Rational?

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

// =============================================================================
// Pattern Bind Primitive (Public API)
// =============================================================================

/**
 * Creates a new pattern by applying [transform] to every event in this pattern.
 *
 * This is the fundamental "Glue" of Strudel (monadic bind / inner join).
 * 1. Queries 'this' pattern (the "outer" or "control" pattern).
 * 2. For each event, generates a new "inner" pattern via [transform].
 * 3. Queries that inner pattern *constrained* to the outer event's time window.
 *
 * By default, metadata (weight, numSteps) is preserved from the outer pattern.
 * This matches the behavior of the underlying BindPattern class.
 *
 * @param transform Function to generate an inner pattern from an outer event.
 *                  Return null to produce silence for that event.
 */
fun StrudelPattern.bindPattern(
    transform: (StrudelPatternEvent) -> StrudelPattern?,
): StrudelPattern = BindPattern(
    outer = this,
    preserveMetadata = true,
    transform = transform,
)

// =============================================================================
// Applicative Lifters
// =============================================================================

/**
 * Lifts a function that accepts a [Double] to work with a Pattern of numbers.
 *
 * Equivalent to `register(name, func)` in JS where arity is 1.
 *
 * PRESERVES metadata (weight, numSteps) from the SOURCE pattern (this@lift).
 * The control pattern drives which values are used, but structure comes from source.
 *
 * @param control The control pattern (e.g. `fast("<2 4>")`)
 * @param transform The function to apply (e.g. `(value, source) -> source.fast(value)`)
 */
fun StrudelPattern.lift(
    control: StrudelPattern,
    transform: (Double, StrudelPattern) -> StrudelPattern,
): StrudelPattern = object : StrudelPattern {
    // Preserve metadata from SOURCE pattern
    override val weight: Double get() = this@lift.weight
    override val numSteps: Rational? get() = this@lift.numSteps
    override fun estimateCycleDuration(): Rational = this@lift.estimateCycleDuration()

    // Control pattern drives the structure via bind
    private val joined = control.bindPattern { event ->
        val value = event.data.value?.asDouble ?: return@bindPattern null
        transform(value, this@lift)
    }

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: StrudelPattern.QueryContext,
    ): List<StrudelPatternEvent> {
        return joined.queryArcContextual(from, to, ctx)
    }
}

/**
 * Lifts a control pattern to modify Voice Data.
 * Used for functions like sound(), bank(), vowel(), begin(), end(), etc.
 *
 * It assumes the [control] pattern ALREADY contains the parsed data in its events
 * (e.g. created via args.toPattern(modifier)).
 *
 * It performs an inner join (bind) and merges the control's data onto the source's data.
 */
fun StrudelPattern.liftData(
    control: StrudelPattern,
): StrudelPattern = object : StrudelPattern {
    // Preserve metadata from the source pattern
    override val weight: Double get() = this@liftData.weight
    override val numSteps: Rational? get() = this@liftData.numSteps
    override fun estimateCycleDuration(): Rational = this@liftData.estimateCycleDuration()

    // Use normalized bindPattern (defaults to preserveMetadata=true, which is redundant here but safe)
    private val joined = control.bindPattern { controlEvent ->

        // For each control event, we map the source pattern
        this@liftData.mapEvents { sourceEvent ->

            // MERGE: control data overrides source data (where not null)
            val mergedData = sourceEvent.data.merge(controlEvent.data)

            sourceEvent.copy(data = mergedData)
        }
    }

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: StrudelPattern.QueryContext,
    ): List<StrudelPatternEvent> {
        return joined.queryArcContextual(from, to, ctx)
    }
}

/**
 * Applies a control pattern to this pattern (Outer Join).
 *
 * Preserves the structure of [this] (source). For each event, samples [control] at the event's
 * start time and applies the [combiner] function to produce the result event.
 *
 * This is used for modifying event values based on another pattern while maintaining the
 * original timing structure (e.g., applying gain, pitch shifts, effects, etc.).
 *
 * @param control The pattern to sample for control values
 * @param from Start of query arc
 * @param to End of query arc
 * @param ctx Query context to pass down the pattern hierarchy
 * @param combiner Function to combine source event with sampled control event (null if no control event found)
 * @return List of events with control applied
 */
fun StrudelPattern.applyControl(
    control: StrudelPattern,
    from: Rational,
    to: Rational,
    ctx: StrudelPattern.QueryContext,
    combiner: (source: StrudelPatternEvent, control: StrudelPatternEvent?) -> StrudelPatternEvent?,
): List<StrudelPatternEvent> {
    val sourceEvents = this.queryArcContextual(from, to, ctx)
    if (sourceEvents.isEmpty()) return emptyList()

    val result = mutableListOf<StrudelPatternEvent>()
    val epsilon = 1e-5.toRational()

    for (event in sourceEvents) {
        // Point-query the control pattern at the event's begin time
        val controlEvents = control.queryArcContextual(event.begin, event.begin + epsilon, ctx)
        val controlEvent = controlEvents.firstOrNull()

        val combined = combiner(event, controlEvent)
        if (combined != null) {
            result.add(combined)
        }
    }
    return result
}

/**
 * Creates a pattern that transforms the event list using the given function.
 *
 * This is a convenience method for creating MapPattern instances. The resulting pattern
 * preserves the original pattern's structure and timing while allowing modification of events.
 *
 * This operation is useful for:
 * - Filtering events based on predicates
 * - Mapping event properties
 * - Sorting or reordering events
 * - Any other list transformation
 *
 * @param transform Function that transforms the list of events
 * @return A new pattern that applies the transformation
 */
fun StrudelPattern.map(
    transform: (List<StrudelPatternEvent>) -> List<StrudelPatternEvent>,
): StrudelPattern {
    return MapPattern(source = this, transform)
}

/**
 * Creates a pattern that maps individual events.
 *
 * This is a convenience extension for transforming each event individually, as opposed
 * to `map()` which transforms the entire event list. Use this when you need to modify
 * event properties, add metadata, or transform event data.
 *
 * @param transform Function that transforms each event
 * @return A new pattern that applies the transformation to each event
 */
fun StrudelPattern.mapEvents(
    transform: (StrudelPatternEvent) -> StrudelPatternEvent,
): StrudelPattern {
    return ReinterpretPattern(source = this) { evt, _ -> transform(evt) }
}

/**
 * Creates a pattern that maps individual events with access to the query context.
 *
 * Similar to `mapEvents()` but provides access to the QueryContext, allowing context-aware
 * transformations (e.g., using random seeds, context keys, etc.).
 *
 * @param transform Function that transforms each event with context access
 * @return A new pattern that applies the transformation to each event
 */
fun StrudelPattern.mapEventsWithContext(
    transform: (StrudelPatternEvent, StrudelPattern.QueryContext) -> StrudelPatternEvent,
): StrudelPattern {
    return ReinterpretPattern(source = this, interpret = transform)
}

/**
 * Creates a pattern with an overridden weight value.
 *
 * Weight is used for proportional time distribution in sequences (e.g., mini-notation @ operator).
 * This is useful when you need to adjust a pattern's relative duration without modifying
 * the pattern itself.
 *
 * @param weight The new weight value
 * @return A new pattern with the specified weight
 */
fun StrudelPattern.withWeight(weight: Double): StrudelPattern {
    return PropertyOverridePattern(source = this, weightOverride = weight)
}

/**
 * Creates a pattern with an overridden steps value.
 *
 * Steps is used for aligning patterns in polymeter. This allows you to override
 * the number of steps per cycle without modifying the pattern itself.
 *
 * @param steps The new steps value
 * @return A new pattern with the specified steps
 */
fun StrudelPattern.withSteps(steps: Rational): StrudelPattern {
    return PropertyOverridePattern(source = this, stepsOverride = steps)
}

/**
 * Stacks this pattern with other patterns, playing all simultaneously.
 *
 * This is a convenience method for creating StackPattern instances. All patterns
 * are queried for the same time range and their events are combined and sorted by time.
 *
 * @param others Additional patterns to stack with this one
 * @return A new pattern that plays all patterns simultaneously
 */
fun StrudelPattern.stack(vararg others: StrudelPattern): StrudelPattern {
    return StackPattern(patterns = listOf(this) + others.toList())
}
