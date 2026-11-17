/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.camera

import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.Y_AXIS
import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.common.Rig
import io.peekandpoke.klang.viz.liveact.hash01
import io.peekandpoke.klang.viz.liveact.smoother
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.addScaledVector
import io.peekandpoke.klang.workshop.threejs.cross
import io.peekandpoke.klang.workshop.threejs.distanceTo
import io.peekandpoke.klang.workshop.threejs.lerp
import io.peekandpoke.klang.workshop.threejs.subVectors
import io.peekandpoke.kraft.addons.threejs.js.Vector3
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

// The camera's choreography. Mostly the total: the wide view from the audience, the start position. The song is cut
// into rounds of 48 cycles (bars, about 79 s), and a round is one of four kinds, by the seed:
//   looks   34 bars on the total, then three looks in a row, three bars each, with a cut between them (close on one
//           player, or the stage from another angle)
//   arc     the same, but the nine bars are one look: the camera goes slowly round one player, across their front
//   circle  24 bars on the total, then one slow flight right round the whole band, from the start position and
//           back to it, with no cut at all
//   flight  20 bars on the total, then out backward over the audience to the mixing desk far back (the FOH), a
//           while there, and back in; one of the two ways may be a cut instead (see Flight)
// A round of looks or with an arc also has two looks in a row in its first half (bars 12 to 18, back to the total
// by a plain cut), so such a round never holds the total longer than some 17 bars (the maintainer, 2026-11-17:
// "could use more camera cuts and movements. not too much but a little more").
// From the late looks or an arc it comes back to the total by a plain cut in two rounds of three, and in the third by a slow
// dolly zoom over five bars (the camera travels back while its lens opens, so the player keeps much of their size
// at first as the stage grows round them).
// Calm on purpose (the maintainer, 2026-11-16: fast moves "make dizzy"; "do not cut too often to the people, but
// maybe multiple in a row"; "the camera must mostly show the total"; "cut back to the total without camera
// movement, not always though").
// Everything follows from the time alone, so the same moment always has the same picture.

/** The camera in the world: where it is ([pos]), what it looks at ([at]), its lens ([fov], degrees). */
class Shot(val pos: Vector3, val at: Vector3, val fov: Double)

/** A fixed place of the camera, in plain numbers: where it is, what it looks at, the lens. */
data class Placement(
    val pos: Triple<Double, Double, Double>,
    val at: Triple<Double, Double, Double>,
    val fov: Double,
)

/** A slow way round one player, in the player's own space: how far off, how high, what it looks at, the lens. */
data class Orbit(val far: Double, val high: Double, val at: Triple<Double, Double, Double>, val fov: Double)

/** The kind of a round of the choreography. [key] is its name in a page's switches (`?move=circle`). */
enum class RoundKind(val key: String) {
    /** Three looks in a row, with a cut between them. */
    Looks("looks"),

    /** One slow way round one player. */
    Arc("arc"),

    /** One slow flight right round the whole band. */
    Circle("circle"),

    /** Out over the audience to the mixing desk far back, and back in. */
    Flight("flight");

    companion object {
        /** The kind named [key]; null where there is none. */
        fun of(key: String?): RoundKind? = entries.firstOrNull { it.key == key }
    }
}

/** What a look shows: the stage from another angle, or one player. */
sealed class Subject {
    /** The player looked at; null for the stage. */
    abstract val player: Player?

    /** What it shows, in words (for the line at the top). */
    abstract val words: String

    /** The stage from [angle]. */
    class Angle(val angle: Placement) : Subject() {
        override val player: Player? get() = null

        override val words: String get() = if (angle === STADIUM) "stadium" else "the stage from elsewhere"
    }

    /** The [player], by [look] (in the player's own space). */
    class OnPlayer(override val player: Player, val look: Placement) : Subject() {
        override val words: String get() = player.key
    }
}

/** The total, the start position. Never change it: copy first. */
val HOME: Shot by lazy { Shot(pos = vec(0.0, 1.85, 9.4), at = vec(0.0, 1.0, 0.0), fov = 35.0) }

/**
 * Looks at a player who stands, in the player's own space (x to their left, y up, z ahead of them): where the camera
 * is, what it looks at, the lens.
 */
