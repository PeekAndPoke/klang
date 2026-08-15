/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

/** Curriculum slot B3 — see docs/tasks/tutorial-curriculum.md */
val firstNotesTutorial = Tutorial(
    slug = "first-notes",
    title = "First Notes",
    description = "From drums to melody: note names, octaves, and a first melody of your own.",
    track = TutorialTrack.Pattern,
    difficulty = TutorialDifficulty.Beginner,
    scope = TutorialScope.Quick,
    tags = listOf(TutorialTag.Melody, TutorialTag.GettingStarted),
    teaches = listOf("note"),
    sections = listOf(
        TutorialSection(
            heading = "From drums to pitch",
            text = "note() works exactly like sound() — names in quotes, spaces splitting the cycle into " +
                "steps, rests with ~ — but its names are pitches: \"c3\" is the note C in octave 3.\n\n" +
                "The .sound(\"sine\") behind it is the same sound() you used on the drums, doing a new " +
                "job. With a drum name it plays a recording; with \"sine\" there is no recording — Klang " +
                "generates the tone itself, at whatever pitch note() asks for. (Same dot-chain rule as " +
                "gain(): it attaches to the pattern above it — this time picking the voice instead of " +
                "the volume.) Sine is the plainest voice Klang has, a pure tone with nothing added; " +
                "its siblings — saw, square, triangle — get their own lesson soon.\n\nListen for: " +
                "four pitches climbing. The grid is the one you already know from the drums — the " +
                "steps just have height now.",
            code = """
                note("c3 e3 g3 c4")  // four pitches, four steps — the rule you already know
                  .sound("sine")     // the voice that sings them: a generated pure tone
                  .gain(0.5)         // pure tones read loud — keep them around 0.5
            """.trimIndent(),
        ),
        TutorialSection(
            heading = "The seven letters",
            text = "Notes are named with seven letters — c d e f g a b — and then the ladder starts " +
                "over. This line climbs all seven and arrives at c again.\n\nListen for: the last step. " +
                "After b3, the c4 does not sound like a new place — it sounds like coming home, one " +
                "floor up. That \"same but higher\" feeling is the octave, and it is why the letters " +
                "repeat.",
            code = """
                note("c3 d3 e3 f3 g3 a3 b3 c4")  // up the seven letters, back to c
                  .sound("sine").gain(0.5)
            """.trimIndent(),
        ),
        TutorialSection(
            heading = "The octave number",
            text = "The number after the letter picks the floor: c3 is low, c5 is high. Same letter, " +
                "same note — different height.\n\nTry it: swap the // between the two lines and press " +
                "Update.\n\n" +
                "Listen for: the first line is one note moving between floors; the second walks " +
                "between neighbouring notes on one floor. That is the octave from the last section, side by " +
                "side with ordinary movement.\n\nOctaves 1 and 2 are bass territory; 3 and 4 are where melodies usually " +
                "live; 5 and up cuts through everything.\n\nTry it: swap back to the first line, then " +
                "drop its opening c3 to c2 and press " +
                "Update — on small speakers it nearly vanishes. That is the speaker's limit, not the " +
                "note's.",
            code = """
                note("c3 c4 c5 c4").sound("sine").gain(0.5)     // the same note on three floors, there and back
                // note("c4 d4 e4 d4").sound("sine").gain(0.5)  // neighbour notes on one floor — swap to compare
            """.trimIndent(),
        ),
        TutorialSection(
            heading = "The notes between",
            text = "The seven letters are not the whole ladder. Between most of them sits one more note — " +
                "the black keys on a piano. You write them by adding s for sharp — raising the note " +
                "into the gap above it — or b for flat, lowering it into the gap below: cs3 sits " +
                "between c3 and d3, eb3 between d3 and e3. " +
                "(Between e and f, and between b and c, there is none — that unevenness is what gives " +
                "the ladder its shape.)\n\nThe live line is the ladder with one change: e3 dropped " +
                "to eb3.\n\nTry it: swap the // against the original.\n\nListen for: the third step " +
                "lands lower — into the crack between d3 and e3 — and the whole ladder suddenly " +
                "sounds darker.\n\nTry it: raise the c3 to cs3 and press Update — the sharp is the " +
                "same move pointing the other way. Which of these in-between notes belong together " +
                "with which letters is what scales are about; that is a later lesson.",
            code = """
                note("c3 d3 eb3 f3 g3 a3 b3 c4").sound("sine").gain(0.5)    // the ladder — e3 lowered to eb3
                // note("c3 d3 e3 f3 g3 a3 b3 c4").sound("sine").gain(0.5)  // the original — swap to compare
            """.trimIndent(),
        ),
        TutorialSection(
            heading = "Steps and leaps",
            text = "Melodies move in two ways: to a neighbouring letter, or further. Musicians call " +
                "the first a step — a different sense of the word than the pattern steps from the " +
                "drum lessons: here it means the next letter up or down. Anything bigger is a leap.\n\n" +
                "Try it: swap the // between the two lines and press Update.\n\nListen for: steps walk — " +
                "smooth, speech-like, easy to follow. Leaps sing — dramatic, attention-grabbing. " +
                "Most melodies you love are mostly steps with one or two well-placed leaps; the leap " +
                "is the moment you remember.",
            code = """
                note("c4 d4 e4 d4").sound("sine").gain(0.5)     // steps: neighbour to neighbour
                // note("c4 g4 e4 c5").sound("sine").gain(0.5)  // leaps: every move bigger than one letter
            """.trimIndent(),
        ),
        TutorialSection(
            heading = "A first melody",
            text = "Steps, one leap, and two rests — the ideas this melody is built on, in one line. Let it loop; it is written to circle back on itself. The walking part in " +
                "the middle is the step line from the last section — c4, d4, e4 — turned around at " +
                "its top, with a leap up from a3 in front and two ~ where the melody breathes. (Each " +
                "note holds for its whole step and then stops; shaping how a note starts and fades is " +
                "its own lesson, coming in the Sound track.)\n\nListen for: the shape. Leap up, walk " +
                "to the top, walk back down to where it can start again.\n\nTry it: move a note up or " +
                "down one letter, or trade a rest for a note, and press Update. If a change sounds " +
                "wrong, change it back — that back-and-forth is how melodies actually get written. " +
                "This melody was also written to sit on top of the groove you shaped last lesson; " +
                "playing two lines at once comes later in the Pattern track, in the Layers lesson.",
            code = """
                note("a3 c4 d4 ~ e4 d4 c4 ~")  // leap up, walk to the top, walk back down
                  .sound("sine")               // the plain voice carries it
                  .gain(0.5)                   // pure tones read loud — 0.5 is plenty
            """.trimIndent(),
        ),
    ),
)
