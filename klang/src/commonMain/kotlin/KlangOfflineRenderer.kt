/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_be.AudioBackendContext
import io.peekandpoke.klang.audio_be.KlangAudioRenderer
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KlangPattern
import io.peekandpoke.klang.audio_bridge.KlangTime
import io.peekandpoke.klang.audio_bridge.MasterValue
import io.peekandpoke.klang.audio_bridge.PipelineValue
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.SoundValue
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.uniqueId
import io.peekandpoke.klang.audio_fe.samples.Samples

/**
 * Offline renderer that drives the DSP graph directly at full CPU speed.
 *
 * Platform-independent: produces audio blocks via a callback.
 * Bypasses the real-time playback infrastructure entirely.
 */
class KlangOfflineRenderer(
    private val sampleRate: Int = 48_000,
    /**
     * Must stay at [AudioBackendContext.RENDER_QUANTUM_FRAMES] for the render to match live
     * playback — block size drives the analog-drift rate and the filter smoothing granularity,
     * so a "faster" larger block is a different sound, not just a faster render.
     */
    private val blockFrames: Int = AudioBackendContext.RENDER_QUANTUM_FRAMES,
) {
    data class Result(
        val durationSec: Double,
        val totalFrames: Int,
        val elapsedMs: Double,
    )

    /**
     * Render a pattern to audio.
     *
     * @param pattern the compiled pattern to render
     * @param cycles number of cycles to render
     * @param cyclesPerSecond tempo (cps = rpm / 60.0)
     * @param tailSec extra seconds after last note for reverb/delay tails
     * @param onBlock called for each rendered block with interleaved stereo 16-bit PCM
     */
    suspend fun render(
        pattern: KlangPattern,
        cycles: Int,
        cyclesPerSecond: Double,
        tailSec: Double = 2.0,
        customIgnitors: List<Pair<String, IgnitorDsl>> = emptyList(),
        samples: Samples? = null,
        onBlock: (samples: ShortArray, count: Int) -> Unit,
    ): Result {
        val klangTime = KlangTime.create()
        val startMs = klangTime.internalMsNow()

        // 1. Single-engine DSP graph for offline render-to-PCM — independent of the live
        //    per-playback dispatcher (which is the realtime host).
        val commLink = KlangCommLink()
        val renderer = KlangAudioRenderer.create(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            commLink = commLink.backend,
            performanceTimeMs = { klangTime.internalMsNow() },
        )
        val ignitorRegistry = renderer.ignitorRegistry
        val pipelineRegistry = renderer.pipelineRegistry
        val masterRegistry = renderer.masterRegistry
        val voiceScheduler = renderer.voices

        // Register this render's custom ignitors on top of the built-in defaults.
        for ((name, dsl) in customIgnitors) {
            if (ignitorRegistry.contains(name)) {
                println("[KlangOfflineRenderer] Custom ignitor '$name' overrides built-in sound")
            }
            ignitorRegistry.register(name, dsl)
        }

        renderer.setBackendStartTime(0.0)

        // 2. Query all events and cache voice data (toVoiceData() creates new objects)
        data class CachedEvent(
            val startCycles: Double,
            val durationCycles: Double,
            val voiceData: VoiceData,
        )

        val rawEvents = pattern.queryEvents(
            fromCycles = 0.0,
            toCycles = cycles.toDouble(),
            cps = cyclesPerSecond,
        )

        // Pre-register inline ignitors with the in-process BE so their synthetic names
        // (from IgnitorDsl.uniqueId()) are recognised at voice scheduling time.
        rawEvents.asSequence()
            .map { it.sound }
            .filterIsInstance<SoundValue.Osc>()
            .forEach { soundValue ->
                val name = soundValue.osc.uniqueId()
                if (!ignitorRegistry.contains(name)) {
                    ignitorRegistry.register(name, soundValue.osc)
                }
            }

        // Same for inline pipelines (register is idempotent — same name+dsl on repeat).
        rawEvents.asSequence()
            .map { it.pipeline }
            .filterIsInstance<PipelineValue.Dsl>()
            .forEach { pipelineRegistry.register(it.pipeline.uniqueId(), it.pipeline) }

        // Same for inline masters — so an offline render is as faithful as live playback.
        rawEvents.asSequence()
            .map { it.master }
            .filterIsInstance<MasterValue.Dsl>()
            .forEach { masterRegistry.register(it.master.uniqueId(), it.master) }

        val events = rawEvents.map { CachedEvent(it.startCycles, it.durationCycles, it.toVoiceData()) }

        // 3. Preload samples
        if (samples != null) {
            val sampleRequests = events
                .map { it.voiceData.asSampleRequest() }
                .filter { !ignitorRegistry.contains(it.sound ?: "") }
                .toSet()

            for (req in sampleRequests) {
                try {
                    val loaded = samples.get(req) ?: continue
                    val pcm = loaded.pcm ?: continue

                    voiceScheduler.addSample(
                        KlangCommLink.Cmd.Sample.Complete(
                            req = req,
                            note = loaded.sample.note,
                            pitchHz = loaded.sample.pitchHz,
                            sample = pcm,
                        )
                    )
                } catch (e: Exception) {
                    println("[KlangOfflineRenderer] Failed to load sample ${req.sound}: ${e.message}")
                }
            }
        }

        // 4. Schedule all voices
        val secPerCycle = 1.0 / cyclesPerSecond
        val playbackId = "offline"

        for (event in events) {
            val relativeStart = event.startCycles * secPerCycle
            val duration = event.durationCycles * secPerCycle

            voiceScheduler.scheduleVoice(
                ScheduledVoice(
                    playbackId = playbackId,
                    data = event.voiceData,
                    startTime = relativeStart,
                    gateEndTime = relativeStart + duration,
                    playbackStartTime = 0.0,
                )
            )
        }

        // 5. Calculate total frames
        val musicalDurationSec = cycles.toDouble() * secPerCycle
        val totalDurationSec = musicalDurationSec + tailSec
        // Render past the end by the engine's own output latency, otherwise the last samples are
        // still sitting in the master limiter's lookahead delay ring when the loop stops. Matters at
        // tailSec = 0.0, where the truncated tail is the actual music rather than a reverb tail.
        val totalFrames = (totalDurationSec * sampleRate).toInt() + renderer.latencyFrames

        // 6. Render loop
        val outShorts = ShortArray(blockFrames * 2)
        // Absolute backend frame — Double, see RenderClock.cursorFrame. An offline render is short
        // enough that Int would do, but the type has to match the live path.
        var currentFrame = 0.0

        while (currentFrame < totalFrames) {
            renderer.renderBlock(cursorFrame = currentFrame, out = outShorts)
            onBlock(outShorts, blockFrames * 2)
            currentFrame += blockFrames
        }

        val elapsedMs = klangTime.internalMsNow() - startMs

        return Result(
            durationSec = totalDurationSec,
            totalFrames = totalFrames,
            elapsedMs = elapsedMs,
        )
    }
}
