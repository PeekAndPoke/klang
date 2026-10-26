/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.peekandpoke.kraft.addons.styling.RawStyleSheet
import io.peekandpoke.kraft.addons.styling.StyleSheet

/**
 * The classes of the Klang controls (KLANG-00337): lamps, buttons, the riveted panel.
 *
 * Every rule reads the look's roles as `var(--kl-...)` ([io.peekandpoke.klang.ui.feel.KlangRole]) and nothing
 * else, so a changed role repaints without a re-render. Derived shades are mixed here from the roles.
 *
 * One light from above (KLANG-00207): a raised thing has a highlight on top and a shadow below, a pressed thing an
 * inset shadow, a printed label neither.
 *
 * The order of the rules is part of the design: a later rule of one class wins over an earlier one, so the finishes
 * come first, then the shapes, then pressed, disabled and later, then the beacon.
 */
object KlangControlStyles : StyleSheet("kl-controls") {

    // ── Lamps ─────────────────────────────────────────────────────────────────

    val lamp by rule {
        put("display", "inline-block")
        put("flex-shrink", "0")
        put("border-radius", "50%")
        put("background", "var(--kl-lamp-off)")
        put("box-shadow", "inset 0 1px 1px rgba(0,0,0,0.6)")
    }

    val lampOn by rule {
        put("background", "var(--kl-lamp-on)")
        put("box-shadow", "0 0 6px var(--kl-lamp-on)")
    }

    val lampTry by rule {
        put("background", "var(--kl-lamp-try)")
        put("box-shadow", "0 0 6px var(--kl-lamp-try)")
        put("animation", "kl-lamp-pulse 1.8s ease-in-out infinite")
    }

    val lampWarning by rule {
        put("background", "var(--kl-lamp-warning)")
        put("box-shadow", "0 0 6px var(--kl-lamp-warning)")
    }

    val lampCritical by rule {
        put("background", "var(--kl-lamp-critical)")
        put("box-shadow", "0 0 6px var(--kl-lamp-critical)")
    }

    // ── Buttons: the base ─────────────────────────────────────────────────────

    val button by rule {
        put("display", "inline-flex")
        put("align-items", "center")
        put("justify-content", "center")
        put("gap", "8px")
        put("box-sizing", "border-box")
        put("height", "34px")
        put("padding", "0 14px")
        put("margin", "0")
        put("border", "1px solid var(--kl-edge)")
        put("border-radius", "var(--kl-radius)")
        put("font-family", "inherit")
        put("font-size", "13px")
        put("font-weight", "700")
        put("line-height", "1")
        put("white-space", "nowrap")
        put("cursor", "pointer")
        put("transition", "filter 120ms ease, box-shadow 120ms ease")

        rule("&:hover") {
            put("filter", "brightness(1.08)")
        }

        rule("&:focus-visible") {
            put("outline", "2px solid var(--kl-focus)")
            put("outline-offset", "2px")
        }

        // Fomantic gives every icon a right margin; on a button that would push it off centre
        rule("& > i.icon") {
            put("margin", "0")
        }
    }

    // ── Buttons: finish and surface ───────────────────────────────────────────

    val darkFlat by rule {
        put("color", "var(--kl-ink-strong)")
        put("background", "linear-gradient(180deg, var(--kl-cap-light), var(--kl-cap-dark))")
        put("box-shadow", "0 2px 3px rgba(0,0,0,0.6), inset 0 1px 0 rgba(255,255,255,0.1)")
    }

    val darkDomed by rule {
        put("color", "var(--kl-ink-strong)")
        put("border-radius", "var(--kl-radius-domed)")
        put(
            "background",
            "radial-gradient(ellipse 120% 90% at 50% 10%, var(--kl-cap-light) 0%," +
                    " color-mix(in srgb, var(--kl-cap-light), var(--kl-cap-dark)) 55%, var(--kl-cap-dark) 100%)"
        )
        put("box-shadow", "0 3px 5px rgba(0,0,0,0.7), inset 0 1px 0 rgba(255,255,255,0.18)")
    }

