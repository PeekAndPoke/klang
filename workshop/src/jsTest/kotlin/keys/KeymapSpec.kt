package io.peekandpoke.klang.workshop.keys

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

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

    "keys in a row: read from text with spaces, shown as one group per step, chord sequences not yet" {
        val search = play.copy(id = "search", keys = listOf("Mod-K", "Shift Shift"))
        val keymap = Keymap(platform = pc, actions = listOf(search))

        keymap.bindingsOf("search") shouldContainExactly listOf(
            KeyChord(key = "k", ctrl = true),
            KeySequence.ofTaps(TapModifier.Shift, TapModifier.Shift),
        )
        keymap.chordsOf("search") shouldContainExactly listOf(KeyChord(key = "k", ctrl = true))
        keymap.problems.shouldBeEmpty()

        withClue("the groups of key caps") {
            KeySequence.parse("Shift Shift", pc).keyGroups(pc, compact = true) shouldBe listOf(listOf("⇧"), listOf("⇧"))
            KeySequence.parse("Shift Shift", pc).label(pc) shouldBe "Shift Shift"
            KeySequence.parse("Mod Mod", mac).keyGroups(mac) shouldBe listOf(listOf("⌘"), listOf("⌘"))
        }

        withClue("three taps are unreadable: only the last two taps count, so they could never fire") {
            Keymap(platform = pc, actions = listOf(play.copy(keys = listOf("Shift Shift Shift")))).problems.single()
                .shouldBeInstanceOf<Keymap.Problem.Unreadable>()
        }

        withClue("taps mixed with chords are unreadable") {
            Keymap(platform = pc, actions = listOf(play.copy(keys = listOf("Shift Mod-K")))).problems.single()
                .shouldBeInstanceOf<Keymap.Problem.Unreadable>()
        }

        withClue("two actions on the same keys in a row clash") {
            val other = play.copy(id = "other", keys = listOf("Shift Shift"))
            Keymap(platform = pc, actions = listOf(search, other)).problems shouldContainExactly listOf(
                Keymap.Problem.SequenceClash(
                    scope = KeyAction.APP_SCOPE,
                    sequence = KeySequence.ofTaps(TapModifier.Shift, TapModifier.Shift),
                    actionIds = listOf("search", "other"),
                )
            )
        }
    }

    "chords in a row: read, shown one group per chord, checked like single keys, and a prefix clash reported" {
        val save = play.copy(id = "save", keys = listOf("Mod-K Mod-S"))
        val ctrlK = KeyChord(key = "k", ctrl = true)
        val ctrlS = KeyChord(key = "s", ctrl = true)
        val ctrlA = KeyChord(key = "a", ctrl = true)

        Keymap(platform = pc, actions = listOf(save)).let { keymap ->
            keymap.bindingsOf("save") shouldContainExactly listOf(KeySequence.ofChords(ctrlK, ctrlS))
            keymap.problems.shouldBeEmpty()
            keymap.chordSequences.map { (action, sequence) -> action.id to sequence } shouldContainExactly
                listOf("save" to KeySequence.ofChords(ctrlK, ctrlS))
        }

        withClue("the groups and the label, on a PC and on a Mac") {
            KeySequence.parse("Mod-K Mod-S", pc).keyGroups(pc) shouldBe listOf(listOf("Ctrl", "K"), listOf("Ctrl", "S"))
            KeySequence.parse("Mod-K Mod-S", pc).label(pc) shouldBe "Ctrl+K Ctrl+S"
            KeySequence.parse("Mod-K Mod-S", mac).label(mac) shouldBe "⌘K ⌘S"
        }

        withClue("a step the browser keeps is reserved, as a single key would be") {
            Keymap(platform = pc, actions = listOf(save.copy(keys = listOf("Mod-K Mod-W")))).problems shouldContainExactly
                listOf(Keymap.Problem.Reserved(actionId = "save", chord = KeyChord(key = "w", ctrl = true)))
        }

        withClue("a single key that begins a sequence, and a sequence that begins a longer one, clash as prefixes") {
            val open = play.copy(id = "open", keys = listOf("Mod-K"))
            val saveAll = play.copy(id = "save-all", keys = listOf("Mod-K Mod-S Mod-A"))

            Keymap(platform = pc, actions = listOf(open, save, saveAll)).problems shouldContainExactly listOf(
                Keymap.Problem.PrefixClash(
                    scope = KeyAction.APP_SCOPE,
                    prefix = ctrlK,
                    sequence = KeySequence.ofChords(ctrlK, ctrlS),
                    actionIds = listOf("open", "save"),
                ),
                Keymap.Problem.PrefixClash(
                    scope = KeyAction.APP_SCOPE,
                    prefix = ctrlK,
                    sequence = KeySequence.ofChords(ctrlK, ctrlS, ctrlA),
                    actionIds = listOf("open", "save-all"),
                ),
                Keymap.Problem.PrefixClash(
                    scope = KeyAction.APP_SCOPE,
                    prefix = KeySequence.ofChords(ctrlK, ctrlS),
                    sequence = KeySequence.ofChords(ctrlK, ctrlS, ctrlA),
                    actionIds = listOf("save", "save-all"),
                ),
            )
        }

        withClue("in different scopes no prefix clash") {
            val editorOpen = play.copy(id = "open", keys = listOf("Mod-K"), scope = "editor")

            Keymap(platform = pc, actions = listOf(editorOpen, save)).problems.shouldBeEmpty()
        }
    }
})
