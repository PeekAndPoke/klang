package io.peekandpoke.klang.comp

import io.peekandpoke.klang.Player
import io.peekandpoke.klang.script.KlangScriptLibrary
import io.peekandpoke.klang.script.runtime.NullValue
import io.peekandpoke.klang.script.runtime.RuntimeValue
import io.peekandpoke.klang.script.stdlibLib
import io.peekandpoke.klang.ui.codemirror.KlangScriptEditorComp
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.ComponentRef
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.css.*
import kotlinx.css.properties.LineHeight
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.span

@Suppress("FunctionName")
fun Tag.KlangScriptReplComp(
    initialCode: String,
    autoImportLibraries: List<KlangScriptLibrary> = listOf(stdlibLib),
) = comp(KlangScriptReplComp.Props(initialCode, autoImportLibraries)) { KlangScriptReplComp(it) }

class KlangScriptReplComp(ctx: Ctx<Props>) : Component<KlangScriptReplComp.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        val initialCode: String,
        val autoImportLibraries: List<KlangScriptLibrary> = listOf(stdlibLib),
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val laf by subscribingTo(KlangTheme)
    private var code by value(props.initialCode)
    private var output by value(emptyList<String>())
    private var result by value<String?>(null)
    private var hasError by value(false)

    private val editorRef = ComponentRef.Tracker<KlangScriptEditorComp>()

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    private fun run() {
        val outputLines = mutableListOf<String>()

        val built = Player.createEngine(
            outputHandler = { _, args -> outputLines.add(args.joinToString(" ")) }
        )

        // Pre-execute import statements for auto-imported libraries
        for (lib in props.autoImportLibraries) {
            built.execute("""import * from "${lib.name}"""")
        }

        // Clear previous editor errors
        editorRef { it.setErrors(emptyList()) }

        try {
            val resultValue: RuntimeValue = built.execute(code)
            output = outputLines
            result = when {
                resultValue is NullValue -> null
                resultValue.value is Unit -> null
                else -> resultValue.toDisplayString()
            }
            hasError = false
        } catch (e: Throwable) {
            output = outputLines
            result = e.message ?: "Unknown error"
            hasError = true
            // Show error in editor
            editorRef { it.setErrors(listOf(mapToEditorError(e))) }
        }
    }

    private fun clear() {
        output = emptyList()
        result = null
        hasError = false
        editorRef { it.setErrors(emptyList()) }
    }

    //  RENDER  /////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        div(classes = laf.styles.darken10()) {
            css {
                border = Border(1.px, BorderStyle.solid, Color(laf.textTertiary))
                borderRadius = 4.px
                overflow = Overflow.hidden
                marginTop = 0.5.rem
                marginBottom = 0.5.rem
            }

            // Control bar
            div {
                css {
                    borderBottom = Border(1.px, BorderStyle.solid, Color(laf.textTertiary))
                    padding = Padding(0.5.rem)
                    display = Display.flex
                    gap = 0.5.rem
                    alignItems = Align.center
                }

                // Run button
                ui.mini.circular.white.button {
                    onClick { run() }
                    icon.play()
                    +"Run"
                }

                // Clear button
                ui.mini.circular.button {
                    onClick { clear() }
                    icon.eraser()
                    +"Clear"
                }
            }

            // Code editor
            KlangScriptEditorComp(
                code = code,
                onCodeChanged = { newCode ->
                    code = newCode
                    editorRef { it.setErrors(emptyList()) }
                },
                availableLibraries = props.autoImportLibraries,
                autoImportedLibraries = props.autoImportLibraries,
            ).track(editorRef)

            // Output panel (only shown when there is output or a result)
            if (output.isNotEmpty() || result != null) {
                div {
                    css {
                        borderTop = Border(1.px, BorderStyle.solid, Color(laf.textTertiary))
                        padding = Padding(0.75.rem)
                        fontFamily = "monospace"
                        fontSize = 13.px
                        lineHeight = LineHeight("1.5")
                        whiteSpace = WhiteSpace.preWrap
                        backgroundColor = Color("rgba(0,0,0,0.15)")
                    }

                    // Output lines
                    for (line in output) {
                        div {
                            css { color = Color(laf.textSecondary) }
                            +line
                        }
                    }

                    // Result line
                    if (result != null) {
                        div {
                            css {
                                color = if (hasError) Color(laf.critical) else Color(laf.accent)
                                if (output.isNotEmpty()) {
                                    marginTop = 0.25.rem
                                    borderTop = Border(1.px, BorderStyle.dashed, Color(laf.textTertiary))
                                    paddingTop = 0.25.rem
                                }
                            }
                            span {
                                css { opacity = 0.6 }
                                +if (hasError) "Error: " else "-> "
                            }
                            +result!!
                        }
                    }
                }
            }
        }
    }
}
