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
                "eight steps, every step filled — and stays on that grid the whole way. (The code sits " +
                "on one line here, so a single // can switch a whole version off.)\n\nThe tilde ~ is a " +
                "rest: silence that still takes its step. The second line below is the first line with " +
                "every hi-hat replaced by ~.\n\nTry it: swap the // between the two lines and press " +
                "Update.\n\nListen for: the kick and snare land in exactly the same places in both " +
                "versions — only the hats vanished, and the steps they stood on stayed open instead of " +
                "closing up. Watch the code, too: nothing lights up on the ~ steps. The step is there; " +
                "it just doesn't sound. (~ works in any pattern string, not only drums — you will rest " +
                "melodies the same way next lesson.)",
            code = """
                sound("bd hh sd hh bd hh sd oh").gain(0.8)  // full: every step filled
                // sound("bd ~ sd ~ bd ~ sd ~").gain(0.8)   // same grid, hats replaced by rests
            """.trimIndent(),
        ),
        TutorialSection(
            heading = "The backbeat was hiding inside",
            text = "Keep the stripped-down version playing and count one-two-three-four along.\n\n" +
                "Listen for: the kicks land on counts one and three, the snares on two and four. " +
                "Snare-on-two-and-four is the backbeat — the skeleton under most rock and pop. It was " +
                "inside the full beat all along; removing the hats is what makes it audible.",
            code = """
                sound("bd ~ sd ~ bd ~ sd ~")  // kicks on one and three, snares on two and four
                  .gain(0.8)
            """.trimIndent(),
        ),
        TutorialSection(
            heading = "Take the skeleton away",
            text = "Now delete the other half instead: keep only the hats. Play the first line and count " +
                "along until the count feels solid.\n\nTry it: swap the // and keep counting through " +
                "the change. (The hats are lifted to gain 1.0 in the second line — on their own, they " +
                "are much quieter sounds than kick and snare.)\n\nListen for: the hats land " +
                "exactly between your counts — musicians call those the off-beats — and your head " +
                "keeps nodding where the kick and snare used to be. Your ear supplies the skeleton for " +
                "a while, even though the speaker no longer plays it. Silence in the right place " +
                "creates pull; that is why rests matter more than extra sounds.",
            code = """
                sound("bd ~ sd ~ bd ~ sd ~").gain(0.8)     // the skeleton — count along with this first
                // sound("~ hh ~ hh ~ hh ~ oh").gain(1.0)  // hats alone, lifted to 1.0 — alone they need it
            """.trimIndent(),
        ),
        TutorialSection(
            heading = "Shape an eight-step groove",
            text = "With the full grid and ~ you can shape real grooves. This one starts from the " +
                "skeleton, drops the second kick and the first snare, and puts two hats back where " +
                "they pull hardest: one on count two, one on the off-beat right before the snare, " +
                "leaning into it. The snare answers on count four alone, and the last step stays open.\n\n" +
                "Listen for: the two hats do not alternate evenly — the second one leans into the " +
                "snare.\n\nTry it: move one of the hats to a different step and press Update. Then " +
                "replace the snare with a ~ — replace, not remove: deleting the name outright would " +
                "leave seven names, and the cycle would re-split into sevenths, moving every hit " +
                "after the first. Sounds and silences trading places on a fixed grid — that is " +
                "beat-making.",
            code = """
                sound("bd ~ hh ~ ~ hh sd ~")  // 8 steps: 4 sounds, 4 rests
                  .gain(0.8)                  // one level for the whole line — accents come next
            """.trimIndent(),
        ),
        TutorialSection(
            heading = "Accents: gain as a pattern",
            text = "This is not a new function — it is the rule you already know, applied to numbers. " +
                "gain() accepts a pattern string too, and the numbers split the cycle exactly like " +
                "drum names do: eight numbers, eight steps, and every sound picks up the number " +
                "standing on its step. Giving hits different levels — some standing out, some sitting " +
                "back — is called accenting, and it is the other half of groove. (Most settings accept a pattern string like this — " +
                "remember that; the whole course leans on it.)\n\nTry it: swap the // to compare " +
                "against the flat version.\n\nListen for: only the hats moved — they sit back, while " +
                "the kick and snare stand exactly where they were. Same sounds, different accents.",
            code = """
                sound("bd ~ hh ~ ~ hh sd ~").gain("0.8 0.8 0.4 0.8 0.8 0.5 0.8 0.8")  // second hat a touch louder — it leads into the snare
                // sound("bd ~ hh ~ ~ hh sd ~").gain(0.8)                             // flat: every hit equal
            """.trimIndent(),
        ),
        TutorialSection(
            heading = "Less is a groove",
            text = "Both lines below are the same tempo and the same eight steps. The live line is " +
                "what we made; the switched-off line is the full beat this lesson started with.\n\n" +
                "Try it: swap the // and press Update — and remember to comment the live line out, or " +
                "the lower one still wins.\n\nThe shaped version is also quieter — it has half the hits — " +
                "so nudge your volume up a notch and judge the groove, not the level.\n\nListen for: " +
                "the last step. The full version fills that moment for you; the shaped one leaves it " +
                "to you, and that gap is the run-up you feel before the kick comes back around.",
            code = """
                sound("bd ~ hh ~ ~ hh sd ~").gain("0.8 0.8 0.4 0.8 0.8 0.5 0.8 0.8")  // shaped and accented
                // sound("bd hh sd hh bd hh sd oh").gain(0.8)                         // full: every step filled
            """.trimIndent(),
        ),
    ),
)
