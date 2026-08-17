/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

/**
 * Curriculum slot B4 — see docs/tasks/tutorial-curriculum.md
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
    scope = TutorialScope.Quick,
    tags = listOf(TutorialTag.Rhythm, TutorialTag.Patterns),
    teaches = listOf("[]", "*"),
    sections = listOf(
        TutorialSection(
            heading = "A step that splits",
            text = "Square brackets take a single step and divide it: everything inside [ ] shares " +
                "that one step equally, and the rest of the pattern does not move. Four steps " +
                "below, but the second one now holds two hats.\n\nTry it: swap the // between the " +
                "two lines and press Update.\n\nListen for: the count. It is one-two-three-four in " +
                "both versions — the kick and snare never move — but in the first, count two " +
                "carries a double hat, its second half landing on the off-beat, the spot the hats " +
                "sat in ${Tut.spaceAndRests}. The cycle did not gain a step; one step gained an inside.",
            code = """
                sound("bd [hh hh] sd hh").gain(0.8)  // count two split in half — the grid unchanged
                // sound("bd hh sd hh").gain(0.8)    // the plain four steps — swap to compare
            """.trimIndent(),
        ),
        TutorialSection(
            heading = "Any number inside",
            text = "Two things inside the brackets split the step in half; three split it in " +
                "thirds. Any number works, and the neighbours never move.\n\nTry it: swap the // " +
                "between two hats and three.\n\nListen for: three hits where there were two — a " +
                "quick roll inside a single count. The four-count never wavers; the crowding " +
                "happens under it.",
            code = """
                sound("bd [hh hh hh] sd hh").gain(0.8) // three in the slot: a roll inside count two
                // sound("bd [hh hh] sd hh").gain(0.8) // two in the slot — swap to compare
            """.trimIndent(),
        ),
        TutorialSection(
            heading = "Brackets inside brackets",
            text = "A split can itself be split. In the live line, the second half of count two is " +
                "divided again — one hat in the first half of the step, two crammed into the " +
                "second: a gallop.\n\nTry it: swap the // to compare the even roll against the " +
                "gallop.\n\nListen for: the roll is even; the gallop limps on purpose — " +
                "long-short-short. Same step, same total time, different inner shape.",
            code = """
                sound("bd [hh [hh hh]] sd hh").gain(0.8)  // a gallop: half a step, then two quarter-steps
                // sound("bd [hh hh hh] sd hh").gain(0.8) // the even roll — swap to compare
            """.trimIndent(),
        ),
        TutorialSection(
            heading = "The shorthand: *",
            text = "Splitting a step into equal copies of the same sound is so common it has its " +
                "own notation: hh*2 means [hh hh], hh*4 means four of them. The live line below " +
                "sounds identical to the first line of this lesson — the same pattern, written " +
                "shorter.\n\nTry it: swap the // to hear four in the slot — then try *3, and " +
                "*8.\n\nListen for: at *4 the split step turns into a rush, each hat just a " +
                "sixteenth of the cycle — and still, the kick and snare stand exactly where they " +
                "always stood. Subdivision adds detail inside the grid, never pressure on it.",
            code = """
                sound("bd hh*2 sd hh").gain(0.8)    // same sound as [hh hh], shorter to write
                // sound("bd hh*4 sd hh").gain(0.8) // four in the slot — swap to compare
            """.trimIndent(),
        ),
        TutorialSection(
            heading = "The grid, explained",
            text = "Now a secret about everything you have played so far: the eight-step grid from " +
                "${Tut.yourFirstBeat} and ${Tut.spaceAndRests} can be read as subdivision — four counts, " +
                "each split in two. The live line below is the very first beat of this course, " +
                "spelled the other way round.\n\nTry it: swap the // against its eight-name " +
                "spelling — they sound identical.\n\nListen for: nothing moving. Swap while it " +
                "plays and watch the counter — no stutter, no shift, not one hit landing early. " +
                "Two spellings, one beat. From here on you can think in counts and split them " +
                "exactly as deep as a groove needs. ${Tut.alternationAndRepetition} turns the trick " +
                "outward: whole cycles taking turns.",
            code = """
                sound("[bd hh] [sd hh] [bd hh] [sd oh]").gain(0.8)  // four counts, each split in two
                // sound("bd hh sd hh  bd hh sd oh").gain(0.8)      // the very first beat, as eight steps
            """.trimIndent(),
        ),
    ),
)
