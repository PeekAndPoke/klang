/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders

import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystBodyEffect
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystCompressorEffect
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystContext
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystDelayEffect
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystDuckingEffect
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystEffect
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystFormantEffect
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystPhaserEffect
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystReverbEffect
import io.peekandpoke.klang.audio_be.cylinders.katalyst.VoiceLease
import io.peekandpoke.klang.audio_be.effects.Compressor
import io.peekandpoke.klang.audio_be.effects.DelayLine
import io.peekandpoke.klang.audio_be.effects.Ducking
import io.peekandpoke.klang.audio_be.effects.Phaser
import io.peekandpoke.klang.audio_be.effects.Reverb
import io.peekandpoke.klang.audio_be.voices.Voice

/**
 * Mixing channel / Effect bus — called "Cylinder" in strudel.
 *
 * Each orbit has a composable bus pipeline:
 * **Delay → Reverb → Phaser → Compressor**
 *
 * Ducking runs in a separate pass after all orbits are processed (cross-orbit dependency).
 */
class Cylinder(val id: Int, val blockFrames: Int, sampleRate: Int, private val silentBlocksBeforeTailCheck: Int = 10) {

    // ════════════════════════════════════════════════════════════════════════════
    // Bus pipeline effects
    // ════════════════════════════════════════════════════════════════════════════

    // Body / vowel resonators — timbre shapers of the whole orbit (moved off the per-voice chain).
    // They run first so they colour the dry mix before the time/dynamics effects.
    val body = KatalystBodyEffect(sampleRate.toDouble())

    val vowel = KatalystFormantEffect(sampleRate.toDouble())

    val delay = KatalystDelayEffect(
        delayLine = DelayLine(maxDelaySeconds = 10.0, sampleRate = sampleRate),
    )

    val reverb = KatalystReverbEffect(
        reverb = Reverb(sampleRate),
    )

    val phaser = KatalystPhaserEffect(
        phaser = Phaser(sampleRate),
    )

    val compressor = KatalystCompressorEffect()

    val ducking = KatalystDuckingEffect()

    /**
     * The bus effect pipeline: Body → Vowel → Delay → Reverb → Phaser → Compressor.
     *
     * Ducking is NOT in this pipeline — it's applied separately by [Cylinders] after all orbits
     * are processed, because it needs cross-orbit access to the sidechain source.
     */
    val pipeline: List<KatalystEffect> = listOf(body, vowel, delay, reverb, phaser, compressor)

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
     */
    fun updateFromVoice(voice: Voice, blockStart: Int) {
        isActive = true

        if (lease.claim(voice.id, blockStart, blockFrames)) {
            applyBusEffects(voice)
        }
    }

