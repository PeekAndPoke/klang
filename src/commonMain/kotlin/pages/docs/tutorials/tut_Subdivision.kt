/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

/**
 * Curriculum slot B4 — see KLANG-00145 (tutorial curriculum)
 *
 * The finale's A/B pair is IDENTICAL BY DESIGN: "[bd hh] [sd hh] [bd hh] [sd oh]" and
 * "bd hh sd hh bd hh sd oh" are two spellings of the same beat, and hearing no
 * difference IS the lesson. Registered as a sanctioned exception to the render-QA
 * audibility gate — do not "fix" it. The §1, §2 and §4 pairs differ in hit count
 * (density is the concept), also sanctioned; §3 differs in placement only and
 * passes parity on its own.
 */
val subdivisionTutorial = Tutorial(
    slug = "subdivision",
    title = Tut.subdivision,
    description = "Split a single step with [ ] and *, and the eight-step grid explains itself.",
    difficulty = TutorialDifficulty.Beginner,
    depth = TutorialDepth.Quick,
    tags = listOf(TutorialTag.Rhythm, TutorialTag.Patterns),
    teaches = listOf("[]", "*"),
    sections = listOf(
        TutorialSection(
            heading = "A step that splits",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    **Square brackets** take a single step and divide it: everything inside `[ ]` shares that one step equally, and the rest of the pattern does not move. Four steps below, but the second one now holds two hats.

                    **Try it:** play the first example, then the second.

                    **Listen for:** the count. It is one-two-three-four in both versions (the kick and snare never move), but in the first, count two carries a double hat, its second half landing on the off-beat, the spot the hats sat in ${Tut.spaceAndRests}. The cycle did not gain a step; one step gained an inside.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    sound("bd [hh hh] sd hh")  // count two split in half, the grid unchanged
                      .gain(0.8)
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    sound("bd hh sd hh")  // the plain four steps, to compare
                      .gain(0.8)
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "Any number inside",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    Two things inside the brackets split the step in half; three split it in thirds. Any number works, and the neighbours never move.

                    **Try it:** play the first example, then the second: three hats against two.

                    **Listen for:** three hits where there were two: a quick roll inside a single count. The four-count never wavers; the crowding happens under it.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    sound("bd [hh hh hh] sd hh")  // three in the slot: a roll inside count two
                      .gain(0.8)
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    sound("bd [hh hh] sd hh")  // two in the slot, to compare
                      .gain(0.8)
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "Brackets inside brackets",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    A split can itself be split. In the first example, the second half of count two is divided again, with one hat in the first half of the step and two crammed into the second: a gallop.

                    **Try it:** play both examples to compare the even roll against the gallop.

                    **Listen for:** the roll is even; the gallop limps on purpose: long-short-short. Same step, same total time, different inner shape.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    sound("bd [hh [hh hh]] sd hh")  // a gallop: half a step, then two quarter-steps
                      .gain(0.8)
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    sound("bd [hh hh hh] sd hh")  // the even roll, to compare
                      .gain(0.8)
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "The shorthand: *",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    Splitting a step into equal copies of the same sound is so common it has its own notation: `hh*2` means `[hh hh]`, `hh*4` means four of them. The first example below sounds identical to the first example of this lesson: the same pattern, written shorter.

                    **Try it:** play the second example to hear four in the slot, then edit the number to `*3` and `*8` and press **Update**.

                    **Listen for:** at `*4` the split step turns into a rush, each hat just a sixteenth of the cycle. And still, the kick and snare stand exactly where they always stood. **Subdivision** adds detail inside the grid, never pressure on it.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    sound("bd hh*2 sd hh")  // same sound as [hh hh], shorter to write
                      .gain(0.8)
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    sound("bd hh*4 sd hh")  // four in the slot, to compare
                      .gain(0.8)
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "The grid, explained",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    Now a secret about everything you have played so far: the eight-step grid from ${Tut.yourFirstBeat} and ${Tut.spaceAndRests} can be read as subdivision: four counts, each split in two. The first example below is the very first beat of this course, spelled the other way round.

                    **Try it:** play both examples; they sound identical.

                    **Listen for:** nothing moving. Play one after the other and watch the counter: no stutter, no shift, not one hit landing early. Two spellings, one beat. From here on you can think in counts and split them exactly as deep as a groove needs. ${Tut.alternationAndRepetition} turns the trick outward: whole cycles taking turns.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    sound("[bd hh] [sd hh] [bd hh] [sd oh]")  // four counts, each split in two
                      .gain(0.8)
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    sound("bd hh sd hh  bd hh sd oh")  // the very first beat, as eight steps
                      .gain(0.8)
                    """.trimIndent(),
                ),
            ),
        ),
    ),
)
