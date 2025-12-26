package io.peekandpoke.klang.audio_bridge

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class FilterDef {
    @Serializable
    @SerialName("low-pass")
    data class LowPass(val cutoffHz: Double, val q: Double?) : FilterDef()

    @Serializable
    @SerialName("high-pass")
    data class HighPass(val cutoffHz: Double, val q: Double?) : FilterDef()
}
