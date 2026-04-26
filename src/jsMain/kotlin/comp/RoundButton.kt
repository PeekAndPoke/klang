package io.peekandpoke.klang.comp

import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.html.onMouseEnter
import io.peekandpoke.ultra.html.onMouseLeave
import io.peekandpoke.ultra.semanticui.SemanticIconFn
import io.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.css.Align
import kotlinx.css.Color
import kotlinx.css.Cursor
import kotlinx.css.Display
import kotlinx.css.JustifyContent
import kotlinx.css.LinearDimension
import kotlinx.css.Position
import kotlinx.css.alignItems
import kotlinx.css.borderWidth
import kotlinx.css.color
import kotlinx.css.cursor
import kotlinx.css.display
import kotlinx.css.fontSize
import kotlinx.css.height
import kotlinx.css.justifyContent
import kotlinx.css.position
import kotlinx.css.px
import kotlinx.css.width
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.title

@Suppress("FunctionName")
fun Tag.RoundButton(
    icon: SemanticIconFn,
    color: Color,
    onClick: () -> Unit,
    title: String? = null,
    disabled: Boolean = false,
    size: LinearDimension = 50.px,
    backgroundColor: Color? = null,
) = comp(
    RoundButton.Props(
        icon = icon,
        color = color,
        onClick = onClick,
        title = title,
        disabled = disabled,
        size = size,
        backgroundColor = backgroundColor,
    )
) {
    RoundButton(it)
}

class RoundButton(ctx: Ctx<Props>) : Component<RoundButton.Props>(ctx) {

    data class Props(
        val icon: SemanticIconFn,
        val color: Color,
        val onClick: () -> Unit,
        val title: String?,
        val disabled: Boolean,
        val size: LinearDimension,
        val backgroundColor: Color?,
    )

    private var isHovered: Boolean by value(false)

    override fun VDom.render() {
        val isDisabled = props.disabled
        val iconColor = if (isDisabled) Color.grey else props.color
        val hovered = isHovered && !isDisabled

        div {
            css {
                position = Position.relative
                display = Display.inlineBlock
                if (!isDisabled) {
                    cursor = Cursor.pointer
                }
            }

            if (!isDisabled) {
                onClick { props.onClick() }
                onMouseEnter { isHovered = true }
                onMouseLeave { isHovered = false }
            }

            ui.basic.inverted.white.circular.icon.label {
                css {
                    borderWidth = 1.8.px
                    width = props.size
                    height = props.size
                    props.backgroundColor?.let { bg ->
                        put("background-color", "$bg !important")
                    }
                    // Explicit centering for icon
                    display = Display.flex
                    alignItems = Align.center
                    justifyContent = JustifyContent.center
                }

                props.title?.let { title = it }

                val iconFn = props.icon

                icon.iconFn().then {
                    css {
                        // Icon size: 50% of button height
                        fontSize = props.size * 0.5
                        color = iconColor
                        put("padding", "0px !important")
                        put("transition", "filter 220ms ease, text-shadow 220ms ease")
                        // Idle state reads as a dimmed ember; hover lifts to full
                        // brightness with a wider glow so the button "lights up".
                        if (!isDisabled) {
                            put("filter", if (hovered) "brightness(1.0)" else "brightness(0.65)")
                            val glowBlur = props.size * if (hovered) 0.32 else 0.25
                            put("text-shadow", "0 0 $glowBlur")
                        }
                    }
                }
            }
        }
    }
}
