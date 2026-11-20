/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.lights

import io.peekandpoke.klang.viz.liveact.stage.GhostLight
import kotlin.math.PI
import kotlin.math.cos

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
 *   front lamp's shadow is still drawn: the figures are made of it), the lamps behind a little, all in the colour's
 *   rim, so the band's edges just show; the sun gone; the lacquer mirrors little and only the glow behind. All the
 *   light is in the haze behind the fence, which glows in the colour where no lamp reaches ([hazeBase]); the band's
 *   shadows stay open in it all the time, pale figures, the brightest of the picture ([ghostShare], [ghostPale]),
 *   before the colour's deep ([ghostDepth]); the light on the surfaces behind is dimmed.
 */
object WorldLight {
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

    /** How bright the figures in the ghosts' haze are, as a share of the colour's pale ([GhostLight.pale]). */
    const val GHOST_PALE = 1.6

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
     * 1 (night) at half the round, day again at its end. The way between follows a cosine, eased once more, so it
     * rests a little at each end and moves fastest at dusk and dawn. A [t] of no number is the day; a [period] of no
     * number, or not above 0, is [DRIFT_PERIOD].
     */
    fun drift(t: Double, period: Double = DRIFT_PERIOD): Double {
        // NaN-guard: no time is the day, no period the period as made
        if (t != t) {
            return 0.0
        }

        val round = if (period.isFinite() && period > 0.0) period else DRIFT_PERIOD
        val wave = 0.5 - 0.5 * cos(2 * PI * t / round)

        return wave * wave * (3 - 2 * wave)
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

    /** How far the lamps behind lean toward [lampTint]: as made 0, by day 0.35, by night 0.4, at ember 0.55. */
    fun backLean(world: WorldTime): Double = blend(world, made = 0.0, day = 0.35, night = 0.4, ember = 0.55, ghosts = 1.0)

    /** How strongly the lamps behind shine against the stage as made: by day 1, by night 0.8, at ember 0.9. */
    fun backScale(world: WorldTime): Double = blend(world, made = 1.0, day = 1.0, night = 0.8, ember = 0.9, ghosts = 0.12)

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
     * at ember 1; in the ghosts 0.15, so the band stays dark, its edges only catching the glow behind ([roomSun]).
     */
    fun roomLight(world: WorldTime): Double = blend(world, made = 1.0, day = 1.0, night = 1.0, ember = 1.0, ghosts = 0.15)

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
