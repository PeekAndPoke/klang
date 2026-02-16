package io.peekandpoke.klang.comp

import de.peekandpoke.ultra.common.toFixed
import kotlinx.css.Color
import kotlinx.css.LinearDimension
import kotlinx.html.Tag
import kotlin.math.round

fun Tag.roundOrbitsGauge(
    value: Double?,
    size: LinearDimension,
) = RoundGauge(
    size = size,
    value = { value ?: 0.0 },
    display = { if (value == null) "-" else round(it).toInt().toString() },
    title = "Active Orbits",
    range = 0.0..10.0,
    icon = { small.satellite },
    iconColors = listOf(
        0.0..3.0 to Color.lightSkyBlue,
        2.0..5.0 to Color.yellowGreen,
        4.0..7.0 to Color.yellow,
        6.0..9.0 to Color.orange,
        8.0..Double.MAX_VALUE to Color.red,
    ),
    disabled = value == null
)

fun Tag.renderHeadroomGauge(
    value: Double?,
    size: LinearDimension,
) = RoundGauge(
    size = size,
    value = { if (value != null) 1.0 - value else 0.0 },
    display = { if (value == null) "-.--" else (1.0 - it).toFixed(2) },
    title = "Render Headroom",
    range = 0.0..1.0,
    icon = { small.microchip },
    iconColors = listOf(
        Double.MIN_VALUE..0.4 to Color.lightSkyBlue,
        0.3..0.5 to Color.yellowGreen,
        0.4..0.6 to Color.yellow,
        0.5..0.7 to Color.orange,
        0.6..Double.MAX_VALUE to Color.red,
    ),
    disabled = value == null
)

fun Tag.activeVoicesGauge(
    value: Int?,
    size: LinearDimension,
) = RoundGauge(
    size = size,
    value = { value?.toDouble() ?: 0.0 },
    display = {
        if (value == null) "--" else round(it).toInt().toString().padStart(2, '0')
    },
    title = "Active Voices",
    range = 0.0..100.0,
    icon = { small.music },
    iconColors = listOf(
        0.0..30.0 to Color.lightSkyBlue,
        20.0..50.0 to Color.yellowGreen,
        40.0..70.0 to Color.yellow,
        60.0..90.0 to Color.orange,
        80.0..Double.MAX_VALUE to Color.red,
    ),
    disabled = value == null
)
