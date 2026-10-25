package io.peekandpoke.klang.workshop.keys

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * A key chord read from its text, matched against the key the user pressed on different keyboards, and shown as the
 * user reads it.
 */
class KeyChordSpec : StringSpec({

    val pc = KeyPlatform.Pc
    val mac = KeyPlatform.Mac

    "Mod is Ctrl on a PC and Cmd on a Mac" {
        KeyChord.parse("Mod-Shift-Enter", pc) shouldBe KeyChord(key = "Enter", ctrl = true, shift = true)
        KeyChord.parse("Mod-Shift-Enter", mac) shouldBe KeyChord(key = "Enter", meta = true, shift = true)
    }

    "the text: other names, a letter in either case, the minus key, function keys" {
        withClue("aliases") {
            KeyChord.parse("Esc", pc).key shouldBe "Escape"
            KeyChord.parse("Ctrl-Up", pc) shouldBe KeyChord(key = "ArrowUp", ctrl = true)
            KeyChord.parse("Option-Cmd-L", mac) shouldBe KeyChord(key = "l", alt = true, meta = true)
        }

        withClue("a capital letter is the same key") {
            KeyChord.parse("Mod-Z", pc) shouldBe KeyChord.parse("Mod-z", pc)
        }

        withClue("the last dash of \"Mod--\" is the minus key") {
            KeyChord.parse("Mod--", pc) shouldBe KeyChord(key = "-", ctrl = true)
        }

        withClue("function keys") {
            KeyChord.parse("Shift-f3", pc) shouldBe KeyChord(key = "F3", shift = true)
        }
    }

    "unreadable text is refused" {
        shouldThrow<IllegalArgumentException> { KeyChord.parse("Hyper-A", pc) }
        shouldThrow<IllegalArgumentException> { KeyChord.parse("Mod-Banana", pc) }
        shouldThrow<IllegalArgumentException> { KeyChord.parse("", pc) }
    }

    "modifiers match exactly" {
        val undo = KeyChord.parse("Mod-Z", pc)

        undo.matches(event = keyEvent(key = "z", code = "KeyZ", ctrl = true), platform = pc) shouldBe true

        withClue("Ctrl+Shift+Z is not Ctrl+Z") {
            undo.matches(event = keyEvent(key = "Z", code = "KeyZ", ctrl = true, shift = true), platform = pc) shouldBe false
        }

        withClue("Z alone is not Ctrl+Z") {
            undo.matches(event = keyEvent(key = "z", code = "KeyZ"), platform = pc) shouldBe false
        }
    }

    "letters by the typed character: Ctrl+Z on a French keyboard is where the W is on a US one" {
        val undo = KeyChord.parse("Mod-Z", pc)

        undo.matches(event = keyEvent(key = "z", code = "KeyW", ctrl = true), platform = pc) shouldBe true
        undo.matches(event = keyEvent(key = "w", code = "KeyZ", ctrl = true), platform = pc) shouldBe false
    }

    "letters by the position for a letter of another alphabet" {
        withClue("a Cyrillic layout") {
            KeyChord.parse("Mod-Z", pc).matches(event = keyEvent(key = "я", code = "KeyZ", ctrl = true), platform = pc) shouldBe true
        }
    }

    "chords with Alt (Option) go by the key's position, as Option types signs" {
        withClue("Option+J types ∆ on a Mac: the Alt-J chord still fires") {
            KeyChord.parse("Alt-J", mac).matches(event = keyEvent(key = "∆", code = "KeyJ", alt = true), platform = mac) shouldBe true
        }

        withClue("Cmd+Option+L types ¬: the Mac draft's reformat key fires") {
            KeyChord.parse("Cmd-Option-L", mac)
                .matches(event = keyEvent(key = "¬", code = "KeyL", alt = true, meta = true), platform = mac) shouldBe true
        }

        withClue("Cmd+Option+/ types ÷: the Mac draft's block comment fires") {
            KeyChord.parse("Cmd-Option-/", mac)
                .matches(event = keyEvent(key = "÷", code = "Slash", alt = true, meta = true), platform = mac) shouldBe true
        }

        withClue("a German Mac: Option+L types the ASCII sign @, Cmd+Option+L still reformats") {
            KeyChord.parse("Cmd-Option-L", mac)
                .matches(event = keyEvent(key = "@", code = "KeyL", alt = true, meta = true), platform = mac) shouldBe true
        }

        withClue("a browser on a Mac that reports Option as AltGraph: still the Mac's Option rule, the chord fires") {
            val optionL = keyEvent(key = "¬", code = "KeyL", alt = true, meta = true, altGraph = true)

            KeyChord.parse("Cmd-Option-L", mac).matches(event = optionL, platform = mac) shouldBe true
        }

        withClue("a US Mac: Option+E is a dead key, the Alt-E chord still fires") {
            KeyChord.parse("Alt-E", mac).matches(event = keyEvent(key = "Dead", code = "KeyE", alt = true), platform = mac) shouldBe true
        }

        withClue("a German PC: Alt+ö sits where the US semicolon is, it is no Alt-; chord") {
            KeyChord.parse("Alt-;", pc).matches(event = keyEvent(key = "ö", code = "Semicolon", alt = true), platform = pc) shouldBe false
        }

        withClue("another key's position does not") {
            KeyChord.parse("Alt-J", mac).matches(event = keyEvent(key = "˚", code = "KeyK", alt = true), platform = mac) shouldBe false
        }

        withClue("Alt on a PC types plain letters: on AZERTY, Alt+A is the A key, where the US keyboard has its Q") {
            val altA = keyEvent(key = "a", code = "KeyQ", alt = true)

            KeyChord.parse("Alt-A", pc).matches(event = altA, platform = pc) shouldBe true
            KeyChord.parse("Alt-Q", pc).matches(event = altA, platform = pc) shouldBe false
        }
    }

    "AltGr types characters: no chord matches while it is held, Windows reports it as Ctrl+Alt" {
        withClue("German AltGr+Q types @") {
            KeyChord.parse("Ctrl-Alt-Q", pc)
                .matches(event = keyEvent(key = "@", code = "KeyQ", ctrl = true, alt = true, altGraph = true), platform = pc) shouldBe false
        }

        withClue("German AltGr+7 types {") {
            KeyChord.parse("Ctrl-Alt-7", pc)
                .matches(
                    event = keyEvent(key = "{", code = "Digit7", ctrl = true, alt = true, altGraph = true),
                    platform = pc,
                ) shouldBe false
        }

        withClue("Polish AltGr+L types ł, which must not reformat") {
            KeyChord.parse("Ctrl-Alt-L", pc)
                .matches(event = keyEvent(key = "ł", code = "KeyL", ctrl = true, alt = true, altGraph = true), platform = pc) shouldBe false
        }

        withClue("a real Ctrl+Alt+L") {
            KeyChord.parse("Ctrl-Alt-L", pc)
                .matches(event = keyEvent(key = "l", code = "KeyL", ctrl = true, alt = true), platform = pc) shouldBe true
        }

        withClue("a real Ctrl+Alt+L that Windows reports as AltGr: the typed l still counts") {
            KeyChord.parse("Ctrl-Alt-L", pc)
                .matches(event = keyEvent(key = "l", code = "KeyL", ctrl = true, alt = true, altGraph = true), platform = pc) shouldBe true
            KeyChord.parse("Ctrl-Alt-Up", pc)
                .matches(
                    event = keyEvent(key = "ArrowUp", code = "ArrowUp", ctrl = true, alt = true, altGraph = true),
                    platform = pc,
                ) shouldBe true
        }
    }

    "digits by the position, also when Shift turns them into another character, and on the keypad" {
        val bookmark7 = KeyChord.parse("Mod-Shift-7", pc)

        withClue("a German keyboard: Shift+7 types a slash") {
            bookmark7.matches(event = keyEvent(key = "/", code = "Digit7", ctrl = true, shift = true), platform = pc) shouldBe true
        }

        withClue("a US keyboard: Shift+7 types an ampersand") {
            bookmark7.matches(event = keyEvent(key = "&", code = "Digit7", ctrl = true, shift = true), platform = pc) shouldBe true
        }

        withClue("the keypad") {
            KeyChord.parse("Mod-7", pc).matches(event = keyEvent(key = "7", code = "Numpad7", ctrl = true), platform = pc) shouldBe true
        }

        withClue("the keypad with NumLock off: its 7 is Home") {
            KeyChord.parse("Mod-7", pc).matches(event = keyEvent(key = "Home", code = "Numpad7", ctrl = true), platform = pc) shouldBe false
        }

        withClue("another digit's key") {
            bookmark7.matches(event = keyEvent(key = "(", code = "Digit8", ctrl = true, shift = true), platform = pc) shouldBe false
        }
    }

    "symbols by one rule each, so one key never triggers two chords" {
        val stop = KeyChord.parse("Mod-Shift-.", pc)
        val blockComment = KeyChord.parse("Mod-Shift-/", pc)

        withClue("with Shift by the US position: a German keyboard's Shift+. types a colon") {
            stop.matches(event = keyEvent(key = ":", code = "Period", ctrl = true, shift = true), platform = pc) shouldBe true
        }

        withClue("German Ctrl+Shift+7 types a slash: it is bookmark 7, never the block comment") {
            val germanSlash = keyEvent(key = "/", code = "Digit7", ctrl = true, shift = true)

            blockComment.matches(event = germanSlash, platform = pc) shouldBe false
            KeyChord.parse("Mod-Shift-7", pc).matches(event = germanSlash, platform = pc) shouldBe true
        }

        withClue("AZERTY: the key at the US period types / with Shift; Stop is that key, the slash chord is not") {
            val azerty = keyEvent(key = "/", code = "Period", ctrl = true, shift = true)

            stop.matches(event = azerty, platform = pc) shouldBe true
            blockComment.matches(event = azerty, platform = pc) shouldBe false
        }

        withClue("AZERTY: the real period is Shift on the key at the US comma, which is not Stop") {
            stop.matches(event = keyEvent(key = ".", code = "Comma", ctrl = true, shift = true), platform = pc) shouldBe false
        }

        withClue("AZERTY: Ctrl+6 types a minus on the number row: it is the digit 6, never Ctrl+-") {
            val ctrl6 = keyEvent(key = "-", code = "Digit6", ctrl = true)

            KeyChord.parse("Mod-6", pc).matches(event = ctrl6, platform = pc) shouldBe true
            KeyChord.parse("Mod--", pc).matches(event = ctrl6, platform = pc) shouldBe false
        }

        withClue("AZERTY: Ctrl+Shift+M sits where the US semicolon is: it is the letter, never Ctrl+Shift+;") {
            val ctrlShiftM = keyEvent(key = "M", code = "Semicolon", ctrl = true, shift = true)

            KeyChord.parse("Mod-Shift-M", pc).matches(event = ctrlShiftM, platform = pc) shouldBe true
            KeyChord.parse("Mod-Shift-;", pc).matches(event = ctrlShiftM, platform = pc) shouldBe false
        }

        withClue("without Shift the typed character decides: the German key where the US slash sits types a minus") {
            KeyChord.parse("Mod-/", pc).matches(event = keyEvent(key = "-", code = "Slash", ctrl = true), platform = pc) shouldBe false
            KeyChord.parse("Mod-/", pc).matches(event = keyEvent(key = "/", code = "Slash", ctrl = true), platform = pc) shouldBe true
        }
    }

    "named keys by their name, Space by the space character" {
        KeyChord.parse("Mod-Shift-Enter", pc)
            .matches(event = keyEvent(key = "Enter", code = "Enter", ctrl = true, shift = true), platform = pc) shouldBe true
        KeyChord.parse("Ctrl-Space", pc).matches(event = keyEvent(key = " ", code = "Space", ctrl = true), platform = pc) shouldBe true
        KeyChord.parse("Escape", pc).matches(event = keyEvent(key = "Escape", code = "Escape"), platform = pc) shouldBe true
    }

    "labels: words joined by plus on a PC, the Mac's signs in the Mac's order" {
        KeyChord.parse("Mod-Shift-Enter", pc).label(pc) shouldBe "Ctrl+Shift+Enter"
        KeyChord.parse("Mod-Shift-Enter", mac).label(mac) shouldBe "⇧⌘↩"
        KeyChord.parse("Ctrl-Alt-Shift-L", mac).label(mac) shouldBe "⌃⌥⇧L"
        KeyChord.parse("Mod-Shift-.", pc).label(pc) shouldBe "Ctrl+Shift+."
    }

    "key parts: one per key cap, words on a PC, compact signs where PC keyboards have them, the Mac's signs" {
        val playKey = "Mod-Shift-Enter"

        KeyChord.parse(playKey, pc).keyParts(pc) shouldBe listOf("Ctrl", "Shift", "Enter")
        KeyChord.parse(playKey, pc).keyParts(pc, compact = true) shouldBe listOf("Ctrl", "⇧", "↵")
        KeyChord.parse(playKey, mac).keyParts(mac) shouldBe listOf("⇧", "⌘", "↩")

        withClue("Ctrl and Alt stay words on a PC, also compact") {
            KeyChord.parse("Ctrl-Alt-Backspace", pc).keyParts(pc, compact = true) shouldBe listOf("Ctrl", "Alt", "⌫")
        }

        withClue("the label joins the words") {
            KeyChord.parse(playKey, pc).label(pc) shouldBe "Ctrl+Shift+Enter"
        }
    }
})
