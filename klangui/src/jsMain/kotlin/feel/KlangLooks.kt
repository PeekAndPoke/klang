/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.feel

/**
 * The built-in looks (KLANG-00339): the three the maintainer picked from the kitchen sink's samples (2026-10-25), and
 * Army, the first Cream kept when Cream became creamy, Forest, Sun and Asphalt (2026-10-28). A
 * custom look of the user's own comes later; the drawer shows it as "later" until then.
 */
object KlangLooks {

    val Default = KlangLookPreset(id = "default", name = "Default", values = emptyMap())

    val Copper = KlangLookPreset(
        id = "copper",
        name = "Copper",
        values = mapOf(
            // Grounds of its own, a reddish brown in the copper's hue, darker than the cream's and apart from the
            // default's blue-black (the maintainer, 2026-10-27: "a more distinct background color"); its keys' caps
            // and the fields' well in the same warmth; then all a step darker ("a bit darker, not much")
            KlangRole.GroundApp to "#171210",
            KlangRole.GroundDeep to "#1e1713",
            // The tool windows a greyish variant of the grounds, as the default's are (the maintainer, 2026-10-27)
            KlangRole.GroundPanel to "#27231f",
            KlangRole.GroundRaised to "#35302b",
            KlangRole.GroundSunken to "#110c0a",
            KlangRole.CapLight to "#5b4a40",
            KlangRole.CapDark to "#241b17",
            // Its inks warm as well: the default's blue-grey text "sticks out as not being part of the palette" (the
            // maintainer, 2026-10-27)
            KlangRole.InkText to "#e8d8cc",
            KlangRole.InkStrong to "#fff2e8",
            KlangRole.InkLabel to "#c2a593",
            KlangRole.MetalLight to "#f6d6bd",
            KlangRole.MetalMid to "#c98a5e",
            KlangRole.MetalDark to "#7a4a2c",
            KlangRole.MetalInk to "#1a0d05",
            KlangRole.AccentMuted to "#b0764c",
            // Selected in the blue opposite the copper (hue about 25, the azure about 204): warm and cold, a classic pair
            KlangRole.LampOn to "#4fa3d9",
            // The spectrum: Patina rising to glowing copper, white-hot at the top
            KlangRole.SpectrumQuiet to "#3f9a8c",
            KlangRole.SpectrumLow to "#6fae8f",
            KlangRole.SpectrumMid to "#c99a5e",
            KlangRole.SpectrumHot to "#d9733f",
            KlangRole.SpectrumPeak to "#ffcf9a",
        ),
    )

    /**
     * Coffee and cream (the maintainer, 2026-10-28: "make the cream theme really creamy! Currently it looks like an
     * "army" theme"): grounds of espresso, red-brown rather than olive, as bright as the first Cream's so the contrast
     * the maintainer tuned stays (2026-10-27: the panel's ground "too dark, there is no contrast to the editor"); metal
     * of vanilla and whipped cream; inks of ivory and latte; gauges in butterscotch, not the default's teal, which beside
     * khaki read military.
     */
    val Cream = KlangLookPreset(
        id = "cream",
        name = "Cream",
        values = mapOf(
            KlangRole.GroundApp to "#1f1a16",
            KlangRole.GroundDeep to "#28211c",
            // The tool windows a greyish variant of the grounds, as the default's are (the maintainer, 2026-10-27)
            KlangRole.GroundPanel to "#2e2925",
            KlangRole.GroundRaised to "#3c3631",
            KlangRole.GroundSunken to "#17120f",
            KlangRole.CapLight to "#6e5d50",
            KlangRole.CapDark to "#2b221c",
            KlangRole.InkText to "#f2e6d6",
            KlangRole.InkStrong to "#fffaf1",
            KlangRole.InkLabel to "#d2bea6",
            KlangRole.MetalLight to "#fff9ef",
            KlangRole.MetalMid to "#eedfc8",
            KlangRole.MetalDark to "#b59c80",
            KlangRole.MetalInk to "#2a1d12",
            KlangRole.GaugeArc to "#e2b27a",
            KlangRole.GaugeTick to "#ab9783",
            KlangRole.GaugeGlow to "#c49a70",
            KlangRole.AccentMuted to "#b8966f",
            // Selected in a soft periwinkle, the blue opposite the cream's caramel, muted as the cream is soft
            KlangRole.Accent to "#8fa6e6",
            KlangRole.LampOn to "#8099d6",
            // The spectrum: Ice-cream pastels: mint, pistachio, vanilla, peach, strawberry
            KlangRole.SpectrumQuiet to "#8fc9d9",
            KlangRole.SpectrumLow to "#a8d8a0",
            KlangRole.SpectrumMid to "#f2dd9a",
            KlangRole.SpectrumHot to "#f2b48a",
            KlangRole.SpectrumPeak to "#f29aa8",
        ),
    )

