/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.PlaybackEngine.Companion.MAX_TAIL_HOLD_SECONDS
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystRegistry
import io.peekandpoke.klang.audio_be.master.MasterBus
import io.peekandpoke.klang.audio_be.voices.VoiceScheduler
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import kotlin.math.min

/**
 * One per-`playbackId` DSP instance. Owns its **full** render state — its own [VoiceScheduler]
 * (own scheduling timeline, solo state, scratch, `RenderContext`, `VoiceFactory`) and its own
 * [Cylinders] (orbits + FX). The only thing it does NOT own is the shared backend state
 * ([AudioBackendContext]); in particular the audio timeline (clock) is read from there, never per
 * engine — see `docs/tasks-archive/2026-09/20260904-per-playback-engine.md` (D2·b/D2·d).
 */
class PlaybackEngine(
    val scheduler: VoiceScheduler,
    val cylinders: Cylinders,
    private val masterBus: MasterBus,
    private val katalystRegistry: KatalystRegistry,
    private val blockFrames: Int,
    private val sampleRate: Int,
) {
    /**
     * This engine's own bus buffer, used only while a master chain is in play or the stopped
     * engine is being released; a playback without `master(…)` keeps the original zero-copy path
     * straight into the shared mix. Created with the engine (two block-sized arrays), so nothing
     * allocates in render.
     */
    private val bus = StereoBuffer(blockFrames)

    /**
     * Consecutive rendered blocks in which this engine had no active voice: how long its notes
     * have been over, saturated at [maxTailHoldBlocks] (an always-on engine never overflows it: the `min` in
     * [renderInto] caps it before `+ 1` could reach `Int.MAX_VALUE`, on every path, a never-audible one included).
     * A stopped playback's endless tails are held for this long before they are released (see
     * [isIdle]); a tail that sustains itself on an orbit keeps the orbit active, so a count that
     * waited for the orbits too would never start.
     */
    private var quietBlocks: Int = 0

    /** True once the playback was told to stop ([stop]); [resume] clears it before any release. */
    private var stopped: Boolean = false

    /** The release of the stopped engine's whole output, started once when its hold ends ([renderInto]). */
    private val tailRelease: TailRelease = TailRelease(sampleRate = sampleRate, blockFrames = blockFrames)

    /** True while the stopped engine's output is being released; there is no way back. */
    var isReleasing: Boolean = false
        private set

    /** True once the release has fallen under its floor: nothing this engine renders is heard. */
    private var released: Boolean = false

    /**
     * The tail-hold bound in blocks, derived per engine.
     *
     * Must be computed from the sample rate and block size, not hard-coded — a fixed block count is
     * a different *duration* at every block size and sample rate. Every host now renders at
     * [AudioBackendContext.RENDER_QUANTUM_FRAMES], but the sample rate still varies with the device,
     * and pinning a block count would silently re-introduce that divergence.
     */
    private val maxTailHoldBlocks: Int =
        ((MAX_TAIL_HOLD_SECONDS * sampleRate) / blockFrames).toInt().coerceAtLeast(1)

    /** The master bus — exposed for tests asserting cache/tail behaviour. */
    internal val masterBusForTest: MasterBus get() = masterBus

    /**
     * Registers a custom chain for this playback, for either position.
     *
     * The chain lands on this engine's fork, which is the registry every cylinder of this engine
     * AND its [MasterBus] resolve a name against (Katalyst step 3a; the output since phase 3 step 12
     * C5): a `katalyst(…)` reference on the voice stream then finds it, and the orbit installs it
     * the next time it is idle; a `master(…)` reference finds it, and the bus builds it on that
     * request. Registration builds nothing: a registered chain may never be played at the output.
     */
    fun registerKatalyst(name: String, dsl: KatalystDsl) = katalystRegistry.register(name, dsl)

    /** Render this engine's voices through its own cylinders, accumulating into [target]. */
    // NB `cursorFrame` is Double, not Int: it is an ABSOLUTE frame on the backend timeline, which
    // grows for the life of the backend and overflows Int after ~12.4 h. Exact below 2^53
    // (~5,950 years at 48 kHz). See RenderClock.cursorFrame. Per-sample offsets stay Int.
    fun renderInto(target: StereoBuffer, cursorFrame: Double) {
        cylinders.clearAll()
        scheduler.process(cursorFrame)
        // A master request that waited (a fade or drain was running, or its name was not
        // registered yet) lands here, before the fast-path check, so it can land on an engine
        // that has no master yet. One field read when nothing waits.
        masterBus.pollPendingSwap()

        // Track how long this engine's notes have been over: that, and not elapsed wall time, is
        // what a stopped playback's endless tails are held for (see [isIdle]).
        quietBlocks = if (scheduler.getActiveVoiceCount() != 0) 0 else min(quietBlocks + 1, maxTailHoldBlocks)

        if (!isReleasing && stopped && quietBlocks >= maxTailHoldBlocks && masterBus.isSettled && sustainsItself()) {
            // Decision (j): the hold is over and a tail that can never end still rings. Released,
            // never dropped, and with it whatever finite tail rings beside it. Waits for a master
            // swap to settle first: its own drain is capped and released ([ChainSwap]).
            isReleasing = true
            tailRelease.restart()
        }

        if (isReleasing) {
            renderReleased(target, cursorFrame)
            markMasterBusRendered()
            return
        }

        if (!masterBus.isActive) {
            // Fast path: an empty output chain, byte-identical to the engine before authored masters.
            cylinders.processAndMix(target, cursorFrame)
            markMasterBusRendered()
            return
        }

        // A master chain needs the engine's bus in isolation before it joins the shared mix.
        val ownBus = bus
        ownBus.clear()
        cylinders.processAndMix(ownBus, cursorFrame)
        masterBus.process(ownBus, blockFrames)
        target.addFrom(source = ownBus, frames = blockFrames)

        markMasterBusRendered()
    }

    /**
     * One block of the stopped engine's release: everything it renders, orbits and master alike,
     * goes through its own bus and is added to [target] under the [TailRelease] gain.
     */
    private fun renderReleased(target: StereoBuffer, cursorFrame: Double) {
        val ownBus = bus

        ownBus.clear()
        cylinders.processAndMix(ownBus, cursorFrame)

        if (masterBus.isActive) {
            masterBus.process(ownBus, blockFrames)
        }

        if (tailRelease.addReleased(target = target, source = ownBus)) {
            released = true
        }
    }

    /**
     * Tells the master bus a block has now been produced (master round M1). Called from the END
     * of each of [renderInto]'s three exits, so "this engine has rendered" is true from the next
     * block onward and a `master(…)` promoted in the engine's FIRST block still sees `false` and
     * is adopted at full weight instead of fading up from unmastered.
     *
     * The PLACEMENT is the contract, so it is deliberately at the end of the audio work rather
     * than somewhere in the middle whose position a reader has to reason about: called before
     * `scheduler.process` instead, the very first master would crossfade and M1 would be back.
     * It cannot live inside `MasterBus.process` either — the fast path above skips that entirely
     * while the bus is inactive, which is exactly the unmastered case being discriminated.
     */
    private fun markMasterBusRendered() {
        masterBus.markRendered()
    }

    /** True while this engine still has sound of its own (voices or ringing orbit buses). */
    private fun hasOwnSound(): Boolean =
        scheduler.getActiveVoiceCount() != 0 || cylinders.anyActive()

    /** True while a tail that can never end on its own rings, on an orbit or at the master. */
    private fun sustainsItself(): Boolean = cylinders.anySustainsItself() || masterBus.sustainsItself()

    /**
     * The playback was told to stop (`Cmd.Cleanup`): from now on its finite tails ring out and its
     * endless ones are released after [MAX_TAIL_HOLD_SECONDS] ([isIdle]).
     */
    fun stop() {
        stopped = true
    }

    /**
     * The stopped playback was scheduled again before it was disposed (a resume). Only valid while
     * not [isReleasing]: a release has no way back, so the dispatcher detaches a releasing engine
     * instead of resuming it.
     */
    fun resume() {
        stopped = false
    }

    /**
     * The engine's end: every rented unit (orbit delay rings, reverb networks, master chain units)
     * goes back to the backend's warehouse (2f). Called by the dispatcher exactly once, after the
     * engine has been removed from the render set; nothing renders through it afterwards.
     */
    fun dispose() {
        cylinders.releaseAll()
        masterBus.releaseAll()
    }

    /**
     * True once this engine may be disposed: nothing of it can be heard any more. Either it has no
     * active voices, all its cylinders have gone silent, its master swap has settled **and** the
     * master chain has stopped ringing; or its release has run out.
     *
     * The master check matters for a song whose reverb/delay lives on the master rather than on an
     * orbit: without it, disposing the drained engine would chop the master tail mid-decay (the
     * orbit buses protect their own tails via `Cylinder.tryDeactivate`). The swap check keeps a
     * master fade or drain from being dropped mid-way; the swap caps its own drain.
     *
     * **After a stop, no hard cut, ever** (step 12 decision (j), maintainer 2026-09-28, refined
     * the same day: release only ENDLESS tails). Every tail that can end on its own rings out in
     * full, however long it takes. Only a tail that can NEVER end ([KatalystChain.sustainsItself]:
     * a delay recirculating at `|feedback| >= 1`, on an orbit or at the master) would keep a
     * stopped engine for ever; while one rings [MAX_TAIL_HOLD_SECONDS] after the notes ended (and
     * the master swap has settled), the engine's WHOLE output is released ([TailRelease], 60 dB per
     * 3 s from exactly 1, retired under -90 dB, 4.5 s), and idle once that has run out. The simple
     * rule: a finite tail still ringing at that moment is released with it. Only a song with an
     * endless tail is affected, and it is bounded. So a stopped engine lives as long as its longest
     * finite tail, or the hold plus the release when an endless one is present. A playback that
     * was not stopped is never released: its drone is the authored sound.
     */
    fun isIdle(): Boolean {
        if (isReleasing) {
            return released
        }

        if (hasOwnSound()) {
            return false
        }

        if (!masterBus.isSettled) {
            return false
        }

        return !masterBus.isRinging
    }

    companion object {
        /**
         * How long a stopped playback's ENDLESS tails are held before the engine is released
         * ([isIdle]): a delay recirculating at `|feedback| >= 1` never decays, so without a bound a
         * stopped playback with one would render for ever. Counted from the moment its notes
         * ended. A tail that ends on its own is never bounded by this: it rings out in full. Only
         * when an endless tail is present can a finite one be released with it (the simple rule in
         * [isIdle]); a normalized `size` 1.0 Freeverb is ~94 dB down after this long (on its
         * slowest mode), so a reverb released then is not heard to go.
         */
        private const val MAX_TAIL_HOLD_SECONDS = 20.0

        /** Builds an engine: its own [Cylinders] + a [VoiceScheduler] wired to the shared [context]. */
        fun create(context: AudioBackendContext): PlaybackEngine {
            // ONE fork per engine, shared by everything that needs it: this engine registers its
            // chains here, every cylinder it rents resolves a `katalyst(…)` name against it, and the
            // master bus resolves a `master(…)` name against it. It dies with the engine, so a
            // chain cannot outlive the playback that declared it.
            val katalystRegistry = context.katalystRegistry.fork()
            val cylinders = Cylinders(
                blockFrames = context.blockFrames,
                sampleRate = context.sampleRate,
                units = context.warehouse.cylinders,
                katalysts = katalystRegistry,
            )
            // The bus is built first and handed to the scheduler as the sink for `master(…)` events,
            // so neither has to know about the other's lifecycle.
            val masterBus = MasterBus(
                sampleRate = context.sampleRate,
                blockFrames = context.blockFrames,
                registry = katalystRegistry,
                rings = context.warehouse.sized,
                reverbs = context.warehouse.reverbs,
            )
            val scheduler = VoiceScheduler(
                VoiceScheduler.Options(context = context, cylinders = cylinders, masterBus = masterBus)
            )
            return PlaybackEngine(
                scheduler = scheduler,
                cylinders = cylinders,
                masterBus = masterBus,
                katalystRegistry = katalystRegistry,
                blockFrames = context.blockFrames,
                sampleRate = context.sampleRate,
            )
        }
    }
}
