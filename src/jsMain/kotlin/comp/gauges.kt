package io.peekandpoke.klang.comp

import de.peekandpoke.ultra.common.toFixed
import kotlinx.css.Color
import kotlinx.css.LinearDimension
import kotlinx.html.Tag
import kotlin.math.round

/**
 * Standard gauge color scheme used across all gauges
 */
object GaugeColors {
    val excellent = Color.lightSkyBlue
    val good = Color.yellowGreen
    val moderate = Color.yellow
    val warning = Color.orange
    val critical = Color.red
}

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
        0.0..3.0 to GaugeColors.excellent,
        2.0..5.0 to GaugeColors.good,
        4.0..7.0 to GaugeColors.moderate,
        6.0..9.0 to GaugeColors.warning,
        8.0..Double.MAX_VALUE to GaugeColors.critical,
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
        Double.MIN_VALUE..0.2 to GaugeColors.excellent,
        0.2..0.4 to GaugeColors.good,
        0.4..0.6 to GaugeColors.moderate,
        0.6..0.8 to GaugeColors.warning,
        0.8..Double.MAX_VALUE to GaugeColors.critical,
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
        0.0..20.0 to GaugeColors.excellent,
        20.0..40.0 to GaugeColors.good,
        40.0..60.0 to GaugeColors.moderate,
        60.0..80.0 to GaugeColors.warning,
        80.0..Double.MAX_VALUE to GaugeColors.critical,
    ),
    disabled = value == null
)
