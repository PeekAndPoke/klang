package io.peekandpoke.klang.audio_fe.samples

import io.peekandpoke.klang.audio_bridge.AdsrEnvelope
import io.peekandpoke.klang.audio_bridge.SampleMetadata
import kotlinx.serialization.Serializable

/** Sound font index */
data class SoundfontIndex(
    val name: String,
    val baseUrl: String,
    val entries: Map<String, List<Variant>>,
) {

    @Serializable
    data class Variant(
        /** The name of the variant */
        val name: String,
        /** The file with the sound font content for the variant relative to [SoundfontIndex.baseUrl] */
        val file: String,
        /** Optional information about the original source of the soundfont */
        val source: Source?,
    ) {
        @Serializable
        data class Source(
            val baseUrl: String,
            val file: String,
        )
    }

    @Serializable
    data class SoundData(
        val zones: List<Zone>,
    ) {
        @Serializable
        data class Zone(
            val midi: Int, // 21,
            val originalPitch: Double, // 6000,
            val keyRangeLow: Int, // 0,
            val keyRangeHigh: Int, // 60,
            val loopStart: Int, // 36629,
            val loopEnd: Int, // 182237,
            val coarseTune: Int, // 0,
            val fineTune: Int, // 0,
            val sampleRate: Int, // 22050,
            val ahdsr: Boolean, // false,
            val file: String, // "SUQzBAAAAAAAI1RTU...",
            val anchor: Double, // 6.46648502
        ) {
            /**
             * Get loop info.
             *
             * Treat this sample as looped when the defined [loopEnd] - [loopStart] is at least [minLen] seconds
             */
            fun getSampleMetadata(minLen: Double = 0.1): SampleMetadata {
                val loopLen = loopEnd - loopStart
                val loopDuration = loopLen.toDouble() / sampleRate

                // Heuristic: If the loop is tiny, it's likely a "fake" loop for a percussive sound.
                // 50ms (0.05s) is a safe threshold.
                val isSustainLoop = loopDuration > minLen

                val loop = if (isSustainLoop) {
                    SampleMetadata.LoopRange(start = loopStart, end = loopEnd)
                } else {
                    null
                }

                val envelope = if (ahdsr || isSustainLoop) {
                    // Explicit ADSR requested/supported by font
                    AdsrEnvelope(
                        attack = 0.01,
                        decay = 0.1,
                        sustain = 1.0,
                        release = 0.2,
                    )
                } else {
                    // Percussive (Drum, Xylophone): Instant On, Fade Out
                    AdsrEnvelope(
                        attack = 0.01,
                        decay = 0.5, // Longer decay to let the sample ring out a bit
                        sustain = 0.0,
                        release = 0.1 // If note is cut short
                    )
                }

                return SampleMetadata(
                    anchor = anchor,
                    loop = loop,
                    adsr = envelope,
                )
            }
        }
    }
}
