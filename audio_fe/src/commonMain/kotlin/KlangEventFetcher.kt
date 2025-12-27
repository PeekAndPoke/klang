package io.peekandpoke.klang.audio_fe

import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.infra.KlangPlayerState
import io.peekandpoke.klang.audio_fe.samples.SampleRequest
import io.peekandpoke.klang.audio_fe.samples.Samples
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class KlangEventFetcher<T>(
    private val source: KlangEventSource<T>,
    private val state: KlangPlayerState,
    private val samples: Samples,
    private val commLink: KlangCommLink.FrontendEndpoint,
    private val config: Config,
    private val transform: (T) -> ScheduledVoice,
) {
    data class Config(
        val sampleRate: Int,
        val cps: Double,
        val lookaheadSec: Double,
        val fetchPeriodMs: Long,
        val prefetchCycles: Double,
    )

    private val secPerCycle get() = 1.0 / config.cps

    suspend fun runFetcher(scope: CoroutineScope) {
        var queryCursorCycles = 0.0
        val fetchChunk = 1.0

        while (scope.isActive) {
            val nowFrame = state.cursorFrame()
            val nowSec = nowFrame.toDouble() / config.sampleRate.toDouble()
            val nowCycles = nowSec / secPerCycle

            val targetCycles = nowCycles + (config.lookaheadSec / secPerCycle)

            while (queryCursorCycles < targetCycles) {
                val from = queryCursorCycles
                val to = from + fetchChunk

                try {
                    val events = source.query(from, to)

                    for (e in events) {
                        // 1. Transform source event T to scheduled event S
                        val voice = transform(e)
                        // 2. Schedule the voice
                        commLink.control.dispatch(
                            KlangCommLink.Cmd.ScheduleVoice(voice)
                        )
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
                val evt = commLink.feedback.receive() ?: break

                when (evt) {
                    is KlangCommLink.Feedback.RequestSample -> {
                        println("Backend requested sample: $evt")

                        samples.getWithCallback(evt.toSampleRequest()) { result ->
                            val sample = result?.first
                            val pcm = result?.second

                            commLink.control.dispatch(
                                KlangCommLink.Cmd.Sample(
                                    request = evt,
                                    data = sample?.let {
                                        KlangCommLink.Cmd.Sample.Data(
                                            note = it.note,
                                            pitchHz = it.pitchHz,
                                            pcm = pcm
                                        )
                                    }
                                )
                            )
                        }
                    }
                }
            }

            delay(config.fetchPeriodMs)
        }
    }

    private fun KlangCommLink.Feedback.RequestSample.toSampleRequest() =
        SampleRequest(bank = bank, sound = sound, index = index, note = note)
}
