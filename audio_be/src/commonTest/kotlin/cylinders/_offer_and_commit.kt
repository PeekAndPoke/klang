/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders

import io.peekandpoke.klang.audio_be.voices.Voice

// The production path for one voice in one block, as specs drive it: [voice] offers itself ([Cylinder.offer]) and
// the block's owner is committed at once ([Cylinder.commitOwner]), which production does once per block in
// `Cylinders.processAndMix`. A row that needs two voices in one block calls `offer` twice, then `commitOwner`.

// blockStart is an ABSOLUTE backend frame, Double, see RenderClock.cursorFrame.
fun Cylinder.offerAndCommit(voice: Voice, blockStart: Double) {
    offer(voice, blockStart)
    commitOwner()
}

// blockStart is an ABSOLUTE backend frame, Double, see RenderClock.cursorFrame.
fun Cylinders.offerAndCommit(id: Int, voice: Voice, blockStart: Double): Cylinder =
    offer(id, voice, blockStart).also { it.commitOwner() }
