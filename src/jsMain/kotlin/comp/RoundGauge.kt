package io.peekandpoke.klang.comp

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.semanticui.SemanticIconFn
import de.peekandpoke.ultra.semanticui.icon
import de.peekandpoke.ultra.semanticui.semanticIcon
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.externals.ResizeObserver
import io.peekandpoke.klang.utils.mixColor
import kotlinx.css.*
import kotlinx.html.Tag
import kotlinx.html.canvas
import kotlinx.html.div
import kotlinx.html.title
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import kotlin.math.PI
import kotlin.math.min

@Suppress("FunctionName")
fun Tag.RoundGauge(
    value: () -> Double,
    display: ((Double) -> String)?,
    title: String?,
    range: ClosedRange<Double>,
    icon: SemanticIconFn?,
    iconColors: List<Pair<ClosedRange<Double>, Color>>,
    disabled: Boolean,
    size: LinearDimension = 50.px,
) = comp(
    RoundGauge.Props(
        value = value,
        display = display,
        title = title,
        range = range,
        icon = icon,
        iconColors = iconColors,
        disabled = disabled,
        size = size,
    )
) {
    RoundGauge(it)
}

class RoundGauge(ctx: Ctx<Props>) : Component<RoundGauge.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    companion object {
        // for extensions
    }

    data class Props(
        val value: () -> Double,
        val display: ((Double) -> String)?,
        val title: String?,
        val range: ClosedRange<Double>,
        val icon: SemanticIconFn?,
        val iconColors: List<Pair<ClosedRange<Double>, Color>>,
        val disabled: Boolean,
        val size: LinearDimension,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private var canvas: HTMLCanvasElement? = null
    private var ctx2d: CanvasRenderingContext2D? = null
    private var resizeObserver: ResizeObserver? = null

    // Smoothed value for smooth needle movement
    private var smoothedValue: Double by value(props.range.start)

    private val textColor = Color.white.withAlpha(0.5)

    init {
        lifecycle {
            onMount {
                dom?.let { container ->
                    canvas = container.querySelector("canvas") as? HTMLCanvasElement
                    ctx2d = canvas?.getContext("2d") as? CanvasRenderingContext2D

                    resizeObserver = ResizeObserver { entries, _ ->
                        for (entry in entries) {
                            val width = entry.contentRect.width
                            val height = entry.contentRect.height

                            canvas?.width = width.toInt()
                            canvas?.height = height.toInt()

                            draw()
                        }
                    }
                    resizeObserver?.observe(container)
                }
            }

            onNextProps { _, newProps ->
                // Smooth the value changes (90% old + 10% new)
                smoothedValue = (smoothedValue * 9.0 + newProps.value()) / 10.0

                draw()
            }

            onUnmount {
                resizeObserver?.disconnect()
            }
        }
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        val isDisabled = props.disabled
        val iconFn: SemanticIconFn = props.icon ?: semanticIcon { this }
        val iconColor = if (isDisabled) {
            Color.grey
        } else {
            mixColor(props.value(), props.iconColors)
        }

        div {
            css {
                position = Position.relative
                display = Display.inlineBlock
            }

            ui.basic.inverted.white.circular.icon.label {
                css {
                    borderWidth = 1.8.px
                    width = props.size
                    height = props.size
                    position = Position.relative
                }

                props.title?.let { title = it }

                icon.iconFn().then {
                    css {
                        color = iconColor
                        if (!isDisabled) {
                            put("text-shadow", "0 0 10px")
                        }
                    }
                }

                props.display?.let { display ->
                    div {
                        css {
                            position = Position.absolute
                            left = 0.px
                            bottom = 20.pct
                            width = 100.pct
                            textAlign = TextAlign.center
                            color = textColor
                        }
                        +display(smoothedValue)
                    }
                }
            }

            canvas {
                css {
                    position = Position.absolute
                    top = 0.px
                    left = 0.px
                    width = 100.pct
                    height = 100.pct
                    pointerEvents = PointerEvents.none
                }
            }
        }
    }

    private fun draw() {
        val canvas = canvas ?: return
        val ctx = ctx2d ?: return

        val w = canvas.width.toDouble()
        val h = canvas.height.toDouble()
        val cx = w / 2
        val cy = h / 2

        val size = min(w, h)

        ctx.clearRect(0.0, 0.0, w, h)

        // Calculate indicator angle
        // Gauge range: 135deg (bottom-left) to 405deg (bottom-right) = 270deg sweep
        val startAngle = 0.75 * PI  // 135deg
        val fullSweep = 1.5 * PI     // 270deg sweep

        // Use smoothed value for gradual movement
        val value = (smoothedValue).coerceIn(props.range.start, props.range.endInclusive)
        val rangeSize = props.range.endInclusive - props.range.start
        val percentage = if (rangeSize > 0) (value - props.range.start) / rangeSize else 0.0

        val indicatorAngle = startAngle + (percentage * fullSweep)

        // Draw subtle tick marks inside the circle
        val tickColor = "rgba(255, 255, 255, 0.33)"
        val tickCount = 11  // Number of tick marks
        val tickOuterRadius = (size / 2) * 0.88  // Start just inside circle edge
        val tickInnerRadius = (size / 2) * 0.78  // End further inside

        ctx.strokeStyle = tickColor
        ctx.lineWidth = 1.0

        for (i in 0 until tickCount) {
            val tickAngle = startAngle + (i.toDouble() / (tickCount - 1)) * fullSweep

            val x1 = cx + (tickOuterRadius * kotlin.math.cos(tickAngle))
            val y1 = cy + (tickOuterRadius * kotlin.math.sin(tickAngle))
            val x2 = cx + (tickInnerRadius * kotlin.math.cos(tickAngle))
            val y2 = cy + (tickInnerRadius * kotlin.math.sin(tickAngle))

            ctx.beginPath()
            ctx.moveTo(x1, y1)
            ctx.lineTo(x2, y2)
            ctx.stroke()
        }

        // Draw needle as a triangle (sharp tip)
        val color = Color.white

        // Needle dimensions
        val innerRadius = size * 0.08  // Start point (just beyond center)
        val outerRadius = (size / 2) * 0.85  // Tip point
        val needleWidth = size * 0.03  // Width at base

        // Calculate tip point (sharp end)
        val tipX = cx + (outerRadius * kotlin.math.cos(indicatorAngle))
        val tipY = cy + (outerRadius * kotlin.math.sin(indicatorAngle))

        // Calculate base point (at inner radius)
        val baseX = cx + (innerRadius * kotlin.math.cos(indicatorAngle))
        val baseY = cy + (innerRadius * kotlin.math.sin(indicatorAngle))

        // Calculate perpendicular offset for needle width
        val perpAngle = indicatorAngle + (PI / 2)
        val offsetX = needleWidth * kotlin.math.cos(perpAngle)
        val offsetY = needleWidth * kotlin.math.sin(perpAngle)

        // Draw triangle: tip + two base corners
        ctx.fillStyle = color.toString()
        ctx.beginPath()
        ctx.moveTo(tipX, tipY)  // Sharp tip
        ctx.lineTo(baseX + offsetX, baseY + offsetY)  // Base left
        ctx.lineTo(baseX - offsetX, baseY - offsetY)  // Base right
        ctx.closePath()
        ctx.fill()
    }
}