    val silverFlat by rule {
        put("color", "var(--kl-metal-ink)")
        put(
            "background",
            "linear-gradient(180deg, var(--kl-metal-light) 0%, var(--kl-metal-mid) 48%," +
                    " color-mix(in srgb, var(--kl-metal-mid), var(--kl-metal-dark) 30%) 52%," +
                    " color-mix(in srgb, var(--kl-metal-light), var(--kl-metal-mid)) 100%)"
        )
        put("box-shadow", "0 2px 4px rgba(0,0,0,0.6), inset 0 1px 0 rgba(255,255,255,0.9)")
    }

    val silverDomed by rule {
        put("color", "var(--kl-metal-ink)")
        put("border-radius", "var(--kl-radius-domed)")
        put(
            "background",
            "radial-gradient(ellipse 120% 90% at 45% 15%, var(--kl-metal-light) 0%, var(--kl-metal-mid) 50%," +
                    " var(--kl-metal-dark) 100%)"
        )
        put("box-shadow", "0 3px 5px rgba(0,0,0,0.7), inset 0 1px 0 rgba(255,255,255,0.7)")
    }

    // ── Buttons: shape ────────────────────────────────────────────────────────

    val pill by rule {
        put("border-radius", "999px")
    }

    val round by rule {
        put("width", "34px")
        put("padding", "0")
        put("border-radius", "50%")
    }

    /** A tall button: a drawer's entry, a swatch button. Its lamp sits at the top left. */
    val tile by rule {
        put("position", "relative")
        put("height", "auto")
        put("min-height", "52px")
        put("flex-direction", "column")
        put("align-items", "center")
        put("gap", "4px")
        put("padding", "8px 10px")
        put("white-space", "normal")
        put("line-height", "1.2")

        rule("& > span[data-lamp]") {
            put("position", "absolute")
            put("left", "10px")
            put("top", "13px")
        }
    }

    /** A tile with a lamp leaves room for it on the left. */
    val tileWithLamp by rule {
        put("padding-left", "24px")
        put("align-items", "flex-start")
        put("text-align", "left")
    }

    // ── Buttons: state ────────────────────────────────────────────────────────

    val pressedDark by rule {
        put(
            "background",
            "linear-gradient(180deg, var(--kl-cap-dark), color-mix(in srgb, var(--kl-cap-dark), var(--kl-cap-light) 25%))"
        )
        put("box-shadow", "inset 0 2px 5px rgba(0,0,0,0.8)")
    }

    val pressedSilver by rule {
        put("background", "linear-gradient(180deg, var(--kl-metal-dark), var(--kl-metal-mid))")
        put("box-shadow", "inset 0 2px 5px rgba(0,0,0,0.6)")
    }

    val disabled by rule {
        put("opacity", "0.45")
        put("cursor", "default")
        put("box-shadow", "none")

        rule("&:hover") {
            put("filter", "none")
        }
    }

    /** Not there yet ("with the backend"): a dashed outline where the button will be, nothing to press. */
    val later by rule {
        put("color", "var(--kl-ink-label)")
        put("background", "transparent")
        put("border", "1px dashed var(--kl-ink-label)")
        put("box-shadow", "none")
        put("cursor", "default")

        rule("&:hover") {
            put("filter", "none")
        }
    }

    /**
     * "Try me": the trail's pulse around a control (KLANG-00334, the first-visit trail). The pulse is a ring of its
     * own (`::after`), so the cap keeps its own shadows, raised or pressed.
     */
    val beacon by rule {
        put("position", "relative")

        rule("&::after") {
            put("content", "\"\"")
            put("position", "absolute")
            put("inset", "-1px")
            put("border-radius", "inherit")
            put("pointer-events", "none")
            put("animation", "kl-beacon 1.8s ease-in-out infinite")
        }
    }

    // ── The riveted panel ─────────────────────────────────────────────────────

    val panel by rule {
        put("position", "relative")
        put("box-sizing", "border-box")
        put("padding", "20px 18px 18px")
        put("color", "var(--kl-ink-text)")
        put(
            "background",
            "linear-gradient(180deg, color-mix(in srgb, var(--kl-ground-panel), white 6%) 0%, var(--kl-ground-panel) 40%," +
                    " color-mix(in srgb, var(--kl-ground-panel), black 15%) 100%)"
        )
        put("border", "1px solid var(--kl-edge)")
        put("border-radius", "4px")
        put("box-shadow", "0 14px 40px rgba(0,0,0,0.6), inset 0 1px 0 rgba(255,255,255,0.09)")
    }

