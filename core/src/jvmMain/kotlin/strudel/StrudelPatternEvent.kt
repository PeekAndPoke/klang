package io.peekandpoke.klang.strudel

import io.peekandpoke.klang.dsp.AudioFilter
import io.peekandpoke.klang.dsp.Oscillators
import io.peekandpoke.klang.samples.SampleRequest

/**
 * Strudel sound event.
 *
 * For the moment: ... trying to stay close to the class DoughVoice:
 * https://codeberg.org/uzu/strudel/src/branch/main/packages/supradough/dough.mjs
 */
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
    val filters: List<AudioFilter>,

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
    val isOscillator = Oscillators.isOsc(sound)

    val isSampleSound = !isOscillator

    val sampleRequest: SampleRequest =
        SampleRequest(bank = bank, sound = sound, index = soundIndex, note = note)
}
