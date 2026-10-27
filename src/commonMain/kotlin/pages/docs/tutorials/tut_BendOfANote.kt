/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

/**
 * Curriculum slot A2b: the curves of an envelope's moving stages (KLANG-00371; the maintainer, 2026-10-27: "please do
 * some examples with different curves, probably another lesson"). Right after [shapeOfANoteTutorial], on the same
 * phrase; it plays at half speed so each note is a second long, room for a stage to show its bend.
 */
val bendOfANoteTutorial = Tutorial(
    slug = "bend-of-a-note",
    title = Tut.bendOfANote,
    description = "Curves: a fade can start slowly or quickly. Three stages, each with a bend of its own.",
    difficulty = TutorialDifficulty.Beginner,
    depth = TutorialDepth.Quick,
    tags = listOf(TutorialTag.Synthesis),
    teaches = listOf("adsrCurves"),
    rpm = 15.0,
    sections = listOf(
        TutorialSection(
            heading = "The bend you have been hearing",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    In ${Tut.shapeOfANote} each stage was a number of seconds. A stage also has a way of moving: a fade can creep in and then hurry, or hurry in and then settle. That is its **curve**.

                    Klang's stages bend by default. A rising stage starts slowly and finishes fast; a falling one drops quickly and then leaves a long, soft tail. `adsrCurves()` names the curve of the three stages that move: attack, decay and release. Sustain is a level, not a movement, so it has none.

                    This lesson plays at half speed, so each note lasts a full second: long enough to hear a stage take its shape. The picture above the examples draws the curve of whichever one plays.

                    **Try it:** play the first example, then the second, where every stage is a straight line.

                    **Listen for:** the attack. With the bend, the note stays quiet for a moment and then blooms; straight, it is already half there while the bent one is still holding back. Then the tail: bent, it falls away quickly and lingers soft; straight, it stays loud longer and stops more abruptly.
                    """.trimIndent(),
                ),
                Block.Visual.Adsr(),
                Block.Code(
                    code = """
                    note("a3 ~ c4 ~")               // the opening leap, a second per note
                      .sound("saw")                 // the bright voice
                      .adsr(0.6, 0.2, 0.6, 1.0)     // a slow attack, a long tail, each bent as by default
                      .gain(0.5)                    // kept modest
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    note("a3 ~ c4 ~")                              // the same leap
                      .sound("saw")                                // the same voice
                      .adsr(0.6, 0.2, 0.6, 1.0)                    // the same four numbers
                      .adsrCurves("linear", "linear", "linear")    // attack, decay, release: all straight
                      .gain(0.5)
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "Attack: a late bloom or a quick rise",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    You can name one stage on its own and leave the others as they are: `adsrCurves(attack = "scurve")` changes only the attack.

                    `"invsquare"` rushes up and then eases into the top. `"scurve"` starts softly and arrives softly, with the hurry in the middle. The first example below is the default from above, the late bloom.

                    **Try it:** play the three examples in turn and give each a few notes.

                    **Listen for:** where the note seems to arrive. The default arrives late, the quick rise almost at once, the S-curve in the middle without a corner on either side. The same 0.6 seconds every time; only the way through them changed.
                    """.trimIndent(),
                ),
                Block.Visual.Adsr(),
                Block.Code(
                    code = """
                    note("a3 ~ c4 ~")             // the leap
                      .sound("saw")               // the bright voice
                      .adsr(0.6, 0.2, 0.6, 1.0)   // the slow attack, bent as by default: a late bloom
                      .gain(0.5)
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    note("a3 ~ c4 ~")                       // the leap
                      .sound("saw")                         // the bright voice
                      .adsr(0.6, 0.2, 0.6, 1.0)             // the slow attack
                      .adsrCurves(attack = "invsquare")     // a quick rise, then easing in
                      .gain(0.5)
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    note("a3 ~ c4 ~")                       // the leap
                      .sound("saw")                         // the bright voice
                      .adsr(0.6, 0.2, 0.6, 1.0)             // the slow attack
                      .adsrCurves(attack = "scurve")        // soft at both ends, the hurry in the middle
                      .gain(0.5)
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "Release: how the tail falls",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    The release is where a curve is easiest to hear: a second of tail, as long as the rest after each note.

                    The default drops fast and then trails off softly, the way a struck string fades. `"linear"` fades evenly all the way down. `"invsquare"` turned around: it holds up, then falls away near the end.

                    **Try it:** play the three examples in turn. Then, in the last one, try `"cube"` and press **Update**: it drops quickly and goes quiet sooner.

                    **Listen for:** the rest after each note. With the default, the rest fills with a faint glow; straight, the tail stays present and then is gone; held up, the note seems to go on and then lets go all at once.
                    """.trimIndent(),
                ),
                Block.Visual.Adsr(),
                Block.Code(
                    code = """
                    note("a3 ~ c4 ~")             // the leap
                      .sound("saw")               // the bright voice
                      .adsr(0.6, 0.2, 0.6, 1.0)   // the long tail, bent as by default: a fast drop, a soft trail
                      .gain(0.5)
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    note("a3 ~ c4 ~")                      // the leap
                      .sound("saw")                        // the bright voice
                      .adsr(0.6, 0.2, 0.6, 1.0)            // the long tail
                      .adsrCurves(release = "linear")      // an even fade
                      .gain(0.5)
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    note("a3 ~ c4 ~")                      // the leap
                      .sound("saw")                        // the bright voice
                      .adsr(0.6, 0.2, 0.6, 1.0)            // the long tail
                      .adsrCurves(release = "invsquare")   // it holds up, then falls away
                      .gain(0.5)
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "Your own bends",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    Six curves are there to pick from: `"linear"`, `"square"`, `"cube"`, `"scurve"`, `"invsquare"` and `"exponential"`, the default. The decay bends the same way as the release, between the top and the sustain level.

                    Below is a pad with a soft S-curve swell, the default decay, and an even fade at the end.

                    **Try it:** change one name at a time and press **Update**, the picture redrawing each time. Then take the pluck from ${Tut.shapeOfANote}, `adsr(0.001, 0.3, 0, 0.1)`: its attack is too short for a curve to matter, but its decay is the pluck. Give the decay `"linear"` and hear the pluck turn boxier and longer.

                    **Listen for:** the curves matter where a stage takes its time. On a slow swell, a long tail or a pluck's fall they are the character of the sound; on a switch-like attack of a thousandth of a second they are nothing.
                    """.trimIndent(),
                ),
                Block.Visual.Adsr(),
                Block.Code(
                    code = """
                    note("a3 ~ c4 ~")                                    // the leap
                      .sound("saw")                                      // the bright voice
                      .adsr(0.6, 0.2, 0.6, 1.0)                          // a pad: slow in, long out
                      .adsrCurves("scurve", "exponential", "linear")     // a soft swell, the default decay, an even fade
                      .gain(0.5)
                    """.trimIndent(),
                ),
            ),
        ),
    ),
)
