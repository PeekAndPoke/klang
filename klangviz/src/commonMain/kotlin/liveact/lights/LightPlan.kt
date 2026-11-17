/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.lights

import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.hash01
import io.peekandpoke.klang.viz.liveact.smooth
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

// The lights' plan: what the lamps do at a moment, as numbers only. Which mood when, where the front lamp comes up
// and in which colour, where the stroboscope runs, how a lamp flickers. The lamps themselves are in Lights.

/** The stroboscope's two levels: the front lamp between the flashes, and the other lamps meanwhile. */
object Strobe {
    /** The front lamp between the flashes: it stays on a little. */
    const val LOW = 0.22

    /** The other lamps while it flashes: this share of their light. */
    const val REST = 0.35
}

/**
 * Whether a lamp's shadow is drawn this frame: [intensity] is the lamp's light, [made] whether its shadow was ever
 * drawn. A dark lamp throws no shadow, and drawing one means drawing the whole band once more. Once it must be
 * drawn all the same: every lit surface reads every lamp's shadow, and without one to read nothing lit is drawn at
 * all (seen 2026-11-16: what three.js puts in an unmade shadow's place does not fit the shader, and the browser
 * refuses the drawing).
 */
fun shadowDrawn(intensity: Double, made: Boolean): Boolean {
    // NaN-guard: an intensity that is no number (?front=x) is not above 0
    return intensity > 0.0 || !made
}

/** Which lamps a lamp belongs to: the ones behind the band, the fill from the front, the one low at the front. */
enum class LampGroup {
    BACK,
    FILL,
    FRONT;

    /**
     * The share of its light a lamp of this group gives, with the front lamp at [front] (0 to 1) and the
     * stroboscope running or not ([strobing]): the lamps behind go down most when the front lamp comes up.
     */
    fun share(front: Double, strobing: Boolean): Double = when (this) {
        FRONT -> front
        BACK -> if (strobing) Strobe.REST else 1 - 0.9 * front
        FILL -> if (strobing) Strobe.REST else 1 - 0.6 * front
    }
}

/** Where a lamp behind the band hangs: one of the two main ones at the sides, or the one between them. */
enum class BackPlace {
    OUTER,
    INNER,
}

/**
 * Which of the lamps behind the band are on. Mostly all three; for a while only the two at the sides, or only the
 * one between them (the maintainer, 2026-11-17: "sometimes only the two others or only the inner one").
 */
enum class BackRig {
    ALL,
    OUTER,
    INNER;

    /**
     * The share of its light a lamp at [place] gives in this rig. The inner one alone is turned up. It lights the
     * middle of the stage: the two guitarists at the sides stand in the dark then, as the drummer does while only
     * the outer two are on.
     */
    fun share(place: BackPlace): Double = when (this) {
        ALL -> 1.0
        OUTER -> if (place == BackPlace.OUTER) 1.0 else 0.0
        INNER -> if (place == BackPlace.INNER) INNER_ALONE else 0.0
    }

    companion object {
        /** The inner lamp's light when it is the only one behind, against its light among the three. */
        const val INNER_ALONE = 1.4
    }
}

/** The rigs at a moment: going over [from] one [to] the next, by [share] (0: all the first). */
class BackRigMix(val from: BackRig, val to: BackRig, val share: Double) {
    /** The share of its light a lamp at [place] gives just now. */
    fun share(place: BackPlace): Double = from.share(place) + (to.share(place) - from.share(place)) * share
}

/**
 * The front lamp's colours: each time it comes up it has one of them, the seed's choice; the stroboscope is
 * always the cold blue. The amber is a deep one (a paler one looked like a building site's lamp, the maintainer,
 * 2026-11-16). ?fcolor=ff1408 sets one.
 */
object FrontColors {
    const val AMBER = 0xff8420
    const val RED = 0xff1408
    const val BLUE = 0x8fb8ff
    const val VIOLET = 0xc428ff

