package io.peekandpoke.klang.comp

import de.peekandpoke.ultra.common.toFixed
import kotlinx.css.Color
import kotlinx.css.LinearDimension
import kotlinx.html.Tag

fun Tag.roundOrbitsGauge(
    value: Double,
    size: LinearDimension,
) = RoundGauge(
    size = size,
    value = value,
    display = { it.toInt().toString() },
    title = "Active Orbits",
    range = 0.0..10.0,
    icon = { small.satellite },
    iconColors = listOf(
        0.0..4.0 to Color.lightGreen,
        4.0..6.0 to Color.yellow,
        6.0..8.0 to Color.orange,
        8.0..Double.MAX_VALUE to Color.red,
    )
)

fun Tag.renderHeadroomGauge(
    value: Double,
    size: LinearDimension,
) = RoundGauge(
    size = size,
    value = 1.0 - value,
    display = { (1.0 - it).toFixed(2) },
    title = "Render Headroom",
    range = 0.0..1.0,
    icon = { small.microchip },
    iconColors = listOf(
        Double.MIN_VALUE..0.4 to Color.lightGreen,
        0.25..0.6 to Color.yellow,
        0.45..0.8 to Color.orange,
        0.56..Double.MAX_VALUE to Color.red,
    )
)

fun Tag.activeVoicesGauge(
    value: Int,
    size: LinearDimension,
) = RoundGauge(
    size = size,
    value = value.toDouble(),
    display = { it.toInt().toString().padStart(2, '0') },
    title = "Active Voices",
    range = 0.0..80.0,
    icon = { small.volume_up },
    iconColors = listOf(
        0.0..30.0 to Color.lightGreen,
        25.0..40.0 to Color.yellow,
        35.0..50.0 to Color.orange,
        50.0..Double.MAX_VALUE to Color.red,
    )
)
