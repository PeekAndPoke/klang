/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

/**
 * Curriculum slot A6 — see docs/tasks/tutorial-curriculum.md
 *
 * Keeps two registered obligations: The Four Waveforms named `supersaw` among the
 * "further voices" promised for later lessons, and the word **voice** is already spent
 * (A1 formalized it as oscillator timbre), so the `unison` count is called **unison
 * layers** in prose here, never bare "voices".
 *
 * Engine truth (verified in audio_be / audio_bridge, and the reason the plan's A6 row
 * needed correcting):
 *
 * - `unison`/`spread` are INERT on the plain voices. Only the super oscillators carry
 *   `voices`/`spread` slots (IgnitorDefaults.kt: sine/saw/square/triangle get `analog`
 *   ONLY). §2 states this in prose; do not "demonstrate" it with a silent A/B.
 * - The stack is SUM-NORMALIZED (`Ignitors.superSawVoiceGains` divides by the gain sum,
 *   and the per-note jitter pass re-normalizes), so layer-count A/Bs are level-matched
 *   by construction. That is why §2 and §3 keep one gain across the whole `<>` climb.
 * - Defaults, from IgnitorDsl.Slots: `voices` 8, `spread` 0.2, `analog` 0.0. So a bare
 *   `sound("supersaw")` is ALREADY eight layers at 0.2 — §1 leans on exactly that.
 * - `spread` is in SEMITONES (±spread/2 per edge layer); `analog` is peak drift in
 *   CENTS (±analog cents, AnalogDrift/DriftLanes), NOT the 0..1 the sprudel KDoc
 *   used to claim. That KDoc was wrong and was corrected at source in the same change
 *   as this lesson (lang_synthesis_oscparam.kt), the way A4's `lpe` unit was.
 * - The slow drift layer is seeded at CENTRE so every note attacks in tune, and only
 *   develops over a held note (~10 s time constant). That is why §4 wears A2's pad
 *   shape and B9's `.slow(2)`: on a pluck there is nothing to hear, by design.
 *
 * ⚠️ The plan's original listen-for for this slot, "mono vs wide on headphones", is
 * ENGINE-FALSE and is deliberately NOT delivered: the super oscillators sum to mono,
 * and `panSpread` is wired but inaudible (its own KDoc says so). Stereo width belongs
 * to The Transform Toolkit, which earns it with a transposed copy panned opposite.
 * Do not add a width A/B here.
 *
 * Render-QA measurement items, in order: §1's `saw` vs `supersaw` pair (eight
 * incoherent copies normalized to sum 1.0 may read quieter than one coherent saw; both
 * sit at 0.5 and the level caveat is narrated). Tune by ear, not from theory.
 */
