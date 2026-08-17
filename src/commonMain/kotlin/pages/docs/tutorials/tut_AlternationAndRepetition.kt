/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

/** Curriculum slot B5 — see docs/tasks/tutorial-curriculum.md */
val alternationAndRepetitionTutorial = Tutorial(
    slug = "alternation-and-repetition",
    title = Tut.alternationAndRepetition,
    description = "Angle brackets give each cycle its own content — patterns that outgrow a single cycle.",
    difficulty = TutorialDifficulty.Beginner,
    scope = TutorialScope.Quick,
    tags = listOf(TutorialTag.Patterns, TutorialTag.Melody),
    teaches = listOf("<>", "!", "@"),
    sections = listOf(
        TutorialSection(
            heading = "One per cycle: < >",
            blocks = listOf(
                Block.Text(
                    "Angle brackets are the opposite of a sequence: instead of splitting one cycle " +
                    "between all their entries, they play one entry per cycle and rotate. The line " +
                    "below holds four bass notes but plays a single note each time around — it takes " +
                    "four cycles to hear them all.\n\nTry it: change the c3 to c4 and press Update — " +
                    "only one cycle in four changes.\n\nListen for: the pitch at each restart — a2, " +
                    "then c3, then g2, then e2, one per turn of the counter. The pattern is bigger " +
                    "than the cycle now; that is new.",
                ),
                Block.Code(
                    code = """
                    note("<a2 c3 g2 e2>")            // four entries, one per cycle — four cycles to rotate
                      .sound("saw")                  // the bright voice from ${Tut.theFourWaveforms}
                      .adsr("0.001:0.3:0:0.1")       // the pluck shape from ${Tut.shapeOfANote}
                      .gain(0.5)                     // synth voices sit around 0.5
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "A bar per cycle",
            blocks = listOf(
                Block.Text(
                    "${Tut.yourFirstBeat} set the musician's word bar aside, so there was one name for " +
                    "the window in time. It comes back now with a different job: the cycle is the " +
                    "container, and a bar is one cycle's worth of notes — with < >, a pattern can " +
                    "hold several bars and deal them out in turn. This is how real songs are written " +
                    "in Klang: a handful of bars between the angle brackets, taking turns.\n\nTry it: " +
                    "swap the // to compare rotating bars against one repeating bar.\n\nListen for: " +
                    "cycle one circles a2; cycle two lifts the home note to c3 and comes back — and " +
                    "the g2 in third place stays put in both, the hinge the two bars turn on.",
                ),
                Block.Code(
                    code = """
                    note("<[a2 a2 g2 a2] [c3 c3 g2 c3]>").sound("saw").adsr("0.001:0.3:0:0.1").gain(0.5) // two bars, one per cycle
                    // note("[a2 a2 g2 a2]").sound("saw").adsr("0.001:0.3:0:0.1").gain(0.5)              // one bar, repeating
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "Say it again: !",
            blocks = listOf(
                Block.Text(
                    "The exclamation mark clones a step in place: a2!3 means a2 a2 a2, as three " +
                    "real steps. It looks like the * from ${Tut.subdivision} but does the opposite job: " +
                    "! multiplies steps; * squeezes copies inside a single step and leaves the step " +
                    "count alone. That is why g2 moves in one line and not the other — a2!3 turns two " +
                    "steps into four, so g2 shrinks to a quarter of the cycle; a2*3 keeps two steps, " +
                    "so g2 keeps its half. (Alone, with no neighbour to push around, the two " +
                    "sound the same — the difference needs company.)\n\nTry it: swap the // " +
                    "and hear the same three a2s land completely differently.\n\nListen for: the " +
                    "first line walks — four even steps, three of them a2. The second stumbles — " +
                    "three quick a2s in the first half, then g2 alone with the whole second half to " +
                    "itself. (The steps here are half a cycle wide, far bigger than the hat slots in " +
                    "${Tut.subdivision} — that is why the same *3 sounds unhurried.)",
                ),
                Block.Code(
                    code = """
                    note("a2!3 g2").sound("saw").adsr("0.001:0.3:0:0.1").gain(0.5)    // !3: three real steps — four steps total
                    // note("a2*3 g2").sound("saw").adsr("0.001:0.3:0:0.1").gain(0.5) // *3: three inside one step — two steps total
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "Leaning on a note: @",
            blocks = listOf(
                Block.Text(
                    "The at-sign gives a step extra weight: a2@3 takes three shares of the cycle " +
                    "where its neighbour takes one. ! gives a note more turns; @ gives its step more " +
                    "room.\n\nTry it: swap the // to compare the lean against an even split.\n\n" +
                    "Listen for: when g2 arrives. In the first line a2 holds the floor for three " +
                    "quarters of the cycle and g2 slips in right at the end — a lean. In the second, " +
                    "the two notes share the cycle half and half. (The pluck itself does not ring any " +
                    "longer — sustain 0 sees to that, as you heard in ${Tut.shapeOfANote}. @ " +
                    "stretches the step, and everything after it moves.)",
                ),
                Block.Code(
                    code = """
                    note("a2@3 g2").sound("saw").adsr("0.001:0.3:0:0.1").gain(0.5)  // @3: a2's step takes three shares of four — g2 waits
                    // note("a2 g2").sound("saw").adsr("0.001:0.3:0:0.1").gain(0.5) // even: half and half
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "A four-bar bassline",
            blocks = listOf(
                Block.Text(
                    "Everything from this lesson in one line: bars between < >, the first one " +
                    "doubled with !2, and each bar built on a first note that holds the floor before " +
                    "two quicker steps. Two turns on a, a lift to c, a turn through e, and around " +
                    "again.\n\nTry it: change one note in one bar and press Update — the other bars " +
                    "keep their shape, so you are editing a song section, not a loop.\n\nListen for: " +
                    "the four-cycle journey home. Watch the counter to see which bar you are in; by " +
                    "the time the first bar comes back around, you have heard a four-cycle line — " +
                    "what this lesson was building to.",
                ),
                Block.Code(
                    code = """
                    note("<[a2@2 a2 g2]!2 [c3@2 c3 b2] [e2@2 e2 g2]>")  // three bars, the first doubled: four cycles
                      .sound("saw")                                     // bass voice
                      .adsr("0.001:0.3:0:0.1")                          // the pluck shape
                      .gain(0.5)                                        // synths sit at 0.5
                    """.trimIndent(),
                ),
            ),
        ),
    ),
)
