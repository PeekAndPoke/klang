package io.peekandpoke.klang.audio_bridge

import kotlinx.serialization.Serializable

@Serializable
sealed class FilterDef {
    data class LowPass(val cutoffHz: Double, val q: Double?) : FilterDef()
    data class HighPass(val cutoffHz: Double, val q: Double?) : FilterDef()
}
