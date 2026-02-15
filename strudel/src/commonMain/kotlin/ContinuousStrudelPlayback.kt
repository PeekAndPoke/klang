package io.peekandpoke.klang.strudel

import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_engine.KlangPlayback
import io.peekandpoke.klang.audio_engine.KlangPlaybackContext
import io.peekandpoke.klang.audio_engine.KlangPlaybackSignals

/**
 * Continuous Strudel playback that runs indefinitely until explicitly stopped.
 * This is the default playback mode for live coding.
 */
internal class ContinuousStrudelPlayback internal constructor(
    override val playbackId: String,
    pattern: StrudelPattern,
    context: KlangPlaybackContext,
    private val onStopped: (KlangPlayback) -> Unit = {},
) : StrudelPlayback {

    override val signals = KlangPlaybackSignals()

    private val controller = StrudelPlaybackController(
        playbackId = playbackId,
        pattern = pattern,
        context = context,
        onStopped = { handleControllerStopped() },
        signals = signals,
    )

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
    }

    override fun handleFeedback(feedback: KlangCommLink.Feedback) {
        controller.handleFeedback(feedback)
    }

    private fun handleControllerStopped() {
        // Clear signal listeners
        signals.clear()
        // Notify owner
        onStopped(this)
    }
}