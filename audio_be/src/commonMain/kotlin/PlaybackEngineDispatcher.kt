/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.PlaybackEngineDispatcher.Companion.create
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.engines.EngineRegistry
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_be.voices.VoiceScheduler
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink

/**
 * Owns the backend DSP graph and is the single place inbound [KlangCommLink.Cmd]s are routed.
 *
 * Both platform backends — the JVM `SourceDataLine` loop and the JS `AudioWorklet` — previously
 * duplicated this graph construction *and* an identical `when (cmd)` dispatch. They now build one
 * dispatcher via [create] and forward every command to [handle], shrinking the platform layer to a
 * thin pump: drain commands → render → convert/output → forward feedback.
 *
 * This is the seam the per-playback engine layer grows behind. Today it wraps a single
 * `VoiceScheduler` + `Cylinders`; later it will own a map of per-`playbackId` engines and the final
 * mix. See `docs/tasks/per-playback-engine.md` (deliverable D1).
 */
class PlaybackEngineDispatcher(
    val ignitorRegistry: IgnitorRegistry,
    val engineRegistry: EngineRegistry,
    val voices: VoiceScheduler,
    val renderer: KlangAudioRenderer,
) {
    /**
     * Route a single inbound command to its handler — the one Cmd dispatch site in the backend.
     *
     * Written as an exhaustive `when` *expression* on purpose: a future [KlangCommLink.Cmd]
     * subtype fails the build here until it is handled, rather than being silently dropped by
     * both backends at once.
     */
    fun handle(cmd: KlangCommLink.Cmd): Unit = when (cmd) {
        is KlangCommLink.Cmd.ScheduleVoice ->
            voices.scheduleVoice(voice = cmd.voice, clearScheduled = cmd.clearScheduled)

        is KlangCommLink.Cmd.ScheduleVoices ->
            voices.scheduleVoices(cmd.voices)

        is KlangCommLink.Cmd.ReplaceVoices ->
            voices.replaceVoices(cmd.playbackId, cmd.voices, cmd.afterTimeSec)

        is KlangCommLink.Cmd.Cleanup ->
            voices.cleanup(cmd.playbackId)

        is KlangCommLink.Cmd.ClearScheduled ->
            voices.clearScheduled(cmd.playbackId)

        is KlangCommLink.Cmd.Sample ->
            voices.addSample(msg = cmd)

        is KlangCommLink.Cmd.RegisterIgnitor ->
            ignitorRegistry.register(cmd.name, cmd.dsl)

        is KlangCommLink.Cmd.RegisterEngine ->
            engineRegistry.register(cmd.name, cmd.dsl)
    }

    companion object {
        /**
         * Builds the standard backend DSP graph — the single construction site shared by
         * `JvmAudioBackend`, `KlangAudioWorklet` and `KlangOfflineRenderer`.
         */
        fun create(
            sampleRate: Int,
            blockFrames: Int,
            commLink: KlangCommLink.BackendEndpoint,
            performanceTimeMs: () -> Double,
        ): PlaybackEngineDispatcher {
            val cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate)

            val ignitorRegistry = IgnitorRegistry().apply { registerDefaults() }
            val engineRegistry = EngineRegistry()

            val voices = VoiceScheduler(
                VoiceScheduler.Options(
                    commLink = commLink,
                    sampleRate = sampleRate,
                    blockFrames = blockFrames,
                    ignitorRegistry = ignitorRegistry,
                    engineRegistry = engineRegistry,
                    cylinders = cylinders,
                    performanceTimeMs = performanceTimeMs,
                )
            )

            val renderer = KlangAudioRenderer(
                sampleRate = sampleRate,
                blockFrames = blockFrames,
                voices = voices,
                cylinders = cylinders,
            )

            return PlaybackEngineDispatcher(
                ignitorRegistry = ignitorRegistry,
                engineRegistry = engineRegistry,
                voices = voices,
                renderer = renderer,
            )
        }
    }
}
