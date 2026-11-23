/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.lights

import io.peekandpoke.klang.viz.liveact.stage.GhostLight
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.tan

/**
 * What the time of day of the two worlds ([WorldTime]) does to the light (KLANG-00478, to be judged by eye; the
 * critic's "day and night as two stage states, plus ember", the maintainer, 2026-11-19: "The two worlds shots are
 * amazing"). Each value has four ends, the day, the night, the ember and the ghosts, and between them it is blended
 * evenly: from the day to the night by [WorldTime.night], from there to the ember by [WorldTime.ember], from there to
 * the ghosts by [WorldTime.ghosts]; and the whole from the stage as it was made by [WorldTime.on]. At [WorldTime.on] 0
 * every value is the one the stage was made with, so the time of day off changes nothing.
 *
 * - **The sun behind the drummer:** by day big, low and a rich amber, strong enough to spill gold through the mesh
 *   and the haze; by night a small cold white moon, higher up; at ember a deep red furnace, low.
 * - **The haze:** warm and lit by day, cold and thinner by night, red smoke at ember.
 * - **The low front lamp:** by day weaker and warmer, its shadows soft, so the true selves are faint; by night and at
 *   ember at full, large and hard, a little colder by night and redder at ember. Its own colours and moods stay: the
 *   time scales them and leans them.
 * - **The lamps behind and the fill:** they lean toward the time's colour, a little dimmer by night.
 * - **The lacquer and the gold:** by day the coat glossy and the gold rich; by night the coat duller and the gold
 *   pale, cold highlights only; at ember red. What the lacquer mirrors of the sun follows the sun.
 * - **The walls:** by night the light on the wall, the fence and the surface behind leans cold and is dimmed
 *   ([wallCold], [wallLight]), so the windows into the cold world glow against a dark wall; by day and at ember as
 *   made.
 * - **The cold world** behind the fence, seen through the band's shadows: none by day, all of it by night and at
 *   ember ([beyond]).
 * - **The ghosts** ([GhostLight] gives the colour): the stage dark. The front lamp and the fill give no light (the
 *   front lamp's shadow is still drawn: the figures are made of it), the lamps behind a trace, in the colour's rim, so
 *   the band's edges just show and the floor carries no pools of their light; the sun gone; the lacquer mirrors little
 *   and only the glow behind. All the light is in the haze behind the fence, which glows in the colour where no lamp
 *   reaches ([hazeBase]); the band's shadows stay open in it all the time, pale figures, the brightest of the picture
 *   ([ghostShare], [ghostPale]), seen through a veil of the haze ([ghostClearing]), before the colour's deep
 *   ([ghostDepth]); the light on the surfaces behind is dimmed.
 */
object WorldLight {
    /**
     * How far the front lamp creeps toward the band in the ghosts, in metres (it stays well before where the trumpet
     * player strolls), in how many seconds it comes that far (a visit's length), and in how many it goes back (the
     * stage's turn back from the ghosts). It was 2.4 m in the same time; faster since the camera goes back with it
     * ([ghostPull]; the maintainer, 2026-11-22: "if you do make the ghost approach faster").
     */
    const val GHOST_APPROACH = 3.0
    const val GHOST_APPROACH_SECONDS = GhostVisits.HOLD

    /** The lamp as shown follows its creep softly: about two thirds of what is left in this many seconds ([ghostEase]). */
    const val GHOST_EASE_SECONDS = 1.2

    /** The widest the front lamp's cone opens for the ghosts, half its opening in radians ([ghostCone]). */
    const val GHOST_CONE_MOST = 1.2
    const val GHOST_RETREAT_SECONDS = GhostVisits.TURN_SECONDS

    /** How long a round of the drift takes, in seconds: day to night and back in about four minutes. */
    const val DRIFT_PERIOD = 240.0

    /** The sun's colour by day: a rich amber. */
    const val DAY_SUN = 0xf5a050

    /** The moon's: a cold white. */
    const val MOON = 0xdde6ff

    /** The furnace's at ember: a deep red. */
    const val FURNACE = 0xd8240a

    /**
     * How bright the ghosts' haze glows where no lamp reaches, as a share of the colour's glow ([GhostLight.glow],
     * [hazeBase]): all the light of the picture is there. A first guess for the maintainer's eye.
     */
    const val GHOST_GLOW = 2.4

