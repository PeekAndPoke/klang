/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

/**
 * Curriculum slot A5 — see docs/tasks/tutorial-curriculum.md
 *
 * The merge lesson: patterns and sound design become one idea. Opens from the
 * stepped/inside/smooth progression A4's finale promised.
 *
 * Engine truth (verified in sprudel, review round 1): a control signal is read
 * ONCE PER NOTE, at the note's onset (_applyControl samples at whole.begin;
 * the voice fields are scalars). Hence §1 teaches the once-per-note rule, and
 * §4's pump chops the pad into sixteenths so the curve gets read — a held pad
 * reads it once and freezes. The saw signal resets to 0 at each period start,
 * so an ASCENDING range puts the duck on the drum; the pump's
 * range(0.34, 0.52) centres the two-note pad near B7's sanctioned 0.4. Do not
 * cite Sandsturm's bass-gain line as a pump precedent: its off-8th onsets all
 * read the saw at 0.5, making that gain effectively constant.
 */
val signalsMoveTheKnobsTutorial = Tutorial(
    slug = "signals-move-the-knobs",
    title = Tut.signalsMoveTheKnobs,
    description = "The waveforms return as slow curves that turn the knobs for you — tremolo, autopan, riser, pump.",
    difficulty = TutorialDifficulty.Intermediate,
    scope = TutorialScope.Quick,
    tags = listOf(TutorialTag.Synthesis, TutorialTag.Patterns),
    teaches = listOf("range", "pan"),
    previews = listOf("fast", "slow"),
    sections = listOf(
        TutorialSection(
            heading = "From steps to a slide",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    ${Tut.filters} stepped the cutoff cycle by cycle; ${Tut.theFilterEnvelope} moved it inside each note. This lesson is the third way: a signal slides a setting across the whole pattern.

                    A **signal** is one of the shapes from ${Tut.theFourWaveforms} doing a new job. In the line below, the word `sine` appears twice: inside `sound()` it is the voice you hear; bare, in front of `.range()`, it is a curve Klang follows — once per cycle, far too slow to be a tone. `.range(a, b)` says which numbers the curve travels between: its bottom becomes `a`, its top becomes `b`. One fact to keep: Klang reads the curve once for each note, at the moment the note starts — so the more notes a line plays, the finer the movement.

                    **Try it:** swap the `//` and press Update. (The moving line is quieter on average — judge the movement, not the level.)

                    **Listen for:** the melody breathing — every note lands a little louder or softer than its neighbours, tracing a swell and fade across the cycle; the effect musicians call **tremolo**. The loudness has become a curve, read note by note.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    note("a3 c4 d4 ~  e4 d4 c4 ~").sound("sine").gain(sine.range(0.2, 0.5))    // bare sine: the gain follows a slow curve
                    // note("a3 c4 d4 ~  e4 d4 c4 ~").sound("sine").gain(0.5)                  // a plain number — swap to compare
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "Left and right: pan",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    `pan()` places a line in the stereo field: 0 is left, 1 is right, 0.5 the middle. A fixed number parks the line; a signal walks it.

                    This one wants headphones — on a phone speaker the two sides collapse into one.

                    **Try it:** swap the `//` so the second line plays, and press Update. Then move the `//` again so the third line plays: `perlin` is a shape you have not met as a voice — a smooth wander that never repeats.

                    **Listen for:** the melody crossing from ear to ear once per cycle, one note at a time — **autopan**, by name. With `perlin` it stops keeping even that schedule and simply drifts.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    note("a3 c4 d4 ~  e4 d4 c4 ~").sound("sine").gain(0.5).pan(sine.range(0.1, 0.9))      // pan moves between left and right
                    // note("a3 c4 d4 ~  e4 d4 c4 ~").sound("sine").gain(0.5).pan(0.5)                    // fixed in the middle — swap
                    // note("a3 c4 d4 ~  e4 d4 c4 ~").sound("sine").gain(0.5).pan(perlin.range(0.1, 0.9)) // perlin: a wander with no schedule — swap
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "The riser",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    Now point a signal at the filter. The voice switches to `saw` — the same `saw` ${Tut.filters} worked on; a filter can only take away what is there. The `saw` shape as a signal climbs steadily, then snaps back to its start; `.slow(4)` stretches that climb over four cycles. (`fast()` and `slow()` are borrowed here — they speed any signal up or down; their full story is a Pattern-track lesson still to come.)

                    **Try it:** give the **riser** a good eight cycles — two full climbs — then swap the `//` and press Update, and swap back. (The climbing line is brighter and louder over most of its trip — judge the movement, not the level.)

                    **Listen for:** the blanket from ${Tut.filters} lifting on its own — four cycles of opening, then the snap back to muffled as the climb starts over — the build-up you know from club music.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    note("a3 c4 d4 ~  e4 d4 c4 ~").sound("saw").lpf(saw.slow(4).range(300, 4000)).gain(0.5)    // the cutoff rises for four cycles, then resets to 300
                    // note("a3 c4 d4 ~  e4 d4 c4 ~").sound("saw").lpf(800).gain(0.5)                          // parked at 800 — swap
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "The pump",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    Now the curve meets the beat. The pad below is the two-note comma shape from ${Tut.chordsInOneStep}, chopped into sixteenths with the `*` from ${Tut.subdivision} — one long chord would read the curve once and stand still; sixteen slices read it sixteen times. Its gain rides `saw.fast(4)`: the `saw`'s snap back lands on every count, so the pad drops to 0.34 exactly when a drum hits and creeps back toward 0.52 between them. (Two notes in one step add up — the practice from ${Tut.chordsInOneStep} — so the pump's numbers centre near 0.4 rather than 0.5.)

                    **Try it:** swap 0.34 and 0.52 and press Update — each count now starts loud and falls away instead. Put them back, then remove the `.fast(4)`: one duck per cycle, landing on the cycle's first kick alone.

                    **Listen for:** the pad getting out of the way each time a drum lands, kick or snare, and creeping back between them. This is the **pump** — the breathing loudness under most dance music.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    stack(
                      sound("bd ~ sd ~  bd ~ sd ~").gain(0.8),                              // the backbeat skeleton — one drum on every count
                      note("[a2,e3]*16").sound("sine").gain(saw.fast(4).range(0.34, 0.52))  // sixteenth slices; the gain dips on each count, rises back between
                    )
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "Three knobs, no hands",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    All three knobs from this lesson at once, at three different speeds: the pump ducks on every count, the pan crosses once per cycle, the riser climbs for four. Nothing new below — three signals from the sections above, layered with `stack()` from ${Tut.layers}.

                    **Try it:** put `//` in front of any one layer and press Update to hear the other two. Then pick one signal and change its range numbers by a little.

                    **Listen for:** the three speeds moving independently — count, cycle, four cycles. Nothing here is written out step by step; every level, position and cutoff is read off a curve.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    stack(
                      sound("bd ~ sd ~  bd ~ sd ~").gain(0.8),                                                                           // the skeleton, dry and steady
                      note("[a2,e3]*16").sound("sine").gain(saw.fast(4).range(0.34, 0.52)),                                              // the pump: the gain dips on every count
                      note("a3 c4 d4 ~  e4 d4 c4 ~").sound("saw").lpf(saw.slow(4).range(300, 4000)).gain(0.5).pan(sine.range(0.1, 0.9))  // riser + autopan on the melody
                    )
                    """.trimIndent(),
                ),
            ),
        ),
    ),
)
