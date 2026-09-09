/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_be.AudioBackendContext
import io.peekandpoke.klang.audio_be.KlangAudioRenderer
import io.peekandpoke.klang.audio_be.voices.VoiceScheduler
import io.peekandpoke.klang.audio_bridge.AdsrDef
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.FilterDefs
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

class KlangBenchmark(
    private val sampleRate: Int = 44100,
    private val blockFrames: Int = AudioBackendContext.RENDER_QUANTUM_FRAMES,
) {
    companion object {
        /** Blocks of silence rendered before the first measurement, to wake the JIT and the CPU governor. */
        private const val WARMUP_BLOCKS = 50

        /** Blocks per measurement: ~145 ms of audio at 44.1 kHz, long enough to average out scheduler noise. */
        private const val BLOCKS_TO_MEASURE = 50

        /** Voices the ramp starts with. Even a struggling machine carries this many. */
        private const val INITIAL_VOICES = 16

        /**
         * Most the ramp may grow in one step. The RTF prediction alone would take a fast machine
         * from a handful of voices to its limit in four or five steps - correct, but the gauges
         * jump rather than climb. This paces the early steps so the needle sweeps.
         */
        private const val MAX_GROWTH = 1.4

        /** Smallest step the ramp takes, so the endgame cannot crawl one voice at a time. */
        private const val MIN_STEP = 4
    }

    data class Progress(
        val currentVoices: Int,
        val activeVoices: Int,
        val currentRtf: Double,
        val currentIteration: Int,
        val totalIterations: Int,
        val isComplete: Boolean,
        val result: Result? = null,
    )

    data class Result(
        val maxSafeVoices: Int,
        val rtfAtLimit: Double,
        val details: String,
        val rounds: Int,
    )

    private val _progress = StreamSource(
        Progress(
            currentVoices = 0,
            activeVoices = 0,
            currentRtf = 0.0,
            currentIteration = 0,
            totalIterations = 3,
            isComplete = false
        )
    )
    val progress: Stream<Progress> = _progress.readonly

    suspend fun run(
        targetRtf: Double = 0.9, // Stop when we use x % of CPU time
        maxVoicesCap: Int = 1000, // Don't go forever
        iterations: Int = 3, // Number of iterations to average
        onProgress: ((Progress) -> Unit)? = null,
    ) {
        console.log("[Benchmark] Starting Voice Stress Test with $iterations iterations...")

        // Emit initial progress
        val initialProgress = Progress(
            currentVoices = 0,
            activeVoices = 0,
            currentRtf = 0.0,
            currentIteration = 0,
            totalIterations = iterations,
            isComplete = false
        )
        _progress(initialProgress)
        onProgress?.invoke(initialProgress)

        // Run multiple iterations and collect results
        val results = mutableListOf<Result>()

        repeat(iterations) { iteration ->
            console.log("[Benchmark] Starting iteration ${iteration + 1}/$iterations...")

            val result = runSingleIteration(
                iteration = iteration + 1,
                totalIterations = iterations,
                targetRtf = targetRtf,
                maxVoicesCap = maxVoicesCap,
                onProgress = onProgress
            )

            results.add(result)

            // Emit progress update after each iteration completes
            val iterationProgress = Progress(
                currentVoices = 0,
                activeVoices = result.maxSafeVoices,
                currentRtf = result.rtfAtLimit,
                currentIteration = iteration + 1,
                totalIterations = iterations,
                isComplete = false
            )
            _progress(iterationProgress)
            onProgress?.invoke(iterationProgress)

            // Small delay between iterations
            if (iteration < iterations - 1) {
                delay(500.milliseconds)
            }
        }

        // Small delay before final result to ensure UI updates
        delay(100.milliseconds)

        // Calculate average result
        val avgMaxSafeVoices = (results.map { it.maxSafeVoices }.average()).toInt()
        val avgRtfAtLimit = results.map { it.rtfAtLimit }.average()

        val detailsText = buildString {
            appendLine("Average across $iterations runs:")
            appendLine("Max safe voices: $avgMaxSafeVoices")
            appendLine("RTF at limit: ${avgRtfAtLimit.asDynamic().toFixed(3)}")
            appendLine()
            appendLine("Individual runs:")
            results.forEachIndexed { index, result ->
                appendLine(
                    "  Run ${index + 1}: ${result.maxSafeVoices} voices (RTF: ${
                        result.rtfAtLimit.asDynamic().toFixed(3)
                    })"
                )
            }
        }

        val finalResult = Result(
            maxSafeVoices = avgMaxSafeVoices,
            rtfAtLimit = avgRtfAtLimit,
            details = detailsText,
            rounds = iterations,
        )

        val finalProgress = Progress(
            currentVoices = 0,
            activeVoices = avgMaxSafeVoices,
            currentRtf = avgRtfAtLimit,
            currentIteration = iterations,
            totalIterations = iterations,
            isComplete = true,
            result = finalResult
        )
        _progress(finalProgress)
        onProgress?.invoke(finalProgress)

        console.log("[Benchmark] All iterations complete. Average: $avgMaxSafeVoices voices")
    }

    private suspend fun runSingleIteration(
        iteration: Int,
        totalIterations: Int,
        targetRtf: Double,
        maxVoicesCap: Int,
        onProgress: ((Progress) -> Unit)?,
    ): Result {
        // 1. Setup Headless Environment
        val renderer = KlangAudioRenderer.create(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            commLink = KlangCommLink().backend,
        )
        val scheduler = renderer.voices
        renderer.setBackendStartTime(0.0)

        val outBuffer = ShortArray(blockFrames * 2)

        var cursorFrame = 0.0

        if (iteration == 1) {
            console.log("[Benchmark] Warming up...")
            // 2. Warmup (Crucial for JIT) - only on first iteration
            // Render blocks of silence to wake up the CPU governor and JIT. The ramp keeps rendering
            // on the same cursor afterwards: the scheduler judges every voice against the render
            // clock, and that clock must only ever move forward.
            repeat(WARMUP_BLOCKS) {
                renderer.renderBlock(cursorFrame, outBuffer)
                cursorFrame += blockFrames
            }
            console.log("[Benchmark] Warmup complete")
        }

        console.log("[Benchmark] Starting measurement for iteration $iteration...")

        // The scheduler snaps this playback's epoch to the clock the first time it sees one of our
        // voices, and every startTime is measured from there. Stamping from a zero point of our own
        // would put each step's voices a warmup-length into the future, so they would only turn up
        // at the very end of the window that is supposed to measure them.
        val epochFrame = cursorFrame

        // 3. Ramp Up Loop
        var activeVoices = 0
        var targetVoices = INITIAL_VOICES
        // Last measurement that stayed below the target, for the interpolation at the crossing.
        var safeVoices = 0
        var safeRtf = 0.0

        while (true) {
            // Voices already playing keep sounding (their gate runs for a virtual eternity), so a
            // step only has to add the difference up to the count it wants to measure. Aimed at the
            // MIDDLE of the block we are about to render: exactly on the boundary, one ulp of
            // rounding would make the scheduler read the voice as late and drop the whole step.
            val startTimeSec = (cursorFrame - epochFrame + blockFrames * 0.5) / sampleRate

            repeat(targetVoices - activeVoices) {
                addHeavyVoice(scheduler, startTimeSec, activeVoices + it)
            }

            // Trigger voice scheduling by processing current block
            scheduler.process(cursorFrame)

            // Measure Performance - render with all active voices
            val mark = TimeSource.Monotonic.markNow()

            repeat(BLOCKS_TO_MEASURE) {
                scheduler.process(cursorFrame)
                renderer.renderBlock(cursorFrame, outBuffer)
                cursorFrame += blockFrames
            }

            val elapsedUs = mark.elapsedNow().toDouble(DurationUnit.MICROSECONDS)
            val audioDurationUs = (BLOCKS_TO_MEASURE * blockFrames * 1_000_000.0) / sampleRate

            val currentRtf = elapsedUs / audioDurationUs

            activeVoices = scheduler.getActiveVoiceCount()
            console.log(
                "[Benchmark] $activeVoices voices (asked for $targetVoices): RTF = ${
                    currentRtf.asDynamic().toFixed(3)
                }"
            )

            // Emit progress
            val progressUpdate = Progress(
                currentVoices = targetVoices,
                activeVoices = activeVoices,
                currentRtf = currentRtf,
                currentIteration = iteration,
                totalIterations = totalIterations,
                isComplete = false
            )
            _progress(progressUpdate)
            onProgress?.invoke(progressUpdate)

            // Yield to the browser to allow UI updates
            delay(10.milliseconds)

            if (currentRtf >= targetRtf) {
                val maxSafeVoices = crossingVoices(
                    safeVoices = safeVoices,
                    safeRtf = safeRtf,
                    voices = activeVoices,
                    rtf = currentRtf,
                    targetRtf = targetRtf,
                )

                console.log("[Benchmark] Iteration $iteration complete: $maxSafeVoices max safe voices (active: $activeVoices)")

                return Result(
                    maxSafeVoices = maxSafeVoices,
                    rtfAtLimit = currentRtf,
                    details = "Iteration $iteration: Hit limit at $activeVoices active voices (RTF: ${
                        currentRtf.asDynamic().toFixed(3)
                    })",
                    rounds = iteration,
                )
            }

            if (targetVoices >= maxVoicesCap) {
                console.log("[Benchmark] Iteration $iteration complete: $maxVoicesCap max safe voices (maxed out)")

                return Result(
                    maxSafeVoices = maxVoicesCap,
                    rtfAtLimit = currentRtf,
                    details = "Iteration $iteration: Maxed out cap of $maxVoicesCap voices!",
                    rounds = iteration,
                )
            }

            safeVoices = activeVoices
            safeRtf = currentRtf
            targetVoices = nextTargetVoices(
                targetVoices = targetVoices,
                activeVoices = activeVoices,
                currentRtf = currentRtf,
                targetRtf = targetRtf,
                maxVoicesCap = maxVoicesCap,
            )
        }
    }

    /**
     * How many voices the next step runs. The load is close enough to linear in the voice count that
     * the measured RTF predicts where the target sits: `voices * target / measured`. That prediction
     * ignores the engine's fixed per-block cost, so it always lands a little SHORT of the true limit,
     * which is exactly what a ramp wants: it closes in from below and its steps shrink by themselves
     * as the measurement approaches the target. Capped at [MAX_GROWTH] per step so one noisy sample
     * cannot leap from a handful of voices to the cap, and floored at [MIN_STEP] so the ramp always
     * makes progress.
     */
    private fun nextTargetVoices(
        targetVoices: Int,
        activeVoices: Int,
        currentRtf: Double,
        targetRtf: Double,
        maxVoicesCap: Int,
    ): Int {
        val predicted = if (currentRtf > 0.0) {
            (activeVoices * targetRtf / currentRtf).toInt()
        } else {
            maxVoicesCap
        }

        val floor = targetVoices + MIN_STEP
        val ceiling = maxOf((targetVoices * MAX_GROWTH).toInt(), floor)

        return predicted.coerceIn(floor, ceiling).coerceAtMost(maxVoicesCap)
    }

    /**
     * Where the load crosses [targetRtf], read off the line between the last measurement below it and
     * the one that went over. Linear enough to land within a voice or two of a one-by-one crawl,
     * without the crawl.
     */
    private fun crossingVoices(
        safeVoices: Int,
        safeRtf: Double,
        voices: Int,
        rtf: Double,
        targetRtf: Double,
    ): Int {
        val voiceSpan = voices - safeVoices
        val rtfSpan = rtf - safeRtf

        if (voiceSpan <= 0 || rtfSpan <= 0.0) {
            return safeVoices.coerceAtLeast(0)
        }

        return (safeVoices + voiceSpan * ((targetRtf - safeRtf) / rtfSpan)).toInt().coerceAtLeast(0)
    }

    private fun addHeavyVoice(scheduler: VoiceScheduler, startTimeSec: Double, id: Int) {
        // Create a computationally "average-to-heavy" voice
        // (Oscillator + Filter + ADSR + Reverb Send)
        val voice = ScheduledVoice(
            playbackId = "benchmark",
            startTime = startTimeSec, // Start at specified time
            gateEndTime = startTimeSec + 1000.0, // Long sustain to stay active
            playbackStartTime = 0.0,
            data = VoiceData.empty.copy(
                sound = "supersaw",
                oscParams = mapOf("voices" to 8.0),
                freqHz = 220.0 + (id * 2),
                // Add a filter to make it realistic load
                filters = FilterDefs(
                    listOf(
                        FilterDef.LowPass(freq = 1000.0, q = 1.0)
                    )
                ),
                adsr = AdsrDef.defaultSynth,
                // Enable reverb send (expensive mixing)
                room = 0.2,
                roomSize = 0.5
            )
        )
        scheduler.scheduleVoice(voice)
    }
}