    /**
     * How far the haze parts before the figures in the ghosts ([ghostClearing]): a little, so the haze lies over the
     * figures much as over the surface around them, and they stand in it as light in a haze (0.6 at first, which
     * left them clear and flat against the haze).
     */
    const val GHOST_CLEARING = 0.3

    /**
     * How bright the figures in the ghosts' haze are, as a share of the colour's pale ([GhostLight.pale]); the fence
     * makes them brightest inside and dimmer at the edge and lower down (0.95 at first, for figures flat and full).
     */
    const val GHOST_PALE = 1.1

    /** How strongly the lacquer mirrors the ghosts' glow behind the band, as a share of the colour's glow ([roomSun]). */
    const val GHOST_ROOM = 0.9

    /** The colour the lamps behind and the fill lean toward: by day warm, by night a cold blue, at ember red. */
    const val DAY_TINT = 0xffb060
    const val NIGHT_TINT = 0x5a78c8
    const val EMBER_TINT = 0xff2a08

    /** The colour the front lamp leans toward: by day a warm, pale amber, by night a cold white, at ember red. */
    const val DAY_FRONT = 0xffd8a0
    const val NIGHT_FRONT = 0xb8ccff
    const val EMBER_FRONT = 0xff3010

    /**
     * The time of day of the drift at the show's time [t] (seconds), one round in [period] seconds: 0 (day) at 0 s,
     * 1 (night) at half the round, day again at its end. The way between follows a cosine and rests three times: at
     * the day, at the night, and half way between them at dusk and at dawn, where the lamps wear their twilight
     * colours ([moodColor]; the maintainer, 2026-11-23: "yes give it a rest, or make the transition slower"; before,
     * it moved fastest just there). A [t] of no number is the day; a [period] of no number, or not above 0, is
     * [DRIFT_PERIOD].
     */
    fun drift(t: Double, period: Double = DRIFT_PERIOD): Double {
        // NaN-guard: no time is the day, no period the period as made
        if (t != t) {
            return 0.0
        }

        val round = if (period.isFinite() && period > 0.0) period else DRIFT_PERIOD
        val wave = 0.5 - 0.5 * cos(2 * PI * t / round)

        // NaN-guard: a time without an end has no place in the round: the day
        if (wave != wave) {
            return 0.0
        }

        // each half of the way eased on its own: from the day to the twilight, from the twilight to the night
        val half = if (wave < 0.5) wave * 2 else wave * 2 - 1
        val eased = half * half * (3 - 2 * half)

        return if (wave < 0.5) 0.5 * eased else 0.5 + 0.5 * eased
    }

    /** The share of its light the sun gives against the stage as made: by day 1.3, by night 0.9, at ember 1.6. */
    fun sunStrength(world: WorldTime): Double = blend(world, made = 1.0, day = 1.3, night = 0.9, ember = 1.6, ghosts = 0.0)

    /** How large the sun's glow is against the stage as made: by day 1.7, by night 0.5 (the moon), at ember 1.1. */
    fun sunSize(world: WorldTime): Double = blend(world, made = 1.0, day = 1.7, night = 0.5, ember = 1.1, ghosts = 0.5)

    /**
     * How high the sun's middle stands on the surface behind the fence, in metres: as made 1.3 (low, the drummer's
     * head before it), by day the same, the moon by night at 2.5 (over his head), the furnace at ember 1.0.
     */
    fun sunHeight(world: WorldTime): Double = blend(world, made = 1.3, day = 1.3, night = 2.5, ember = 1.0, ghosts = 2.5)

    /**
     * How much the front lamp dims the sun against the stage as made ([Sun.share] of the lamp's level times this): by
     * day 0.4, so the sun stays strong under the lamp and the shadows are faint; by night and at ember 1.
     */
    fun sunDimming(world: WorldTime): Double = blend(world, made = 1.0, day = 0.4, night = 1.0, ember = 1.0, ghosts = 1.0)

