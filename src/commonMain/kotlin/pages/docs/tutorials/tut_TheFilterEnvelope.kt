/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

/**
 * Curriculum slot A4 — see docs/tasks/tutorial-curriculum.md
 *
 * lpe() depth is a RATIO of the resting cutoff, not Hz — single digits sweep
 * far (the built-in songs live in 0.25–25). Prose stays intent-level per the
 * flux ruling; the numbers live only in the code, where retuning is cheap.
 * A/B pairs where the swept side is brighter/louder at the strike are
 * narrated in-lesson; sanctioned parity exceptions.
 */
val theFilterEnvelopeTutorial = Tutorial(
    slug = "the-filter-envelope",
    title = Tut.theFilterEnvelope,
    description = "The cutoff gets its own ADSR — and the plain pluck becomes the classic synth pluck.",
    difficulty = TutorialDifficulty.Beginner,
    scope = TutorialScope.Quick,
    tags = listOf(TutorialTag.Synthesis),
    teaches = listOf("lpe", "lpadsr"),
    sections = listOf(
        TutorialSection(
            heading = "A colour that moves inside the note",
            text = "${Tut.filters} set a colour and held it. But in most sounds you love, the " +
                "colour moves within every single note — bright at the strike, darker as it " +
                "fades. This lesson gives the cutoff its own envelope.\n\nThe starting point is " +
                "a dull pluck: the two long notes from ${Tut.shapeOfANote}, the pluck shape on " +
                "the loudness, and a low-pass sitting at 400.\n\nListen for: the reference — " +
                "closed, muffled, static. Keep it in your ear; everything below opens it up. " +
                "(A moving cutoff changes level along with colour; throughout this lesson, " +
                "judge the movement, not the level.)",
            code = """
                note("a3 ~ c4 ~")                // the long two-note phrase — room for the colour to move
                  .sound("saw")                  // the bright voice, about to be darkened
                  .adsr("0.001:0.3:0:0.1")       // the pluck shape on the loudness
                  .lpf(400)                      // a closed, static filter — the "before"
                  .gain(0.5)                     // synths sit at 0.5
            """.trimIndent(),
        ),
        TutorialSection(
            heading = "The cutoff gets an envelope",
            text = "Two new settings, one idea. lpe() sets the depth of the travel: how far " +
                "the cutoff climbs above its resting place before falling home. Small numbers " +
                "already go far — the built-in songs mostly live in single digits. lpadsr() is " +
                "the shape of that travel, and it is the same four numbers you know from " +
                "${Tut.shapeOfANote}: attack, decay, sustain, release — applied to the cutoff " +
                "instead of the loudness.\n\nTry it: swap the // to compare against the closed " +
                "version. Then, with the first line live, set lpe to 1, and to 15 — the same " +
                "shape, a short trip and a long one.\n\n" +
                "Listen for: the öw — say it out loud, mouth open, then closing. That closing " +
                "is the shape the filter draws across each note: bright at the strike, " +
                "shutting while the note still rings. This is the classic synth pluck, and it " +
                "is the same instrument as the dull one; only the colour learned to move.",
            code = """
                note("a3 ~ c4 ~").sound("saw").adsr("0.001:0.3:0:0.1").lpf(400).lpe(7).lpadsr("0.001:0.15:0:0.1").gain(0.5) // the öw
                // note("a3 ~ c4 ~").sound("saw").adsr("0.001:0.3:0:0.1").lpf(400).gain(0.5)                                // closed — swap
            """.trimIndent(),
        ),
        TutorialSection(
            heading = "Slow bloom",
            text = "The travel does not have to be fast. Hold the loudness steady — the organ " +
                "shape from ${Tut.shapeOfANote} — and let the cutoff rise slowly instead: the " +
                "note starts closed and opens while it sounds, settling at a bright shade. " +
                "(With the held shape, the envelope's later stages get air — on a pluck they " +
                "would fall in silence.)\n\nThe loudness is identical on both lines below; " +
                "only the four lpadsr numbers changed sides.\n\nTry it: swap the // to compare " +
                "the bloom against the strike.\n\nListen for: where the brightness sits inside each " +
                "note. The strike puts it at the very front; the bloom saves it for the " +
                "middle. You are composing with the inside of a note.",
            code = """
                note("a3 ~ c4 ~").sound("saw").adsr("0.01:0.1:1:0.05").lpf(400).lpe(7).lpadsr("0.2:0.2:0.8:0.2").gain(0.5)     // bloom: opens, then holds bright
                // note("a3 ~ c4 ~").sound("saw").adsr("0.01:0.1:1:0.05").lpf(400).lpe(7).lpadsr("0.001:0.15:0:0.1").gain(0.5) // strike — swap
            """.trimIndent(),
        ),
        TutorialSection(
            heading = "Resonance on the move",
            text = "Now add lpq() from ${Tut.filters}. A resonant edge on a moving cutoff " +
                "does not just sweep — the boosted edge travels through the note like a mouth " +
                "changing shape.\n\nTry it: swap the // to hear the sweep with and without " +
                "its edge. (Resonance adds level, so this pair sits a notch lower in gain — " +
                "the practice from ${Tut.filters}.)\n\nListen for: the öw turning into a wah. Same travel, same " +
                "speed — only the resonance differs. This pairing, filter envelope plus " +
                "resonance, is the sound of half the basslines ever synthesized.",
            code = """
                note("a3 ~ c4 ~").sound("saw").lpq(6).adsr("0.001:0.3:0:0.1").lpf(400).lpe(7).lpadsr("0.001:0.15:0:0.1").gain(0.4) // wah: the added lpq up front
                // note("a3 ~ c4 ~").sound("saw").adsr("0.001:0.3:0:0.1").lpf(400).lpe(7).lpadsr("0.001:0.15:0:0.1").gain(0.4)     // plain sweep — swap
            """.trimIndent(),
        ),
        TutorialSection(
            heading = "Pluck, wah, bloom",
            text = "Three instruments again, in the spirit of ${Tut.shapeOfANote} — each line " +
                "a verbatim recap of a section above. The wah is the pluck with its edge " +
                "singing; the bloom changes both the loudness shape and where the travel " +
                "sits. Three dials — loudness shape, cutoff travel, resonant edge — made all " +
                "three instruments.\n\nTry it: give each line a few loops, then start " +
                "swapping numbers between them.\n\nListen for: the two envelopes dividing the " +
                "work — the adsr says how long a note lives, the lpadsr what it looks like " +
                "while alive. (You stepped the cutoff cycle by cycle in ${Tut.filters} and " +
                "moved it inside single notes here; sliding it smoothly across a whole pattern " +
                "is the signals lesson, further along the Sound track.)",
            code = """
                note("a3 ~ c4 ~").sound("saw").adsr("0.001:0.3:0:0.1").lpf(400).lpe(7).lpadsr("0.001:0.15:0:0.1").gain(0.5)           // pluck
                // note("a3 ~ c4 ~").sound("saw").lpq(6).adsr("0.001:0.3:0:0.1").lpf(400).lpe(7).lpadsr("0.001:0.15:0:0.1").gain(0.4) // wah
                // note("a3 ~ c4 ~").sound("saw").adsr("0.01:0.1:1:0.05").lpf(400).lpe(7).lpadsr("0.2:0.2:0.8:0.2").gain(0.5)         // bloom
            """.trimIndent(),
        ),
    ),
)
