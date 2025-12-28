package io.peekandpoke.klang.audio_bridge

import kotlinx.serialization.Serializable

/**
 * Defines a voice
 */
@Serializable
data class VoiceData(
    // note, scale, freq
    val note: String?,
    val freqHz: Double?,
    val scale: String?,

    // Gain
    val gain: Double,

    // Sound, bank, sound index
    /** Sample bank (e.g. "MPC60" or "AkaiMPC60"), optional.*/
    val bank: String?,
    /** Parsed from osc if it looks like "bd:2". sound="bd", soundIndex=2 */
    val sound: String?,
    /** Sound index */
    val soundIndex: Int?,

    // Oscillator parameters
    /** density for dust, crackle */
    val density: Double?,
    /** Used for: supersaw */
    val spread: Double?,
    /** Used for: supersaw */
    val detune: Double?,
    /** Used for: supersaw */
    val unison: Double?,

    // Filters
    val filters: List<FilterDef>,

    // ADSR envelope
    val attack: Double?,
    val decay: Double?,
    val sustain: Double?,
    val release: Double?,

    // Vibrato
    val vibrato: Double?,
    val vibratoMod: Double?,

    // Distort / Shape
    val distort: Double?,

    // HPF / LPF
    val cutoff: Double?,
    val hcutoff: Double?,
    val resonance: Double?,

    // Routing
    val orbit: Int?,

    // Panning (-1.0 = Left, 0.0 = Center, 1.0 = Right)
    val pan: Double?,

    // Delay
    val delay: Double?, // Mix amount (0.0 to 1.0)
    val delayTime: Double?, // Time in seconds
    val delayFeedback: Double?, // Feedback amount (0.0 to <1.0)

    // Reverb
    val room: Double?,
    val roomsize: Double?,

    // ???
    val bandf: Double?,
    val coarse: Double?,
    val crush: Double?,
) {
    fun asSampleRequest(): SampleRequest {
        return SampleRequest(bank = bank, sound = sound, index = soundIndex, note = note)
    }
}
