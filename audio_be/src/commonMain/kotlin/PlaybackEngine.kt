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
 *
 * ## The end of life: one [Phase]
 *
 * The engine's whole lifecycle is its [phase] (tidy-up step 9, audit item C3.1). The dispatcher asks it and never
 * keeps a lifecycle of its own; the offline renderer never stops its engine. States times events, every transition
 * (a dash: the phase stays):
 *
 * | phase \ event | [renderInto]                                                        | [stop]    | [resume]  | [dispose]  |
 * |---------------|---------------------------------------------------------------------|-----------|-----------|------------|
 * | `Playing`     | renders; counts its quiet blocks                                    | `Stopped` | -         | `Disposed` |
 * | `Stopped`     | renders; `Releasing` once the hold is over and an endless tail rings | -         | `Playing` | `Disposed` |
 * | `Releasing`   | renders under the release; `Released` when it falls under its floor | -         | -         | `Disposed` |
 * | `Released`    | renders under the release, which is 0 from here on                  | -         | -         | `Disposed` |
 * | `Disposed`    | never rendered (the dispatcher drops an engine before disposing it) | -         | -         | -          |
 *
 * - **The hold** ([renderInto], `Stopped` only): [MAX_TAIL_HOLD_SECONDS] of quiet blocks counted from the last
 *   active voice (the count runs while `Playing` too, so a stop after a long silence holds only the rest), the
 *   master swap settled, and a tail that can never end still ringing ([sustainsItself]). A finite tail never
 *   triggers it: it rings out in full.
 * - **No way back from a release:** [resume] in `Releasing` or `Released` changes nothing; the dispatcher detaches
 *   such an engine from its id instead and gives the id a fresh one.
 * - **Idle** ([isIdle]), when the dispatcher may dispose a stopped engine: `Playing` and `Stopped` when nothing of
 *   it sounds and its master has settled and stopped ringing; `Releasing` never; `Released` and `Disposed` always.
 *
 * Every phase is a `data object`: none carries data of its own (`docs/plans/effect-state-machines.md` §1). The
 * quiet-block count is the engine's, because `Playing` counts it and `Stopped` reads it; the [TailRelease] is a
 * resource created with the engine, which `Releasing` restarts and both release phases render through.
 */
