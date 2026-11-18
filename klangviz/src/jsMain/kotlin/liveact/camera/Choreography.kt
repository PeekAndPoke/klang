/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.camera

import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.Y_AXIS
import io.peekandpoke.klang.viz.liveact.band.Place
import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.figure.Rig
import io.peekandpoke.klang.viz.liveact.refactorme.SchmetterlingCast
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
// into rounds of 48 cycles (bars, about 79 s), and a round is one of five kinds, by the seed:
//   looks   34 bars on the total, then three looks in a row, three bars each, with a cut between them (close on one
//           player, or the stage from another angle)
//   arc     the same, but the nine bars are one look: the camera goes slowly part of the way round one player
//   circle  24 bars on the total, then one slow flight right round the whole band, from the start position and
//           back to it, with no cut at all
//   flight  20 bars on the total, then out backward over the audience to the mixing desk far back (the FOH), a
//           while there, and back in; one of the two ways may be a cut instead (see Flight)
//   reverse 32 bars on the total, then twelve bars on the reverse total: from behind the drummer, over his
//           shoulder, the band from the back and the audience before it; as from a hovering drone, almost steady
// A round of looks, with an arc or with the reverse total also has two looks in a row in its first half (bars 12 to
// 18, back to the total by a plain cut), so such a round never holds the total longer than some 17 bars (the
// maintainer, 2026-11-17: "could use more camera cuts and movements. not too much but a little more").
// From the late looks or an arc it comes back to the total by a plain cut in two rounds of three, and in the third
// by a slow dolly zoom over five bars (the camera travels back while its lens opens, so the player keeps much of
// their size at first as the stage grows round them). Always by a cut from a look that looks away from the total
// (the drummer over his shoulder), and from the frontwoman, who may stroll off meanwhile.
// Calm on purpose (the maintainer, 2026-11-16: fast moves "make dizzy"; "do not cut too often to the people, but
// maybe multiple in a row"; "the camera must mostly show the total"; "cut back to the total without camera
// movement, not always though").
// Everything follows from the time alone, so the same moment always has the same picture.

/** The camera in the world: where it is ([pos]), what it looks at ([at]), its lens ([fov], degrees). */
class Shot(val pos: Vector3, val at: Vector3, val fov: Double)

/** The total, the start position. Never change it: copy first. */
val HOME: Shot by lazy { Shot(pos = vec(TOTAL_FROM.x, 1.85, TOTAL_FROM.z), at = vec(0.0, 1.0, 0.0), fov = 35.0) }

/**
 * The looks at every player of a band that stands at [homes]: [looksOf], each camera moved where it would stand in
 * another player or their instrument, so that it is [CLEAR] m from where every other one stands (see [clearLook]).
 * A look from above the players' heads passes over them and stays as it is set.
 */
fun looksOn(homes: Map<Player, Place>): Map<Player, List<Placement>> = homes.mapValues { (player, home) ->
    val others = homes.filterKeys { it != player }.values.map { Ground(x = it.x, z = it.z) }

    looksOf(player).map { look ->
        when {
            look.pos.second + home.y >= OVERHEAD -> look

            else -> {
                val moved = clearLook(
                    camera = Ground(x = look.pos.first, z = look.pos.third),
                    target = Ground(x = look.at.first, z = look.at.third),
                    home = Standing(at = Ground(x = home.x, z = home.z), turn = home.turn),
                    others = others,
                    clear = CLEAR,
                )

                look.copy(pos = Triple(moved.x, look.pos.second, moved.z))
            }
        }
    }
}

/** How far a camera that looks at one player stays from where every other one stands, in metres: clear of a guitar's neck and head. */
const val CLEAR = 1.3

/** From this height on a camera is above the players' heads and passes over them. */
private const val OVERHEAD = 2.1

/**
 * The camera's choreography under [show]: a bar is its cycle, and every seed gives another show (who is looked at,
 * from where, in which order, when, and how it comes back).
 *
 * [rigs]: the players' figures. [looks]: the looks at each player (see [looksOn]). [activity]: the times a player plays a note, sorted, as far as the song is known
 * (asked at every look). [force]: makes every round that kind (to see one; else the seed decides).
 */
