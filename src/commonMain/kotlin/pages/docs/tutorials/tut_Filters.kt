/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

/**
 * Curriculum slot A3 — see docs/tasks/tutorial-curriculum.md
 *
 * Filter A/B pairs are inherently level-unequal, in BOTH directions: cutting
 * removes energy (the hpf side loses the loud low end), while resonance ADDS
 * level — which is why §4 and §5's resonant lines sit at gain 0.4. All narrated
 * in-lesson; sanctioned parity exceptions — do not gain-compensate the pairs
 * to equality.
 */
val filtersTutorial = Tutorial(
    slug = "filters",
    title = Tut.filters,
    description = "lpf and hpf: keep the body or keep the sparkle — colour as a decision.",
    difficulty = TutorialDifficulty.Beginner,
    scope = TutorialScope.Quick,
    tags = listOf(TutorialTag.Synthesis),
    teaches = listOf("lpf", "hpf", "lpq"),
    sections = listOf(
        TutorialSection(
            heading = "Colour, not loudness",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    ${Tut.shapeOfANote} shaped how loud a note is over its life; this lesson shapes its colour — first as a fixed decision, then stepping cycle by cycle. (Making the colour move inside each note is ${Tut.theFilterEnvelope}.)

                    `lpf()` is a **low-pass filter**: it lets the lows through and cuts what lies above its **cutoff** — the number, a frequency in hertz. Pitch has a number too: the `a3` this melody starts on swings 220 times a second — 220 hertz — and every octave up doubles it. A voice's overtones stack above its pitch, so a cutoff of 800 lets the melody's own notes through and takes away much of their stack.

                    **Try it:** swap the `//` to compare against the unfiltered `saw`.

                    **Listen for:** the same melody with the sparkle taken off — like a blanket over the speaker. (Filtering can change loudness along with colour; throughout this lesson, judge the colour, not the level.)
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    note("a3 c4 d4 ~  e4 d4 c4 ~").sound("saw").lpf(800).gain(0.5) // low-pass: sparkle above 800 removed
                    // note("a3 c4 d4 ~  e4 d4 c4 ~").sound("saw").gain(0.5)       // the full saw — swap to compare
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "The cutoff is a dial",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    The cutoff is not a switch, it is a dial — and like every setting, it takes a pattern. With angle brackets from ${Tut.alternationAndRepetition}, each cycle gets its own cutoff.

                    **Try it:** reorder the four numbers and press Update.

                    **Listen for:** the order of loss. At 3200 almost nothing is missing; at 1600 the sparkle is gone but the melody is intact; at 800 it is under the blanket; at 400 only the body is left. The sparkle goes first, the body last — that order is what a cutoff is.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    note("a3 c4 d4 ~  e4 d4 c4 ~").sound("saw")  // the same melody all lesson
                      .lpf("<3200 1600 800 400>")                // one cutoff per cycle: bright to dark
                      .gain(0.5)                                 // level steady — the filter does the changing
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "From below: hpf",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    `hpf()` is the same idea upside down — a **high-pass filter** lets the highs through and cuts what lies below its cutoff. Both lines below use the same number, 800, so they split the `saw` at roughly the same place and keep opposite halves.

                    **Try it:** swap the `//`.

                    **Listen for:** which half carries what. Low-passed, the melody keeps its body and loses its sparkle. High-passed, it keeps the sparkle and loses the ground under it — thin and distant, like a phone speaker. (The high-passed line is also much quieter: it lost the loudest part of the sound, the low end. Judge the colour.) **Body** below, **sparkle** above: that is most of what there is to know about colour.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    note("a3 c4 d4 ~  e4 d4 c4 ~").sound("saw").hpf(800).gain(0.5)    // high-pass: body below 800 removed
                    // note("a3 c4 d4 ~  e4 d4 c4 ~").sound("saw").lpf(800).gain(0.5) // low-pass: the opposite half
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "Resonance: lpq",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    A filter has a boundary — its cutoff — and `lpq()` makes that boundary itself audible: **resonance** boosts the frequencies right at it, so the filter stops being a blanket and becomes a presence. From here on, this is the resonant edge. (Resonance adds level, so this pair sits a notch lower in gain.)

                    **Try it:** swap the `//` to compare the resonant edge against the plain filter. Then push `lpq` to 20.

                    **Listen for:** a whistling sheen right at the cutoff — the filter beginning to sing along. At 20 the whistle becomes the loudest thing in the line. Small amounts sharpen a sound; big amounts take it over.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    note("a3 c4 d4 ~  e4 d4 c4 ~").sound("saw").lpf(700).lpq(6).gain(0.4) // the resonant edge — try lpq 20
                    // note("a3 c4 d4 ~  e4 d4 c4 ~").sound("saw").lpf(700).gain(0.4)     // the plain filter — swap to compare
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "A colour of your own",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    Everything from this lesson in one line: the melody under a cutoff that alternates bright and dark, with enough resonance to make the edge sing.

                    Cutoff and resonance are the two dials you will reach for most in all of sound design.

                    **Try it:** swap the `//` to hear the `hpf` version, then pick numbers of your own.

                    **Listen for:** two colours taking turns — on the `hpf` line, a different instrument from the same melody — and how completely a filter decides what kind of music this is. (Its moving cousin lives in ${Tut.theFilterEnvelope}.)
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    note("a3 c4 d4 ~  e4 d4 c4 ~").sound("saw").lpf("<3200 400>").lpq(4).gain(0.4) // bright and dark, alternating
                    // note("a3 c4 d4 ~  e4 d4 c4 ~").sound("saw").hpf("<200 1600>").gain(0.5)     // the other half of the spectrum
                    """.trimIndent(),
                ),
            ),
        ),
    ),
)
