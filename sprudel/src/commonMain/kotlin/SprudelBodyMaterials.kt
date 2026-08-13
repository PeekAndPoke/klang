/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel

import io.peekandpoke.klang.audio_bridge.FilterDef

/**
 * Body-resonator material catalogue — the fixed modal resonances behind `body("<material>")`.
 *
 * A material is *pure data*: a list of [FilterDef.Body.Mode] `(freq Hz, db, Q)` triples. The audio
 * backend ([BodyFilter][io.peekandpoke.klang.audio_bridge.FilterDef.Body]) plays them as a parallel
 * SVF-bandpass bank mixed over the dry source. After the bank's `1/Q` normalization, `db` is the
 * mode's *actual* peak emphasis in dB — a few dB, not 20+. Modes are dense so the bank covers the
 * spectrum (overlapping skirts keep the inter-mode response up); higher modes generally use lower Q
 * so they ring shorter — except the metals, where high Q is kept up top (the metallic "tell").
 *
 * Starting-point tables — expect to tune `db`, the mode sets, and `BODY_FLOOR` by ear. The
 * load-bearing landmark resonances (guitar air + main-top pair, brass honk/brilliance formants)
 * are anchored from published acoustics (Fletcher & Rossing; Benade — see CREDITS.MD); the rest is
 * sparse plausible fill. These are caricatures the ear can name, not measured replicas.
 *
 * Public so UI tools (e.g. the `body()` editor) can visualize a material's modal response.
 */
object SprudelBodyMaterials {

    private fun m(freq: Double, db: Double, q: Double) = FilterDef.Body.Mode(freq, db, q)

    /** All selectable names (`none` = off), grouped by family in display order. */
    val names: List<String> = listOf(
        "none",
        // Woods (guitar/string tonewoods)
        "wood", "cedar", "spruce", "mahogany", "rosewood", "maple", "oak",
        // Bowed strings
        "violin",
        // Voices
        "croon",
        // Pipe / glass
        "tube", "glass",
        // Skin
        "membrane",
        // Metals
        "brass", "steel", "bell",
    )

    /** One-line character description per material (for UI + docs). */
    val descriptions: Map<String, String> = mapOf(
        "none" to "off — no body",
        "wood" to "warm resonant box",
        "cedar" to "warm softwood guitar top",
        "spruce" to "bright, articulate top",
        "mahogany" to "dry, punchy midrange",
        "rosewood" to "rich, scooped, long ring",
        "maple" to "transparent, tight, bright",
        "oak" to "hard, dense, boxy mids",
        "violin" to "singing, bridge-hill brilliance",
        "croon" to "warm baritone vocal color",
        "tube" to "resonant pipe",
        "glass" to "bright, long ring",
        "membrane" to "drum-like, fast decay",
        "brass" to "metallic horn body",
        "steel" to "bright, tight metal",
        "bell" to "inharmonic, minor-third ring",
    )

