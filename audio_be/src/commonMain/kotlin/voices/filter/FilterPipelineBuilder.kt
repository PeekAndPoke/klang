package io.peekandpoke.klang.audio_be.voices.filter

import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_be.filters.effects.*
import io.peekandpoke.klang.audio_be.voices.BlockRenderer
import io.peekandpoke.klang.audio_be.voices.Voice

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
    val preFilters = buildList<AudioFilter> {
        if (crush.amount > 0.0) add(BitCrushFilter(crush.amount))
        if (coarse.amount > 1.0) add(SampleRateReducerFilter(coarse.amount))
    }
    AudioFilterRenderer.ofNullable(preFilters)?.let { add(it) }

    // Main filter (subtractive)
    add(AudioFilterRenderer.of(mainFilter))

    // Amplitude envelope (ADSR VCA)
    add(EnvelopeRenderer(envelope, startFrame, gateEndFrame))

    // Post-filters (distortion)
    val postFilters = buildList<AudioFilter> {
        if (distort.amount > 0.0) add(DistortionFilter(distort.amount, distort.shape))
    }
    AudioFilterRenderer.ofNullable(postFilters)?.let { add(it) }

    // Tremolo
    if (tremolo.depth > 0.0) {
        add(AudioFilterRenderer.of(TremoloFilter(tremolo.rate, tremolo.depth, sampleRate)))
    }

    // Phaser
    if (phaser.depth > 0.0) {
        add(
            AudioFilterRenderer.of(
                PhaserFilter(
                    rate = phaser.rate,
                    depth = phaser.depth,
                    center = if (phaser.center > 0) phaser.center else 1000.0,
                    sweep = if (phaser.sweep > 0) phaser.sweep else 1000.0,
                    sampleRate = sampleRate,
                )
            )
        )
    }
}
