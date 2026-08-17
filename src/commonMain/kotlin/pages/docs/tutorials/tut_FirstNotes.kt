/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

/** Curriculum slot B3 — see docs/tasks/tutorial-curriculum.md */
val firstNotesTutorial = Tutorial(
    slug = "first-notes",
    title = Tut.firstNotes,
    description = "From drums to melody: note names, octaves, and a first melody of your own.",
    difficulty = TutorialDifficulty.Beginner,
    scope = TutorialScope.Quick,
    tags = listOf(TutorialTag.Melody, TutorialTag.GettingStarted),
    teaches = listOf("note"),
    previews = listOf("@"),
    sections = listOf(
        TutorialSection(
            heading = "From drums to pitch",
            text = "note() works exactly like sound() — names in quotes, spaces splitting the cycle into " +
                "steps, rests with ~ — but its names are pitches: \"c3\" is the note C in octave 3.\n\n" +
                "The .sound(\"sine\") behind it is the same sound() you used on the drums, doing a new " +
                "job. With a drum name it plays a recording; with \"sine\" there is no recording — Klang " +
                "generates the tone itself, at whatever pitch note() asks for. " +
                "Sine is the plainest voice Klang has, a pure tone with nothing added; " +
                "its siblings — saw, square, triangle — get their own lesson: " +
                "${Tut.theFourWaveforms}.\n\nListen for: " +
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
                note("c3 d3 e3 f3  g3 a3 b3 c4")  // up the seven letters, back to c
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
                note("c3 d3 eb3 f3  g3 a3 b3 c4").sound("sine").gain(0.5)    // the ladder — e3 lowered to eb3
                // note("c3 d3 e3 f3  g3 a3 b3 c4").sound("sine").gain(0.5)  // the original — swap to compare
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
                "its own lesson: ${Tut.shapeOfANote}.)\n\nListen for: the shape. Leap up, walk " +
                "to the top, walk back down to where it can start again.\n\nTry it: move a note up or " +
                "down one letter, or trade a rest for a note, and press Update. If a change sounds " +
                "wrong, change it back — that back-and-forth is how melodies actually get written. " +
                "This melody was also written to sit on top of the groove you shaped in " +
                "${Tut.spaceAndRests}; playing two lines at once is its own Pattern-track lesson: " +
                "Layers.",
            code = """
                note("a3 c4 d4 ~  e4 d4 c4 ~")  // leap up, walk to the top, walk back down
                  .sound("sine")                // the plain voice carries it
                  .gain(0.5)                    // pure tones read loud — 0.5 is plenty
            """.trimIndent(),
        ),
        TutorialSection(
            heading = "Holding a note",
            text = "One more version, for a question you may already be asking: how do you hold a " +
                "note instead of resting after it? Put @2 behind it — that note then takes two " +
                "steps' worth of time. Here the two rests are gone; d4 and c4 hold through the " +
                "space instead. (@ is borrowed from a later lesson — ${Tut.alternationAndRepetition} " +
                "tells its full story; here it is simply the answer to the question.)\n\nTry it: " +
                "swap the // to compare holding against resting.\n\nListen for: the same melody, " +
                "now unbroken — where the silence was, the long note carries through.",
            code = """
                note("a3 c4 d4@2  e4 d4 c4@2").sound("sine").gain(0.5)    // @2: the note holds two steps' worth
                // note("a3 c4 d4 ~  e4 d4 c4 ~").sound("sine").gain(0.5) // the rest version — swap to compare
            """.trimIndent(),
        ),
    ),
)
