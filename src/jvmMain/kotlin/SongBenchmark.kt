/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.peekandpoke.klang.audio_be.AudioBackendContext
import io.peekandpoke.klang.audio_be.KlangAudioRenderer
import io.peekandpoke.klang.audio_bridge.KlangPattern
import io.peekandpoke.klang.audio_bridge.KlangTime
import io.peekandpoke.klang.audio_bridge.PipelineValue
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.SoundValue
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.uniqueId
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull
import io.peekandpoke.klang.sprudel.lang.sprudelLib
import kotlinx.coroutines.runBlocking
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

/**
 * Song-level CPU benchmark.
 *
 * Unlike [io.peekandpoke.klang.audio_benchmark] (which times isolated effects/ignitors built from
 * hand-made [VoiceData]), this harness compiles **real KlangScript/sprudel song code** to a
 * [KlangPattern] and drives it through the actual offline DSP graph — the same pipeline the
 * browser worklet runs — while timing every render block.
 *
 * The point is to attribute CPU cost to individual voices and to individual effect stages, by
 * comparing a full voice chain against progressively stripped-down versions of itself (see
 * [SongBenchmarkCases]).
 *
 * Metrics per case:
 *  - **medianRtf**   — total render-time / total audio-time (median across measure passes).
 *                      RTF < 1.0 = faster than real-time. This is the steady-state average cost.
 *  - **peakBlockRtf** — the single busiest render block's time / one block's audio duration
 *                      (median of per-pass maxima). This is the "worst render cycle" number that
 *                      corresponds to the browser's per-render-quantum CPU spikes.
 *  - **onsets**      — number of scheduled voice events in the window (explains how `unison` /
 *                      `superimpose` / note density inflate cost).
 */
