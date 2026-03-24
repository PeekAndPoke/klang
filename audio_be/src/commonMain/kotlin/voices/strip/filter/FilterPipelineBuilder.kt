package io.peekandpoke.klang.audio_be.voices.strip.filter

import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer

/**
 * Builds the filter pipeline (BlockRenderer chain) from voice parameters.
 *
 * Pipeline order:
 * 1. Filter modulation (control rate — updates cutoffs before filter runs)
 * 2. Pre-filters (destructive: crush, coarse)
 * 3. Main filter (subtractive: LP/HP/BP/Notch)
 * 4. Amplitude envelope (ADSR VCA)
 * 5. Post-filters (distortion)
 * 6. Tremolo
 * 7. Phaser
 *
 * Only active stages are included (e.g. tremolo is skipped if depth == 0).
 */
fun buildFilterPipeline(
    modulators: List<Voice.FilterModulator>,
    startFrame: Long,
    gateEndFrame: Long,
    crush: Voice.Crush,
    coarse: Voice.Coarse,
    mainFilter: AudioFilter,
    envelope: Voice.Envelope,
    distort: Voice.Distort,
    tremolo: Voice.Tremolo,
    phaser: Voice.Phaser,
    sampleRate: Int,
): List<BlockRenderer> = buildList {
    // Filter modulation (control rate — updates cutoffs before filter runs)
    if (modulators.isNotEmpty()) {
        add(FilterModRenderer(modulators, startFrame, gateEndFrame))
    }

    // Pre-filters (destructive: crush, coarse)
    if (crush.amount > 0.0) {
        add(CrushRenderer(crush.amount))
    }
    if (coarse.amount > 1.0) {
        add(CoarseRenderer(coarse.amount))
    }

    // Main filter (subtractive: LP/HP/BP/Notch — uses AudioFilter.Tunable for modulation)
    add(AudioFilterRenderer.of(mainFilter))

    // Amplitude envelope (ADSR VCA)
    add(EnvelopeRenderer(envelope, startFrame, gateEndFrame))

    // Post-filters (distortion)
    if (distort.amount > 0.0) {
        add(DistortionRenderer(distort.amount, distort.shape))
    }

    // Tremolo
    if (tremolo.depth > 0.0) {
        add(TremoloRenderer(tremolo.rate, tremolo.depth, sampleRate))
    }

    // Phaser
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
}
