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
import org.w3c.dom.events.KeyboardEvent
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

        withClue("the first key is keys in a row: the label names them, as the hint does") {
            withManager { keys, _ ->
                keys.define(bag)
                keys.setUserKeys(actionId = "play", keys = listOf("Shift Shift", "F5"))
                keys.labelOf(play) shouldBe "Shift Shift"
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

            val letter = KeyAction(id = "letter", title = "Letter", keys = listOf("N"))
            keys.define(object : KeyBindings {
                override val actions = listOf(letter)
            })
            keys.handle(letter) { answered.add("letter") }

            withClue("a plain letter in a text field belongs to the field, Ctrl+Shift+Enter still plays") {
                input.dispatchEvent(keyEvent(key = "n", code = "KeyN"))
                input.dispatchEvent(ctrlShiftEnter())
            }

            withClue("an Esc the field uses (it prevents it) stays the field's; an Esc it ignores goes on") {
                val uses: (Event) -> Unit = { it.preventDefault() }
                input.addEventListener("keydown", uses)
                input.dispatchEvent(keyEvent(key = "Escape"))
                input.removeEventListener("keydown", uses)
                input.dispatchEvent(keyEvent(key = "Escape"))
            }

            withClue("an Esc that cancels an input method's composition stays the field's") {
                input.dispatchEvent(keyEvent(key = "Escape", composing = true))
            }

            withClue("so does one Safari reports by its keyCode 229 alone, without isComposing") {
                input.dispatchEvent(keyEvent(key = "Escape", keyCode = 229))
            }

            answered shouldContainExactly listOf("play", "close")
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

    "stacking: of two actions on one key, the newest enabled registration answers; when it goes, the one before" {
        withManager { keys, host ->
            val first = KeyAction(id = "first", title = "First", keys = listOf("F9"))
            val second = KeyAction(id = "second", title = "Second", keys = listOf("F9"))
            keys.define(object : KeyBindings {
                override val actions = listOf(first, second)
            })
            val answered = mutableListOf<String>()
            var secondEnabled = true
            // The first defined action registered last: the order of registration decides, not the order of definition
            keys.handle(second, enabled = { secondEnabled }) { answered.add("second") }
            val newest = keys.handle(first) { answered.add("first") }

            host.dispatchEvent(keyEvent(key = "F9", code = "F9"))
            newest.remove()
            host.dispatchEvent(keyEvent(key = "F9", code = "F9"))
            secondEnabled = false
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

    "a layer made for a component waits off the stack until it opens; closed, it never opens" {
        withManager { keys, _ ->
            val waiting = keys.newLayer()
            keys.isOnTop(keys.rootLayer) shouldBe true

            keys.open(waiting)
            keys.isOnTop(waiting) shouldBe true

            waiting.close()
            keys.open(waiting)

            withClue("closed stays closed") {
                keys.isOnTop(keys.rootLayer) shouldBe true
            }

            withClue("a layer closed before it ever opened never opens either") {
                val never = keys.newLayer()
                never.close()
                keys.open(never)
                keys.isOnTop(keys.rootLayer) shouldBe true
            }
        }
    }

    "keys in a row: Shift tapped twice triggers its action; held too long, too slow, or with a key between, it does not" {
        val search = KeyAction(id = "search", title = "Search", keys = listOf("Mod-K", "Shift Shift"))
        val host = document.createElement("div") as HTMLElement
        document.body!!.appendChild(host)
        val keys = KeysManager(platform = KeyPlatform.Pc, tapMaxMs = 60, tapPairMs = 120, target = host)
        keys.define(object : KeyBindings {
            override val actions = listOf(search)
        })
        val answered = mutableListOf<String>()
        keys.handle(search) { answered.add("search") }

        fun down(shift: Boolean = true) = host.dispatchEvent(keyEvent(key = "Shift", code = "ShiftLeft", shift = shift))
        fun up() = host.dispatchEvent(keyEvent(key = "Shift", code = "ShiftLeft", type = "keyup"))
        fun tap() {
            down()
            up()
        }

        try {
            tap()
            tap()
            withClue("two quick taps") { answered shouldContainExactly listOf("search") }

            withClue("a capital between: Shift down, A, Shift up is no tap") {
                tap()
                down()
                host.dispatchEvent(keyEvent(key = "A", code = "KeyA", shift = true))
                up()
                answered shouldContainExactly listOf("search")
            }

            withClue("held too long") {
                tap()
                down()
                delay(100)
                up()
                answered shouldContainExactly listOf("search")
            }

            withClue("too slow between the taps") {
                tap()
                delay(200)
                tap()
                answered shouldContainExactly listOf("search")
            }

            withClue("Ctrl held with Shift is no tap of Shift") {
                host.dispatchEvent(keyEvent(key = "Shift", code = "ShiftLeft", shift = true, ctrl = true))
                up()
                host.dispatchEvent(keyEvent(key = "Shift", code = "ShiftLeft", shift = true, ctrl = true))
                up()
                answered shouldContainExactly listOf("search")
            }

            withClue("again two quick taps, in a text field too: a tap types nothing") {
                delay(200)
                val input = document.createElement("input") as HTMLElement
                host.appendChild(input)
                input.dispatchEvent(keyEvent(key = "Shift", code = "ShiftLeft", shift = true))
                input.dispatchEvent(keyEvent(key = "Shift", code = "ShiftLeft", type = "keyup"))
                input.dispatchEvent(keyEvent(key = "Shift", code = "ShiftLeft", shift = true))
                input.dispatchEvent(keyEvent(key = "Shift", code = "ShiftLeft", type = "keyup"))
                answered shouldContainExactly listOf("search", "search")
            }

            withClue("under another layer the page's taps wait") {
                delay(200)
                val modal = keys.openLayer()
                tap()
                tap()
                modal.close()
                answered shouldContainExactly listOf("search", "search")
            }

            withClue("a click between down and up is no tap: Shift+click twice extends a selection") {
                delay(200)
                repeat(2) {
                    down()
                    host.dispatchEvent(PointerEvent("pointerdown", PointerEventInit(bubbles = true)))
                    up()
                }
                answered shouldContainExactly listOf("search", "search")
            }

            withClue("a held Shift's repeats (Windows) do not restart the tap: held long, then let go, is no tap") {
                delay(200)
                repeat(2) {
                    down()
                    delay(40)
                    host.dispatchEvent(keyEvent(key = "Shift", code = "ShiftLeft", shift = true, repeat = true))
                    delay(40)
                    host.dispatchEvent(keyEvent(key = "Shift", code = "ShiftLeft", shift = true, repeat = true))
                    up()
                }
                answered shouldContainExactly listOf("search", "search")
            }

            withClue("the window losing the focus between the taps ends the row") {
                delay(200)
                tap()
                window.dispatchEvent(Event("blur"))
                tap()
                answered shouldContainExactly listOf("search", "search")
            }

            withClue("Shift under a held AltGr (Linux: no Ctrl, no Alt) is no tap") {
                delay(200)
                repeat(2) {
                    host.dispatchEvent(keyEvent(key = "Shift", code = "ShiftLeft", shift = true, altGraph = true))
                    up()
                }
                answered shouldContainExactly listOf("search", "search")
            }

            withClue("only the last two taps count: a Ctrl brushed first does not spoil Shift Shift") {
                delay(200)
                host.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", ctrl = true))
                host.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", type = "keyup"))
                tap()
                tap()
                answered shouldContainExactly listOf("search", "search", "search")
            }

            withClue("another modifier coming up (its keydown lost, as when the focus came back) is no tap") {
                delay(200)
                down()
                host.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", type = "keyup"))
                tap()
                answered shouldContainExactly listOf("search", "search", "search")
            }

            withClue("Shift taken by a composing input method (it switches its mode) is no tap, also by keyCode 229 alone") {
                delay(200)
                repeat(2) {
                    host.dispatchEvent(keyEvent(key = "Shift", code = "ShiftLeft", shift = true, composing = true))
                    up()
                }
                delay(200)
                repeat(2) {
                    host.dispatchEvent(keyEvent(key = "Shift", code = "ShiftLeft", shift = true, keyCode = 229))
                    up()
                }
                answered shouldContainExactly listOf("search", "search", "search")
            }
        } finally {
            keys.dispose()
            host.remove()
        }
    }

    "chords in a row: the first chord waits for the next; Esc, a key that fits nothing, a click or the time end the wait" {
        val save = KeyAction(id = "save", title = "Save", keys = listOf("Mod-K Mod-S"))
        val saveAll = KeyAction(id = "save-all", title = "Save all", keys = listOf("Mod-K Mod-A"))
        val close = KeyAction(id = "close", title = "Close", keys = listOf("Escape"))
        val host = document.createElement("div") as HTMLElement
        val inner = document.createElement("div") as HTMLElement
        host.appendChild(inner)
        document.body!!.appendChild(host)
        val keys = KeysManager(platform = KeyPlatform.Pc, hintDelayMs = 30, sequenceWaitMs = 100, target = host)
        keys.define(object : KeyBindings {
            override val actions = listOf(save, saveAll, close)
        })
        val answered = mutableListOf<String>()
        keys.handle(save) { answered.add("save") }
        keys.handle(saveAll) { answered.add("save-all") }
        keys.handle(close) { answered.add("close") }
        val pageSaw = mutableListOf<String>()
        inner.addEventListener("keydown", { event -> pageSaw.add((event as KeyboardEvent).key) })

        val ctrlK = KeyChord(key = "k", ctrl = true)

        fun press(key: String, ctrl: Boolean = true) =
            inner.dispatchEvent(keyEvent(key = key, code = "Key${key.uppercase()}", ctrl = ctrl))

        try {
            press("k")
            withClue("waiting after the first chord") { keys.waiting() shouldContainExactly listOf(ctrlK) }
            press("s")
            withClue("the second chord triggers it, and the page never sees it") {
                answered shouldContainExactly listOf("save")
                pageSaw shouldContainExactly listOf("k")
                keys.waiting() shouldContainExactly emptyList()
            }

            press("k")
            press("a")
            withClue("the same first chord, another second: the other sequence") {
                answered shouldContainExactly listOf("save", "save-all")
            }

            withClue("a held chord's repeat does not end the wait") {
                press("k")
                inner.dispatchEvent(keyEvent(key = "k", code = "KeyK", ctrl = true, repeat = true))
                press("s")
                answered shouldContainExactly listOf("save", "save-all", "save")
            }

            withClue("Esc ends the wait and does nothing else") {
                press("k")
                inner.dispatchEvent(keyEvent(key = "Escape", code = "Escape"))
                keys.waiting() shouldContainExactly emptyList()
                answered shouldContainExactly listOf("save", "save-all", "save")
            }

            withClue("a key that fits no sequence ends the wait, and is swallowed") {
                press("k")
                val other = keyEvent(key = "x", code = "KeyX")
                inner.dispatchEvent(other)
                other.defaultPrevented shouldBe true
                keys.waiting() shouldContainExactly emptyList()
                press("s")
                answered shouldContainExactly listOf("save", "save-all", "save")
            }

            withClue("a key an input method composes with (keyCode 229) ends the wait and goes on to the field") {
                press("k")
                val composed = keyEvent(key = "Process", code = "KeyA", keyCode = 229)
                inner.dispatchEvent(composed)
                composed.defaultPrevented shouldBe false
                pageSaw.last() shouldBe "Process"
                keys.waiting() shouldContainExactly emptyList()
                press("s")
                answered shouldContainExactly listOf("save", "save-all", "save")
            }

            withClue("a click ends the wait") {
                press("k")
                inner.dispatchEvent(PointerEvent("pointerdown", PointerEventInit(bubbles = true)))
                press("s")
                answered shouldContainExactly listOf("save", "save-all", "save")
            }

            withClue("the time running out ends the wait") {
                press("k")
                delay(150)
                keys.waiting() shouldContainExactly emptyList()
                press("s")
                answered shouldContainExactly listOf("save", "save-all", "save")
            }

            withClue("in a text field: the first chord has Ctrl, so it starts, and the next is the wait's") {
                val input = document.createElement("input") as HTMLElement
                inner.appendChild(input)
                input.dispatchEvent(keyEvent(key = "k", code = "KeyK", ctrl = true))
                input.dispatchEvent(keyEvent(key = "s", code = "KeyS", ctrl = true))
                answered shouldContainExactly listOf("save", "save-all", "save", "save")
            }

            withClue("under another layer the first chord waits for nothing and stays the page's") {
                val modal = keys.openLayer()
                press("k")
                keys.waiting() shouldContainExactly emptyList()
                modal.close()
            }

            withClue("a layer that opens while waiting ends the wait: the keys below no longer answer") {
                press("k")
                val modal = keys.openLayer()
                keys.waiting() shouldContainExactly emptyList()
                modal.close()
            }

            withClue("the window's blur ends the wait") {
                press("k")
                window.dispatchEvent(Event("blur"))
                keys.waiting() shouldContainExactly emptyList()
            }

            withClue("the second chord hides the hints: Ctrl held again alone between the chords shows none after") {
                press("k")
                inner.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", ctrl = true))
                press("s")
                delay(60)
                keys.hints() shouldBe false
                inner.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", type = "keyup"))
            }

            withClue("a single key on the first chord: the newest registration decides whether it acts or waits") {
                val count = answered.size
                val open = KeyAction(id = "open", title = "Open", keys = listOf("Mod-K"))
                val bag = keys.define(object : KeyBindings {
                    override val actions = listOf(open)
                })
                val openKey = keys.handle(open) { answered.add("open") }

                press("k")
                keys.waiting() shouldContainExactly emptyList()
                openKey.remove()
                press("k")
                keys.waiting() shouldContainExactly listOf(ctrlK)
                press("s")
                answered.drop(count) shouldContainExactly listOf("open", "save")
                bag.remove()
            }

            withClue("two actions on the same chords in a row: the newest registration answers") {
                val count = answered.size
                val again = KeyAction(id = "save-again", title = "Save again", keys = listOf("Mod-K Mod-S"))
                val bag = keys.define(object : KeyBindings {
                    override val actions = listOf(again)
                })
                val againKey = keys.handle(again) { answered.add("save-again") }

                press("k")
                press("s")
                answered.drop(count) shouldContainExactly listOf("save-again")
                againKey.remove()
                bag.remove()
            }
        } finally {
            keys.dispose()
            host.remove()
        }
    }

    "priority: the highest answers whatever mounted first; within one priority the newest; a removal hands back" {
        withManager { keys, host ->
            val front = KeyPriority(value = 10, name = "front")
            val page = KeyAction(id = "page", title = "Page", keys = listOf("F9"))
            val panel = KeyAction(id = "panel", title = "Panel", keys = listOf("F9"), priority = front)
            keys.define(object : KeyBindings {
                override val actions = listOf(page, panel)
            })
            val answered = mutableListOf<String>()

            // The panel first, the page after it: the page is newer, the panel stands higher
            val olderPanel = keys.handle(panel) { answered.add("panel 1") }
            val newerPanel = keys.handle(panel) { answered.add("panel 2") }
            keys.handle(page) { answered.add("page") }

            host.dispatchEvent(keyEvent(key = "F9", code = "F9"))
            newerPanel.remove()
            host.dispatchEvent(keyEvent(key = "F9", code = "F9"))
            olderPanel.remove()
            host.dispatchEvent(keyEvent(key = "F9", code = "F9"))

            answered shouldContainExactly listOf("panel 2", "panel 1", "page")
        }
    }

    "priority stays inside a layer: a higher priority below waits for a lower one on top" {
        withManager { keys, host ->
            val front = KeyPriority(value = 10, name = "front")
            val page = KeyAction(id = "page", title = "Page", keys = listOf("F9"), priority = front)
            val modal = KeyAction(id = "modal", title = "Modal", keys = listOf("F9"))
            keys.define(object : KeyBindings {
                override val actions = listOf(page, modal)
            })
            val answered = mutableListOf<String>()
            keys.handle(page) { answered.add("page") }
            val layer = keys.openLayer()
            keys.handle(modal, layer = layer) { answered.add("modal") }

            host.dispatchEvent(keyEvent(key = "F9", code = "F9"))
            layer.close()
            host.dispatchEvent(keyEvent(key = "F9", code = "F9"))

            answered shouldContainExactly listOf("modal", "page")
        }
    }

    "chords in a row: a tap pair that acts ends the wait; the waited key reaches no other listener on the target" {
        val save = KeyAction(id = "save", title = "Save", keys = listOf("Mod-K Mod-S"))
        val search = KeyAction(id = "search", title = "Search", keys = listOf("Shift Shift"))
        val host = document.createElement("div") as HTMLElement
        document.body!!.appendChild(host)
        val keys = KeysManager(platform = KeyPlatform.Pc, tapMaxMs = 60, tapPairMs = 120, target = host)
        keys.define(object : KeyBindings {
            override val actions = listOf(save, search)
        })
        val answered = mutableListOf<String>()
        keys.handle(save) { answered.add("save") }
        keys.handle(search) { answered.add("search") }
        // A capture listener on the same target, added after the manager's
        val seen = mutableListOf<String>()
        host.addEventListener("keydown", { event -> seen.add((event as KeyboardEvent).key) }, true)

        fun tapShift() {
            host.dispatchEvent(keyEvent(key = "Shift", code = "ShiftLeft", shift = true))
            host.dispatchEvent(keyEvent(key = "Shift", code = "ShiftLeft", type = "keyup"))
        }

        try {
            host.dispatchEvent(keyEvent(key = "k", code = "KeyK", ctrl = true))
            host.dispatchEvent(keyEvent(key = "s", code = "KeyS", ctrl = true))
            withClue("the second chord stops at the manager") {
                answered shouldContainExactly listOf("save")
                seen.filter { it == "s" } shouldContainExactly emptyList()
            }

            host.dispatchEvent(keyEvent(key = "k", code = "KeyK", ctrl = true))
            tapShift()
            tapShift()
            withClue("the tap pair acts and the wait is over") {
                answered shouldContainExactly listOf("save", "search")
                keys.waiting() shouldContainExactly emptyList()
            }
        } finally {
            keys.dispose()
            host.remove()
        }
    }
})
