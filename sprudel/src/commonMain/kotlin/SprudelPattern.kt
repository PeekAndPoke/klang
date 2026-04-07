@file:Suppress("FunctionName")

package io.peekandpoke.klang.sprudel

import io.peekandpoke.klang.audio_bridge.KlangPattern
import io.peekandpoke.klang.audio_bridge.KlangPatternEvent
import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.common.math.Rational.Companion.toRational
import io.peekandpoke.klang.script.KlangScriptEngine
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg
import io.peekandpoke.klang.sprudel.lang.VoiceMergerFn
import io.peekandpoke.klang.sprudel.lang.VoiceModifierFn
import io.peekandpoke.klang.sprudel.lang.applyControl
import io.peekandpoke.klang.sprudel.lang.silence
import io.peekandpoke.klang.sprudel.lang.sprudelLib
import io.peekandpoke.klang.sprudel.lang.toPattern
import io.peekandpoke.klang.sprudel.pattern.BindPattern
import io.peekandpoke.klang.sprudel.pattern.ContextRangeMapPattern
import io.peekandpoke.klang.sprudel.pattern.FastGapPattern
import io.peekandpoke.klang.sprudel.pattern.MapPattern
import io.peekandpoke.klang.sprudel.pattern.PropertyOverridePattern
import io.peekandpoke.klang.sprudel.pattern.ReinterpretPattern
import io.peekandpoke.klang.sprudel.pattern.ReinterpretPattern.Companion.reinterpretVoice
import io.peekandpoke.klang.sprudel.pattern.StackPattern
import io.peekandpoke.klang.sprudel.pattern.StaticSprudelPattern
import io.peekandpoke.ultra.datetime.Kronos
import kotlin.jvm.JvmName
import kotlin.random.Random

/**
 * Sprudel pattern.
 *
 * Implements [KlangPattern] to participate in cross-pattern composition.
 * The [queryEvents] default implementation bridges from sprudel's Rational-based [queryArc] API
 * to the generic cycle-based API, handling onset filtering and sorting.
 */
