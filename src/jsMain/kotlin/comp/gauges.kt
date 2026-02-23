package io.peekandpoke.klang.comp

import de.peekandpoke.ultra.common.toFixed
import io.peekandpoke.klang.feel.KlangStudioColors
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
    range = 0.0..32.0,
    icon = { small.satellite },
    colors = KlangStudioColors.rangedMixer(0, 32),
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
    colors = KlangStudioColors.rangedMixer(0.0, 1.0),
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
    colors = KlangStudioColors.rangedMixer(0, 100),
    disabled = value == null
)