    /**
     * The sun's colour with the time of day [world]: [made] is its colour as the stage made it (the lamps' mood, the
     * front lamp's lean, [Sun.color]); the time's own colour ([DAY_SUN], [MOON], [FURNACE]) leans toward the front
     * lamp's [front] by up to 0.7 of its [frontLevel] by day, less by night (the moon stays cold), and stands in for
     * [made] as far as [WorldTime.on] goes.
     */
    fun sunColor(world: WorldTime, made: Int, front: Int, frontLevel: Double): Int {
        val own = colorAt(world, day = DAY_SUN, night = MOON, ember = FURNACE, ghosts = world.ghostLight.rim)
        // NaN-guard: a front lamp at no number (?front) leaves the sun its own colour
        val level = if (frontLevel != frontLevel) 0.0 else frontLevel.coerceIn(0.0, 1.0)
        val lean = 0.7 * level * (1.0 - 0.75 * world.night)
        val withLamp = Sun.mix(from = own, to = front, share = lean)

        return Sun.mix(from = made, to = withLamp, share = world.on)
    }

    /** How strongly the front lamp shines against the stage as made: by day 0.5, by night and at ember 1. */
    fun frontScale(world: WorldTime): Double = blend(world, made = 1.0, day = 0.5, night = 1.0, ember = 1.0, ghosts = 0.0)

    /**
     * The front lamp's colour, [made] as the show (or the key f) gives it, leaning toward the time's: by day toward
     * [DAY_FRONT] by 0.5, by night toward [NIGHT_FRONT] by 0.3, at ember toward [EMBER_FRONT] by 0.25; its own colour
     * stays the larger part by night and at ember.
     */
    fun frontColor(world: WorldTime, made: Int): Int = Sun.mix(
        from = made,
        to = colorAt(world, day = DAY_FRONT, night = NIGHT_FRONT, ember = EMBER_FRONT, ghosts = world.ghostLight.rim),
        share = blend(world, made = 0.0, day = 0.5, night = 0.3, ember = 0.25, ghosts = 0.3),
    )

    /**
     * How soft the front lamp's shadows are, as three.js's shadow radius (in texels of its map): as made 1, by day 5
     * (soft, faint true selves), by night and at ember 1 (hard).
     */
    fun frontSoftness(world: WorldTime): Double = blend(world, made = 1.0, day = 5.0, night = 1.0, ember = 1.0, ghosts = 1.0)

    /**
     * How far the light on the surfaces behind the band (the wall, the fence and the surface behind it) leans toward a
     * cold moonlit grey blue, 0 to 1: none as made, by day or at ember (the ember stays red); by night 0.8, so the
     * front lamp's warm light reads cold there. Only those surfaces: the band keeps the lamp's own colours and moods.
     */
    fun wallCold(world: WorldTime): Double = blend(world, made = 0.0, day = 0.0, night = 0.8, ember = 0.0, ghosts = 0.0)

    /**
     * How bright the light on the surfaces behind the band is against the stage as made: as made and by day 1, by
     * night 0.5, so the windows into the cold world glow against a darker wall; at ember 1.
     */
    fun wallLight(world: WorldTime): Double = blend(world, made = 1.0, day = 1.0, night = 0.5, ember = 1.0, ghosts = 0.2)

    /** The colour the lamps behind and the fill lean toward: [DAY_TINT], [NIGHT_TINT], [EMBER_TINT]. */
    fun lampTint(world: WorldTime): Int =
        colorAt(world, day = DAY_TINT, night = NIGHT_TINT, ember = EMBER_TINT, ghosts = world.ghostLight.rim)

    /**
     * The colour of a main lamp behind in its mood ([lamp]) at the time [world]: by day the mood's daylight, by
     * night its night light, half way between them its twilight (the sets of lamps of `LightPlan.MOODS`); at ember
     * the twilight's again, which the lean toward red then takes ([backLean]); the stage as made keeps the mood's
     * own colour ([WorldTime.on]). The ghosts change nothing here: the lamps are all but dark then ([backScale]).
     */
    fun moodColor(world: WorldTime, lamp: MoodLamp): Int {
        // NaN-guard: a time of no number is the day
        val night = if (world.night != world.night) 0.0 else world.night.coerceIn(0.0, 1.0)
        val time = if (night < 0.5) {
            Sun.mix(from = lamp.day, to = lamp.twilight, share = night * 2)
        } else {
            Sun.mix(from = lamp.twilight, to = lamp.night, share = night * 2 - 1)
        }
        val own = Sun.mix(from = time, to = lamp.twilight, share = share(world.ember) * night)

        return Sun.mix(from = lamp.color, to = own, share = share(world.on))
    }

