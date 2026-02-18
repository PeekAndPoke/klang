package io.peekandpoke.klang.utils

import kotlinx.css.Color

fun mixColor(value: Number, colors: List<Pair<ClosedRange<Double>, Color>>): Color {
    val dbl = value.toDouble()
    val initial = colors.firstOrNull()?.second ?: return Color.white

    val matched = colors.filter { it.first.contains(dbl) }

    if (matched.isEmpty()) return Color.white

    if (matched.size == 1) return matched.first().second

    val blended = matched.drop(1).fold(matched.first().second) { acc, color ->
        acc.blend(color.second)
    }

    return blended
}
