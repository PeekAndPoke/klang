package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_be.KlangAudioRenderer
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_be.voices.VoiceScheduler
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KlangPattern
import io.peekandpoke.klang.audio_bridge.KlangTime
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_fe.samples.Samples

/**
 * Offline renderer that drives the DSP graph directly at full CPU speed.
 *
 * Platform-independent: produces audio blocks via a callback.
 * Bypasses the real-time playback infrastructure entirely.
 */
class KlangOfflineRenderer(
    private val sampleRate: Int = 48_000,
    private val blockFrames: Int = 512,
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

        // 1. Create DSP graph
        val commLink = KlangCommLink()
        val cylinders = Cylinders(maxCylinders = 16, blockFrames = blockFrames, sampleRate = sampleRate)
        val ignitorRegistry = IgnitorRegistry().apply {
            registerDefaults()
            for ((name, dsl) in customIgnitors) {
                if (contains(name)) {
                    println("[KlangOfflineRenderer] Custom ignitor '$name' overrides built-in sound")
                }
                register(name, dsl)
            }
        }

        val voiceScheduler = VoiceScheduler(
            VoiceScheduler.Options(
                commLink = commLink.backend,
                sampleRate = sampleRate,
                blockFrames = blockFrames,
                ignitorRegistry = ignitorRegistry,
                cylinders = cylinders,
                performanceTimeMs = { klangTime.internalMsNow() },
            )
        )
        voiceScheduler.setBackendStartTime(0.0)

        val renderer = KlangAudioRenderer(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            voices = voiceScheduler,
            cylinders = cylinders,
        )

        // 2. Query all events and cache voice data (toVoiceData() creates new objects)
        data class CachedEvent(
            val startCycles: Double,
            val durationCycles: Double,
            val voiceData: VoiceData,
        )

        val events = pattern.queryEvents(
            fromCycles = 0.0,
            toCycles = cycles.toDouble(),
            cps = cyclesPerSecond,
        ).map { CachedEvent(it.startCycles, it.durationCycles, it.toVoiceData()) }

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
        val totalFrames = (totalDurationSec * sampleRate).toInt()

        // 6. Render loop
        val outShorts = ShortArray(blockFrames * 2)
        var currentFrame = 0

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