    /** [x] as a share, 0 to 1. NaN-guard: what is no number is none. */
    private fun share(x: Double): Double = if (x != x) 0.0 else x.coerceIn(0.0, 1.0)

    /** How far the lamps behind lean toward [lampTint]: as made 0, by day 0.35, by night 0.4, at ember 0.55. */
    fun backLean(world: WorldTime): Double = blend(world, made = 0.0, day = 0.35, night = 0.4, ember = 0.55, ghosts = 1.0)

    /** How strongly the lamps behind shine against the stage as made: by day 1, by night 0.8, at ember 0.9. */
    fun backScale(world: WorldTime): Double = blend(world, made = 1.0, day = 1.0, night = 0.8, ember = 0.9, ghosts = 0.03)

    /** How far the fill from the front leans toward [lampTint]: as made 0, by day 0.4, by night 0.5, at ember 0.4. */
    fun fillLean(world: WorldTime): Double = blend(world, made = 0.0, day = 0.4, night = 0.5, ember = 0.4, ghosts = 1.0)

    /** How strongly the fill shines against the stage as made: by day 1.25, by night 0.85, at ember 0.8. */
    fun fillScale(world: WorldTime): Double = blend(world, made = 1.0, day = 1.25, night = 0.85, ember = 0.8, ghosts = 0.0)

    /**
     * What the haze's light is multiplied by, linear red, green and blue: as made white; by day warm, by night cold,
     * at ember red.
     */
    fun hazeTint(world: WorldTime): Triple<Double, Double, Double> = Triple(
        blend(world, made = 1.0, day = 1.1, night = 0.75, ember = 1.3, ghosts = 1.0),
        blend(world, made = 1.0, day = 0.95, night = 0.9, ember = 0.55, ghosts = 1.0),
        blend(world, made = 1.0, day = 0.8, night = 1.2, ember = 0.4, ghosts = 1.0),
    )

    /** How thick the haze is against the stage as made: by day 1, by night 0.6 (thinner), at ember 1.15 (smoke). */
    fun hazeThickness(world: WorldTime): Double = blend(world, made = 1.0, day = 1.0, night = 0.6, ember = 1.15, ghosts = 1.1)

    /**
     * The light the haze has where no lamp reaches, linear: as made a breath of grey blue (0.006, 0.006, 0.009); by
     * day warmer, by night moonlit blue, at ember a glow of red.
     */
    fun hazeBase(world: WorldTime): Triple<Double, Double, Double> {
        val (r, g, b) = world.ghostLight.glow

        return Triple(
            blend(world, made = 0.006, day = 0.012, night = 0.006, ember = 0.02, ghosts = GHOST_GLOW * r),
            blend(world, made = 0.006, day = 0.009, night = 0.009, ember = 0.004, ghosts = GHOST_GLOW * g),
            blend(world, made = 0.009, day = 0.006, night = 0.02, ember = 0.003, ghosts = GHOST_GLOW * b),
        )
    }

    /** The lacquer's clear coat's roughness: as made 0.05 (almost a mirror), by day the same, by night 0.28, at ember 0.1. */
    fun coatRoughness(world: WorldTime): Double = blend(world, made = 0.05, day = 0.05, night = 0.28, ember = 0.1, ghosts = 0.2)

    /**
     * The gold's colour, linear: as made (0.9, 0.64, 0.26); by day a little richer, by night pale and cold (it shows
     * only as cold highlights), at ember red copper.
     */
    fun gold(world: WorldTime): Triple<Double, Double, Double> = Triple(
        blend(world, made = 0.9, day = 0.95, night = 0.42, ember = 0.9, ghosts = 0.42),
        blend(world, made = 0.64, day = 0.68, night = 0.44, ember = 0.28, ghosts = 0.44),
        blend(world, made = 0.26, day = 0.27, night = 0.5, ember = 0.12, ghosts = 0.5),
    )

    /**
     * The warm strip the lacquer mirrors where the sun stands, linear: as made (1.6, 0.62, 0.2); by day stronger and
     * richer, by night a faint cold moon, at ember a red glow.
     */
    fun roomSun(world: WorldTime): Triple<Double, Double, Double> {
        val (r, g, b) = world.ghostLight.glow

        return Triple(
            blend(world, made = 1.6, day = 2.2, night = 0.3, ember = 1.5, ghosts = GHOST_ROOM * r),
            blend(world, made = 0.62, day = 0.95, night = 0.38, ember = 0.12, ghosts = GHOST_ROOM * g),
            blend(world, made = 0.2, day = 0.3, night = 0.55, ember = 0.04, ghosts = GHOST_ROOM * b),
        )
    }

