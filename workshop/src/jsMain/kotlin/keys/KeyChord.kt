package io.peekandpoke.klang.workshop.keys

import org.w3c.dom.events.KeyboardEvent

/**
 * One key with its modifiers, written as text: `"Mod-Shift-Enter"`, `"Ctrl-Alt-L"`, `"F3"`, `"Mod-Shift-7"`.
 *
 * The modifiers come first, joined by `-`: `Mod` (Ctrl on a PC, Cmd on a Mac), `Ctrl`, `Alt` (Option on a Mac), `Shift`
 * and `Meta` (Cmd on a Mac, the Windows key on a PC; `Cmd` is the same). The key comes last: a letter, a digit, a symbol
 * (`.`, `/`, `-` as in `"Mod--"`), or a named key as the browser names it (`Enter`, `Escape`, `Backspace`, `ArrowUp`,
 * `F1`; `Esc`, `Up`, `Down`, `Left`, `Right` and `Space` are understood too).
 *
 * How a key is matched against the key the user pressed. Every key event has exactly one reading, so one key never
 * triggers two chords: a key on the number row is a digit, a typed a-z is a letter, anything else a symbol.
 * - **Letters** by the typed character, so Ctrl+Z stays undo on a French keyboard; by the key's position when the
 *   typed character is a letter of another alphabet (a Cyrillic layout).
 * - **Chords with Option on a Mac** match letters and symbols by the key's position on a US keyboard, so a chord
 *   defined on purpose still fires whatever Option types there: a sign (Option+J is `∆`), an ASCII sign (German
 *   Option+L is `@`) or a dead key (US Option+E) (maintainer, 2026-10-25, the Mac's Option rule a); Option typing is
 *   lost only on keys that have such a chord. Alt on a PC types plain characters, so a PC Alt chord keeps matching by
 *   its letter on every layout.
 * - **Digits** by the key's position (`event.code`), so Ctrl+Shift+7 is the same key on every layout, where Shift turns
 *   the 7 into another character; on the keypad only while it types digits (NumLock on).
 * - **Symbols** without Shift by the typed character, with Shift by the key's position on a US keyboard (Ctrl+Shift+.
 *   is the key right of the comma key, which types `:` with Shift on a German keyboard). On a German keyboard
 *   Ctrl+Shift+7 is therefore the digit 7, never the slash.
 * - **Named keys** by their name.
 * - **Modifiers** exactly: Ctrl+Shift+Z is not Ctrl+Z.
 * - **AltGr** types characters: while it is held only the typed character counts (Windows reports AltGr as Ctrl+Alt,
 *   and may report a real Ctrl+Alt as AltGr), so German Ctrl+Alt+L still matches and Polish AltGr+L (`ł`) does not.
 *
 * A chord that can never be pressed ([canFire] false: a symbol with Shift, or with Option on a Mac, whose US position
 * is unknown, `Mod-Shift-?`, `Alt-+` on a Mac)
 * is a problem of the keymap.
 */
