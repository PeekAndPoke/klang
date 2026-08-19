/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

/**
 * Curriculum slot B8 — see docs/tasks/tutorial-curriculum.md
 *
 * Keeps B3's deferred promises: which in-between notes belong to which letters
 * (the scale answers), and the major/minor mood A/B (0 2 4 on c4 = c4 e4 g4 vs
 * c4 eb4 g4). Continuity facts verified against the scale tables: the B3/B6
 * melody IS n("0 2 3 ~  4 3 2 ~").scale("a3:minor"), the B6 bass IS
 * n("0 ~ ~ 0  ~ ~ -3 ~").scale("a2:minor"), and rung -3 stays e2 on a2:major
 * too (the fifth below sits on both ladders). §1's A/B is identical by design
 * and narrated as such — a sanctioned exception to the render-QA audibility
 * gate; do not "fix" it.
 */
val scalesAndMelodiesTutorial = Tutorial(
    slug = "scales-and-melodies",
    title = Tut.scalesAndMelodies,
    description = "Numbers instead of note names: the scale decides what the numbers mean, and the whole song can change its mind.",
    difficulty = TutorialDifficulty.Intermediate,
    scope = TutorialScope.Quick,
    tags = listOf(TutorialTag.Melody, TutorialTag.Patterns),
    teaches = listOf("n", "scale", "transpose"),
    sections = listOf(
        TutorialSection(
            heading = "Numbers on the ladder: n",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    `n()` plays **rungs** of a **ladder** by number, and `scale()` names the ladder: `0` is the home rung, counting upward. The pair below is the seven-letter climb from ${Tut.firstNotes}, written twice: as note names, and as numbers on the `c3:major` ladder. (The two lines sound identical on purpose: the numbers are the same notes.)

                    **Try it:** swap the `//` to convince your ear, press Update, and swap back. Then, on the number line, change the `7` to `9`. The ladder keeps going past the octave.

                    **Listen for:** no difference between the two spellings. What the numbers buy you is not a new sound; it is that the melody no longer says which notes it wants, only which rungs. The ladder decides the rest.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    n("0 1 2 3  4 5 6 7").scale("c3:major").sound("sine").gain(0.5)    // the climb as numbers: 0 is c3, 7 is c4
                    // note("c3 d3 e3 f3  g3 a3 b3 c4").sound("sine").gain(0.5)        // the climb from ${Tut.firstNotes}, swap
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "Swap the ladder",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    Here is the payoff. In ${Tut.firstNotes} you lowered `e3` into the crack below it and the whole line darkened: a choice made by hand, one note at a time. A scale bundles those choices: minor lowers three of the seven letters, and the ladder makes the choice for every note at once.

                    The same three numbers below (`0 2 4`) land on `c4 eb4 g4` on the minor ladder, and on `c4 e4 g4` on the major one. Only rung `2` moves, and it is the one you hear most.

                    **Try it:** swap the `//` and press Update, a few times, until the difference is unmistakable.

                    **Listen for:** the mood flip. Same numbers, same rhythm. One lowered letter, and the line goes from overcast to sunlit and back as you swap. This single choice, major or minor, is the loudest word in a song's vocabulary.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    n("0 2 4 2  0 2 4 2").scale("c4:minor").sound("sine").gain(0.5)    // minor: the numbers land on c4 eb4 g4
                    // n("0 2 4 2  0 2 4 2").scale("c4:major").sound("sine").gain(0.5) // major: c4 e4 g4, swap to compare
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "The melody, renumbered",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    The melody you have carried since ${Tut.firstNotes} was walking a ladder the whole time: `a3 c4 d4 e4` are rungs `0 2 3 4` of `a3:minor`. Written as numbers, its shape is suddenly visible in the code: the leap from `0` to `2`, the walk up to `4`, the walk back down.

                    **Try it:** change the scale to `a3:major` and press Update. The shape stays; one of its letters rises (the `c4` becomes `cs4`), and the melody turns sunnier. Change it back and hear it settle.

                    **Listen for:** the same melody both times, and not quite. The shape stayed; the ladder changed underneath it.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    n("0 2 3 ~  4 3 2 ~").scale("a3:minor")    // the melody from ${Tut.firstNotes}: leap to 2, up to 4, back down
                      .sound("sine").gain(0.5)                 // voice and level as always
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "The whole ladder moves: transpose",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    `transpose()` shifts every note by **semitones**: the smallest move there is, from any note to the very next one, cracks included. Twelve of them make one octave. (A semitone is smaller than the walking step of ${Tut.firstNotes}: a step to the next letter crosses one semitone or two, depending on the letters.) So `transpose(12)` lifts the whole line one floor up, and `transpose(-12)` drops it one floor down.

                    It also takes a pattern. With the angle brackets from ${Tut.alternationAndRepetition}, `"<0 12>"` plays the melody on its home floor one cycle and one floor up the next.

                    **Try it:** swap the `//` and press Update, and swap back. Then make it `"<0 -12>"`. The visits go down instead.

                    **Listen for:** the same melody taking turns between two floors, cycle by cycle: the octave from ${Tut.firstNotes}, now a moving part.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    n("0 2 3 ~  4 3 2 ~").scale("a3:minor").sound("sine").gain(0.5).transpose("<0 12>")    // home floor, then one floor up, in turns
                    // n("0 2 3 ~  4 3 2 ~").scale("a3:minor").sound("sine").gain(0.5)                     // home floor only, swap
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "The tiny mix, in one language",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    The layers from ${Tut.layers}, rewritten as numbers: dry, one thing at a time. The bass turns out to live on its own ladder one floor below the melody: rung `0` is `a2`, and rung `-3` sits three rungs down, at `e2`. Two ladders, one family: `a2:minor` under `a3:minor`.

                    **Try it:** turn both scales to major and press Update. The bass keeps its two notes (they sit on both ladders) while the melody lifts its `c4` to `cs4`: the whole song brightens on two word-swaps. Then put `//` in front of one layer at a time to hear each pair alone, the practice from ${Tut.layers}.

                    **Listen for:** how little you typed and how much moved. The notes come from the ladder now; the patterns only say when.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    stack(
                      sound("bd ~ hh ~  ~ hh sd ~").gain("0.8 0.8 0.4 0.8  0.8 0.5 0.8 0.8"),  // the accented groove from ${Tut.spaceAndRests}
                      n("0 ~ ~ 0  ~ ~ -3 ~").scale("a2:minor").sound("sine").gain(0.6),        // ${Tut.layers}' bass: 0 is a2, -3 is the e2 below it
                      n("0 2 3 ~  4 3 2 ~").scale("a3:minor").sound("sine").gain(0.5)          // the melody on its ladder
                    )
                    """.trimIndent(),
                ),
            ),
        ),
    ),
)
