/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.camera

import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.hash01
import kotlin.math.floor

// The camera's choice of a look and of the kind of a round: plain arithmetic on the times the players play, going on
// from the looks the camera has begun, so it has a spec (KLANG-00451). Where a look puts the camera, and how it moves
// between them, the choreography works out.

// A round in bars: where the looks begin, where the way back begins, where the circle begins
internal const val FIRST = 34
internal const val BACK = 43
internal const val CIRCLE = 24
internal const val ROUND = 48

/** The reverse total begins and ends, in bars. */
internal const val REVERSE_FROM = 32
internal const val REVERSE_TO = 44

/** Where the two looks of a round's first half begin, in bars. */
internal const val EARLY = 12

/** How many looks a round can have: three late, two early. */
internal const val LOOKS_A_ROUND = 5

/** The bar of a round at which its look number [i] begins: 0 to 2 are the late three, 3 and 4 the early two. */
internal fun opens(i: Int): Int = if (i < 3) FIRST + 3 * i else EARLY + 3 * (i - 3)

/** The kind of a round of the choreography. [key] is its name in a page's switches (`?move=circle`). */
enum class RoundKind(val key: String) {
    /** Three looks in a row, with a cut between them. */
    Looks("looks"),

    /** One slow way round one player. */
    Arc("arc"),

    /** One slow flight right round the whole band. */
    Circle("circle"),

    /** Out over the audience to the mixing desk far back, and back in. */
    Flight("flight"),

    /** A long look from behind the drummer at the audience. */
    Reverse("reverse");

    companion object {
        /** The kind named [key]; null where there is none. */
        fun of(key: String?): RoundKind? = entries.firstOrNull { it.key == key }
    }
}

/**
 * The camera's rounds under a show, and what its choice of a look goes by all through it: the show's [seed], a [bar]
 * in seconds, the looks at each player ([looks]), the players who play seldom ([seldom], in this order: when one of
 * them plays in a round's first look, that look is theirs), the one who strolls ([strolls]: the camera never goes
 * round her) and the kind every round is made ([force]; null: the seed decides).
 */
class Rounds(
    val seed: Int,
    val bar: Double,
    val looks: Map<Player, List<Placement>>,
    val seldom: List<Player>,
    val strolls: Player,
    val force: RoundKind? = null,
) {
    /** A round, in seconds. */
    val length: Double = ROUND * bar

    /** The song starts after the count-in; the rounds begin up to twelve bars later, by the seed. */
    val start: Double = (2 + floor(chance(n = 0, i = 0, salt = 9) * 13)) * bar

    /** A number from 0 to 1 that belongs to the round [n], the look [i] and a purpose [salt], under this seed. */
    fun chance(n: Int, i: Int, salt: Int): Double {
        return hash01(n * 127.1 + i * 311.7 + salt * 74.7 + seed * 0.618)
    }

    /** When the look number [i] of round [n] begins, in seconds. */
    fun begins(n: Int, i: Int): Double = start + n * length + opens(i) * bar
}

/**
 * The looks the camera has begun, by round and number ([begun], the key `n * LOOKS_A_ROUND + i`): what each one
 * shows stays, whatever the song forgets or learns. The plan of nothing yet is the one the camera begins with.
 */
data class LookPlan(val begun: Map<Int, Subject> = emptyMap()) {
    /** The plan in round [n]: the looks of the rounds before the last are of no use any more. */
    fun inRound(n: Int): LookPlan {
        val from = (n - 1) * LOOKS_A_ROUND

        return if (begun.keys.all { it >= from }) this else LookPlan(begun = begun.filterKeys { it >= from })
    }
}

/** What the camera chose ([what]), and the plan that goes on from it ([plan]), to be given as `before` the next time. */
data class Chosen<T>(val what: T, val plan: LookPlan)

/**
 * What the look number [i] of round [n] shows (0, 1, 2 the late three, 3 and 4 the early two): a player who plays
 * then, or the stage from another angle, by [playsAt] (when a player begins a note, sorted, as far as the song is
 * known at [now], seconds), going on from the plan [before].
 *
 * It is chosen from the notes known, and kept once the look has begun (at [now] or before): a song played live
 * forgets those notes while the round still asks for its looks (the way back from the last look goes on long after
 * the first). A look not yet begun is chosen anew each time, from what the song knows by then.
 */