    /**
     * The first Cream, as it was until 2026-10-28: khaki metal on olive-brown grounds, which "looks like an "army"
     * theme" (the maintainer, 2026-10-28: "But we should maybe have an army theme"). Kept as it was, under its name.
     */
    val Army = KlangLookPreset(
        id = "army",
        name = "Army",
        values = mapOf(
            // The grounds lifted off the editor's black, warm (the maintainer, 2026-10-27: the panel's ground was "too
            // dark, there is no contrast to the editor"); the sunken one, the fields' well, in the cream's hue too, not
            // the default's blue-black ("too blueish")
            KlangRole.GroundApp to "#1d1b17",
            KlangRole.GroundDeep to "#25221c",
            // The tool windows a greyish variant of the grounds, as the default's are (the maintainer, 2026-10-27)
            KlangRole.GroundPanel to "#2b2a28",
            KlangRole.GroundRaised to "#383733",
            KlangRole.GroundSunken to "#15130f",
            // The keys' caps warm too, not the default's blue-grey
            KlangRole.CapLight to "#5a5344",
            KlangRole.CapDark to "#25221b",
            KlangRole.InkText to "#eadfc4",
            KlangRole.InkStrong to "#fff6dc",
            KlangRole.InkLabel to "#c6b996",
            KlangRole.MetalLight to "#fbf3dd",
            KlangRole.MetalMid to "#e3d5b0",
            KlangRole.MetalDark to "#a08f66",
            // Selected in the blue opposite the khaki (hue about 44, the periwinkle about 223), muted as the khaki is soft
            KlangRole.LampOn to "#8099d6",
            // The spectrum: Night vision: phosphor green, dark to bright
            KlangRole.SpectrumQuiet to "#2e5a2e",
            KlangRole.SpectrumLow to "#3f7a3a",
            KlangRole.SpectrumMid to "#5fa04a",
            KlangRole.SpectrumHot to "#8fd06a",
            KlangRole.SpectrumPeak to "#c8f59a",
        ),
    )

    /**
     * A forest (the maintainer, 2026-10-28): grounds of pine shade, metal of birch bark and lichen, gauges in fresh
     * leaves, a fern-green accent; selected in the sky blue seen through the canopy (a look's own blue, as each has).
     */
    val Forest = KlangLookPreset(
        id = "forest",
        name = "Forest",
        values = mapOf(
            KlangRole.GroundApp to "#121a15",
            KlangRole.GroundDeep to "#17211b",
            KlangRole.GroundPanel to "#222a25",
            KlangRole.GroundRaised to "#2f3732",
            KlangRole.GroundSunken to "#0c120e",
            KlangRole.CapLight to "#465a4b",
            KlangRole.CapDark to "#1a241d",
            KlangRole.InkText to "#dfe8dc",
            KlangRole.InkStrong to "#f4fbf0",
            KlangRole.InkLabel to "#a9bba6",
            KlangRole.MetalLight to "#eaf0e1",
            KlangRole.MetalMid to "#b7c4a4",
            KlangRole.MetalDark to "#6f7f5c",
            KlangRole.MetalInk to "#121a10",
            KlangRole.GaugeArc to "#86c97c",
            KlangRole.GaugeTick to "#8a9a86",
            KlangRole.GaugeGlow to "#4f8a5a",
            KlangRole.Accent to "#6cc48a",
            KlangRole.AccentMuted to "#4f7f5f",
            KlangRole.LampOn to "#6fb3d9",
            // The spectrum: Deep pine and moss to sunlit leaves, autumn at the top
            KlangRole.SpectrumQuiet to "#2f6f5a",
            KlangRole.SpectrumLow to "#4e9a5a",
            KlangRole.SpectrumMid to "#8cc45a",
            KlangRole.SpectrumHot to "#d4c45a",
            KlangRole.SpectrumPeak to "#f2a65a",
        ),
    )

