package io.peekandpoke.klang.codetools

import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.modals.ModalsManager
import de.peekandpoke.kraft.semanticui.modals.FadingModal
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.semanticui.ui
import kotlinx.css.LinearDimension
import kotlinx.css.width
import kotlinx.html.FlowContent
import kotlinx.html.Tag

@Suppress("FunctionName")
fun Tag.CodeToolModal(
    handle: ModalsManager.Handle,
    content: FlowContent.(ModalsManager.Handle) -> Unit,
) = comp(
    CodeToolModal.Props(
        handle = handle,
        content = content,
    )
) {
    CodeToolModal(it)
}

class CodeToolModal(ctx: Ctx<Props>) : FadingModal<CodeToolModal.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        override val handle: ModalsManager.Handle,
        val content: FlowContent.(ModalsManager.Handle) -> Unit,
    ) : FadingModal.Props()

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun FlowContent.renderContent() {
        ui.modal.transition.visible.active.front {
            css {
                width = LinearDimension.fitContent
            }
            val content = props.content
            content(props.handle)
        }
    }
}
