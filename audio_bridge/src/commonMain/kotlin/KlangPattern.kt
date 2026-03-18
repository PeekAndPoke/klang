package io.peekandpoke.klang.audio_bridge

/**
 * Universal pattern interface for composable music patterns.
 *
 * Every pattern source (Strudel, sequencer, MIDI, generative, etc.) implements this interface.
 * Patterns can be composed across implementations — a Strudel pattern stacked with a sequencer
 * pattern goes through the same scheduling and playback infrastructure.
 *
 * queries patterns for events in cycle ranges and converts them to [ScheduledVoice] instances.
 */
interface KlangPattern {
    /**
     * Query events in the given cycle range.
     *
     * Contract:
     * - Returns only events that should be played (onset filtering is the implementor's responsibility)
     * - Events outside [fromCycles, toCycles) may be excluded
     * - [cps] (cycles per second) is provided for implementations that need tempo context
     * - Events should be returned in start-time order
     */
    fun queryEvents(fromCycles: Double, toCycles: Double, cps: Double): List<KlangPatternEvent>
}
