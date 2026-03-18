package io.peekandpoke.klang.audio_engine

import de.peekandpoke.ultra.streams.Stream
import de.peekandpoke.ultra.streams.StreamSource
import io.peekandpoke.klang.audio_bridge.KlangPattern
import io.peekandpoke.klang.audio_bridge.KlangPatternEvent
import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink

/**
 * One-shot playback that stops automatically after a specified number of cycles.
 * Useful for sample previews, auditioning, and other finite-length playback scenarios.
 *
 * @param cyclesToPlay Number of cycles to play before stopping (default: 1)
 */
internal class OneShotPlayback internal constructor(
    override val playbackId: String,
    pattern: KlangPattern,
    context: KlangPlaybackContext,
    private val cyclesToPlay: Int = 1,
    onStarted: () -> Unit = {},
    onStopped: () -> Unit = {},
) : KlangCyclicPlayback {

    private val _signals = StreamSource<KlangPlaybackSignal>(KlangPlaybackSignal.Idle)

    override val signals: Stream<KlangPlaybackSignal> = _signals.readonly

    // Wrap the pattern to only return events within the target cycle range
    private val limitedPattern = CycleLimitedPattern(pattern, cyclesToPlay)

    private val controller = KlangPlaybackController(
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

/**
 * A pattern wrapper that filters out events beyond a cycle limit.
 * Generic replacement for strudel's `filterWhen` DSL method.
 */
private class CycleLimitedPattern(
    private val inner: KlangPattern,
    private val maxCycles: Int,
) : KlangPattern {
    override fun queryEvents(fromCycles: Double, toCycles: Double, cps: Double): List<KlangPatternEvent> {
        if (fromCycles >= maxCycles) {
            return emptyList()
        }
        val clampedTo = toCycles.coerceAtMost(maxCycles.toDouble())
        return inner.queryEvents(fromCycles, clampedTo, cps)
    }
}
