package io.peekandpoke.klang.strudel

import kotlinx.serialization.Serializable

@Serializable
sealed class StrudelFilterDef {
    data class LowPass(val cutoffHz: Double, val q: Double?) : StrudelFilterDef()
    data class HighPass(val cutoffHz: Double, val q: Double?) : StrudelFilterDef()
}
