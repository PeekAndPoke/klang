package io.peekandpoke.klang.audio_be.exciter

import io.peekandpoke.klang.audio_bridge.ExciterDsl
import io.peekandpoke.klang.audio_bridge.VoiceData

/**
 * Single source of truth for all oscillator lookups.
 *
 * Each [createExciter] call produces a fresh [Exciter] with independent mutable state
 * (phase accumulators, filter memory, etc.) — two voices never share a Exciter instance.
 */
class ExciterRegistry {
    companion object {
        /** Default sound when none is specified */
        const val DEFAULT_SOUND = "triangle"
    }

    private val defs = mutableMapOf<String, ExciterDsl>()

    fun register(name: String, dsl: ExciterDsl) {
        defs[name.lowercase()] = dsl
    }

    fun get(name: String): ExciterDsl? = defs[name.lowercase()]

    fun contains(name: String?): Boolean {
        val key = (name ?: DEFAULT_SOUND).lowercase()
        return defs.containsKey(key)
    }

    fun needsFreq(name: String?): Boolean {
        val key = (name ?: DEFAULT_SOUND).lowercase()
        return defs[key]?.needsFreq ?: true
    }

    fun names(): Set<String> = defs.keys.toSet()

    /**
     * Creates a fresh [Exciter] for the given oscillator name.
     *
     * Returns null if the name is unknown.
     */
    fun createExciter(name: String?, data: VoiceData, freqHz: Double): Exciter? {
        val key = (name ?: DEFAULT_SOUND).lowercase()
        val oscParams = data.oscParams

        val dsl = defs[key] ?: return null

        val raw = dsl.toExciter(oscParams)
        val warmth = oscParams?.get("warmth") ?: 0.0
        return if (warmth > 0.0) raw.withWarmth(warmth) else raw
    }

    /** Create a child that inherits all defs. Child can add/override without affecting parent. */
    fun fork(): ExciterRegistry {
        val child = ExciterRegistry()
        child.defs.putAll(this.defs)
        return child
    }
}
