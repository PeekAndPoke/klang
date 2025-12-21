package io.peekandpoke

import kotlin.reflect.KFunction

typealias OscFn = (phase: Double) -> Double

@Suppress("UNCHECKED_CAST")
fun OscFn.name() = try {
    (this as? KFunction<Double>)?.name ?: this.toString()
} catch (_: Exception) {
    this.toString()
}

object SimpleOsc {

    fun oscSaw(phase: Double): Double {
        val x = phase / (2.0 * Math.PI)
        return 2.0 * (x - kotlin.math.floor(x + 0.5))
    }

    fun oscSquare(phase: Double): Double = if (kotlin.math.sin(phase) >= 0.0) 1.0 else -1.0

    fun oscTri(phase: Double): Double {
        // Triangle via asin(sin) normalized to [-1,1]
        return (2.0 / Math.PI) * kotlin.math.asin(kotlin.math.sin(phase))
    }
}