    /** A panel holding a list (a menu): tighter, its entries run to the edges. */
    val panelTight by rule {
        put("padding", "14px 0 12px")
    }

    val rivet by rule {
        put("position", "absolute")
        put("width", "6px")
        put("height", "6px")
        put("border-radius", "50%")
        put("background", "radial-gradient(circle at 35% 35%, var(--kl-metal-dark), var(--kl-cap-dark))")
        put("pointer-events", "none")
    }

    val rivetTopLeft by rule {
        put("top", "6px")
        put("left", "6px")
    }

    val rivetTopRight by rule {
        put("top", "6px")
        put("right", "6px")
    }

    val rivetBottomLeft by rule {
        put("bottom", "6px")
        put("left", "6px")
    }

    val rivetBottomRight by rule {
        put("bottom", "6px")
        put("right", "6px")
    }

    // ── Card and caption ──────────────────────────────────────────────────────

    /** A flat plate on the ground: no rivets (rivets mean a popup), no shadow of its own. */
    val card by rule {
        put("display", "flex")
        put("flex-direction", "column")
        put("gap", "12px")
        put("box-sizing", "border-box")
        put("padding", "18px")
        put("color", "var(--kl-ink-text)")
        put("background", "var(--kl-ground-app)")
        put("border", "1px solid var(--kl-edge)")
        put("border-radius", "4px")
    }

    val captionTitle by rule {
        put("font-size", "13px")
        put("font-weight", "700")
        put("color", "var(--kl-ink-strong)")
    }

    val captionNote by rule {
        put("font-family", "monospace")
        put("font-size", "11px")
        put("color", "var(--kl-ink-label)")
    }

    /** A silkscreen heading: small capitals, spaced, printed on the panel. */
    val heading by rule {
        put("font-family", "monospace")
        put("font-size", "11px")
        put("letter-spacing", "0.18em")
        put("text-transform", "uppercase")
        put("color", "var(--kl-ink-label)")
    }

    // ── Swatch, search field, key cap ─────────────────────────────────────────

    val swatch by rule {
        put("width", "16px")
        put("height", "16px")
        put("flex-shrink", "0")
        put("border-radius", "50%")
        put("border", "1px solid var(--kl-edge)")
    }

    /** A swatch where there is none yet ("Custom" before it exists). */
    val swatchEmpty by rule {
        put("border", "1px dashed var(--kl-ink-label)")
    }

    /** A field cut into the panel: the search icon, the input, a key cap. */
    val searchField by rule {
        put("display", "flex")
        put("align-items", "center")
        put("gap", "8px")
        put("box-sizing", "border-box")
        put("height", "36px")
        put("padding", "0 10px")
        put("color", "var(--kl-ink-label)")
        put("background", "var(--kl-ground-sunken)")
        put("border", "1px solid var(--kl-edge)")
        put("border-radius", "var(--kl-radius)")
        put("box-shadow", "inset 0 2px 4px rgba(0,0,0,0.7)")

        rule("&:focus-within") {
            put("box-shadow", "inset 0 2px 4px rgba(0,0,0,0.7), 0 0 0 1.5px var(--kl-focus)")
        }
    }

    val searchInput by rule {
        put("flex", "1")
        put("min-width", "0")
        put("background", "transparent")
        put("border", "none")
        put("outline", "none")
        put("color", "var(--kl-ink-text)")
        put("font-family", "inherit")
        put("font-size", "13px")
    }

    /** A key cap, printed: the hint of a key that does the same. */
    val keyCap by rule {
        put("font-family", "monospace")
        put("font-size", "10px")
        put("line-height", "1")
        put("padding", "2px 5px")
        put("color", "var(--kl-ink-label)")
        put("border", "1px solid color-mix(in srgb, var(--kl-ink-label) 40%, transparent)")
        put("border-bottom-width", "2px")
        put("border-radius", "3px")
        put("white-space", "nowrap")
    }

    // ── Drawer ────────────────────────────────────────────────────────────────

    val drawerHeader by rule {
        put("display", "flex")
        put("align-items", "center")
        put("justify-content", "space-between")
        put("gap", "8px")
        put("margin-bottom", "12px")
    }