    /**
     * How bright the lacquer mirrors its room against the stage as made (with the house): as made, by day, by night and
     * at ember 1; in the ghosts 0.35, so the band stays dark, its edges only catching the glow behind ([roomSun]).
     */
    fun roomLight(world: WorldTime): Double = blend(world, made = 1.0, day = 1.0, night = 1.0, ember = 1.0, ghosts = 0.35)

    /**
     * How much of the cold world shows through the band's shadows on the surface behind the fence, 0 to 1: none by
     * day, coming in from dusk (a night of 0.35) and full from 0.85 on, at ember too; none with the time off.
     */
    fun beyond(world: WorldTime): Double {
        val x = ((world.night - 0.35) / 0.5).coerceIn(0.0, 1.0)
        val byNight = x * x * (3 - 2 * x)

        return world.on * (byNight + (1.0 - byNight) * world.ghosts)
    }

    /**
     * How far the time has gone over to the ghosts, 0 to 1 ([WorldTime.ghosts], as far as [WorldTime.on] lets it):
     * the band's shadows on the surface behind the fence stay open as windows, and the haze stays parted before them,
     * whatever the front lamp does (the lamp's shadow is drawn, its light gives nothing); and the windows show the
     * ghosts' pale light ([ghostPale]) in place of the cold world, the surface around them its deep ([ghostDepth]).
     */
    fun ghostShare(world: WorldTime): Double = world.on * world.ghosts

    /**
     * How far the front lamp is toward the band after [dt] more seconds, in metres, when it was [from]: while the
     * ghosts are [coming] (the stage turns to them or stays with them) it creeps closer at a steady pace, as far as
     * [GHOST_APPROACH] over a visit's length ([GHOST_APPROACH_SECONDS]); as they go it moves back, over
     * [GHOST_RETREAT_SECONDS] from its farthest. The ghosts are the band's shadows by that lamp: as it comes they grow
     * for as long as they are seen, and with the band swaying they seem to come marching closer; as they fade they
     * shrink again (the maintainer, 2026-11-22; before, the lamp came most of its shorter way in the first seconds
     * and stayed). No time: where it was.
     */
    fun ghostCreep(from: Double, coming: Boolean, dt: Double): Double {
        // NaN-guard: no time moves nothing, and no place is the lamp's own
        if (dt != dt || dt <= 0.0) {
            return if (from != from) 0.0 else from.coerceIn(0.0, GHOST_APPROACH)
        }

        val was = if (from != from) 0.0 else from
        val pace = if (coming) GHOST_APPROACH / GHOST_APPROACH_SECONDS else -GHOST_APPROACH / GHOST_RETREAT_SECONDS

        return (was + pace * dt).coerceIn(0.0, GHOST_APPROACH)
    }

    /**
     * The front lamp's way toward the band as it is shown, after [dt] more seconds, when it was shown at [from] and
     * [ghostCreep] says [to]: it follows softly, most of the way in [GHOST_EASE_SECONDS], so where the creep turns
     * round at once (the ghosts go: from coming closer to going back) the lamp, the ghosts' size and the camera
     * that goes with them ease into the way back (the maintainer, 2026-11-23: the return "should ease in ...
     * currently it somehow starts a bit abrupt"). No time: where it was.
     */
    fun ghostEase(from: Double, to: Double, dt: Double): Double {
        // NaN-guard: no number moves nothing, and no place is the lamp's own
        val was = if (from != from) 0.0 else from

        if (dt != dt || dt <= 0.0 || to != to) {
            return was.coerceIn(0.0, GHOST_APPROACH)
        }

        return (was + (to - was) * (1 - exp(-dt / GHOST_EASE_SECONDS))).coerceIn(0.0, GHOST_APPROACH)
    }

