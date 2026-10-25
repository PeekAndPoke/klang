package io.peekandpoke.klang.workshop.keys

import kotlinx.html.FlowContent
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.unsafe

/**
 * The key signs of [KeyChord.keyParts] (`⇧`, `↵`, `⌘`, ...) drawn as small vector icons, sharp at any size: a font
 * draws them small and soft at the size of a key cap. Stroked in the text colour (`currentColor`), on a 12 by 12 grid.
 */
object KeyCapGlyphs {

    /** What each sign says, for a screen reader. */
    private val WORDS = mapOf(
        "⇧" to "Shift",
        "↵" to "Enter",
        "↩" to "Return",
        "⌫" to "Backspace",
        "⌘" to "Command",
        "⌥" to "Option",
        "⌃" to "Control",
        "↑" to "Up",
        "↓" to "Down",
        "←" to "Left",
        "→" to "Right",
    )

    /** The SVG paths of each sign; a part not in here is shown as text. */
    private val PATHS = mapOf(
        // Shift: the hollow arrow up
        "⇧" to "M6 1.5 L10.5 6 H8 V10.5 H4 V6 H1.5 Z",
        // Enter (PC) and Return (Mac): down, then back to the left, with the arrow's head
        "↵" to "M10 2 V7 H2.5 M5 4.5 L2.5 7 L5 9.5",
        "↩" to "M10 2 V7 H2.5 M5 4.5 L2.5 7 L5 9.5",
        // Backspace: the key pointing left, with a cross
        "⌫" to "M4 2.5 H10.5 V9.5 H4 L1 6 Z M6 4.5 L8.5 7.5 M8.5 4.5 L6 7.5",
        // Command: the square with its four loops
        "⌘" to "M4.5 4.5 H7.5 V7.5 H4.5 Z " +
            "M4.5 4.5 V3 A1.5 1.5 0 1 0 3 4.5 H4.5 " +
            "M7.5 4.5 V3 A1.5 1.5 0 1 1 9 4.5 H7.5 " +
            "M7.5 7.5 V9 A1.5 1.5 0 1 0 9 7.5 H7.5 " +
            "M4.5 7.5 V9 A1.5 1.5 0 1 1 3 7.5 H4.5",
        // Option: the switch
        "⌥" to "M1 3.5 H4 L8 8.5 H11 M7.5 3.5 H11",
        // Control: the caret
        "⌃" to "M3 7.5 L6 4 L9 7.5",
        "↑" to "M6 10.5 V1.5 M2.5 5 L6 1.5 L9.5 5",
        "↓" to "M6 1.5 V10.5 M2.5 7 L6 10.5 L9.5 7",
        "←" to "M10.5 6 H1.5 M5 2.5 L1.5 6 L5 9.5",
        "→" to "M1.5 6 H10.5 M7 2.5 L10.5 6 L7 9.5",
    )

    /** Draws [part]: its icon, [sizePx] high, or the part as text. */
    fun FlowContent.keyCapGlyph(part: String, sizePx: Int = 12) {
        val path = PATHS[part]

        if (path == null) {
            +part

            return
        }

        span {
            style = "display: inline-flex; width: ${sizePx}px; height: ${sizePx}px"
            attributes["role"] = "img"
            attributes["aria-label"] = WORDS[part] ?: part

            unsafe {
                +(
                    """<svg viewBox="0 0 12 12" width="$sizePx" height="$sizePx" fill="none" stroke="currentColor" """ +
                        """stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">""" +
                        """<path d="$path"/></svg>"""
                    )
            }
        }
    }
}
