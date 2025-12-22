package io.peekandpoke

import io.peekandpoke.dsp.AudioFilter

/**
 * Strudel sound event.
 *
 * For the moment: ... trying to stay close to the class DoughVoice:
 * https://codeberg.org/uzu/strudel/src/branch/main/packages/supradough/dough.mjs
 */
data class StrudelEvent(
    /** The begin of the note */
    val begin: Double,
    /** The end of the note */
    val end: Double,
    /** The duration of the note */
    val dur: Double,
    // note, scale, gain
    val note: String,
    val scale: String?,
    val gain: Double,
    // Oscilator
    /** Oscillator name, see [io.peekandpoke.dsp.Oscillators.get] */
    val osc: String?,
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
)
