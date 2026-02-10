package io.peekandpoke.klang.comp

import de.peekandpoke.ultra.common.toFixed
import kotlinx.css.Color
import kotlinx.css.LinearDimension
import kotlinx.html.Tag
import kotlin.math.ceil

fun Tag.roundOrbitsGauge(
    value: Double,
    size: LinearDimension,
) = RoundGauge(
    size = size,
    value = { value },
    display = { ceil(it).toInt().toString() },
    title = "Active Orbits",
    range = 0.0..10.0,
    icon = { small.satellite },
    iconColors = listOf(
        0.0..3.0 to Color.lightGreen,
        2.0..5.0 to Color.green,
        4.0..7.0 to Color.yellow,
        6.0..9.0 to Color.orange,
        8.0..Double.MAX_VALUE to Color.red,
    )
)

fun Tag.renderHeadroomGauge(
    value: Double,
    size: LinearDimension,
) = RoundGauge(
    size = size,
    value = { 1.0 - value },
    display = { (1.0 - it).toFixed(2) },
    title = "Render Headroom",
    range = 0.0..1.0,
    icon = { small.microchip },
    iconColors = listOf(
        Double.MIN_VALUE..0.4 to Color.lightGreen,
        0.3..0.5 to Color.green,
        0.4..0.6 to Color.yellow,
        0.5..0.7 to Color.orange,
        0.6..Double.MAX_VALUE to Color.red,
    )
)

fun Tag.activeVoicesGauge(
    value: Int,
    size: LinearDimension,
) = RoundGauge(
    size = size,
    value = { value.toDouble() },
    display = { ceil(it).toInt().toString().padStart(2, '0') },
    title = "Active Voices",
    range = 0.0..100.0,
    icon = { small.volume_up },
    iconColors = listOf(
        0.0..30.0 to Color.lightGreen,
        20.0..50.0 to Color.green,
        40.0..50.0 to Color.yellow,
        60.0..90.0 to Color.orange,
        80.0..Double.MAX_VALUE to Color.red,
    )
)