    /** The show's choice, by the seed. */
    val SHOW: List<Int> = listOf(AMBER, RED, BLUE, RED, AMBER, VIOLET)

    /**
     * By hand, to try the colours: the key f turns the front lamp on in the first of them, f again in the next, and
     * after the last gives the lamp back to the show.
     */
    val TRY: List<Int> = listOf(AMBER, RED, BLUE, VIOLET)
}

/**
 * One of the two main lamps behind in a mood: its [color], and where it seems to hang, off its first place ([offset]:
 * x, y, z in metres).
 */
data class MoodLamp(val color: Int, val offset: Triple<Double, Double, Double>)

/** A mood: the two main lamps behind, the [left] one and the [right] one. */
data class Mood(val left: MoodLamp, val right: MoodLamp)

/** The moods at a moment: going over [from] one [to] the next, by [share] (0: all the first). */
class MoodMix(val from: Mood, val to: Mood, val share: Double)

/** How much the front lamp is on ([level], 0 to 1), and whether it [strobing]. */
data class FrontLight(val level: Double, val strobing: Boolean) {
    companion object {
        val off = FrontLight(level = 0.0, strobing = false)
    }
}

/** A stretch of time, [from] and [to] in seconds. */
data class Run(val from: Double, val to: Double)

/**
 * Where the bass drum runs: eight hits or more in a row, each less than a quarter of a second after the one
 * before (a double sounding at once counts once). The stroboscope is on for just these stretches. [kicks]: when
 * the bass drum is hit, sorted.
 */
fun bassDrumRuns(kicks: List<Double>): List<Run> {
    val single = kicks.filterIndexed { i, k -> i == 0 || k - kicks[i - 1] > 0.03 }
    val runs = mutableListOf<Run>()
    var first = 0

    for (i in 1..single.size) {
        if (i == single.size || single[i] - single[i - 1] >= 0.25) {
            if (i - first >= 8) {
                runs.add(Run(from = single[first], to = single[i - 1]))
            }

            first = i
        }
    }

    return runs
}

/**
 * The lamps are analog: old stage lamps on a tired supply. Each one's brightness wanders slowly on its own,
 * breathes a little, shivers faintly, and now and then sags for a moment as if something else drew current; none
 * is ever quite steady and no two do the same.
 *
 * The factor on a lamp's light at [t], for the lamp's own number [own] (see [LightPlan.own]).
 */
fun lampFlicker(t: Double, own: Double): Double {
    val wander = 0.060 * sin(t * 0.23 + own) + 0.040 * sin(t * 0.61 + own * 2.3) + 0.025 * sin(t * 1.37 + own * 0.7)
    val shiver = 0.012 * sin(t * 9.1 + own * 3.1) + 0.008 * sin(t * 14.7 + own * 1.9)
    // a sag: a dip of a sixth, about every twenty seconds, at its own moment per lamp
    val phase = ((t + own * 5.3) % 19.7 + 19.7) % 19.7
    val sag = if (phase < 1.2) 0.16 * sin(PI * phase / 1.2).pow(2) else 0.0

    return 1 + wander + shiver - sag
}

/** The lights' plan for one [show]: the moods, the front lamp, the stroboscope. */
class LightPlan(private val show: Show) {

    /** Where the bass drum runs fast: the stroboscope's stretches. */
    val runs: List<Run>
        get() {
            val kicks = show.times("kick")

            // the same list as long as the song has not grown
            if (kicks !== runsOf) {
                runsOf = kicks
                found = bassDrumRuns(kicks)
            }

            return found
        }

    private var runsOf: List<Double>? = null
    private var found: List<Run> = emptyList()

    /** The moods in the order they come, as far as asked for: the show opens in the first. */
    private val order = mutableListOf(0)

    /** A lamp's own number under the show's seed, from its [lampSeed]: what makes its flicker its own. */
    fun own(lampSeed: Double): Double = lampSeed + (show.seed % 97) * 0.37

