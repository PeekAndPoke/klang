package io.peekandpoke.klang.audio_bridge

data class SampleMetadata(
    val anchor: Double,
    val loop: LoopRange?,
    val adsr: AdsrEnvelope?,
) {
    companion object {
        val default = SampleMetadata(anchor = 0.0, loop = null, adsr = null)
    }

    data class LoopRange(val start: Int, val end: Int)
}
