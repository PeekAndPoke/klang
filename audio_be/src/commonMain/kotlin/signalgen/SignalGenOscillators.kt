package io.peekandpoke.klang.audio_be.signalgen

import io.peekandpoke.klang.audio_be.osci.OscFn

/**
 * Hard-coded SignalGen-based oscillator compositions, registered by name.
 *
 * These are playable from strudel patterns via `.s("sgpad")`, `.s("sgbell")`, etc.
 *
 * TEMPORARY: SignalGen POC bridge — replace with proper registry / user-definable compositions
 * when SignalGen becomes first-class.
 */
object SignalGenOscillators {

    /** Set of all SignalGen oscillator names (lowercase). */
    private val names = setOf("sgpad", "sgbell", "sgbuzz")

    /** Returns true if the given sound name is a SignalGen oscillator. */
    fun isSignalGenOsc(name: String?): Boolean {
        if (name == null) return false
        return names.contains(name.lowercase())
    }

    /**
     * Creates a SignalGen-based OscFn for the given name, or null if not a SignalGen oscillator.
     */
    fun create(
        name: String,
        sampleRate: Int,
        voiceDurationFrames: Int,
        gateEndFrame: Int,
        releaseFrames: Int,
        voiceEndFrame: Int,
        scratchBuffers: ScratchBuffers,
    ): OscFn? {
        val signalGen = when (name.lowercase()) {
            "sgpad" -> {
                // Rich detuned pad: two saws slightly detuned, mixed and filtered
                val osc1 = SignalGens.sawtooth()
                val osc2 = SignalGens.sawtooth().detune(0.1)
                (osc1 + osc2).div(2.0).onePoleLowpass(3000.0)
            }

            "sgbell" -> {
                // FM bell: sine carrier with sine modulator
                SignalGens.sine().fm(
                    modulator = SignalGens.sine(),
                    ratio = 1.4,
                    depth = 300.0,
                    envAttackSec = 0.001,
                    envDecaySec = 0.5,
                    envSustainLevel = 0.0,
                )
            }

            "sgbuzz" -> {
                // Buzzy filtered square
                SignalGens.square().lowpass(2000.0)
            }

            else -> return null
        }

        return signalGen.toOscFn(
            sampleRate = sampleRate,
            voiceDurationFrames = voiceDurationFrames,
            gateEndFrame = gateEndFrame,
            releaseFrames = releaseFrames,
            voiceEndFrame = voiceEndFrame,
            scratchBuffers = scratchBuffers,
        )
    }
}
