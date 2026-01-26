package io.peekandpoke.klang.audio_bridge

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class FilterDef {
    @Serializable
    @SerialName("low-pass")
    data class LowPass(
        val cutoffHz: Double,
        val q: Double?,
        val envelope: FilterEnvelope? = null,
    ) : FilterDef()

    @Serializable
    @SerialName("high-pass")
    data class HighPass(
        val cutoffHz: Double,
        val q: Double?,
        val envelope: FilterEnvelope? = null,
    ) : FilterDef()

    @Serializable
    @SerialName("band-pass")
    data class BandPass(
        val cutoffHz: Double,
        val q: Double?,
        val envelope: FilterEnvelope? = null,
    ) : FilterDef()

    @Serializable
    @SerialName("notch")
    data class Notch(
        val cutoffHz: Double,
        val q: Double?,
        val envelope: FilterEnvelope? = null,
    ) : FilterDef()
}
