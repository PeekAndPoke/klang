/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.feel

/**
 * The roles as the look editor shows them (KLANG-00382): a card per element, the cards in groups, every role on exactly
 * one card (the maintainer, 2026-10-28: "There should be one for each element. Nicely grouped, tiled and centered in
 * the tiles").
 */
enum class KlangRoleGroup(val title: String, val elements: List<Element>) {

    Surfaces(
        title = "Surfaces and type",
        elements = listOf(
            Element(
                id = "grounds",
                title = "Grounds",
                roles = listOf(
                    KlangRole.GroundApp to "App",
                    KlangRole.GroundDeep to "Deep",
                    KlangRole.GroundPanel to "Panel",
                    KlangRole.GroundRaised to "Raised",
                    KlangRole.GroundSunken to "Sunken well",
                ),
            ),
            Element(
                id = "ink",
                title = "Ink",
                roles = listOf(KlangRole.InkText to "Text", KlangRole.InkStrong to "Strong", KlangRole.InkLabel to "Labels"),
            ),
            Element(
                id = "corners",
                title = "Corners",
                roles = listOf(KlangRole.Radius to "Flat corner", KlangRole.RadiusDomed to "Domed corner"),
            ),
            Element(
                id = "texture",
                title = "Texture",
                roles = listOf(KlangRole.Grain to "Grain", KlangRole.Brushed to "Brushed"),
            ),
        ),
    ),

    Metal(
        title = "Metal and caps",
        elements = listOf(
            Element(
                id = "metal",
                title = "Metal",
                roles = listOf(
                    KlangRole.MetalLight to "Lit",
                    KlangRole.MetalMid to "Body",
                    KlangRole.MetalDark to "Shadow",
                    KlangRole.MetalInk to "Print",
                ),
            ),
            Element(
                id = "caps",
                title = "Caps and keys",
                roles = listOf(KlangRole.CapLight to "Lit", KlangRole.CapDark to "Shadow", KlangRole.Edge to "Edge"),
            ),
        ),
    ),

    Lamps(
        title = "Lamps, accent and focus",
        elements = listOf(
            Element(
                id = "lamps",
                title = "Lamps",
                roles = listOf(
                    KlangRole.LampOff to "Off",
                    KlangRole.LampOn to "On, selected",
                    KlangRole.LampTry to "Try me",
                    KlangRole.LampWarning to "Warning",
                    KlangRole.LampCritical to "Error",
                ),
            ),
            Element(
                id = "accent",
                title = "Accent",
                roles = listOf(KlangRole.Accent to "Accent", KlangRole.AccentMuted to "Muted"),
            ),
            Element(id = "scrollbar", title = "Scrollbar", roles = listOf(KlangRole.ScrollThumbHover to "Thumb, hovered")),
            Element(id = "focus", title = "Focus ring", roles = listOf(KlangRole.Focus to "Focus")),
        ),
    ),

    Instruments(
        title = "Instruments",
        elements = listOf(
            Element(
                id = "gauge",
                title = "Gauge",
                roles = listOf(KlangRole.GaugeArc to "Arc", KlangRole.GaugeTick to "Ticks", KlangRole.GaugeGlow to "Glow"),
            ),
            Element(id = "knob", title = "Knob", roles = listOf(KlangRole.KnobArc to "Arc")),
            Element(id = "scope", title = "Oscilloscope", roles = listOf(KlangRole.ScopeTrace to "String")),
            Element(id = "lcd", title = "LCD", roles = listOf(KlangRole.LcdInk to "Digits")),
        ),
    ),

    Spectrum(
        title = "The spectrum",
        elements = listOf(
            Element(
                id = "spectrum",
                title = "Spectrum, quiet to peak",
                roles = listOf(
                    KlangRole.SpectrumQuiet to "Quiet",
                    KlangRole.SpectrumLow to "Low",
                    KlangRole.SpectrumMid to "Mid",
                    KlangRole.SpectrumHot to "Hot",
                    KlangRole.SpectrumPeak to "Peak",
                ),
            ),
        ),
    ),
    ;

    /** One element of the look, a card: its [roles] with the short label each has on it. */
    data class Element(val id: String, val title: String, val roles: List<Pair<KlangRole, String>>)

    /** Every role of this group, in its cards' order. */
    val roles: List<KlangRole> get() = elements.flatMap { element -> element.roles.map { it.first } }
}