    val drawerGrid by rule {
        put("display", "grid")
        put("grid-template-columns", "repeat(2, minmax(0, 1fr))")
        put("gap", "8px")
        put("margin-top", "12px")
    }

    val drawerSub by rule {
        put("font-size", "11px")
        put("font-weight", "400")
        put("color", "var(--kl-ink-label)")
    }

    val drawerFooter by rule {
        put("margin-top", "14px")
        put("padding-top", "12px")
        put("border-top", "1px solid var(--kl-ground-deep)")
    }

    /** The look picker's row: the presets as equal tiles. */
    val lookRow by rule {
        put("display", "grid")
        put("grid-template-columns", "repeat(4, minmax(0, 1fr))")
        put("gap", "6px")
        put("margin-top", "8px")
    }

    // ── Dialog ────────────────────────────────────────────────────────────────

    val dialogTitle by rule {
        put("font-size", "16px")
        put("font-weight", "700")
        put("color", "var(--kl-ink-strong)")
    }

    val dialogBody by rule {
        put("margin-top", "8px")
        put("line-height", "20px")
    }

    /**
     * The key row at the panel's foot, edge to edge, as the button row of a device (maintainer, 2026-10-25): a sunken
     * strip, the keys sharing its width, so no key floats left or right of an empty space.
     */
    val dialogKeys by rule {
        put("display", "grid")
        put("grid-auto-columns", "minmax(0, 1fr)")
        put("grid-auto-flow", "column")
        put("gap", "10px")
        put("margin", "18px -18px -18px")
        put("padding", "12px 18px")
        put("background", "color-mix(in srgb, var(--kl-ground-sunken) 70%, transparent)")
        put("border-top", "1px solid var(--kl-edge)")
        // The panel's inner corner: its 4 px radius less its 1 px border
        put("border-radius", "0 0 3px 3px")
        put("box-shadow", "inset 0 2px 4px rgba(0,0,0,0.5)")
    }

    /** One key of the row: holds its button at the full width of its share; a long verb wraps inside it. */
    val dialogKey by rule {
        put("display", "flex")
        put("flex-direction", "column")
        put("align-items", "stretch")

        rule("& > button") {
            put("height", "auto")
            put("min-height", "34px")
            put("padding-top", "6px")
            put("padding-bottom", "6px")
            put("white-space", "normal")
            put("text-align", "center")
            put("line-height", "1.2")
        }
    }

    // ── Menu ──────────────────────────────────────────────────────────────────

    val menuItem by rule {
        put("display", "flex")
        put("align-items", "center")
        put("justify-content", "space-between")
        put("gap", "16px")
        put("width", "100%")
        put("box-sizing", "border-box")
        put("padding", "7px 14px")
        put("border", "none")
        put("background", "transparent")
        put("color", "var(--kl-ink-text)")
        put("font-family", "inherit")
        put("font-size", "13px")
        put("text-align", "left")
        put("cursor", "pointer")
        put("outline", "none")

        rule("&:hover, &:focus") {
            put("background", "color-mix(in srgb, var(--kl-accent) 18%, transparent)")
            put("color", "var(--kl-ink-strong)")
        }

        rule("&:disabled") {
            put("color", "var(--kl-ink-label)")
            put("background", "transparent")
            put("cursor", "default")
        }
    }

    val menuHint by rule {
        put("font-family", "monospace")
        put("font-size", "11px")
        put("color", "var(--kl-ink-label)")
    }

    // ── Tool window ───────────────────────────────────────────────────────────

    /** Fills the place it is given (a fly-in slot, a docked column) along its parent's flex row. */
    val toolWindow by rule {
        put("display", "flex")
        put("flex-direction", "column")
        put("flex", "1")
        put("box-sizing", "border-box")
        put("min-width", "0")
        put("min-height", "0")
        put("color", "var(--kl-ink-text)")
        put("background", "var(--kl-ground-panel)")
        put("border-left", "1px solid var(--kl-edge)")
    }

    /** Flying in over the editor: a shadow to the left, rounded where it meets the editor. */
    val toolWindowFloating by rule {
        put("border-top-left-radius", "6px")
        put("box-shadow", "-18px 0 40px rgba(0,0,0,0.55), inset 1px 0 0 rgba(255,255,255,0.06)")
        put("animation", "kl-fly-in 0.18s ease-out")
    }

