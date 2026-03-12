package io.peekandpoke.klang.feel

import kotlinx.css.Color

/**
 * Snapshot accessors for the current [KlangLookAndFeel].
 *
 * Use this only in non-subscribing contexts (e.g. [io.peekandpoke.klang.codemirror.CodeMirrorTheme])
 * where reacting to theme changes is not required.
 *
 * In Kraft components, subscribe to [KlangTheme] instead.
 */
object KlangColors {

    // ── Surfaces ──────────────────────────────────────────────────────────────

    val appBackground: Color get() = Color(KlangTheme().appBackground)
    val menuBackground: Color get() = Color(KlangTheme().menuBackground)
    val panelBackground: Color get() = Color(KlangTheme().panelBackground)
    val cardBackground: Color get() = Color(KlangTheme().cardBackground)
    val overlayBackground: Color get() = Color(KlangTheme().overlayBackground)

    // ── Text ──────────────────────────────────────────────────────────────────

    val textPrimary: Color get() = Color(KlangTheme().textPrimary)
    val textSecondary: Color get() = Color(KlangTheme().textSecondary)
    val textTertiary: Color get() = Color(KlangTheme().textTertiary)

    // ── Accent ────────────────────────────────────────────────────────────────

    val accent: Color get() = Color(KlangTheme().accent)

    // ── Status ────────────────────────────────────────────────────────────────

    val excellent: Color get() = Color(KlangTheme().excellent)
    val good: Color get() = Color(KlangTheme().good)
    val moderate: Color get() = Color(KlangTheme().moderate)
    val warning: Color get() = Color(KlangTheme().warning)
    val critical: Color get() = Color(KlangTheme().critical)

    val statusColors: List<Color> get() = KlangTheme().statusColors

    // ── Hex strings (for CodeMirror / canvas / JS object contexts) ────────────

    object Hex {
        val appBackground: String get() = KlangTheme().appBackground
        val menuBackground: String get() = KlangTheme().menuBackground
        val panelBackground: String get() = KlangTheme().panelBackground
        val cardBackground: String get() = KlangTheme().cardBackground
        val overlayBackground: String get() = KlangTheme().overlayBackground

        val textPrimary: String get() = KlangTheme().textPrimary
        val textSecondary: String get() = KlangTheme().textSecondary
        val textTertiary: String get() = KlangTheme().textTertiary

        val accent: String get() = KlangTheme().accent

        val excellent: String get() = KlangTheme().excellent
        val good: String get() = KlangTheme().good
        val moderate: String get() = KlangTheme().moderate
        val warning: String get() = KlangTheme().warning
        val critical: String get() = KlangTheme().critical
    }
}
