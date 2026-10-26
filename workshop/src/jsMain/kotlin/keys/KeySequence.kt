package io.peekandpoke.klang.workshop.keys

/**
 * Something that triggers an action: a single [KeyChord] (`"Mod-Shift-Enter"`) or a [KeySequence] of keys pressed in a
 * row (`"Shift Shift"`). A binding is shown as groups of key caps, one group per step.
 */
sealed interface KeyBinding {
    /** The steps as groups of key cap parts, one group per step; see [KeyChord.keyParts]. */
    fun keyGroups(platform: KeyPlatform, compact: Boolean = false): List<List<String>>

    /** The binding as the user reads it in text: `Ctrl+Shift+Enter`, `Shift Shift`. */
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

/**
 * Keys pressed in a row (KLANG-00325, maintainer 2026-10-25: "keys in a row"), written as steps separated by spaces.
 *
 * For now a sequence is two modifiers tapped on their own: `"Shift Shift"` (search everywhere, as in IntelliJ). A tap
 * is the modifier going down and up with no other key between and not held long; the two taps follow each other
 * within a short window. The [KeysManager] owns both times. `Mod` is Ctrl on a PC and Cmd on a Mac, as in a chord.
 * Sequences of chords (`"Mod-K Mod-S"`) come later; until then their text is unreadable, so a binding never waits
 * silently for a key that cannot come.
 */
data class KeySequence(
    /** The tapped modifiers, in order; two. */
    val taps: List<TapModifier>,
) : KeyBinding {

    companion object {
        /** True when [text] is a sequence (steps separated by spaces), not a single chord. */
        fun isSequence(text: String): Boolean = text.trim().contains(' ')

        /**
         * Reads [text] for [platform].
         *
         * @throws IllegalArgumentException when a step is no bare modifier (a chord sequence, not supported yet) or
         *   there are not two steps (only the last two taps count, so a longer row could never fire)
         */
        fun parse(text: String, platform: KeyPlatform): KeySequence {
            val steps = text.trim().split(Regex("\\s+"))

            require(steps.size == 2) { "Keys in a row are two taps for now: \"$text\"" }

            return KeySequence(
                taps = steps.map { step ->
                    when (step.lowercase()) {
                        "mod" -> if (platform == KeyPlatform.Mac) TapModifier.Meta else TapModifier.Ctrl
                        "ctrl", "control" -> TapModifier.Ctrl
                        "alt", "option", "opt" -> TapModifier.Alt
                        "shift" -> TapModifier.Shift
                        "meta", "cmd", "command" -> TapModifier.Meta
                        else -> throw IllegalArgumentException(
                            "\"$step\" in \"$text\": a step of a sequence is a modifier tapped on its own for now"
                        )
                    }
                },
            )
        }
    }

    override fun keyGroups(platform: KeyPlatform, compact: Boolean): List<List<String>> =
        taps.map { listOf(it.keyPart(platform = platform, compact = compact)) }

    override fun label(platform: KeyPlatform): String = taps.joinToString(" ") { it.keyPart(platform = platform, compact = false) }
}