class Choreography(
    show: Show,
    private val rigs: Map<Player, Rig>,
    looks: Map<Player, List<Placement>>,
    private val activity: (Player) -> List<Double>,
    force: RoundKind? = null,
) {
    private val cycle: Double = show.bar

    /** The rounds, and what the choice of a look goes by: the cast's two players who play seldom, and who strolls. */
    private val rounds = Rounds(
        seed = show.seed,
        bar = cycle,
        looks = looks,
        // (into refactorme: that they play seldom is the song's arrangement, who plays them is its cast; the one
        // who strolls is the trumpet player)
        seldom = listOf(SchmetterlingCast.MARIMBA, SchmetterlingCast.TRUMPET),
        strolls = SchmetterlingCast.TRUMPET,
        force = force,
    )
    private val round: Double = rounds.length

    /** The song starts after the count-in; the rounds begin up to twelve bars later, by the seed. */
    val start: Double = rounds.start

    /** What the camera did at the last [at], in words. */
    var label: String = "total"
        private set

    /** A number from 0 to 1 that belongs to the round [n], the look [i] and a purpose [salt], under this seed. */
    private fun chance(n: Int, i: Int, salt: Int): Double = rounds.chance(n = n, i = i, salt = salt)

    /** The moment the camera was last asked for ([at]). */
    private var heard: Double = Double.NEGATIVE_INFINITY

    /** The looks that have begun (see [chooseLook]), carried on from one question to the next. */
    private var plan: LookPlan = LookPlan()

    /** What the look number [i] of round [n] shows (see [chooseLook]). */
    private fun subject(n: Int, i: Int): Subject {
        val chosen = chooseLook(rounds = rounds, n = n, i = i, now = heard, playsAt = activity, before = plan)

        plan = chosen.plan

        return chosen.what
    }

    /** What kind the round [n] is (see [chooseKind]). */
    private fun kind(n: Int): RoundKind {
        val chosen = chooseKind(rounds = rounds, n = n, now = heard, playsAt = activity, before = plan)

        plan = chosen.plan

        return chosen.what
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

    /**
     * The reverse total at [t], as from a drone that hovers: the camera's place drifts by a centimetre or so and
     * its look by a few centimetres some 17 m away, very slowly (see [hover]).
     */
    fun reverse(t: Double): Shot {
        val shot = shot(Subject.Angle(REVERSE))

        shot.pos.x += 0.015 * hover(t = t, phase = 0.0)
        shot.pos.y += 0.010 * hover(t = t, phase = 1.7)
        shot.at.x += 0.03 * hover(t = t, phase = 0.9)
        shot.at.y += 0.02 * hover(t = t, phase = 4.0)

        return shot
    }

    /** The slow way round the player of round [n], [u] of it (0 to 1): the part of the round set for them (see [arcOf]). */
    private fun arc(n: Int, u: Double): Shot {
        val player = checkNotNull(subject(n, 0).player) { "An arc round $n without a player" }

        return arcBy(player = player, way = if (chance(n, 0, 8) < 0.5) 1 else -1, u = u)
    }

    /** The way round [player], [u] of it, from one side ([way] 1) or the other (-1). */
    fun arcBy(player: Player, way: Int, u: Double): Shot {
        val a = arcOf(player)
        val angle = a.angle(way = way, u = u)
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

    /** Whether [shot] looks away from the total (see [looksAway]): the way back from it is a cut. */
    fun away(shot: Shot): Boolean = looksAway(
        aheadX = shot.at.x - shot.pos.x,
        aheadZ = shot.at.z - shot.pos.z,
        totalX = HOME.at.x - HOME.pos.x,
        totalZ = HOME.at.z - HOME.pos.z,
    )

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
        plan = plan.inRound(n)
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

        if (roundKind == RoundKind.Reverse) {
            if (x < REVERSE_FROM || x >= REVERSE_TO) {
                return home
            }

            label = "reverse total, over the drummer's shoulder"

            return reverse(t)
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

        // a look that looks away from the total (over the drummer's shoulder): the dolly would swing the camera right
        // round, so it cuts. And from the frontwoman, who may stroll off and turn while the camera travels: the way
        // back hangs on her place, and would jump. And from an arc that ends behind someone (see `Orbit.cutsBack`)
        val strolls = roundKind != RoundKind.Arc && subject(n, 2).player == rounds.strolls
        val behind = roundKind == RoundKind.Arc && subject(n, 0).player?.let { arcOf(it).cutsBack } == true

        if (away(shot) || strolls || behind) {
            label = "total"

            return home
        }

        // back by a dolly zoom over five bars, leaving and arriving very softly
        label = "dolly back to the total"

        return back(shot, home, smoother((x - BACK) / (ROUND - BACK)))
    }
}
