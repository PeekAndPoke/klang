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
import kotlinx.html.div
import kotlinx.html.span
import kotlinx.html.title

private val save = KeyAction(id = "save", title = "Save", keys = listOf("Mod-S"))
private val open = KeyAction(id = "open", title = "Open", keys = listOf("Mod-O"))

/** An onKey that is the same function at every render, so an OnKey's props stay equal. */
private val stableOnKey: () -> Unit = {}

/** The page under test: an OnKey whose presence, state and action the test changes. */
private class OnKeyHost(ctx: NoProps) : PureComponent(ctx) {
    companion object {
        var current: OnKeyHost? = null
    }

    var shown by value(true)
    var enabled by value(true)
    var action by value(save)
    var showHint by value(true)
    var inert by value(false)
    var label by value("first")

    /** Whether the OnKey sits in a button, and the button's own title; else in a plain `div`. */
    var inButton by value(false)
    var buttonTitle: String? by value("Save it")

    /** A second OnKey in the button, for Open. */
    var twoKeys by value(false)

    /** A `div` with a button's role while [roleOn], its own title [divTitle], an OnKey whose props never change. */
    var roleDiv by value(false)
    var roleOn by value(true)
    var divTitle by value("Open it")
    val pressed = mutableListOf<String>()

    init {
        current = this
    }

    override fun VDom.render() {
        div {
            if (inert) {
                // Not "": Preact sets inert as the element's property, and an empty string is false there
                attributes["inert"] = "inert"
            }

            if (shown && !inButton) {
                // A new lambda every render: the newest one must answer
                val version = "${action.id}, enabled=$enabled, $label"
                OnKey(action = action, enabled = enabled, showHint = showHint) { pressed.add(version) }
            }

            if (roleDiv) {
                div {
                    attributes["data-test"] = "role-div"

                    if (roleOn) {
                        attributes["role"] = "button"
                    }

                    title = divTitle
                    // The same props at every render: a title the page changes must still get the key
                    OnKey(action = open, onKey = stableOnKey)
                }
            }

            if (inButton) {
                button {
                    buttonTitle?.let { title = it }

                    if (shown) {
                        OnKey(action = action, enabled = enabled, showHint = showHint) { pressed.add("button") }
                    }

                    if (twoKeys) {
                        OnKey(action = open) { pressed.add("open") }
                    }

                    +"Save"
                }
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

    "inside an inert part of the page (a page fading out) it answers no key and shows no hint" {
        withApp { _, host ->
            host.inert = true
            delay(30)
            press("s")
            document.body!!.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", ctrl = true))
            delay(60)

            host.pressed shouldContainExactly emptyList()
            document.querySelector(".app-hint") shouldBe null

            document.body!!.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", type = "keyup"))
        }
    }

    "in a button, the key goes into its hover text after its own; it follows a new key, the page's title, and goes with it" {
        withApp { keys, host ->
            fun title() = document.querySelector("button")?.getAttribute("title")

            withClue("in a div: no hover text") { document.querySelector("div[title]") shouldBe null }

            host.inButton = true

            eventually { title() shouldBe "Save it (Ctrl+S)" }

            keys.setUserKeys(actionId = "save", keys = listOf("F2"))
            withClue("a key the user changes") { eventually { title() shouldBe "Save it (F2)" } }

            host.buttonTitle = "Keep it"
            withClue("the page's own title, changed") { eventually { title() shouldBe "Keep it (F2)" } }

            host.showHint = false
            withClue("no hint shown: its own title alone") { eventually { title() shouldBe "Keep it" } }

            host.showHint = true
            eventually { title() shouldBe "Keep it (F2)" }

            host.shown = false
            withClue("the OnKey gone: the button's own title back") { eventually { title() shouldBe "Keep it" } }

            host.shown = true
            host.buttonTitle = null
            withClue("a button without a title of its own: the key alone") { eventually { title() shouldBe "F2" } }

            host.enabled = false
            // Preact leaves an empty title where the page's own went
            withClue("a key that does not act: not in the hover text") { eventually { title().isNullOrEmpty() shouldBe true } }

            host.enabled = true
            host.buttonTitle = "Save it"
            eventually { title() shouldBe "Save it (F2)" }

            host.twoKeys = true
            delay(30)
            host.label = "redrawn"
            delay(30)
            withClue("two keys in one button: the first writes the hover text, once, however often they redraw") {
                title() shouldBe "Save it (F2)"
            }

            host.enabled = false
            withClue("the first's key not acting: the second's in the hover text") {
                eventually { title() shouldBe "Save it (Ctrl+O)" }
            }

            host.enabled = true
            delay(30)

            host.twoKeys = false
            host.buttonTitle = "Store it"
            host.shown = false
            withClue("the page's own title changed as the key goes: the page's stays") { eventually { title() shouldBe "Store it" } }
        }
    }

    "an element with a button's role takes the key; a title the page changes gets it again; no role, no key" {
        withApp { _, host ->
            fun title() = document.querySelector("[data-test=role-div]")?.getAttribute("title")

            host.roleDiv = true
            eventually { title() shouldBe "Open it (Ctrl+O)" }

            host.divTitle = "Open the file"
            withClue("the page's title changed, the OnKey's props the same: the key again") {
                eventually { title() shouldBe "Open the file (Ctrl+O)" }
            }

            host.roleOn = false
            withClue("no longer a button: its own title back") { eventually { title() shouldBe "Open the file" } }
        }
    }
})
