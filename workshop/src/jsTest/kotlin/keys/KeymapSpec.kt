package io.peekandpoke.klang.workshop.keys

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/**
 * A keymap: the defaults per platform, the user's keys over them, and what is wrong with it.
 */
class KeymapSpec : StringSpec({

    val pc = KeyPlatform.Pc
    val mac = KeyPlatform.Mac

    val play = KeyAction(id = "play", title = "Play", keys = listOf("Mod-Shift-Enter"))
    val stop = KeyAction(id = "stop", title = "Stop", keys = listOf("Mod-Shift-."))
    val select = KeyAction(id = "select-line", title = "Select line", keys = listOf("Alt-L"), macKeys = listOf("Ctrl-L"))

    "the defaults, the Mac's where it has its own" {
        Keymap(platform = pc, actions = listOf(select)).chordsOf("select-line") shouldContainExactly
            listOf(KeyChord(key = "l", alt = true))
        Keymap(platform = mac, actions = listOf(select)).chordsOf("select-line") shouldContainExactly
            listOf(KeyChord(key = "l", ctrl = true))
    }

    "the user's keys replace the defaults; an empty list takes every key off" {
        val keymap = Keymap(
            platform = pc,
            actions = listOf(play, stop),
            userKeys = mapOf("play" to listOf("F5", "Mod-Enter"), "stop" to emptyList()),
        )

        keymap.chordsOf("play") shouldContainExactly listOf(KeyChord(key = "F5"), KeyChord(key = "Enter", ctrl = true))
        keymap.chordsOf("stop").shouldBeEmpty()

        withClue("an action nobody defined has no keys, its stored keys are kept") {
            Keymap(platform = pc, actions = listOf(play), userKeys = mapOf("gone" to listOf("F1"))).let {
                it.chordsOf("gone").shouldBeEmpty()
                it.userKeys.keys shouldContainExactly setOf("gone")
            }
        }
    }

    "the actions a key event triggers" {
        val keymap = Keymap(platform = pc, actions = listOf(play, stop))

        keymap.actionsFor(keyEvent(key = "Enter", ctrl = true, shift = true)) shouldContainExactly listOf(play)
        keymap.actionsFor(keyEvent(key = "Enter", ctrl = true)).shouldBeEmpty()
        keymap.matches("stop", keyEvent(key = ":", code = "Period", ctrl = true, shift = true)) shouldBe true
    }

    "a sound keymap has no problems" {
        Keymap(platform = pc, actions = listOf(play, stop, select)).problems.shouldBeEmpty()
    }

    "two actions on one key in one scope clash, both keep the key; another scope does not clash" {
        val alsoPlay = KeyAction(id = "also-play", title = "Also play", keys = listOf("Mod-Shift-Enter"))
        val keymap = Keymap(platform = pc, actions = listOf(play, alsoPlay))

        keymap.problems shouldContainExactly listOf(
            Keymap.Problem.Clash(
                scope = KeyAction.APP_SCOPE,
                chord = KeyChord(key = "Enter", ctrl = true, shift = true),
                actionIds = listOf("play", "also-play"),
            )
        )
        keymap.actionsFor(keyEvent(key = "Enter", ctrl = true, shift = true)) shouldContainExactly listOf(play, alsoPlay)

        withClue("another scope") {
            Keymap(platform = pc, actions = listOf(play, alsoPlay.copy(scope = "editor"))).problems.shouldBeEmpty()
        }

        withClue("a clash the user made") {
            Keymap(platform = pc, actions = listOf(play, stop), userKeys = mapOf("stop" to listOf("Mod-Shift-Enter")))
                .problems.size shouldBe 1
        }
    }

    "one key given twice to one action is no clash with itself" {
        Keymap(platform = pc, actions = listOf(play.copy(keys = listOf("F5", "F5")))).let {
            it.chordsOf("play") shouldContainExactly listOf(KeyChord(key = "F5"))
            it.problems.shouldBeEmpty()
        }
    }

    "a key the browser keeps is a problem, also with Shift, with Cmd on a Mac" {
        val newTab = KeyAction(id = "new", title = "New", keys = listOf("Mod-T"))

        Keymap(platform = pc, actions = listOf(newTab)).problems shouldContainExactly listOf(
            Keymap.Problem.Reserved(actionId = "new", chord = KeyChord(key = "t", ctrl = true))
        )
        Keymap(platform = pc, actions = listOf(newTab.copy(keys = listOf("Mod-Shift-W")))).problems.size shouldBe 1
        Keymap(platform = mac, actions = listOf(newTab)).problems shouldContainExactly listOf(
            Keymap.Problem.Reserved(actionId = "new", chord = KeyChord(key = "t", meta = true))
        )

        withClue("the Mac keeps Cmd+Q from every app; on a PC Ctrl+Q is free") {
            val quit = KeyAction(id = "quit", title = "Quit", keys = listOf("Mod-Q"))

            Keymap(platform = mac, actions = listOf(quit)).problems.size shouldBe 1
            Keymap(platform = pc, actions = listOf(quit)).problems.shouldBeEmpty()
        }
    }

    "a key no press can match is a problem: with Shift, a symbol is written as the US key there" {
        val keymap = Keymap(platform = pc, actions = listOf(play.copy(keys = listOf("Mod-Shift-?", "Mod-Shift-/"))))

        keymap.problems shouldContainExactly listOf(
            Keymap.Problem.Unreachable(actionId = "play", chord = KeyChord(key = "?", ctrl = true, shift = true))
        )
    }

    "an Option chord on a key whose US position is unknown is unreachable on a Mac, not on a PC" {
        val plus = play.copy(keys = listOf("Alt-+"))

        Keymap(platform = mac, actions = listOf(plus)).problems shouldContainExactly listOf(
            Keymap.Problem.Unreachable(actionId = "play", chord = KeyChord(key = "+", alt = true))
        )
        Keymap(platform = pc, actions = listOf(plus)).problems.shouldBeEmpty()
    }

    "an unreadable key is a problem, and the action keeps its other keys" {
        val keymap = Keymap(platform = pc, actions = listOf(play.copy(keys = listOf("Mod-Banana", "F5"))))

        keymap.chordsOf("play") shouldContainExactly listOf(KeyChord(key = "F5"))
        keymap.problems.size shouldBe 1
        (keymap.problems.single() as Keymap.Problem.Unreadable).text shouldBe "Mod-Banana"
    }
})
