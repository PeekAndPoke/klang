package io.peekandpoke.klang.workshop.keys

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.eventually
import kotlinx.browser.document
import kotlinx.html.span

private val save = KeyAction(id = "save", title = "Save", keys = listOf("Mod-S"))
private val goTo = (1..9).map { KeyAction(id = "go-$it", title = "Go to $it", keys = listOf("Ctrl-$it")) }

private val bag = object : KeyBindings {
    override val actions = listOf(save) + goTo
    override val overview = listOf(
        KeyOverviewRow.Single(save),
        KeyOverviewRow.Group(KeyActionGroup(title = "Go to", actions = goTo)),
    )
}

/**
 * An overview of a bag's keys: one row per action or group, a group's keys joined where they share their modifiers,
 * and the overlay in a page while the hints show.
 */
class KeyOverviewSpec : StringSpec({

    val pc = KeyPlatform.Pc

    fun overviewOf(userKeys: Map<String, List<String>> = emptyMap(), rows: List<KeyOverviewRow> = bag.overview) =
        KeyOverviewState.of(title = "Keys", rows = rows, keymap = Keymap(platform = pc, actions = bag.actions, userKeys = userKeys))

    "a single action shows its first key, a group its shared modifiers and the run of its keys" {
        overviewOf().entries shouldBe listOf(
            KeyOverviewState.Entry(title = "Save", keys = listOf(listOf("Ctrl", "S"))),
            KeyOverviewState.Entry(title = "Go to", keys = listOf(listOf("Ctrl", "1…9"))),
        )
    }

    "a row without any key is left out" {
        overviewOf(userKeys = mapOf("save" to emptyList())).entries.map { it.title } shouldBe listOf("Go to")
    }

    "keys with a gap are listed, a group of one is its key" {
        val odd = KeyActionGroup(title = "Odd", actions = listOf(goTo[0], goTo[2], goTo[4]))
        val one = KeyActionGroup(title = "One", actions = listOf(goTo[0]))

        overviewOf(rows = listOf(KeyOverviewRow.Group(odd), KeyOverviewRow.Group(one))).entries.map { it.keys } shouldBe listOf(
            listOf(listOf("Ctrl", "1, 3, 5")),
            listOf(listOf("Ctrl", "1")),
        )
    }

    "a key the user changed on its own: the group shows each key as it is, nothing hidden" {
        withClue("another modifier on the same digit: nine ways, the changed one among them") {
            val keys = overviewOf(userKeys = mapOf("go-2" to listOf("Alt-2"))).entries[1].keys

            keys.size shouldBe 9
            keys[1] shouldBe listOf("Alt", "2")
        }

        withClue("a key that is no digit") {
            overviewOf(userKeys = mapOf("go-2" to listOf("F2"))).entries[1].keys[1] shouldBe listOf("F2")
        }
    }

    "the overlay shows the rows while the hints show, nothing before" {
        val manager = KeysManager(platform = pc, hintDelayMs = 10).apply { define(bag) }
        val plain: KeyOverviewRenderer = { state ->
            state.entries.forEach { entry ->
                span(classes = "row") { +"${entry.title}: ${entry.keys.joinToString { it.joinToString("+") }}" }
            }
        }

        try {
            TestBed.preact(appSetup = { keys(manager) }, view = { KeyBindingsOverlay(bindings = bag, renderer = plain) }) {
                document.querySelectorAll(".row").length shouldBe 0

                document.body!!.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", ctrl = true))

                eventually {
                    val found = document.querySelectorAll(".row")
                    val rows = (0 until found.length).map { found.item(it)!!.textContent }
                    rows shouldBe listOf("Save: Ctrl+S", "Go to: Ctrl+1…9")
                }

                document.body!!.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", type = "keyup"))

                eventually {
                    document.querySelectorAll(".row").length shouldBe 0
                }
            }
        } finally {
            manager.dispose()
        }
    }
})
