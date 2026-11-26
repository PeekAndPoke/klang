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
import io.peekandpoke.klang.viz.liveact.smoother
import io.peekandpoke.klang.viz.liveact.theatre.Sky
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
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

// The camera's choreography. Mostly the total: the wide view from the audience, the start position. The song is cut
// into rounds of 48 cycles (bars, about 79 s), and a round is one of six kinds, by the seed (none twice in three rounds):
//   looks   34 bars on the total, then three looks in a row, three bars each, with a cut between them (close on one
//           player, or the stage from another angle)
//   arc     the same, but the nine bars are one look: the camera goes slowly part of the way round one player
//   circle  16 bars on the total, then one slow flight right round the whole band, from the start position and
//           back to it, with no cut at all
//   flight  20 bars on the total, then out backward over the audience to the mixing desk far back (the FOH), a
//           while there, and back in; one of the two ways may be a cut instead (see Flight)
//   reverse 32 bars on the total, then twelve bars on the reverse total: from behind the drummer, over his
//           shoulder, the band from the back and the audience before it; as from a hovering drone, almost steady
//   crowd   30 bars on the total, then 14 bars slowly along the back of the crowd, high over their heads, looking
//           at the stage; cut to and cut away from (see CrowdArc)
// A round of looks, with an arc, with the reverse total or along the crowd also has two looks in a row in its first
// half (bars 12 to 18, back to the total by a plain cut), so such a round never holds the total longer than some 17
// bars (the maintainer, 2026-11-17: "could use more camera cuts and movements. not too much but a little more").
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

/** How far the total breathes from [HOME] at most (see `home`): across, up and down, and toward the stage and back. */
const val TOTAL_DRIFT_X = 0.15
const val TOTAL_DRIFT_Y = 0.04
const val TOTAL_DRIFT_Z = 0.12

/**
 * The looks at every player of a band that stands at [homes]: [looksOf], each camera moved where it would stand in
 * another player or their instrument, so that it is [CLEAR] m from where every other one stands (see [clearLook]).
 * A look from above the players' heads passes over them and stays as it is set.
 */
