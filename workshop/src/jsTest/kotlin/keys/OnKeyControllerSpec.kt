package io.peekandpoke.klang.workshop.keys

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.eventually
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.browser.document
import kotlinx.coroutines.delay
import kotlinx.html.Tag
import kotlinx.html.button
import kotlinx.html.span

private val play = KeyAction(id = "play", title = "Play", keys = listOf("Mod-P"))
private val pause = KeyAction(id = "pause", title = "Pause", keys = listOf("Mod-U"))
private val search = KeyAction(id = "search", title = "Search", keys = listOf("Shift Shift", "Mod-K"))

/** An app's own button that writes its key into its label, with no OnKey and no controller code of its own. */
private class OwnButton(ctx: NoProps) : PureComponent(ctx) {
    companion object {
        var current: OwnButton? = null
    }

    var armed by value(true)
    val pressed = mutableListOf<String>()

    val key: OnKeyController = onKeyController(action = play, enabled = { armed }) { pressed.add(key.action.id) }

    init {
        current = this
    }

    override fun VDom.render() {
        button {
            +"Go"
            key.hint?.let { hint -> span(classes = "own-key") { +" (${hint.label}${if (hint.enabled) "" else ", off"})" } }
        }
    }
}

@Suppress("FunctionName")
private fun Tag.OwnButton() = comp { OwnButton(it) }

/**
 * The controller in an app's own component: it answers while mounted, its hint redraws the component, a new action
 * takes over at once, and the hint says when the action cannot act.
 */
class OnKeyControllerSpec : StringSpec({

    suspend fun withApp(block: suspend (KeysManager, OwnButton) -> Unit) {
        val manager = KeysManager(platform = KeyPlatform.Pc, hintDelayMs = 10).apply {
            define(object : KeyBindings {
                override val actions = listOf(play, pause, search)
            })
        }

        try {
            TestBed.preact(appSetup = { keys(manager) }, view = { OwnButton() }) {
                block(manager, OwnButton.current!!)
            }
        } finally {
            manager.dispose()
        }
    }

    fun press(key: String) {
        document.body!!.dispatchEvent(keyEvent(key = key, code = "Key${key.uppercase()}", ctrl = true))
    }

    fun holdCtrl() {
        document.body!!.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", ctrl = true))
    }

    fun releaseCtrl() {
        document.body!!.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", type = "keyup"))
    }

    "the component's own label shows the key while the hints show, redrawn by the controller" {
        withApp { _, _ ->
            document.querySelector(".own-key") shouldBe null

            holdCtrl()
            eventually {
                document.querySelector(".own-key")?.textContent shouldBe " (Ctrl+P)"
            }

            releaseCtrl()
            eventually {
                document.querySelector(".own-key") shouldBe null
            }
        }
    }

    "it answers its action while enabled; the hint says when it cannot act" {
        withApp { _, button ->
            press("p")
            button.armed = false
            delay(30)
            press("p")

            button.pressed shouldContainExactly listOf("play")

            holdCtrl()
            eventually {
                document.querySelector(".own-key")?.textContent shouldBe " (Ctrl+P, off)"
            }
            releaseCtrl()
        }
    }

    "a new action takes over at once, and the hint follows" {
        withApp { _, button ->
            button.key.action = pause
            press("p")

            withClue("the old key") {
                button.pressed shouldContainExactly emptyList()
            }

            press("u")
            button.pressed shouldContainExactly listOf("pause")

            holdCtrl()
            eventually {
                document.querySelector(".own-key")?.textContent shouldBe " (Ctrl+U)"
            }
            releaseCtrl()
        }
    }

    "under another layer the hint waits and the key does not answer; when the layer closes both come back" {
        withApp { keys, button ->
            val modal = keys.openLayer()
            press("p")
            holdCtrl()
            delay(60)

            withClue("under the modal") {
                button.pressed shouldContainExactly emptyList()
                document.querySelector(".own-key") shouldBe null
            }

            modal.close()

            eventually {
                document.querySelector(".own-key")?.textContent shouldBe " (Ctrl+P)"
            }

            releaseCtrl()
            press("p")
            button.pressed shouldContainExactly listOf("play")
        }
    }

    "a hint that always shows: without the hints held, still only while its layer is on top" {
        withApp { keys, button ->
            button.key.hintMode = KeyHintMode.Always
            eventually {
                document.querySelector(".own-key")?.textContent shouldBe " (Ctrl+P)"
            }

            val modal = keys.openLayer()
            eventually {
                withClue("under a modal") { document.querySelector(".own-key") shouldBe null }
            }

            modal.close()
            eventually {
                document.querySelector(".own-key")?.textContent shouldBe " (Ctrl+P)"
            }

            button.key.hintMode = KeyHintMode.WhileHeld
            eventually {
                withClue("back to while held") { document.querySelector(".own-key") shouldBe null }
            }
        }
    }

    "keys in a row first: the hint shows them as one group per step, the chords stay apart" {
        withApp { _, button ->
            button.key.action = search

            holdCtrl()
            eventually {
                document.querySelector(".own-key")?.textContent shouldBe " (Shift Shift)"
            }

            val hint = button.key.hint!!
            hint.keyGroups() shouldBe listOf(listOf("⇧"), listOf("⇧"))
            hint.keyParts() shouldBe listOf("⇧", "⇧")
            hint.chords shouldContainExactly listOf(KeyChord(key = "k", ctrl = true))
            releaseCtrl()
        }
    }
})
