/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.feel

/**
 * One role of the Klang look: a value every element of one kind reads (KLANG-00337).
 *
 * Each role reaches the page as the CSS variable [cssVar]; the component styles read only these variables, so a
 * changed role repaints without a re-render. Derived shades (the silver's break, a domed cap's highlight, a pressed
 * cap) are computed in the styles from these roles, never stored as roles of their own.
 *
 * A role has a [kind], and a value a theme layer brings must fit it ([accepts]): a shared theme is data, never CSS
 * text or a URL. A role may [follows] another: while no layer sets it, it takes that role's value, so a look that
 * recolours the metal recolours the scope's trace with it.
 */
enum class KlangRole(
    val cssVar: String,
    val kind: Kind,
    val default: String,
    /** The role whose value this one takes while no layer sets it; null for its own [default]. */
    val follows: KlangRole? = null,
) {

    // ── Ground: the surfaces things sit on ────────────────────────────────────

    /** The app's background. */
    GroundApp(cssVar = "--kl-ground-app", kind = Kind.Color, default = "#191C22"),

    /** The deep ground of the side column and the tool strip. */
    GroundDeep(cssVar = "--kl-ground-deep", kind = Kind.Color, default = "#12131c"),

    /** Panels, tool windows, popups. */
    GroundPanel(cssVar = "--kl-ground-panel", kind = Kind.Color, default = "#21252b"),

    /** Wells cut into a surface: fields, the editor, the scope, an LCD. */
    GroundSunken(cssVar = "--kl-ground-sunken", kind = Kind.Color, default = "#0e1014"),

    // ── Ink: text and print on the panel ──────────────────────────────────────

    /** Body text. */
    InkText(cssVar = "--kl-ink-text", kind = Kind.Color, default = "#D7DADF"),

    /** Headlines and the labels of controls. */
    InkStrong(cssVar = "--kl-ink-strong", kind = Kind.Color, default = "#ffffff"),

    /** Silkscreen labels, captions, secondary text. */
    InkLabel(cssVar = "--kl-ink-label", kind = Kind.Color, default = "#abb2bf"),

    // ── Edge and caps: raised dark controls ───────────────────────────────────

    /** The outline of raised things. */
    Edge(cssVar = "--kl-edge", kind = Kind.Color, default = "#000000"),

    /** A dark cap where the light from above hits it. */
    CapLight(cssVar = "--kl-cap-light", kind = Kind.Color, default = "#4a505b"),

    /** A dark cap where the light does not reach. */
    CapDark(cssVar = "--kl-cap-dark", kind = Kind.Color, default = "#1d2026"),

    // ── Metal: silver caps, bezels, rivets ────────────────────────────────────

    /** Metal where the light hits it. */
    MetalLight(cssVar = "--kl-metal-light", kind = Kind.Color, default = "#f2f4f7"),

    /** The body of the metal. */
    MetalMid(cssVar = "--kl-metal-mid", kind = Kind.Color, default = "#c9ced6"),

    /** Metal in shadow. */
    MetalDark(cssVar = "--kl-metal-dark", kind = Kind.Color, default = "#7d838d"),

    /** Text and icons printed on metal. */
    MetalInk(cssVar = "--kl-metal-ink", kind = Kind.Color, default = "#111111"),

    // ── Lamps ─────────────────────────────────────────────────────────────────

    /** A lamp that is off: the dark glass. */
    LampOff(cssVar = "--kl-lamp-off", kind = Kind.Color, default = "#3a3220"),

    /**
     * A lamp that is on: this thing is active or selected (a lit lamp, the pressed toggle, the open Motor button). A blue,
     * tuned to each look's metal (the maintainer, 2026-10-26: "The earlier blue was ok, but the blue-nes must be
     * adjusted per theme to match color-theoretically better"): here the accent blue, beside the nearly neutral silver;
     * a look with a warm metal takes the blue opposite its metal's hue (copper: azure, cream: a soft periwinkle).
     */
    LampOn(cssVar = "--kl-lamp-on", kind = Kind.Color, default = "#528bff"),

    /** "Try me": the one colour of the first-visit trail, used for nothing else. */
    LampTry(cssVar = "--kl-lamp-try", kind = Kind.Color, default = "#EBC773"),

    /** A warning. */
    LampWarning(cssVar = "--kl-lamp-warning", kind = Kind.Color, default = "#d19a66"),

    /** An error. */
    LampCritical(cssVar = "--kl-lamp-critical", kind = Kind.Color, default = "#e06c75"),

    // ── Gauges ────────────────────────────────────────────────────────────────

    /** A gauge's arc: how far the needle has come. */
    GaugeArc(cssVar = "--kl-gauge-arc", kind = Kind.Color, default = "#56b6c2"),

    /** A gauge's scale marks. */
    GaugeTick(cssVar = "--kl-gauge-tick", kind = Kind.Color, default = "#8a919e"),

    /** The glow around a gauge whose Motor is online. */
    GaugeGlow(cssVar = "--kl-gauge-glow", kind = Kind.Color, default = "#5c7cbc"),

    /**
     * The oscilloscope's trace in the Motor: the colour of the gauges' rims, [MetalMid] (the maintainer, 2026-10-26:
     * "make the Osci the same color as the outlines of the gauges", after blue and brass; "When the them is set to
     * copper the Osc should be copper like the gauges"). Its own role, so a look can set it apart from the metal.
     */
    ScopeTrace(cssVar = "--kl-scope-trace", kind = Kind.Color, default = MetalMid.default, follows = MetalMid),

    /**
     * A knob's arc, how far it is turned: material, the colour of the gauges' rims, [MetalMid] (the maintainer,
     * 2026-10-26: the knob's circle should match the gauges' outlines in every look, as it did in copper).
     */
    KnobArc(cssVar = "--kl-knob-arc", kind = Kind.Color, default = MetalMid.default, follows = MetalMid),

    /**
     * An LCD's digits (KLANG-00349, the RPM's readout): an instrument's reading, as the gauges' arc, [GaugeArc]. Its
     * own role, so a look can light its LCDs apart from the gauges.
     */
    LcdInk(cssVar = "--kl-lcd-ink", kind = Kind.Color, default = GaugeArc.default, follows = GaugeArc),

    // ── Accent ────────────────────────────────────────────────────────────────

    /** Selection, links. */
    Accent(cssVar = "--kl-accent", kind = Kind.Color, default = "#528bff"),

    /** The accent on large surfaces, where [Accent] is too loud: a selected row, the editor's frame. */
    AccentMuted(cssVar = "--kl-accent-muted", kind = Kind.Color, default = "#5c7cbc"),

    /** The keyboard focus ring. */
    Focus(cssVar = "--kl-focus", kind = Kind.Color, default = "#8fb0ff"),

    // ── Shape ─────────────────────────────────────────────────────────────────

    /** The corner of a flat control: a machined chamfer, never a pill (KLANG-00207). */
    Radius(cssVar = "--kl-radius", kind = Kind.Length, default = "3px"),

    /** The corner of a domed control. */
    RadiusDomed(cssVar = "--kl-radius-domed", kind = Kind.Length, default = "10px"),

    // ── Surface ───────────────────────────────────────────────────────────────

    /**
     * How strong the fine grain over the buttons and the knobs' caps is, from 0 (smooth) to 1 (the maintainer,
     * 2026-10-26: "maybe this noise should be a theme property as well?").
     */
    Grain(cssVar = "--kl-grain", kind = Kind.Amount, default = "0.16"),
    ;

    /** What a role's value is, and so which values a theme may give it. */
    enum class Kind {
        /** A hex colour: `#rgb`, `#rgba`, `#rrggbb` or `#rrggbbaa`. */
        Color,

        /** A length in px, 0 to 999, with an optional fraction: `3px`, `2.5px`. */
        Length,

        /** A share from 0 to 1, up to three decimals: `0`, `0.16`, `1`. */
        Amount,
    }

    /** True when [value] fits this role's [kind]; a theme layer drops a value that does not. */
    fun accepts(value: String): Boolean = when (kind) {
        Kind.Color -> HEX_COLOR.matches(value)
        Kind.Length -> PX_LENGTH.matches(value)
        Kind.Amount -> AMOUNT.matches(value)
    }

    companion object {
        private val HEX_COLOR = Regex("^#([0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
        private val PX_LENGTH = Regex("^[0-9]{1,3}(\\.[0-9]{1,3})?px$")
        private val AMOUNT = Regex("^(0(\\.[0-9]{1,3})?|1(\\.0{1,3})?)$")

        private val byCssVar: Map<String, KlangRole> = entries.associateBy { it.cssVar }

        /** The role behind a CSS variable name, for code that knows roles by name only (a theme file, a script). */
        fun ofCssVar(cssVar: String): KlangRole? = byCssVar[cssVar]
    }
}
