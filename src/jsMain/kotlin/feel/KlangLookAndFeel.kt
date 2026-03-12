package io.peekandpoke.klang.feel

import de.peekandpoke.kraft.addons.styling.StyleSheet
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.css.Color

/**
 * All Klang UI colors in one place.
 *
 * This is a pure data class — no side effects, easy to copy/modify for user theming.
 * All values are hex strings so they work both in CSS (via [Color]) and in JS contexts
 * (e.g. CodeMirror theme objects).
 *
 * Use [KlangTheme] to obtain the active instance; components subscribe to that
 * stream so they re-render automatically when the theme changes.
 */
data class KlangLookAndFeel(

    // ── Surfaces ──────────────────────────────────────────────────────────────

    val appBackground: String = "#191C22",
    val menuBackground: String = "#000000",
    val panelBackground: String = "#21252b",
    val cardBackground: String = "#2c313a",
    val overlayBackground: String = "#353a42",

    // ── Text hierarchy ────────────────────────────────────────────────────────

    val textPrimary: String = "#D7DADF",
    val textSecondary: String = "#abb2bf",
    /** Dimmer than [textSecondary] — used for line-number gutters etc. */
    val textTertiary: String = "#5c6370",

    // ── Accent ────────────────────────────────────────────────────────────────

    val accent: String = "#528bff",

    // ── Gold — code highlights, links ─────────────────────────────────────────

    val gold: String = "#e8b84b",

    // ── Status scale (5 levels) ───────────────────────────────────────────────

    val excellent: String = "#56b6c2",
    val good: String = "#98c379",
    val moderate: String = "#e5c07b",
    val warning: String = "#d19a66",
    val critical: String = "#e06c75",

    ) {

    companion object {
        /** Reads all tokens from the CSS custom properties defined on `:root` in `index.css`. */
        fun fromCss(): KlangLookAndFeel {
            val style = window.getComputedStyle(document.documentElement!!)
            fun v(name: String): String = style.getPropertyValue(name).trim()
            return KlangLookAndFeel(
                appBackground = v("--klang-bg-app"),
                menuBackground = v("--klang-bg-menu"),
                panelBackground = v("--klang-bg-panel"),
                cardBackground = v("--klang-bg-card"),
                overlayBackground = v("--klang-bg-overlay"),
                textPrimary = v("--klang-text-primary"),
                textSecondary = v("--klang-text-secondary"),
                textTertiary = v("--klang-text-tertiary"),
                accent = v("--klang-accent"),
                gold = v("--klang-gold"),
                excellent = v("--klang-excellent"),
                good = v("--klang-good"),
                moderate = v("--klang-moderate"),
                warning = v("--klang-warning"),
                critical = v("--klang-critical"),
            )
        }
    }

    // ── Convenience helpers ───────────────────────────────────────────────────

    val statusColors: List<Color>
        get() = listOf(Color(excellent), Color(good), Color(moderate), Color(warning), Color(critical))

    // ── Programmable stylesheet ───────────────────────────────────────────────

    /**
     * Stylesheet generated from this look-and-feel.
     *
     * Currently a placeholder — CSS rules will be added here incrementally as we
     * migrate from inline `css {}` blocks to fully programmable CSS.
     *
     * [KlangTheme] mounts/unmounts this when the active look-and-feel changes.
     */
    inner class Styles : StyleSheet("klang-theme") {

        /**
         * Dark-themed docs hover popup.
         * Applied to the segment container; overrides Semantic UI descendant colors.
         */
        /** Overlays a 20 % dark tint — use on code blocks, inset panels, etc. */
        val darken20 by rule {
            put("background-color", "rgba(0,0,0,0.2) !important")
        }

        val docsPopup by rule {
            rule(" .ui.header") {
                put("color", "$textSecondary !important")
            }
            rule(" .ui.list > .item > .content") {
                put("color", "$textPrimary !important")
            }
            rule(" .ui.divider") {
                put("border-top-color", "$appBackground !important")
                put("border-bottom-color", "transparent !important")
            }
            rule(" pre") {
                put("color", "$textPrimary !important")
            }
        }
    }

    val styles = Styles()
}
