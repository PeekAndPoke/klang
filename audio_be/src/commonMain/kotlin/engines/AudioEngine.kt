package io.peekandpoke.klang.audio_be.engines

/**
 * Named preset for the voice filter pipeline topology.
 *
 * Each engine defines a different ordering (and potentially a different set) of
 * `BlockRenderer` stages in the voice's Filter stage. The song DSL selects an
 * engine per voice via `.engine("name")` — unknown or missing names fall back
 * to [Modern].
 *
 * String-keyed lookup keeps the door open for a future `EngineDsl` similar to
 * `IgnitorDsl` — a declarative, user-extensible way to build custom engines
 * without code changes.
 */
enum class AudioEngine(val engineName: String) {

    /**
     * Default engine. Classic subtractive ordering: **osc → waveshaper → VCF → VCA**.
     *
     * ```
     * FilterMod → Crush → Coarse → Distort → AudioFilter → Tremolo → Phaser → Envelope
     * ```
     *
     * ADSR runs last, so the filter and phaser see a steady-amplitude signal
     * and don't smear the attack. Waveshapers still precede the filter so
     * their harmonics get cleaned up.
     */
    Modern("modern"),

    /**
     * Guitar-pedal feel: envelope drives the waveshapers, so distortion
     * responds to dynamics.
     *
     * ```
     * FilterMod → Envelope → Crush → Coarse → Distort → AudioFilter → Tremolo → Phaser
     * ```
     *
     * Quiet attack stays clean, hot sustain saturates, release tail fades
     * through the drive. Filter is after the waveshapers but before the
     * modulation FX. Trades "no attack smearing" for "dynamics-responsive
     * distortion" — use when you want the pedal-chain character.
     */
    Pedal("pedal");

    companion object {
        /**
         * Resolves a user-facing engine name (case-insensitive) to an [AudioEngine].
         * Unknown or null names return [Modern] — never throws.
         */
        fun fromName(name: String?): AudioEngine {
            val key = name?.lowercase() ?: return Modern
            return entries.firstOrNull { it.engineName == key } ?: Modern
        }
    }
}
