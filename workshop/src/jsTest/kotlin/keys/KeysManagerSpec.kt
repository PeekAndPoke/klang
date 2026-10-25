package io.peekandpoke.klang.workshop.keys

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.delay
import org.w3c.dom.EventInit
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.pointerevents.PointerEvent
import org.w3c.dom.pointerevents.PointerEventInit

/**
 * The key manager on a real element in the browser: bags of actions, the handler stack, the user's keys, the keys it
 * leaves alone, and the hints.
 */
class KeysManagerSpec : StringSpec({

    val play = KeyAction(id = "play", title = "Play", keys = listOf("Mod-Shift-Enter"))
    val stop = KeyAction(id = "stop", title = "Stop", keys = listOf("Mod-Shift-."))
    val close = KeyAction(id = "close", title = "Close", keys = listOf("Escape"))

    val bag = object : KeyBindings {
        override val actions = listOf(play, stop, close)
    }

    val ctrlShiftEnter = { keyEvent(key = "Enter", code = "Enter", ctrl = true, shift = true) }

    /** A manager listening on a fresh element in the page, given to [block] with that element. */
    suspend fun withManager(
        store: KeymapStore = KeymapStore.InMemory(),
        hintDelayMs: Int = 400,
        block: suspend (KeysManager, HTMLElement) -> Unit,
    ) {
        val host = document.createElement("div") as HTMLElement
        document.body!!.appendChild(host)
        val manager = KeysManager(platform = KeyPlatform.Pc, store = store, hintDelayMs = hintDelayMs, target = host)

        try {
            block(manager, host)
        } finally {
            manager.dispose()
            host.remove()
        }
    }

    "a handler answers its action's key, and the key goes no further" {
        withManager { keys, host ->
            keys.define(bag)
            var played = 0
            keys.handle(play) { played++ }

            val event = ctrlShiftEnter()
            host.dispatchEvent(event)

            played shouldBe 1
            event.defaultPrevented shouldBe true

            withClue("another key is left to the browser") {
                val other = keyEvent(key = "Enter", ctrl = true)
                host.dispatchEvent(other)

                played shouldBe 1
                other.defaultPrevented shouldBe false
            }
        }
    }

    "handlers stack: the newest enabled one answers, the one before takes over when it goes or is disabled" {
        withManager { keys, host ->
            keys.define(bag)
            val answered = mutableListOf<String>()
            keys.handle(close) { answered.add("page") }
            var dialogOpen = true
            val dialog = keys.handle(close, enabled = { dialogOpen }) { answered.add("dialog") }

            host.dispatchEvent(keyEvent(key = "Escape"))
            dialogOpen = false
            host.dispatchEvent(keyEvent(key = "Escape"))
            dialogOpen = true
            dialog.remove()
            host.dispatchEvent(keyEvent(key = "Escape"))

            answered shouldContainExactly listOf("dialog", "page", "page")
        }
    }

    "registrations go out of order without disturbing the others" {
        withManager { keys, host ->
            keys.define(bag)
            val answered = mutableListOf<String>()
            val first = keys.handle(close) { answered.add("first") }
            val second = keys.handle(close) { answered.add("second") }
            keys.handle(close) { answered.add("third") }.remove()

            host.dispatchEvent(keyEvent(key = "Escape"))
            first.remove()
            host.dispatchEvent(keyEvent(key = "Escape"))
            second.remove()
            second.remove()
            host.dispatchEvent(keyEvent(key = "Escape"))

            answered shouldContainExactly listOf("second", "second")
        }
    }

    "update swaps what a handler does and keeps its place in the stack" {
        withManager { keys, host ->
            keys.define(bag)
            val answered = mutableListOf<String>()
            val below = keys.handle(close) { answered.add("below") }
            val top = keys.handle(close) { answered.add("top") }

            below.update { answered.add("below, new") }
            host.dispatchEvent(keyEvent(key = "Escape"))
            top.remove()
            host.dispatchEvent(keyEvent(key = "Escape"))

            answered shouldContainExactly listOf("top", "below, new")
        }
    }

    "no enabled handler: the key is left to the browser" {
        withManager { keys, host ->
            keys.define(bag)
            keys.handle(play, enabled = { false }) { error("disabled") }

            val event = ctrlShiftEnter()
            host.dispatchEvent(event)

            event.defaultPrevented shouldBe false
        }
    }

    "bags: an action defined twice is refused; a bag's update and removal change the keymap" {
        withManager { keys, _ ->
            val registration = keys.define(bag)

            shouldThrow<IllegalArgumentException> {
                keys.define(object : KeyBindings {
                    override val actions = listOf(play)
                })
            }
            keys.keymap().actions shouldContainExactly listOf(play, stop, close)

            registration.update(object : KeyBindings {
                override val actions = listOf(stop)
            })
            keys.keymap().actions shouldContainExactly listOf(stop)

            registration.remove()
            keys.keymap().actions shouldContainExactly emptyList()
        }
    }

    "the user's keys: the handler follows without registering again, and they are kept in the store" {
        val store = KeymapStore.InMemory()

        withManager(store = store) { keys, host ->
            keys.define(bag)
            var played = 0
            keys.handle(play) { played++ }

            keys.setUserKeys(actionId = "play", keys = listOf("F5"))
            host.dispatchEvent(ctrlShiftEnter())
            host.dispatchEvent(keyEvent(key = "F5", code = "F5"))

            played shouldBe 1
            store.load() shouldBe mapOf("play" to listOf("F5"))
            keys.labelOf(play) shouldBe "F5"

            keys.resetUserKeys("play")
            host.dispatchEvent(ctrlShiftEnter())

            played shouldBe 2
            store.load() shouldBe emptyMap()
        }

        withClue("a new manager starts from the stored keys") {
            store.save(mapOf("play" to listOf("F6")))

            withManager(store = store) { keys, _ ->
                keys.define(bag)
                keys.labelOf(play) shouldBe "F6"
            }
        }
    }

    "chordsOf follows the user's keys" {
        withManager { keys, _ ->
            keys.define(bag)
            val seen = mutableListOf<List<KeyChord>>()
            val unsubscribe = keys.chordsOf(play).subscribeToStream { seen.add(it) }

            keys.setUserKeys(actionId = "play", keys = listOf("F5"))
            keys.setUserKeys(actionId = "stop", keys = listOf("F8"))
            unsubscribe()

            withClue("a change to another action publishes nothing new") {
                seen shouldContainExactly listOf(
                    listOf(KeyChord(key = "Enter", ctrl = true, shift = true)),
                    listOf(KeyChord(key = "F5")),
                )
            }
        }
    }

    "keys it leaves alone: one handled already, a held key's repeat, a plain key while typing" {
        withManager { keys, host ->
            keys.define(bag)
            val answered = mutableListOf<String>()
            keys.handle(play) { answered.add("play") }
            keys.handle(close) { answered.add("close") }

            withClue("handled already, by the code editor say") {
                val event = ctrlShiftEnter()
                event.preventDefault()
                host.dispatchEvent(event)
            }

            withClue("a repeat") {
                host.dispatchEvent(keyEvent(key = "Enter", ctrl = true, shift = true, repeat = true))
            }

            val input = document.createElement("input") as HTMLElement
            host.appendChild(input)

            withClue("Esc in a text field belongs to the field, Ctrl+Shift+Enter still plays") {
                input.dispatchEvent(keyEvent(key = "Escape"))
                input.dispatchEvent(ctrlShiftEnter())
            }

            answered shouldContainExactly listOf("play")
        }
    }

    "hints: shown while Ctrl is held alone for the delay, gone on release" {
        withManager(hintDelayMs = 30) { keys, host ->
            host.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", ctrl = true))

            keys.hints() shouldBe false
            delay(80)
            keys.hints() shouldBe true

            withClue("adding Shift keeps them") {
                host.dispatchEvent(keyEvent(key = "Shift", code = "ShiftLeft", ctrl = true, shift = true))
                keys.hints() shouldBe true
            }

            host.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", shift = true, type = "keyup"))
            keys.hints() shouldBe false
        }
    }

    "hints: another key hides them and stops the delay; another modifier does not start it" {
        withManager(hintDelayMs = 30) { keys, host ->
            withClue("Ctrl+Z before the delay: no hints") {
                host.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", ctrl = true))
                host.dispatchEvent(keyEvent(key = "z", code = "KeyZ", ctrl = true))
                delay(80)
                keys.hints() shouldBe false
            }

            withClue("shown, then a key") {
                host.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", ctrl = true, type = "keyup"))
                host.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", ctrl = true))
                delay(80)
                keys.hints() shouldBe true
                host.dispatchEvent(keyEvent(key = "Enter", code = "Enter", ctrl = true))
                keys.hints() shouldBe false
            }

            withClue("Alt alone is not the trigger") {
                host.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", type = "keyup"))
                host.dispatchEvent(keyEvent(key = "Alt", code = "AltLeft", alt = true))
                delay(80)
                keys.hints() shouldBe false
            }
        }
    }

    "a clash: the first defined action with an enabled handler answers" {
        withManager { keys, host ->
            val first = KeyAction(id = "first", title = "First", keys = listOf("F9"))
            val second = KeyAction(id = "second", title = "Second", keys = listOf("F9"))
            keys.define(object : KeyBindings {
                override val actions = listOf(first, second)
            })
            val answered = mutableListOf<String>()
            var firstEnabled = true
            keys.handle(second) { answered.add("second") }
            keys.handle(first, enabled = { firstEnabled }) { answered.add("first") }

            host.dispatchEvent(keyEvent(key = "F9", code = "F9"))
            firstEnabled = false
            host.dispatchEvent(keyEvent(key = "F9", code = "F9"))

            answered shouldContainExactly listOf("first", "second")
        }
    }

    "a bag's failed update leaves the keymap as it was; a removed bag ignores updates" {
        withManager { keys, _ ->
            val registration = keys.define(bag)
            keys.define(object : KeyBindings {
                override val actions = listOf(KeyAction(id = "other", title = "Other"))
            })

            shouldThrow<IllegalArgumentException> {
                registration.update(object : KeyBindings {
                    override val actions = listOf(KeyAction(id = "other", title = "Other again"))
                })
            }
            keys.keymap().actions.map { it.id } shouldContainExactly listOf("play", "stop", "close", "other")

            withClue("after the failure the bag still holds its own actions: the next change builds the keymap from them") {
                keys.setUserKeys(actionId = "play", keys = listOf("F5"))
                keys.keymap().actions.map { it.id } shouldContainExactly listOf("play", "stop", "close", "other")
            }

            withClue("removing it takes them away") {
                registration.remove()
                keys.keymap().actions.map { it.id } shouldContainExactly listOf("other")
            }

            registration.update(bag)
            keys.keymap().actions.map { it.id } shouldContainExactly listOf("other")
        }
    }

    "hints: AltGr never shows them, though Windows sends a Control keydown first" {
        withManager(hintDelayMs = 30) { keys, host ->
            host.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", ctrl = true))
            host.dispatchEvent(keyEvent(key = "AltGraph", code = "AltRight", ctrl = true, alt = true, altGraph = true))
            delay(80)

            keys.hints() shouldBe false
        }
    }

    "hints: AltGr never shows them, also when the trigger is Ctrl+Alt" {
        val host = document.createElement("div") as HTMLElement
        document.body!!.appendChild(host)
        val keys = KeysManager(platform = KeyPlatform.Pc, hintTrigger = "Ctrl-Alt", hintDelayMs = 30, target = host)

        try {
            host.dispatchEvent(keyEvent(key = "AltGraph", code = "AltRight", ctrl = true, alt = true, altGraph = true))
            delay(80)
            keys.hints() shouldBe false

            withClue("a real Ctrl+Alt shows them") {
                host.dispatchEvent(keyEvent(key = "Alt", code = "AltLeft", ctrl = true, alt = true))
                delay(80)
                keys.hints() shouldBe true
            }
        } finally {
            keys.dispose()
            host.remove()
        }
    }

    "hints: Ctrl, then Alt before the delay ends, stops the delay" {
        withManager(hintDelayMs = 30) { keys, host ->
            host.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", ctrl = true))
            host.dispatchEvent(keyEvent(key = "Alt", code = "AltLeft", ctrl = true, alt = true))
            delay(80)

            keys.hints() shouldBe false
        }
    }

    "hints: a click, the wheel and the window losing focus hide them" {
        withManager(hintDelayMs = 10) { keys, host ->
            suspend fun show() {
                host.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", type = "keyup"))
                host.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", ctrl = true))
                delay(40)
                keys.hints() shouldBe true
            }

            show()
            host.dispatchEvent(PointerEvent("pointerdown", PointerEventInit(ctrlKey = true, bubbles = true)))
            withClue("a click") { keys.hints() shouldBe false }

            show()
            host.dispatchEvent(Event("wheel", EventInit(bubbles = true)))
            withClue("the wheel") { keys.hints() shouldBe false }

            show()
            window.dispatchEvent(Event("blur"))
            withClue("the window lost the focus") { keys.hints() shouldBe false }
        }
    }

    "hints: moving the mouse while the delay runs stops it (Ctrl+hover); shown, a move with Ctrl let go hides them" {
        withManager(hintDelayMs = 30) { keys, host ->
            fun move(x: Int, y: Int, ctrl: Boolean = true) {
                host.dispatchEvent(PointerEvent("pointermove", PointerEventInit(clientX = x, clientY = y, ctrlKey = ctrl, bubbles = true)))
            }

            move(100, 100, ctrl = false)
            host.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", ctrl = true))
            move(102, 101)
            delay(80)

            withClue("a hand resting on the mouse moves it a pixel or two: still the hints") { keys.hints() shouldBe true }

            host.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", type = "keyup"))
            host.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", ctrl = true))
            move(130, 100)
            delay(80)

            withClue("Ctrl+hover") { keys.hints() shouldBe false }

            withClue("Windows repeats a held Ctrl: the repeat does not start the delay again") {
                host.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", ctrl = true, repeat = true))
                delay(80)
                keys.hints() shouldBe false
            }

            host.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", type = "keyup"))
            host.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", ctrl = true))
            delay(80)
            keys.hints() shouldBe true

            move(131, 100)
            withClue("still held") { keys.hints() shouldBe true }

            move(131, 100, ctrl = false)
            withClue("let go where no keyup came") { keys.hints() shouldBe false }
        }
    }

    "hints follow the keys before the page's handlers: a handler that stops the keyup cannot leave them showing" {
        withManager(hintDelayMs = 10) { keys, host ->
            val child = document.createElement("div") as HTMLElement
            host.appendChild(child)
            child.addEventListener("keyup", { it.stopPropagation() })

            child.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", ctrl = true))
            delay(40)
            keys.hints() shouldBe true

            child.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", type = "keyup"))
            keys.hints() shouldBe false
        }
    }

    "dispose hides the hints and stops listening" {
        val host = document.createElement("div") as HTMLElement
        document.body!!.appendChild(host)
        val keys = KeysManager(platform = KeyPlatform.Pc, hintDelayMs = 10, target = host)
        keys.define(bag)
        var played = 0
        keys.handle(play) { played++ }

        host.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", ctrl = true))
        delay(40)
        keys.hints() shouldBe true

        keys.dispose()
        keys.hints() shouldBe false
        host.dispatchEvent(ctrlShiftEnter())
        played shouldBe 0
        host.remove()
    }

    "layers: only the top one answers, the page's keys wait under a modal, and come back when it closes" {
        withManager { keys, host ->
            keys.define(bag)
            val answered = mutableListOf<String>()
            keys.handle(close) { answered.add("page") }
            keys.handle(play) { answered.add("page plays") }

            val modal = keys.openLayer()
            keys.handle(close, layer = modal) { answered.add("modal") }

            host.dispatchEvent(keyEvent(key = "Escape"))
            withClue("the page's Play waits too, even with no handler for it in the modal") {
                val event = ctrlShiftEnter()
                host.dispatchEvent(event)
                event.defaultPrevented shouldBe false
            }

            modal.close()
            host.dispatchEvent(keyEvent(key = "Escape"))

            answered shouldContainExactly listOf("modal", "page")
        }
    }

    "layers close in any order: the top open one always wins, a closed one never comes back, the root stays" {
        withManager { keys, host ->
            keys.define(bag)
            val answered = mutableListOf<String>()
            keys.handle(close) { answered.add("root") }
            val first = keys.openLayer()
            keys.handle(close, layer = first) { answered.add("first") }
            val second = keys.openLayer()
            keys.handle(close, layer = second) { answered.add("second") }

            keys.isOnTop(second) shouldBe true

            withClue("the one below closes first: the top one still answers") {
                first.close()
                host.dispatchEvent(keyEvent(key = "Escape"))
            }

            second.close()
            second.close()
            host.dispatchEvent(keyEvent(key = "Escape"))

            keys.isOnTop(keys.rootLayer) shouldBe true
            answered shouldContainExactly listOf("second", "root")
        }
    }

    "the top layer is a stream: hints redraw when a modal opens or closes" {
        withManager { keys, _ ->
            val seen = mutableListOf<KeysManager.KeyLayer>()
            val unsubscribe = keys.topLayer.subscribeToStream { seen.add(it) }

            val modal = keys.openLayer()
            modal.close()
            unsubscribe()

            seen shouldContainExactly listOf(keys.rootLayer, modal, keys.rootLayer)
        }
    }
})