    /** The [k]th mood of the show. */
    fun moodOf(k: Int): Mood {
        while (order.size <= k) {
            val n = order.size
            val pick = 1 + floor(hash01(n * 12.9 + show.seed * 0.71 + 3.3) * (MOODS.size - 1)).toInt() % (MOODS.size - 1)
            order.add(if (pick == order[n - 1]) 1 + pick % (MOODS.size - 1) else pick)
        }

        return MOODS[order[max(0, k)]]
    }

    /** The moods at [t]: the one that holds, going over into the next in its last bars. */
    fun moodAt(t: Double): MoodMix {
        val x = max(0.0, t) / show.bar
        val k = floor(x / (HOLD + TURN)).toInt()
        val u = smooth((x - k * (HOLD + TURN) - HOLD) / TURN)

        return MoodMix(from = moodOf(k), to = moodOf(k + 1), share = u)
    }

    /** The rigs in the order they come, as far as asked for: the show opens with all the lamps. */
    private val rigs = mutableListOf(BackRig.ALL)

    /**
     * The [k]th rig of the show, the seed's choice. Never the same few lamps twice in a row: all of them come on
     * between. The chance picks all the lamps half the time and each of the two others a quarter; with that rule
     * it comes to three rigs in five with all the lamps, and one in five each with only the outer two and only the
     * inner one.
     */
    fun rigOf(k: Int): BackRig {
        while (rigs.size <= k) {
            val n = rigs.size
            val chance = hash01(n * 7.3 + show.seed * 0.53 + 1.9)
            val pick = when {
                chance < 0.5 -> BackRig.ALL
                chance < 0.75 -> BackRig.OUTER
                else -> BackRig.INNER
            }

            rigs.add(if (pick == rigs[n - 1]) BackRig.ALL else pick)
        }

        return rigs[max(0, k)]
    }

    /** `?rig=outer` (or inner, or all): the rig held all through, to look at it; null where the show decides. */
    private val held: BackRig? = show.switches["rig"]?.let { name -> BackRig.entries.firstOrNull { it.name.lowercase() == name } }

    /**
     * The rigs at [t]: the one that holds, going over into the next in its last bars. Before the song: the first.
     * With `?rig=` the one held.
     */
    fun rigAt(t: Double): BackRigMix {
        if (held != null) {
            return BackRigMix(from = held, to = held, share = 0.0)
        }

        val x = max(0.0, t) / show.bar
        val k = floor(x / (RIG_HOLD + RIG_TURN)).toInt()
        val u = smooth((x - k * (RIG_HOLD + RIG_TURN) - RIG_HOLD) / RIG_TURN)

        return BackRigMix(from = rigOf(k), to = rigOf(k + 1), share = u)
    }

    /** The front lamp's colour at [t]; [tried]: the colour tried by hand (an index into [FrontColors.TRY]), -1 for none. */
    fun frontColor(t: Double, tried: Int): Int {
        if (tried >= 0) {
            return FrontColors.TRY[tried]
        }

        val chosen = show.switches["fcolor"]

        if (chosen != null) {
            // as parseInt(..., 16) in the prototype; what is no number is black, as three.js makes of NaN
            return chosen.toIntOrNull(16) ?: 0
        }

        val turn = floor((t / show.bar + (show.seed * 7.0) % 40) / 40)
        val colors = FrontColors.SHOW

        return colors[floor(hash01(turn * 9.7 + show.seed * 1.3 + 0.4) * colors.size).toInt() % colors.size]
    }

