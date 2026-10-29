/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages

import io.peekandpoke.klang.BuiltInJingles
import io.peekandpoke.klang.Jingle
import io.peekandpoke.klang.Player
import io.peekandpoke.klang.audio_engine.KlangCyclicPlayback
import io.peekandpoke.klang.comp.PlayableCodeExample
import io.peekandpoke.klang.compile
import io.peekandpoke.klang.playJingle
import io.peekandpoke.klang.ui.comp.KlangButton
import io.peekandpoke.klang.ui.comp.KlangControlStyles
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.routing.JoinedPageTitle
import io.peekandpoke.kraft.utils.launch
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.span

@Suppress("FunctionName")
fun Tag.DevJinglesPage() = comp {
    DevJinglesPage(it)
}

/**
 * The Dev Tools' jingles: every jingle ([BuiltInJingles.all]), to hear as the start page plays it (once, its cycles,
 * then it stops) and to change and play over in an editor (the maintainer, 2026-10-29: "add a page in the devtools
 * section "Jingles" so that devs can listen to them... with editor maybe"). The editor plays round and round, as every
 * code example does; a change there stays on the page, the jingle's file keeps its code.
 */
class DevJinglesPage(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    /** The jingle played as on the start page, while it plays; one at a time. */
    private var playing: KlangCyclicPlayback? = null

    init {
        lifecycle {
            onUnmount {
                playing?.stop()
            }
        }
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    /** Plays [jingle] as the start page does, the one before stopped. */
    private fun play(jingle: Jingle) {
        playing?.stop()
        playing = null

        launch {
            val player = Player.ensure().await()
            val pattern = jingle.compile() ?: return@launch

            playing = player.playJingle(jingle = jingle, pattern = pattern)
        }
    }

    override fun VDom.render() {
        JoinedPageTitle { listOf("Jingles", "Dev Tools") }

        div {
            key = "dev-jingles-page"
            css {
                put("box-sizing", "border-box")
                put("height", "100%")
                put("overflow", "auto")
                put("padding", "28px 32px")
                put("color", "var(--kl-ink-text)")
            }

            h1(classes = KlangControlStyles.heading.toString()) {
                css {
                    put("margin", "0 0 10px")
                    put("font-size", "13px")
                }
                +"Jingles"
            }

            span {
                css {
                    put("display", "block")
                    put("margin-bottom", "18px")
                    put("font-size", "13px")
                    put("color", "var(--kl-ink-label)")
                }
                +"What \"Make Music Now\" plays as it leaves the start page, one of these at random."
            }

            for (jingle in BuiltInJingles.all) {
                renderJingle(jingle)
            }
        }
    }

    private fun FlowContent.renderJingle(jingle: Jingle) {
        div {
            key = jingle.id
            css { put("margin-bottom", "26px") }

            div {
                css {
                    put("display", "flex")
                    put("flex-wrap", "wrap")
                    put("align-items", "center")
                    put("gap", "14px")
                    put("margin-bottom", "10px")
                }

                div(classes = KlangControlStyles.heading.toString()) { +jingle.title }

                span {
                    css {
                        put("font-size", "12px")
                        put("color", "var(--kl-ink-label)")
                        put("margin-right", "auto")
                    }
                    +"${jingle.cycles} cycles at ${jingle.rpm} rpm"
                }

                KlangButton(onClick = { play(jingle) }) { +"Play as on the start page" }
                KlangButton(onClick = { playing?.stop() }) { +"Stop" }
            }

            PlayableCodeExample(code = jingle.code, rpm = jingle.rpm)
        }
    }
}
