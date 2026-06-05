Yes, that is a much better approach (a "Max Polyphony Stress Test"). It gives you a concrete, understandable number: **"
This device can handle 42 voices safely."**

Here is how we implement this. We define a budget (e.g., 70% of Real-Time) and keep adding voices in batches until the
render time exceeds that budget.

I will create a new file `audio_be/src/commonMain/kotlin/KlangBenchmark.kt` with this logic.

```kotlin
package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.orbits.Orbits
import io.peekandpoke.klang.audio_be.osci.oscillators
import io.peekandpoke.klang.audio_be.voices.VoiceScheduler
import io.peekandpoke.klang.audio_bridge.*
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

class KlangBenchmark(
    private val sampleRate: Int = 44100,
    private val blockFrames: Int = 128
) {
    data class Result(
        val maxSafeVoices: Int,
        val rtfAtLimit: Double,
        val details: String
    )

    fun findVoiceLimit(
        targetRtf: Double = 0.7, // Stop when we use 70% of CPU time
        maxVoicesCap: Int = 200 // Don't go forever
    ): Result {
        println("[Benchmark] Starting Voice Stress Test...")

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

        // 2. Warmup (Crucial for JIT)
        // Render 50 blocks of silence to wake up the CPU governor and JIT
        repeat(50) { frame ->
            renderer.renderBlock(frame * blockFrames.toLong(), outBuffer)
        }

        // 3. Ramp Up Loop
        var currentVoices = 0
        var cursorFrame = 0L
        val batchSize = 4 // Add 4 voices at a time
        val blocksToMeasure = 50 // Measure over ~150ms of audio

        while (currentVoices < maxVoicesCap) {
            // Add a batch of voices
            repeat(batchSize) {
                // We restart voices constantly so they are always in "sustain" or "attack" phase
                // doing actual work (filters, envs, etc)
                addHeavyVoice(scheduler, cursorFrame, currentVoices + it)
            }
            currentVoices += batchSize

            // Measure Performance
            val mark = TimeSource.Monotonic.markNow()

            repeat(blocksToMeasure) {
                // Trick: We keep rewinding time slightly for the scheduler 
                // so voices don't expire/release during measurement. 
                // OR we just set gate time to be huge.
                // Here we just render forward.
                renderer.renderBlock(cursorFrame, outBuffer)
                cursorFrame += blockFrames
            }

            val elapsedUs = mark.elapsedNow().toDouble(DurationUnit.MICROSECONDS)
            val audioDurationUs = (blocksToMeasure * blockFrames * 1_000_000.0) / sampleRate

            val currentRtf = elapsedUs / audioDurationUs

            // println("Voices: $currentVoices | RTF: $currentRtf")

            if (currentRtf >= targetRtf) {
                return Result(
                    maxSafeVoices = currentVoices - batchSize, // Back off to safe zone
                    rtfAtLimit = currentRtf,
                    details = "Hit limit at $currentVoices voices (RTF: $currentRtf)"
                )
            }
        }

        return Result(
            maxSafeVoices = maxVoicesCap,
            rtfAtLimit = 0.0,
            details = "Maxed out cap of $maxVoicesCap voices!"
        )
    }

    private fun addHeavyVoice(scheduler: VoiceScheduler, nowFrame: Long, id: Int) {
        val nowSec = nowFrame.toDouble() / sampleRate

        // Create a computationally "average-to-heavy" voice
        // (Oscillator + Filter + ADSR + Reverb Send)
        val voice = ScheduledVoice(
            startTime = 0.0, // Schedule in past so it starts immediately
            gateEndTime = 1000.0, // Infinite sustain
            data = VoiceData.empty.copy(
                sound = "saw",
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
        scheduler.scheduleVoice("bench-$id", voice)
    }
}
```

### Explanation of the Strategy

1. **Warmup:** We run 50 empty blocks first. This triggers the JVM JIT compiler to optimize the hot paths (loops) and
   wakes up the CPU from sleep states.
2. **Incremental Load:** We add 4 voices at a time.
3. **Measurement Window:** We measure `50 blocks` (about 145ms at 44.1kHz). This is long enough to average out GC pauses
   but short enough to run the whole benchmark quickly.
4. **Infinite Sustain:** We schedule voices with a huge `gateEndTime` (1000 seconds) and `startTime` at 0. This ensures
   that as we advance `cursorFrame`, the voices remain active and computing. We don't want them to enter the "release"
   phase and die off during the test.
5. **Fail Condition:** If `Time Taken / Audio Duration` (RTF) exceeds `0.7` (70%), we consider the CPU saturated. We
   leave 30% headroom because real-world usage has spikes (GC, UI, OS tasks).

### Usage

```kotlin
val bench = KlangBenchmark()
val result = bench.findVoiceLimit()

println("Max Voices: ${result.maxSafeVoices}")
// e.g. "Max Voices: 48"
```
