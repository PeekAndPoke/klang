package io.peekandpoke.klang.audio_be

/**
 * Canonical scalar sample type for the DSP hot path.
 *
 * Aliased to [Double] to eliminate per-sample Float↔Double conversions on the JVM
 * (no-ops on JS, real `d2f`/`f2d` ops on JVM). Conversions to/from [Float] only happen
 * at platform output boundaries (interleave to `ShortArray` for `SourceDataLine` /
 * `Float32Array` for the Web Audio worklet) and at the sample-asset playback boundary
 * (`MonoSamplePcm.pcm: FloatArray` is widened to [AudioBuffer] when read in
 * [io.peekandpoke.klang.audio_be.ignitor.SampleIgnitor]).
 */
typealias AudioSample = Double

/**
 * Canonical buffer type for the DSP hot path. See [AudioSample].
 */
typealias AudioBuffer = DoubleArray
