package io.peekandpoke.klang.audio_be.voices.strip.filter

import io.peekandpoke.klang.audio_be.engines.AudioEngine
import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer

/**
 * Builds the filter pipeline (BlockRenderer chain) from voice parameters.
 *
 * The concrete stage ordering depends on the selected [AudioEngine]:
 *
 * - [AudioEngine.Modern] (default): classic subtractive `osc → waveshaper → VCF → VCA`.
 *   ADSR runs last so the filter/phaser see steady-state amplitude.
 * - [AudioEngine.Pedal]: envelope drives the waveshapers (guitar-pedal feel).
 *   ADSR runs first so distortion responds to dynamics.
 *
 * Waveshapers always precede the main filter (so LP/HP can clean up their harmonics).
 * Only active stages are included (e.g. tremolo is skipped if depth == 0).
 */
fun buildFilterPipeline(
    engine: AudioEngine,
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
): List<BlockRenderer> = when (engine) {
    AudioEngine.Modern -> buildModernFilterPipeline(
        modulators, startFrame, gateEndFrame,
        crush, coarse, mainFilter, envelope, distort, tremolo, phaser, sampleRate,
    )

    AudioEngine.Pedal -> buildPedalFilterPipeline(
        modulators, startFrame, gateEndFrame,
        crush, coarse, mainFilter, envelope, distort, tremolo, phaser, sampleRate,
    )
}

/**
 * Modern engine — ADSR last.
 *
 * ```
 * 1. FilterMod       (control rate)
 * 2. Crush
 * 3. Coarse
 * 4. Distort
 * 5. AudioFilter     (LP/HP/BP/Notch)
 * 6. Tremolo
 * 7. Phaser
 * 8. Envelope (ADSR VCA — end of tonal stage)
 * ```
 *
 * Classic subtractive `osc → VCF → VCA`. The filter and phaser see steady
 * amplitude, so the envelope attack is not smeared by their impulse response.
 * Waveshapers still precede the filter so LP/HP can clean up their harmonics.
 */
private fun buildModernFilterPipeline(
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
    if (modulators.isNotEmpty()) {
        add(FilterModRenderer(modulators, startFrame, gateEndFrame))
    }

    // Waveshaping
    if (crush.amount > 0.0) {
        add(CrushRenderer(crush.amount, crush.oversample))
    }
    if (coarse.amount > 1.0) {
        add(CoarseRenderer(coarse.amount, coarse.oversample))
    }
    if (distort.amount > 0.0) {
        add(DistortionRenderer(distort.amount, distort.shape, distort.oversample))
    }

    // Main filter
    add(AudioFilterRenderer.of(mainFilter))

    // Modulation FX
    if (tremolo.depth > 0.0) {
        add(TremoloRenderer(tremolo.rate, tremolo.depth, sampleRate))
    }
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

    // VCA — last in the tonal stage
    add(EnvelopeRenderer(envelope, startFrame, gateEndFrame))
}

/**
 * Pedal engine — ADSR first, guitar-pedal feel.
 *
 * ```
 * 1. FilterMod       (control rate)
 * 2. Envelope (ADSR VCA — before waveshaping so drive responds to dynamics)
 * 3. Crush
 * 4. Coarse
 * 5. Distort
 * 6. AudioFilter     (LP/HP/BP/Notch — cleans up waveshaper harmonics)
 * 7. Tremolo
 * 8. Phaser
 * ```
 *
 * Envelope runs upstream of the waveshapers so quiet attacks stay clean,
 * hot sustain saturates, and release tails fade through the drive. Trades
 * the modern engine's "no attack smearing" for "dynamics-responsive
 * distortion" — use when you want the pedal-chain character.
 */
private fun buildPedalFilterPipeline(
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
    if (modulators.isNotEmpty()) {
        add(FilterModRenderer(modulators, startFrame, gateEndFrame))
    }

    // VCA first — dynamics feed the waveshapers
    add(EnvelopeRenderer(envelope, startFrame, gateEndFrame))

    // Waveshaping
    if (crush.amount > 0.0) {
        add(CrushRenderer(crush.amount, crush.oversample))
    }
    if (coarse.amount > 1.0) {
        add(CoarseRenderer(coarse.amount, coarse.oversample))
    }
    if (distort.amount > 0.0) {
        add(DistortionRenderer(distort.amount, distort.shape, distort.oversample))
    }

    // Main filter
    add(AudioFilterRenderer.of(mainFilter))

    // Modulation FX
    if (tremolo.depth > 0.0) {
        add(TremoloRenderer(tremolo.rate, tremolo.depth, sampleRate))
    }
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
