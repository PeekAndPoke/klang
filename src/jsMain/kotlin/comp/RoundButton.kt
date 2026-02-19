package io.peekandpoke.klang.comp

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.semanticui.SemanticIconFn
import de.peekandpoke.ultra.semanticui.icon
import de.peekandpoke.ultra.semanticui.ui
import kotlinx.css.*
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
) = comp(
    RoundButton.Props(
        icon = icon,
        color = color,
        onClick = onClick,
        title = title,
        disabled = disabled,
        size = size,
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
    )

    override fun VDom.render() {
        val isDisabled = props.disabled
        val iconColor = if (isDisabled) Color.grey else props.color

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
            }

            ui.basic.inverted.white.circular.icon.label {
                css {
                    borderWidth = 1.8.px
                    width = props.size
                    height = props.size
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
                        // Glow effect - stronger glow scaled to button size
                        if (!isDisabled) {
                            // Blur radius: 25% of button size for strong glow
                            val glowBlur = props.size * 0.25
                            put("text-shadow", "0 0 $glowBlur")
                        }
                    }
                }
            }
        }
    }
}
