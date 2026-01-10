package io.peekandpoke.klang.audio_bridge

import kotlinx.serialization.Serializable

/**
 * Defines a voice
 */
@Serializable
data class VoiceData(
    // note, scale, freq
    // TODO: note can also be numbers -> Midi and detune, f.e. 50.3
    val note: String?,
    val freqHz: Double?,
    val scale: String?,

    // Gain / Dynamics
    val gain: Double?,
    val legato: Double?,

    // Sound, bank, sound index
    /** Sample bank (e.g. "MPC60" or "AkaiMPC60"), optional.*/
    val bank: String?,
    /** Parsed from osc if it looks like "bd:2". sound="bd", soundIndex=2 */
    val sound: String?,
    /** Sound index */
    val soundIndex: Int?,

    // Oscillator parameters
    /** Density */
    val density: Double?,
    /** Panorama spread */
    val panSpread: Double?,
    /** Frequency spread */
    val freqSpread: Double?,
    /** Number of voices */
    val voices: Double?,

    // Filters
    val filters: FilterDefs = FilterDefs.empty,

    // ADSR
    val adsr: AdsrEnvelope,

    // Pitch / Glisando
    val accelerate: Double?,

    // Vibrato
    val vibrato: Double?,
    val vibratoMod: Double?,

    // Effects
    val distort: Double?,
    val coarse: Double?,
    val crush: Double?,

    // HPF / LPF
    /** Low pass filter cutoff frequency */
    val cutoff: Double?,
    /** High pass filter cutoff frequency */
    val hcutoff: Double?,
    /** Band pass filter cutoff frequency */
    val bandf: Double?,
    /** Resonance amount for filters */
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
    val roomSize: Double?,

    // Custom value
    val value: VoiceValue? = null,
) {
    companion object {
        val empty = VoiceData(
            note = null,
            freqHz = null,
            scale = null,
            gain = 1.0,
            legato = null,
            bank = null,
            sound = null,
            soundIndex = null,
            density = null,
            panSpread = null,
            freqSpread = null,
            voices = null,
            filters = FilterDefs.empty,
            adsr = AdsrEnvelope.empty,
            accelerate = null,
            vibrato = null,
            vibratoMod = null,
            distort = null,
            coarse = null,
            crush = null,
            cutoff = null,
            hcutoff = null,
            bandf = null,
            resonance = null,
            orbit = null,
            pan = null,
            delay = null,
            delayTime = null,
            delayFeedback = null,
            room = null,
            roomSize = null,
            value = null,
        )
    }

    fun asSampleRequest(): SampleRequest {
        return SampleRequest(bank = bank, sound = sound, index = soundIndex, note = note)
    }
}
