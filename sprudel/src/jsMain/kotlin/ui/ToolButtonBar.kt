package io.peekandpoke.klang.sprudel.ui

import io.peekandpoke.klang.ui.codetools.KlangToolAutoUpdate
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.css.*
import kotlinx.html.Tag
import kotlinx.html.div

@Suppress("FunctionName")
internal fun Tag.ToolButtonBar(
    isInitialModified: Boolean,
    isCurrentModified: Boolean,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onCommit: () -> Unit,
) = comp(
    ToolButtonBarComp.Props(
        isInitialModified = isInitialModified,
        isCurrentModified = isCurrentModified,
        onCancel = onCancel,
        onReset = onReset,
        onCommit = onCommit,
    )
) { ToolButtonBarComp(it) }

internal class ToolButtonBarComp(ctx: Ctx<Props>) : Component<ToolButtonBarComp.Props>(ctx) {

    data class Props(
        val isInitialModified: Boolean,
        val isCurrentModified: Boolean,
        val onCancel: () -> Unit,
        val onReset: () -> Unit,
        val onCommit: () -> Unit,
    )

    private val laf by subscribingTo(KlangTheme)
    private val autoUpdate by subscribingTo(KlangToolAutoUpdate)

    override fun VDom.render() {
        div {
            css {
                display = Display.flex
                justifyContent = JustifyContent.spaceBetween
                alignItems = Align.center
                gap = 8.px
            }

            // Left side: auto-update toggle
            div {
                ui.mini.givenNot(autoUpdate) { basic }.given(autoUpdate) { with(laf.styles.goldButton()) }.button {
                    onClick { KlangToolAutoUpdate.toggle() }
                    icon.sync_alternate()
                    +"Auto-update"
                }
            }

            // Right side: action buttons
            div {
                css { display = Display.flex; gap = 8.px }
                ui.basic.button {
                    onClick { props.onCancel() }
                    icon.times()
                    +"Cancel"
                }
                ui.basic.givenNot(props.isInitialModified) { disabled }.button {
                    onClick { props.onReset() }
                    icon.undo()
                    +"Reset"
                }
                if (!autoUpdate) {
                    ui.black.givenNot(props.isCurrentModified) { disabled }.button {
                        onClick { props.onCommit() }
                        icon.check()
                        +"Update"
                    }
                }
            }
        }
    }
}