fun looksOn(homes: Map<Player, Place>): Map<Player, List<Placement>> = homes.mapValues { (player, home) ->
    val others = homes.filterKeys { it != player }.values.map { Ground(x = it.x, z = it.z) }

    // (one who sits on the floor is looked at as one who sits, whoever they are)
    (if (home.sits) SITTING_LOOKS else looksOf(player)).map { look ->
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

/** How far the line from an arc's camera to its player stays from where every other one stands, in metres. */
const val SIGHT = 0.6

/**
 * Whether the arc round [player] has room on the stage as the band is placed ([homes]): all along it the camera is
 * [CLEAR] of where every other one stands, and its line of sight [SIGHT] of them. The arcs are set for Der
 * Schmetterling's places; in another song's places (Kokon's close half circle) one may pass through a neighbour, and
 * the rounds give that player no arc then (`Rounds.arcless`).
 */
fun arcHasRoom(player: Player, homes: Map<Player, Place>): Boolean {
    val home = homes[player] ?: return false
    val arc = arcOf(player, sits = home.sits)

    // (a place in the player's space, on the stage)
    fun onStage(x: Double, z: Double) =
        Ground(x = home.x + x * cos(home.turn) + z * sin(home.turn), z = home.z - x * sin(home.turn) + z * cos(home.turn))

    val target = onStage(x = arc.at.first, z = arc.at.third)

    return (0..ARC_STEPS).all { step ->
        val angle = arc.angle(way = 1, u = step / ARC_STEPS.toDouble())
        val camera = onStage(x = arc.far * sin(angle), z = arc.far * cos(angle))
        val dx = target.x - camera.x
        val dz = target.z - camera.z

        homes.all { (other, place) ->
            // the nearest point to the other one on the line from the camera to what it looks at
            val along = (((place.x - camera.x) * dx + (place.z - camera.z) * dz) / (dx * dx + dz * dz)).coerceIn(0.0, 1.0)

            other == player ||
                (hypot(camera.x - place.x, camera.z - place.z) > CLEAR && hypot(camera.x + along * dx - place.x, camera.z + along * dz - place.z) > SIGHT)
        }
    }
}

/** At how many steps along an arc its room is looked at ([arcHasRoom]). */
private const val ARC_STEPS = 100

/**
 * The look at the moon from behind the band: how far along the way to the moon the look's aim lies (metres: about
 * as far as the band is), how much of the way to it the look rises, and the lens there.
 */
private const val MOON_REACH = 7.0
private const val MOON_SHARE = 0.62
private const val MOON_FOV = 30.0

/** The camera takes off before the one who strolls at most once in this many seconds: it stays a moment to wait for. */
private const val RETREAT_REST = 150.0

/** From this height on a camera is above the players' heads and passes over them. */
private const val OVERHEAD = 2.1

/**
 * The camera's choreography under [show]: a bar is its cycle, and every seed gives another show (who is looked at,
 * from where, in which order, when, and how it comes back).
 *
 * [rigs]: the players' figures. [looks]: the looks at each player (see [looksOn]). [activity]: the times a player plays a note, sorted, as far as the song is known
 * (asked at every look). [seldom]: the players who play seldom, and [strolls]: the one who strolls (both the cast's,
 * see [Rounds]). [force]: makes every round that kind (to see one; else the seed decides).
 */
class Choreography(
    show: Show,
    private val rigs: Map<Player, Rig>,
    looks: Map<Player, List<Placement>>,
    private val activity: (Player) -> List<Double>,
    seldom: List<Player>,
    strolls: Player?,
    force: RoundKind? = null,
    /** Who sits on the floor at their place (the stage plot's): the camera goes round them lower. */
    private val sitting: Set<Player> = emptySet(),
    /** The moment on the show's clock at which the song begins (see [Rounds]): the rounds are counted from there. */
    after: Double = 0.0,
    /** Whose arc has no room in this song's places ([arcHasRoom]): the rounds give them none. */
    arcless: Set<Player> = emptySet(),
) {
    private val cycle: Double = show.bar

    /** The rounds, and what the choice of a look goes by: the players who play seldom, and who strolls. */
    private val rounds = Rounds(
        seed = show.seed,
        bar = cycle,
        looks = looks,
        seldom = seldom,
        strolls = strolls,
        force = force,
        after = after,
        arcless = arcless,
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

    /** How far the total stands further back than usual, in metres: set from frame to frame (the ghosts). */
    var back: Double = 0.0
        set(value) {
            // NaN-guard: what is no number moves nothing
            field = if (value != value) 0.0 else value.coerceIn(0.0, 12.0)
        }

    /** The way of the one who strolls toward the camera, from frame to frame (see [Approaching]). */
    private var approach: Approach? = null

    /** When the camera last took off before her (see [retreat]); long ago where it never has. */
    private var retreated: Double = Double.NEGATIVE_INFINITY

    /**
     * When it took off before her within the last two rounds, the earliest first: a round whose flight would leave
     * within a round after one of them is no flight ([kind], KLANG-00489).
     */
    private val retreats: MutableList<Double> = mutableListOf()

    /** Whether it is back from that flight and waits on the total for the round to come there too. */
    private var landed: Boolean = false

    /** The looks that have begun (see [chooseLook]), carried on from one question to the next. */
    private var plan: LookPlan = LookPlan()

    /** What the look number [i] of round [n] shows (see [chooseLook]). */
    private fun subject(n: Int, i: Int): Subject {
        val chosen = chooseLook(rounds = rounds, n = n, i = i, now = heard, playsAt = activity, before = plan)

        plan = chosen.plan

        return chosen.what
    }

    /**
     * What kind the round [n] is (see [chooseKind]): no flight where the camera took off before her within a round
     * before the round's flight would leave (KLANG-00489). A take-off later than that, while the round goes on,
     * changes nothing: the round keeps its kind.
     */
    private fun kind(n: Int): RoundKind {
        val flown = flownBefore(rounds = rounds, n = n, retreats = retreats)
        val chosen = chooseKind(rounds = rounds, n = n, now = heard, playsAt = activity, before = plan, flown = flown)

        plan = chosen.plan

        return chosen.what
    }

    /**
     * The flight round the whole band at [u] of its time (0 to 1): once round, from the start position and back to it.
     * Behind the band it comes closer (the wall is there) and a little higher. [home]: the total as it drifts now.
     */
    private fun circle(n: Int, u: Double, home: Shot): Shot {
        return circleBy(way = if (chance(n, 0, 8) < 0.5) 1 else -1, u = u, home = home)
    }

    /**
     * The flight round the band at [u] of its time (0 to 1), to the left ([way] 1) or to the right (-1). Half way,
     * behind the band, it rests and looks up at the moon over the audience (see [MoonStop]).
     */
    fun circleBy(way: Int, u: Double, home: Shot): Shot {
        val angle = way * 2 * PI * MoonStop.travel(u)
        val far = 8.1 + 1.6 * cos(angle)
        val pos = vec(8.5 * sin(angle), HOME.pos.y + 0.45 * (1 - cos(angle)), -0.3 + far * cos(angle))
            .add(home.pos)
            .sub(HOME.pos)

        // it goes round the band, not round the total: where the total stands further back (the ghosts), the flight
        // leaves from there and comes back to there, and is on its own circle behind the band
        pos.z -= back * (1 - cos(angle)) / 2

        // the look rises most of the way to the moon, so the band's heads stay in the picture below it
        val moon = MoonStop.look(u)
        val sky = pos.clone().addScaledVector(vec(Sky.MOON.x, Sky.MOON.y, Sky.MOON.z), MOON_REACH)

        return Shot(pos = pos, at = home.at.clone().lerp(sky, moon * MOON_SHARE), fov = home.fov + (MOON_FOV - home.fov) * moon)
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

    /** The way along the back of the crowd of round [n], [u] of it (0 to 1): where it begins and ends, by the seed. */
    private fun crowdArc(n: Int, u: Double): Placement =
        CrowdArc.at(way = CrowdArc.way(side = chance(n, 0, 12), length = chance(n, 0, 13), high = chance(n, 0, 14)), u = u)

    /** The slow way round the player of round [n], [u] of it (0 to 1): the part of the round set for them (see [arcOf]). */
    private fun arc(n: Int, u: Double): Shot {
        val player = checkNotNull(subject(n, 0).player) { "An arc round $n without a player" }

        return arcBy(player = player, way = if (chance(n, 0, 8) < 0.5) 1 else -1, u = u)
    }

    /** The way round [player], [u] of it, from one side ([way] 1) or the other (-1). */
    fun arcBy(player: Player, way: Int, u: Double): Shot {
        val a = arcOf(player, sits = player in sitting)
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

    /** The total at [t]. It breathes: a slow drift, never still. It stands [back] metres further back than usual. */
    fun home(t: Double): Shot {
        return Shot(
            pos = HOME.pos.clone().add(
                vec(TOTAL_DRIFT_X * sin(t * 0.07), TOTAL_DRIFT_Y * sin(t * 0.05 + 1), TOTAL_DRIFT_Z * sin(t * 0.04) + back),
            ),
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

        // the time went back: nothing of it has happened yet (before the round is asked, which reads the take-offs)
        if (t < retreated) {
            retreated = Double.NEGATIVE_INFINITY
            landed = false
        }

        retreats.removeAll { it > t || it < t - 2 * round }

        return retreat(t = t, home = home, planned = planned(t = t, home = home))
    }

    /**
     * The camera at [t] with its answer to the one who strolls: where she comes walking straight at the camera while
     * it is on the total ([home], and [planned] is it), it takes off backward over the audience before her, stays at
     * the mixing desk a while and flies back in, as a round with a flight does (see [Flight]); whatever the round
     * had planned meanwhile is left out, and back on the total it waits there until the round is on it too. Not more often than every [RETREAT_REST] seconds, and not where every round
     * is made one kind (to see that kind alone).
     */
    private fun retreat(t: Double, home: Shot, planned: Shot): Shot {
        val her = rounds.strolls?.let { rigs[it] }?.root?.position

        approach = her?.let { Approaching.next(before = approach, t = t, x = it.x, z = it.z, camX = home.pos.x, camZ = home.pos.z) }

        val bars = (t - retreated) / cycle
        val length = Flight.END - Flight.OUT

        if (bars >= length) {
            // back from a flight, it stays on the total until the round is there too: it never cuts into the middle
            // of what the round had planned meanwhile (the flight round the band, a flight of the round's own)
            if (landed && planned !== home) {
                label = "total"

                return home
            }

            landed = false

            val begins = rounds.force == null && t >= start && planned === home && t - retreated >= RETREAT_REST &&
                approach?.arrived == true && !roundFlewBefore(rounds = rounds, t = t, isFlight = { kind(it) == RoundKind.Flight })

            if (begins) {
                retreated = t
                retreats.add(t)
            }

            return planned
        }

        // in the flight (also where the time was moved back into it): it lands on the total and waits there
        landed = true

        val share = Flight.share(way = FlightWay.OutAndBack, x = Flight.OUT + bars)

        label = when {
            share >= 1.0 -> "from the mixing desk"
            bars < Flight.IN - Flight.OUT -> "flight out before the frontwoman"
            else -> "flight back in over the audience"
        }

        return flight(share = share, home = home, t = t)
    }

    /** The camera at [t] as the rounds plan it; [home]: the total as it drifts now (returned itself where it is the total). */
    private fun planned(t: Double, home: Shot): Shot {
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

            return circle(n, (x - CIRCLE) / (ROUND - CIRCLE), home)
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

        if (roundKind == RoundKind.Crowd) {
            if (x < CrowdArc.FROM || x >= CrowdArc.TO) {
                return home
            }

            label = "along the back of the crowd"

            return shot(Subject.Angle(crowdArc(n, (x - CrowdArc.FROM) / (CrowdArc.TO - CrowdArc.FROM))))
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
        val strolls = roundKind != RoundKind.Arc && rounds.strolls != null && subject(n, 2).player == rounds.strolls
        val behind = roundKind == RoundKind.Arc && subject(n, 0).player?.let { arcOf(it, sits = it in sitting).cutsBack } == true

        if (away(shot) || strolls || behind) {
            label = "total"

            return home
        }

        // back by a dolly zoom over five bars, leaving and arriving very softly
        label = "dolly back to the total"

        return back(shot, home, smoother((x - BACK) / (ROUND - BACK)))
    }
}
