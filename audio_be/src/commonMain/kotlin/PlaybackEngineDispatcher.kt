/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.engines.EngineRegistry
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_be.voices.VoiceScheduler
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink

/**
 * The backend host: routes inbound [KlangCommLink.Cmd]s and renders the final block.
 *
 * It owns the shared resources (the [SampleStore] cache, the registries, the final [MasterStage])
 * and the [PlaybackEngine]s. Both platform backends (JVM `SourceDataLine`, JS `AudioWorklet`) shrink
 * to a thin pump: drain commands → [renderBlock] → convert/output → forward feedback.
 *
 * D2·b·1 wires a **single** engine (behaviour-identical to the old single-scheduler renderer);
 * D2·b·2 turns this into a `Map<playbackId, PlaybackEngine>` for per-playback isolation. See
 * `docs/tasks/per-playback-engine.md`.
 */
class PlaybackEngineDispatcher(
    sampleRate: Int,
    blockFrames: Int,
    val ignitorRegistry: IgnitorRegistry,
    val engineRegistry: EngineRegistry,
    val sampleStore: SampleStore,
    private val engine: PlaybackEngine,
) {
    private val mix = StereoBuffer(blockFrames)
    private val master = MasterStage(sampleRate = sampleRate, blockFrames = blockFrames)

    /** Compat accessor — the single engine's scheduler. D2·b·2 makes scheduling per-engine. */
    val voices: VoiceScheduler get() = engine.scheduler

    /**
     * Route a single inbound command. Exhaustive `when` *expression* on purpose: a new
     * [KlangCommLink.Cmd] subtype fails the build here until it is handled.
     */
    fun handle(cmd: KlangCommLink.Cmd): Unit = when (cmd) {
        is KlangCommLink.Cmd.ScheduleVoice ->
            engine.scheduler.scheduleVoice(voice = cmd.voice, clearScheduled = cmd.clearScheduled)

        is KlangCommLink.Cmd.ScheduleVoices ->
            engine.scheduler.scheduleVoices(cmd.voices)

        is KlangCommLink.Cmd.ReplaceVoices ->
            engine.scheduler.replaceVoices(cmd.playbackId, cmd.voices, cmd.afterTimeSec)

        is KlangCommLink.Cmd.Cleanup ->
            engine.scheduler.cleanup(cmd.playbackId)

        is KlangCommLink.Cmd.ClearScheduled ->
            engine.scheduler.clearScheduled(cmd.playbackId)

        is KlangCommLink.Cmd.Sample ->
            sampleStore.addSample(cmd)

        is KlangCommLink.Cmd.RegisterIgnitor ->
            ignitorRegistry.register(cmd.name, cmd.dsl)

        is KlangCommLink.Cmd.RegisterEngine ->
            engineRegistry.register(cmd.name, cmd.dsl)
    }

    /** Render one block to [out]: engine(s) → summed mix → master/output stage. */
    fun renderBlock(cursorFrame: Int, out: ShortArray) {
        // D2·b·1: a single engine renders straight into the final mix (#11 fast path, N=1).
        mix.clear()
        engine.renderInto(mix, cursorFrame)
        master.process(mix, out)
    }

    /** Pre-allocate cylinders during the warmup handshake. */
    fun preallocateCylinders() = engine.preallocateCylinders()

    /** Reset the master post-chain (limiter envelope + DC blockers) after warmup. */
    fun resetPostChain() = master.reset()

    companion object {
        /** Builds the backend host with its shared resources and a single engine. */
        fun create(
            sampleRate: Int,
            blockFrames: Int,
            commLink: KlangCommLink.BackendEndpoint,
            performanceTimeMs: () -> Double,
        ): PlaybackEngineDispatcher {
            val ignitorRegistry = IgnitorRegistry().apply { registerDefaults() }
            val engineRegistry = EngineRegistry()
            val sampleStore = SampleStore(commLink)

            val engine = PlaybackEngine.create(
                sampleRate = sampleRate,
                blockFrames = blockFrames,
                commLink = commLink,
                performanceTimeMs = performanceTimeMs,
                ignitorRegistry = ignitorRegistry,
                engineRegistry = engineRegistry,
                sampleStore = sampleStore,
            )

            return PlaybackEngineDispatcher(
                sampleRate = sampleRate,
                blockFrames = blockFrames,
                ignitorRegistry = ignitorRegistry,
                engineRegistry = engineRegistry,
                sampleStore = sampleStore,
                engine = engine,
            )
        }
    }
}
