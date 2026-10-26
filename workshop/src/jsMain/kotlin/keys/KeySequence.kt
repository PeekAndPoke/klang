package io.peekandpoke.klang.workshop.keys

/**
 * Something that triggers an action: a single [KeyChord] (`"Mod-Shift-Enter"`) or a [KeySequence] of keys pressed in a
 * row (`"Shift Shift"`, `"Mod-K Mod-S"`). A binding is shown as groups of key caps, one group per step.
 */
sealed interface KeyBinding {
    /** The steps as groups of key cap parts, one group per step; see [KeyChord.keyParts]. */
    fun keyGroups(platform: KeyPlatform, compact: Boolean = false): List<List<String>>

    /** The binding as the user reads it in text: `Ctrl+Shift+Enter`, `Shift Shift`, `Ctrl+K Ctrl+S`. */
    fun label(platform: KeyPlatform): String
}

/** A modifier key on its own, as a tap in a [KeySequence]. */
enum class TapModifier {
    Ctrl,
    Alt,
    Shift,
    Meta;

    /** The tapped key's cap on [platform]: its word on a PC (`⇧` for Shift when [compact]), its sign on a Mac. */
    fun keyPart(platform: KeyPlatform, compact: Boolean): String = when (platform) {
        KeyPlatform.Pc -> when (this) {
            Ctrl -> "Ctrl"
            Alt -> "Alt"
            Shift -> if (compact) "⇧" else "Shift"
            Meta -> "Win"
        }

        KeyPlatform.Mac -> when (this) {
            Ctrl -> "⌃"
            Alt -> "⌥"
            Shift -> "⇧"
            Meta -> "⌘"
        }
    }

    companion object {
        /** The tapped modifier a key event names (`event.key`), null for any other key. */
        fun ofKey(key: String): TapModifier? = when (key) {
            "Control" -> Ctrl
            "Alt" -> Alt
            "Shift" -> Shift
            "Meta" -> Meta
            else -> null
        }
    }
}

/** One step of a [KeySequence]: a modifier tapped on its own, or a chord pressed. */
sealed interface KeyStep {
    /** The step's key caps on [platform]; see [KeyChord.keyParts]. */
    fun keyParts(platform: KeyPlatform, compact: Boolean): List<String>

    /** The step as the user reads it: `Shift`, `Ctrl+K`. */
    fun label(platform: KeyPlatform): String

    /** A modifier going down and up on its own. */
    data class Tap(val modifier: TapModifier) : KeyStep {
        override fun keyParts(platform: KeyPlatform, compact: Boolean): List<String> =
            listOf(modifier.keyPart(platform = platform, compact = compact))

        override fun label(platform: KeyPlatform): String = modifier.keyPart(platform = platform, compact = false)
    }

    /** A chord pressed, as a single key binding is. */
    data class Press(val chord: KeyChord) : KeyStep {
        override fun keyParts(platform: KeyPlatform, compact: Boolean): List<String> =
            chord.keyParts(platform = platform, compact = compact)

        override fun label(platform: KeyPlatform): String = chord.label(platform)
    }
}

/**
 * Keys pressed in a row (KLANG-00325, maintainer 2026-10-25: "keys in a row"), written as steps separated by spaces.
 * Two kinds, never mixed:
 *
 * - **Taps:** two modifiers tapped on their own, `"Shift Shift"` (search everywhere, as in IntelliJ). A tap is the
 *   modifier going down and up with no other key between and not held long; the two taps follow each other within a
 *   short window.
 * - **Chords:** two chords or more, `"Mod-K Mod-S"`. After the first the [KeysManager] waits a while for the next
 *   (the app shows that it waits); Esc, a key that fits no sequence, a click or the time running out ends the wait.
 *
 * The [KeysManager] owns the times. `Mod` is Ctrl on a PC and Cmd on a Mac, as in a chord.
 */
data class KeySequence(
    /** The steps, in order: two taps, or two chords or more. */
    val steps: List<KeyStep>,
) : KeyBinding {

    companion object {
        /** The words of a modifier tapped on its own. */
        private val TAP_WORDS = listOf("mod", "ctrl", "control", "alt", "option", "opt", "shift", "meta", "cmd", "command")

        /** True when [text] is a sequence (steps separated by spaces), not a single chord. */
        fun isSequence(text: String): Boolean = text.trim().contains(' ')

        /** A sequence of [taps]. */
        fun ofTaps(vararg taps: TapModifier): KeySequence = KeySequence(steps = taps.map { KeyStep.Tap(it) })

        /** A sequence of [chords]. */
        fun ofChords(vararg chords: KeyChord): KeySequence = KeySequence(steps = chords.map { KeyStep.Press(it) })

        /**
         * Reads [text] for [platform].
         *
         * @throws IllegalArgumentException when taps and chords are mixed, a chord cannot be read, taps are not two (only
         *   the last two taps count, so a longer row could never fire), or there is a single step
         */
        fun parse(text: String, platform: KeyPlatform): KeySequence {
            val words = text.trim().split(Regex("\\s+"))
            val taps = words.count { it.lowercase() in TAP_WORDS }

            require(words.size >= 2) { "Keys in a row need two steps or more: \"$text\"" }

            return when (taps) {
                words.size -> {
                    require(words.size == 2) { "Taps in a row are two: \"$text\"" }

                    KeySequence(steps = words.map { KeyStep.Tap(tapOf(word = it, platform = platform)) })
                }

                0 -> KeySequence(steps = words.map { KeyStep.Press(KeyChord.parse(it, platform)) })

                else -> throw IllegalArgumentException("\"$text\" mixes modifiers tapped on their own with chords")
            }
        }

        private fun tapOf(word: String, platform: KeyPlatform): TapModifier = when (word.lowercase()) {
            "mod" -> if (platform == KeyPlatform.Mac) TapModifier.Meta else TapModifier.Ctrl
            "ctrl", "control" -> TapModifier.Ctrl
            "alt", "option", "opt" -> TapModifier.Alt
            "shift" -> TapModifier.Shift
            else -> TapModifier.Meta
        }
    }

    /** The chords pressed in a row; empty for taps. */
    val chords: List<KeyChord> get() = steps.filterIsInstance<KeyStep.Press>().map { it.chord }

    override fun keyGroups(platform: KeyPlatform, compact: Boolean): List<List<String>> =
        steps.map { it.keyParts(platform = platform, compact = compact) }

    override fun label(platform: KeyPlatform): String = steps.joinToString(" ") { it.label(platform) }
}