val STANDING_LOOKS: List<Placement> = listOf(
    Placement(pos = Triple(0.7, 0.75, 2.4), at = Triple(0.0, 1.20, 0.1), fov = 42.0),       // from below, in front
    Placement(pos = Triple(2.0, 1.45, 0.9), at = Triple(0.0, 1.15, 0.2), fov = 38.0),       // from their left
    Placement(pos = Triple(-0.9, 1.55, 1.7), at = Triple(0.0, 1.30, 0.1), fov = 30.0),      // close on head and hands
    Placement(pos = Triple(-1.9, 1.1, 1.3), at = Triple(0.0, 1.10, 0.2), fov = 40.0),       // from their right, low
)

/** Looks at the players who do not stand ([looksOf] gives everyone else [STANDING_LOOKS]), as [STANDING_LOOKS]. */
val LOOKS: Map<Player, List<Placement>> = mapOf(
    Player.Drums to listOf(
        Placement(pos = Triple(1.2, 1.0, 3.1), at = Triple(0.0, 0.95, 0.4), fov = 40.0),    // over the kick drum
        Placement(pos = Triple(-1.1, 2.2, -1.5), at = Triple(0.0, 0.85, 0.7), fov = 55.0),  // over his shoulder, down on the kit
        Placement(pos = Triple(2.4, 1.25, 0.7), at = Triple(0.0, 0.95, 0.4), fov = 38.0),   // from his left
    ),
    Player.Marimba to listOf(
        Placement(pos = Triple(0.2, 2.2, 2.3), at = Triple(0.0, 0.90, 0.5), fov = 42.0),    // down on the bars
        Placement(pos = Triple(1.9, 1.15, 1.5), at = Triple(0.0, 1.05, 0.5), fov = 38.0),   // along the bars
        Placement(pos = Triple(-0.8, 1.5, 2.2), at = Triple(0.0, 1.25, 0.2), fov = 32.0),
    ),
)

/** The looks at [player]. */
fun looksOf(player: Player): List<Placement> = LOOKS[player] ?: STANDING_LOOKS

/** The stadium shot: from far back and high in the audience, the whole stage small above a field of held-up lights. */
val STADIUM: Placement = Placement(pos = Triple(5.0, 11.0, 46.0), at = Triple(0.0, 0.0, 14.0), fov = 27.0)

/** The stage from elsewhere. */
val ANGLES: List<Placement> = listOf(
    Placement(pos = Triple(-7.2, 1.1, 5.6), at = Triple(0.0, 1.0, 0.6), fov = 40.0),        // from the left, low
    Placement(pos = Triple(0.0, 5.6, 7.4), at = Triple(0.0, 0.6, 0.6), fov = 38.0),         // from above
    Placement(pos = Triple(7.0, 1.4, 5.2), at = Triple(0.0, 1.0, 0.6), fov = 40.0),         // from the right
    Placement(pos = Triple(0.0, 0.45, 6.6), at = Triple(0.0, 1.5, 0.0), fov = 44.0),        // from the floor
    STADIUM,                                                                                // from far back in the audience
)

/** Round one player who stands, in the player's own space: how far off, how high, what it looks at, the lens. */
private val STANDING_ARC = Orbit(far = 2.7, high = 1.35, at = Triple(0.0, 1.15, 0.1), fov = 38.0)

/** Round the players who do not stand, as [STANDING_ARC]. */
private val ARCS: Map<Player, Orbit> = mapOf(
    Player.Drums to Orbit(far = 3.4, high = 1.30, at = Triple(0.0, 0.95, 0.4), fov = 40.0),
    Player.Marimba to Orbit(far = 3.1, high = 1.60, at = Triple(0.0, 1.00, 0.4), fov = 40.0),
)

// A round in bars: where the looks begin, where the way back begins, where the circle begins
private const val FIRST = 34
private const val BACK = 43
private const val CIRCLE = 24
private const val ROUND = 48

/** Where the two looks of a round's first half begin, in bars. */
private const val EARLY = 12

/** How many looks a round can have: three late, two early. */
private const val LOOKS_A_ROUND = 5

/** The bar of a round at which its look number [i] begins: 0 to 2 are the late three, 3 and 4 the early two. */
private fun opens(i: Int): Int = if (i < 3) FIRST + 3 * i else EARLY + 3 * (i - 3)