    val toolWindowHeader by rule {
        put("display", "flex")
        put("align-items", "center")
        put("gap", "8px")
        put("flex-shrink", "0")
        put("height", "44px")
        put("padding", "0 8px 0 16px")
        put("border-bottom", "1px solid var(--kl-ground-deep)")
    }

    val toolWindowTitle by rule {
        put("font-family", "monospace")
        put("font-size", "11px")
        put("letter-spacing", "0.18em")
        put("text-transform", "uppercase")
        put("color", "var(--kl-ink-strong)")
    }

    val toolWindowChip by rule {
        put("font-size", "11px")
        put("color", "var(--kl-ink-label)")
        put("border", "1px solid color-mix(in srgb, var(--kl-ink-label) 40%, transparent)")
        put("border-radius", "10px")
        put("padding", "2px 8px")
        put("white-space", "nowrap")
    }

    /** A small icon button: a tool window's pin and close, the tool strip's toggles. Printed, not raised; on, it sinks in. */
    val iconToggle by rule {
        put("display", "inline-flex")
        put("align-items", "center")
        put("justify-content", "center")
        put("width", "28px")
        put("height", "28px")
        put("padding", "0")
        put("border", "1px solid transparent")
        put("border-radius", "var(--kl-radius)")
        put("background", "transparent")
        put("color", "var(--kl-ink-label)")
        put("cursor", "pointer")

        rule("&:hover") {
            put("color", "var(--kl-ink-strong)")
        }

        rule("&:focus-visible") {
            put("outline", "none")
            put("box-shadow", "0 0 0 1.5px var(--kl-focus)")
        }

        rule("& > i.icon") {
            put("margin", "0")
        }
    }

    val iconToggleOn by rule {
        put("color", "var(--kl-lamp-on)")
        put("background", "var(--kl-ground-sunken)")
        put("border-color", "var(--kl-edge)")
        put("box-shadow", "inset 0 2px 4px rgba(0,0,0,0.8)")
    }

    /** The tool strip's size of it. */
    val iconToggleLarge by rule {
        put("width", "36px")
        put("height", "36px")
        put("font-size", "16px")
    }

    val toolWindowBody by rule {
        put("flex", "1")
        put("min-height", "0")
        put("overflow", "auto")
        put("padding", "16px")
    }

    // ── The Motor's menu button ───────────────────────────────────────────────

    /** The nine dots on the menu button; while its drawer is open they are lit, the button pressed. */
    val menuButtonDots by rule {
        put("display", "block")
        put("fill", "currentColor")
    }

    val menuButtonOpen by rule {
        put("color", "var(--kl-lamp-on)")
    }

    // ── The workbench ─────────────────────────────────────────────────────────

    val workbench by rule {
        put("display", "flex")
        put("width", "100%")
        put("height", "100%")
        put("overflow", "hidden")
        put("color", "var(--kl-ink-text)")
        put("background", "var(--kl-ground-app)")
    }

    /** The column on the left: its content scrolls, the Motor under it never moves (KLANG-00334 item 25). */
    val workbenchColumn by rule {
        put("position", "relative")
        put("display", "flex")
        put("flex-direction", "column")
        put("width", "320px")
        put("flex-shrink", "0")
        put("background", "var(--kl-ground-deep)")
        put("border-right", "1px solid var(--kl-edge)")
    }

    /** The column above the Motor: its scroller, and the drawer laid over it, its foot just above the Motor. */
    val workbenchColumnArea by rule {
        put("position", "relative")
        put("display", "flex")
        put("flex-direction", "column")
        put("flex", "1")
        put("min-height", "0")
    }

    val workbenchColumnScroll by rule {
        put("flex", "1")
        put("min-height", "0")
        put("overflow-y", "auto")
    }

    val workbenchMotor by rule {
        put("flex-shrink", "0")
        put("border-top", "1px solid var(--kl-edge)")
    }

    /** Over the column, just above the Motor; taller than the column, it scrolls, whatever the Motor's height. */
    val workbenchDrawer by rule {
        put("position", "absolute")
        put("left", "10px")
        put("bottom", "8px")
        put("width", "300px")
        put("max-height", "calc(100% - 16px)")
        put("overflow-y", "auto")
        put("z-index", "20")

        // Holding the focus only when it has no control of its own: a frame, not a control, so no ring
        rule("&:focus") {
            put("outline", "none")
        }
    }

