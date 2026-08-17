/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

/** Curriculum slot A2 — see docs/tasks/tutorial-curriculum.md */
val shapeOfANoteTutorial = Tutorial(
    slug = "shape-of-a-note",
    title = Tut.shapeOfANote,
    description = "ADSR: four numbers that turn one voice into a pluck, an organ, or a pad.",
    difficulty = TutorialDifficulty.Beginner,
    scope = TutorialScope.Quick,
    tags = listOf(TutorialTag.Synthesis),
    teaches = listOf("adsr"),
    sections = listOf(
        TutorialSection(
            heading = "The shape you have been hearing",
            blocks = listOf(
                Block.Text(
                    "So far, every note held for its whole step and then stopped — that was a shape " +
                    "all along, just a hidden default. adsr() writes it out: four numbers separated by " +
                    "colons — attack (seconds to fade in), decay (seconds to settle), sustain (the level " +
                    "it holds while the note lasts, 0 to 1), release (seconds to fade out after the note " +
                    "ends).\n\nThe melody from ${Tut.firstNotes} is too quick for shapes this size, so " +
                    "this lesson stretches its opening leap — a3 up to c4 — into two long notes with " +
                    "room around them.\n\nListen for: nothing new — this is exactly the organ-like shape " +
                    "every note has worn since your first note in ${Tut.firstNotes}, now visible in the code. " +
                    "The next three sections take it apart, one change at a time.",
                ),
                Block.Code(
                    code = """
                    note("a3 ~ c4 ~")              // the melody's opening leap, stretched long
                      .sound("saw")                // the bright voice from ${Tut.theFourWaveforms}
                      .adsr("0.01:0.1:1:0.05")     // the default shape, written out: on, hold, off
                      .gain(0.5)                   // kept modest — the stages are easier to hear
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "Attack — how a note begins",
            blocks = listOf(
                Block.Text(
                    "The first number is the fade-in, in seconds. Nearly zero means the note " +
                    "switches on; longer means it swells in.\n\nTry it: swap the // between the two " +
                    "lines and press Update.\n\nListen for: the snap of an instant beginning against " +
                    "the breath of a slow one. Attack alone decides whether a sound feels struck or " +
                    "blown — most of what makes a piano a piano and a flute a flute happens in these " +
                    "first milliseconds.",
                ),
                Block.Visual.Adsr("0.3:0.1:1:0.05"),
                Block.Code(
                    code = """
                    note("a3 ~ c4 ~").sound("saw").adsr("0.3:0.1:1:0.05").gain(0.5)      // slow attack: the note swells in
                    // note("a3 ~ c4 ~").sound("saw").adsr("0.001:0.1:1:0.05").gain(0.5) // instant attack: switched on
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "Decay and sustain — what it settles to",
            blocks = listOf(
                Block.Text(
                    "Sustain is the odd one out: it is a level, not a time. After the attack, the " +
                    "note slides — taking decay seconds — down to the sustain level, and holds there " +
                    "for the rest of the step. Set sustain to 0 and the note dies away while it is " +
                    "still being held.\n\nTry it: swap the // and press Update — only the sustain " +
                    "number differs between the two lines. Then, with the first line live, change the " +
                    "decay from 0.4 to 0.05 and press Update again: the same fall, eight times faster. " +
                    "Decay is how long the drop takes; sustain is where it lands.\n\nListen for: " +
                    "falling-away against steady. The first line is struck and fades while held; the " +
                    "second stands like something powered. Same voice, same notes — only the middle of " +
                    "the shape changed.",
                ),
                Block.Visual.Adsr("0.001:0.4:0:0.05"),
                Block.Code(
                    code = """
                    note("a3 ~ c4 ~").sound("saw").adsr("0.001:0.4:0:0.05").gain(0.5)    // sustain 0: dies away while held
                    // note("a3 ~ c4 ~").sound("saw").adsr("0.001:0.4:1:0.05").gain(0.5) // sustain 1: holds steady
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "Release — what remains",
            blocks = listOf(
                Block.Text(
                    "The last number is the fade-out after the note's step ends. The default 0.05 " +
                    "stops almost dead; a long release lets the note ring on. The rests give the tail " +
                    "room — and with a release this long, the tail outlives them: the next note arrives " +
                    "on top of the last one's fading memory. Two notes sounding at once, from a line " +
                    "that plays them one at a time.\n\nTry it: swap the // and press Update.\n\n" +
                    "Listen for: the note breathing past its own step. The rest after each note is no " +
                    "longer empty — and at the end of it, the notes begin to touch.",
                ),
                Block.Visual.Adsr("0.001:0.1:0.8:1.2"),
                Block.Code(
                    code = """
                    note("a3 ~ c4 ~").sound("saw").adsr("0.001:0.1:0.8:1.2").gain(0.5)     // long release: rings into the rest
                    // note("a3 ~ c4 ~").sound("saw").adsr("0.001:0.1:0.8:0.05").gain(0.5) // short release: stops almost dead
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "Pluck, organ, pad",
            blocks = listOf(
                Block.Text(
                    "Three instruments, one voice, one phrase — nothing changes below but the four " +
                    "numbers.\n\nTry it: give each line a few loops.\n\nListen for: how completely the " +
                    "shape is the instrument. The pluck is struck and gone, the organ is on while held, " +
                    "the pad swells in and rings out. When you can hear a sound in your head and reach " +
                    "for the four numbers that make it, this lesson has done its job — start from one " +
                    "of these and bend it.\n\n(Loudness is only half of a note's life; the other half " +
                    "is colour over time, and that is its own Sound-track lesson: filters.)",
                ),
                Block.Code(
                    code = """
                    note("a3 ~ c4 ~").sound("saw").adsr("0.001:0.3:0:0.1").gain(0.5)      // pluck: struck, then gone
                    // note("a3 ~ c4 ~").sound("saw").adsr("0.01:0.1:1:0.05").gain(0.5)   // organ: on while held
                    // note("a3 ~ c4 ~").sound("saw").adsr("0.25:0.15:0.8:1.2").gain(0.5) // pad: swells in, rings out
                    """.trimIndent(),
                ),
            ),
        ),
    ),
)