class PlaybackEngine(
    /**
     * The id the dispatcher files this engine under (the offline renderer's one engine has
     * the fixed id `"offline"`). The scheduler's context takes its id from the voices.
     */
    val playbackId: String,
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
     * The engine's end of life, one state at a time; the transition table is in the class KDoc.
     */
    sealed class Phase {
        /** Scheduled and rendering, not told to stop. */
        data object Playing : Phase()

        /** Told to stop ([stop]): its finite tails ring out, its endless ones are held; [resume] returns to Playing. */
        data object Stopped : Phase()

        /** The hold is over and an endless tail rang: the whole output is being released. No way back. */
        data object Releasing : Phase()

        /** The release has fallen under its floor: nothing this engine renders is heard. Idle. */
        data object Released : Phase()

        /** Its units are back in the warehouse ([dispose]); it renders no more. */
        data object Disposed : Phase()
    }

    /** Where this engine is in its life; every transition is in the class KDoc. */
    var phase: Phase = Phase.Playing
        private set

    /**
     * Consecutive rendered blocks in which this engine had no active voice: how long its notes
     * have been over, saturated at [maxTailHoldBlocks] (an always-on engine never overflows it: the `min` in
     * [renderInto] caps it before `+ 1` could reach `Int.MAX_VALUE`, on every path, a never-audible one included).
     * Counted in every phase, `Playing` included: a stopped playback's endless tails are held until it reaches the
     * bound (see [isIdle]); a tail that sustains itself on an orbit keeps the orbit active, so a count that
     * waited for the orbits too would never start.
     */
    private var quietBlocks: Int = 0

    /** The release of the stopped engine's whole output, restarted once when its hold ends ([renderInto]). */
    private val tailRelease: TailRelease = TailRelease(sampleRate = sampleRate, blockFrames = blockFrames)

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

    /**
     * Render this engine's voices through its own cylinders, accumulating into [target]: one path for every phase
     * (audit item A2.11).
     *
     * - **Straight into [target]** while neither a master chain nor a release is in play: byte-identical to the
     *   engine before authored masters (the orbits sum straight into the shared mix, no copy).
     * - **Through the engine's own bus** otherwise: the orbits sum into it, the master chain runs on it when one
     *   is active, and it joins [target] whole, or under the [TailRelease] gain while `Releasing` or `Released`.
     *
     * The master bus learns that a block has been produced at the END of the call (master round M1): "this engine
     * has rendered" is true from the next block on, so a `master(…)` promoted in the engine's FIRST block still sees
     * `false` and is adopted at full weight instead of fading up from unmastered. Called before `scheduler.process`
     * instead, the very first master would crossfade; and it cannot live inside `MasterBus.process`, which the
     * straight path skips while the bus is inactive, exactly the unmastered case being told apart.
     */
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

        if (phase == Phase.Stopped && quietBlocks >= maxTailHoldBlocks && masterBus.isSettled && sustainsItself()) {
            // Decision (j): the hold is over and a tail that can never end still rings. Released,
            // never dropped, and with it whatever finite tail rings beside it. Waits for a master
            // swap to settle first: its own drain is capped and released ([ChainSwap]).
            phase = Phase.Releasing
            tailRelease.restart()
        }

        val releasing = when (phase) {
            Phase.Playing, Phase.Stopped, Phase.Disposed -> false
            Phase.Releasing, Phase.Released -> true
        }
        // Read before the orbits render: they cannot change it (only the swap's own process can).
        val mastered = masterBus.isActive

        if (!releasing && !mastered) {
            cylinders.processAndMix(target, cursorFrame)
        } else {
            val ownBus = bus

            ownBus.clear()
            cylinders.processAndMix(ownBus, cursorFrame)

            if (mastered) {
                masterBus.process(ownBus, blockFrames)
            }

            if (!releasing) {
                target.addFrom(source = ownBus, frames = blockFrames)
            } else if (tailRelease.addReleased(target = target, source = ownBus)) {
                // Under the floor from this block on: true in every block after, and `Released` stays.
                phase = Phase.Released
            }
        }

        masterBus.markRendered()
    }

    /** True while this engine still has sound of its own (voices or ringing orbit buses). */
    private fun hasOwnSound(): Boolean =
        scheduler.getActiveVoiceCount() != 0 || cylinders.anyActive()

    /** True while a tail that can never end on its own rings, on an orbit or at the master. */
    private fun sustainsItself(): Boolean = cylinders.anySustainsItself() || masterBus.sustainsItself()

    /**
     * The playback was told to stop (`Cmd.Cleanup`): from now on its finite tails ring out and its
     * endless ones are released after [MAX_TAIL_HOLD_SECONDS] ([isIdle]). `Playing` becomes `Stopped`; in any
     * other phase nothing changes (a second stop, a release under way).
     */
    fun stop() {
        phase = when (phase) {
            Phase.Playing -> Phase.Stopped
            Phase.Stopped, Phase.Releasing, Phase.Released, Phase.Disposed -> phase
        }
    }

    /**
     * The stopped playback was scheduled again before it was disposed (a resume): `Stopped` becomes `Playing`.
     * A release has no way back, so in `Releasing` and `Released` nothing changes; the dispatcher detaches such an
     * engine instead of resuming it.
     */
    fun resume() {
        phase = when (phase) {
            Phase.Stopped -> Phase.Playing
            Phase.Playing, Phase.Releasing, Phase.Released, Phase.Disposed -> phase
        }
    }

    /**
     * The engine's end: every rented unit (orbit delay rings, reverb networks, master chain units)
     * goes back to the backend's warehouse (2f), and the phase is `Disposed`. Called by the dispatcher exactly
     * once, after the engine has been removed from the render set; nothing renders through it afterwards.
     */
    fun dispose() {
        cylinders.releaseAll()
        masterBus.releaseAll()
        phase = Phase.Disposed
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
    fun isIdle(): Boolean = when (phase) {
        Phase.Playing, Phase.Stopped -> !hasOwnSound() && masterBus.isSettled && !masterBus.isRinging
        Phase.Releasing -> false
        Phase.Released, Phase.Disposed -> true
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

        /** Builds an engine for [playbackId]: its own [Cylinders] + a [VoiceScheduler] wired to the shared [context]. */
        fun create(context: AudioBackendContext, playbackId: String): PlaybackEngine {
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
                playbackId = playbackId,
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