    val workbenchCentre by rule {
        put("position", "relative")
        put("display", "flex")
        put("flex-direction", "column")
        put("flex", "1")
        put("min-width", "0")
    }

    /**
     * The page's area: it scrolls up and down; sideways it clips, as the app's old layout did, so a glow or a shadow
     * poking past the edge never adds a second scrollbar beside the editor's own.
     */
    val workbenchContent by rule {
        put("flex", "1")
        put("min-height", "0")
        put("overflow-x", "hidden")
        put("overflow-y", "auto")
    }

    val workbenchBottom by rule {
        put("display", "flex")
        put("flex-direction", "column")
        put("flex-shrink", "0")
        put("height", "240px")
        put("background", "var(--kl-ground-panel)")
        put("border-top", "1px solid var(--kl-edge)")
    }

    val workbenchBottomHeader by rule {
        put("display", "flex")
        put("align-items", "center")
        put("flex-shrink", "0")
        put("height", "36px")
        put("padding", "0 16px")
        put("border-bottom", "1px solid var(--kl-ground-deep)")
    }

    val workbenchBottomBody by rule {
        put("flex", "1")
        put("min-height", "0")
        put("overflow", "auto")
        put("padding", "14px 16px")
    }

    /** A tool window flying in over the content, from the strip's side. */
    val workbenchFloating by rule {
        put("position", "absolute")
        put("top", "0")
        put("right", "0")
        put("bottom", "0")
        put("width", "380px")
        put("z-index", "15")
        put("display", "flex")

        // Holding the focus as a frame, not a control: no ring
        rule("&:focus") {
            put("outline", "none")
        }
    }

    /** A pinned tool window, docked between the content and the strip. */
    val workbenchPinned by rule {
        put("display", "flex")
        put("width", "340px")
        put("flex-shrink", "0")

        // Holding the focus as a frame, not a control: no ring
        rule("&:focus") {
            put("outline", "none")
        }
    }

    val workbenchStrip by rule {
        put("display", "flex")
        put("flex-direction", "column")
        put("align-items", "center")
        put("gap", "6px")
        put("width", "48px")
        put("flex-shrink", "0")
        put("padding-top", "12px")
        put("background", "var(--kl-ground-deep)")
        put("border-left", "1px solid var(--kl-edge)")
    }

    /** The maker's mark at the strip's foot, written upwards (KLANG-00334 items 22, 23). */
    val workbenchMark by rule {
        put("margin-top", "auto")
        put("padding-bottom", "28px")

        rule("& > span") {
            put("display", "block")
            put("writing-mode", "vertical-rl")
            put("transform", "rotate(180deg)")
            put("font-family", "monospace")
            put("font-weight", "700")
            put("font-size", "20px")
            put("letter-spacing", "0.12em")
            put("color", "var(--kl-ink-strong)")
            put("text-shadow", "0 0 5px var(--kl-edge)")
            put("white-space", "nowrap")
        }
    }

    // ── Gauges ────────────────────────────────────────────────────────────────

    /** The rim: silver, lit from above. The glow's blur comes per gauge as `--gauge-blur`, its size times a share. */
    val gauge by rule {
        put("position", "relative")
        put("display", "inline-block")
        put("flex-shrink", "0")
        put("border-radius", "50%")
        put("background", "linear-gradient(180deg, var(--kl-metal-light), var(--kl-metal-mid) 55%, var(--kl-metal-dark))")
        put("box-shadow", "0 2px 4px rgba(0,0,0,0.6)")
    }

    val gaugeOnline by rule {
        put(
            "box-shadow",
            "0 2px 4px rgba(0,0,0,0.6), 0 0 var(--gauge-blur) color-mix(in srgb, var(--kl-gauge-glow) 60%, transparent)"
        )
    }

    val gaugeHot by rule {
        put(
            "box-shadow",
            "0 2px 4px rgba(0,0,0,0.6), 0 0 var(--gauge-blur) color-mix(in srgb, var(--kl-lamp-critical) 70%, transparent)"
        )
    }

