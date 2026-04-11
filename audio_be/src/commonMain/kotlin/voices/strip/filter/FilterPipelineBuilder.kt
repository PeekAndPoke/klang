package io.peekandpoke.klang.audio_be.voices.strip.filter

import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer

/**
 * Builds the filter pipeline (BlockRenderer chain) from voice parameters.
 *
 * Pipeline order (classic subtractive: osc → VCA → waveshaper → VCF):
 * 1. Filter modulation (control rate — updates cutoffs before filter runs)
 * 2. Amplitude envelope (ADSR VCA — shapes amplitude before waveshaping)
 * 3. Waveshaping (destructive: crush, coarse, distortion)
 * 4. Main filter (subtractive: LP/HP/BP/Notch)
 * 5. Tremolo
 * 6. Phaser
 *
 * Envelope before distortion: distortion responds to dynamics — quiet attack
 * stays clean, full sustain drives hard, release tail fades out cleanly.
 * Filters after distortion: LP/HP/BP have the final say over the frequency
 * spectrum — distortion's intermodulation products are cleaned up.
 *
 * Only active stages are included (e.g. tremolo is skipped if depth == 0).
 */
fun buildFilterPipeline(
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
    // Filter modulation (control rate — updates cutoffs before filter runs)
    if (modulators.isNotEmpty()) {
        add(FilterModRenderer(modulators, startFrame, gateEndFrame))
    }

    // Amplitude envelope (ADSR VCA — before waveshaping so distortion responds to dynamics)
    add(EnvelopeRenderer(envelope, startFrame, gateEndFrame))

    // Waveshaping (destructive: crush, coarse, distortion — before filters so filters have final say)
    if (crush.amount > 0.0) {
        add(CrushRenderer(crush.amount, crush.oversample))
    }
    if (coarse.amount > 1.0) {
        add(CoarseRenderer(coarse.amount, coarse.oversample))
    }
    if (distort.amount > 0.0) {
        add(DistortionRenderer(distort.amount, distort.shape, distort.oversample))
    }

    // Main filter (subtractive: LP/HP/BP/Notch — final say on frequency spectrum)
    add(AudioFilterRenderer.of(mainFilter))

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
