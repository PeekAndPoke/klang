package io.peekandpoke.klang.audio_bridge

/**
 * PCM decoded sample ready for mixing.
 *
 * MVP:
 * - mono float PCM
 * - sampleRate matches renderer sampleRate (resampling can be added later)
 */
class MonoSamplePcm(
    val sampleRate: Int,
    val pcm: FloatArray,
    val loop: SampleLoopInfo? = null,
) {
    fun withLoop(value: SampleLoopInfo?): MonoSamplePcm {
        return MonoSamplePcm(sampleRate = sampleRate, pcm = pcm, loop = value)
    }
}
