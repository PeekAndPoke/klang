package io.peekandpoke.klang.common.math

import io.peekandpoke.klang.common.math.CycleTime.Companion.T
import io.peekandpoke.klang.common.math.CycleTime.Companion.ofCycles
import io.peekandpoke.klang.common.math.CycleTime.Companion.ofSubdivision
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.math.floor

/**
 * Nearest-integer rounding for tick snapping. Uses native [floor] (half-up) rather than
 * `kotlin.math.round` (round-half-to-even), which on Kotlin/JS is hand-implemented with tie
 * branches and was a measurable hot path. `floor(x + 0.5)`:
 * - is faster (one add + native floor),
 * - rounds to nearest so exact subdivisions land right — e.g. `1.0/3.0` is `0.3333…` just *below*
 *   a true third, so plain `floor` would truncate `T/3` one tick low; `floor(x+0.5)` lands on `T/3`,
 * - never yields `-0.0` (floor of any value ≥ -0.5 is `+0.0`), keeping `== ZERO` checks robust.
 * Ties round half-up; musically irrelevant (the snapping itself is the fixed-point approximation).
 */
private fun roundTicks(x: Double): Double = floor(x + 0.5)

/**
 * Fixed-point musical time, measured in integer **ticks** of a cycle.
 *
 * One cycle = [T] ticks. This replaces [Rational] on the timing hot path: with a single shared
 * resolution, `plus`/`minus`/`compareTo` become plain integer arithmetic — **no gcd, no division** —
 * which is where the BigInt-backed [Rational] spent almost all of its time on Kotlin/JS.
 *
 * Stored as a [Double] holding an exact integer. This is safe because:
 * - IEEE-754 represents every integer up to 2^53 exactly, and our tick counts stay around 10^10
 *   even for marathon sessions (T ≈ 8.6e5 × ~1e4 cycles), far below 2^53 ≈ 9e15.
 * - Therefore `+`, `-`, unary minus, and `times(Int)` are **bit-exact with no rounding**.
 * - Rounding is only applied where a value is first snapped to the grid: [ofCycles], [ofSubdivision],
 *   [scaleBy]/[divBy]. That snapping is the intended quantization (e.g. a `0.001`-cycle humanization
 *   offset snaps to the nearest tick instead of polluting denominators).
 *
 * As a [JvmInline] value class this compiles to a bare `number` on JS (no boxing — unlike `Long`) and
 * a primitive `double` on JVM, so the abstraction is zero-cost on the hot path.
 *
 * ### Exactness envelope
 * `T = 2^13 · 3 · 5 · 7 = 860160` represents exactly: all dyadic subdivisions down to 1/8192, and
 * triplets/quintuplets/septuplets and their products (1/3, 1/5, 1/7, 1/6, 1/12 … 1/96, 1/15, 1/105 …).
 * Subdivisions with denominators carrying 3^2 (1/9, 1/18 …), 5^2 (1/25), 7^2, or any prime ≥ 11
 * (1/11, 1/13 …), as well as dyadic finer than 1/8192, are **rounded to the nearest tick**
 * (sub-microsecond at typical tempo). These are rare in practice.
 */
@Suppress("MemberVisibilityCanBePrivate")
@Serializable
@JvmInline
value class CycleTime(val ticks: Double) : Comparable<CycleTime> {

    // --- Exact arithmetic (no rounding: integer ± integer stays an exact integer < 2^53) ---

    operator fun plus(other: CycleTime): CycleTime = CycleTime(ticks + other.ticks)
    operator fun minus(other: CycleTime): CycleTime = CycleTime(ticks - other.ticks)
    operator fun unaryMinus(): CycleTime = CycleTime(-ticks)

    /** Scales by an integer factor — exact. */
    operator fun times(n: Int): CycleTime = CycleTime(ticks * n)

    override operator fun compareTo(other: CycleTime): Int = ticks.compareTo(other.ticks)

    // --- Grid-snapping arithmetic (rounds to the nearest tick) ---

    /** Scales by an arbitrary factor (tempo: fast/slow/zoom). Rounds to the nearest tick. */
    fun scaleBy(factor: Double): CycleTime = CycleTime(roundTicks(ticks * factor))

    /** Divides by an arbitrary factor. Rounds to the nearest tick. */
    fun divBy(factor: Double): CycleTime = CycleTime(roundTicks(ticks / factor))

    /** Dimensionless ratio of two durations (e.g. mapping outer time into an inner step). */
    fun ratioTo(other: CycleTime): Double = ticks / other.ticks

    // --- Min / max without boxing (value class through Comparable would box) ---

    fun coerceAtLeast(other: CycleTime): CycleTime = if (ticks >= other.ticks) this else other
    fun coerceAtMost(other: CycleTime): CycleTime = if (ticks <= other.ticks) this else other

    // --- Cycle-boundary helpers ---

    /** The integer cycle index containing this time (floor of cycles). */
    fun cycleIndex(): Int = floor(ticks / T).toInt()

    /** Snaps down to the start of the containing cycle. */
    fun floorToCycle(): CycleTime = CycleTime(floor(ticks / T) * T)

    /** Snaps up to the next cycle boundary (or stays, if already on one). */
    fun ceilToCycle(): CycleTime {
        val cycles = ticks / T
        val f = floor(cycles)
        return CycleTime((if (cycles == f) f else f + 1.0) * T)
    }

    /** The position within the current cycle, in [0, 1) cycles. */
    fun fracOfCycle(): CycleTime = CycleTime(ticks - floor(ticks / T) * T)

    /** Modulo by a span (e.g. wrapping into a loop length). Result has the sign of [span]. */
    fun modCycles(span: CycleTime): CycleTime {
        val r = ticks % span.ticks
        return CycleTime(if (r < 0.0) r + span.ticks else r)
    }

    // --- Conversions ---

    /** Position in cycles as a Double (for the audio bridge: startCycles / durationCycles). */
    fun toCycles(): Double = ticks / T

    /** Alias of [toCycles] — the position in cycles as a Double. */
    fun toDouble(): Double = ticks / T

    override fun toString(): String = "CycleTime(${ticks / T} cycles, $ticks ticks)"

    companion object {
        /** Ticks per cycle: 2^13 · 3 · 5 · 7. Highly composite → exact tuplets + fine dyadic grid. */
        const val T: Double = 860160.0

        val ZERO = CycleTime(0.0)
        val ONE = CycleTime(T)
        val HALF = CycleTime(T / 2.0)
        val QUARTER = CycleTime(T / 4.0)

        /** From a fractional cycle position (Double bridge). Rounds to the nearest tick. */
        fun ofCycles(cycles: Double): CycleTime = CycleTime(roundTicks(cycles * T))

        /** From a whole cycle index — exact. */
        fun ofCycleIndex(index: Int): CycleTime = CycleTime(index * T)

        /** Position of step [i] of [n] equal subdivisions (i/n of a cycle). Rounds if n ∤ T. */
        fun ofSubdivision(i: Int, n: Int): CycleTime = CycleTime(roundTicks(i.toDouble() * T / n))
    }
}
