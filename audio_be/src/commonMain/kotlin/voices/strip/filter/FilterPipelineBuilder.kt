/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices.strip.filter

import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer
import io.peekandpoke.klang.audio_bridge.EngineDsl
import io.peekandpoke.klang.audio_bridge.StageDsl

/**
 * Builds the filter pipeline (BlockRenderer chain) from voice parameters.
 *
 * The stage ORDER and PRESENCE come from the resolved [EngineDsl]: the pipeline iterates
 * its [StageDsl] slots and maps each to its `BlockRenderer`. The built-in engines:
 *
 * - `modern` (default): classic subtractive `osc → waveshaper → VCF → VCA`.
 *   ADSR (VCA) runs last so the filter/phaser see steady-state amplitude.
 * - `pedal`: VCA runs first so the waveshapers respond to dynamics.
 *
 * Only ACTIVE stages are included — a waveshaper/tremolo/phaser slot is skipped when its
 * per-voice amount is off. The engine sets order/presence/feel; the note sets amounts.
 */
fun buildFilterPipeline(
    engine: EngineDsl,
    modulators: List<Voice.FilterModulator>,
    startFrame: Int,
    gateEndFrame: Int,
    crush: Voice.Crush,
    coarse: Voice.Coarse,
    mainFilter: AudioFilter,
    envelope: Voice.Envelope,
    distort: Voice.Distort,
    tremolo: Voice.Tremolo,
    phaser: Voice.Phaser,
    sampleRate: Int,
): List<BlockRenderer> = buildList {
    for (stage in engine.stages) {
        when (stage) {
            StageDsl.FilterMod ->
                if (modulators.isNotEmpty()) {
                    add(FilterModRenderer(modulators, startFrame, gateEndFrame))
                }

            StageDsl.Crush ->
                if (crush.amount > 0.0) {
                    add(CrushRenderer(crush.amount, crush.oversample))
                }

            StageDsl.Coarse ->
                if (coarse.amount > 1.0) {
                    add(CoarseRenderer(coarse.amount, coarse.oversample))
                }

            StageDsl.Distort ->
                if (distort.amount > 0.0) {
                    add(DistortionRenderer(distort.amount, distort.shape, distort.oversample))
                }

            is StageDsl.Filter ->
                add(AudioFilterRenderer.of(mainFilter))

            StageDsl.Tremolo ->
                if (tremolo.depth > 0.0) {
                    add(TremoloRenderer(tremolo.rate, tremolo.depth, sampleRate))
                }

            StageDsl.Phaser ->
                if (phaser.depth > 0.0) {
                    add(
                        StripPhaserRenderer(
                            rate = phaser.rate,
                            depth = phaser.depth,
                            center = if (phaser.center > 0) phaser.center else 1000.0,
                            sweep = if (phaser.sweep > 0) phaser.sweep else 1000.0,
                            sampleRate = sampleRate,
                        )
                    )
                }

            is StageDsl.Vca ->
                add(
                    EnvelopeRenderer(
                        envelope, startFrame, gateEndFrame,
                        expK = stage.expK,
                        declickSeconds = stage.declickSeconds,
                    )
                )
        }
    }
}
