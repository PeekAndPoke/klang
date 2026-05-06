package io.peekandpoke.klang.audio_bridge

/**
 * PCM decoded sample ready for mixing.
 *
 * MVP:
 * - mono PCM at the engine's canonical sample type (Double on JVM and JS)
 * - sampleRate matches renderer sampleRate (resampling can be added later)
 *
 * The engine canonical sample type is Double to keep the audio hot path free
 * of Float↔Double conversions (which are real `d2f`/`f2d` ops on JVM, no-ops on JS).
 * Decoders produce Float (matching WAV/MP3 source format) and convert exactly once
 * when constructing the [MonoSamplePcm].
 */
class MonoSamplePcm(
    val sampleRate: Int,
    val pcm: DoubleArray,
    val meta: SampleMetadata = SampleMetadata.default,
) {
    fun withMetadata(value: SampleMetadata): MonoSamplePcm {
        return MonoSamplePcm(
            sampleRate = sampleRate,
            pcm = pcm,
            meta = value,
        )
    }
}
