/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
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
        val ownerAlive = owned && (blockStart - lastSeenFrame) <= blockFrames
        if (!ownerAlive || voiceId == ownerId) {
            owned = true
            ownerId = voiceId
            lastSeenFrame = blockStart
            return true
        }
        return false
    }

    /** Release the lease — called when the orbit fully deactivates so a reused orbit starts fresh. */
    fun reset() {
        owned = false
        ownerId = 0
        lastSeenFrame = 0.0
    }
}
