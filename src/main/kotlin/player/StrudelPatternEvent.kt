package io.peekandpoke.player

import io.peekandpoke.dsp.AudioFilter
import io.peekandpoke.dsp.Oscillators
import io.peekandpoke.samples.SampleRequest

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

    // HPF / LPF
    val cutoff: Double?,
    val hcutoff: Double?,
    val resonance: Double?,

    // ???
    val bandf: Double?,
    val coarse: Double?,
    val crush: Double?,
    val distort: Double?,
) {
    val isOscillator = Oscillators.Companion.isOsc(sound)

    val isSampleSound = !isOscillator

    val sampleRequest: SampleRequest = SampleRequest(bank, sound, soundIndex)
}
