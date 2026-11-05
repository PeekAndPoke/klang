/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.peekandpoke.klang.spine.Playbacks
import io.peekandpoke.klang.spine.SongDeck
import io.peekandpoke.klang.ui.KlangKeyBindings
import io.peekandpoke.klang.workshop.keys.KeyPlatform
import io.peekandpoke.klang.workshop.keys.KeysManager
import io.peekandpoke.klang.workshop.keys.keys
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.eventually
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.browser.document
import kotlinx.coroutines.delay
import kotlinx.html.Tag
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.KeyboardEventInit

/** The status bar's view as the app's bar draws it, its song, its fade and its keys set from the spec. */
private class BarHost(ctx: NoProps) : PureComponent(ctx) {
    companion object {
        var current: BarHost? = null
    }

    var line: Playbacks.SongLine? by value(null)
    var shown: Boolean by value(true)
    var openKey: Boolean by value(true)

    val opened = mutableListOf<String>()

    init {
        current = this
    }

    override fun VDom.render() {
        statusBarView(
            line = line,
            idleText = "Klangmotor",
            shown = shown,
            onOpen = { opened.add(it) },
            onStop = {},
            openKey = openKey,
        )
    }
}

@Suppress("FunctionName")
private fun Tag.BarHost() = comp { BarHost(it) }

/** Alt+P opens the page of the song that plays, as a click on its title does (KLANG-00412). */
class StatusBarSpec : StringSpec({

    val kokon = Playbacks.SongLine(
        playing = SongDeck.Playing(songId = "kokon", title = "Kokon", isPlaying = true),
        seconds = 3,
    )

    fun altP() = document.body!!.dispatchEvent(
        KeyboardEvent(
            "keydown",
            KeyboardEventInit(key = "p", code = "KeyP", altKey = true, bubbles = true, cancelable = true),
        )
    )

    "Alt+P opens the playing song's page; not with no song, not faded out on its own page, not in a bar without keys" {
        val manager = KeysManager(platform = KeyPlatform.Pc, hintDelayMs = 10).apply { define(KlangKeyBindings) }

        try {
            TestBed.preact(appSetup = { keys(manager) }, view = { BarHost() }) {
                val host = BarHost.current!!

                withClue("no song: nothing to open") {
                    delay(30)
                    altP()
                    host.opened shouldContainExactly emptyList()
                }

                host.line = kokon

                withClue("a song shows: its page opens") {
                    eventually {
                        altP()
                        host.opened shouldContainExactly listOf("kokon")
                    }
                }

                host.shown = false
                delay(30)
                altP()

                withClue("faded out, on the song's own page: the key is off") {
                    host.opened shouldContainExactly listOf("kokon")
                }

                host.shown = true
                host.openKey = false
                delay(30)
                altP()

                withClue("a bar without keys, the kitchen sink's") {
                    host.opened shouldContainExactly listOf("kokon")
                }
            }
        } finally {
            manager.dispose()
        }
    }
})
