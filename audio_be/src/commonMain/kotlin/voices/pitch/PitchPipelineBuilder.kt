package io.peekandpoke.klang.audio_be.voices.pitch

import io.peekandpoke.klang.audio_be.voices.BlockRenderer
import io.peekandpoke.klang.audio_be.voices.Voice

/**
 * Builds the pitch pipeline (BlockRenderer chain) from voice parameters.
 *
 * Pipeline order:
 * 1. Vibrato (LFO pitch modulation)
 * 2. Accelerate (pitch glide over voice lifetime)
 * 3. Pitch Envelope (attack/decay pitch transient)
 * 4. FM Synthesis (frequency modulation)
 *
 * Only active stages are included (e.g. vibrato is skipped if depth == 0).
 * Returns empty list if no pitch modulation is active.
 */
fun buildPitchPipeline(
    vibrato: Voice.Vibrato,
    accelerate: Voice.Accelerate,
    pitchEnvelope: Voice.PitchEnvelope?,
    fm: Voice.Fm?,
    freqHz: Double,
    sampleRate: Int,
    startFrame: Long,
    endFrame: Long,
    gateEndFrame: Long,
): List<BlockRenderer> = buildList {
    if (vibrato.depth > 0.0) {
        add(VibratoRenderer(vibrato, sampleRate))
    }

    if (accelerate.amount != 0.0 && endFrame > startFrame) {
        add(AccelerateRenderer(accelerate, startFrame, endFrame))
    }

    if (pitchEnvelope != null) {
        add(PitchEnvelopeRenderer(pitchEnvelope, startFrame))
    }

    if (fm != null && fm.depth != 0.0) {
        add(FmRenderer(fm, freqHz, sampleRate, startFrame, gateEndFrame))
    }
}
