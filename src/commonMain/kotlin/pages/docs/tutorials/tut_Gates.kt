/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

/**
 * Curriculum slot B10 — see docs/tasks/tutorial-curriculum.md
 *
 * B7's stab riff (hits on steps one, four, seven) IS the tresillo — gaps of
 * three, three, two — so §1 teaches struct() by rewriting that riff as
 * what+when. §1's A/B is identical by design and narrated as such — a
 * sanctioned exception to the render-QA audibility gate; do not "fix" it.
 * The planned `chord` preview was dropped: the comma-chord from B7 carries
 * the lesson without borrowing anything.
 */
val gatesTutorial = Tutorial(
    slug = "gates",
    title = Tut.gates,
    description = "struct() splits a line into what plays and when it plays, and the when turns out to have a name.",
    difficulty = TutorialDifficulty.Intermediate,
    scope = TutorialScope.Quick,
    tags = listOf(TutorialTag.Rhythm, TutorialTag.Patterns),
    teaches = listOf("struct"),
    sections = listOf(
        TutorialSection(
            heading = "The what and the when: struct",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    The stab riff from ${Tut.chordsInOneStep} wrote the chord out three times, with rests between. `struct()` takes the same line apart: the pattern says what plays (one power chord), and the **gate** string says when, with `x` for a hit and `~` for a rest, splitting the cycle into steps exactly like every string you have written.

                    (The two lines below sound identical on purpose: they are the same riff, spelled whole and spelled apart.)

                    **Try it:** swap the `//` and press Update to convince your ear, and swap back. Then change `[a2,e3,a3]` to `[g2,d3,g3]` and press Update. One edit moves all three hits.

                    **Listen for:** no difference. Then look at the code again. The gate line names the chord once: change the chord and every hit follows; change the gate and the chord stays.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    note("[a2,e3,a3]").sound("saw").adsr("0.001:0.3:0:0.1").struct("x ~ ~ x  ~ ~ x ~").gain(0.35)          // one chord + a gate
                    // note("[a2,e3,a3] ~ ~ [a2,e3,a3]  ~ ~ [a2,e3,a3] ~").sound("saw").adsr("0.001:0.3:0:0.1").gain(0.35) // spelled whole, swap
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "Three, three, two",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    Look at the gate's spacing: hits on steps one, four, and seven, with gaps of three, three, and two. That spacing has a name, the **tresillo**, and it is one of the most widespread rhythm shapes in the world: son, reggaeton, and half of pop lean on it. You have been playing it since ${Tut.chordsInOneStep} without the name.

                    **Try it:** swap the `//` to the straightened gate (hits every other step) and press Update, then swap back.

                    **Listen for:** the difference in pull. The even gate marches in place; the tresillo leans forward, because its last gap is one step short: the cycle comes back around one step before you expect it.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    note("[a2,e3,a3]").sound("saw").adsr("0.001:0.3:0:0.1").struct("x ~ ~ x  ~ ~ x ~").gain(0.35)    // the tresillo: three, three, two
                    // note("[a2,e3,a3]").sound("saw").adsr("0.001:0.3:0:0.1").struct("x ~ x ~  x ~ x ~").gain(0.35) // straightened, swap to compare
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "Two whats, one when",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    A gate is a rhythm you can hand to more than one line. Below, a bass note and the chord stabs carry the same gate string, character for character: every hit of one lands exactly on a hit of the other, the layering from ${Tut.layers} locked to a single when.

                    **Try it:** change one `x` to `~` in just one of the two gate strings and press Update. The lines unlock, and you hear at once what the shared gate was doing. Put it back.

                    **Listen for:** two instruments striking as one instrument. A band playing the same rhythm together stops being lines and starts being a **section**. The shared gate is how you write that.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    stack(
                      note("a2").sound("sine").struct("x ~ ~ x  ~ ~ x ~").gain(0.6),                                 // the bass on the gate
                      note("[a2,e3,a3]").sound("saw").adsr("0.001:0.3:0:0.1").struct("x ~ ~ x  ~ ~ x ~").gain(0.35)  // the stabs on the same gate
                    )
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "The gated section",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    The groove from ${Tut.spaceAndRests} underneath, and the locked pair above it. The drums and the gate agree where it matters: the first hit shares its step with the kick, the last one with the snare, and the middle one strikes where the drums leave room.

                    **Try it:** put `//` in front of one layer at a time to hear the others. Then move the middle `x` one step later in both gate strings (same lock, new lean) and press Update.

                    **Listen for:** the push and the frame at once: drums keeping the cycle square while bass and stabs lean three-three-two across it. Most grooves you love are exactly this argument.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    stack(
                      sound("bd ~ hh ~  ~ hh sd ~").gain(0.8),                                                       // the groove, square
                      note("a2").sound("sine").struct("x ~ ~ x  ~ ~ x ~").gain(0.6),                                 // tresillo bass
                      note("[a2,e3,a3]").sound("saw").adsr("0.001:0.3:0:0.1").struct("x ~ ~ x  ~ ~ x ~").gain(0.35)  // tresillo stabs
                    )
                    """.trimIndent(),
                ),
            ),
        ),
    ),
)
