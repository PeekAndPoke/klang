package io.peekandpoke.klang.audio_be.signalgen

import io.peekandpoke.klang.audio_be.osci.Oscillators
import io.peekandpoke.klang.audio_be.osci.withWarmth
import io.peekandpoke.klang.audio_bridge.SignalGenDsl
import io.peekandpoke.klang.audio_bridge.VoiceData

/**
 * Single source of truth for all oscillator lookups.
 *
 * Wraps both the [SignalGenDsl] registry and the legacy [Oscillators].
 * DSL entries are checked first; legacy oscillators serve as fallback.
 *
 * Each [createSignalGen] call produces a fresh [SignalGen] with independent mutable state
 * (phase accumulators, filter memory, etc.) — two voices never share a SignalGen instance.
 */
class SignalGenRegistry(
    private var legacyOscillators: Oscillators? = null,
) {
    private val defs = mutableMapOf<String, SignalGenDsl>()

    fun register(name: String, dsl: SignalGenDsl) {
        defs[name.lowercase()] = dsl
    }

    fun get(name: String): SignalGenDsl? = defs[name.lowercase()]

    fun contains(name: String?): Boolean {
        if (name == null) return true // null → default oscillator (triangle)
        val key = name.lowercase()
        return defs.containsKey(key) || legacyOscillators?.isOsc(name) == true
    }

    fun names(): Set<String> = defs.keys.toSet()

    /**
     * Creates a fresh [SignalGen] for the given oscillator name.
     *
     * Checks the DSL registry first, then falls back to legacy [Oscillators].
     * Returns null if the name is unknown in both.
     */
    fun createSignalGen(name: String?, data: VoiceData, freqHz: Double): SignalGen? {
        // DSL path — toSignalGen() creates fresh instances with independent mutable state
        if (name != null) {
            val dsl = defs[name.lowercase()]
            if (dsl != null) return dsl.toSignalGen()
        }

        // Legacy OscFn path (handles null → triangle fallback)
        val oscillators = legacyOscillators ?: return null
        if (!oscillators.isOsc(name)) return null

        val rawOsc = oscillators.get(
            name = data.sound,
            freqHz = freqHz,
            density = data.density,
            voices = data.voices,
            freqSpread = data.freqSpread,
            panSpread = data.panSpread,
        )

        val warmth = data.warmth ?: 0.0
        val osc = if (warmth > 0.0) rawOsc.withWarmth(warmth) else rawOsc

        return osc.toSignalGen()
    }

    /** Create a child that inherits all defs and the legacy fallback. Child can add/override without affecting parent. */
    fun fork(): SignalGenRegistry {
        val child = SignalGenRegistry(legacyOscillators)
        child.defs.putAll(this.defs)
        return child
    }
}
