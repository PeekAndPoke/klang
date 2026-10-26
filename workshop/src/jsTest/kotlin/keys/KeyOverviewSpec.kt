package io.peekandpoke.klang.workshop.keys

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.eventually
import kotlinx.browser.document
import kotlinx.html.div
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
            KeyOverviewState.Entry(title = "Save", keys = listOf(listOf(listOf("Ctrl", "S")))),
            KeyOverviewState.Entry(title = "Go to", keys = listOf(listOf(listOf("Ctrl", "1…9")))),
        )
    }

    "headings cut the rows into sections; rows before the first heading go untitled, a section without rows is left out" {
        val rows = listOf(
            KeyOverviewRow.Single(save),
            KeyOverviewRow.Heading("Empty"),
            KeyOverviewRow.Heading("Jump"),
            KeyOverviewRow.Group(KeyActionGroup(title = "Go to", actions = goTo)),
        )
        val state = overviewOf(rows = rows)

        state.sections.map { it.title to it.entries.map { entry -> entry.title } } shouldBe listOf(
            null to listOf("Save"),
            "Jump" to listOf("Go to"),
        )
        state.entries.map { it.title } shouldBe listOf("Save", "Go to")

        withClue("a section whose rows all lost their keys is left out too") {
            overviewOf(userKeys = mapOf("save" to emptyList()), rows = rows).sections.map { it.title } shouldBe listOf("Jump")
        }
    }

    "a row without any key is left out" {
        overviewOf(userKeys = mapOf("save" to emptyList())).entries.map { it.title } shouldBe listOf("Go to")
    }

    "keys with a gap are listed, a group of one is its key" {
        val odd = KeyActionGroup(title = "Odd", actions = listOf(goTo[0], goTo[2], goTo[4]))
        val one = KeyActionGroup(title = "One", actions = listOf(goTo[0]))

        overviewOf(rows = listOf(KeyOverviewRow.Group(odd), KeyOverviewRow.Group(one))).entries.map { it.keys } shouldBe listOf(
            listOf(listOf(listOf("Ctrl", "1, 3, 5"))),
            listOf(listOf(listOf("Ctrl", "1"))),
        )
    }

    "a single action whose first key is keys in a row shows one group of caps per step" {
        overviewOf(userKeys = mapOf("save" to listOf("Shift Shift", "Mod-S"))).entries[0].keys shouldBe
            listOf(listOf(listOf("⇧"), listOf("⇧")))
    }

    "a key the user changed on its own: the group shows each key as it is, nothing hidden" {
        withClue("another modifier on the same digit: nine ways, the changed one among them") {
            val keys = overviewOf(userKeys = mapOf("go-2" to listOf("Alt-2"))).entries[1].keys

            keys.size shouldBe 9
            keys[1] shouldBe listOf(listOf("Alt", "2"))
        }

        withClue("keys in a row for one of them: each shown as it is, the user's key not hidden") {
            val keys = overviewOf(userKeys = mapOf("go-2" to listOf("Shift Shift"))).entries[1].keys

            keys.size shouldBe 9
            keys[1] shouldBe listOf(listOf("⇧"), listOf("⇧"))
        }

        withClue("a key that is no digit") {
            overviewOf(userKeys = mapOf("go-2" to listOf("F2"))).entries[1].keys[1] shouldBe listOf(listOf("F2"))
        }
    }

    "the overlay shows the rows while the hints show, nothing before" {
        val manager = KeysManager(platform = pc, hintDelayMs = 10).apply { define(bag) }
        val plain: KeyOverviewRenderer = { state ->
            state.entries.forEach { entry ->
                val ways = entry.keys.joinToString { way -> way.joinToString(" ") { it.joinToString("+") } }
                span(classes = "row") { +"${entry.title}: $ways" }
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

    "the plain look draws each heading before its section's rows" {
        val headed = object : KeyBindings {
            override val actions = bag.actions
            override val overview = listOf(
                KeyOverviewRow.Single(save),
                KeyOverviewRow.Heading("Jump"),
                KeyOverviewRow.Group(KeyActionGroup(title = "Go to", actions = goTo)),
            )
        }
        val manager = KeysManager(platform = pc, hintDelayMs = 10).apply { define(headed) }

        try {
            TestBed.preact(appSetup = { keys(manager) }, view = { KeyBindingsOverlay(bindings = headed) }) {
                document.body!!.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", ctrl = true))

                eventually {
                    val text = document.querySelector(".${KeyHints.OVERVIEW_CLASS}")?.textContent.orEmpty()

                    text.indexOf("Save") shouldBeGreaterThanOrEqual 0
                    text.indexOf("Jump") shouldBeGreaterThan text.indexOf("Save")
                    text.indexOf("Go to") shouldBeGreaterThan text.indexOf("Jump")
                }

                document.body!!.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", type = "keyup"))
            }
        } finally {
            manager.dispose()
        }
    }

    "the overlay waits under another layer and inside an inert part of the page" {
        val manager = KeysManager(platform = pc, hintDelayMs = 10).apply { define(bag) }
        val plain: KeyOverviewRenderer = { state -> state.entries.forEach { entry -> span(classes = "row") { +entry.title } } }

        try {
            TestBed.preact(
                appSetup = { keys(manager) },
                view = {
                    div(classes = "awake") { KeyBindingsOverlay(bindings = bag, renderer = plain) }
                    div(classes = "asleep") {
                        // Not "": Preact sets inert as the element's property, and an empty string is false there
                        attributes["inert"] = "inert"
                        KeyBindingsOverlay(bindings = bag, renderer = plain)
                    }
                },
            ) {
                document.body!!.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", ctrl = true))

                eventually {
                    withClue("the awake one shows, the inert one does not") {
                        document.querySelectorAll(".awake .row").length shouldBe 2
                        document.querySelectorAll(".asleep .row").length shouldBe 0
                    }
                }

                val modal = manager.openLayer()

                eventually {
                    withClue("under another layer") { document.querySelectorAll(".awake .row").length shouldBe 0 }
                }

                modal.close()

                eventually {
                    document.querySelectorAll(".awake .row").length shouldBe 2
                }

                document.body!!.dispatchEvent(keyEvent(key = "Control", code = "ControlLeft", type = "keyup"))
            }
        } finally {
            manager.dispose()
        }
    }
})
