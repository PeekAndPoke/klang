package io.peekandpoke.klang.workshop.keys

import kotlinx.browser.window

/**
 * The platform whose keys a [KeysManager] uses: `Mod` in a [KeyChord] is Ctrl on a PC and Cmd on a Mac, and a
 * [KeyAction] may name other keys for the Mac.
 */
enum class KeyPlatform {
    /** Windows and Linux. */
    Pc,

    /** macOS, and the iPad with a keyboard. */
    Mac;

    companion object {
        /** The platform of this browser. */
        fun detect(): KeyPlatform {
            val platform = window.navigator.platform.lowercase()

            return when {
                "mac" in platform || "iphone" in platform || "ipad" in platform -> Mac
                else -> Pc
            }
        }
    }
}
