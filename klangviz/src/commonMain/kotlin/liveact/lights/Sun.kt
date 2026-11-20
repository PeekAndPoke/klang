/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.lights

import kotlin.math.roundToInt

/**
 * The sun behind the drummer (`?stage=mesh`): a big, low glow behind the mesh that lights the haze from behind, so
 * the wires and the drummer stand dark against it. Its colour follows the mood of the lamps behind the band: an ember
 * in a warm mood, a cold steel blue only where the mood is clearly cold; under the front lamp it leans toward the
 * lamp's colour. It stays dim, as the lamps behind do, while the front lamp is up, so the band's shadows read. And it
 * follows how brightly the lamps behind light the stage ([lit], [follow]): it sinks in the dark stretches, where
 * only the lamp between the two main ones is on, and swells again when all of them come back.
 */
object Sun {
    /** Its colour in a warm mood: a muted ember, darker and greyer than the front lamp's amber. */
    const val EMBER = 0xd8925a

    /** Its colour in a cold mood. */
    const val COLD = 0x5f7dff

    /**
     * Its colour at the moods of [mood], with the front lamp in [front] at [frontLevel] (0 to 1): from [COLD] to
     * [EMBER] by how warm the two main lamps behind are together (red against blue), then toward [front] by up to
     * 0.7 as the front lamp comes up.
     */
    fun color(mood: MoodMix, front: Int, frontLevel: Double): Int {
        val left = mix(from = mood.from.left.color, to = mood.to.left.color, share = mood.share)
        val right = mix(from = mood.from.right.color, to = mood.to.right.color, share = mood.share)
        val both = mix(from = left, to = right, share = 0.5)
        // from -1 (all blue) to 1 (all red); the cold-against-hot mood is about -0.1 and counts as warm
        val warmth = (channel(both, shift = 16) - channel(both, shift = 0)) / 255.0
        val own = mix(from = COLD, to = EMBER, share = ((warmth + 0.75) / 0.6).coerceIn(0.0, 1.0))
        // NaN-guard: a front lamp at no number (?front) leaves the sun its own colour
        val level = if (frontLevel.isNaN()) 0.0 else frontLevel.coerceIn(0.0, 1.0)

        return mix(from = own, to = front, share = 0.7 * level)
    }

    /**
     * The share of its light it gives with the front lamp at [front] (0 to 1), or while the stroboscope runs
     * ([strobing]): down to 0.3 under the full front lamp, so the shadows on the back read over it.
     */
    fun share(front: Double, strobing: Boolean): Double {
        if (strobing) {
            return Strobe.REST
        }

        // NaN-guard: a front lamp at no number (?front) dims nothing
        val level = if (front.isNaN()) 0.0 else front.coerceIn(0.0, 1.0)

        return 1.0 - 0.7 * level
    }

    /**
     * How brightly the lamps behind the band light the stage in the rigs [rig], from 0 (all dark) to 1 (all of them
     * on): each of [lamps] (where it hangs, and its light at full) at its share in the rigs, together, against all of
     * them at full. With the lamps as they hang (the two main ones 260 each, the one between them 180): all 1, the
     * outer two alone about 0.74, the inner one alone (turned up) 0.36. No lamps: 0.
     */
    fun lit(rig: BackRigMix, lamps: List<Pair<BackPlace, Double>>): Double {
        val full = lamps.sumOf { (_, light) -> light }

        if (full <= 0.0) {
            return 0.0
        }

        return lamps.sumOf { (place, light) -> light * rig.share(place) } / full
    }

    /**
     * The share of its light the sun gives on a stage lit [lit] ([Sun.lit], 0 to 1): the square of it, so it sinks
     * further than the lamps do and the dark stays dark. All the lamps behind on: 1; the outer two alone about 0.55;
     * the inner one alone about 0.13 (the dark look, `?front=0&rig=inner`, was lit by the sun alone before). Above 1
     * (the inner lamp alone is turned up, but never brighter than all three) it holds at 1.
     */
    fun follow(lit: Double): Double {
        // NaN-guard: a brightness that is no number gives no sun
        if (lit != lit) {
            return 0.0
        }

        val level = lit.coerceIn(0.0, 1.0)

        return level * level
    }

    /** One of a colour's channels: red at [shift] 16, green at 8, blue at 0. */
    private fun channel(color: Int, shift: Int): Int = (color shr shift) and 0xFF

    /** The colour [share] of the way from [from] to [to], channel by channel. */
    private fun mix(from: Int, to: Int, share: Double): Int {
        var out = 0

        for (shift in intArrayOf(16, 8, 0)) {
            val a = channel(from, shift = shift)
            val b = channel(to, shift = shift)

            out = out or ((a + (b - a) * share).roundToInt().coerceIn(0, 255) shl shift)
        }

        return out
    }
}
