package io.peekandpoke.klang.audio_fe

import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_fe.samples.SampleRequest
import io.peekandpoke.klang.audio_fe.samples.Samples
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class KlangEventFetcher<T>(
    private val config: Config<T>,
) {
    data class Config<T>(
        val source: KlangEventSource<T>,
        val samples: Samples,
        val commLink: KlangCommLink.FrontendEndpoint,
        val transform: (T) -> ScheduledVoice,
        val sampleRate: Int,
        val cps: Double,
        val lookaheadSec: Double,
        val prefetchCycles: Double,
    )

    suspend fun run(scope: CoroutineScope) {
        val secPerCycle = 1.0 / config.cps
        var queryCursorCycles = 0.0
        val fetchChunk = 1.0
        var currentFrame = 0L

        // samples
        val samples = config.samples
        // Control channel
        val control = config.commLink.control
        // Feedback channel
        val feedback = config.commLink.feedback

        while (scope.isActive) {
            val nowFrame = currentFrame
            val nowSec = nowFrame.toDouble() / config.sampleRate.toDouble()
            val nowCycles = nowSec / secPerCycle

            val targetCycles = nowCycles + (config.lookaheadSec / secPerCycle)

            // Fetch as many new cycles as needed
            while (queryCursorCycles < targetCycles) {
                val from = queryCursorCycles
                val to = from + fetchChunk

                try {
                    println("Querying from $from to $to")
                    val events = config.source.query(from, to)

                    for (e in events) {
                        // 1. Transform source event T to scheduled event S
                        val voice = config.transform(e)
                        // 2. Schedule the voice
                        control.send(KlangCommLink.Cmd.ScheduleVoice(voice))
                    }

                    queryCursorCycles = to
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    t.printStackTrace()
                    break
                }
            }

            // Query feedback-events from backend
            while (true) {
                val evt = feedback.receive() ?: break

                when (evt) {
                    is KlangCommLink.Feedback.UpdateCursorFrame -> {
                        currentFrame = evt.frame
                    }

                    is KlangCommLink.Feedback.RequestSample -> {
                        samples.getWithCallback(evt.toSampleRequest()) { result ->
                            val sample = result?.first
                            val pcm = result?.second

                            control.send(
                                KlangCommLink.Cmd.Sample(
                                    request = evt,
                                    data = sample?.let { sample ->
                                        pcm?.let { pcm ->
                                            KlangCommLink.Cmd.Sample.Data(
                                                note = sample.note,
                                                pitchHz = sample.pitchHz,
                                                sampleRate = pcm.sampleRate,
                                                pcm = pcm.pcm,
                                            )
                                        }
                                    }
                                ),
                            )
                        }
                    }
                }
            }

            // 60 FPS
            delay(16)
        }

        println("KlangPlayerBackend stopped")
    }

    private fun KlangCommLink.Feedback.RequestSample.toSampleRequest() =
        SampleRequest(bank = bank, sound = sound, index = index, note = note)
}