    /**
     * How much the front lamp is on at [t] (0 to 1), and whether it strobes. The light changes sides now and then:
     * for eight bars in every forty the lamps behind go down and the one lamp low at the front comes up, so the band
     * throws big shadows up the wall; a second each way. Where in the song, the seed decides.
     *
     * [waiting]: before the click. [trying]: a colour is tried by hand (the key f), the lamp is full on.
     */
    fun frontAt(t: Double, waiting: Boolean, trying: Boolean): FrontLight {
        if (waiting) {
            return FrontLight.off
        }

        val bar = show.bar
        val x = ((t / bar + (show.seed * 7.0) % 40) % 40 + 40) % 40

        if (trying) {
            return FrontLight(level = 1.0, strobing = false)
        }

        if (show.switches.has("front")) {
            // as parseFloat in the prototype: what is no number stays no number
            return FrontLight(level = show.switches.double("front") ?: Double.NaN, strobing = false)
        }

        if (t < 0) {
            return FrontLight.off
        }

        // and as a stroboscope, while the bass drum runs fast (see runs): it flashes on the sixteenths (about ten a
        // second, the maintainer's choice, 2026-11-16: the eighths were too slow). Not from black to full: between the
        // flashes the front lamp stays on a little and the other lamps stay at Strobe.REST of their light (all off
        // and full on was too hard, the maintainer, 2026-11-16). Flashing this fast over the whole picture can
        // trigger seizures in people with photosensitive epilepsy: a public showing needs a warning before it.
        // ?nostrobe switches it off.
        if (!show.switches.has("nostrobe") && runs.any { t >= it.from - 0.02 && t < it.to + 0.08 }) {
            val sixteenth = (t * show.bpm / 60 * 4) % 1

            return FrontLight(level = if (sixteenth < 0.28) 1.0 else Strobe.LOW, strobing = true)
        }

        return FrontLight(level = smooth((x - 26) * bar / 1.0) * smooth((34 - x) * bar / 1.0), strobing = false)
    }

    companion object {
        /** A mood stays for [HOLD] bars; over the next [TURN] it goes over into another. */
        const val HOLD = 24
        const val TURN = 4

        /**
         * A rig (which lamps behind are on) stays for [RIG_HOLD] bars; over the next [RIG_TURN] it goes over into
         * another. Its round is not the moods': so the two fall together differently each time.
         */
        const val RIG_HOLD = 14
        const val RIG_TURN = 2

        /**
         * The moods: the colours of the two main lamps behind (the left one, the right one), and where each seems to
         * hang, off its first place (x, y, z in metres): as if other lamps of the rig had been turned on, so the
         * shadows lie a little differently. A mood stays for 24 bars; over the next 4 it goes over into another, the
         * seed's choice, never the same again at once. The show opens in the first.
         */
        val MOODS: List<Mood> = listOf(
            // cold against hot
            mood(left = 0x3070ff, right = 0xff3070, l = Triple(0.0, 0.0, 0.0), r = Triple(0.0, 0.0, 0.0)),
            // violet and amber
            mood(left = 0x7a3cff, right = 0xff9030, l = Triple(1.2, 0.5, 0.6), r = Triple(-0.8, -0.4, 0.3)),
            // teal and magenta
            mood(left = 0x18b8c0, right = 0xe030c0, l = Triple(-1.0, -0.5, 0.9), r = Triple(1.1, 0.6, -0.4)),
            // cold all over
            mood(left = 0x2038ff, right = 0x3c98ff, l = Triple(0.8, 0.8, -0.5), r = Triple(-1.3, 0.3, 0.8)),
            // hot all over
            mood(left = 0xff2a2a, right = 0xff7a20, l = Triple(-0.7, -0.6, 0.4), r = Triple(0.9, -0.5, 0.7)),
            // the first, the sides swapped
            mood(left = 0xff3070, right = 0x3070ff, l = Triple(1.4, 0.2, 0.2), r = Triple(-1.4, 0.2, 0.2)),
        )

        private fun mood(
            left: Int,
            right: Int,
            l: Triple<Double, Double, Double>,
            r: Triple<Double, Double, Double>,
        ): Mood = Mood(left = MoodLamp(color = left, offset = l), right = MoodLamp(color = right, offset = r))
    }
}
