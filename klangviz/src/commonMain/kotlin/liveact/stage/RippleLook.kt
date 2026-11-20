/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Switches

/**
 * How uneven the mirror floor's stone is (`?ripple=gentle`, [Switch.Ripple]; the key b steps through them): how much a
 * slow ripple of its surface bends the mirror image and tilts the floor's own shading, so the bent reflection reads as
 * caused by the stone (the maintainer, 2026-11-20: "the eye sees the floor is totally even but the reflections behave
 * as if the floor is 'bumped' ... a little of this 'glitter' effect can be an intentional choice ... but the balance
 * needs to be found"). [word] is its name in the address and in the line at the top. Only the mirror floor has it
 * ([FloorLook.Mirror]); the underworld stills it on top, at every level.
 *
 * [broad]: the bend by a broad ripple, some 2 m from crest to crest, as a share of the mirror's picture; [fine]: the
 * bend by the ripple as it was first made, a larger and a finer one (some 0.8 m and 0.25 m), which bent a fence post's
 * reflection into zigzags that shimmered as the camera moved.
 * - [None]: the stone flat, the reflection unbent.
 * - [Faint]: half the gentle one.
 * - [Gentle]: the default; a fence post's reflection stays a straight line that sways gently.
 * - [Strong]: the ripple as it was first made.
 */
enum class RippleLook(val word: String, val broad: Double, val fine: Double) {
    None(word = "none", broad = 0.0, fine = 0.0),
    Faint(word = "faint", broad = 0.0007, fine = 0.0),
    Gentle(word = "gentle", broad = 0.0015, fine = 0.0),
    Strong(word = "strong", broad = 0.0, fine = 0.006);

    companion object {
        /** The ripple the address asks for: none, faint, gentle or strong; any other value, or none, [Gentle]. */
        fun of(switches: Switches): RippleLook = entries.firstOrNull { it.word == switches[Switch.Ripple] } ?: Gentle
    }
}
