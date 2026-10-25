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
import kotlinx.html.div
import kotlinx.html.span

private val save = KeyAction(id = "save", title = "Save", keys = listOf("Mod-S"))
private val open = KeyAction(id = "open", title = "Open", keys = listOf("Mod-O"))

/** The page under test: an OnKey whose presence, state and action the test changes. */
private class OnKeyHost(ctx: NoProps) : PureComponent(ctx) {
    companion object {
        var current: OnKeyHost? = null
    }

    var shown by value(true)
    var enabled by value(true)
    var action by value(save)
    var showHint by value(true)
    var label by value("first")
    val pressed = mutableListOf<String>()

    init {
        current = this
    }

    override fun VDom.render() {
        div {
            if (shown) {
                // A new lambda every render: the newest one must answer
                val version = "${action.id}, enabled=$enabled, $label"
                OnKey(action = action, enabled = enabled, showHint = showHint) { pressed.add(version) }
            }
        }
    }
}

@Suppress("FunctionName")
private fun Tag.OnKeyHost() = comp { OnKeyHost(it) }

/** A hint in the app's own look, set once for the app. */
private val appHint: KeyHintRenderer = { state -> span(classes = "app-hint") { +"app ${state.label}" } }

/**
 * OnKey in a mounted Kraft app: it answers while it is in the page, with the latest render's handler and state, follows
 * a new action, and draws the app's hint while the hints show.
 */
class OnKeySpec : StringSpec({

    suspend fun withApp(block: suspend (KeysManager, OnKeyHost) -> Unit) {
        val manager = KeysManager(platform = KeyPlatform.Pc, hintDelayMs = 10).apply {
            define(object : KeyBindings {
                override val actions = listOf(save, open)
            })
        }

        try {
            TestBed.preact(appSetup = { keys(manager, hint = appHint) }, view = { OnKeyHost() }) {
                block(manager, OnKeyHost.current!!)
            }
        } finally {
            manager.dispose()
        }
    }

    fun press(key: String) {
        document.body!!.dispatchEvent(keyEvent(key = key, code = "Key${key.uppercase()}", ctrl = true))
    }

    "it answers while mounted, with the latest render's handler; disabled it lets the key pass; gone it answers no more" {
        withApp { _, host ->
            press("s")

            host.enabled = false
            delay(30)
            press("s")

            host.enabled = true
            delay(30)
            press("s")

            host.shown = false
            delay(30)
            press("s")

            host.pressed shouldContainExactly listOf("save, enabled=true, first", "save, enabled=true, first")
        }
    }

    "a later render's handler answers, not the first one" {
        withApp { _, host ->
            press("s")
            host.label = "second"
            delay(30)
            press("s")

            host.pressed shouldContainExactly listOf("save, enabled=true, first", "save, enabled=true, second")
        }
    }

    "a new action: the old key no longer answers, the new one does" {
        withApp { _, host ->
            host.action = open
            delay(30)
            press("s")

            withClue("the old key") {
                host.pressed shouldContainExactly emptyList()
            }

            press("o")
            host.pressed shouldContainExactly listOf("open, enabled=true, first")
        }
    }

    "the hint: the app's renderer on the stage while the hints show, none when OnKey says showHint = false" {
        withApp { _, host ->
            withClue("no hint before Ctrl is held") {
                document.querySelector(".app-hint") shouldBe null
            }

            document.body!!.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", ctrl = true))

            eventually {
                document.querySelector(".app-hint")?.textContent shouldBe "app Ctrl+S"
            }

            host.showHint = false
            delay(30)
            withClue("showHint = false") {
                document.querySelector(".app-hint") shouldBe null
            }

            document.body!!.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", type = "keyup"))
        }
    }

    "the hint follows a key the user changes" {
        withApp { keys, _ ->
            keys.setUserKeys(actionId = "save", keys = listOf("F2"))
            document.body!!.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", ctrl = true))

            eventually {
                document.querySelector(".app-hint")?.textContent shouldBe "app F2"
            }

            document.body!!.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", type = "keyup"))
        }
    }
})
