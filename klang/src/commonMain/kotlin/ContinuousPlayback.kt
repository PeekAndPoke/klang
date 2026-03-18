package io.peekandpoke.klang.audio_engine

import de.peekandpoke.ultra.streams.Stream
import de.peekandpoke.ultra.streams.StreamSource
import io.peekandpoke.klang.audio_bridge.KlangPattern
import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink

/**
 * Continuous playback that runs indefinitely until explicitly stopped.
 * This is the default playback mode for live coding.
 */
internal class ContinuousPlayback internal constructor(
    override val playbackId: String,
    pattern: KlangPattern,
    context: KlangPlaybackContext,
    onStarted: () -> Unit = {},
    onStopped: () -> Unit = {},
) : KlangCyclicPlayback {

    private val _signals = StreamSource<KlangPlaybackSignal>(KlangPlaybackSignal.Idle)

    override val signals: Stream<KlangPlaybackSignal> = _signals.readonly

    private val controller = KlangPlaybackController(
        playbackId = playbackId,
        pattern = pattern,
        context = context,
        signals = _signals,
        onStarted = onStarted,
        onStopped = onStopped,
    )

    override fun updatePattern(pattern: KlangPattern) {
        controller.updatePattern(pattern)
    }

    override fun updateCyclesPerSecond(cps: Double) {
        controller.updateCyclesPerSecond(cps)
    }

    override fun reemitVoiceSignals() {
        controller.reemitVoiceSignals()
    }

    override fun start() {
        start(KlangCyclicPlayback.Options())
    }

    override fun start(options: KlangCyclicPlayback.Options) {
        controller.start(options)
    }

    override fun stop() {
        controller.stop()
        _signals.removeAllSubscriptions()
    }

    override fun handleFeedback(feedback: KlangCommLink.Feedback) {
        controller.handleFeedback(feedback)
    }
}