interface SprudelPattern : KlangPattern {
    companion object {
        /** Small epsilon value for point queries (sampling control patterns at a specific time) */
        val QUERY_EPSILON = 1e-7.toRational()

        /**
         * Compile Sprudel code using a raw KlangScript engine (no pre-imported libraries).
         * Used by tests and simple compilation scenarios.
         */
        fun compileRaw(code: String): SprudelPattern? {
            val engine = klangScript {
                registerLibrary(sprudelLib)
            }

            val result = engine.execute(code + "\n")
            return result.toObjectOrNull<SprudelPattern>()
        }

        /**
         * Compile Sprudel code with pre-imported libraries, maintaining accurate source locations.
         *
         * Pre-executes import statements in the engine environment so user code
         * starts at line 1 — essential for code highlighting during playback.
         *
         * @param engine A pre-configured KlangScriptEngine (use [Player.createEngine] in the app)
         * @param code The Sprudel pattern code to compile (without import statements)
         * @return The compiled pattern, or null if compilation fails
         */
        fun compile(engine: KlangScriptEngine, code: String): SprudelPattern? {
            // Pre-execute import statements to populate the environment
            engine.execute("""import * from "stdlib"""")
            engine.execute("""import * from "sprudel"""")

            val result = engine.execute(code + "\n")
            return result.toObjectOrNull<SprudelPattern>()
        }

        /**
         * Compile Sprudel code without a pre-existing engine.
         * Creates a default engine internally. Used by tests.
         */
        fun compile(code: String): SprudelPattern? {
            val engine = klangScript {
                registerLibrary(sprudelLib)
            }
            return compile(engine, code)
        }
    }

    /**
     * Query context which passed down the pattern hierarchy when querying events.
     *
     * Patterns can update the context to send information down the chain.
     */
    class QueryContext(data: Map<Key<*>, Any?> = emptyMap()) {
        companion object {
            val randomSeedKey = Key<Int>("randomSeed")
            val cpsKey = Key<Double>("cps")
            val kronosKey = Key<Kronos>("kronos")

            val empty = QueryContext()

            /** Builder */
            operator fun invoke(block: Updater.() -> Unit): QueryContext = QueryContext().update(block)
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
        fun getRandom(): Random {
            val seed = getOrNull(randomSeedKey) ?: 0
            return Random(seed)
        }

        /** Gets a new random generator seeded with the context's random seed and the given seed. */
        fun getSeededRandom(seed: Any, vararg seeds: Any): Random {
            val baseSeed = getOrNull(randomSeedKey) ?: 0
            val s = baseSeed.hashCode() + seeds.fold(seed.hashCode()) { acc, it -> acc * it.hashCode() }

            return Random(mixSeed(s))
        }

        /** MurmurHash3 integer finalizer — avalanches an Int seed so nearby values scatter. */
        private fun mixSeed(value: Int): Int {
            var h = value
            h = h xor (h ushr 16)
            h *= -0x7a143595  // 0x85ebca6b
            h = h xor (h ushr 13)
            h *= -0x3d4d51cb  // 0xc2b2ae35
            h = h xor (h ushr 16)
            return h
        }

        /** Gets the cycles per second (cps) for this context. */
        fun getCps() = getOrDefault(cpsKey, 0.5)

        /** Gets the time source (Kronos) for this context, defaults to system UTC time. */
        fun getKronos() = getOrNull(kronosKey) ?: Kronos.systemUtc

        /** Makes a copy of the context. */
        private fun clone(): QueryContext = QueryContext(data)
    }

    /**
     * A helper interface for patterns with a fixed weight of 1.0.
     */
    interface FixedWeight : SprudelPattern {
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
    fun queryArc(from: Rational, to: Rational): List<SprudelPatternEvent> =
        queryArcContextual(from = from, to = to, ctx = QueryContext.empty)

    /**
     * Queries events from [from] and [to] cycles with an empty [QueryContext].
     */
    fun queryArc(from: Double, to: Double): List<SprudelPatternEvent> =
        queryArc(from = from.toRational(), to = to.toRational())

    /**
     * Queries events from [from] and [to] cycles with the given [ctx].
     */
    fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<SprudelPatternEvent>

    /**
     * Queries events from [from] and [to] cycles with the given [ctx].
     */
    fun queryArcContextual(from: Double, to: Double, ctx: QueryContext): List<SprudelPatternEvent> =
        queryArcContextual(from = from.toRational(), to = to.toRational(), ctx = ctx)

    /**
     * KlangPattern bridge: converts sprudel's Rational-based queryArc to the generic cycle-based API.
     * Handles onset filtering and sorting.
     */
    override fun queryEvents(fromCycles: Double, toCycles: Double, cps: Double): List<KlangPatternEvent> {
        val ctx = QueryContext { set(QueryContext.cpsKey, cps) }
        return queryArcContextual(Rational(fromCycles), Rational(toCycles), ctx)
            .filter { it.isOnset }
            .sortedBy { it.part.begin }
    }
}

/**
 * Samples the pattern at a specific point in time.
 *
 * This performs a point query using a small epsilon window and returns the first event found,
 * or null if no event exists at that time. Commonly used for sampling continuous control patterns
 * (like sine, saw) at discrete event times.
 *
 * @param time The time to sample at
 * @param ctx Query context
 * @return The first event at the sample time, or null if none exists
 */
fun SprudelPattern.sampleAt(time: Rational, ctx: QueryContext): SprudelPatternEvent? =
    queryArcContextual(from = time, to = time + SprudelPattern.QUERY_EPSILON, ctx = ctx).firstOrNull()

/**
 * Creates a static pattern, that can be stored and used for playback with
 * any life sprudel event generator.
 *
 * Acts like recording the arc [from] - [to] for later playback.
 */
fun SprudelPattern.makeStatic(from: Rational, to: Rational): StaticSprudelPattern =
    StaticSprudelPattern(events = queryArc(from = from, to = to))

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
fun SprudelPattern.map(
    transform: (List<SprudelPatternEvent>) -> List<SprudelPatternEvent>,
): SprudelPattern {
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
fun SprudelPattern.mapEvents(
    transform: (SprudelPatternEvent) -> SprudelPatternEvent,
): SprudelPattern {
    return ReinterpretPattern(source = this) { evt, _ -> transform(evt) }
}

/**
 * Filters events based on their value.
 * JavaScript: filterValues(test) - keeps only events where test(value) returns true
 *
 * @param test Predicate function that tests event values
 * @return Pattern with only events that pass the test
 */
fun SprudelPattern.filterValues(test: (SprudelVoiceValue?) -> Boolean): SprudelPattern {
    val source = this

    return object : SprudelPattern {
        override val weight: Double get() = source.weight
        override val numSteps: Rational? get() = source.numSteps

        override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<SprudelPatternEvent> {
            return source.queryArcContextual(from, to, ctx).filter { event ->
                test(event.data.value)
            }
        }

        override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()
    }
}

/**
 * Applicative `appLeft` (<*) operator.
 * Keeps values from the left pattern, but only where the right pattern has events.
 * Structure is taken from the left pattern.
 *
 * JavaScript: pat_func.appLeft(pat_val)
 * - Queries left pattern for events
 * - For each left event, queries right pattern at that time
 * - Keeps left event's value and whole, but intersects the part with right events
 *
 * @param patVal The pattern providing structure (right side)
 * @return Pattern with left values but filtered by right structure
 */
fun SprudelPattern.appLeft(patVal: SprudelPattern): SprudelPattern {
    val patFunc = this

    return object : SprudelPattern {
        override val weight: Double get() = patFunc.weight
        override val numSteps: Rational? get() = patFunc.numSteps

        override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<SprudelPatternEvent> {
            val result = mutableListOf<SprudelPatternEvent>()

            for (hapFunc in patFunc.queryArcContextual(from, to, ctx)) {
                // Query right pattern at the left event's timespan
                val querySpan = hapFunc.whole
                val hapVals = patVal.queryArcContextual(querySpan.begin, querySpan.end, ctx)

                for (hapVal in hapVals) {
                    // Intersect the parts
                    val newPart = hapFunc.part.clipTo(hapVal.part)
                    if (newPart != null) {
                        // Keep left's whole and value, but intersect part
                        result.add(
                            hapFunc.copy(
                                part = newPart
                                // whole is preserved from hapFunc
                            )
                        )
                    }
                }
            }

            return result
        }

        override fun estimateCycleDuration(): Rational = patFunc.estimateCycleDuration()
    }
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
fun SprudelPattern.withWeight(weight: Double): SprudelPattern {
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
fun SprudelPattern.withSteps(steps: Rational?): SprudelPattern {
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
fun SprudelPattern.stack(vararg others: SprudelPattern): SprudelPattern {
    return StackPattern(patterns = listOf(this) + others.toList())
}

/**
 * Creates a static pattern, that can be stored and used for playback with
 * any life sprudel event generator.
 *
 * Acts like recording the arc [from] - [to] for later playback.
 */
fun SprudelPattern.makeStatic(from: Double, to: Double): StaticSprudelPattern =
    makeStatic(from.toRational(), to.toRational())

/**
 * Maps the rango context, useful for mapping between bipolar and unipolar ranges
 */
fun SprudelPattern._mapRangeContext(
    transformMin: (Double) -> Double,
    transformMax: (Double) -> Double,
): SprudelPattern {
    return ContextRangeMapPattern(
        source = this,
        transformMin = transformMin,
        transformMax = transformMax,
    )
}

/**
 * Inner join with one argument pattern.
 * The transform receives the source pattern and the value from arg1.
 */
fun SprudelPattern._innerJoin(
    arg1: SprudelPattern,
    transform: (source: SprudelPattern, val1: SprudelVoiceValue?) -> SprudelPattern,
): SprudelPattern {
    val sourcePattern = this
    return arg1._bind { event ->
        transform(sourcePattern, event.data.value)
            .mapEvents { it.prependLocations(event.sourceLocations) }
    }
}

/**
 * Inner join with one argument pattern.
 */
@JvmName("_innerJoin_args1")
fun <T> SprudelPattern._innerJoin(
    args: List<SprudelDslArg<T>>,
    transform: (source: SprudelPattern, v1: SprudelVoiceValue?) -> SprudelPattern,
): SprudelPattern {
    return this._innerJoin(
        arg1 = args.getOrNull(0),
        transform = transform,
    )
}

/**
 * Inner join with one argument pattern.
 */
@JvmName("_innerJoin_args1")
fun <T> SprudelPattern._innerJoin(
    arg1: SprudelDslArg<T>?,
    transform: (source: SprudelPattern, v1: SprudelVoiceValue?) -> SprudelPattern,
): SprudelPattern {
    return this._innerJoin(
        arg1 = arg1?.toPattern() ?: return this,
        transform = transform,
    )
}

/**
 * Inner join with two argument patterns.
 * The transform receives the source pattern and the values from arg1 and arg2.
 */
fun SprudelPattern._innerJoin(
    arg1: SprudelPattern,
    arg2: SprudelPattern,
    transform: (source: SprudelPattern, v1: SprudelVoiceValue?, v2: SprudelVoiceValue?) -> SprudelPattern,
): SprudelPattern {
    val sourcePattern = this
    return arg1._bind { event1 ->
        arg2._bind { event2 ->
            transform(sourcePattern, event1.data.value, event2.data.value)
        }
    }
}

/**
 * Inner join with one argument pattern.
 */
@JvmName("_innerJoin_args1")
fun <T> SprudelPattern._innerJoin(
    args: List<SprudelDslArg<T>>,
    transform: (source: SprudelPattern, v1: SprudelVoiceValue?, v2: SprudelVoiceValue?) -> SprudelPattern,
): SprudelPattern {
    return this._innerJoin(
        arg1 = args.getOrNull(0)?.toPattern() ?: return this,
        arg2 = args.getOrNull(1)?.toPattern() ?: return this,
        transform = transform,
    )
}

/**
 * Inner join with three argument patterns.
 * The transform receives the source pattern and the values from arg1, arg2, and arg3.
 */
fun SprudelPattern._innerJoin(
    arg1: SprudelPattern,
    arg2: SprudelPattern,
    arg3: SprudelPattern,
    transform: (
        source: SprudelPattern, val1: SprudelVoiceValue?, val2: SprudelVoiceValue?, val3: SprudelVoiceValue?,
    ) -> SprudelPattern,
): SprudelPattern {
    val sourcePattern = this
    return arg1._bind { event1 ->
        arg2._bind { event2 ->
            arg3._bind { event3 ->
                transform(sourcePattern, event1.data.value, event2.data.value, event3.data.value)
            }
        }
    }
}

/**
 * Inner join with one argument pattern.
 */
@JvmName("_innerJoin_args1")
fun <T> SprudelPattern._innerJoin(
    args: List<SprudelDslArg<T>>,
    transform: (source: SprudelPattern, v1: SprudelVoiceValue?, v2: SprudelVoiceValue?, v3: SprudelVoiceValue?) -> SprudelPattern,
): SprudelPattern {
    return this._innerJoin(
        arg1 = args.getOrNull(0)?.toPattern() ?: return this,
        arg2 = args.getOrNull(1)?.toPattern() ?: return this,
        arg3 = args.getOrNull(2)?.toPattern() ?: return this,
        transform = transform,
    )
}

/**
 * Inner join that passes argument patterns' values and the source pattern to a transform function.
 * This matches JavaScript Sprudel's register() behavior with innerJoin.
 *
 * The structure is driven by the argument patterns - for each combination of values
 * from the argument patterns, the transform function is called with those values
 * and the source pattern.
 *
 * @param argPatterns List of patterns whose values will be extracted
 * @param transform Function that receives (argument values, source pattern) and returns a pattern
 */
fun SprudelPattern._innerJoin(
    argPatterns: List<SprudelPattern>,
    transform: (source: SprudelPattern, values: List<SprudelVoiceValue?>) -> SprudelPattern,
): SprudelPattern {
    if (argPatterns.isEmpty()) {
        return transform(this, emptyList())
    }

    val sourcePattern = this

    // Recursively bind through all argument patterns to collect their values
    fun bindAll(patterns: List<SprudelPattern>, accumulatedValues: List<SprudelVoiceValue?>): SprudelPattern {
        if (patterns.isEmpty()) {
            // All argument values collected, call transform with values and source pattern
            return transform(sourcePattern, accumulatedValues)
        }

        val currentPattern = patterns.first()
        val remainingPatterns = patterns.drop(1)

        // Bind the current pattern to extract its value
        return currentPattern._bind { event ->
            val value = event.data.value
            bindAll(remainingPatterns, accumulatedValues + value)
        }
    }

    return bindAll(argPatterns, emptyList())
}

/**
 * Creates a new pattern by applying [transform] to every event in this pattern.
 *
 * This is the fundamental "Glue" of Sprudel (monadic bind / inner join).
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
fun SprudelPattern._bind(
    transform: (SprudelPatternEvent) -> SprudelPattern?,
): SprudelPattern = BindPattern(
    outer = this,
    preserveMetadata = true,
    transform = transform,
)

/**
 * Maps a function over the values in this pattern, producing a pattern-of-patterns.
 * Equivalent to `fmap` in JS Strudel.
 *
 * The function receives a value and returns a [SprudelPattern].
 * The result is a pattern where each event's value is itself a pattern.
 */
fun SprudelPattern._fmap(
    transform: (SprudelVoiceValue?) -> SprudelPattern,
): SprudelPattern {
    return this.mapEvents { event ->
        event.copy(
            data = event.data.copy(
                // Store the transformed pattern as the value
                // This creates a pattern-of-patterns
                value = SprudelVoiceValue.Pattern(transform(event.data.value))
            )
        )
    }
}

/**
 * Flattens a pattern-of-patterns by squeezing each inner pattern into its outer event's timespan.
 * Equivalent to `squeezeJoin()` in JS Strudel.
 *
 * Expects a pattern where event values are SprudelVoiceValue.Pattern.
 */
fun SprudelPattern._squeezeJoin(): SprudelPattern = object : SprudelPattern {
    override val weight: Double get() = this@_squeezeJoin.weight
    override val numSteps: Rational? get() = this@_squeezeJoin.numSteps
    override fun estimateCycleDuration(): Rational = this@_squeezeJoin.estimateCycleDuration()

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<SprudelPatternEvent> {
        val outerEvents = this@_squeezeJoin.queryArcContextual(from, to, ctx)
        val results = mutableListOf<SprudelPatternEvent>()

        for (outerEvent in outerEvents) {
            // Get the inner pattern from the event's value
            val innerPattern = (outerEvent.data.value as? SprudelVoiceValue.Pattern)?.pattern
                ?: continue

            // Focus the inner pattern [0,1] onto the outer event's whole (or part if no whole)
            val targetSpan = outerEvent.whole
            val focusedPattern = innerPattern._focusSpan(targetSpan)

            // Query the focused pattern within the outer event's part
            val innerEvents = focusedPattern.queryArcContextual(
                outerEvent.part.begin,
                outerEvent.part.end,
                ctx
            )

            // Merge outer event's data (sound, note, etc.) with inner event's data (timing)
            val mergedEvents = innerEvents.map { innerEvent ->
                innerEvent.copy(
                    data = outerEvent.data.copy(value = innerEvent.data.value),
                )
            }

            results.addAll(mergedEvents)
        }

        return results
    }
}

/**
 * Flattens a pattern-of-patterns by querying each inner pattern directly without time transformation.
 * Equivalent to `innerJoin()` in JS Strudel.
 *
 * Unlike _squeezeJoin which squeezes the inner pattern's [0,1] cycle into the outer event's timespan,
 * _innerJoin queries the inner pattern for the outer event's timespan directly without transformation.
 * The inner event's whole is used (not the outer's).
 *
 * Expects a pattern where event values are SprudelVoiceValue.Pattern.
 */
fun SprudelPattern._innerJoin(): SprudelPattern = object : SprudelPattern {
    override val weight: Double get() = this@_innerJoin.weight
    override val numSteps: Rational? get() = this@_innerJoin.numSteps
    override fun estimateCycleDuration(): Rational = this@_innerJoin.estimateCycleDuration()

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<SprudelPatternEvent> {
        val outerEvents = this@_innerJoin.queryArcContextual(from, to, ctx)
        val results = mutableListOf<SprudelPatternEvent>()

        for (outerEvent in outerEvents) {
            // Get the inner pattern from the event's value
            val innerPattern = (outerEvent.data.value as? SprudelVoiceValue.Pattern)?.pattern
                ?: continue

            // Query the inner pattern for the outer event's part timespan (no time transformation)
            val innerEvents: List<SprudelPatternEvent> =
                innerPattern.queryArcContextual(outerEvent.part.begin, outerEvent.part.end, ctx)

            // Merge outer event's data (sound, note, etc.) with inner event's data (timing + value)
            // Use inner event's whole (not outer's)
            val mergedEvents = innerEvents.map { innerEvent ->
                innerEvent.copy(
                    data = outerEvent.data.copy(value = innerEvent.data.value),
                )
            }

            results.addAll(mergedEvents)
        }

        return results
    }
}

/**
 * Helper to focus a pattern's [0,1] cycle onto a specific timespan.
 */
private fun SprudelPattern._focusSpan(span: TimeSpan): SprudelPattern {
    val duration = span.duration
    if (duration == Rational.ZERO) return silence

    return this._withQueryTime { t -> (t - span.begin) / duration }
        .mapEvents { event ->
            event.copy(
                part = event.part.scale(duration).shift(span.begin),
                whole = event.whole.scale(duration).shift(span.begin),
            )
        }
}

/**
 * Binds an inner pattern to each event of the outer pattern, "squeezing" the inner pattern
 * (which usually operates in 0..1 time) to fit exactly within the outer event's duration.
 *
 * Equivalent to `pat.fmap(transform).squeezeJoin()` in JS Strudel.
 */
fun SprudelPattern._bindSqueeze(
    transform: (SprudelPatternEvent) -> SprudelPattern?,
): SprudelPattern = this._bind { outerEvent ->
    val innerPattern = transform(outerEvent) ?: return@_bind null

    val b = outerEvent.part.begin
    val d = outerEvent.part.duration

    if (d == Rational.ZERO) return@_bind null

    // Map query time: t -> (t - b) / d  (map global [b, b+d] to local [0, 1])
    // Map event time: e -> b + e * d    (map local [0, 1] back to global [b, b+d])
    innerPattern._withQueryTime { t -> (t - b) / d }.mapEvents { e ->
        val scaledPart = e.part.scale(d).shift(b)
        val scaledWhole = e.whole.scale(d).shift(b)
        e.copy(part = scaledPart, whole = scaledWhole)
    }
}

/**
 * Binds an inner pattern, squeezing it into the outer event's duration (step).
 * Alias for _bindSqueeze. Matches JS stepJoin().
 */
fun SprudelPattern._stepJoin(
    transform: (SprudelPatternEvent) -> SprudelPattern?,
): SprudelPattern = _bindSqueeze(transform)

/**
 * Binds an inner pattern, adjusting its speed based on the ratio of outer steps to inner steps.
 * Equivalent to `pat.fmap(transform).polyJoin()` in JS Strudel.
 */
fun SprudelPattern._bindPoly(
    transform: (SprudelPatternEvent) -> SprudelPattern?,
): SprudelPattern {
    val outerSteps = this.numSteps ?: return this._bind(transform)

    return this._bind { outerEvent ->
        val innerPattern = transform(outerEvent) ?: return@_bind null
        val innerSteps = innerPattern.numSteps

        if (innerSteps != null && innerSteps != Rational.ZERO) {
            val factor = outerSteps / innerSteps
            // p.extend(factor) -> p.fast(factor) (steps are preserved by _bind)

            // Map query time: t -> t * factor
            // Map event time: e -> e / factor
            innerPattern._withQueryTime { t -> t * factor }.mapEvents { e ->
                val scaledPart = e.part.scale(Rational.ONE / factor)
                val scaledWhole = e.whole.scale(Rational.ONE / factor)
                e.copy(part = scaledPart, whole = scaledWhole)
            }
        } else {
            innerPattern
        }
    }
}

/**
 * Binds an inner pattern, resetting it to the start of the cycle for each outer event.
 * Equivalent to `pat.fmap(transform).resetJoin()` in JS Strudel.
 */
fun SprudelPattern._bindReset(
    transform: (SprudelPatternEvent) -> SprudelPattern?,
): SprudelPattern = this._bind { outerEvent ->
    val innerPattern = transform(outerEvent) ?: return@_bind null

    // Align inner pattern cycle start to outer event start
    // NOTE: Using whole.begin (onset time) for cycle position, not part.begin (visible start).
    // This matches musical semantics where the event's cycle position is determined by its onset.
    // If JS implementation differs, this may need adjustment. See accessor-replacement notes.
    val eventBegin = outerEvent.whole.begin
    val shift = eventBegin % Rational.ONE

    innerPattern._withQueryTime { t -> t - shift }.mapEvents { e ->
        val shiftedPart = e.part.shift(shift)
        val shiftedWhole = e.whole.shift(shift)
        e.copy(part = shiftedPart, whole = shiftedWhole)
    }
}

/**
 * Binds an inner pattern, restarting it from 0 for each outer event.
 * Equivalent to `pat.fmap(transform).restartJoin()` in JS Strudel.
 */
fun SprudelPattern._bindRestart(
    transform: (SprudelPatternEvent) -> SprudelPattern?,
): SprudelPattern = this._bind { outerEvent ->
    val innerPattern = transform(outerEvent) ?: return@_bind null

    // Align inner pattern start (0) to outer event start
    // NOTE: Using whole.begin (onset time) for alignment, not part.begin (visible start).
    // This matches musical semantics where the event conceptually starts at its onset.
    // If JS implementation differs, this may need adjustment. See accessor-replacement notes.
    val eventBegin = outerEvent.whole.begin

    innerPattern._withQueryTime { t -> t - eventBegin }.mapEvents { e ->
        val shiftedPart = e.part.shift(eventBegin)
        val shiftedWhole = e.whole.shift(eventBegin)
        e.copy(part = shiftedPart, whole = shiftedWhole)
    }
}

/**
 * Lifts a function that accepts a [Double] to work with a pattern of numbers (INNER JOIN semantics).
 *
 * This is the general-purpose applicative lifter for tempo/structural operations that transform
 * the pattern itself (like fast(), slow(), zoom(), range(), etc.) rather than just modifying
 * voice data fields.
 *
 * Equivalent to `register(name, func)` in JS Strudel where arity is 1.
 *
 * **Metadata Preservation:**
 * Preserves metadata (weight, numSteps) from the SOURCE pattern, not the control pattern.
 * The control pattern drives which values are used, but the structure comes from the source.
 *
 * **Examples:**
 * ```
 * note("c3 e3 g3").fast(pure(2))          // Doubles the speed
 * note("c3 e3 g3").fast("<2 3 4>")        // Variable speed control
 * note("c3 e3 g3").slow(sine.range(1, 4)) // Continuous speed variation
 * ```
 *
 * @param control   The control pattern providing numeric values (e.g., `fast("<2 4>")`)
 * @param transform Function to apply the value to the source pattern
 * @return Pattern with inner join applied, preserving source metadata
 */
fun SprudelPattern._lift(
    control: SprudelPattern,
    transform: (Double, SprudelPattern) -> SprudelPattern,
): SprudelPattern = object : SprudelPattern {
    // Preserve metadata from SOURCE pattern
    override val weight: Double get() = this@_lift.weight
    override val numSteps: Rational? get() = this@_lift.numSteps
    override fun estimateCycleDuration(): Rational = this@_lift.estimateCycleDuration()

    // Control pattern drives the structure via bind
    // LAZY initialization to avoid circular dependencies during construction
    private val joined = control._bind { event ->
        val value = event.data.value?.asDouble ?: return@_bind null
        transform(value, this@_lift)
    }

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<SprudelPatternEvent> {
        return joined.queryArcContextual(from, to, ctx)
    }
}

/**
 * Lifts a control pattern to modify Voice Data (INNER JOIN semantics).
 *
 * Used for functions that set discrete values in voice data: sound(), bank(), vowel(),
 * as well as sample playback controls like begin(), end(), speed(), loop(), etc.
 *
 * This assumes the [control] pattern ALREADY contains the parsed data in its events
 * (e.g., created via `args.toPattern(modifier)`).
 *
 * **Inner Join Behavior:**
 * - Queries the control pattern first (structure comes from control)
 * - For each control event's time window, queries the source pattern
 * - Merges control data onto source data (control values override source values)
 *
 * **When to use _liftData vs _liftNumericField:**
 * - Use `_liftData`: Discrete values, string/enum fields, complex data structures
 * - Use `_liftNumericField`: Numeric fields that need point-sampling of continuous patterns
 *
 * @param control The control pattern containing parsed voice data to merge
 * @return Pattern with inner join applied, merging control data onto source events
 */
fun SprudelPattern._liftData(
    control: SprudelPattern,
): SprudelPattern = object : SprudelPattern {
    // Preserve metadata from the source pattern
    override val weight: Double get() = this@_liftData.weight
    override val numSteps: Rational? get() = this@_liftData.numSteps
    override fun estimateCycleDuration(): Rational = this@_liftData.estimateCycleDuration()

    // Use normalized bindPattern (defaults to preserveMetadata=true, which is redundant here but safe)
    // LAZY initialization to avoid circular dependencies during construction
    private val joined = control._bind { controlEvent ->

        // For each control event, we map the source pattern
        this@_liftData.mapEvents { sourceEvent ->

            // MERGE: control data overrides source data (where not null)
            val mergedData = sourceEvent.data.merge(controlEvent.data)

            sourceEvent.copy(data = mergedData)
        }
    }

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<SprudelPatternEvent> {
        return joined.queryArcContextual(from, to, ctx)
    }
}

/**
 * Lifts a numeric field modifier to work with control patterns (OUTER JOIN semantics).
 *
 * This is specifically designed for numeric parameters that need to sample continuous patterns
 * (like sine, saw, etc.) at each source event's time. Unlike [_liftData] which uses inner join,
 * this uses outer join to preserve the source pattern's structure and sample the control pattern
 * at each source event's begin time.
 *
 * Use this for functions that set numeric fields in voice data: filters (lpf, hpf), effects
 * (delay, room), dynamics (gain, pan), synthesis (fmh, fmenv), and tonal parameters (note, octave).
 *
 * **Why outer join?**
 * - Continuous patterns like `sine` produce a single event covering [0,1) with average value
 * - Inner join would apply that same value to ALL source events
 * - Outer join samples the control at each event's time, giving different values
 *
 * **Example:**
 * ```
 * note("a b c d").lpf(sine.range(100, 1000))
 * // Outer join samples sine at t=0, 0.25, 0.5, 0.75 giving 500, 1000, 500, 100
 * // Inner join would give all notes lpf=500 (the average)
 * ```
 *
 * @param args DSL arguments to convert to control pattern
 * @param update Function to update voice data with the numeric value (may be null)
 * @return Pattern with outer join applied, preserving source structure and metadata
 */
fun SprudelPattern._liftNumericField(
    args: List<SprudelDslArg<Any?>>,
    update: SprudelVoiceData.(Double?) -> SprudelVoiceData,
): SprudelPattern {
    if (args.isEmpty()) return this
    val control = args.toPattern()

    // Use outer join to sample control at each source event's time
    return _outerJoin(control) { sourceEvent, controlEvent ->
        val value = controlEvent?.data?.value?.asDouble
        sourceEvent.copy(data = sourceEvent.data.update(value))
            .prependLocations(controlEvent?.sourceLocations)
    }
}

/**
 * Lifts a numeric field modifier to work with control patterns (OUTER JOIN semantics).
 *
 * If no control pattern is provided, the current voice data is interpreted as a static value.
 */
fun SprudelPattern._liftOrReinterpretNumericalField(
    args: List<SprudelDslArg<Any?>>,
    update: SprudelVoiceData.(Double?) -> SprudelVoiceData,
): SprudelPattern {
    if (args.isEmpty()) {
        return this.reinterpretVoice {
            it.update(it.value?.asDouble)
        }
    }

    return this._liftNumericField(args, update)
}

/**
 * Lifts a string field modifier to work with control patterns (OUTER JOIN semantics).
 *
 * **Example:**
 * ```
 * note("a b c d").compressor("1:1:1 2:2:2")
 * ```
 *
 * @param args DSL arguments to convert to control pattern
 * @param update Function to update voice data with the numeric value (may be null)
 * @return Pattern with outer join applied, preserving source structure and metadata
 */
fun SprudelPattern._liftStringField(
    args: List<SprudelDslArg<Any?>>,
    update: SprudelVoiceData.(String?) -> SprudelVoiceData,
): SprudelPattern {
    if (args.isEmpty()) return this
    val control = args.toPattern()

    // Use outer join to sample control at each source event's time
    return _outerJoin(control) { sourceEvent, controlEvent ->
        val value = controlEvent?.data?.value?.asString
        sourceEvent.copy(data = sourceEvent.data.update(value))
            .prependLocations(controlEvent?.sourceLocations)
    }
}

/**
 * Lifts a string field modifier to work with control patterns (OUTER JOIN semantics).
 *
 * If no control pattern is provided, the current voice data is interpreted as a static value.
 */
fun SprudelPattern._liftOrReinterpretStringField(
    args: List<SprudelDslArg<Any?>>,
    update: SprudelVoiceData.(String?) -> SprudelVoiceData,
): SprudelPattern {
    if (args.isEmpty()) {
        return this.reinterpretVoice {
            it.update(it.value?.asString)
        }
    }

    return this._liftStringField(args, update)
}

/**
 * Creates a new pattern by applying a control pattern to this pattern using Outer Join semantics.
 *
 * Preserves the structure of the source pattern. The control pattern is sampled at each
 * source event's start time.
 */
fun SprudelPattern._outerJoin(
    control: SprudelPattern,
    combiner: (source: SprudelPatternEvent, control: SprudelPatternEvent?) -> SprudelPatternEvent?,
): SprudelPattern = object : SprudelPattern {
    override val weight: Double get() = this@_outerJoin.weight
    override val numSteps: Rational? get() = this@_outerJoin.numSteps
    override fun estimateCycleDuration(): Rational = this@_outerJoin.estimateCycleDuration()

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: QueryContext,
    ): List<SprudelPatternEvent> {
        return this@_outerJoin._applyControl(
            control = control,
            from = from,
            to = to,
            ctx = ctx,
            combiner = combiner
        )
    }
}

/**
 * Applies a modification to a pattern events VoiceData using the provided arguments.
 * Arguments are interpreted as a control pattern.
 */
fun SprudelPattern._applyControlFromParams(
    args: List<SprudelDslArg<Any?>>,
    modify: VoiceModifierFn,
    combine: VoiceMergerFn,
): SprudelPattern {
    if (args.isEmpty()) return this

    val control = args.toPattern(modify)

    val mapper: (SprudelVoiceData) -> SprudelVoiceData = { data ->
        val value = data.value
        if (value != null) data.modify(value) else data
    }

    return this.applyControl(control, mapper, combine)
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
fun SprudelPattern._applyControl(
    control: SprudelPattern,
    from: Rational,
    to: Rational,
    ctx: QueryContext,
    combiner: (source: SprudelPatternEvent, control: SprudelPatternEvent?) -> SprudelPatternEvent?,
): List<SprudelPatternEvent> {
    val sourceEvents = this.queryArcContextual(from, to, ctx)
    if (sourceEvents.isEmpty()) return emptyList()

    val result = mutableListOf<SprudelPatternEvent>()

    for (event in sourceEvents) {
        // Sample the control pattern at the event's begin time
        // NOTE: Using whole.begin (onset time) for sampling, not part.begin (visible start).
        // This ensures control patterns are sampled at the musical onset, not at visible clip boundaries.
        // If JS implementation differs, this may need adjustment. See accessor-replacement notes.
        val sampleTime = event.whole.begin
        val controlEvent = control.sampleAt(sampleTime, ctx)

        val combined = combiner(event, controlEvent)
            ?.prependLocations(controlEvent?.sourceLocations)

        if (combined != null) {
            result.add(combined)
        }
    }
    return result
}

fun SprudelPattern._withQueryTime(transform: (Rational) -> Rational): SprudelPattern = object : SprudelPattern {
    override val weight: Double get() = this@_withQueryTime.weight
    override val numSteps: Rational? get() = this@_withQueryTime.numSteps
    override fun estimateCycleDuration(): Rational = this@_withQueryTime.estimateCycleDuration()

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<SprudelPatternEvent> {
        return this@_withQueryTime.queryArcContextual(transform(from), transform(to), ctx)
    }
}

/**
 * Creates a pattern where the query time span is transformed by the given function.
 *
 * This transforms the input query range before passing it to the inner pattern.
 * Unlike [_withQueryTime] which transforms individual time points, this transforms
 * the entire TimeSpan, allowing for operations like reversing or scaling.
 *
 * @param transform Function that transforms a TimeSpan to a new TimeSpan
 * @return A new pattern with transformed query spans
 */
fun SprudelPattern._withQuerySpan(transform: (TimeSpan) -> TimeSpan): SprudelPattern = object : SprudelPattern {
    override val weight: Double get() = this@_withQuerySpan.weight
    override val numSteps: Rational? get() = this@_withQuerySpan.numSteps
    override fun estimateCycleDuration(): Rational = this@_withQuerySpan.estimateCycleDuration()

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<SprudelPatternEvent> {
        val querySpan = TimeSpan(from, to)
        val transformedSpan = transform(querySpan)
        return this@_withQuerySpan.queryArcContextual(transformedSpan.begin, transformedSpan.end, ctx)
    }
}

/**
 * Creates a pattern where the time of each event is transformed by the given function.
 *
 * This is the counterpart to [_withQueryTime]. While [_withQueryTime] transforms the input query range
 * (affecting WHEN we look for events), [_withHapTime] transforms the output events
 * (affecting WHERE events appear in time).
 */
fun SprudelPattern._withHapTime(transform: (Rational) -> Rational): SprudelPattern = mapEvents { event ->
    val newPartBegin = transform(event.part.begin)
    val newPartEnd = transform(event.part.end)

    val newPart = TimeSpan(newPartBegin, newPartEnd)

    val newWhole = event.whole.let {
        val newWholeBegin = transform(it.begin)
        val newWholeEnd = transform(it.end)
        TimeSpan(newWholeBegin, newWholeEnd)
    }

    event.copy(part = newPart, whole = newWhole)
}

/**
 * Creates a pattern where the time span of each event is transformed by the given function.
 *
 * This transforms both the part and whole time spans of output events after querying.
 * Unlike [_withHapTime] which transforms individual time points, this transforms
 * complete TimeSpans, allowing for operations like reversing or complex time manipulations.
 *
 * @param transform Function that transforms a TimeSpan to a new TimeSpan
 * @return A new pattern with transformed event spans
 */
fun SprudelPattern._withHapSpan(transform: (TimeSpan) -> TimeSpan): SprudelPattern = mapEvents { event ->
    event.copy(
        part = transform(event.part),
        whole = transform(event.whole)
    )
}

/**
 * Returns a new pattern that splits queries at cycle boundaries.
 *
 * This ensures that the pattern's logic is applied per-cycle, even if the requested
 * arc spans multiple cycles. This is crucial for patterns that depend on cycle position
 * (like `rev`, `zoom`) or need to maintain structure within cycles.
 */
fun SprudelPattern._splitQueries(): SprudelPattern = object : SprudelPattern {
    override val weight: Double get() = this@_splitQueries.weight
    override val numSteps: Rational? get() = this@_splitQueries.numSteps
    override fun estimateCycleDuration(): Rational = this@_splitQueries.estimateCycleDuration()

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: QueryContext,
    ): List<SprudelPatternEvent> {
        val result = mutableListOf<SprudelPatternEvent>()

        val startCycle = from.floor().toInt()
        val endCycle = to.ceil().toInt()

        for (cycle in startCycle until endCycle) {
            val cycleStart = Rational(cycle)
            val cycleEnd = cycleStart + Rational.ONE

            val queryStart = maxOf(from, cycleStart)
            val queryEnd = minOf(to, cycleEnd)

            if (queryEnd > queryStart) {
                result.addAll(this@_splitQueries.queryArcContextual(queryStart, queryEnd, ctx))
            }
        }
        return result
    }
}

fun SprudelPattern._fastGap(factor: Rational): SprudelPattern {
    return FastGapPattern.static(this, factor)
}
