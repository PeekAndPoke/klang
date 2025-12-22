package io.peekandpoke

import io.peekandpoke.GraalJsBridge.safeNumber
import io.peekandpoke.GraalJsBridge.safeNumberOrNull
import io.peekandpoke.GraalJsBridge.safeString
import io.peekandpoke.GraalJsBridge.safeStringOrNull
import org.graalvm.polyglot.Value

/**
 * Strudel sound event.
 *
 * Trying to stay as close as possible to the class DoughVoice:
 * https://codeberg.org/uzu/strudel/src/branch/main/packages/supradough/dough.mjs
 */
data class StrudelEvent(
    /** The begin of the note */
    val begin: Double,
    /** The end of the note */
    val end: Double,
    /** The duration of the note */
    val dur: Double,
    // Frequency and note
    val note: String,
    val gain: Double,
    // Oscilator
    /** Oscillator name, see [Oscillators.get] */
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
    val filters: List<Filter>,
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
    companion object {
        fun of(event: Value, sampleRate: Int): StrudelEvent {
            val filters = mutableListOf<Filter>()

            val part = event.getMember("part")

            // Begin
            val begin = part?.getMember("begin")?.safeNumber(0.0) ?: 0.0
            // End
            val end = part?.getMember("end")?.safeNumber(0.0) ?: 0.0
            // Get duration
            val dur = end - begin

            // Get details from "value" field
            val value = event.getMember("value")
            // Get note
            val note = value.getMember("note").safeString("")
            // Get gain
            val gain = value.getMember("gain").safeNumberOrNull()
                ?: value.getMember("amp").safeNumberOrNull()
                ?: 1.0
            // Get Oscillator parameters
            val osc = value.getMember("s").safeStringOrNull()
                ?: value.getMember("wave").safeStringOrNull()
                ?: value.getMember("sound").safeStringOrNull()
            val density = value.getMember("density").safeNumberOrNull()
            val spread = value.getMember("spread").safeNumberOrNull()
            val detune = value.getMember("detune").safeNumberOrNull()
            val unison = value.getMember("unison").safeNumberOrNull()
            // get LPF/HPF resonance
            val resonance = value.getMember("resonance").safeNumberOrNull()
            // Apply low pass filter?
            val cutoff = value.getMember("cutoff").safeNumberOrNull()
            cutoff?.let {
                filters.add(SimpleFilters.createLPF(cutoffHz = it, q = resonance, sampleRate.toDouble()))
            }
            // Apply high pass filter?
            val hcutoff = value.getMember("hcutoff").safeNumberOrNull()
            hcutoff?.let {
                filters.add(SimpleFilters.createHPF(cutoffHz = it, q = resonance, sampleRate.toDouble()))
            }

            // add event
            return StrudelEvent(
                begin = begin,
                end = end,
                dur = dur,
                // Frequency and note
                note = note,
                gain = gain,
                // Oscilator
                osc = osc,
                density = density,
                unison = unison,
                detune = detune,
                spread = spread,
                // Filters
                filters = filters,
                // ADSR envelope
                attack = null, // TODO ...
                decay = null, // TODO ...
                sustain = null, // TODO ...
                release = null, // TODO ...
                // Vibrato
                vibrato = null, // TODO ...
                vibratoMod = null,  // TODO ...
                // HPF / LPF
                cutoff = cutoff,
                hcutoff = hcutoff,
                resonance = resonance,
                // ???
                bandf = null, // TODO ...
                coarse = null, // TODO ...
                crush = null, // TODO ...
                distort = null, // TODO ...
            )
        }
    }
}
