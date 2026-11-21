/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.lights

import io.peekandpoke.klang.viz.liveact.stage.Surrounding

/**
 * The dim light at the feet of the surroundings (KLANG-00478, [Surrounding]):
 * quiet, so they never fight the band. Its colour is the time of day's, as the lamps behind lean ([WorldLight.lampTint]:
 * warm by day, cold by night, red at ember), and one side leans cold, the other warm, as in the idea picture the
 * maintainer liked (the audience's left, x below 0, toward [COLD]; its right toward [WARM]). It dims a little by night
 * and is all but gone in the ghosts, when the stage is all but dark; there the shapes themselves go too ([shown]).
 */
object AroundLight {
    /** The light's colour with the time of day off (the stage as it was made): a cool grey. */
    const val MADE = 0x8a90a0

    /** The colours the two sides lean toward, and how far. */
    const val COLD = 0x6f8fe8
    const val WARM = 0xffa050
    const val LEAN = 0.4

    /**
     * How strong the light is against its full: by day 0.75 (its warm colour is strong enough), by night 0.7, at ember
     * 0.85, in the ghosts 0.05.
     */
    fun level(world: WorldTime): Double = WorldLight.blend(world, made = 0.8, day = 0.75, night = 0.7, ember = 0.85, ghosts = 0.05)

    /** The light's own colour at the time of day [world], before the sides lean. */
    fun tint(world: WorldTime): Int = Sun.mix(from = MADE, to = WorldLight.lampTint(world), share = world.on)

    /** The light's colour on the audience's left (x below 0): leaning cold. */
    fun coldSide(world: WorldTime): Int = Sun.mix(from = tint(world), to = COLD, share = LEAN)

    /** The light's colour on the audience's right (x above 0): leaning warm. */
    fun warmSide(world: WorldTime): Int = Sun.mix(from = tint(world), to = WARM, share = LEAN)

    /**
     * How much of the shapes is there at all, 0 to 1: all of them, but in the ghosts only a trace, so the stage is all
     * but dark and the haze's glow is the light of the picture.
     */
    fun shown(world: WorldTime): Double = 1.0 - 0.9 * WorldLight.ghostShare(world)
}
