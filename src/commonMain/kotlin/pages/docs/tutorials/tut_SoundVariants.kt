/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

val soundVariantsTutorial = Tutorial(
    slug = "sound-variants",
    title = "Sound Variants — One Instrument, Many Flavours",
    description = "Bundle multiple pad sounds into a single instrument and switch flavours per note with the `:n` suffix or the `n()` pattern.",
    difficulty = TutorialDifficulty.Intermediate,
    scope = TutorialScope.Standard,
    tags = listOf(TutorialTag.Synthesis, TutorialTag.Melody),
    sections = listOf(
        TutorialSection(
            heading = "Introduction",
            text = "Sometimes you want two pads — a soft one and a brighter one — but you don't want to write two parallel parts and stack them. `Osc.variants(a, b, ...)` bundles several sounds into a single instrument. Each note then picks a flavour by index: the same mechanism that picks samples in a bank like `bd:0`, `bd:1`. Same pattern, different colour, per note.",
        ),
        TutorialSection(
            heading = "1. Glass — A Soft Sine Pad",
            text = "Our first pad is built around a unison sine — gentle, airy, no edges. A long attack lets each note bloom; a tight low-pass keeps the high partials of the detuning under control. Hold it and you can feel it breathe.",
            code = """// Pad #1: a glassy sine pad — soft, slow, transparent
let glass = Osc.supersine(/* freq */ Osc.freq(), /* voices */ 6, /* detune */ 0.15)
    .lowpass(1800)
    .adsr(0.3, 0.6, 0.8, 1.5)

note("a3 c4 e4 d4").sound(glass).gain(0.3).room(0.3)""",
        ),
        TutorialSection(
            heading = "2. Warm — A Rich Saw Pad",
            text = "The second pad is built from a supersaw — many detuned sawtooths stacked together. Saws are bright and full of harmonics, so we tame them with a lower filter. The result is warm and chordal, the classic analog-pad colour that sits underneath everything.",
            code = """// Pad #2: a warm supersaw pad — wide, rich, woody
let warm = Osc.supersaw(/* freq */ Osc.freq(), /* voices */ 8, /* detune */ 0.2)
    .lowpass(700)
    .adsr(0.4, 0.5, 0.7, 1.8)

note("a3 c4 e4 d4").sound(warm).gain(0.25).room(0.3)""",
        ),
        TutorialSection(
            heading = "3. Bundle Them — Switch by Note Suffix",
            text = "Now wrap both pads in `Osc.variants(...)`. The first child is index 0, the second is index 1. In the pattern, `note(\"a b:1\")` says: play 'a' with variant 0 (default) and 'b' with variant 1. Same note pattern, two timbres alternating — the second note picks the warm pad.",
            code = """// Define the two pads as before…
let glass = Osc.supersine(Osc.freq(), 6, 0.15)
    .lowpass(1800).adsr(0.3, 0.6, 0.8, 1.5)

let warm = Osc.supersaw(Osc.freq(), 8, 0.2)
    .lowpass(700).adsr(0.4, 0.5, 0.7, 1.8)

// …and bundle them. `:1` on a note picks variant 1.
let pad = Osc.variants(glass, warm)

note("a3 c4:1 e4 d4:1").sound(pad).gain(0.3).room(0.3)""",
        ),
        TutorialSection(
            heading = "4. Switch by a Separate `n()` Pattern",
            text = "Inlining `:1` after every note works, but if the variant pattern is more complex you can keep it on its own line. `.n(\"0 1 0 1\")` says: variant 0, 1, 0, 1 — one per note. The melody stays clean in `note(...)`; the variant choreography lives in `.n(...)`. Same end result, easier to edit each axis separately.",
            code = """let glass = Osc.supersine(Osc.freq(), 6, 0.15)
    .lowpass(1800).adsr(0.3, 0.6, 0.8, 1.5)

let warm = Osc.supersaw(Osc.freq(), 8, 0.2)
    .lowpass(700).adsr(0.4, 0.5, 0.7, 1.8)

let pad = Osc.variants(glass, warm)

// Melody in note(), variant per step in n()
note("a3 c4 e4 d4").n("0 1 0 1").sound(pad).gain(0.3).room(0.3)""",
        ),
        TutorialSection(
            heading = "5. Scale-Aware Variants — Open vs. Palm-Muted Guitar",
            text = "Once you bring `.scale(...)` into the mix, the cleanest way to drive variants is `seq(\"step:variant\")`. The step before the colon is the scale degree; the variant after is which child of `Osc.variants(...)` to use. Think of a guitar phrase where some notes ring out and some are palm-muted: same scale, different timbre per note, all on one line.",
            code = """// Two flavours of the same guitar tone
let open  = Osc.saw().lowpass(2000).adsr(0.005, 0.15, 0.0, 0.1)
let muted = Osc.saw().lowpass(600).adsr(0.005, 0.05, 0.0, 0.05)
let pad   = Osc.variants(open, muted)

// "0" / "2" / "4" / "7" are scale steps in C minor.
// "4:1" / "5:1" tag those notes as the muted variant.
seq("0 2 4 4:1 5:1 7").scale("c4:minor").sound(pad).gain(0.3)""",
        ),
        TutorialSection(
            heading = "6. Putting It All Together",
            text = "Here's a longer phrase that uses the variants for texture. The melody floats on top in A minor; the `n()` pattern weaves between glass and warm so the timbre keeps shifting under the same notes. A shared `.lowpass(...)` on the bundle would wrap both pads — but here each pad already has its own filter, so they keep their distinct character. Add a drum and you have a finished sketch.",
            code = """let glass = Osc.supersine(Osc.freq(), 6, 0.15)
    .lowpass(1800).adsr(0.3, 0.6, 0.8, 1.5)

let warm = Osc.supersaw(Osc.freq(), 8, 0.2)
    .lowpass(700).adsr(0.4, 0.5, 0.7, 1.8)

let pad = Osc.variants(glass, warm)

stack(
  // Pad melody — variants weave underneath the notes
  note("a3 c4 e4 g4 e4 c4 d4 a3")
    .n("0 0 1 1 0 1 1 0")
    .sound(pad).gain(0.3).room(0.35).rsize(5.0).orbit(0),
  // A soft kick to anchor the pulse
  sound("bd ~ ~ bd ~ ~ bd ~").gain(0.5).orbit(1),
  // Hi-hat ticking through
  sound("hh*8").gain(0.2).orbit(1)
)""",
        ),
        TutorialSection(
            heading = "What You Learned",
            text = "`Osc.variants(...)` bundles multiple sounds into one instrument addressed by index. The index comes from `:n` on a note (`b:1`), from a separate `.n(\"0 1\")` pattern, or — when you're driving notes from a scale — inline as `seq(\"step:variant\").scale(...)`. Negative indices wrap from the end, overflow wraps to zero, so you can drive variants from arithmetic patterns without worrying about bounds. The trick scales: shared post-effects like `.variants(a, b).lowpass(400)` wrap whichever variant got picked, and nested variants all dispatch on the same index — one knob controls correlated changes across the tree.",
        ),
    ),
)
