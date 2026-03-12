package io.peekandpoke.klang.strudel

import de.peekandpoke.ultra.streams.Stream
import de.peekandpoke.ultra.streams.StreamSource
import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_engine.KlangPlaybackContext
import io.peekandpoke.klang.strudel.lang.filterWhen

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
    private val cyclesToPlay: Int = 1,
    onStarted: () -> Unit = {},
    onStopped: () -> Unit = {},
) : StrudelPlayback {

    private val _signals = StreamSource<KlangPlaybackSignal>(KlangPlaybackSignal.Idle)

    override val signals: Stream<KlangPlaybackSignal> = _signals.readonly

    // Wrap the pattern to only return events within the target cycle range
    private val limitedPattern = pattern.filterWhen { it < cyclesToPlay }

    private val controller = StrudelPlaybackController(
        playbackId = playbackId,
        pattern = limitedPattern,
        context = context,
        signals = _signals,
        onStarted = onStarted,
        onStopped = onStopped,
    )

    init {
        // Auto-stop after N cycles
        _signals.subscribeToStream { signal ->
            if (signal is KlangPlaybackSignal.CycleCompleted && signal.cycleIndex >= cyclesToPlay - 1) {
                stop()
            }
        }
    }

    override fun updatePattern(pattern: StrudelPattern) {
        controller.updatePattern(pattern)
    }

    override fun updateCyclesPerSecond(cps: Double) {
        controller.updateCyclesPerSecond(cps)
    }

    override fun reemitVoiceSignals() {
        controller.reemitVoiceSignals()
    }

    override fun start() {
        start(StrudelPlayback.Options())
    }

    override fun start(options: StrudelPlayback.Options) {
        controller.start(options.copy(prefetchCycles = cyclesToPlay))
    }

    override fun stop() {
        controller.stop()
        _signals.removeAllSubscriptions()
    }

    override fun handleFeedback(feedback: KlangCommLink.Feedback) {
        controller.handleFeedback(feedback)
    }
}
