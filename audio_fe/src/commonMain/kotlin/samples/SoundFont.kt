package io.peekandpoke.klang.audio_fe.samples

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
            val originalPitch: Int, // 6000,
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
        )
    }
}