    /**
     * How wide the front lamp's cone is (half its opening, in radians) while it has crept [creep] metres toward the
     * band: wide enough that it still lights, and so throws shadows on, as much of the surface at [wall] as it does
     * from where it stands with its cone as made ([angle]), and [wider] metres more to either side when it has come
     * all its way. Without that the cone, coming closer, covers less and less of the surface while the ghosts on it
     * grow, and the outer ones are cut off left and right (the maintainer, 2026-11-23: "they disappear on the
     * backwall left and right, so the shadow area should probably wider, about one fence piece"). [lamp], [wall]:
     * places along z, in metres.
     */
    fun ghostCone(creep: Double, lamp: Double, wall: Double, angle: Double, wider: Double): Double {
        val near = lamp - creep

        // NaN-guard: no number, or a lamp that is not before the surface, leaves the cone as made
        if (!(creep > 0.0) || !(near > wall) || !(lamp > wall) || !(angle > 0.0) || wider != wider) {
            return angle
        }

        val covered = (lamp - wall) * tan(angle) + wider * (creep / GHOST_APPROACH).coerceIn(0.0, 1.0)

        return atan(covered / (near - wall)).coerceIn(angle, GHOST_CONE_MOST)
    }

    /**
     * How far the total's camera goes back, in metres, while the front lamp has crept [creep] metres toward the
     * band: as far as it takes for the band's shadows to stay as large in the picture as they were, so the ghosts
     * keep their size and the band shrinks before them (the maintainer, 2026-11-22: "move the camera back so that
     * the shadows appear the same size and the band shrinks"). A shadow on the surface at [wall] of someone at
     * [band] under a lamp at [lamp] is larger than they are by the lamp's way to the surface over its way to them;
     * seen from [camera] it is as large as that over the camera's way to the surface (all four: places along z, in
     * metres, the lamp and the camera before the band, the surface behind it).
     */
    fun ghostPull(creep: Double, lamp: Double, wall: Double, band: Double, camera: Double): Double {
        val near = lamp - creep

        // NaN-guard: no number, or a lamp that is not before the band, moves no camera
        if (!(creep > 0.0) || !(near > band) || !(lamp > band) || !(camera > wall)) {
            return 0.0
        }

        val grown = ((near - wall) / (near - band)) / ((lamp - wall) / (lamp - band))

        return (camera - wall) * (grown - 1.0)
    }

    /**
     * How far the haze stays parted before the band's shadows in the ghosts, whatever the front lamp does, 0 to 1: a
     * share of [ghostShare] ([GHOST_CLEARING]), so a veil of the glowing haze stays before the figures and they glow
     * through it.
     */
    fun ghostClearing(world: WorldTime): Double = ghostShare(world) * GHOST_CLEARING

    /**
     * The light the figures in the haze send in the ghosts, linear: the colour's pale ([GhostLight.pale]) at
     * [GHOST_PALE]. The fence mixes it in for the cold world by [ghostShare].
     */
    fun ghostPale(world: WorldTime): Triple<Double, Double, Double> {
        val (r, g, b) = world.ghostLight.pale

        return Triple(GHOST_PALE * r, GHOST_PALE * g, GHOST_PALE * b)
    }

    /**
     * The light the surface behind the haze sends of its own in the ghosts, linear: the colour's deep
     * ([GhostLight.depth]), as far as [ghostShare] goes; none outside them.
     */
    fun ghostDepth(world: WorldTime): Triple<Double, Double, Double> {
        val (r, g, b) = world.ghostLight.depth
        val share = ghostShare(world)

        return Triple(share * r, share * g, share * b)
    }

    /**
     * A value at the time [world]: [day], [night] and [ember] at the three ends, blended between them, from there to
     * [ghosts] by [WorldTime.ghosts], and from [made] (the stage as it was made) by [WorldTime.on].
     */
    fun blend(world: WorldTime, made: Double, day: Double, night: Double, ember: Double, ghosts: Double): Double {
        val dark = night + (ember - night) * world.ember
        val time = day + (dark - day) * world.night
        val own = time + (ghosts - time) * world.ghosts

        return made + (own - made) * world.on
    }

    /**
     * A colour at the time [world], from [day] to [night] to [ember], and to [ghosts], as [blend] goes (without the
     * stage as made).
     */
    private fun colorAt(world: WorldTime, day: Int, night: Int, ember: Int, ghosts: Int): Int {
        val time = Sun.mix(from = day, to = Sun.mix(from = night, to = ember, share = world.ember), share = world.night)

        return Sun.mix(from = time, to = ghosts, share = world.ghosts)
    }
}
