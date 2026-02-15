package io.peekandpoke.klang.strudel

import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_engine.KlangPlayback
import io.peekandpoke.klang.audio_engine.KlangPlaybackContext
import io.peekandpoke.klang.audio_engine.KlangPlaybackSignal
import io.peekandpoke.klang.audio_engine.KlangPlaybackSignals

/**
 * One-shot Strudel playback that stops automatically after a specified number of cycles.
 * Useful for sample previews, auditioning, and other finite-length playback scenarios.
 *
 * @param cyclesToPlay Number of cycles to play before stopping (default: 1)
 */
internal class OneShotStrudelPlayback internal constructor(
    override val playbackId: String,
    pattern: StrudelPattern,
    context: KlangPlaybackContext,
    private val onStopped: (KlangPlayback) -> Unit = {},
    private val cyclesToPlay: Int = 1,
) : StrudelPlayback {

    override val signals = KlangPlaybackSignals()

    private val controller = StrudelPlaybackController(
        playbackId = playbackId,
        pattern = pattern,
        context = context,
        onStopped = { handleControllerStopped() },
        signals = signals,
    )

    private var unsubscribe: (() -> Unit)? = null

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
        // Dispose old subscription before creating new one (if start called while running)
        unsubscribe?.invoke()

        // Subscribe to CycleCompleted signal to stop after N cycles
        unsubscribe = signals.subscribe { signal ->
            if (signal is KlangPlaybackSignal.CycleCompleted) {
                // Stop when we've completed the target number of cycles (0-based index)
                if (signal.cycleIndex >= cyclesToPlay - 1) {
                    stop()
                }
            }
        }

        // Override lookahead and prefetch for one-shot playback
        // This prevents scheduling events beyond the target cycles
        val oneShotOptions = options.copy(
            // Reduce lookahead to 90% of target cycles to prevent scheduling beyond target
            lookaheadCycles = minOf(options.lookaheadCycles, cyclesToPlay * 0.9),
            // Set prefetch to match cyclesToPlay
            prefetchCycles = cyclesToPlay,
        )

        // Start the controller with adjusted options
        controller.start(oneShotOptions)
    }

    override fun stop() {
        // Unsubscribe from cycle completed signal
        unsubscribe?.invoke()
        unsubscribe = null

        // Stop the controller
        controller.stop()
    }

    override fun handleFeedback(feedback: KlangCommLink.Feedback) {
        controller.handleFeedback(feedback)
    }

    private fun handleControllerStopped() {
        // Ensure unsubscribe is called before clearing all signals
        unsubscribe?.invoke()
        unsubscribe = null

        // Clear signal listeners
        signals.clear()

        // Notify owner
        onStopped(this)
    }
}