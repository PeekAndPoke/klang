package io.peekandpoke.klang.strudel

import io.peekandpoke.klang.audio_be.OscFn
import io.peekandpoke.klang.audio_be.Oscillators
import io.peekandpoke.klang.audio_fe.samples.SampleRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Strudel sound event.
 *
 * For the moment: ... trying to stay close to the class DoughVoice:
 * https://codeberg.org/uzu/strudel/src/branch/main/packages/supradough/dough.mjs
 */
@Serializable
data class StrudelPatternEvent(
    /** The begin of the note */
    val begin: Double,
    /** The end of the note */
    val end: Double,
    /** The duration of the note */
    val dur: Double,

    // note, scale, gain
    val note: String?,
    val scale: String?,
    val gain: Double,

    // Sound, bank, sound index
    /** Parsed from osc if it looks like "bd:2". sound="bd", soundIndex=2 */
    val sound: String?,
    /** Sample bank (e.g. "MPC60" or "AkaiMPC60"), optional.*/
    val bank: String?,
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
    val filters: List<StrudelFilterDef>,

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
    @Transient
    val isOscillator = Oscillators.isOsc(sound)

    @Transient
    val isSampleSound = !isOscillator

    @Transient
    val sampleRequest: SampleRequest =
        SampleRequest(bank = bank, sound = sound, index = soundIndex, note = note)

    // TODO: do we really need to pass the [freqHz] in here?
    //  Or cam we calculate this earlier?
    fun createOscillator(oscillators: Oscillators, freqHz: Double): OscFn {
        val e = this

        return oscillators.get(
            name = e.sound,
            freqHz = freqHz,
            density = e.density,
            unison = e.unison,
            detune = e.detune,
            spread = e.spread,
        )
    }
}