val thicknessTutorial = Tutorial(
    slug = "thickness",
    title = Tut.thickness,
    description = "One voice, stacked: how many copies, how far apart, and how much they drift.",
    difficulty = TutorialDifficulty.Intermediate,
    depth = TutorialDepth.Quick,
    tags = listOf(TutorialTag.Synthesis),
    teaches = listOf("unison", "analog"),
    sections = listOf(
        TutorialSection(
            heading = "A voice made of copies",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    ${Tut.theFourWaveforms} closed by promising a shelf of further voices. Here is the one everything else on that shelf is built like: `supersaw`.

                    It is not a fifth waveform. It is the `saw` you already know, playing several times at once, each copy nudged a little off the note. The phrase below is the long two-note leap from ${Tut.shapeOfANote}, so each note has room to be heard.

                    **Try it:** swap the `//` between the two lines and press **Update**, a few times. (Judge the thickness, not the level.)

                    **Listen for:** the single `saw` stands perfectly still. The `supersaw` will not hold still: it shimmers, swelling and thinning on a slow pulse of its own. That pulse is the copies disagreeing about the pitch and drifting in and out of step with each other. Nothing about the note has changed; there is simply more than one of it.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    note("a3 ~ c4 ~").sound("supersaw").gain(0.5)  // many saws at once: it shimmers
                    // note("a3 ~ c4 ~").sound("saw").gain(0.5)    // one saw, perfectly still, swap to compare
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "How many: unison",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    Out of the box, `supersaw` is already eight copies. `unison()` says how many. To keep one word for one thing, this course calls them **unison layers**: ${Tut.theFourWaveforms} spent the word "voice" on the wave shape, and every layer here is the same voice.

                    Like every setting, `unison()` takes a pattern, so the angle brackets from ${Tut.alternationAndRepetition} can hand it a new count each cycle. (It only means something to a voice built to stack, which is the whole `super` family. On the plain `saw` there is nothing to stack, and the setting quietly does nothing.)

                    **Try it:** let all four cycles go by twice, then change the `12` to `2` and press **Update**. The last cycle stops standing out.

                    **Listen for:** at `1` the shimmer is gone and you are back to one saw. Each cycle after that adds body without adding pitch, and without getting louder: the stack shares one level between its layers, however many there are. Thickness turns out to be a count, not a volume.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    note("a3 ~ c4 ~").sound("supersaw")  // the stacking voice
                      .unison("<1 2 4 12>")              // one layer per cycle: 1, then 2, 4, 12
                      .gain(0.5)                         // one level all the way: the stack shares it out
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "How far apart: spread",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    The other half of the stack is how far off the note the layers sit. `unison(spread = ...)` sets that distance, in the **semitones** you met in ${Tut.scalesAndMelodies}, and it is a small number on purpose: a spread of `0.2` fans the layers across two tenths of one semitone, a gap far too small to read as a wrong note.

                    **Try it:** let the four cycles come around twice, then change `unison(8)` to `unison(2)` and press **Update**. The same four distances, on a stack with almost nothing in it.

                    **Listen for:** at `0.02` the layers are almost on top of each other and the sound is nearly still, just fatter than one saw. At `0.1` it breathes. At `0.3` it is the big detuned lead of every trance record. At `0.8` the layers have drifted so far apart that your ear stops hearing one thick note and starts hearing several wrong ones. Somewhere between the third and fourth cycle is the edge of the effect.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    note("a3 ~ c4 ~").sound("supersaw")           // the stacking voice
                      .unison(8)                                  // eight layers, the usual count
                      .unison(spread = "<0.02 0.1 0.3 0.8>")      // semitones apart: tight, breathing, wide, too wide
                      .gain(0.5)                                  // unchanged: only the distance moves
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "Never quite still: analog",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    `unison` and `spread` build a stack that is thick but fixed: the layers sit where you put them and stay. Real analog synthesizers cannot do that. Their oscillators wander, by tiny amounts, all the time, and that refusal to hold still is most of what people mean by analog warmth.

                    `analog()` puts the wandering back. Its number is the size of the wobble in **cents**, a hundredth of a semitone, and every layer wanders on its own. `analog(6)` is six hundredths of a semitone: far too small to hear as a pitch, big enough to hear as life.

                    It needs a note long enough to wander during, so this phrase now wears the pad shape from ${Tut.shapeOfANote} and `slow(2)` from ${Tut.theTransformToolkit} to stretch each note over a whole cycle. (Notes always begin in tune; the wander only develops while one is held. On a short pluck there is nothing to hear.)

                    **Try it:** swap the `//` and press **Update**. Then push `analog` to 25 and hear the wobble turn seasick, and drop it to 1 for the version most songs actually use.

                    **Listen for:** the still version is beautiful and slightly dead, the same shape held for as long as you like. The drifting one never repeats itself: the shimmer keeps rearranging while the note is held, and the longer you listen, the more obviously the first version is a machine.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    note("a3 ~ c4 ~").sound("supersaw").unison(voices = 8, spread = 0.2).analog(6).adsr(0.25, 0.15, 0.8, 1.2).slow(2).gain(0.5)    // drifting: each layer wanders on its own
                    // note("a3 ~ c4 ~").sound("supersaw").unison(voices = 8, spread = 0.2).adsr(0.25, 0.15, 0.8, 1.2).slow(2).gain(0.5)           // perfectly still, swap to compare
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "Thin, wide, alive",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    Three instruments from three numbers, in the spirit of ${Tut.shapeOfANote} and ${Tut.theFilterEnvelope}. All three are the same voice on the same two notes; only the size of the stack, the distance across it, and the drift inside it change.

                    **Try it:** give each line a few loops, then take the third one and walk `spread` from `0.05` up to `0.5` a step at a time. Somewhere in there is the sound you were looking for.

                    **Listen for:** how many players you hear. The tight one is one instrument. The wide one is a whole section of them, on the same note, none of them quite agreeing about it. The alive one is that section played by people, who cannot hold still even when they mean to.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    note("a3 ~ c4 ~").sound("supersaw").unison(voices = 3, spread = 0.04).adsr(0.25, 0.15, 0.8, 1.2).slow(2).gain(0.5)              // tight: three layers, barely apart
                    // note("a3 ~ c4 ~").sound("supersaw").unison(voices = 12, spread = 0.3).adsr(0.25, 0.15, 0.8, 1.2).slow(2).gain(0.5)           // wide: the big detuned lead
                    // note("a3 ~ c4 ~").sound("supersaw").unison(voices = 12, spread = 0.3).analog(6).adsr(0.25, 0.15, 0.8, 1.2).slow(2).gain(0.5) // alive: the same, drifting
                    """.trimIndent(),
                ),
            ),
        ),
    ),
)
