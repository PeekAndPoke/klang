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
        // forced-colours modes, which drop shadows
        rule("&:focus-visible") {
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
            @keyframes kl-lamp-pulse {
                0%, 100% { filter: brightness(0.8); }
                50% { filter: brightness(1.3); }
            }
            @media (prefers-reduced-motion: reduce) {
                .$button.$beacon::after { animation: none; box-shadow: 0 0 0 2px var(--kl-lamp-try); }
                .$lamp.$lampTry { animation: none; }
            }
        """.trimIndent()
    )
}
