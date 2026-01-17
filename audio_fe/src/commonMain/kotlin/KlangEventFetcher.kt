package io.peekandpoke.klang.audio_fe

import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_fe.samples.Samples
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class KlangEventFetcher(
    private val config: Config,
) {
    data class Config(
        val playbackId: String,
        val source: KlangEventSource,
        val samples: Samples,
        val commLink: KlangCommLink.FrontendEndpoint,
        val sampleRate: Int,
        val cps: Double,
        val lookaheadSec: Double,
        val prefetchCycles: Double,
    )

    private val sampleSoundLookAheadCycles = 8.0
    private var sampleSoundLookAheadPointer = 0.0

    private val secPerCycle = 1.0 / config.cps
    private var queryCursorCycles = 0.0
    private val fetchChunk = 1.0

    // Autonomous progression - track elapsed time since start
    private var startTimeMs = 0.0
    private var localFrameCounter = 0L

    // samples
    private val samples = config.samples

    // Control channel
    private val control = config.commLink.control

    // Feedback channel
    private val feedback = config.commLink.feedback

    // Keep track of the samples that we have already sent to the backend
    private val samplesAlreadySent = mutableSetOf<SampleRequest>()

    suspend fun run(scope: CoroutineScope) {
        // Record start time for autonomous progression
        startTimeMs = KlangTime.nowMs()

        lookAheadForSampleSounds(0.0, sampleSoundLookAheadCycles)

        while (scope.isActive) {
            // Update local frame counter based on elapsed time
            updateLocalFrameCounter()

            // Look ahead for sample sound
            lookAheadForSampleSounds(queryCursorCycles + sampleSoundLookAheadCycles, 1.0)

            // Request the next cycles from the source
            requestNextCyclesAndAdvanceCursor()

            // Query feedback-events from backend (only for sample requests now)
            processFeedbackEvents()

            // roughly 60 FPS
            delay(16)
        }

        println("KlangEventFetcher stopped")
    }

    private fun updateLocalFrameCounter() {
        val elapsedMs = KlangTime.nowMs() - startTimeMs
        val elapsedSec = elapsedMs / 1000.0
        localFrameCounter = (elapsedSec * config.sampleRate).toLong()
    }

    private fun lookAheadForSampleSounds(from: Double, dur: Double = from + 1.0) {
        val to = from + dur

        if (to <= sampleSoundLookAheadPointer) return

        sampleSoundLookAheadPointer = to

        println("[Song: ${config.playbackId}] Sample Look ahead $from - $to")

        // Lookup events
        val events = config.source.query(from, to)

        // Figure out which samples we need to send to the backend
        val newSamples = events
            .map { it.data.asSampleRequest() }
            .toSet().minus(samplesAlreadySent)

        for (sample in newSamples) {
            // 1. Remember this one
            samplesAlreadySent.add(sample)
            // 2. Request the sample and send it to the backend
            requestSendSampleAndSendCmd(sample)
        }
    }

    private fun requestNextCyclesAndAdvanceCursor() {
        // Use local frame counter for autonomous progression
        val nowFrame = localFrameCounter
        val nowSec = nowFrame.toDouble() / config.sampleRate.toDouble()
        val nowCycles = nowSec / secPerCycle

        val targetCycles = nowCycles + (config.lookaheadSec / secPerCycle)

        // Fetch as many new cycles as needed
        while (queryCursorCycles < targetCycles) {
            val from = queryCursorCycles
            val to = from + fetchChunk

            try {
                val events = try {
                    config.source.query(from, to)
                } catch (e: Exception) {
                    e.printStackTrace()
                    emptyList()
                }

                for (voice in events) {
                    // Schedule the voice
                    control.send(
                        KlangCommLink.Cmd.ScheduleVoice(
                            playbackId = config.playbackId,
                            voice = voice,
                        )
                    )
                }

                queryCursorCycles = to
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                t.printStackTrace()
                break
            }
        }
    }

    private fun processFeedbackEvents() {
        while (true) {
            val evt = feedback.receive() ?: break

            // Only process feedback for this playback
            if (evt.playbackId != config.playbackId) {
                continue
            }

            when (evt) {
                is KlangCommLink.Feedback.RequestSample -> {
                    requestSendSampleAndSendCmd(evt.req)
                }

                is KlangCommLink.Feedback.UpdateCursorFrame -> {
                    // Ignore - event-fetcher is now autonomous
                }
            }
        }
    }

    private fun requestSendSampleAndSendCmd(req: SampleRequest) {
        samples.getWithCallback(req) { result ->
            val sample = result?.sample
            val pcm = result?.pcm

            val cmd = if (sample == null || pcm == null) {
                KlangCommLink.Cmd.Sample.NotFound(
                    playbackId = config.playbackId,
                    req = req,
                )
            } else {
                KlangCommLink.Cmd.Sample.Complete(
                    playbackId = config.playbackId,
                    req = req,
                    note = sample.note,
                    pitchHz = sample.pitchHz,
                    sample = pcm,
                )
            }

            control.send(cmd)
        }
    }
}
