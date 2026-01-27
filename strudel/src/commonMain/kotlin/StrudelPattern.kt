package io.peekandpoke.klang.strudel

import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull
import io.peekandpoke.klang.strudel.lang.strudelLib
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational
import io.peekandpoke.klang.strudel.pattern.StaticStrudelPattern
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
