package io.peekandpoke.klang.ui.feel

import io.peekandpoke.kraft.addons.styling.StyleSheet
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
        /** Gold-highlighted button — use for selected/active toggle buttons. */
        val goldButton by rule {
            put("background-color", "$gold !important")
            put("color", "#222 !important")
        }

        /** Overlays a 10 % dark tint — use on subtle inset panels. */
        val darken10 by rule {
            put("background-color", "rgba(0,0,0,0.1) !important")
        }

        /** Overlays a 20 % dark tint — use on code blocks, inset panels, etc. */
        val darken20 by rule {
            put("background-color", "rgba(0,0,0,0.2) !important")
        }

        val mnPatternInput by rule {
            put("position", "relative")
            put("border-radius", "var(--klang-border-radius)")
        }

        val mnPatternInputOverlay by rule {
            put("position", "absolute")
            put("top", "0"); put("left", "0"); put("right", "0"); put("bottom", "0")
            put("font-family", "monospace")
            put("font-size", "15px")
            put("line-height", "1.4")
            put("padding", "8px 10px")
            put("white-space", "pre")
            put("pointer-events", "none")
            put("box-sizing", "border-box")
            put("border", "1px solid transparent")
            put("overflow", "hidden")
            put("color", "transparent")
        }

        val mnPatternInputTextarea by rule {
            put("position", "relative")
            put("width", "100%")
            put("font-family", "monospace")
            put("font-size", "15px")
            put("line-height", "1.4")
            put("padding", "8px 10px")
            put("border-radius", "var(--klang-border-radius)")
            put("border", "1px solid $overlayBackground")
            put("transition", "border-color var(--klang-transition)")
            put("outline", "none")
            put("box-sizing", "border-box")
            put("resize", "none")
            put("white-space", "pre")
            put("overflow-x", "auto")
            put("overflow-y", "hidden")
            put("min-height", "38px")
            put("background", "transparent")
            put("color", textPrimary)
            put("caret-color", textPrimary)
        }

        val mnPatternInputTextareaError by rule {
            put("border-color", "$critical !important")
        }

        val mnPatternInputTextareaFocus by rule {
            rule(":focus") {
                put("border-color", "$gold !important")
                put("box-shadow", "0 0 0 1px $gold !important")
                put("outline", "none")
            }
        }

        // ── Modifier chip row ─────────────────────────────────────────────────

        val mnChip by rule {
            put("display", "flex")
            put("align-items", "center")
            put("gap", "4px")
            put("background-color", "rgba(0,0,0,0.2)")
            put("border-radius", "6px")
            put("padding", "0 8px")
            put("min-height", "30px")
            put("box-sizing", "border-box")
        }

        val mnChipToggle by rule {
            put("cursor", "pointer")
            put("font-family", "monospace")
            put("font-size", "13px")
            put("color", textSecondary)
            put("background-color", "rgba(0,0,0,0.2)")
            put("border-radius", "6px")
            put("padding", "4px 10px")
            put("transition", "color var(--klang-transition)")
            rule(":hover") { put("color", textPrimary) }
        }

        val mnChipLabel by rule {
            put("font-size", "12px")
            put("font-weight", "600")
            put("color", textTertiary)
            put("min-width", "60px")
        }

        val mnChipSymbol by rule {
            put("font-family", "monospace")
            put("font-weight", "bold")
            put("font-size", "14px")
            put("color", textSecondary)
        }

        val mnChipAction by rule {
            put("cursor", "pointer")
            put("color", textTertiary)
            put("font-size", "14px")
            put("padding", "0 2px")
            put("transition", "color var(--klang-transition)")
            rule(":hover") { put("color", textPrimary) }
        }

        val mnChipSeparator by rule {
            put("color", textTertiary)
            put("font-size", "12px")
        }

        val mnChipInput by rule {
            put("background", "rgba(0,0,0,0.2)")
            put("color", textPrimary)
            put("border", "1px solid $overlayBackground")
            put("border-radius", "4px")
            put("outline", "none")
            put("height", "22px")
            put("box-sizing", "border-box")
            put("transition", "border-color var(--klang-transition), box-shadow var(--klang-transition)")
            put("-moz-appearance", "textfield")
            rule(":focus") {
                put("border-color", "$gold")
                put("box-shadow", "0 0 0 1px $gold")
                put("outline", "none")
            }
            rule("::-webkit-inner-spin-button") {
                put("-webkit-appearance", "none")
                put("margin", "0")
            }
            rule("::-webkit-outer-spin-button") {
                put("-webkit-appearance", "none")
                put("margin", "0")
            }
        }

        val mnChipStep by rule {
            put("cursor", "pointer")
            put("color", textSecondary)
            put("font-size", "14px")
            put("font-weight", "bold")
            put("padding", "0 3px")
            put("user-select", "none")
            put("transition", "color var(--klang-transition)")
            rule(":hover") { put("color", textPrimary) }
        }

        // ── Docs popup ────────────────────────────────────────────────────────

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
