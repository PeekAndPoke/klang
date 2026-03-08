package io.peekandpoke.klang.strudel.ui

import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.modals.ModalsManager.Companion.modals
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.html.onInput
import de.peekandpoke.ultra.semanticui.SemanticIconFn
import de.peekandpoke.ultra.semanticui.icon
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.strudel.lang.parser.MnNode
import io.peekandpoke.klang.ui.KlangUiTool
import io.peekandpoke.klang.ui.KlangUiToolContext
import io.peekandpoke.klang.ui.KlangUiToolEmbeddable
import io.peekandpoke.klang.ui.codetools.CodeToolModal
import kotlinx.css.*
import kotlinx.html.*

// ── Tool factory ──────────────────────────────────────────────────────────────

/**
 * A [KlangUiTool] that edits mini-notation pattern strings.
 *
 * The user edits the raw mini-notation string in a text field.
 * Clicking into an atom token reveals a modifier panel and, when [atomTool] is set,
 * an inline or modal sub-tool for editing the atom's value.
 */
class StrudelMiniNotationEditorTool(
    private val atomTool: KlangUiTool? = null,
) : KlangUiTool {
    override val title: String = atomTool?.title ?: "Mini-Notation Editor"

    override val iconFn: SemanticIconFn = atomTool?.iconFn ?: { code }

    override fun FlowContent.render(ctx: KlangUiToolContext) {
        StrudelMiniNotationEditorComp(ctx, atomTool)
    }
}

@Suppress("FunctionName")
private fun Tag.StrudelMiniNotationEditorComp(toolCtx: KlangUiToolContext, atomTool: KlangUiTool?) =
    comp(StrudelMiniNotationEditorComp.Props(toolCtx, atomTool)) { StrudelMiniNotationEditorComp(it) }

// ── Component ─────────────────────────────────────────────────────────────────

private class StrudelMiniNotationEditorComp(ctx: Ctx<Props>) : MnPatternEditorBase<StrudelMiniNotationEditorComp.Props>(ctx) {

    data class Props(
        override val toolCtx: KlangUiToolContext,
        val atomTool: KlangUiTool?,
    ) : BaseProps

    // ── Render ────────────────────────────────────────────────────────────────

    override fun VDom.render() {
        val atom = (selectedAtom ?: lastAtom).also { if (selectedAtom != null) lastAtom = selectedAtom }

        ui.segment {
            css {
                minWidth = 60.vw
                minHeight = 60.vh
                display = Display.flex
                flexDirection = FlexDirection.column
            }

            ui.small.header { +"Mini Notation" }

            mnPatternTextInput(text, atom, parseError) { newText, cursor ->
                text = newText
                cursorOffset = cursor
                lastAtom = lastAtom?.let { a -> pattern?.let { p -> findAtomById(p, a.id) } }
            }

            ui.divider {}
            if (atom != null) {
                mnModifierPanel(atom) { updated -> updateAtom(atom, updated) }
                ui.divider {}
                renderAtomPanel(atom)
            } else {
                mnModifierPanelDisabled()
            }

            div { css { flexGrow = 1.0 } }

            ui.divider {}
            renderBottomBar()
        }
    }

    // ── Atom value panel ──────────────────────────────────────────────────────

    private fun FlowContent.renderAtomPanel(atom: MnNode.Atom) {
        val atomTool = props.atomTool
        when {
            atomTool == null -> renderAtomValueInput(atom)
            atomTool is KlangUiToolEmbeddable -> renderEmbeddedAtomTool(atom, atomTool)
            else -> renderAtomModalButton(atom, atomTool)
        }
    }

    private fun FlowContent.renderAtomValueInput(atom: MnNode.Atom) {
        div {
            css { display = Display.flex; alignItems = Align.center; gap = 8.px }
            span {
                css { fontSize = 12.px; color = Color("#666"); fontWeight = FontWeight.w600; minWidth = 60.px }
                +"Value"
            }
            input {
                type = InputType.text
                value = atom.value
                css {
                    fontFamily = "monospace"
                    fontSize = 14.px
                    padding = Padding(4.px, 8.px)
                    borderRadius = 4.px
                    put("border", "1px solid #ccc")
                }
                onInput { e ->
                    val v = e.target?.asDynamic()?.value as? String ?: return@onInput
                    updateAtom(atom, atom.copy(value = v))
                }
            }
        }
    }

    private fun atomSubCtx(atom: MnNode.Atom, onCancel: () -> Unit, onCommit: (String) -> Unit) =
        props.toolCtx.copy(
            currentValue = "\"${atom.value}\"",
            onCancel = onCancel,
            onCommit = onCommit,
        )

    private fun FlowContent.renderEmbeddedAtomTool(atom: MnNode.Atom, atomTool: KlangUiToolEmbeddable) {
        val subCtx = atomSubCtx(atom, onCancel = {}, onCommit = { newVal ->
            updateAtom(atom, atom.copy(value = newVal.trim().removeSurrounding("\"")))
        })
        with(atomTool) { renderEmbedded(subCtx) }
    }

    private fun FlowContent.renderAtomModalButton(atom: MnNode.Atom, atomTool: KlangUiTool) {
        ui.basic.small.button {
            onClick {
                modals.show { handle ->
                    CodeToolModal(handle) {
                        val subCtx = atomSubCtx(
                            atom,
                            onCancel = { handle.close() },
                            onCommit = { newVal ->
                                updateAtom(atom, atom.copy(value = newVal.trim().removeSurrounding("\"")))
                                handle.close()
                            },
                        )
                        with(atomTool) { render(subCtx) }
                    }
                }
            }
            icon.edit()
            +"Edit '${atom.value}'…"
        }
    }
}