data class KeyChord(
    /** The key, normalised: a lower-case letter, a digit, a symbol or a named key. */
    val key: String,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
    val meta: Boolean = false,
) {
    companion object {
        /** The position (`event.code`) of a symbol on a US keyboard. */
        private val SYMBOL_CODES = mapOf(
            "." to "Period",
            "," to "Comma",
            "/" to "Slash",
            ";" to "Semicolon",
            "'" to "Quote",
            "[" to "BracketLeft",
            "]" to "BracketRight",
            "\\" to "Backslash",
            "-" to "Minus",
            "=" to "Equal",
            "`" to "Backquote",
        )

        /** Other names for named keys, to the browser's name. */
        private val ALIASES = mapOf(
            "esc" to "Escape",
            "escape" to "Escape",
            "enter" to "Enter",
            "return" to "Enter",
            "backspace" to "Backspace",
            "delete" to "Delete",
            "del" to "Delete",
            "tab" to "Tab",
            "space" to "Space",
            "up" to "ArrowUp",
            "down" to "ArrowDown",
            "left" to "ArrowLeft",
            "right" to "ArrowRight",
            "arrowup" to "ArrowUp",
            "arrowdown" to "ArrowDown",
            "arrowleft" to "ArrowLeft",
            "arrowright" to "ArrowRight",
            "home" to "Home",
            "end" to "End",
            "pageup" to "PageUp",
            "pagedown" to "PageDown",
            "insert" to "Insert",
        )

        /** The browser's names of the modifier keys themselves (`event.key`). */
        val MODIFIER_KEY_NAMES = setOf("Control", "Alt", "Shift", "Meta", "AltGraph", "OS")

        private val FUNCTION_KEY = Regex("^[fF]([1-9]|1[0-9]|2[0-4])$")

        /**
         * Reads [text] for [platform].
         *
         * @throws IllegalArgumentException when the text names an unknown modifier or no key
         */
        fun parse(text: String, platform: KeyPlatform): KeyChord {
            val trimmed = text.trim()

            // "Mod--" names the minus key: the last dash is the key, not a separator
            val (modifierPart, keyPart) = when {
                trimmed.endsWith("--") -> trimmed.dropLast(2) to "-"
                trimmed == "-" -> "" to "-"
                else -> trimmed.substringBeforeLast("-", missingDelimiterValue = "") to trimmed.substringAfterLast("-")
            }

            require(keyPart.isNotEmpty()) { "No key in \"$text\"" }

            var ctrl = false
            var alt = false
            var shift = false
            var meta = false

            modifierPart.split("-").filter { it.isNotEmpty() }.forEach { modifier ->
                when (modifier.lowercase()) {
                    "mod" -> when (platform) {
                        KeyPlatform.Pc -> ctrl = true
                        KeyPlatform.Mac -> meta = true
                    }

                    "ctrl", "control" -> ctrl = true
                    "alt", "option", "opt" -> alt = true
                    "shift" -> shift = true
                    "meta", "cmd", "command" -> meta = true
                    else -> throw IllegalArgumentException("Unknown modifier \"$modifier\" in \"$text\"")
                }
            }

            return KeyChord(key = normaliseKey(keyPart, text), ctrl = ctrl, alt = alt, shift = shift, meta = meta)
        }

        private fun normaliseKey(key: String, text: String): String {
            if (key.length == 1) {
                return key.lowercase()
            }

            ALIASES[key.lowercase()]?.let { return it }

            if (FUNCTION_KEY.matches(key)) {
                return key.uppercase()
            }

            throw IllegalArgumentException("Unknown key \"$key\" in \"$text\"")
        }
    }

    /** True when [event] is this chord being pressed, on [platform] (Option on a Mac reads the key's position). */
    fun matches(event: KeyboardEvent, platform: KeyPlatform): Boolean {
        if (event.ctrlKey != ctrl || event.altKey != alt || event.shiftKey != shift || event.metaKey != meta) {
            return false
        }

        // AltGr types a character (German AltGr+Q is @): only that character counts, never the key's position. The Mac
        // has no AltGr (a browser may still report Option as AltGraph there), so its Option goes by the rule below
        if (platform == KeyPlatform.Pc && event.getModifierState("AltGraph")) {
            return when {
                key.length == 1 -> event.key.lowercase() == key
                key == "Space" -> event.key == " "
                else -> event.key == key
            }
        }

        return matchesKey(event = event, platform = platform)
    }

    /** True when [event] pressed this chord's key; [matches] has checked the modifiers, Alt and Shift choose the reading. */
    private fun matchesKey(event: KeyboardEvent, platform: KeyPlatform): Boolean {
        val pressed = event.key
        val code = event.code
        // The one reading of the key: a digit on the number row, else a typed a-z letter, else a symbol
        val onNumberRow = code.startsWith("Digit")
        val typedLetter = pressed.length == 1 && pressed.lowercase()[0] in 'a'..'z'

        // Option on a Mac types signs, ASCII ones too (German Option+L is @) and dead keys (US Option+E): its chords go
        // by the key's position (maintainer, 2026-10-25, rule a). Alt on a PC types plain characters, which keep their
        // own readings, so a PC Alt chord stays on its letter on every layout
        if (platform == KeyPlatform.Mac && event.altKey && key.length == 1 && key[0] !in '0'..'9') {
            return when {
                key[0] in 'a'..'z' -> code == "Key${key.uppercase()}"
                else -> SYMBOL_CODES[key]?.let { it == code } ?: false
            }
        }

        return when {
            // The keypad's 7 with NumLock off is Home, not 7
            key.length == 1 && key[0] in '0'..'9' -> code == "Digit$key" || (code == "Numpad$key" && pressed == key)

            key.length == 1 && key[0] in 'a'..'z' -> when {
                pressed.length == 1 && pressed.lowercase()[0] in 'a'..'z' -> pressed.lowercase() == key
                // A letter of another alphabet: the key's position decides
                pressed.length == 1 && pressed[0].isLetter() -> code == "Key${key.uppercase()}"
                // A sign (Option on a Mac) or a named key: no letter was pressed
                else -> false
            }

            key.length == 1 -> when {
                onNumberRow || typedLetter -> false
                event.shiftKey -> SYMBOL_CODES[key]?.let { it == code } ?: false
                else -> pressed == key
            }

            key == "Space" -> pressed == " " || code == "Space"

            else -> pressed == key
        }
    }

    /**
     * False when no key press on [platform] can ever match: a symbol read by its US position (with Shift everywhere,
     * with Option on a Mac) whose position is unknown.
     */
    fun canFire(platform: KeyPlatform): Boolean {
        val unknownSymbol = key.length == 1 && key[0] !in 'a'..'z' && key[0] !in '0'..'9' && key !in SYMBOL_CODES
        // Read by the key's US position: with Shift everywhere, with Option on a Mac
        val byPosition = shift || (alt && platform == KeyPlatform.Mac)

        return !(unknownSymbol && byPosition)
    }

    /** True when the chord holds Ctrl, Alt or Meta: such a chord also acts while the user types in a text field. */
    val hasCommandModifier: Boolean get() = ctrl || alt || meta

    /** The chord as the user reads it on [platform]: `Ctrl+Shift+Enter` on a PC, `⇧⌘↩` on a Mac. */
    fun label(platform: KeyPlatform): String = when (platform) {
        KeyPlatform.Pc -> keyParts(platform).joinToString("+")
        KeyPlatform.Mac -> keyParts(platform).joinToString("")
    }

    /**
     * The chord's keys one by one, modifiers first, for a hint that draws each key as a key cap: `Ctrl`, `Shift`,
     * `Enter` on a PC; the Mac's signs `⌃ ⌥ ⇧ ⌘` in the Mac's order, `⇧`, `⌘`, `↩`.
     *
     * [compact] takes the signs PC users know from their keyboards where a word is long: `⇧` for Shift, `↵` for Enter,
     * `⌫` for Backspace. Ctrl and Alt stay words: their Mac signs mean nothing on a PC.
     */
    fun keyParts(platform: KeyPlatform, compact: Boolean = false): List<String> = when (platform) {
        KeyPlatform.Pc -> listOfNotNull(
            "Ctrl".takeIf { ctrl },
            "Alt".takeIf { alt },
            (if (compact) "⇧" else "Shift").takeIf { shift },
            "Win".takeIf { meta },
            keyName(platform = platform, compact = compact),
        )

        // The Mac's order of the modifier signs: Control, Option, Shift, Command
        KeyPlatform.Mac -> listOfNotNull(
            "⌃".takeIf { ctrl },
            "⌥".takeIf { alt },
            "⇧".takeIf { shift },
            "⌘".takeIf { meta },
            keyName(platform = platform, compact = true),
        )
    }

    private fun keyName(platform: KeyPlatform, compact: Boolean): String = when (key) {
        "Enter" -> when {
            platform == KeyPlatform.Mac -> "↩"
            compact -> "↵"
            else -> "Enter"
        }

        "Backspace" -> if (compact) "⌫" else "Backspace"
        "Escape" -> "Esc"
        "ArrowUp" -> "↑"
        "ArrowDown" -> "↓"
        "ArrowLeft" -> "←"
        "ArrowRight" -> "→"
        "Space" -> "Space"
        else -> if (key.length == 1) key.uppercase() else key
    }
}
