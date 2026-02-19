package io.peekandpoke.klang.audio_engine

import de.peekandpoke.ultra.streams.Stream
import de.peekandpoke.ultra.streams.StreamSource
import io.peekandpoke.klang.audio_be.KlangAudioRenderer
import io.peekandpoke.klang.audio_be.orbits.Orbits
import io.peekandpoke.klang.audio_be.osci.oscillators
import io.peekandpoke.klang.audio_be.voices.VoiceScheduler
import io.peekandpoke.klang.audio_bridge.*
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlinx.coroutines.delay
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

class KlangBenchmark(
    private val sampleRate: Int = 44100,
    private val blockFrames: Int = 128,
) {
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
    )

    private val _progress = StreamSource(
        Progress(
            currentVoices = 0,
            activeVoices = 0,
            currentRtf = 0.0,
            currentIteration = 0,
            totalIterations = 5,
            isComplete = false
        )
    )
    val progress: Stream<Progress> = _progress.readonly

    suspend fun run(
        targetRtf: Double = 0.9, // Stop when we use x % of CPU time
        maxVoicesCap: Int = 1000, // Don't go forever
        iterations: Int = 5, // Number of iterations to average
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
                delay(500)
            }
        }

        // Small delay before final result to ensure UI updates
        delay(100)

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
            details = detailsText
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
        val commLink = KlangCommLink()
        val orbits = Orbits(blockFrames = blockFrames, sampleRate = sampleRate)
        val oscillators = oscillators(sampleRate = sampleRate)

        val scheduler = VoiceScheduler(
            VoiceScheduler.Options(
                commLink = commLink.backend,
                sampleRate = sampleRate,
                blockFrames = blockFrames,
                oscillators = oscillators,
                orbits = orbits
            )
        )
        scheduler.setBackendStartTime(0.0)

        val renderer = KlangAudioRenderer(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            voices = scheduler,
            orbits = orbits
        )

        val outBuffer = ShortArray(blockFrames * 2)

        if (iteration == 1) {
            console.log("[Benchmark] Warming up...")
            // 2. Warmup (Crucial for JIT) - only on first iteration
            // Render 50 blocks of silence to wake up the CPU governor and JIT
            repeat(50) { frame ->
                renderer.renderBlock(frame * blockFrames.toLong(), outBuffer)
            }
            console.log("[Benchmark] Warmup complete")
        }

        console.log("[Benchmark] Starting measurement for iteration $iteration...")

        // 3. Ramp Up Loop
        var currentVoices = 0
        var cursorFrame = 0L
        val batchSize = 1 // Add 4 voices at a time
        val blocksToMeasure = 50 // Measure over ~150ms of audio

        while (currentVoices < maxVoicesCap) {
            // Calculate current time in seconds for scheduling
            val currentTimeSec = cursorFrame.toDouble() / sampleRate

            // Clear scheduled voices and add ALL voices starting at current time
            scheduler.clearScheduled("benchmark")
            repeat(currentVoices + batchSize) {
                addHeavyVoice(scheduler, currentTimeSec, it)
            }
            currentVoices += batchSize

            // Trigger voice scheduling by processing current block
            scheduler.process(cursorFrame)

            // Get actual active voice count
            val activeCount = scheduler.getActiveVoiceCount()
            console.log("[Benchmark] Scheduled: $currentVoices, Active: $activeCount")

            // Measure Performance - render with all active voices
            val mark = TimeSource.Monotonic.markNow()

            repeat(blocksToMeasure) {
                scheduler.process(cursorFrame)
                renderer.renderBlock(cursorFrame, outBuffer)
                cursorFrame += blockFrames
            }

            val elapsedUs = mark.elapsedNow().toDouble(DurationUnit.MICROSECONDS)
            val audioDurationUs = (blocksToMeasure * blockFrames * 1_000_000.0) / sampleRate

            val currentRtf = elapsedUs / audioDurationUs

            val finalActiveCount = scheduler.getActiveVoiceCount()
            console.log(
                "[Benchmark] $currentVoices voices: RTF = ${
                    currentRtf.asDynamic().toFixed(3)
                }, Active: $finalActiveCount"
            )

            // Emit progress
            val progressUpdate = Progress(
                currentVoices = currentVoices,
                activeVoices = finalActiveCount,
                currentRtf = currentRtf,
                currentIteration = iteration,
                totalIterations = totalIterations,
                isComplete = false
            )
            _progress(progressUpdate)
            onProgress?.invoke(progressUpdate)

            // Yield to the browser to allow UI updates
            delay(10)

            if (currentRtf >= targetRtf) {
                // Use the actual active voice count, backing off by batchSize to the last safe measurement
                val safeVoiceCount = (finalActiveCount - batchSize).coerceAtLeast(0)

                console.log("[Benchmark] Iteration $iteration complete: $safeVoiceCount max safe voices (active: $finalActiveCount)")

                return Result(
                    maxSafeVoices = safeVoiceCount,
                    rtfAtLimit = currentRtf,
                    details = "Iteration $iteration: Hit limit at $finalActiveCount active voices (RTF: ${
                        currentRtf.asDynamic().toFixed(3)
                    })"
                )
            }
        }

        console.log("[Benchmark] Iteration $iteration complete: $maxVoicesCap max safe voices (maxed out)")

        return Result(
            maxSafeVoices = maxVoicesCap,
            rtfAtLimit = 0.0,
            details = "Iteration $iteration: Maxed out cap of $maxVoicesCap voices!"
        )
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
                voices = 8.0,
                freqHz = 220.0 + (id * 2),
                // Add a filter to make it realistic load
                filters = FilterDefs(
                    listOf(
                        FilterDef.LowPass(cutoffHz = 1000.0, q = 1.0)
                    )
                ),
                adsr = AdsrEnvelope.defaultSynth,
                // Enable reverb send (expensive mixing)
                room = 0.2,
                roomSize = 0.5
            )
        )
        scheduler.scheduleVoice(voice, clearScheduled = false)
    }
}
