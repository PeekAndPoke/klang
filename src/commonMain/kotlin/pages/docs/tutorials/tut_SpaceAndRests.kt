/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

/** Curriculum slot B2 — see docs/tasks/tutorial-curriculum.md */
val spaceAndRestsTutorial = Tutorial(
    slug = "space-and-rests",
    title = "Space and Rests",
    description = "Rests are where the groove lives: take sounds away and the beat gets stronger, not weaker.",
    track = TutorialTrack.Pattern,
    difficulty = TutorialDifficulty.Beginner,
    scope = TutorialScope.Quick,
    tags = listOf(TutorialTag.Rhythm, TutorialTag.GettingStarted),
    teaches = listOf("~"),
    sections = listOf(
        TutorialSection(
            heading = "The rest: ~",
            text = "This lesson is about deleting. It starts from the beat you built last lesson — " +
                "eight steps, every step filled — and stays on that grid the whole way; by the end, " +
                "half of the sounds will be gone and the beat will groove more, not less.\n\nThe tilde " +
                "~ is a rest: silence that still takes its step. The second line below is the first " +
                "line with every hi-hat replaced by ~. Swap the // between them and press Update.\n\n" +
                "Listen for: the kick and snare land in exactly the same places in both versions — " +
                "only the hats vanished, and the steps they stood on stayed open instead of closing " +
                "up. Watch the code, too: nothing lights up on the ~ steps. The step is there; it just " +
                "doesn't sound. (~ works in any pattern string, not only drums — you will rest " +
                "melodies the same way next lesson.)\n\nOne more thing to hear in the second line: " +
                "count one-two-three-four along. The kicks land on counts one and three, the snares " +
                "on two and four. Snare-on-two-and-four is the backbeat — the skeleton under most " +
                "rock and pop, and it was hiding inside the full beat all along.",
            code = """
                sound("bd hh sd hh bd hh sd oh").gain(0.8)  // full: every step filled
                // sound("bd ~ sd ~ bd ~ sd ~").gain(0.8)   // same grid, hats replaced by rests
            """.trimIndent(),
        ),
        TutorialSection(
            heading = "Take the skeleton away",
            text = "Now delete the other half instead: keep only the hats. Play the first line and " +
                "count along until the count feels solid. Then swap the // and keep counting through " +
                "the change.\n\nListen for: the hats land exactly between your counts — musicians call " +
                "those the off-beats — and your head keeps nodding where the kick and snare used to " +
                "be. Your ear supplies the skeleton for a while, even though the speaker no longer " +
                "plays it. Silence in the right place creates pull; that is why rests matter more " +
                "than extra sounds.",
            code = """
                sound("bd ~ sd ~ bd ~ sd ~").gain(0.8)     // the skeleton — count along with this first
                // sound("~ hh ~ hh ~ hh ~ oh").gain(1.0)  // hats alone — lifted, they are quieter sounds
            """.trimIndent(),
        ),
        TutorialSection(
            heading = "Shape an eight-step groove",
            text = "With the full grid and ~ you can shape real grooves: this one keeps the kick on " +
                "count one, puts a hat on count two, pushes another hat into the snare, and leaves the " +
                "last step open. The snare answers on count four alone.\n\nTry it: move one of the " +
                "hats to a different step and press Update. Then replace the snare with a ~ — replace, " +
                "not remove: deleting the name outright would leave seven names, and the cycle would " +
                "re-split into sevenths, moving every hit that remains. Sounds and silences trading " +
                "places on a fixed grid — that is beat-making.",
            code = """
                sound("bd ~ hh ~ ~ hh sd ~")  // 8 steps: 4 sounds, 4 rests
                  .gain(0.8)
            """.trimIndent(),
        ),
        TutorialSection(
            heading = "Accents: gain as a pattern",
            text = "This is not a new function — it is the rule you already know, applied to numbers. " +
                "gain() accepts a pattern string too, and the numbers split the cycle exactly like " +
                "drum names do: eight numbers, eight steps, and every sound picks up the number " +
                "standing on its step. Softening some hits and not others is called accenting, and it " +
                "is the other half of groove. (Most settings accept a pattern string like this — " +
                "remember that; the whole course leans on it.)\n\nSwap the // to compare against the " +
                "flat version. Listen for: only the hats moved — they sit back, while the kick and " +
                "snare stand exactly where they were. Same sounds, different accents.",
            code = """
                sound("bd ~ hh ~ ~ hh sd ~").gain("0.8 0.8 0.4 0.8 0.8 0.5 0.8 0.8")  // hats softened
                // sound("bd ~ hh ~ ~ hh sd ~").gain(0.8)                             // flat: every hit equal
            """.trimIndent(),
        ),
        TutorialSection(
            heading = "Less is a groove",
            text = "Both lines below are the same tempo and the same eight steps — each written on one " +
                "line, so a single // switches the whole thing off. The first is the full beat this " +
                "lesson started with; the second is what we made of it. Swap the // and press Update " +
                "to A/B them.\n\nListen for: the last step — the full version fills it, the shaped " +
                "version leaves it empty, and that gap is the run-up you feel before the kick comes " +
                "back around. Then ask yourself which version makes you move. The full one tells you " +
                "everything; the shaped one leaves room for you to answer.",
            code = """
                // sound("bd hh sd hh bd hh sd oh").gain(0.8)                         // full: every step filled
                sound("bd ~ hh ~ ~ hh sd ~").gain("0.8 0.8 0.4 0.8 0.8 0.5 0.8 0.8")  // shaped and accented
            """.trimIndent(),
        ),
    ),
)
