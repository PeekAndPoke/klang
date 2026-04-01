package io.peekandpoke.klang.audio_bridge

import kotlinx.serialization.Serializable

@Serializable
data class SampleMetadata(
    val anchor: Double,
    val loop: LoopRange?,
    val adsr: AdsrEnvelope?,
) {
    companion object {
        val default = SampleMetadata(anchor = 0.0, loop = null, adsr = null)
    }

    @Serializable
    data class LoopRange(
        /** Loop start in seconds (sample-rate independent) */
        val startSec: Double,
        /** Loop end in seconds (sample-rate independent) */
        val endSec: Double,
    )
}
