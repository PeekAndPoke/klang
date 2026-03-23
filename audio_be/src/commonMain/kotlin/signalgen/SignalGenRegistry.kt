package io.peekandpoke.klang.audio_be.signalgen

import io.peekandpoke.klang.audio_bridge.SignalGenDsl
import io.peekandpoke.klang.audio_bridge.VoiceData

/**
 * Single source of truth for all oscillator lookups.
 *
 * Each [createSignalGen] call produces a fresh [SignalGen] with independent mutable state
 * (phase accumulators, filter memory, etc.) — two voices never share a SignalGen instance.
 */
class SignalGenRegistry {
    companion object {
        /** Default sound when none is specified */
        const val DEFAULT_SOUND = "triangle"
    }

    private val defs = mutableMapOf<String, SignalGenDsl>()

    fun register(name: String, dsl: SignalGenDsl) {
        defs[name.lowercase()] = dsl
    }

    fun get(name: String): SignalGenDsl? = defs[name.lowercase()]

    fun contains(name: String?): Boolean {
        val key = (name ?: DEFAULT_SOUND).lowercase()
        return defs.containsKey(key)
    }

    fun names(): Set<String> = defs.keys.toSet()

    /**
     * Creates a fresh [SignalGen] for the given oscillator name.
     *
     * Returns null if the name is unknown.
     */
    fun createSignalGen(name: String?, data: VoiceData, freqHz: Double): SignalGen? {
        val key = (name ?: DEFAULT_SOUND).lowercase()
        val oscParams = data.oscParams

        val dsl = defs[key] ?: return null

        val raw = dsl.toSignalGen(oscParams)
        val warmth = oscParams?.get("warmth") ?: 0.0
        return if (warmth > 0.0) raw.withWarmth(warmth) else raw
    }

    /** Create a child that inherits all defs. Child can add/override without affecting parent. */
    fun fork(): SignalGenRegistry {
        val child = SignalGenRegistry()
        child.defs.putAll(this.defs)
        return child
    }
}
