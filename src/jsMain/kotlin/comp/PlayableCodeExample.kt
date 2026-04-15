package io.peekandpoke.klang.comp

import io.peekandpoke.klang.script.stdlibLib
import io.peekandpoke.klang.sprudel.lang.sprudelLib
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.ComponentRef
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.semanticui.forms.UiInputField
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.ultra.semanticui.noui
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.css.Align
import kotlinx.css.Border
import kotlinx.css.BorderStyle
import kotlinx.css.Color
import kotlinx.css.Overflow
import kotlinx.css.alignSelf
import kotlinx.css.border
import kotlinx.css.borderBottom
import kotlinx.css.borderRadius
import kotlinx.css.color
import kotlinx.css.height
import kotlinx.css.marginBottom
import kotlinx.css.marginTop
import kotlinx.css.overflow
import kotlinx.css.paddingLeft
import kotlinx.css.px
import kotlinx.css.rem
import kotlinx.css.width
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.title

@Suppress("FunctionName")
fun Tag.PlayableCodeExample(
    code: String,
    rpm: Double = 30.0,
) = comp(
    PlayableCodeExample.Props(code = code, rpm = rpm)
) {
    PlayableCodeExample(it)
}

class PlayableCodeExample(ctx: Ctx<Props>) : Component<PlayableCodeExample.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        val code: String,
        val rpm: Double = 30.0,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val ctrl = KlangCodePlaybackCtrl.builder()
        .code(props.code)
        .rpm(props.rpm)
        .exclusive()
        .build()

    private val editorCompRef = ComponentRef.Tracker<KlangCodeEditorComp>()

    private val state by subscribingTo(ctrl.state)
    private val laf by subscribingTo(KlangTheme)

    private var highlightPerEvent by value(5) { newValue ->
        editorCompRef { it.setMaxHighlightsPerEvent(newValue) }
    }

    private val isModifiedFromOriginal get() = state.code != props.code

    init {
        lifecycle {
            onUnmount { ctrl.stop() }
        }
    }

    //  RENDER  /////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        div(classes = laf.styles.darken10()) {
            key = "container"

            css {
                border = Border(1.px, BorderStyle.solid, Color(laf.textTertiary))
                borderRadius = 4.px
                overflow = Overflow.hidden
                marginTop = 0.5.rem
                marginBottom = 0.5.rem
            }

            // Control bar
            ui.mini.form {
                key = "controlBar"

                css {
                    borderBottom = Border(1.px, BorderStyle.solid, Color(laf.textTertiary))
                    paddingLeft = 0.5.rem
                }

                ui.horizontal.list {
                    key = "controlBarItems"

                    // Play / Update button
                    noui.item {
                        if (!state.isPlaying) {
                            ui.small.circular.white.button {
                                onClick { ctrl.play() }
                                if (state.isPlayerLoading) {
                                    icon.loading.spinner()
                                    +"Loading"
                                } else {
                                    icon.play()
                                    +"Play"
                                }
                            }
                        } else {
                            ui.small.circular.white.givenNot(state.isCodeModified) { disabled }.button {
                                onClick { ctrl.play() }
                                icon.black.redo_alternate()
                                +"Update"
                            }
                        }
                    }

                    // Stop button
                    noui.item {
                        ui.small.circular.icon.givenNot(state.isPlaying) { disabled }.button {
                            onClick { ctrl.stop() }
                            icon.black.stop()
                        }
                    }

                    // Reset button (only enabled if modified from original)
                    noui.item {
                        ui.small.circular.givenNot(isModifiedFromOriginal) { disabled }.button {
                            onClick {
                                ctrl.stop()
                                ctrl.setCode(props.code)
                                editorCompRef { it.setCode(props.code) }
                            }
                            icon.undo()
                            +"Reset"
                        }
                    }

                    // RPM field
                    noui.item {
                        css { width = 150.px }
                        UiInputField(state.rpm, { ctrl.setRpm(it) }) {
                            step(0.5)
                            wrapFieldWith { fluid }
                            leftLabel {
                                ui.grey.label { +"RPM" }
                            }
                        }
                    }

                    // Highlight-per-event field
                    noui.item {
                        css { width = 120.px }
                        UiInputField(highlightPerEvent, { highlightPerEvent = it }) {
                            step(1)
                            wrapFieldWith { fluid }
                            leftLabel {
                                ui.grey.label {
                                    title = "Max highlights per audio event"
                                    +"EVT"
                                }
                            }
                        }
                    }

                    // Cycle counter
                    noui.item {
                        css {
                            alignSelf = Align.center
                            color = Color.grey
                            height = 32.px
                        }
                        LcdDisplay(
                            value = state.currentCycle,
                            digits = 3,
                            dim = !state.isPlaying,
                        )
                    }
                }
            }

            // Code editor
            div {
                key = "editor"

                KlangCodeEditorComp(
                    ctrl = ctrl,
                    availableLibraries = listOf(stdlibLib, sprudelLib),
                    autoImportedLibraries = listOf(stdlibLib, sprudelLib),
                    maxHighlightsPerEvent = highlightPerEvent,
                ).track(editorCompRef)
            }
        }
    }
}
