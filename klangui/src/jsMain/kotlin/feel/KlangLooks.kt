/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.feel

/**
 * The built-in looks (KLANG-00339): the three the maintainer picked from the kitchen sink's samples (2026-10-25), and
 * Army, the first Cream kept when Cream became creamy, Forest, Sun, Asphalt and Neon (2026-10-28); Party, Cake,
 * Unicorn and Pink Noir, and Claude's own Abyss and Amber (2026-11-01). Which of them a new user finds equipped is
 * [equippedByDefault]; the rest wait on the bench (KLANG-00382).
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
            // The texture and the corners, each look its own (the maintainer, 2026-10-28):
            // Hammered copper: a coarser grain, little brushing
            KlangRole.Grain to "0.22",
            KlangRole.GrainSize to "0.45",
            KlangRole.Brushed to "0.08",
            KlangRole.Radius to "4px",
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
            // The texture and the corners, each look its own (the maintainer, 2026-10-28):
            // Smooth as porcelain: a fine, faint grain
            KlangRole.Grain to "0.08",
            KlangRole.GrainSize to "0.15",
            KlangRole.Brushed to "0.05",
            KlangRole.Radius to "4px",
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
            // The texture and the corners, each look its own (the maintainer, 2026-10-28):
            // Rough paint: a strong grain, rugged corners
            KlangRole.Grain to "0.4",
            KlangRole.GrainSize to "0.4",
            KlangRole.Brushed to "0.14",
            KlangRole.Radius to "2px",
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
            // The texture and the corners, each look its own (the maintainer, 2026-10-28):
            // Woody: a broad grain, strong brushing like the grain of wood
            KlangRole.Grain to "0.25",
            KlangRole.GrainSize to "0.6",
            KlangRole.Brushed to "0.18",
            KlangRole.Radius to "3px",
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
            // The spectrum: blues only, from the lamp's royal blue at the floor, lighter to the top (the maintainer,
            // 2026-10-29, after gold at the top and a violet between: "only blue tones maybe"; a rainbow before)
            // Starting darker, a deep royal blue, to the same icy top, a steeper gradient (the maintainer, 2026-10-29);
            // its floor then a step lighter ("the darkest blue is a bit too dark": #14266e before); the three between
            // even steps of brightness from the floor to the top, worked out in OKLab ("make the color transition to the
            // other color smooth. Keep the lowest and highest color as is and interpolate the brightness between")
            KlangRole.SpectrumQuiet to "#1e3a9e",
            KlangRole.SpectrumLow to "#4665b8",
            KlangRole.SpectrumMid to "#728ed0",
            KlangRole.SpectrumHot to "#a1b8e8",
            KlangRole.SpectrumPeak to "#d2e2ff",
            // The texture and the corners, each look its own (the maintainer, 2026-10-28):
            // The maintainer's, 2026-10-28: a strong grain, tight corners
            KlangRole.Grain to "0.67",
            KlangRole.GrainSize to "0.25",
            KlangRole.Brushed to "0.12",
            KlangRole.Radius to "2px",
            KlangRole.RadiusDomed to "4px",
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
            // Coarse tar: a broad grain, hardly brushed, sharp corners (the maintainer, 2026-10-28: each look its own)
            KlangRole.Grain to "0.32",
            KlangRole.GrainSize to "0.7",
            KlangRole.Brushed to "0.04",
            KlangRole.Radius to "1px",
            // The tube a little stronger than the other looks' (the maintainer, 2026-10-28)
            KlangRole.Scanlines to "0.15",
            KlangRole.Glow to "0.25",
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

    /**
     * Neon disco (the maintainer, 2026-10-28: "a neon disco theme", Army to the bench for it): a near-black violet
     * ground and dark chrome, so the neon glows against them; hot pink, cyan and violet on the instruments and the
     * accent, a disco spectrum from violet to yellow; smooth, softer corners; the tube glows more.
     */
    val Neon = KlangLookPreset(
        id = "neon",
        name = "Neon",
        values = mapOf(
            // Dark violet, so the black screen stands apart (the maintainer, 2026-10-28: near black, "there is no real
            // contrast to the editor anymore"; as bright as the default's was "too bright ... middle")
            KlangRole.GroundApp to "#100a1d",
            KlangRole.GroundDeep to "#0c0716",
            KlangRole.GroundPanel to "#1c122e",
            KlangRole.GroundRaised to "#281c3f",
            KlangRole.GroundSunken to "#05030a",
            KlangRole.CapLight to "#3b2c58",
            KlangRole.CapDark to "#130b20",
            KlangRole.InkText to "#e6dcff",
            KlangRole.InkStrong to "#ffffff",
            KlangRole.InkLabel to "#ab96d8",
            // Dark chrome with a violet cast
            KlangRole.MetalLight to "#d6d0f2",
            KlangRole.MetalMid to "#7d72aa",
            KlangRole.MetalDark to "#2a2245",
            KlangRole.MetalInk to "#0a0612",
            KlangRole.LampOff to "#2a1a3c",
            KlangRole.LampOn to "#00e8ff",
            KlangRole.LampWarning to "#ffb020",
            KlangRole.LampCritical to "#ff2a6d",
            KlangRole.GaugeArc to "#ff2bd6",
            KlangRole.GaugeTick to "#6c5ba8",
            KlangRole.GaugeGlow to "#b026ff",
            KlangRole.KnobArc to "#00e8ff",
            KlangRole.ScopeTrace to "#00e8ff",
            KlangRole.LcdInk to "#39ff9c",
            KlangRole.Accent to "#ff2bd6",
            KlangRole.AccentMuted to "#7b2cff",
            KlangRole.Focus to "#00e8ff",
            // Smooth all over, glass and lacquer, hardly any grain, no brushing (the maintainer, 2026-10-28: "almost no
            // grain at all ... everything smooth")
            KlangRole.Grain to "0.02",
            KlangRole.GrainSize to "0.2",
            KlangRole.Brushed to "0",
            KlangRole.Radius to "4px",
            KlangRole.RadiusDomed to "12px",
            KlangRole.Scanlines to "0.12",
            KlangRole.Glow to "0.45",
            // The spectrum: a disco floor, violet, blue, cyan, pink, and a yellow peak
            KlangRole.SpectrumQuiet to "#7b2cff",
            KlangRole.SpectrumLow to "#2d6bff",
            KlangRole.SpectrumMid to "#00e8ff",
            KlangRole.SpectrumHot to "#ff2bd6",
            KlangRole.SpectrumPeak to "#fff14a",
        ),
    )

    /**
     * A party (the maintainer, 2026-11-01: "some really girly ones ... Maybe "party" and "cake"", then "make the color
     * more spread on the color circle"): a raspberry-black ground and rose-gold metal; on them the colours of a party
     * all round the circle: bubblegum gauges with a violet glow, the knobs in turquoise, the scope in lime, the display
     * in lemon, a coral accent, selected in a bright blue; a fine glitter grain, round corners; the spectrum confetti.
     */
    val Party = KlangLookPreset(
        id = "party",
        name = "Party",
        values = mapOf(
            KlangRole.GroundApp to "#1d0e17",
            KlangRole.GroundDeep to "#25111d",
            KlangRole.GroundPanel to "#2c1a25",
            KlangRole.GroundRaised to "#3b2733",
            KlangRole.GroundSunken to "#12070e",
            KlangRole.CapLight to "#6a3a55",
            KlangRole.CapDark to "#26101d",
            KlangRole.InkText to "#fbe1ee",
            KlangRole.InkStrong to "#fff5fa",
            KlangRole.InkLabel to "#e0a8c4",
            KlangRole.MetalLight to "#ffe3ea",
            KlangRole.MetalMid to "#e8a3b4",
            KlangRole.MetalDark to "#9c5a6e",
            KlangRole.MetalInk to "#2a0a18",
            KlangRole.LampOn to "#3f8cff",
            KlangRole.LampWarning to "#ffb347",
            KlangRole.LampCritical to "#ff3f6c",
            KlangRole.GaugeArc to "#ff5fb0",
            KlangRole.GaugeTick to "#a8789a",
            KlangRole.GaugeGlow to "#9b5cff",
            KlangRole.KnobArc to "#2fe0d0",
            KlangRole.ScopeTrace to "#a8f05a",
            KlangRole.LcdInk to "#ffe94a",
            KlangRole.Accent to "#ff7a5c",
            KlangRole.AccentMuted to "#9b5cff",
            KlangRole.Focus to "#2fe0d0",
            // Glitter: a strong, very fine grain, no brushing; round corners
            KlangRole.Grain to "0.3",
            KlangRole.GrainSize to "0.08",
            KlangRole.Brushed to "0",
            KlangRole.Radius to "6px",
            KlangRole.RadiusDomed to "14px",
            KlangRole.Glow to "0.35",
            // The spectrum: confetti, violet, turquoise, lime, lemon and the party's pink on top
            KlangRole.SpectrumQuiet to "#7a5cff",
            KlangRole.SpectrumLow to "#2fd0e0",
            KlangRole.SpectrumMid to "#8ff06a",
            KlangRole.SpectrumHot to "#ffd23f",
            KlangRole.SpectrumPeak to "#ff4fa3",
        ),
    )

    /**
     * A strawberry cake (the maintainer, 2026-11-01, with Party, the colours spread round the circle): a dark
     * chocolate ground and metal of strawberry frosting; cherry gauges with a raspberry glow, the knobs in mint, the
     * scope in vanilla, the display in pistachio, a raspberry accent with a lavender second, selected in sky blue;
     * smooth as icing, soft corners; the spectrum the sprinkles.
     */
    val Cake = KlangLookPreset(
        id = "cake",
        name = "Cake",
        values = mapOf(
            KlangRole.GroundApp to "#1e1412",
            KlangRole.GroundDeep to "#261916",
            KlangRole.GroundPanel to "#2d2220",
            KlangRole.GroundRaised to "#3b2f2c",
            KlangRole.GroundSunken to "#150d0b",
            KlangRole.CapLight to "#6a4a48",
            KlangRole.CapDark to "#28191a",
            KlangRole.InkText to "#f7e4e4",
            KlangRole.InkStrong to "#fff8f6",
            KlangRole.InkLabel to "#d9aeb4",
            KlangRole.MetalLight to "#ffeef3",
            KlangRole.MetalMid to "#f7b8cc",
            KlangRole.MetalDark to "#c27a92",
            KlangRole.MetalInk to "#3a1220",
            KlangRole.LampOn to "#6ab8ff",
            KlangRole.GaugeArc to "#ff6f8f",
            KlangRole.GaugeTick to "#a88a8a",
            KlangRole.GaugeGlow to "#d94f8a",
            KlangRole.KnobArc to "#7fe0b8",
            KlangRole.ScopeTrace to "#fff0c0",
            KlangRole.LcdInk to "#b8e88a",
            KlangRole.Accent to "#f0507f",
            KlangRole.AccentMuted to "#a88ad8",
            KlangRole.Focus to "#7fe0b8",
            // Icing: a soft, faint grain, hardly brushed, soft corners
            KlangRole.Grain to "0.1",
            KlangRole.GrainSize to "0.3",
            KlangRole.Brushed to "0.02",
            KlangRole.Radius to "6px",
            KlangRole.RadiusDomed to "12px",
            // The spectrum: sprinkles, sky, pistachio, lemon, orange and cherry
            KlangRole.SpectrumQuiet to "#7fc8f0",
            KlangRole.SpectrumLow to "#a8e690",
            KlangRole.SpectrumMid to "#fff08a",
            KlangRole.SpectrumHot to "#ffb070",
            KlangRole.SpectrumPeak to "#ff6f8f",
        ),
    )

    /**
     * A unicorn (with Party and Cake, 2026-11-01, the colours spread round the circle): a dusk-lilac ground and pearly
     * metal; lilac gauges with a pink glow, the knobs in sky blue, the scope in mint, the display in lemon, a pink
     * accent with a peach second, selected in aqua; pearl-smooth, very round; the spectrum a pastel rainbow.
     */
    val Unicorn = KlangLookPreset(
        id = "unicorn",
        name = "Unicorn",
        values = mapOf(
            KlangRole.GroundApp to "#17121f",
            KlangRole.GroundDeep to "#1d1728",
            KlangRole.GroundPanel to "#252030",
            KlangRole.GroundRaised to "#332d3f",
            KlangRole.GroundSunken to "#0f0b15",
            KlangRole.CapLight to "#544870",
            KlangRole.CapDark to "#1f1829",
            KlangRole.InkText to "#ece4fb",
            KlangRole.InkStrong to "#fdfaff",
            KlangRole.InkLabel to "#bfaee0",
            KlangRole.MetalLight to "#f6efff",
            KlangRole.MetalMid to "#d4c2f0",
            KlangRole.MetalDark to "#8f7cb8",
            KlangRole.MetalInk to "#1e1430",
            KlangRole.LampOn to "#5fe8e0",
            KlangRole.GaugeArc to "#c39aff",
            KlangRole.GaugeTick to "#8e84a8",
            KlangRole.GaugeGlow to "#ff8fd0",
            KlangRole.KnobArc to "#8fd0ff",
            KlangRole.ScopeTrace to "#9ff0c8",
            KlangRole.LcdInk to "#fff09a",
            KlangRole.Accent to "#ff8fd8",
            KlangRole.AccentMuted to "#ffb59a",
            KlangRole.Focus to "#8fd0ff",
            // Pearl: almost no grain, no brushing, very round
            KlangRole.Grain to "0.06",
            KlangRole.GrainSize to "0.15",
            KlangRole.Brushed to "0",
            KlangRole.Radius to "5px",
            KlangRole.RadiusDomed to "14px",
            KlangRole.Glow to "0.3",
            // The spectrum: a pastel rainbow, lilac to rose
            KlangRole.SpectrumQuiet to "#b8a0ff",
            KlangRole.SpectrumLow to "#8fd0ff",
            KlangRole.SpectrumMid to "#9ff0c8",
            KlangRole.SpectrumHot to "#fff09a",
            KlangRole.SpectrumPeak to "#ffb0d8",
        ),
    )

    /**
     * Pink and black (the maintainer, 2026-11-01: "We need a really pink and black one too"): pure black grounds, metal
     * of hot pink, and pink on everything that glows; the one colour it chooses that is not pink is white, for the text
     * and the selected lamp; glossy lacquer, no grain; the spectrum pink from the dark to white-hot.
     */
    val PinkNoir = KlangLookPreset(
        id = "pink-noir",
        name = "Pink Noir",
        values = mapOf(
            KlangRole.GroundApp to "#0a0a0a",
            KlangRole.GroundDeep to "#050505",
            KlangRole.GroundPanel to "#141214",
            KlangRole.GroundRaised to "#221d21",
            KlangRole.GroundSunken to "#000000",
            KlangRole.CapLight to "#3a2a35",
            KlangRole.CapDark to "#0e0a0d",
            KlangRole.InkText to "#ffe0f0",
            KlangRole.InkStrong to "#ffffff",
            KlangRole.InkLabel to "#ff7ac0",
            KlangRole.MetalLight to "#ffc2e2",
            KlangRole.MetalMid to "#ff4fa8",
            KlangRole.MetalDark to "#9e1a5e",
            KlangRole.MetalInk to "#0a0005",
            KlangRole.LampOff to "#2a0f1e",
            KlangRole.LampOn to "#ffffff",
            KlangRole.LampCritical to "#ff1f4f",
            KlangRole.GaugeArc to "#ff2fa0",
            KlangRole.GaugeTick to "#7a4a65",
            KlangRole.GaugeGlow to "#ff2fa0",
            KlangRole.KnobArc to "#ff2fa0",
            KlangRole.ScopeTrace to "#ff6fc0",
            KlangRole.LcdInk to "#ff4fb0",
            KlangRole.Accent to "#ff2fa0",
            KlangRole.AccentMuted to "#b0206e",
            KlangRole.Focus to "#ff8fd0",
            // Glossy lacquer: no grain, no brushing; round corners
            KlangRole.Grain to "0",
            KlangRole.GrainSize to "0.2",
            KlangRole.Brushed to "0",
            KlangRole.Radius to "4px",
            KlangRole.RadiusDomed to "10px",
            KlangRole.Glow to "0.45",
            // The spectrum: pink only, from the dark to white-hot
            KlangRole.SpectrumQuiet to "#5a0a35",
            KlangRole.SpectrumLow to "#a0105e",
            KlangRole.SpectrumMid to "#e81f8a",
            KlangRole.SpectrumHot to "#ff6fbf",
            KlangRole.SpectrumPeak to "#ffd6ec",
        ),
    )

    /**
     * The deep sea (Claude's own, 2026-11-01): grounds of the abyss, near black with a blue-green cast, metal of wet
     * dark steel, gauges and the accent in the glow of the creatures down there, selected in a clear blue; a little
     * silt in the grain; the spectrum rising from the deep to the light at the surface.
     */
    val Abyss = KlangLookPreset(
        id = "abyss",
        name = "Abyss",
        values = mapOf(
            KlangRole.GroundApp to "#07121a",
            KlangRole.GroundDeep to "#0a1720",
            KlangRole.GroundPanel to "#132029",
            KlangRole.GroundRaised to "#1f2d37",
            KlangRole.GroundSunken to "#030a0f",
            KlangRole.CapLight to "#2f4a58",
            KlangRole.CapDark to "#0c1820",
            KlangRole.InkText to "#d6ecef",
            KlangRole.InkStrong to "#f2fdff",
            KlangRole.InkLabel to "#8fb4bd",
            KlangRole.MetalLight to "#cfe6ea",
            KlangRole.MetalMid to "#6f97a3",
            KlangRole.MetalDark to "#2c4a55",
            KlangRole.MetalInk to "#04131a",
            KlangRole.GaugeArc to "#3ff5c8",
            KlangRole.GaugeTick to "#5f8590",
            KlangRole.GaugeGlow to "#1f8fd6",
            KlangRole.KnobArc to "#3ff5c8",
            KlangRole.ScopeTrace to "#9ff8e4",
            KlangRole.Accent to "#3ff5c8",
            KlangRole.AccentMuted to "#1f7f8f",
            KlangRole.Focus to "#7ff8dc",
            KlangRole.LampOn to "#4f9dff",
            // Silt: a broad, faint grain, little brushing
            KlangRole.Grain to "0.12",
            KlangRole.GrainSize to "0.5",
            KlangRole.Brushed to "0.06",
            KlangRole.Radius to "3px",
            KlangRole.Glow to "0.35",
            // The spectrum: from the deep blue to the light at the surface
            KlangRole.SpectrumQuiet to "#0b3d6e",
            KlangRole.SpectrumLow to "#1f6fa8",
            KlangRole.SpectrumMid to "#20b5c8",
            KlangRole.SpectrumHot to "#3ff5c8",
            KlangRole.SpectrumPeak to "#d8fff4",
        ),
    )

    /**
     * An amber terminal (Claude's own, 2026-11-01): the old phosphor screens, everything in one amber on near black,
     * brass for the metal; the tube's lines and glow strong, no grain to speak of, square corners. Selected and focused
     * in a pale blue, the one colour it chooses that is not amber, and the accent a deeper orange, so none is the
     * trail's gold.
     */
    val Amber = KlangLookPreset(
        id = "amber",
        name = "Amber",
        values = mapOf(
            KlangRole.GroundApp to "#120c05",
            KlangRole.GroundDeep to "#170f06",
            KlangRole.GroundPanel to "#1d150b",
            KlangRole.GroundRaised to "#2a1f12",
            KlangRole.GroundSunken to "#0a0602",
            KlangRole.CapLight to "#4a3518",
            KlangRole.CapDark to "#1a1208",
            KlangRole.InkText to "#ffcf80",
            KlangRole.InkStrong to "#ffe8bf",
            KlangRole.InkLabel to "#c8944a",
            KlangRole.MetalLight to "#ffe2a8",
            KlangRole.MetalMid to "#d9a24a",
            KlangRole.MetalDark to "#7a521c",
            KlangRole.MetalInk to "#1a0f02",
            KlangRole.GaugeArc to "#ffb000",
            KlangRole.GaugeTick to "#8a6a3a",
            KlangRole.GaugeGlow to "#ff8a00",
            KlangRole.KnobArc to "#ffb000",
            KlangRole.ScopeTrace to "#ffb000",
            KlangRole.LcdInk to "#ffb000",
            KlangRole.Accent to "#ff8a1f",
            KlangRole.AccentMuted to "#a85a10",
            // The focus ring in the selected lamp's blue: an amber ring would read as the trail's gold beacon
            KlangRole.Focus to "#6fd0ff",
            KlangRole.LampOn to "#6fd0ff",
            // The tube: strong lines and glow, hardly any grain, no brushing, square corners
            KlangRole.Grain to "0.04",
            KlangRole.GrainSize to "0.2",
            KlangRole.Brushed to "0",
            KlangRole.Radius to "1px",
            KlangRole.RadiusDomed to "2px",
            KlangRole.Scanlines to "0.22",
            KlangRole.Glow to "0.4",
            // The spectrum: one amber, dark to bright
            KlangRole.SpectrumQuiet to "#5a3200",
            KlangRole.SpectrumLow to "#8f5200",
            KlangRole.SpectrumMid to "#c87800",
            KlangRole.SpectrumHot to "#ffb000",
            KlangRole.SpectrumPeak to "#ffe6a8",
        ),
    )

    val all: List<KlangLookPreset> = listOf(
        Default, Copper, Cream, Army, Forest, Sun, Asphalt, Neon, Party, Cake, Unicorn, PinkNoir, Abyss, Amber,
    )

    /**
     * The looks a new user finds equipped, in the drawer's order, the last slot left empty for a look of their own (the
     * maintainer, 2026-11-02: "Please fill the loadout an leave one empty by default", then "Make this the default"):
     * three rows of four. Army, Cake and Unicorn wait on the bench, to be equipped on the Looks page.
     */
    val equippedByDefault: List<KlangLookPreset> = listOf(
        Default, Copper, Cream, Forest,
        Sun, Amber, Asphalt, Abyss,
        Party, Neon, PinkNoir,
    )

    /** The preset with [id]; null for an id no preset has (one remembered from an older version, say). */
    fun byId(id: String): KlangLookPreset? = all.firstOrNull { it.id == id }
}