    val gaugeFace by rule {
        put("position", "absolute")
        put("inset", "4%")
        put("border-radius", "50%")
        put(
            "background",
            "radial-gradient(circle at 50% 35%, color-mix(in srgb, var(--kl-ground-sunken), white 7%) 0%," +
                    " var(--kl-ground-sunken) 70%)"
        )
        put("box-shadow", "inset 0 2px 4px rgba(0,0,0,0.8)")
    }

    val gaugeScale by rule {
        put("position", "absolute")
        put("inset", "0")
        put("width", "100%")
        put("height", "100%")
        put("pointer-events", "none")
    }

    val gaugeTick by rule {
        put("stroke", "var(--kl-gauge-tick)")
    }

    val gaugeTickHot by rule {
        put("stroke", "var(--kl-lamp-critical)")
    }

    val gaugeTrack by rule {
        put("fill", "none")
        put("stroke", "color-mix(in srgb, var(--kl-gauge-tick) 25%, transparent)")
        put("stroke-linecap", "round")
    }

    val gaugeArc by rule {
        put("fill", "none")
        put("stroke", "var(--kl-gauge-arc)")
        put("stroke-linecap", "round")
    }

    val gaugeArcHot by rule {
        put("stroke", "var(--kl-lamp-critical)")
    }

    val gaugeNeedle by rule {
        put("stroke", "var(--kl-ink-strong)")
        put("stroke-linecap", "round")
    }

    val gaugeHub by rule {
        put("fill", "var(--kl-metal-mid)")
        put("stroke", "var(--kl-edge)")
    }

    /** The icon or the text label, between the arc and the hub, clear of both (maintainer, 2026-10-25). Dim while offline. */
    val gaugeMark by rule {
        put("position", "absolute")
        put("left", "50%")
        put("top", "32%")
        put("transform", "translate(-50%, -50%)")
        put("margin", "0")
        put("color", "var(--kl-ink-label)")
        put("font-weight", "700")
        put("letter-spacing", "0.08em")
        put("white-space", "nowrap")
    }

    val gaugeMarkLit by rule {
        put("color", "var(--kl-gauge-arc)")
        put("text-shadow", "0 0 7px color-mix(in srgb, var(--kl-gauge-arc) 60%, transparent)")
    }

    val gaugeMarkHot by rule {
        put("color", "var(--kl-lamp-critical)")
        put("text-shadow", "0 0 7px color-mix(in srgb, var(--kl-lamp-critical) 60%, transparent)")
    }

    val gaugeValue by rule {
        put("position", "absolute")
        put("left", "0")
        put("right", "0")
        put("bottom", "20%")
        put("text-align", "center")
        put("font-family", "monospace")
        put("color", "var(--kl-ink-text)")
    }

    // ── Knobs ─────────────────────────────────────────────────────────────────

    /** Tight, to save space (maintainer, 2026-10-25): the label sits against the dial, the value right under the dial. */
    val knob by rule {
        put("display", "inline-flex")
        put("flex-direction", "column")
        put("align-items", "center")
        put("gap", "4px")
        put("user-select", "none")
    }

    /** What sits under the dial: the label when it goes below, then the value, 2 px apart. */
    val knobCaption by rule {
        put("display", "flex")
        put("flex-direction", "column")
        put("align-items", "center")
        put("gap", "2px")
    }

    val knobLabel by rule {
        put("font-size", "10px")
        put("line-height", "1")
        put("font-weight", "700")
        put("letter-spacing", "0.14em")
        put("color", "var(--kl-ink-text)")
    }

    val knobValue by rule {
        put("font-family", "monospace")
        put("font-size", "11px")
        put("line-height", "1")
        put("color", "var(--kl-ink-label)")
    }

    /** The part that is dragged and takes the keys; a finger's drag turns it instead of scrolling the page. */
    val knobDial by rule {
        put("position", "relative")
        put("flex-shrink", "0")
        put("border-radius", "50%")
        put("cursor", "ns-resize")
        put("touch-action", "none")
        put("outline", "none")

        // A thin ring on the dial's edge, clear of the label (maintainer, 2026-10-25). A shadow ring, because Chrome
        // draws a round outline without anti-aliasing (it looked pixelated); the transparent outline still shows in
        // forced-colours modes, which drop shadows. On any focus, not only `:focus-visible`: the focused knob is the
        // one the arrows turn, and the browser's `:focus-visible` hid the ring after a click that followed a button's
        // (the maintainer, 2026-10-25)
        rule("&:focus") {
            put("box-shadow", "0 0 0 1.5px var(--kl-focus)")
            put("outline", "1px solid transparent")
        }
    }

