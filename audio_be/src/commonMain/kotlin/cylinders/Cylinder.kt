/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders

import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystChain
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystChainBuilder
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystContext
import io.peekandpoke.klang.audio_be.cylinders.katalyst.VoiceLease
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.constants.ORBIT_SILENCE_FLOOR

/**
 * Mixing channel / Effect bus — called "Cylinder" in strudel.
 *
 * Each orbit runs one [KatalystChain], the per-orbit effect chain, built from a [KatalystDsl]:
 * **Body → Vowel → Delay → Reverb → Phaser → Compressor** for [KatalystDsl.classic], which is what
 * every cylinder has always run and what every cylinder still runs here (Katalyst step 2,
 * 2026-09-17: the list is data instead of a hardcoded `listOf(...)`, and the engine is
 * byte-identical; a DECLARED chain reaches the cylinder in step 3).
 *
 * The duck runs in a separate pass after all orbits are processed (cross-orbit dependency).
 */
// Block-framing ledger D11 (named Class 2 knob): the silence grace before the tail scan is counted
// in BLOCKS, and `Cylinders` visits ONE cylinder per block round-robin, so the wall-clock grace is
// `silentBlocksBeforeTailCheck × allocatedCylinders × blockFrames` — it shrinks with block size and
// grows with orbit count. No audio is cut (the scan itself protects tails), but everything a
// TEARDOWN does rides this schedule: the moment the ring/combs are cleared moves, and so does the
// phaser's clean-slate restart (`Phaser.resetForReuse` zeroes sweep phase AND rate) — a sparse
// pattern whose gaps straddle the grace at one block size but not another re-enters the sweep
// differently (review round 4). `PlaybackEngine` shows the seconds-derived pattern if this ever
// needs pinning to wall time.
class Cylinder(
    id: Int,
    val blockFrames: Int,
    sampleRate: Int,
    silentBlocksBeforeTailCheck: Int = 10,
    /** The ring shelf this orbit's delay rents from. Production passes the backend's one warehouse. */
    rings: SizedBuffers = SizedBuffers.forRings(sampleRate),
    /** The reverb-unit shelf this orbit's reverb rents from. Same warehouse. */
    reverbs: ReverbUnits = ReverbUnits(sampleRate),
) {

    // ════════════════════════════════════════════════════════════════════════════
    // Bus pipeline effects
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * This orbit's effect chain, built ONCE here: the stage instances own the orbit's DSP state
     * (delay ring, reverb network, compressor envelope, phaser sweep clock), so building is not
     * something a block or a voice may do. Nothing is allocated eagerly that is lazy today: the
     * delay's ring and the reverb's network are still rented on the first activating configure.
     */
    private val katalyst: KatalystChain = KatalystChainBuilder.build(
        dsl = KatalystDsl.classic,
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        rings = rings,
        reverbs = reverbs,
    )

    // The chain's stages by name. Null when the chain declares no such stage, which
    // `KatalystDsl.classic` never does, so every one of them is present on every cylinder today.
    // These are the chain's instances, not the cylinder's: a cylinder no longer knows what a body
    // or a reverb IS, which is the whole point of the step.

    val body get() = katalyst.body

    val vowel get() = katalyst.vowel

    val delay get() = katalyst.delay

    val reverb get() = katalyst.reverb

    val phaser get() = katalyst.phaser

    val compressor get() = katalyst.compressor

    val duck get() = katalyst.duck

    /**
     * The bus effect pipeline, in the order this orbit's chain declares its stages.
     *
     * The duck is NOT in this pipeline; it is applied separately by [Cylinders] after all orbits
     * are processed, because it needs cross-orbit access to the sidechain source.
     */
    val pipeline get() = katalyst.pipeline

    /** Rents the warehouse refused this orbit's stages, for the diagnostics feedback. */
    val deniedRents get() = katalyst.deniedRents

    // ════════════════════════════════════════════════════════════════════════════
    // Buffers and context
    // ════════════════════════════════════════════════════════════════════════════

    /** Dry mix buffer — voices sum into this */
    val mixBuffer = StereoBuffer(blockFrames)

    /** Delay send buffer — voices write delay sends here */
    val delaySendBuffer = StereoBuffer(blockFrames)

    /** Reverb send buffer — voices write reverb sends here */
    val reverbSendBuffer = StereoBuffer(blockFrames)

    /** Shared context for all bus effects */
    val katalystContext = KatalystContext(
        blockFrames = blockFrames,
        mixBuffer = mixBuffer,
        delaySendBuffer = delaySendBuffer,
        reverbSendBuffer = reverbSendBuffer,
    )

    // ════════════════════════════════════════════════════════════════════════════
    // State
    // ════════════════════════════════════════════════════════════════════════════

    /** The orbit this cylinder serves. Re-labelled by [adopt] when a shelved cylinder is rented for another. */
    var id: Int = id
        private set

    private var silentBlocksBeforeTailCheck: Int = silentBlocksBeforeTailCheck

    var isActive = false
        private set

    private var silentBlockCount: Int = 0

    // ONE owner per orbit: the first voice to sound owns ALL of the orbit's bus effects while it is alive
    // (first-writer-wins). Other voices on the orbit are ignored — route to a different orbit if you want
    // different bus settings. Overlapping voices with different reverb/delay/compressor make no musical
    // sense, so we don't support it. This also kills the last-writer-wins per-block flip-flop (and the
    // body-filter rebuild thrash it caused on mixed-material orbits).
    private val lease = VoiceLease()

    // ════════════════════════════════════════════════════════════════════════════
    // API
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Update orbit settings from a voice. [blockStart] is the current block's start frame, used by the
     * orbit ownership [lease] to tell voices apart across blocks (production passes it via
     * `Cylinders.getOrInit`). Only the OWNER voice's settings are applied; other voices are ignored.
     *
     * Block-framing ledger D14 (named Class 2 knob): bus params apply at BLOCK granularity, never
     * at the onset sample — a new owner's settings also govern the `ctx.offset` samples before its
     * own first sample (the previous owner's still-decaying tail), and the transition frame moves
     * with alignment. Inherent to a block-continuous bus driven by per-voice triggers.
     */
    // blockStart is an ABSOLUTE backend frame — Double, see RenderClock.cursorFrame.
    fun updateFromVoice(voice: Voice, blockStart: Double) {
        isActive = true

        if (lease.claim(voice.id, blockStart, blockFrames)) {
            // The chain still resolves every knob from the owner voice, as this method's
            // `applyBusEffects` did before the chain existed; step 3 gives the stages their own
            // resolver over the chain's slots.
            katalyst.applyOwner(voice)
        }
    }

    fun clear() {
        if (!isActive) return

        mixBuffer.clear()
        delaySendBuffer.clear()
        reverbSendBuffer.clear()
    }

    /**
     * Processes all bus effects in the chain's order: Body → Vowel → Delay → Reverb → Phaser →
     * Compressor for the classic chain.
     *
     * The duck is NOT processed here, see [Cylinders.processAndMix].
     */
    fun processEffects() {
        if (!isActive) return

        katalyst.process(katalystContext)
    }

    /**
     * Applies ducking using the resolved sidechain buffer.
     *
     * Called by [Cylinders] after all orbits have processed their main pipeline,
     * since ducking needs cross-orbit access.
     */
    fun processDuck(sidechainMixBuffer: StereoBuffer?) {
        if (!isActive) return

        katalystContext.sidechainBuffer = sidechainMixBuffer
        katalyst.processDuck(katalystContext)
        katalystContext.sidechainBuffer = null
    }

    /**
     * Checks if the orbit is silent and deactivates it if so.
     *
     * Uses a two-phase approach to avoid cutting off effect tails (delay/reverb):
     * 1. When mixBuffer is silent, increment a counter instead of deactivating immediately.
     *    This grace period keeps effects processing so their tails continue to decay naturally.
     * 2. After N silent blocks, scan effect internal buffers. If they still have audio, reset
     *    the counter and keep processing. If silent, deactivate.
     */
    /**
     * Retires this cylinder for the shelf (resource warehouse, cylinders): every bus effect off and
     * cleared, the lease freed, the send buffers zeroed, and the rented units (delay ring, reverb
     * network) handed back to THEIR shelves — a shelved cylinder holds nothing. The same clean slate
     * [tryDeactivate] reaches, plus the return. Only for a cylinder that will never render again
     * on its current orbit: `CylinderUnits.giveBack` is the one caller.
     */
    fun retire() {
        // The chain's retire, NOT its reset: the rented units go back DIRTY and the warehouse's
        // housekeeping zeroes them a block at a time (see KatalystChain.retire).
        katalyst.retire()
        lease.reset()
        mixBuffer.clear()
        delaySendBuffer.clear()
        reverbSendBuffer.clear()
        isActive = false
        silentBlockCount = 0
    }

    /** Re-labels a retired cylinder for orbit [id] under the renting `Cylinders`' settings. */
    fun adopt(id: Int, silentBlocksBeforeTailCheck: Int) {
        this.id = id
        this.silentBlocksBeforeTailCheck = silentBlocksBeforeTailCheck
    }

    fun tryDeactivate() {
        if (!isActive) return

        if (!isMixBufferSilent()) {
            silentBlockCount = 0
            return
        }

        silentBlockCount++

        if (silentBlockCount < silentBlocksBeforeTailCheck) return

        // State-aware like the delay's: a draining reverb reports its tail BY CONSTRUCTION, so
        // the orbit stays alive until the countdown's terminal reset — the old param-gated scan
        // hid a still-charged network the moment a no-reverb owner zeroed size.
        if (katalyst.hasTail()) {
            silentBlockCount = 0
            return
        }

        isActive = false
        silentBlockCount = 0
        // Free the orbit lease and reset all bus effects so a reused/reactivated orbit starts clean and
        // is reconfigured by whichever voice next claims it.
        katalyst.reset()
        lease.reset()
    }

    private fun isMixBufferSilent(): Boolean {
        // Shared with the voice cull floor (VOICE_CULL_FLOOR), so a culled voice is by definition
        // below what keeps an orbit alive.
        val threshold = ORBIT_SILENCE_FLOOR
        for (sample in mixBuffer.left) {
            if (sample > threshold || sample < -threshold) return false
        }
        for (sample in mixBuffer.right) {
            if (sample > threshold || sample < -threshold) return false
        }
        return true
    }
}
