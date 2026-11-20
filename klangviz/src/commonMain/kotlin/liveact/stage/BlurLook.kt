/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Switches

/**
 * How blurred the mirror floor's reflection is (`?blur=soft`, [Switch.Blur]; the key c steps through them): how wide
 * the blur grows with the gap behind the floor, as a cone from the floor ([cone]: the blur's width over the distance
 * is this share of the gap over the distance). Over it a small fixed blur stays at every level, so thin things (the
 * trumpet, the strings, the cymbal stands, the fence's wires) never show the mirror picture's pixels or crawl; and
 * near the floor the reflection is crisp at every level, so what stands on it meets its reflection (the maintainer,
 * 2026-11-20: "i think we need the blur otherwise the reflection of f.e. the trumpet is pixelated ... i would reduce
 * the blur on the ground if possible"). [word] is its name in the address and in the line at the top. Only the mirror
 * floor has it ([FloorLook.Mirror]).
 * - [Crisp]: the default (the maintainer, 2026-11-20: "i think i like the 'gentle' ripple in combination with the
 *   crisp reflections"): a narrow cone: polished stone, nearly a mirror.
 * - [Soft]: between the two.
 * - [Stone]: the cone the floor was first made with: rough stone.
 */
enum class BlurLook(val word: String, val cone: Double) {
    Crisp(word = "crisp", cone = 0.004),
    Soft(word = "soft", cone = 0.012),
    Stone(word = "stone", cone = 0.03);

    companion object {
        /** The blur the address asks for: crisp, soft or stone; any other value, or none, [Crisp]. */
        fun of(switches: Switches): BlurLook = entries.firstOrNull { it.word == switches[Switch.Blur] } ?: Crisp
    }
}