    val knobTrack by rule {
        put("fill", "none")
        put("stroke", "color-mix(in srgb, var(--kl-ink-label) 30%, transparent)")
        put("stroke-linecap", "round")
    }

    val knobArc by rule {
        put("fill", "none")
        put("stroke", "var(--kl-accent-muted)")
        put("stroke-linecap", "round")
    }

    /** The cap, domed and lit from above; its inset comes per knob (with or without the ring). */
    val knobCap by rule {
        put("position", "absolute")
        put("border-radius", "50%")
        put("border", "1px solid var(--kl-edge)")
        put("box-shadow", "0 3px 5px rgba(0,0,0,0.7), inset 0 1px 0 rgba(255,255,255,0.18)")
    }

    val knobCapDark by rule {
        put("background", "radial-gradient(circle at 42% 30%, var(--kl-cap-light) 0%, var(--kl-cap-dark) 100%)")
    }

    val knobCapSilver by rule {
        put(
            "background",
            "radial-gradient(circle at 42% 28%, var(--kl-metal-light) 0%, var(--kl-metal-mid) 45%, var(--kl-metal-dark) 100%)"
        )
    }

    /** The turning layer; its rotation comes per knob. */
    val knobTurn by rule {
        put("position", "absolute")
        put("inset", "0")
        put("pointer-events", "none")
    }

    /** The pointer line, printed in the cap's ink. */
    val knobPointer by rule {
        put("position", "absolute")
        put("left", "50%")
        put("top", "8%")
        put("width", "8%")
        put("height", "34%")
        put("transform", "translateX(-50%)")
        put("border-radius", "2px")
        put("background", "var(--kl-ink-strong)")
    }

    /** The pointer as a dot at the rim, when an icon sits on the cap. */
    val knobDot by rule {
        put("position", "absolute")
        put("left", "50%")
        put("top", "7%")
        put("width", "13%")
        put("height", "13%")
        put("transform", "translateX(-50%)")
        put("border-radius", "50%")
        put("background", "var(--kl-ink-strong)")
    }

    val knobIcon by rule {
        put("position", "absolute")
        put("left", "50%")
        put("top", "50%")
        put("transform", "translate(-50%, -50%)")
        put("margin", "0")
        put("color", "var(--kl-ink-strong)")
    }

    /** The silver cap's ink for its pointer or its dot. */
    val knobMarkSilver by rule {
        put("background", "var(--kl-metal-ink)")
    }

    /** The silver cap's ink for its icon. */
    val knobIconSilver by rule {
        put("color", "var(--kl-metal-ink)")
    }

    // ── Motion ────────────────────────────────────────────────────────────────

    /**
     * The pulses, and their stillness under "reduce motion": a lit ring instead of a pulsing one.
     *
     * This sheet mounts before the main one, which then wins a tie, so the stillness rules carry one class more than
     * the rules they override.
     */
    @Suppress("unused")
    private val motion = RawStyleSheet(
        css = """
            @keyframes kl-beacon {
                0%, 100% { box-shadow: 0 0 0 0 transparent; }
                50% {
                    box-shadow: 0 0 0 5px color-mix(in srgb, var(--kl-lamp-try) 35%, transparent),
                        0 0 18px color-mix(in srgb, var(--kl-lamp-try) 65%, transparent);
                }
            }
            @keyframes kl-fly-in {
                from { transform: translateX(60px); opacity: 0; }
                to { transform: none; opacity: 1; }
            }
            @keyframes kl-lamp-pulse {
                0%, 100% { filter: brightness(0.8); }
                50% { filter: brightness(1.3); }
            }
            @media (prefers-reduced-motion: reduce) {
                .$button.$beacon::after { animation: none; box-shadow: 0 0 0 2px var(--kl-lamp-try); }
                .$lamp.$lampTry { animation: none; }
                .$toolWindow.$toolWindowFloating { animation: none; }
            }
        """.trimIndent()
    )
}