/** The players in the order the camera tries them, and the keys 1 to 6 choose them. */
val ORDER: List<Player> = listOf(Player.Drums, Player.Guitar1, Player.Eimer, Player.Bass, Player.Guitar2, Player.Marimba)

/**
 * The camera's choreography under [show]: a bar is its cycle, and every seed gives another show (who is looked at,
 * from where, in which order, when, and how it comes back).
 *
 * [rigs]: the players' figures. [activity]: the times a player plays a note, sorted, as far as the song is known
 * (asked at every look). [force]: makes every round that kind (to see one; else the seed decides).
 */
class Choreography(
    show: Show,
    private val rigs: Map<Player, Rig>,
    private val activity: (Player) -> List<Double>,
    private val force: RoundKind? = null,
) {
    private val seed: Int = show.seed
    private val cycle: Double = show.bar
    private val round: Double = ROUND * cycle

    /** The song starts after the count-in; the rounds begin up to twelve bars later, by the seed. */
    val start: Double = (2 + floor(chance(n = 0, i = 0, salt = 9) * 13)) * cycle

    /** What the camera did at the last [at], in words. */
    var label: String = "total"
        private set

    /** A number from 0 to 1 that belongs to the round [n], the look [i] and a purpose [salt], under this seed. */
    private fun chance(n: Int, i: Int, salt: Int): Double {
        return hash01(n * 127.1 + i * 311.7 + salt * 74.7 + seed * 0.618)
    }

    /** How many notes [player] plays between [a] and [b]. */
    private fun plays(player: Player, a: Double, b: Double): Int {
        var n = 0

        for (t in activity(player)) {
            if (t >= a && t < b) {
                n++
            } else if (t >= b) {
                break
            }
        }

        return n
    }

    /** The moment the camera was last asked for ([at]). */
    private var heard: Double = Double.NEGATIVE_INFINITY

    /** The looks that have begun, by round and number: what they show stays, whatever the song forgets or learns. */
    private val begun = mutableMapOf<Int, Subject>()

    /**
     * What the look number [i] of round [n] shows (0, 1, 2 the late three, 3 and 4 the early two): a player who plays
     * then, or the stage from another angle.
     * It is chosen from the notes known, and kept once the look has begun: a song played live forgets those notes
     * while the round still asks for its looks (the way back from the last look goes on long after the first).
     */
    fun subject(n: Int, i: Int): Subject {
        val key = n * LOOKS_A_ROUND + i

        return begun[key] ?: choose(n, i).also {
            if (heard >= start + n * round + opens(i) * cycle) {
                begun[key] = it
            }
        }
    }

    private fun choose(n: Int, i: Int): Subject {
        val a = start + n * round + opens(i) * cycle
        val b = a + 3 * cycle
        // the first of its row of looks: nobody was shown right before it
        val leads = i == 0 || i == 3

        fun <T> pick(list: List<T>, salt: Int): T = list[floor(chance(n, i, salt) * list.size).toInt() % list.size]

        // about one look in five is the stage from another angle
        if (chance(n, i, 1) < 0.2) {
            return Subject.Angle(pick(ANGLES, 2))
        }

        // the marimba and the trumpet play seldom: when one of them does, the round's first look is theirs
        val rare = listOf(Player.Marimba, Player.Eimer).filter { plays(it, a, b) > 2 }
        var player: Player? = if (rare.isNotEmpty() && i == 0) pick(rare, 3) else null
        // else someone who plays just then, and not the one the look before showed
        val before = if (!leads) subject(n, i - 1).player else null
        val first = floor(chance(n, i, 4) * ORDER.size).toInt()

        for (k in ORDER.indices) {
            if (player != null) {
                break
            }

            val candidate = ORDER[(first + k) % ORDER.size]

            if (candidate != before && plays(candidate, a, b) > 3) {
                player = candidate
            }
        }

        if (player == null) {
            return Subject.Angle(pick(ANGLES, 2))
        }

        return Subject.OnPlayer(player, pick(looksOf(player), 5))
    }

    /**
     * What kind the round [n] is: about a fifth each are a circle, an arc and a flight, the rest looks. An arc needs
     * a player who stays at their place (the frontwoman strolls and turns: the camera would swing with her).
     */
    fun kind(n: Int): RoundKind {
        val kind = force ?: when {
            chance(n, 0, 7) < 0.2 -> RoundKind.Circle
            chance(n, 0, 7) < 0.4 -> RoundKind.Arc
            chance(n, 0, 7) < 0.6 -> RoundKind.Flight
            else -> RoundKind.Looks
        }
        val player = subject(n, 0).player

        return if (kind == RoundKind.Arc && (player == null || player == Player.Eimer)) RoundKind.Looks else kind
    }

    /**
     * The flight round the whole band, [u] of the way (0 to 1): once round, from the start position and back to it.
     * Behind the band it comes closer (the wall is there) and a little higher. [home]: the total as it drifts now.
     */
    private fun circle(n: Int, u: Double, home: Shot): Shot {
        return circleBy(way = if (chance(n, 0, 8) < 0.5) 1 else -1, u = u, home = home)
    }

    /** The flight round the band, [u] of the way, to the left ([way] 1) or to the right (-1). */
    fun circleBy(way: Int, u: Double, home: Shot): Shot {
        val angle = way * 2 * PI * u
        val far = 8.1 + 1.6 * cos(angle)
        val pos = vec(8.5 * sin(angle), HOME.pos.y + 0.45 * (1 - cos(angle)), -0.3 + far * cos(angle))

        return Shot(pos = pos.add(home.pos).sub(HOME.pos), at = home.at, fov = home.fov)
    }

    /**
     * The camera [share] of the way from the total [home] (0) to the mixing desk far back in the audience (1), at
     * [t]: backward and upward over the audience's lights in a straight line, the look sinking from the band to the
     * ground before the stage, the lens closing. At the desk it is never quite still either.
     */
    private fun flight(share: Double, home: Shot, t: Double): Shot {
        val desk = vec(STADIUM.pos).add(vec(0.30 * sin(t * 0.06), 0.10 * sin(t * 0.05 + 1), 0.0))

        return Shot(
            pos = home.pos.clone().lerp(desk, share),
            at = home.at.clone().lerp(vec(STADIUM.at), share),
            fov = home.fov + (STADIUM.fov - home.fov) * share,
        )
    }

    /** The slow way round the player of round [n], [u] of it (0 to 1): across their front, 43 degrees to each side. */
    private fun arc(n: Int, u: Double): Shot {
        val player = checkNotNull(subject(n, 0).player) { "An arc round $n without a player" }

        return arcBy(player = player, way = if (chance(n, 0, 8) < 0.5) 1 else -1, u = u)
    }

    /** The way round [player], [u] of it, from one side ([way] 1) or the other (-1). */
    fun arcBy(player: Player, way: Int, u: Double): Shot {
        val a = ARCS[player] ?: STANDING_ARC
        val angle = way * 1.5 * (u - 0.5)
        val root = rigs.getValue(player).root

        root.updateWorldMatrix(updateParents = true, updateChildren = false)

        return Shot(
            pos = root.localToWorld(vec(a.far * sin(angle), a.high, a.far * cos(angle))),
            at = root.localToWorld(vec(a.at)),
            fov = a.fov,
        )
    }

    /** A look as the camera's place, target and lens, in the world. */
    fun shot(subject: Subject): Shot {
        return when (subject) {
            is Subject.Angle -> Shot(pos = vec(subject.angle.pos), at = vec(subject.angle.at), fov = subject.angle.fov)

            is Subject.OnPlayer -> {
                val root = rigs.getValue(subject.player).root

                root.updateWorldMatrix(updateParents = true, updateChildren = false)

                Shot(
                    pos = root.localToWorld(vec(subject.look.pos)),
                    at = root.localToWorld(vec(subject.look.at)),
                    fov = subject.look.fov,
                )
            }
        }
    }

    /**
     * A look's slow move, [u] of the way through it (0 to 1): a push in, and a slide to one side (the other side on
     * the next look) of about a twentieth of its distance. Returns that distance, as it was before the move.
     */
    private fun slide(shot: Shot, i: Int, u: Double): Double {
        val far = shot.pos.distanceTo(shot.at)
        val side = vec().subVectors(shot.at, shot.pos).cross(Y_AXIS).normalize()

        shot.pos.lerp(shot.at, 0.03 * u)
        shot.pos.addScaledVector(side, (if (i % 2 == 0) 1 else -1) * 0.05 * far * (u - 0.5))

        return far
    }

    /** The total at [t]. It breathes: a slow drift, never still. */
    fun home(t: Double): Shot {
        return Shot(
            pos = HOME.pos.clone().add(vec(0.15 * sin(t * 0.07), 0.04 * sin(t * 0.05 + 1), 0.12 * sin(t * 0.04))),
            at = HOME.at.clone(),
            fov = HOME.fov,
        )
    }

    /**
     * The way back from [shot] to the total [home] by a dolly zoom, [u] of it (0 to 1, the pace already in it): the
     * camera travels to the start; the look turns from the subject to the stage and the lens opens to the wide view,
     * both slowly at first, so the subject keeps much of its size while the stage grows round it. One pace drives
     * all three: nothing surges.
     */
    fun back(shot: Shot, home: Shot, u: Double): Shot {
        val pos = shot.pos.clone().lerp(home.pos, u)
        val held = 2 * atan(shot.pos.distanceTo(shot.at) * tan(shot.fov * PI / 360) / pos.distanceTo(shot.at)) * 180 / PI

        return Shot(pos = pos, at = shot.at.clone().lerp(home.at, u * u), fov = held + (home.fov - held) * u * u)
    }

    /** The camera at [t]. */
    fun at(t: Double): Shot {
        val home = home(t)

        label = "total"

        if (t < start) {
            return home
        }

        val n = floor((t - start) / round).toInt()
        val x = (t - start - n * round) / cycle           // 0 to 48, in bars

        heard = t
        // (the looks of the rounds before the last are of no use any more)
        begun.keys.removeAll { it < (n - 1) * LOOKS_A_ROUND }
        val roundKind = kind(n)

        if (roundKind == RoundKind.Circle) {
            if (x < CIRCLE) {
                return home
            }

            label = "circle round the band"

            return circle(n, smoother((x - CIRCLE) / (ROUND - CIRCLE)), home)
        }

        if (roundKind == RoundKind.Flight) {
            val way = Flight.way(chance(n, 0, 10))
            val share = Flight.share(way, x)

            label = when {
                share <= 0.0 -> "total"
                share >= 1.0 -> "from the mixing desk"
                x < Flight.IN -> "flight out over the audience"
                else -> "flight back in over the audience"
            }

            return if (share <= 0.0) home else flight(share = share, home = home, t = t)
        }

        // two looks in a row in the round's first half
        if (x >= EARLY && x < opens(4) + 3) {
            val i = if (x < opens(4)) 3 else 4
            val seen = subject(n, i)
            val early = this.shot(seen)
            val far = slide(early, i, (x - opens(i)) / 3)

            label = "look ${i - 2} of 2: ${seen.words}"
            early.pos.y += 0.008 * far * sin(t * 0.8 + i)

            return early
        }

        val dolly = chance(n, 0, 6) < 0.34

        if (x < FIRST || (x >= BACK && !dolly)) {
            return home
        }

        val shot: Shot

        if (roundKind == RoundKind.Arc) {
            shot = arc(n, min(1.0, (x - FIRST) / (BACK - FIRST)))
            label = "arc round ${subject(n, 0).player?.key}"

            if (x < BACK) {
                return shot
            }
        } else {
            val i = min(2, floor((x - FIRST) / 3).toInt())
            val seen = subject(n, i)

            shot = this.shot(seen)
            label = "look ${i + 1} of 3: ${seen.words}"

            if (x < BACK) {
                // the look itself is never quite still: a very slow push in, a slow slide to one side (the other side
                // on the next look) of about a twentieth of its distance, and a faint float up and down
                val far = slide(shot, i, (x - FIRST - 3 * i) / 3)

                shot.pos.y += 0.008 * far * sin(t * 0.8 + i)

                return shot
            }

            // the way back starts where the last look's slide ended
            slide(shot, i, 1.0)
        }

        // back by a dolly zoom over five bars, leaving and arriving very softly
        label = "dolly back to the total"

        return back(shot, home, smoother((x - BACK) / (ROUND - BACK)))
    }
}
