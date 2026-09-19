/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

/**
 * First-writer-wins ownership lease for an orbit's bus-effect settings.
 *
 * All voices on an orbit share one set of bus effects (body, vowel, reverb, delay, phaser, compressor).
 * Without arbitration the config flip-flops last-writer-wins every block; worst case (two voices, different
 * settings) it thrashes an effect — e.g. rebuilding a filter bank on the audio thread every block. This
 * lease makes the FIRST voice to sound the orbit's **owner**: while that voice is alive its settings stick
 * and other voices are ignored. Put voices that need independent bus settings on different orbits.
 *
 * **Liveness by absence, not by a death signal.** A live, started voice re-offers itself EVERY block (its
 * `SendRenderer` → `Cylinder.updateFromVoice` runs every block it renders). So the owner "checks in" each
 * block. If it misses a block — for ANY reason: natural end, cut/choke, or playback cleanup — the lease
 * lapses and the next offering voice takes over. No per-removal-path hooks needed. Hand-off has a one-block
 * grace: for the single block between the owner's last check-in and the successor's takeover the effects
 * keep the PREVIOUS owner's settings (applied to whatever sounds that block) before the successor
 * reconfigures. On a homogeneous orbit that's a no-op; across differing settings it's a ~1-block transient.
 *
 * Ownership is tracked by a boolean flag + the owner's [Voice.id] value (never by a sentinel id, so an
 * id that happens to be any Int — e.g. a wrapped counter — can still own the lease).
 *
 * One instance per orbit ([io.peekandpoke.klang.audio_be.cylinders.Cylinder]) governs all its bus effects.
 */
class VoiceLease {

    private var owned: Boolean = false
    private var ownerId: Int = 0
    // Absolute backend frame — Double, see RenderClock.cursorFrame.
    private var lastSeenFrame: Double = 0.0

    val hasOwner: Boolean get() = owned

    /**
     * [voiceId] claims or renews the lease for the block starting at frame [blockStart] (consecutive blocks
     * differ by [blockFrames]).
     *
     * Returns `true` if [voiceId] now holds the lease — it renewed its own, or the previous owner lapsed
     * (missed more than one block) and it took over — in which case the caller should apply this voice's
     * settings to the effect. Returns `false` if a different, still-live owner holds it (skip this voice).
     */
    fun claim(voiceId: Int, blockStart: Double, blockFrames: Int): Boolean {
        if (!isHeld(blockStart, blockFrames) || voiceId == ownerId) {
            owned = true
            ownerId = voiceId
            lastSeenFrame = blockStart
            return true
        }
        return false
    }

    /**
     * True while the owner is alive in the block starting at [blockStart]: it checked in during this
     * block or the one before (the one-block grace above). The same test [claim] makes, in one place.
     *
     * **Held whenever ANY voice plays on the orbit**, not only the owner: a voice that offers itself
     * this block either renews or takes the lease (so it is held), or is turned away because a live
     * owner holds it (so it is held). A lease that is not held therefore means nobody checked in this
     * block, and the owner not in the block before either. `Cylinder.tryDeactivate` reads it that way
     * (an orbit never deactivates while a voice plays on it, decided 2026-09-19). The grace after the
     * last check-in is at most one block: a turned-away voice that stops renews nothing, so its last
     * block is covered by the owner's grace and the block after it is not.
     *
     * Every voice that renders checks in, a culled one included (`Voice.render` renews the lease of a
     * culled voice and nothing else), so the lease stays held until the last voice's scheduled end,
     * its release included, whether that release is still audible or not.
     */
    fun isHeld(blockStart: Double, blockFrames: Int): Boolean = owned && (blockStart - lastSeenFrame) <= blockFrames

    /** Release the lease — called when the orbit fully deactivates so a reused orbit starts fresh. */
    fun reset() {
        owned = false
        ownerId = 0
        lastSeenFrame = 0.0
    }
}
