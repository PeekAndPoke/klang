/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.PlaybackEngine.Companion.MAX_MASTER_TAIL_HOLD_SECONDS
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.master.MasterBus
import io.peekandpoke.klang.audio_be.voices.VoiceScheduler
import io.peekandpoke.klang.audio_bridge.MasterDsl

/**
 * One per-`playbackId` DSP instance. Owns its **full** render state — its own [VoiceScheduler]
 * (own scheduling timeline, solo state, scratch, `RenderContext`, `VoiceFactory`) and its own
 * [Cylinders] (orbits + FX). The only thing it does NOT own is the shared backend state
 * ([AudioBackendContext]); in particular the audio timeline (clock) is read from there, never per
 * engine — see `docs/tasks/per-playback-engine.md` (D2·b/D2·d).
 */
class PlaybackEngine(
    val scheduler: VoiceScheduler,
    val cylinders: Cylinders,
    private val masterBus: MasterBus,
    private val blockFrames: Int,
    private val sampleRate: Int,
) {
    /**
     * This engine's own bus buffer — only allocated once a master chain is actually in play, so a
     * playback without `master(…)` keeps the original zero-copy path.
     */
    private var bus: StereoBuffer? = null

    /** Consecutive rendered blocks in which this engine had no voices and no active cylinders. */
    private var quietBlocks: Int = 0

    /**
     * The tail-hold bound in blocks, derived per engine.
     *
     * Must be computed from the sample rate and block size, not hard-coded — a fixed block count is
     * a different *duration* at every block size and sample rate. Every host now renders at
     * [AudioBackendContext.RENDER_QUANTUM_FRAMES], but the sample rate still varies with the device,
     * and pinning a block count would silently re-introduce that divergence.
     */
    private val maxTailHoldBlocks: Int =
        ((MAX_MASTER_TAIL_HOLD_SECONDS * sampleRate) / blockFrames).toInt().coerceAtLeast(1)

    /** The master bus — exposed for tests asserting cache/tail behaviour. */
    internal val masterBusForTest: MasterBus get() = masterBus

    /** Registers a custom master chain for this playback. */
    fun registerMaster(name: String, dsl: MasterDsl) = masterBus.register(name, dsl)

    /** Render this engine's voices through its own cylinders, accumulating into [target]. */
    // NB `cursorFrame` is Double, not Int: it is an ABSOLUTE frame on the backend timeline, which
    // grows for the life of the backend and overflows Int after ~12.4 h. Exact below 2^53
    // (~5,950 years at 48 kHz). See RenderClock.cursorFrame. Per-sample offsets stay Int.
    fun renderInto(target: StereoBuffer, cursorFrame: Double) {
        cylinders.clearAll()
        scheduler.process(cursorFrame)

        // Track how long this engine has had nothing of its own left — that, and not elapsed wall
        // time, is what a master tail is allowed to hold open (see [isIdle]).
        quietBlocks = if (hasOwnSound()) 0 else quietBlocks + 1

        if (!masterBus.isActive) {
            // Fast path — byte-identical to the pre-MasterDsl engine.
            cylinders.processAndMix(target)
            return
        }

        // A master chain needs the engine's bus in isolation before it joins the shared mix.
        val ownBus = bus ?: StereoBuffer(blockFrames).also { bus = it }
        ownBus.clear()
        cylinders.processAndMix(ownBus)
        masterBus.process(ownBus, blockFrames)

        val targetL = target.left
        val targetR = target.right
        val busL = ownBus.left
        val busR = ownBus.right

        for (i in 0 until blockFrames) {
            targetL[i] += busL[i]
            targetR[i] += busR[i]
        }
    }

    /** True while this engine still has sound of its own (voices or ringing orbit buses). */
    private fun hasOwnSound(): Boolean =
        scheduler.getActiveVoiceCount() != 0 || cylinders.anyActive()

    /**
     * True once this engine has no active voices, all its cylinders have gone silent, **and** the
     * master chain has stopped ringing.
     *
     * The master check matters for a song whose reverb/delay lives on the master rather than on an
     * orbit: without it, disposing the drained engine would chop the master tail mid-decay (the
     * orbit buses protect their own tails via `Cylinder.reverbHasTail()`).
     *
     * The hold is bounded by [MAX_MASTER_TAIL_HOLD_SECONDS], counted from the moment the engine went
     * quiet — a delay with `feedback >= 1.0` recirculates without loss, so its ring never empties
     * and an unbounded hold would keep a stopped playback rendering (leaking one engine per stop).
     */
    fun isIdle(): Boolean {
        if (hasOwnSound()) {
            return false
        }

        if (quietBlocks >= maxTailHoldBlocks) {
            return true
        }

        return !masterBus.isRinging
    }

    companion object {
        /**
         * Upper bound on how long a master tail may hold a quiet engine open.
         *
         * Counted from the moment the engine fell quiet, so an ordinary song of any length keeps its
         * full tail; the bound only ends the pathological case of a tail that never decays (a delay
         * with `feedback >= 1.0` recirculates without loss). A `roomSize` 1.0 Freeverb is ~96 dB down
         * after this long, so a reverb can never be audibly cut by it.
         */
        private const val MAX_MASTER_TAIL_HOLD_SECONDS = 20.0

        /** Builds an engine: its own [Cylinders] + a [VoiceScheduler] wired to the shared [context]. */
        fun create(context: AudioBackendContext): PlaybackEngine {
            val cylinders = Cylinders(blockFrames = context.blockFrames, sampleRate = context.sampleRate)
            // The bus is built first and handed to the scheduler as the sink for `master(…)` events,
            // so neither has to know about the other's lifecycle.
            val masterBus = MasterBus(
                sampleRate = context.sampleRate,
                blockFrames = context.blockFrames,
                registry = context.masterRegistry.fork(),
            )
            val scheduler = VoiceScheduler(
                VoiceScheduler.Options(context = context, cylinders = cylinders, masterBus = masterBus)
            )
            return PlaybackEngine(
                scheduler = scheduler,
                cylinders = cylinders,
                masterBus = masterBus,
                blockFrames = context.blockFrames,
                sampleRate = context.sampleRate,
            )
        }
    }
}