fun chooseLook(
    rounds: Rounds,
    n: Int,
    i: Int,
    now: Double,
    playsAt: (Player) -> List<Double>,
    before: LookPlan,
): Chosen<Subject> {
    val key = n * LOOKS_A_ROUND + i
    val kept = before.begun[key]

    if (kept != null) {
        return Chosen(what = kept, plan = before)
    }

    val chosen = choose(rounds = rounds, n = n, i = i, now = now, playsAt = playsAt, before = before)

    if (now >= rounds.begins(n = n, i = i)) {
        return Chosen(what = chosen.what, plan = LookPlan(begun = chosen.plan.begun + (key to chosen.what)))
    }

    return chosen
}

/**
 * What kind the round [n] is: about a fifth each are a circle, an arc and a flight, about one in seven has the
 * reverse total, the rest are looks. An arc needs a player who stays at their place (the one who strolls turns
 * about: the camera would swing with her). Asks for the round's first look as [chooseLook] does, with the same
 * arguments.
 */
fun chooseKind(
    rounds: Rounds,
    n: Int,
    now: Double,
    playsAt: (Player) -> List<Double>,
    before: LookPlan,
): Chosen<RoundKind> {
    val kind = rounds.force ?: when {
        rounds.chance(n = n, i = 0, salt = 7) < 0.2 -> RoundKind.Circle
        rounds.chance(n = n, i = 0, salt = 7) < 0.4 -> RoundKind.Arc
        rounds.chance(n = n, i = 0, salt = 7) < 0.6 -> RoundKind.Flight
        rounds.chance(n = n, i = 0, salt = 7) < 0.75 -> RoundKind.Reverse
        else -> RoundKind.Looks
    }
    val first = chooseLook(rounds = rounds, n = n, i = 0, now = now, playsAt = playsAt, before = before)
    val player = first.what.player
    val arcless = kind == RoundKind.Arc && (player == null || player == rounds.strolls)

    return Chosen(what = if (arcless) RoundKind.Looks else kind, plan = first.plan)
}

/** The look number [i] of round [n], chosen from what is known (see [chooseLook]); the look before it as it is kept. */
private fun choose(
    rounds: Rounds,
    n: Int,
    i: Int,
    now: Double,
    playsAt: (Player) -> List<Double>,
    before: LookPlan,
): Chosen<Subject> {
    val a = rounds.begins(n = n, i = i)
    val b = a + 3 * rounds.bar
    // the first of its row of looks: nobody was shown right before it
    val leads = i == 0 || i == 3

    fun <T> pick(list: List<T>, salt: Int): T = list[floor(rounds.chance(n = n, i = i, salt = salt) * list.size).toInt() % list.size]

    // about one look in five is the stage from another angle
    if (rounds.chance(n = n, i = i, salt = 1) < 0.2) {
        return Chosen(what = Subject.Angle(pick(ANGLES, 2)), plan = before)
    }

    // the players who play seldom: when one of them does, the round's first look is theirs
    val rare = rounds.seldom.filter { plays(times = playsAt(it), a = a, b = b) > 2 }
    var player: Player? = if (rare.isNotEmpty() && i == 0) pick(rare, 3) else null
    // else someone who plays just then, and not the one the look before showed
    val previous = if (!leads) chooseLook(rounds = rounds, n = n, i = i - 1, now = now, playsAt = playsAt, before = before) else null
    val plan = previous?.plan ?: before
    val shown = previous?.what?.player
    val first = floor(rounds.chance(n = n, i = i, salt = 4) * ORDER.size).toInt()

    for (k in ORDER.indices) {
        if (player != null) {
            break
        }

        val candidate = ORDER[(first + k) % ORDER.size]

        if (candidate != shown && plays(times = playsAt(candidate), a = a, b = b) > 3) {
            player = candidate
        }
    }

    if (player == null) {
        return Chosen(what = Subject.Angle(pick(ANGLES, 2)), plan = plan)
    }

    return Chosen(what = Subject.OnPlayer(player, pick(rounds.looks.getValue(player), 5)), plan = plan)
}

/** How many of [times] (sorted) lie between [a] and [b]. */
private fun plays(times: List<Double>, a: Double, b: Double): Int {
    var n = 0

    for (t in times) {
        if (t >= a && t < b) {
            n++
        } else if (t >= b) {
            break
        }
    }

    return n
}
