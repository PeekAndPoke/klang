/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.peekandpoke.klang.audio_bridge.constants.ADSR_EXP_K
import io.peekandpoke.klang.audio_bridge.constants.ENV_DECLICK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.FILTER_CUTOFF_OFFSET_PER_ANALOG
import io.peekandpoke.klang.audio_bridge.constants.FILTER_DRIFT_RELATIVE_TO_OSC
import io.peekandpoke.klang.audio_bridge.constants.FILTER_DRIVE_PER_ANALOG

/**
 * Declarative, data-driven voice engine (the "Motör" filter/VCA pipeline).
 *
 * An engine is an ordered list of [StageDsl] slots — the topology — where each
 * stage also carries its own character constants (envelope curve, declick,
 * filter humanization). Built-ins [modern] / [pedal] reproduce the historical
 * hardcoded pipelines' TOPOLOGY; their character constants have since been retuned
 * (see [StageDsl]). Users author arbitrary pipelines and may omit stages.
 *
 * Mirrors [IgnitorDsl]: a `@WireFormat` root, registered by name, referenced from `VoiceData.pipeline`. The
 * backend maps each [StageDsl] to a `BlockRenderer`. Marked `@WireFormat` so the codec is generated now (the
 * `@WireName` tags below are live); the `Cmd.RegisterPipeline` plumbing that actually *sends* it is still TODO.
 *
 * Per-note amounts (distort/crush/cutoff/adsr times) stay on `VoiceData` — a
 * stage slot only renders when its amount is active. The engine sets order,
 * presence and feel; the note sets amounts.
 */
@WireFormat
data class PipelineDsl(val stages: List<StageDsl>) {
    companion object {
        /** Classic subtractive: osc → waveshaper → VCF → VCA. ADSR (VCA) last. */
        val modern: PipelineDsl = PipelineDsl(
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
        val pedal: PipelineDsl = PipelineDsl(
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
 * One stage slot in an [PipelineDsl] pipeline.
 *
 * Marker stages carry no config; [Filter] and [Vca] carry their tune-by-ear
 * character constants, whose defaults live in `constants/` — one declaration that
 * both this DSL and the engine read.
 *
 * These defaults began as the historical compile-time values, but no longer track them
 * blindly: [Filter]'s three were retuned by ear on 2026-08-11, and `drivePerAnalog` in
 * particular is half the value the engine used before that. So the built-in engines are
 * NOT byte-for-byte identical to the old hardcoded pipelines — see
 * `docs/tasks/audio-bridge-constants.md` §2.
 */
@WireFormat
sealed interface StageDsl {

    /** Control-rate filter-cutoff modulation. Belongs first (reads prev block). */
    @WireName("filterMod")
    data object FilterMod : StageDsl

    /** Bit-crusher waveshaper. */
    @WireName("crush")
    data object Crush : StageDsl

    /** Sample-rate reducer ("coarse") waveshaper. */
    @WireName("coarse")
    data object Coarse : StageDsl

    /** Distortion waveshaper. */
    @WireName("distort")
    data object Distort : StageDsl

    /** Tremolo (post-filter amplitude LFO). */
    @WireName("tremolo")
    data object Tremolo : StageDsl

    /** Phaser (post-filter all-pass sweep). */
    @WireName("phaser")
    data object Phaser : StageDsl

    /** Main filter (LP/HP/BP/Notch chain) + its per-voice humanization feel. */
    @WireName("filter")
    data class Filter(
        val cutoffOffsetPerAnalog: Double = FILTER_CUTOFF_OFFSET_PER_ANALOG,
        val drivePerAnalog: Double = FILTER_DRIVE_PER_ANALOG,
        val driftRelToOsc: Double = FILTER_DRIFT_RELATIVE_TO_OSC,
    ) : StageDsl

    /** Amplitude VCA (ADSR) + its envelope character. */
    @WireName("vca")
    data class Vca(
        val expK: Double = ADSR_EXP_K,
        val declickSeconds: Double = ENV_DECLICK_SECONDS,
    ) : StageDsl
}
