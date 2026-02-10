package io.peekandpoke.klang.utils

import kotlinx.css.Color

fun mixColor(value: Number, colors: List<Pair<ClosedRange<Double>, Color>>): Color {
    val dbl = value.toDouble()
    val initial = colors.firstOrNull()?.second ?: return Color.white

    return colors.fold(initial) { acc, color ->
        if (color.first.contains(dbl)) color.second.blend(acc) else acc
    }
}
