/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

/**
 * Curriculum slot B7 — see docs/tasks/tutorial-curriculum.md
 *
 * The "," (together) is taught against B4's space-inside-brackets (turns), and
 * "|" (fresh random pick each cycle) against B5's "<>" (a fixed schedule).
 * Deeper harmony — which notes agree and why — is deliberately deferred to the
 * chords-and-voicing lesson; this one licenses only the power chord shape.
 */
val chordsInOneStepTutorial = Tutorial(
    slug = "chords-in-one-step",
    title = Tut.chordsInOneStep,
    description = "The comma stacks notes into a chord inside one step — and the | makes a step gamble.",
    difficulty = TutorialDifficulty.Beginner,
    scope = TutorialScope.Quick,
    tags = listOf(TutorialTag.Chords, TutorialTag.Patterns),
    teaches = listOf(",", "|"),
    sections = listOf(
        TutorialSection(
            heading = "Two notes, one step",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    Inside brackets, a space splits a step into turns — ${Tut.subdivision} built hat rolls that way. A comma does something new: the notes stop taking turns and sound at the same instant. Several notes sounding together is a **chord**.

                    Both lines below play `a2` and `e3` twice per cycle on the bright `saw` voice from ${Tut.theFourWaveforms}, wearing the pluck shape from ${Tut.shapeOfANote} so each hit starts and ends cleanly. (Two notes in one step add up, so the line sits at 0.4 instead of the usual 0.5.)

                    **Try it:** swap the `//` and press Update — the same two notes, comma against space.

                    **Listen for:** struck together, the two notes fuse into one thicker sound; taking turns, they come apart into a quick roll. The comma is the difference between a chord and a very fast melody.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    note("[a2,e3] ~ [a2,e3] ~").sound("saw").adsr("0.001:0.3:0:0.1").gain(0.4)    // comma: both notes strike together
                    // note("[a2 e3] ~ [a2 e3] ~").sound("saw").adsr("0.001:0.3:0:0.1").gain(0.4) // space: the same notes take turns — swap
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "The power chord",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    Add the octave on top — `a2`, `e3`, `a3` — and you have the **power chord**: three notes that behave like one big one. Which notes agree like this, and which clash — that is what harmony is about; a chords lesson still to come takes that up properly. Here, this one shape is enough.

                    The riff below — a short figure that repeats — stabs it three times across the cycle: on the first step, on the fourth, and on the seventh, with rests carrying the time between. (Three notes add up further — the line drops to 0.35.)

                    **Try it:** swap the `//` to hear three notes against two.

                    **Listen for:** the added `a3` does not read as a new melody note. It makes the chord taller and heavier — the "same but higher" of the octave floors in ${Tut.firstNotes}, folded into one strike.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    note("[a2,e3,a3] ~ ~ [a2,e3,a3]  ~ ~ [a2,e3,a3] ~").sound("saw").adsr("0.001:0.3:0:0.1").gain(0.35)    // three-note stabs on steps one, four, seven
                    // note("[a2,e3] ~ ~ [a2,e3]  ~ ~ [a2,e3] ~").sound("saw").adsr("0.001:0.3:0:0.1").gain(0.35)          // the two-note version — swap to compare
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "A step that gambles: |",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    The `|` sign makes a step choose. `a2|g2` plays one or the other — and it draws fresh every time the step comes around, so the outcome changes from cycle to cycle. That is the difference from `<>` in ${Tut.alternationAndRepetition}: `<>` takes turns on a fixed schedule; `|` rolls dice.

                    Below, the riff's last stab gambles between two whole chords: the A power chord — home — or the same shape moved down one letter to G. Let it loop a while before you touch anything.

                    **Try it:** make home more likely by listing it twice — `[a2,e3,a3]|[a2,e3,a3]|[g2,d3,g3]`. Every option gets an equal draw, so repeating one weights the dice toward it.

                    **Listen for:** which chord closes each cycle. You stop predicting and start reacting.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    note("[a2,e3,a3] ~ ~ [a2,e3,a3]  ~ ~ [a2,e3,a3]|[g2,d3,g3] ~")  // the last stab: a random pick — A, or the same shape on G
                      .sound("saw").adsr("0.001:0.3:0:0.1").gain(0.35)              // voice, pluck, and level unchanged
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "Stabs over the groove",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    The riff was built to land on drums. `stack()` from ${Tut.layers} puts the groove from ${Tut.spaceAndRests} underneath: the first stab strikes with the kick, the middle one falls into the drums' gap, and the gambling one lands right on the snare.

                    **Try it:** give each layer a few loops alone — put `//` in front of the other one. Then swap the G chord for a shape of your own: move all three notes down one more letter to `[f2,c3,f3]` and press Update.

                    **Listen for:** what the steady beat does to the gamble. With drums framing it, the changing chord no longer sounds like an accident — it sounds like a band member making a choice. That contrast, something solid next to something loose, is what makes randomness musical.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    stack(
                      sound("bd ~ hh ~  ~ hh sd ~").gain(0.8),                                                                        // the groove — steady every cycle
                      note("[a2,e3,a3] ~ ~ [a2,e3,a3]  ~ ~ [a2,e3,a3]|[g2,d3,g3] ~").sound("saw").adsr("0.001:0.3:0:0.1").gain(0.35)  // the stab riff, unchanged
                    )
                    """.trimIndent(),
                ),
            ),
        ),
    ),
)
