/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.engines.EngineRegistry
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink

/**
 * The backend host: routes inbound [KlangCommLink.Cmd]s and renders the final block.
 *
 * It owns the [AudioBackendContext] (shared services + the read-only clock), the **mutable**
 * [BackendClock] it advances each block, the final [MasterStage], and a
 * `Map<playbackId, PlaybackEngine>` — one fully-isolated engine per playback, created lazily and
 * disposed once told to stop ([cleanup]) and fully drained. Both platform backends shrink to a thin
 * pump: drain commands → [renderBlock] → convert/output → forward feedback. See
 * `docs/tasks/per-playback-engine.md`.
 */
class PlaybackEngineDispatcher(
    private val context: AudioBackendContext,
    private val clock: BackendClock,
) {
    // playbackId -> engine. LinkedHashMap for deterministic render/iteration order.
    private val engines = LinkedHashMap<String, PlaybackEngine>()

    // playbackIds told to stop (Cmd.Cleanup); disposed once their engine has fully drained.
    private val draining = mutableSetOf<String>()

    private val mix = StereoBuffer(context.blockFrames)
    private val master = MasterStage(sampleRate = context.sampleRate, blockFrames = context.blockFrames)

    val ignitorRegistry: IgnitorRegistry get() = context.ignitorRegistry
    val engineRegistry: EngineRegistry get() = context.engineRegistry
    val sampleStore: SampleStore get() = context.sampleStore

    /**
     * Sets the backend epoch (the one audio timeline). Call **once at startup**, before any block is
     * rendered — the cursor advances from here.
     */
    fun setBackendStartTime(startTimeSec: Double) {
        clock.startTimeSec = startTimeSec
    }

    private fun engineFor(playbackId: String): PlaybackEngine {
        // Re-scheduling to a draining playback cancels the pending disposal (e.g. resume after pause).
        draining.remove(playbackId)
        return engines.getOrPut(playbackId) { PlaybackEngine.create(context) }
    }

    /**
     * Route a single inbound command to the engine for its `playbackId` (creating it on demand).
     * Exhaustive `when` *expression*: a new [KlangCommLink.Cmd] subtype fails the build until handled.
     */
    fun handle(cmd: KlangCommLink.Cmd): Unit = when (cmd) {
        is KlangCommLink.Cmd.ScheduleVoice ->
            engineFor(cmd.playbackId).scheduler.scheduleVoice(voice = cmd.voice, clearScheduled = cmd.clearScheduled)

        is KlangCommLink.Cmd.ScheduleVoices ->
            scheduleVoices(cmd.playbackId, cmd.voices)

        is KlangCommLink.Cmd.ReplaceVoices ->
            replaceVoices(cmd.playbackId, cmd.voices, cmd.afterTimeSec)

        is KlangCommLink.Cmd.Cleanup ->
            cleanup(cmd.playbackId)

        is KlangCommLink.Cmd.ClearScheduled ->
            clearScheduled(cmd.playbackId)

        is KlangCommLink.Cmd.Sample ->
            context.sampleStore.addSample(cmd)

        is KlangCommLink.Cmd.RegisterIgnitor ->
            engineFor(cmd.playbackId).scheduler.registerIgnitor(cmd.name, cmd.dsl)

        is KlangCommLink.Cmd.RegisterEngine ->
            engineFor(cmd.playbackId).scheduler.registerEngine(cmd.name, cmd.dsl)
    }

    private fun scheduleVoices(playbackId: String, voices: List<ScheduledVoice>) {
        if (voices.isNotEmpty()) {
            engineFor(playbackId).scheduler.scheduleVoices(voices)
        }
    }

    private fun replaceVoices(playbackId: String, voices: List<ScheduledVoice>, afterTimeSec: Double?) {
        // Replace targets an EXISTING playback — never lazily create an engine here, so a
        // "replace with nothing" cannot materialize (and then leak) an empty engine.
        engines[playbackId]?.scheduler?.replaceVoices(playbackId, voices, afterTimeSec)
    }

    /** Stop scheduling for a playback and let it ring out; disposed once drained (see [renderBlock]). */
    private fun cleanup(playbackId: String) {
        engines[playbackId]?.scheduler?.cleanup(playbackId)
        draining.add(playbackId)
    }

    private fun clearScheduled(playbackId: String) {
        engines[playbackId]?.scheduler?.clearScheduled(playbackId)
    }

    /** Immediate disposal (warmup teardown) — does not let voices ring out. */
    fun cleanupHard(playbackId: String) {
        engines.remove(playbackId)?.scheduler?.cleanupHard(playbackId)
        draining.remove(playbackId)
    }

    /** Render one block to [out]: advance the clock, every engine accumulates into the mix, then master. */
    fun renderBlock(cursorFrame: Int, out: ShortArray) {
        clock.cursorFrame = cursorFrame
        mix.clear()

        // processAndMix accumulates additively, so engines simply render into the same mix in turn.
        // (#11: with one engine this is a straight render into the final mix.) Per-engine master gain
        // in D6 will need a scratch buffer here for the ≥2 case.
        for (engine in engines.values) {
            engine.renderInto(mix, cursorFrame)
        }

        master.process(mix, out)

        disposeDrainedEngines()
    }

    /** Dispose engines that were told to stop and have now fully gone quiet. No auto-GC of live engines. */
    private fun disposeDrainedEngines() {
        if (draining.isEmpty()) {
            return
        }
        val iter = draining.iterator()
        while (iter.hasNext()) {
            val playbackId = iter.next()
            val engine = engines[playbackId]
            if (engine == null || engine.isIdle()) {
                engines.remove(playbackId)
                iter.remove()
            }
        }
    }

    /** Reset the master post-chain (limiter envelope + DC blockers) after warmup. */
    fun resetPostChain() = master.reset()

    // ── Test / diagnostics inspection ────────────────────────────────────────────
    internal val activePlaybackIds: Set<String> get() = engines.keys
    internal fun engine(playbackId: String): PlaybackEngine? = engines[playbackId]

    companion object {
        /** Builds the backend host with its shared context + clock. Engines are created lazily per playback. */
        fun create(
            sampleRate: Int,
            blockFrames: Int,
            commLink: KlangCommLink.BackendEndpoint,
            performanceTimeMs: () -> Double,
        ): PlaybackEngineDispatcher {
            val clock = BackendClock(sampleRate)
            val context = AudioBackendContext.create(
                sampleRate = sampleRate,
                blockFrames = blockFrames,
                commLink = commLink,
                clock = clock,
                performanceTimeMs = performanceTimeMs,
            )
            return PlaybackEngineDispatcher(context = context, clock = clock)
        }
    }
}
