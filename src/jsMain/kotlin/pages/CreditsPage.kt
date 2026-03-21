package io.peekandpoke.klang.pages

import de.peekandpoke.kraft.components.NoProps
import de.peekandpoke.kraft.components.PureComponent
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.key
import de.peekandpoke.ultra.semanticui.noui
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.ui.comp.MarkdownDisplay
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.a

@Suppress("FunctionName")
fun Tag.CreditsPage() = comp {
    CreditsPage(it)
}

class CreditsPage(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        ui.fluid.container {
            key = "credits-page"

            ui.basic.segment {

                ui.header H1 { +"Credits" }

                ui.two.column.stackable.doubling.cards {
                    renderStrudelCard()
                    renderBrowserCard()
                    renderFoundationCard()
                    renderEditorCard()
                    renderUiCard()
                    renderMusicAndAudioCard()
                    renderRuntimeCard()
                    renderTestingCard()
                }
            }
        }
    }

    private fun FlowContent.renderStrudelCard() {
        noui.card {
            noui.content {
                ui.header H2 { +"Shout out to Strudel" }

                ui.divider()

                MarkdownDisplay(
                    """
                        **Klang exists because of Strudel.** What started as pure curiosity — *"how does this work?"* —
                        turned into a deep dive into the world of audio engines, pattern languages, and music synthesis.

                        Strudel's open-source code, its open sample libraries, and the excellent documentation at
                        [strudel.cc](https://strudel.cc) were invaluable in understanding the machinery behind it all.

                        What started as a copycat of Strudel is now evolving into its sibling — **Sprudel**.
                        Sharing the same roots and many of the same ideas, Sprudel takes the pattern language
                        in new directions as part of Klang.

                        A heartfelt thank you to the Strudel community for making all of this openly available.
                    """.trimIndent()
                )

                ui.divider()

                ui.list {
                    noui.item {
                        a(href = "https://strudel.cc", target = "_blank") { +"https://strudel.cc" }
                    }
                    noui.item {
                        a(href = "https://codeberg.org/uzu/strudel", target = "_blank") { +"Strudel on Codeberg" }
                    }
                }
            }
        }
    }

    private fun FlowContent.renderBrowserCard() {
        noui.card {
            noui.content {
                ui.header H2 { +"The Modern Browser" }

                ui.divider()

                MarkdownDisplay(
                    """
                        None of this would be possible without the incredible platform that modern browsers have become.
                        The performance and the richness of APIs available today are nothing short of amazing:

                        - **[Web Audio API](https://developer.mozilla.org/en-US/docs/Web/API/Web_Audio_API)** — real-time audio synthesis and processing
                        - **[AudioWorklet](https://developer.mozilla.org/en-US/docs/Web/API/AudioWorklet)** — high-performance audio processing on a dedicated thread
                        - **[Canvas API](https://developer.mozilla.org/en-US/docs/Web/API/Canvas_API)** — rendering oscilloscopes, waveforms, and visualizations
                        - **[Web Workers](https://developer.mozilla.org/en-US/docs/Web/API/Web_Workers_API)** — parallel processing without blocking the UI
                    """.trimIndent()
                )
            }
        }
    }

    private fun FlowContent.renderFoundationCard() {
        noui.card {
            noui.content {
                ui.header H2 { +"The Foundation" }

                ui.divider()

                MarkdownDisplay(
                    """
                        Klang is built on the shoulders of these giants:

                        - **[Kotlin](https://kotlinlang.org)** & **[Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)** —
                        the language and multiplatform foundation that makes it possible to share code across platforms
                        - **[KotlinX Coroutines](https://github.com/Kotlin/kotlinx.coroutines)** — async programming made elegant
                        - **[KotlinX Serialization](https://github.com/Kotlin/kotlinx.serialization)** — data serialization
                        - **[Ktor](https://ktor.io)** — HTTP framework powering the backend
                        - **[Gradle](https://gradle.org)** — build automation
                        - **[Google KSP](https://github.com/google/ksp)** — Kotlin Symbol Processing for code generation
                    """.trimIndent()
                )
            }
        }
    }

    private fun FlowContent.renderEditorCard() {
        noui.card {
            noui.content {
                ui.header H2 { +"The Editor" }

                ui.divider()

                MarkdownDisplay(
                    """
                        The live coding experience is powered by:

                        - **[CodeMirror 6](https://codemirror.net)** — the code editor at the heart of the coding experience
                        - **[Lezer](https://lezer.codemirror.net)** — parser generator powering syntax highlighting
                    """.trimIndent()
                )
            }
        }
    }

    private fun FlowContent.renderMusicAndAudioCard() {
        noui.card {
            noui.content {
                ui.header H2 { +"Music & Audio" }

                ui.divider()

                MarkdownDisplay(
                    """
                        Making music possible:

                        - **[Tonal.js](https://github.com/tonaljs/tonal)** — music theory library (ported to Kotlin in the :tones module)
                        - **[SoundFont2](https://www.npmjs.com/package/soundfont2)** — SoundFont parsing
                    """.trimIndent()
                )
            }
        }
    }

    private fun FlowContent.renderUiCard() {
        noui.card {
            noui.content {
                ui.header H2 { +"The UI" }

                ui.divider()

                MarkdownDisplay(
                    """
                        Looking good thanks to:

                        - **[Semantic UI](https://semantic-ui.com)** / **[Fomantic UI](https://fomantic-ui.com)** — the UI framework behind the interface
                        - **[CommonMark](https://github.com/commonmark/commonmark-java)** — markdown rendering
                    """.trimIndent()
                )
            }
        }
    }

    private fun FlowContent.renderRuntimeCard() {
        noui.card {
            noui.content {
                ui.header H2 { +"The Runtime" }

                ui.divider()

                MarkdownDisplay(
                    """
                        Bridging worlds:

                        - **[GraalVM](https://www.graalvm.org)** — polyglot runtime used in early development to feed the audio engine with strudel events produced by the original JavaScript implementation
                    """.trimIndent()
                )
            }
        }
    }

    private fun FlowContent.renderTestingCard() {
        noui.card {
            noui.content {
                ui.header H2 { +"Testing" }

                ui.divider()

                MarkdownDisplay(
                    """
                        Keeping things working:

                        - **[Kotest](https://kotest.io)** — Kotlin testing framework
                    """.trimIndent()
                )
            }
        }
    }
}
