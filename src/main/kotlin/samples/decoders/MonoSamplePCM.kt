package io.peekandpoke.samples.decoders

/**
 * PCM decoded sample ready for mixing.
 *
 * MVP:
 * - mono float PCM
 * - sampleRate matches renderer sampleRate (resampling can be added later)
 */
class MonoSamplePCM(
    val sampleRate: Int,
    val pcm: FloatArray,
)
