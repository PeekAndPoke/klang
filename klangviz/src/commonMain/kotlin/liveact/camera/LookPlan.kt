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

// A round in bars: where the looks begin, where the way back begins, where the circle begins. The circle takes the
// round's last 32 bars (it took 24: the maintainer, 2026-11-21, "the 360 around the band has to be a bit slower").
internal const val FIRST = 34
internal const val BACK = 43
internal const val CIRCLE = 16
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
    val strolls: Player?,
    val force: RoundKind? = null,
    /** The moment on the show's clock at which the song begins: 0 for a show's first song. */
    val after: Double = 0.0,
    /** Whose arc has no room where the band is placed in this song: a round whose first look is theirs is no arc. */
    val arcless: Set<Player> = emptySet(),
) {
    /** A round, in seconds. */
    val length: Double = ROUND * bar

    /** The song starts after the count-in; the rounds begin up to twelve bars later, by the seed. */
    val start: Double = after + (2 + floor(chance(n = 0, i = 0, salt = 9) * 13)) * bar

    /** A number from 0 to 1 that belongs to the round [n], the look [i] and a purpose [salt], under this seed. */
    fun chance(n: Int, i: Int, salt: Int): Double {
        return hash01(n * 127.1 + i * 311.7 + salt * 74.7 + seed * 0.618)
    }

    /** When the look number [i] of round [n] begins, in seconds. */
    fun begins(n: Int, i: Int): Double = start + n * length + opens(i) * bar

    /** When the flight of round [n] would leave the total, in seconds, were the round a flight. */
    fun leaves(n: Int): Double = start + n * length + Flight.OUT * bar

    /** The kinds the seed drew for the rounds so far ([drawn]): worked out once, the same whenever asked. */
    private val draws = mutableListOf<RoundKind>()

    /** The round each kind was last drawn in, by its ordinal; -1: never. */
    private val lastDrawn = IntArray(RoundKind.entries.size) { -1 }

    /**
     * The kind the seed draws for round [n], before the song has its say ([chooseKind]): one of the [KIND_CHOICE]
     * kinds that waited longest since the seed last drew them, by their weights ([kindWeight]) (the maintainer,
     * 2026-11-26: "sort by how long they have not been used, and pick randomly from the 3-5 that have recently not
     * been used", KLANG-00489). So no kind comes twice in three rounds. It goes by what the seed drew, not by what the
     * round became (an arc without room is looks): what cannot be shown is not drawn first again and again. Worked out
     * from round 0 on and kept, so a round is the same kind however it is asked. Before the first round: looks. The
     * draws repeat after [DRAWN_ROUNDS] rounds (some nine days of a show), so a clock without an end asks for no
     * more; there the memory starts over.
     */
    fun drawn(n: Int): RoundKind {
        if (n < 0) {
            return RoundKind.Looks
        }

        val m = n % DRAWN_ROUNDS

        while (draws.size <= m) {
            val kind = draw(draws.size)

            lastDrawn[kind.ordinal] = draws.size
            draws.add(kind)
        }

        return draws[m]
    }

    /** The kind round [n] draws, the rounds before it drawn ([draws], [lastDrawn]). */
    private fun draw(n: Int): RoundKind {
        // the longest waited first; a kind never drawn waited longest; between kinds that waited as long the seed
        // decides
        val waiting = RoundKind.entries.sortedWith(
            compareByDescending<RoundKind> { kind -> lastDrawn[kind.ordinal].let { if (it < 0) Int.MAX_VALUE else n - it } }
                .thenBy { kind -> chance(n = n, i = kind.ordinal, salt = 11) },
        ).take(KIND_CHOICE)

        var x = chance(n = n, i = 0, salt = 7) * waiting.sumOf { kindWeight(it) }

        for (kind in waiting) {
            x -= kindWeight(kind)

            if (x < 0) {
                return kind
            }
        }

        return waiting.last()
    }
}

/**
 * How much weight a kind of round has among the [KIND_CHOICE] the seed draws from ([Rounds.drawn]): the shares each
 * kind had before the camera remembered (KLANG-00489). Over a long show they come out a little evened by the memory:
 * a circle, an arc and a flight a fifth each, the reverse total about one in six, looks a little over a fifth.
 */
internal fun kindWeight(kind: RoundKind): Double = when (kind) {
    RoundKind.Circle -> 0.2
    RoundKind.Arc -> 0.2
    RoundKind.Flight -> 0.2
    RoundKind.Reverse -> 0.15
    RoundKind.Looks -> 0.25
}

/** After how many rounds the seed's draws repeat ([Rounds.drawn]): about nine days of rounds of 79 s. */
internal const val DRAWN_ROUNDS = 10_000

/** From how many of the kinds that waited longest a round's kind is drawn ([Rounds.drawn]). */
internal const val KIND_CHOICE = 3

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
 * What kind the round [n] is: the kind the seed drew for it ([Rounds.drawn]: none twice in three rounds), unless
 * the song says otherwise. An arc needs a player who stays at their place (the one who strolls turns about: the
 * camera would swing with her) and whose arc has room ([Rounds.arcless]). A flight is looks where the camera has
 * just [flown] to the mixing desk on its own (before the one who strolls), so it does not fly out again right after.
 * Asks for the round's first look as [chooseLook] does, with the same arguments.
 */
fun chooseKind(
    rounds: Rounds,
    n: Int,
    now: Double,
    playsAt: (Player) -> List<Double>,
    before: LookPlan,
    flown: Boolean = false,
): Chosen<RoundKind> {
    val kind = rounds.force ?: rounds.drawn(n).let { if (it == RoundKind.Flight && flown) RoundKind.Looks else it }
    val first = chooseLook(rounds = rounds, n = n, i = 0, now = now, playsAt = playsAt, before = before)
    val player = first.what.player
    val arcless = kind == RoundKind.Arc && (player == null || player == rounds.strolls || player in rounds.arcless)

    return Chosen(what = if (arcless) RoundKind.Looks else kind, plan = first.plan)
}

/**
 * Whether round [n]'s flight would follow the camera's own flight to the mixing desk too closely: one of [retreats]
 * (when it took off before the one who strolls, in seconds) began within a round before the round's flight would
 * leave ([Rounds.leaves]). A take-off at or after that moment changes nothing: the round keeps its kind (KLANG-00489).
 */
fun flownBefore(rounds: Rounds, n: Int, retreats: List<Double>): Boolean {
    val leaves = rounds.leaves(n)

    return retreats.any { it >= leaves - rounds.length && it < leaves }
}

/**
 * Whether a round's own flight left the total within a round before [t] (this round's or the one before's;
 * [isFlight] says whether a round is a flight): the camera does not take off before the one who strolls right after
 * it (KLANG-00489).
 */
fun roundFlewBefore(rounds: Rounds, t: Double, isFlight: (Int) -> Boolean): Boolean {
    // NaN-guard: no time, no flight before it
    if (t != t) {
        return false
    }

    val n = floor((t - rounds.start) / rounds.length).toInt()

    return (n - 1..n).any { m -> m >= 0 && t >= rounds.leaves(m) && t - rounds.leaves(m) < rounds.length && isFlight(m) }
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