class SongBenchmark(
    val sampleRate: Int = 48_000,
    /**
     * The canonical render quantum — `peakBlockRtf` is only comparable to the browser's per-render
     * quantum spikes if it measures the block size the browser actually renders. A larger block also
     * amortises per-block fixed work over more frames (and changes the sound: `driftUpdateRate` is
     * block-derived), so it would flatter the numbers AND benchmark a different signal.
     */
    val blockFrames: Int = AudioBackendContext.RENDER_QUANTUM_FRAMES,
    private val samples: Samples? = null,
) {
    companion object {
        /**
         * How much of the start of each pass is excluded from the peak-block figure — long enough to
         * cover the one-time lazy allocations (the ~7.68 MB per-orbit delay rings), short enough to
         * still measure most of the music. Was implicitly ~341 ms when this was a 32-block constant
         * at 512 frames.
         */
        private const val PEAK_SKIP_SECONDS: Double = 0.35
    }

    data class Case(
        val name: String,
        val code: String,
        val group: String = "",
        val rpm: Double = 34.5,
        val cycles: Int = 8,
        val warmupPasses: Int = 2,
        val measurePasses: Int = 5,
    )

    data class Result(
        val name: String,
        val group: String,
        val onsets: Int,
        val medianRtf: Double,
        val peakBlockRtf: Double,
        val renderUsPerCycle: Double,
        val audioMsPerCycle: Double,
        val error: String? = null,
    )

    private data class PassMetrics(val totalRenderUs: Double, val maxBlockUs: Double, val onsets: Int)

    fun run(cases: List<Case>): List<Result> = cases.map { runCase(it) }

    private fun runCase(case: Case): Result {
        val audioMsPerCycle = 1000.0 / (case.rpm / 60.0)
        val audioUsPerBlock = blockFrames.toDouble() / sampleRate * 1_000_000.0

        val compiled = try {
            compile(case.code)
        } catch (e: Throwable) {
            return Result(case.name, case.group, 0, 0.0, 0.0, 0.0, audioMsPerCycle, error = e.message ?: e.toString())
        } ?: return Result(case.name, case.group, 0, 0.0, 0.0, 0.0, audioMsPerCycle, error = "compile returned null")

        // Warmup passes prime the JIT and force lazy buffer allocation.
        repeat(case.warmupPasses) { renderPass(compiled, case, capture = false) }

        val passes = (0 until case.measurePasses).map { renderPass(compiled, case, capture = true) }

        val totals = passes.map { it.totalRenderUs }.sorted()
        val peaks = passes.map { it.maxBlockUs }.sorted()
        val medianTotalUs = totals[totals.size / 2]
        val medianPeakUs = peaks[peaks.size / 2]

        val totalAudioUs = audioMsPerCycle * case.cycles * 1000.0
        val medianRtf = medianTotalUs / totalAudioUs
        val peakBlockRtf = medianPeakUs / audioUsPerBlock
        val renderUsPerCycle = medianTotalUs / case.cycles

        return Result(
            name = case.name,
            group = case.group,
            onsets = passes.first().onsets,
            medianRtf = medianRtf,
            peakBlockRtf = peakBlockRtf,
            renderUsPerCycle = renderUsPerCycle,
            audioMsPerCycle = audioMsPerCycle,
        )
    }

    /**
     * One full render pass over [Case.cycles] cycles. Setup (query/schedule/preload) happens
     * OUTSIDE the timer; only the [KlangAudioRenderer.renderBlock] calls are measured.
     */
    private fun renderPass(pattern: KlangPattern, case: Case, capture: Boolean): PassMetrics {
        val cps = case.rpm / 60.0
        val klangTime = KlangTime.create()
        val commLink = KlangCommLink()
        val renderer = KlangAudioRenderer.create(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            commLink = commLink.backend,
            performanceTimeMs = { klangTime.internalMsNow() },
        )
        val ignitorRegistry = renderer.ignitorRegistry
        val pipelineRegistry = renderer.pipelineRegistry
        val voiceScheduler = renderer.voices
        renderer.setBackendStartTime(0.0)

        val rawEvents = pattern.queryEvents(fromCycles = 0.0, toCycles = case.cycles.toDouble(), cps = cps)

        // Register inline oscillators + pipelines (same as KlangOfflineRenderer).
        rawEvents.asSequence()
            .mapNotNull { it.sound as? SoundValue.Osc }
            .forEach { sv ->
                val name = sv.osc.uniqueId()
                if (!ignitorRegistry.contains(name)) ignitorRegistry.register(name, sv.osc)
            }
        rawEvents.asSequence()
            .mapNotNull { it.pipeline as? PipelineValue.Dsl }
            .forEach { pipelineRegistry.register(it.pipeline.uniqueId(), it.pipeline) }

        data class Ev(val start: Double, val dur: Double, val data: VoiceData)

        val events = rawEvents.map { Ev(it.startCycles, it.durationCycles, it.toVoiceData()) }

        // Preload samples (drums etc.) so those voices actually render.
        val s = samples
        if (s != null) {
            val reqs = events.map { it.data.asSampleRequest() }
                .filter { !ignitorRegistry.contains(it.sound ?: "") }
                .toSet()
            runBlocking {
                for (req in reqs) {
                    try {
                        val loaded = s.get(req) ?: continue
                        val pcm = loaded.pcm ?: continue
                        voiceScheduler.addSample(
                            KlangCommLink.Cmd.Sample.Complete(
                                req = req, note = loaded.sample.note, pitchHz = loaded.sample.pitchHz, sample = pcm,
                            )
                        )
                    } catch (_: Exception) {
                    }
                }
            }
        }

        val secPerCycle = 1.0 / cps
        for (ev in events) {
            val start = ev.start * secPerCycle
            voiceScheduler.scheduleVoice(
                ScheduledVoice(
                    playbackId = "bench",
                    data = ev.data,
                    startTime = start,
                    gateEndTime = start + ev.dur * secPerCycle,
                    playbackStartTime = 0.0,
                )
            )
        }

        val totalFrames = (case.cycles * secPerCycle * sampleRate).toInt()
        val out = ShortArray(blockFrames * 2)
        val numBlocks = totalFrames / blockFrames

        var maxBlockUs = 0.0
        val startAll = TimeSource.Monotonic.markNow()

        // NB: renderBlock() → PlaybackEngine.renderInto() already calls scheduler.process(cursorFrame)
        // internally, so we must NOT drive the scheduler ourselves (would double-advance timing).
        //
        // Each pass uses a FRESH renderer, so the first blocks pay a one-time lazy-allocation spike
        // (e.g. the ~7.68 MB delay rings). Skip those when computing the steady-state peak so
        // peakBlockRtf reflects the busiest *musical* block, not cold-start allocation.
        // A DURATION, not a block count: the allocation happens at a point in the *song* (the first
        // voice to touch an orbit), so a fixed block count skips a different amount of music at
        // every block size. At 512 frames `32` meant ~341 ms; at 128 it would mean ~85 ms, letting
        // a first-note allocation on a 1/8 or 1/16 land inside the measured window and masquerade
        // as a DSP cost.
        // Covers the START-OF-SONG allocations only. An orbit gated in by `filterWhen` (Seltsamere
        // Dinge brings orbits 4 and 5 in at cycles 16 and 28) allocates its cylinder deep inside the
        // measured window, and no start-of-pass skip can ever reach that. Those spikes still land in
        // peakBlockRtf — making the peak robust instead of time-gated (99th percentile, or drop the
        // top N blocks) is the real fix. See docs/tasks-archive/2026-08/20260807-block-size-parity.md.
        //
        // Bounded so a case shorter than the skip window cannot leave maxBlockUs at 0.0 and silently
        // report peakBlockRtf = 0.
        val peakSkipBlocks = (PEAK_SKIP_SECONDS * sampleRate / blockFrames).toInt()
            .coerceAtMost(numBlocks - 1)

        if (capture) {
            var frame = 0.0
            for (b in 0 until numBlocks) {
                val t = TimeSource.Monotonic.markNow()
                renderer.renderBlock(cursorFrame = frame, out = out)
                val us = t.elapsedNow().toDouble(DurationUnit.MICROSECONDS)
                if (b >= peakSkipBlocks && us > maxBlockUs) {
                    maxBlockUs = us
                }
                frame += blockFrames
            }
        } else {
            var frame = 0.0
            for (b in 0 until numBlocks) {
                renderer.renderBlock(cursorFrame = frame, out = out)
                frame += blockFrames
            }
        }

        val totalRenderUs = startAll.elapsedNow().toDouble(DurationUnit.MICROSECONDS)
        return PassMetrics(totalRenderUs = totalRenderUs, maxBlockUs = maxBlockUs, onsets = events.size)
    }

    /**
     * Compile KlangScript/sprudel [code] to a [KlangPattern]. The song code carries its own
     * `import * from "stdlib"/"sprudel"` lines, so we do NOT pre-import here (avoids double-import).
     */
    private fun compile(code: String): KlangPattern? {
        val engine = klangScript {
            registerLibrary(sprudelLib)
            registerBuiltInSongsAsModules()
        }
        val result = engine.execute(code + "\n")
        return result.toObjectOrNull<KlangPattern>()
    }
}