    /**
     * The sun (the maintainer, 2026-10-28): warm brown grounds, gold metal, gauges in the sun's orange, a sunset-orange
     * accent (not gold: gold is the first-visit trail's lamp, used for nothing else); selected in royal blue.
     */
    val Sun = KlangLookPreset(
        id = "sun",
        name = "Sun",
        values = mapOf(
            KlangRole.GroundApp to "#1c150e",
            KlangRole.GroundDeep to "#241a10",
            KlangRole.GroundPanel to "#2c241b",
            KlangRole.GroundRaised to "#3a3227",
            KlangRole.GroundSunken to "#140e08",
            KlangRole.CapLight to "#6b5232",
            KlangRole.CapDark to "#2a1f12",
            KlangRole.InkText to "#fbe9c8",
            KlangRole.InkStrong to "#fff7e6",
            KlangRole.InkLabel to "#dcbd8c",
            KlangRole.MetalLight to "#fff1c2",
            KlangRole.MetalMid to "#f0c35e",
            KlangRole.MetalDark to "#a8741f",
            KlangRole.MetalInk to "#2a1a05",
            KlangRole.GaugeArc to "#ffb347",
            KlangRole.GaugeTick to "#b89a6e",
            KlangRole.GaugeGlow to "#e09a3a",
            KlangRole.Accent to "#ffa94d",
            KlangRole.AccentMuted to "#b8862f",
            // Royal blue beside the gold (the maintainer, 2026-10-28: "the blue needs to be a more royal blue")
            KlangRole.LampOn to "#4169e1",
            // The spectrum: A real rainbow: the same colours as the default, saturated, no dust (the maintainer, 2026-10-28)
            KlangRole.SpectrumQuiet to "#1fc6dc",
            KlangRole.SpectrumLow to "#5cd65a",
            KlangRole.SpectrumMid to "#ffd93a",
            KlangRole.SpectrumHot to "#ff972e",
            KlangRole.SpectrumPeak to "#ff4f3a",
        ),
    )

    /**
     * Asphalt (the maintainer, 2026-10-28: "make it more asphalt looking"), with gasoline on it ("adopt the whole
     * "gasoline" look on the Asphalt theme"): grounds of fresh tarmac, near black with a cool tint, and metal in
     * concrete and steel greys, more grain and less brushing, a stony surface; on them the oil film's colours, vivid
     * on the black: gauges and the knob in its cyan with a violet glow, the scope's string a pale cyan silver, the
     * accent its violet-magenta, selected in its deep blue, the spectrum its bands.
     */
    val Asphalt = KlangLookPreset(
        id = "asphalt",
        name = "Asphalt",
        values = mapOf(
            KlangRole.GroundApp to "#161718",
            KlangRole.GroundDeep to "#1c1d1f",
            KlangRole.GroundPanel to "#26282a",
            KlangRole.GroundRaised to "#333537",
            KlangRole.GroundSunken to "#0f1011",
            KlangRole.CapLight to "#55595d",
            KlangRole.CapDark to "#1f2123",
            KlangRole.InkText to "#dcdedf",
            KlangRole.InkStrong to "#f6f7f7",
            KlangRole.InkLabel to "#a3a7aa",
            KlangRole.MetalLight to "#e4e6e7",
            KlangRole.MetalMid to "#a9adb0",
            KlangRole.MetalDark to "#5f6367",
            KlangRole.MetalInk to "#121314",
            KlangRole.GaugeArc to "#3fc4e0",
            KlangRole.GaugeTick to "#7d8185",
            KlangRole.GaugeGlow to "#6a4fd0",
            KlangRole.KnobArc to "#3fc4e0",
            KlangRole.ScopeTrace to "#c4e8ee",
            KlangRole.Accent to "#b04fd6",
            KlangRole.AccentMuted to "#3f4fb0",
            KlangRole.Focus to "#7fd8f0",
            KlangRole.LampOn to "#3460dc",
            KlangRole.Grain to "0.32",
            KlangRole.Brushed to "0.04",
            // The spectrum: gasoline on a puddle (the maintainer, 2026-10-28, from photos of an oil and a soap film), the
            // film's bands in their own order, bottom to top: amber, violet, deep blue, cyan, and a bright yellow on
            // top; vivid and metallic, the dark is the asphalt around them
            KlangRole.SpectrumQuiet to "#d48a35",
            KlangRole.SpectrumLow to "#8e3fc6",
            KlangRole.SpectrumMid to "#3460dc",
            KlangRole.SpectrumHot to "#3fc4e0",
            KlangRole.SpectrumPeak to "#e8d24a",
        ),
    )

    val all: List<KlangLookPreset> = listOf(Default, Copper, Cream, Army, Forest, Sun, Asphalt)

    /** The preset with [id]; null for an id no preset has (one remembered from an older version, say). */
    fun byId(id: String): KlangLookPreset? = all.firstOrNull { it.id == id }
}
