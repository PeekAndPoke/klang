package io.peekandpoke.klang.comp

import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import kotlinx.css.Align
import kotlinx.css.Color
import kotlinx.css.Display
import kotlinx.css.FontWeight
import kotlinx.css.JustifyContent
import kotlinx.css.LinearDimension
import kotlinx.css.Overflow
import kotlinx.css.Position
import kotlinx.css.alignItems
import kotlinx.css.backgroundColor
import kotlinx.css.borderRadius
import kotlinx.css.color
import kotlinx.css.display
import kotlinx.css.fontSize
import kotlinx.css.fontWeight
import kotlinx.css.height
import kotlinx.css.justifyContent
import kotlinx.css.overflow
import kotlinx.css.position
import kotlinx.css.px
import kotlinx.css.width
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.span

@Suppress("FunctionName")
fun Tag.LcdDisplay(
    value: Int,
    digits: Int = 4,
    color: Color = KlangTheme.good,
    size: LinearDimension = 16.px,
    backgroundColor: Color = Color("#111111"),
) = comp(
    LcdDisplay.Props(
        value = value,
        digits = digits,
        color = color,
        size = size,
        backgroundColor = backgroundColor,
    )
) {
    LcdDisplay(it)
}

class LcdDisplay(ctx: Ctx<Props>) : Component<LcdDisplay.Props>(ctx) {

    data class Props(
        val value: Int,
        val digits: Int,
        val color: Color,
        val size: LinearDimension,
        val backgroundColor: Color,
    )

    override fun VDom.render() {
        val displayValue = props.value.toString().takeLast(props.digits).padStart(props.digits, '0')
        val dimColor = Color("${props.color}22")

        div {
            css {
                display = Display.inlineFlex
                alignItems = Align.center
                backgroundColor = props.backgroundColor
                borderRadius = 3.px
                put("padding", "2px 4px")
                put("gap", "1px")
                put("font-family", "'Courier New', 'Consolas', monospace")
                fontWeight = FontWeight.bold
                fontSize = props.size
            }

            for (i in 0 until props.digits) {
                val digit = displayValue[i].digitToInt()

                // Each digit slot
                div {
                    css {
                        position = Position.relative
                        width = LinearDimension("0.7em")
                        height = LinearDimension("1.3em")
                        overflow = Overflow.hidden
                    }

                    // Ghost "8" (all segments visible, dim)
                    span {
                        css {
                            position = Position.absolute
                            display = Display.flex
                            width = LinearDimension("100%")
                            height = LinearDimension("100%")
                            alignItems = Align.center
                            justifyContent = JustifyContent.center
                            color = dimColor
                        }
                        +"8"
                    }

                    // Rolling digit strip (0-9 stacked, translateY animates)
                    div {
                        css {
                            position = Position.absolute
                            width = LinearDimension("100%")
                            height = LinearDimension("1000%")
                            put("transition", "transform 0.3s ease-out")
                            put("transform", "translateY(${-digit * 10}%)")
                        }

                        for (d in 0..9) {
                            div {
                                css {
                                    height = LinearDimension("10%")
                                    display = Display.flex
                                    alignItems = Align.center
                                    justifyContent = JustifyContent.center
                                    color = props.color
                                    put("text-shadow", "none")
                                }
                                +"$d"
                            }
                        }
                    }
                }
            }
        }
    }
}