    /**
     * Resolves a body-resonator material name to its fixed modal resonances. Returns null for an
     * unknown material — the body is then skipped (fail soft, never throw on user input).
     */
    fun modesFor(material: String): List<FilterDef.Body.Mode>? = when (material.lowercase()) {
        // Warm resonant box (guitar/marimba-ish body).
        "wood" -> listOf(
            m(100.0, 3.0, 12.0),
            m(200.0, 2.0, 11.0),
            m(300.0, 1.0, 10.0),
            m(430.0, 0.0, 9.0),
            m(650.0, -1.0, 8.0),
            m(900.0, -2.0, 7.0),
            m(1300.0, -4.0, 6.0),
            m(1900.0, -6.0, 5.0),
        )
        // Resonant pipe with a body — the de-plasticized tube.
        "tube" -> listOf(
            m(85.0, 4.0, 14.0),
            m(175.0, 2.0, 12.0),
            m(270.0, 1.0, 11.0),
            m(450.0, 0.0, 9.0),
            m(700.0, -1.0, 8.0),
            m(1000.0, -3.0, 7.0),
            m(1400.0, -5.0, 6.0),
            m(2000.0, -7.0, 5.0),
        )
        // Bright, high-Q, long ring.
        "glass" -> listOf(
            m(700.0, 2.0, 40.0),
            m(1050.0, 1.0, 50.0),
            m(1600.0, 0.0, 55.0),
            m(2100.0, -1.0, 60.0),
            m(2800.0, -2.0, 50.0),
            m(3300.0, -3.0, 45.0),
            m(4000.0, -5.0, 40.0),
            m(4700.0, -7.0, 35.0),
        )
        // Drum-like, inharmonic, fast decay.
        "membrane" -> listOf(
            m(150.0, 2.0, 6.0),
            m(230.0, 1.0, 5.0),
            m(310.0, 0.0, 5.0),
            m(385.0, 0.0, 4.0),
            m(460.0, -1.0, 4.0),
            m(550.0, -2.0, 4.0),
            m(650.0, -3.0, 3.0),
            m(780.0, -4.0, 3.0),
        )
        // Warm softwood guitar top (cedar) — strong low-mids, highs damp fast (Q falls off).
        // Air + main-top landmarks after Fletcher & Rossing; warm/dark character is the "tell".
        "cedar" -> listOf(
            m(95.0, 3.0, 12.0),
            m(185.0, 2.5, 11.0),
            m(280.0, 1.0, 10.0),
            m(400.0, 0.0, 8.0),
            m(600.0, -2.0, 6.0),
            m(850.0, -4.0, 5.0),
            m(1200.0, -7.0, 4.0),
            m(1700.0, -10.0, 3.5),
        )
        // Bright, articulate spruce top — flat tilt, highs still ring (Q stays up), top res a touch higher.
        "spruce" -> listOf(
            m(105.0, 2.0, 12.0),
            m(210.0, 2.0, 12.0),
            m(320.0, 1.5, 11.0),
            m(450.0, 0.0, 10.0),
            m(700.0, -0.5, 9.0),
            m(1000.0, -1.5, 8.0),
            m(1500.0, -3.0, 7.0),
            m(2200.0, -4.5, 6.0),
        )
        // Mahogany — dry, punchy, midrange fundamental; mid db bump, low Q (fast decay), rolled highs.
        "mahogany" -> listOf(
            m(110.0, 2.0, 9.0),
            m(210.0, 3.0, 9.0),
            m(330.0, 3.0, 8.0),
            m(500.0, 1.0, 7.0),
            m(750.0, -2.0, 5.0),
            m(1050.0, -5.0, 4.0),
            m(1500.0, -9.0, 3.0),
            m(2100.0, -13.0, 3.0),
        )
        // Rosewood — hi-fi, scooped mids, strong bass + treble sparkle, long sustain (high Q).
        "rosewood" -> listOf(
            m(100.0, 3.0, 14.0),
            m(190.0, 2.0, 13.0),
            m(300.0, -1.0, 10.0),
            m(500.0, -3.0, 8.0),
            m(800.0, -2.0, 8.0),
            m(1300.0, -1.0, 9.0),
            m(2000.0, 0.0, 9.0),
            m(3000.0, -2.0, 8.0),
        )
        // Maple — transparent, tight, bright; low overall db (uncoloured), low Q (fast decay), flat.
        "maple" -> listOf(
            m(110.0, 1.5, 8.0),
            m(220.0, 1.5, 8.0),
            m(340.0, 1.0, 7.0),
            m(520.0, 0.0, 6.0),
            m(780.0, -1.0, 5.0),
            m(1150.0, -2.0, 4.0),
            m(1700.0, -3.5, 4.0),
            m(2400.0, -5.0, 3.5),
        )
        // Oak — hard, dense, boxy; upper-mid db bump (~600–1300), moderate-high Q, hard edge.
        "oak" -> listOf(
            m(120.0, 2.0, 11.0),
            m(230.0, 1.0, 10.0),
            m(360.0, 0.5, 10.0),
            m(600.0, 1.0, 9.0),
            m(900.0, 2.0, 9.0),
            m(1300.0, 1.0, 8.0),
            m(1900.0, -1.0, 7.0),
            m(2600.0, -3.0, 6.0),
        )
        // Violin body — small & high-tuned: A0 air ~275, corpus ~460/540, and the **bridge hill**
        // (~2–2.6k, the singing brilliance). Landmarks after Fletcher & Rossing (Dünnwald survey).
        "violin" -> listOf(
            m(275.0, 2.0, 12.0),
            m(460.0, 1.0, 11.0),
            m(540.0, 1.0, 10.0),
            m(800.0, -2.0, 8.0),
            m(1200.0, -3.0, 7.0),
            m(2000.0, 0.0, 6.0),
            m(2600.0, 1.0, 5.0),
            m(3200.0, -2.0, 5.0),
        )
        // Warm baritone vocal color (croon) — dark vowel formants (F1 ~500 / F2 ~1000 / F3 ~2500)
        // + the **singer's formant** cluster (~2.9/3.2k, the "ring" of trained vocalists; Sundberg)
        // + chest warmth. Higher Q than wood/metal so the formants read as "voice". Not vowel-tracking.
        "croon" -> listOf(
            m(120.0, 2.0, 8.0),    // chest warmth
            m(500.0, 3.0, 14.0),   // F1 (dark, low vowel)
            m(1000.0, 1.0, 16.0),  // F2
            m(1700.0, -1.0, 16.0), // lower-mid dip region
            m(2500.0, 0.0, 18.0),  // F3
            m(2900.0, 2.0, 22.0),  // singer's-formant cluster — the "ring"
            m(3200.0, 1.0, 22.0),  //   "
            m(4500.0, -5.0, 10.0), // air roll-off
        )
        // Metallic horn body (brass) — honk formant (~350/520) + bright ring (~1.3k/1.8k), and
        // little high-frequency damping (Q stays high up top, unlike wood — the metal "tell").
        "brass" -> listOf(
            m(160.0, 1.0, 14.0),
            m(350.0, 3.0, 16.0),
            m(520.0, 2.0, 15.0),
            m(900.0, 0.0, 14.0),
            m(1300.0, 2.0, 16.0),
            m(1800.0, 1.0, 15.0),
            m(2600.0, -1.0, 14.0),
            m(3600.0, -3.0, 12.0),
        )
        // Steel — steel-string / bar / drum: very bright, tight, sparse, high-Q metal ring up top.
        "steel" -> listOf(
            m(200.0, 2.0, 20.0),
            m(400.0, 1.0, 22.0),
            m(650.0, 0.0, 24.0),
            m(1000.0, 0.0, 26.0),
            m(1500.0, -1.0, 24.0),
            m(2200.0, -1.0, 22.0),
            m(3200.0, -2.0, 20.0),
            m(4400.0, -4.0, 16.0),
        )
        // Bell — struck bronze: the classic inharmonic partials hum/prime/tierce/quint/nominal at
        // 0.5 : 1 : 1.2 : 1.5 : 2 (the tierce = a MINOR THIRD above the prime — the melancholy bell
        // "tell"), then upper inharmonic partials. Long shimmer (high Q, capped by the SVF's Q≤200).
        "bell" -> listOf(
            m(110.0, 2.0, 30.0),   // hum (octave below prime)
            m(220.0, 3.0, 40.0),   // prime
            m(262.0, 1.0, 45.0),   // tierce — minor third above prime
            m(330.0, 0.0, 45.0),   // quint — fifth above prime
            m(440.0, 1.0, 50.0),   // nominal — octave above prime
            m(590.0, -1.0, 45.0),  // upper inharmonic partials
            m(785.0, -2.0, 40.0),
            m(1050.0, -3.0, 35.0),
        )

        // Explicit "off" — resolves to no body filter, so `body("none")` resets/clears the resonator.
        "none" -> null

        else -> null
    }
}
