package io.peekandpoke.klang.workshop.keys

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.eventually
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.span
import kotlinx.html.style
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList

private val play = KeyAction(id = "play", title = "Play", keys = listOf("Mod-Shift-Enter"))
private val stop = KeyAction(id = "stop", title = "Stop", keys = listOf("Mod-Shift-."))

/** True when the stage placed [hint] and it can be seen: the stage also draws every hint unseen once, to measure it. */
private fun isPlaced(hint: Element): Boolean {
    val wrapper = hint.closest("[data-key-hint]") ?: return false

    return hint.getBoundingClientRect().width > 0.0 && wrapper.getAttribute("style")?.contains("visibility: hidden") != true
}

/** A hint of a fixed size, wider than the buttons, so the two would overlap where they stand. */
private val wideHint: KeyHintRenderer = { state ->
    span(classes = "stage-hint") {
        style = "display: inline-block; width: 80px; height: 16px; background: #222"
        +state.label
    }
}

/**
 * The hint stage in a real page: the hints of two buttons side by side, the second one faded as a disabled button is,
 * are drawn outside the buttons, below them, without overlapping.
 */
class KeyHintStageSpec : StringSpec({

    "two buttons side by side: their hints below them, apart, and not inside the buttons" {
        val manager = KeysManager(platform = KeyPlatform.Pc, hintDelayMs = 10).apply {
            define(object : KeyBindings {
                override val actions = listOf(play, stop)
            })
        }

        try {
            TestBed.preact(
                appSetup = { keys(manager, hint = wideHint) },
                view = {
                    div {
                        style = "position: absolute; left: 200px; top: 100px"

                        button(classes = "play") {
                            style = "width: 60px; height: 30px"
                            OnKey(play) { }
                        }

                        button(classes = "stop") {
                            // Faded as a disabled button: its own layer, which must not reach the hint
                            style = "width: 30px; height: 30px; opacity: 0.45"
                            OnKey(stop, enabled = false) { }
                        }
                    }
                },
            ) {
                document.body!!.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", ctrl = true))

                eventually {
                    val hints = document.querySelectorAll(".stage-hint").asList().map { it as Element }
                    hints.size shouldBe 2

                    val playHint = hints.first { it.textContent == "Ctrl+Shift+Enter" }.getBoundingClientRect()
                    val stopHint = hints.first { it.textContent == "Ctrl+Shift+." }.getBoundingClientRect()
                    val playButton = document.querySelector(".play")!!.getBoundingClientRect()

                    withClue("below the buttons") {
                        playHint.top shouldBeGreaterThanOrEqual playButton.bottom
                        stopHint.top shouldBeGreaterThanOrEqual playButton.bottom
                    }

                    withClue("apart, in the buttons' order") {
                        stopHint.left shouldBeGreaterThanOrEqual playHint.right
                    }

                    withClue("drawn on the stage, outside the faded button") {
                        hints.none { document.querySelector(".stop")!!.contains(it) } shouldBe true
                    }
                }

                document.body!!.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", type = "keyup"))
            }
        } finally {
            manager.dispose()
        }
    }

    "the default look: one key cap per key, the signs as sharp vector icons, the words as text" {
        val manager = KeysManager(platform = KeyPlatform.Pc, hintDelayMs = 10).apply {
            define(object : KeyBindings {
                override val actions = listOf(play)
            })
        }

        try {
            TestBed.preact(
                appSetup = { keys(manager) },
                view = {
                    button(classes = "play") {
                        style = "position: absolute; left: 200px; top: 100px; width: 60px; height: 30px"
                        OnKey(play) { }
                    }
                },
            ) {
                document.body!!.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", ctrl = true))

                eventually {
                    val caps = document.querySelectorAll(".${KeyHints.BADGE_CLASS} kbd").asList().map { it as Element }

                    caps.size shouldBe 3
                    caps[0].textContent shouldBe "Ctrl"
                    withClue("Shift and Enter are icons, not glyphs of the font") {
                        caps.drop(1).all { it.querySelector("svg") != null } shouldBe true
                    }
                }

                document.body!!.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", type = "keyup"))
            }
        } finally {
            manager.dispose()
        }
    }

    "a button scrolled out of its panel gets no hint, its neighbour in view does" {
        val manager = KeysManager(platform = KeyPlatform.Pc, hintDelayMs = 10).apply {
            define(object : KeyBindings {
                override val actions = listOf(play, stop)
            })
        }

        try {
            TestBed.preact(
                appSetup = { keys(manager, hint = wideHint) },
                view = {
                    div(classes = "panel") {
                        style = "position: fixed; left: 200px; top: 100px; width: 200px; height: 60px; overflow-y: auto"

                        button(classes = "play") {
                            style = "display: block; width: 60px; height: 30px"
                            OnKey(play) { }
                        }

                        // Below the panel's visible part, but well inside the window
                        div { style = "height: 100px" }

                        button(classes = "stop") {
                            style = "display: block; width: 60px; height: 30px"
                            OnKey(stop) { }
                        }
                    }
                },
            ) {
                document.body!!.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", ctrl = true))

                eventually {
                    val shown = document.querySelectorAll(".stage-hint").asList()
                        .map { it as Element }
                        .filter { isPlaced(it) }

                    shown.map { it.textContent } shouldBe listOf("Ctrl+Shift+Enter")
                }

                document.body!!.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", type = "keyup"))
            }
        } finally {
            manager.dispose()
        }
    }

    "a scrolled page whose body has overflow-x hidden (as Fomantic sets it) still shows the hints in view" {
        val manager = KeysManager(platform = KeyPlatform.Pc, hintDelayMs = 10).apply {
            define(object : KeyBindings {
                override val actions = listOf(play)
            })
        }

        val html = document.documentElement as HTMLElement
        val body = document.body!!
        val before = listOf(html.style.height, body.style.height, body.style.overflowX)

        try {
            html.style.height = "100%"
            body.style.height = "100%"
            body.style.overflowX = "hidden"

            TestBed.preact(
                appSetup = { keys(manager, hint = wideHint) },
                view = {
                    div {
                        // A long page, the button far down it
                        style = "height: 3000px; position: relative"

                        button(classes = "play") {
                            style = "position: absolute; left: 200px; top: 1500px; width: 60px; height: 30px"
                            OnKey(play) { }
                        }
                    }
                },
            ) {
                window.scrollTo(0.0, 1400.0)

                withClue("the button is in view after the scroll") {
                    document.querySelector(".play")!!.getBoundingClientRect().top shouldBeLessThan window.innerHeight.toDouble()
                }

                document.body!!.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", ctrl = true))

                eventually {
                    // Placed and seen: the stage also draws every hint unseen once, to measure it
                    val shown = document.querySelectorAll(".stage-hint").asList()
                        .map { it as Element }
                        .filter { isPlaced(it) }

                    withClue("its hint shows: the body clips nothing, its overflow belongs to the window") {
                        shown.size shouldBe 1
                    }
                }

                document.body!!.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", type = "keyup"))
            }
        } finally {
            window.scrollTo(0.0, 0.0)
            html.style.height = before[0]
            body.style.height = before[1]
            body.style.overflowX = before[2]
            manager.dispose()
        }
    }
})
