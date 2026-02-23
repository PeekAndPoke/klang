package io.peekandpoke.klang.feel

import kotlinx.css.Color
import kotlin.math.floor

interface ValueToColorMixer {
    fun getColor(value: Double): Color
}

class RangeColorMixer(
    private val range: ClosedRange<Double>,
    private val colors: List<Color>,
) : ValueToColorMixer {

    private val stepSize = (range.endInclusive - range.start) / (colors.size.coerceAtLeast(1))
    private fun colorIndex(value: Double): Double = (value - range.start) / stepSize

    override fun getColor(value: Double): Color {
        if (colors.isEmpty()) return Color.grey

        if (value <= range.start) return colors.first()
        if (value >= range.endInclusive) return colors.last()

        val idx = colorIndex(value)
        val fraction = idx - floor(idx)

        val base = colors[idx.toInt().coerceAtMost(colors.lastIndex)]

        val color = when {
            fraction < 0.5 -> base
            else -> base.blend(colors[(idx + 1).toInt().coerceAtMost(colors.lastIndex)])
        }

        return color
    }
}
