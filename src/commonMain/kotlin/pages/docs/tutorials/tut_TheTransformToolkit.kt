/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

/**
 * Curriculum slot B9 — see docs/tasks/tutorial-curriculum.md
 *
 * Keeps two promises: B4's `*n` gets connected to `.fast(n)` (the same
 * multiplication, one step vs the whole line), and A5's preview of fast()/
 * slow() on signals gets its full pattern-level story. `clip` is a true alias
 * of `legato` (both call `p.legato` in lang_tonal.kt) — the finale shows the
 * alias once (the songs spell it BOTH ways, legato slightly more often), so
 * later lessons may use either name.
 * `superimpose` is taught in the chained-mapper form (`superimpose(transpose(12))`),
 * the same shape the built-in songs use — no lambda syntax this early.
 */
val theTransformToolkitTutorial = Tutorial(
    slug = "the-transform-toolkit",
    title = Tut.theTransformToolkit,
    description = "fast, slow, superimpose, legato — everyday transforms that reshape a line without rewriting it.",
    difficulty = TutorialDifficulty.Intermediate,
    scope = TutorialScope.Quick,
    tags = listOf(TutorialTag.Patterns),
    teaches = listOf("fast", "slow", "superimpose", "legato", "clip"),
    sections = listOf(
        TutorialSection(
            heading = "The whole line, retimed: fast and slow",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    In ${Tut.subdivision}, `*2` doubled one step. `fast()` is the same multiplication applied to the whole line: `fast(2)` plays the entire pattern twice per cycle. `slow(2)` is its mirror — the pattern stretches over two cycles, and every note gets twice the room. (You already pointed `fast()` and `slow()` at signals in ${Tut.signalsMoveTheKnobs}; this is their day job.) Below, the melody wears `slow(2)` first; `fast(2)` and the plain line wait behind the `//`.

                    **Try it:** swap the `//` between the three lines and press Update each time. On the slow line, watch the player's counter — the melody now needs two clicks to come around.

                    **Listen for:** the same shape at three sizes. Half-time makes the melody calm and heavy; double-time turns it into a run. Nothing about the melody changed — only how much time it is given.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    note("a3 c4 d4 ~  e4 d4 c4 ~").sound("sine").gain(0.5).slow(2)    // half-time: the melody takes two cycles
                    // note("a3 c4 d4 ~  e4 d4 c4 ~").sound("sine").gain(0.5).fast(2) // double-time: twice per cycle — swap
                    // note("a3 c4 d4 ~  e4 d4 c4 ~").sound("sine").gain(0.5)         // the plain line — swap
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "A copy with a twist: superimpose",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    `superimpose()` plays the line — and, on top of it, a copy, run through a change. Below, the change is `transpose(12)` from ${Tut.scalesAndMelodies}: the copy sings one floor up, in step with the original. (Two copies add up — the practice from ${Tut.chordsInOneStep} — so the line, copy included, sits at 0.4 instead of the usual 0.5.)

                    **Try it:** swap the `//` to hear the line alone, and press Update. Then make it `transpose(-12)` — the copy moves underneath. Last, headphones on: make it `.pan(0.3).superimpose(transpose(12).pan(0.7))` — the original on the left, its octave copy on the right.

                    **Listen for:** not a second melody but a taller one. The copy hits every note together with the original — the octave from ${Tut.firstNotes}, worn as a coat.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    note("a3 c4 d4 ~  e4 d4 c4 ~").sound("sine").superimpose(transpose(12)).gain(0.4)    // the line plus its octave copy
                    // note("a3 c4 d4 ~  e4 d4 c4 ~").sound("sine").gain(0.5)                            // alone — swap to compare
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "How long a note holds: legato",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    Since ${Tut.firstNotes}, every note has held for its whole step — that was `legato(1)`, unwritten. `legato()` scales the held length: 0.5 cuts each note to half its step and leaves silence behind it; 2 holds it into the next step.

                    **Try it:** swap the `//` and press Update, and swap back. Then change `legato(0.5)` to `legato(2)` — the notes hold past their steps and begin to touch, length doing what release did in ${Tut.shapeOfANote}.

                    **Listen for:** the melody on tiptoe. Same notes, same places — but each one steps away early, and the silences carry as much groove as the notes. Short is a rhythm of its own.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    note("a3 c4 d4 ~  e4 d4 c4 ~").sound("sine").gain(0.5).legato(0.5)    // half-length notes: staccato
                    // note("a3 c4 d4 ~  e4 d4 c4 ~").sound("sine").gain(0.5)             // full-length — swap
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "One melody, three costumes",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    All three transforms on the one melody, over the groove from ${Tut.spaceAndRests}. One spelling change: the duration knob appears under its other name — `clip()` is `legato()`, letter for letter, and the songs spell it both ways.

                    **Try it:** delete one transform at a time — `.slow(2)`, then `.superimpose(transpose(12))`, then `.clip(0.5)` — pressing Update after each, and hear the melody step back toward plain.

                    **Listen for:** how far three words carried it. The counter needs two clicks per pass, the octave copy rides above, and every note is clipped short — the same eight steps you have carried since ${Tut.firstNotes}, wearing everything this lesson taught.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    stack(
                      sound("bd ~ hh ~  ~ hh sd ~").gain(0.8),      // the groove, at its usual speed
                      note("a3 c4 d4 ~  e4 d4 c4 ~").sound("sine")  // the melody from ${Tut.firstNotes}
                        .slow(2)                                    // half-time: two cycles per pass
                        .superimpose(transpose(12))                 // plus its octave copy
                        .clip(0.5)                                  // clip: the same function as legato — short notes
                        .gain(0.4)                                  // two copies add up
                    )
                    """.trimIndent(),
                ),
            ),
        ),
    ),
)