    /**
     * Apply ALL of this orbit's bus effects from its owning [voice]. Absent effects are turned off, so the
     * owner's config fully determines the orbit — nothing leaks from a previous owner. The owner re-applies
     * every block; this is idempotent (body/vowel short-circuit on an unchanged config). The
     * compressor/ducking instances are reused, so their envelope followers survive across notes AS LONG AS
     * consecutive owners keep the effect — a takeover by a voice that has no compressor/ducking clears it,
     * and the next owner that re-adds it starts a fresh envelope.
     */
    private fun applyBusEffects(voice: Voice) {
        // Body / vowel resonators (null → off).
        body.configure(voice.body)
        vowel.configure(voice.vowel)

        // Delay
        delay.delayLine.delayTimeSeconds = voice.delay.time
        delay.delayLine.feedbackCap = voice.delay.cap
        delay.delayLine.feedback = voice.delay.feedback

        // Reverb (reverb.room is used by SendRenderer for send amount)
        // Already normalized (and clamped) by `Reverb.normalizeRoomSize` in VoiceFactory — a comb
        // network above unity yields DC, not a longer tail, so there is no sound above 1.0 to keep.
        reverb.reverb.roomSize = voice.reverb.roomSize
        // roomFade overrides roomSize for the comb feedback, so it lives on the same axis and
        // needs the same bound (it is authored 0..1 directly, not on the /10 scale).
        reverb.reverb.roomFade = voice.reverb.roomFade?.coerceIn(0.0, 1.0)
        reverb.reverb.roomLp = voice.reverb.roomLp
        reverb.reverb.roomDim = voice.reverb.roomDim
        reverb.reverb.iResponse = voice.reverb.iResponse

        // Phaser — always update parameters (not just when depth > 0) to avoid stale state
        phaser.phaser.rate = voice.phaser.rate
        phaser.phaser.depth = voice.phaser.depth
        phaser.phaser.center = if (voice.phaser.center > 0) voice.phaser.center else 1000.0
        phaser.phaser.sweep = if (voice.phaser.sweep > 0) voice.phaser.sweep else 1000.0
        phaser.phaser.feedback = 0.5

        // Ducking / Sidechain — reuse instance to preserve envelope state; clear when the owner has none.
        val voiceDucking = voice.ducking
        if (voiceDucking != null) {
            ducking.duckCylinderId = voiceDucking.cylinderId
            val existing = ducking.ducking
            if (existing == null) {
                ducking.ducking = Ducking(
                    sampleRate = reverb.reverb.sampleRate,
                    attackSeconds = voiceDucking.attackSeconds,
                    depth = voiceDucking.depth,
                )
            } else {
                existing.attackSeconds = voiceDucking.attackSeconds
                existing.depth = voiceDucking.depth
            }
        } else {
            ducking.clear()
        }

        // Compressor — reuse the instance to preserve the envelope follower across notes; clear it when
        // the owner has no compressor (so it doesn't linger from a previous owner).
        val compSettings = voice.compressor
        if (compSettings != null) {
            val existing = compressor.compressor
            if (existing == null) {
                compressor.compressor = Compressor(
                    sampleRate = reverb.reverb.sampleRate,
                    thresholdDb = compSettings.thresholdDb,
                    ratio = compSettings.ratio,
                    kneeDb = compSettings.kneeDb,
                    attackSeconds = compSettings.attackSeconds,
                    releaseSeconds = compSettings.releaseSeconds,
                )
            } else {
                existing.thresholdDb = compSettings.thresholdDb
                existing.ratio = compSettings.ratio
                existing.kneeDb = compSettings.kneeDb
                existing.attackSeconds = compSettings.attackSeconds
                existing.releaseSeconds = compSettings.releaseSeconds
            }
        } else {
            compressor.compressor = null
        }
    }

    /** Turn every bus effect off AND clear its internal state — called when the orbit deactivates (lease
     *  freed) so a reused orbit starts from a clean slate and never replays a previous owner's tail. */
    private fun resetBusEffects() {
        body.reset()
        vowel.reset()
        delay.delayLine.delayTimeSeconds = 0.0
        delay.delayLine.feedback = 0.0
        delay.delayLine.reset() // clear the delay ring, not just the params
        reverb.reverb.roomSize = 0.0
        reverb.reverb.reset() // clear the comb/allpass tail, not just the params
        phaser.phaser.depth = 0.0
        compressor.compressor = null
        ducking.clear()
    }

    fun clear() {
        if (!isActive) return

        mixBuffer.clear()
        delaySendBuffer.clear()
        reverbSendBuffer.clear()
    }

    /**
     * Processes all bus effects in pipeline order: Delay → Reverb → Phaser → Compressor.
     *
     * Ducking is NOT processed here — see [Cylinders.processAndMix].
     */
    fun processEffects() {
        if (!isActive) return

        for (effect in pipeline) {
            effect.process(katalystContext)
        }
    }

    /**
     * Applies ducking using the resolved sidechain buffer.
     *
     * Called by [Cylinders] after all orbits have processed their main pipeline,
     * since ducking needs cross-orbit access.
     */
    fun processDucking(sidechainMixBuffer: StereoBuffer?) {
        if (!isActive) return

        katalystContext.sidechainBuffer = sidechainMixBuffer
        ducking.process(katalystContext)
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
    fun tryDeactivate() {
        if (!isActive) return

        if (!isMixBufferSilent()) {
            silentBlockCount = 0
            return
        }

        silentBlockCount++

        if (silentBlockCount < silentBlocksBeforeTailCheck) return

        fun delayHasTail() = delay.delayLine.delayTimeSeconds > 0.001 && delay.delayLine.hasTail()
        fun reverbHasTail() = reverb.reverb.roomSize > 0.001 && reverb.reverb.hasTail()

        if (reverbHasTail() || delayHasTail()) {
            silentBlockCount = 0
            return
        }

        isActive = false
        silentBlockCount = 0
        // Free the orbit lease and reset all bus effects so a reused/reactivated orbit starts clean and
        // is reconfigured by whichever voice next claims it.
        resetBusEffects()
        lease.reset()
    }

    private fun isMixBufferSilent(): Boolean {
        val threshold = 0.00001
        for (sample in mixBuffer.left) {
            if (sample > threshold || sample < -threshold) return false
        }
        for (sample in mixBuffer.right) {
            if (sample > threshold || sample < -threshold) return false
        }
        return true
    }
}
