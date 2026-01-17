package io.peekandpoke.klang.audio_fe

/**
 * Event source that supports updating cycles per second
 */
interface CpsAwareEventSource : KlangEventSource {
    var cyclesPerSecond: Double
}
