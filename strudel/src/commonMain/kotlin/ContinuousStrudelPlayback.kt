package io.peekandpoke.klang.strudel

import de.peekandpoke.ultra.streams.StreamSource
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_engine.KlangPlaybackContext
import io.peekandpoke.klang.audio_engine.KlangPlaybackSignal

/**
 * Continuous Strudel playback that runs indefinitely until explicitly stopped.
 * This is the default playback mode for live coding.
 */
internal class ContinuousStrudelPlayback internal constructor(
    override val playbackId: String,
    pattern: StrudelPattern,
    context: KlangPlaybackContext,
) : StrudelPlayback {

    private val _signals = StreamSource<KlangPlaybackSignal>(KlangPlaybackSignal.Idle)

    private val controller = StrudelPlaybackController(
        playbackId = playbackId,
        pattern = pattern,
        context = context,
        signals = _signals,
    )

    override fun onSignal(listener: (KlangPlaybackSignal) -> Unit): () -> Unit {
        return _signals.subscribeToStream(listener)
    }

    override fun updatePattern(pattern: StrudelPattern) {
        controller.updatePattern(pattern)
    }

    override fun updateCyclesPerSecond(cps: Double) {
        controller.updateCyclesPerSecond(cps)
    }

    override fun start() {
        start(StrudelPlayback.Options())
    }

    override fun start(options: StrudelPlayback.Options) {
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
