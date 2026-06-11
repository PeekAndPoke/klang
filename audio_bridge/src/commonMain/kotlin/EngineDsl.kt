package io.peekandpoke.klang.audio_bridge

import io.peekandpoke.klang.audio_bridge.EngineDsl.Companion.modern
import io.peekandpoke.klang.audio_bridge.EngineDsl.Companion.pedal
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Declarative, data-driven voice engine (the "Motör" filter/VCA pipeline).
 *
 * An engine is an ordered list of [StageDsl] slots — the topology — where each
 * stage also carries its own character constants (envelope curve, declick,
 * filter humanization). Built-ins [modern] / [pedal] reproduce the historical
 * hardcoded pipelines; users author arbitrary pipelines and may omit stages.
 *
 * Mirrors [IgnitorDsl]: serialisable, registered by name, referenced from
 * `VoiceData.engine`. The backend maps each [StageDsl] to a `BlockRenderer`.
 *
 * Per-note amounts (distort/crush/cutoff/adsr times) stay on `VoiceData` — a
 * stage slot only renders when its amount is active. The engine sets order,
 * presence and feel; the note sets amounts.
 */
@Serializable
data class EngineDsl(val stages: List<StageDsl>) {
    companion object {
        /** Classic subtractive: osc → waveshaper → VCF → VCA. ADSR (VCA) last. */
        val modern: EngineDsl = EngineDsl(
            listOf(
                StageDsl.FilterMod,
                StageDsl.Crush,
                StageDsl.Coarse,
                StageDsl.Distort,
                StageDsl.Filter(),
                StageDsl.Tremolo,
                StageDsl.Phaser,
                StageDsl.Vca(),
            )
        )

        /** Guitar-pedal feel: VCA drives the waveshapers. ADSR (VCA) early. */
        val pedal: EngineDsl = EngineDsl(
            listOf(
                StageDsl.FilterMod,
                StageDsl.Vca(),
                StageDsl.Crush,
                StageDsl.Coarse,
                StageDsl.Distort,
                StageDsl.Filter(),
                StageDsl.Tremolo,
                StageDsl.Phaser,
            )
        )
    }
}

/**
 * One stage slot in an [EngineDsl] pipeline.
 *
 * Marker stages carry no config; [Filter] and [Vca] carry their tune-by-ear
 * character constants. All defaults equal the historical compile-time values,
 * so the built-in engines are byte-for-byte identical to the old hardcoded
 * pipelines.
 */
@Serializable
sealed interface StageDsl {

    /** Control-rate filter-cutoff modulation. Belongs first (reads prev block). */
    @Serializable
    @SerialName("filterMod")
    data object FilterMod : StageDsl

    /** Bit-crusher waveshaper. */
    @Serializable
    @SerialName("crush")
    data object Crush : StageDsl

    /** Sample-rate reducer ("coarse") waveshaper. */
    @Serializable
    @SerialName("coarse")
    data object Coarse : StageDsl

    /** Distortion waveshaper. */
    @Serializable
    @SerialName("distort")
    data object Distort : StageDsl

    /** Tremolo (post-filter amplitude LFO). */
    @Serializable
    @SerialName("tremolo")
    data object Tremolo : StageDsl

    /** Phaser (post-filter all-pass sweep). */
    @Serializable
    @SerialName("phaser")
    data object Phaser : StageDsl

    /** Main filter (LP/HP/BP/Notch chain) + its per-voice humanization feel. */
    @Serializable
    @SerialName("filter")
    data class Filter(
        val cutoffOffsetPerAnalog: Double = 0.003, // FILTER_CUTOFF_OFFSET_PER_ANALOG
        val drivePerAnalog: Double = 0.5,          // FILTER_DRIVE_PER_ANALOG
        val driftRelToOsc: Double = 5.0,           // FILTER_DRIFT_RELATIVE_TO_OSC
    ) : StageDsl

    /** Amplitude VCA (ADSR) + its envelope character. */
    @Serializable
    @SerialName("vca")
    data class Vca(
        val expK: Double = 3.0,             // ADSR_EXP_K
        val declickSeconds: Double = 0.001, // ENV_DECLICK_SECONDS
    ) : StageDsl
}
