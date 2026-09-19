/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

/**
 * Curriculum slot A7 — see docs/tasks/tutorial-curriculum.md
 *
 * Keeps Layers' promise: B6 previewed `reverb(wet, size)` as "how much goes in" and
 * "how big the room is" and pointed at a Sound-track lesson still to come. This is it,
 * and it opens under exactly those two intuitions.
 *
 * Engine truth (verified in audio_be):
 *
 * - An unset slot of either space effect takes the shared musical default
 *   (`audio_bridge` `constants/SendEffectDefaults.kt`, maintainer 2026-09-16): reverb wet 0.25
 *   and size 5; delay wet 0.25, time 0.25 s, feedback 0.3. So a bare `reverb(0.4)` and a bare
 *   `delay(0.4)` both sound. §1 introduces wet and size, §2 lets the reader hear the default
 *   size take over (4 deleted, 5 heard), §3's `time = 0.25` equals the default, which at this
 *   lesson's 30 RPM is one step. (Until 2026-09-16 an unset size or time was a silent gate and
 *   §2 taught that trap; it no longer exists.)
 * - `reverb(size)` scale is ~0..10 (3 ≈ 1 s tail, 5 ≈ 1.4 s, 10 ≈ 12.5 s). Prose stays at
 *   "bigger room", never at seconds, per the flux ruling.
 * - `onepole` is a one-pole lowpass in Hz, deliberately NOT `lpf` (which is the resonant
 *   SVF). It was renamed from `warmth(0..1)` in the 2026-08-24 pitch/unit unification,
 *   so the unit is Hz now. §5 teaches the difference in intent only: no slopes, no dB/oct.
 *   It is also an OSC PARAM read inside the ignitor (IgnitorRegistry reads
 *   `oscParams["onepole"]`), NOT a post-effect. It therefore darkens what the distortion is
 *   fed. The first draft of §5 claimed "the distortion is untouched; what leaves is the
 *   harshness sitting on top of it" — plausible and FALSE. §5 now teaches the true version:
 *   drive and tone are one decision. This holds whatever pipeline is selected, because
 *   onepole is inside the voice and the pipeline runs on the voice's output.
 * - `gain` is the ONE level word (signal-flow plan section 6, 2026-09-19): the channel fader,
 *   applied with pan at the voice output (`SendRenderer`: `* gainL/gainR` from
 *   `voice.gain * gainMultiplier`). It does NOT drive the distortion. The first draft of §6
 *   claimed dropping `gain` "feeds the distortion less and changes the growl", plausible and
 *   FALSE. A SECOND multiplier at the same point, retired 2026-09-19 (the rules register's
 *   retired list), said nothing `gain` does not say, so §6 now teaches one word and stages an
 *   audible level A/B. The numbers carry the old sound over by multiplication: §6's 0.2 and
 *   the summary line's 0.225 are the products of the two levels those lines used to carry.
 *   `velocity` and mute/solo still scale `gain`; `velocity` is sprudel's own word and is
 *   multiplied into `gain` where the voice crosses the wire, so the backend sees one level.
 * - At this lesson's 30 RPM a cycle is 2 s and a step is 0.25 s, which is why
 *   `delay(time = 0.25)` lands each echo exactly one step later. B1 licensed the "at 30 RPM"
 *   caveat; §3 restates it rather than assuming it.
 *
 * ⚠️ NO ORDER A/B HERE, by plan ruling. The per-voice chain order comes from the
 * PipelineDsl preset (FilterPipelineBuilder iterates the preset's stages), so sprudel CALL
 * order does NOT reorder the chain. The order-matters demo belongs to the Pipeline lesson
 * (C7) via `.pipeline()`. §4 forward-references it by topic, since C7 has no name yet.
 *
 * One of the biggest `teaches` lists in the corpus (4). The plan assigns all of it to this one slot,
 * and it is one theme in three moves: dress it, dirty it, lift it. If a review panel finds
 * it dense, the natural split is space (§§1-3) and dirt-plus-level (§§4-6).
 *
 * Render-QA measurement items: §4's dry-vs-distorted pair is level-unequal in the loud
 * direction by nature (that IS what §6 then fixes), and §1/§3's wet lines add energy.
 * All three are narrated; sanctioned parity exceptions.
 */
val spaceAndDirtTutorial = Tutorial(
    slug = "space-and-dirt",
    title = Tut.spaceAndDirt,
    description = "Put a sound in a room, throw an echo after it, rough it up, and set the level it leaves at.",
    difficulty = TutorialDifficulty.Intermediate,
    depth = TutorialDepth.Standard,
    tags = listOf(TutorialTag.Effects, TutorialTag.Mixing),
    teaches = listOf("reverb", "delay", "distort", "onepole"),
    sections = listOf(
        TutorialSection(
            heading = "How much goes in: wet and size",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    ${Tut.layers} borrowed two settings to give its melody a space of its own, and promised the full story later. Here it is, on the melody you have carried since ${Tut.firstNotes}, played by the bright voice from ${Tut.theFourWaveforms} with the pluck shape from ${Tut.shapeOfANote} so every note ends cleanly and you can hear what is left behind.

                    **Reverb** is the wash of reflections a space adds. `reverb()` puts a sound in one, and its first two numbers answer different questions. `wet` is how much of the sound is sent into the room, from 0 to 1. `size` is how big that room is.

                    **Try it:** swap the `//` and press **Update**. Then, with the first line live, grow `size` to 8, and shrink it to 1.

                    **Listen for:** the rests. Dry, each note stops and leaves silence. Wet, the silence is full of the note that just ended, and at `size` 8 the tail is still going when the next note arrives.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    note("a3 c4 d4 ~  e4 d4 c4 ~").sound("saw").adsr(0.001, 0.3, 0, 0.1).reverb(wet = 0.4, size = 4).gain(0.5)  // 0.4 goes into a room of size 4
                    // note("a3 c4 d4 ~  e4 d4 c4 ~").sound("saw").adsr(0.001, 0.3, 0, 0.1).gain(0.5)                           // dry, swap to compare
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "What you leave out",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    You do not have to give every number. Each setting you leave out takes a default that already sounds like something: a `reverb()` without `size` puts the sound in a medium room, size 5, and one without `wet` sends a quarter of it in. You only have to write the numbers you want to be different.

                    **Try it:** delete `.reverb(size = 4)` from the line below and press **Update**. Then put it back.

                    **Listen for:** a slightly longer tail, not silence. With `size` gone the room falls back to its default, 5, one size bigger than the 4 you took away. The delay in the next section works the same way.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    note("a3 c4 d4 ~  e4 d4 c4 ~").sound("saw")        // the same melody all lesson
                      .adsr(0.001, 0.3, 0, 0.1)                        // the pluck: clean ends, so tails show
                      .reverb(0.4)                                     // how much goes in
                      .reverb(size = 4)                                // how big the room is: delete this and the default takes over
                      .gain(0.5)                                       // synths sit at 0.5
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "The other space: delay",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    A room blurs a sound. A **delay** repeats it: the same note again, a moment later, and again, quieter each time. `delay()` is its send, the twin of `reverb()`, and its second number is a time in seconds.

                    This lesson runs at 30 RPM, so a cycle is two seconds and each of the eight steps is a quarter of one. `delay(time = 0.25)` therefore drops each echo exactly one step behind the note that made it, which is why the line sounds doubled rather than smeared. `feedback` is how much of each echo is fed back in to echo again: how many repeats you get.

                    **Try it:** press play, then change `feedback` to 0.75 and press **Update**. Then set `time` to 0.125, half a step.

                    **Listen for:** the melody answering itself a step later. At `feedback` 0.75 the answers pile up until the line is more echo than notes; at a time of 0.125 the echoes fall between the steps and the line doubles in speed instead.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    note("a3 c4 d4 ~  e4 d4 c4 ~").sound("saw")           // the same melody
                      .adsr(0.001, 0.3, 0, 0.1)                           // the pluck shape
                      .delay(0.4)                                         // how much goes in, exactly like reverb
                      .delay(time = 0.25)                                 // how long until the echo: one step, at 30 RPM
                      .delay(feedback = 0.4)                              // how much of each echo echoes again
                      .gain(0.5)                                          // synths sit at 0.5
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "Dirt: distort",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    Rooms and echoes put a sound somewhere. **Distortion** changes the sound itself: drive it harder than it wants to go and it stops being polite, gaining an edge and a growl it did not have.

                    `distort()` takes an amount, and optionally the name of a shape, because there is more than one way to be rude. `"soft"` rounds the peaks off warmly; `"tube"` leans the way a valve amplifier leans; `"hard"` simply refuses to go past a limit.

                    **Try it:** swap the `//` and press **Update**. Then add a shape to the live line: `distort(0.5, "tube")`, then `"hard"`, then `"fold"`.

                    **Listen for:** the pluck growing teeth. It is also plainly louder, which is a real problem and the reason this lesson has one section left. (Where distortion sits among the other effects is not something the call order decides; a Motor-track lesson still to come takes that up.)
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    note("a3 c4 d4 ~  e4 d4 c4 ~").sound("saw").adsr(0.001, 0.3, 0, 0.1).distort(0.5).gain(0.5)  // driven hard: edge and growl
                    // note("a3 c4 d4 ~  e4 d4 c4 ~").sound("saw").adsr(0.001, 0.3, 0, 0.1).gain(0.5)            // clean, swap to compare
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "The voice's own tone control: onepole",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    Distortion adds brightness along with the growl, usually more than you wanted. `onepole()` is where you decide how bright this voice is: a **tone control**, one number in hertz, lower is darker, and `0` means off.

                    It belongs to the voice itself rather than to the treatment around it, so it does not trim the dirt afterwards; it sets what the dirt is given to work with.

                    This is not the `lpf` from ${Tut.filters}, and the difference is worth keeping. `lpf` is the filter you shape a sound with: it has an edge you can make sing, and ${Tut.theFilterEnvelope} put that edge on the move. `onepole` has no edge and nothing to sweep. You set it once and leave it.

                    **Try it:** swap the `//` and press **Update**. Then walk `onepole` down: 6000, then 2500, then 1200.

                    **Listen for:** not only less glare on top, but a different growl underneath. A darker voice gives the distortion less to chew on, so the dirt changes character as it changes brightness. Drive and tone turn out to be one decision, not two.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    note("a3 c4 d4 ~  e4 d4 c4 ~").sound("saw").adsr(0.001, 0.3, 0, 0.1).distort(0.5).onepole(3500).gain(0.5)  // driven, then darkened
                    // note("a3 c4 d4 ~  e4 d4 c4 ~").sound("saw").adsr(0.001, 0.3, 0, 0.1).distort(0.5).gain(0.5)             // driven and glaring, swap
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "The last word: gain",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    Everything this lesson added made the line louder, and a sound you cannot place in a mix is not finished. `gain()` is the level the voice leaves at: one multiplier on the finished sound, and there is only one of them.

                    Every line in this lesson has ended in `gain(0.5)` without a word about why. This is why. A voice needs a level, and you cannot know which one until the sound is designed, so you set it last, when the room, the echo, the drive and the tone are where you want them and the only thing still wrong is how big the line is.

                    It takes a pattern like everything else, which is how ${Tut.spaceAndRests} put accents on individual hits: one number for the whole line, a pattern for note by note, the same word either way. A mute or a fade scales it too.

                    **Try it:** swap the `//` and press **Update**. Then walk `gain` from 0.2 up to 0.5 and find the point where the growl still reads but stops shouting.

                    **Listen for:** nothing changing except size. The growl and the tone are exactly as you left them; the line simply stops dominating everything around it.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    note("a3 c4 d4 ~  e4 d4 c4 ~").sound("saw").adsr(0.001, 0.3, 0, 0.1).distort(0.5).onepole(3500).gain(0.2)     // same growl, trimmed on the way out
                    // note("a3 c4 d4 ~  e4 d4 c4 ~").sound("saw").adsr(0.001, 0.3, 0, 0.1).distort(0.5).onepole(3500).gain(0.5)  // untrimmed, swap to compare
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "Dressed, dirtied, lifted",
            blocks = listOf(
                Block.Markdown(
                    markdown = """
                    All of it on one line, in the order the lesson met it: a room to sit in, an echo to answer with, drive, a tone control to tame the drive, and a trim on the way out. Nothing here is new.

                    **Try it:** delete one call at a time, from the bottom up, pressing **Update** after each, until the plain pluck from ${Tut.shapeOfANote} is all that is left. Then build it back and change one number in each.

                    **Listen for:** how much of what you think of as an instrument is the treatment rather than the notes. The melody has not changed since ${Tut.firstNotes}. Everything else has.
                    """.trimIndent(),
                ),
                Block.Code(
                    code = """
                    note("a3 c4 d4 ~  e4 d4 c4 ~").sound("saw")           // the melody, unchanged all course
                      .adsr(0.001, 0.3, 0, 0.1)                           // the pluck shape
                      .reverb(wet = 0.3, size = 3)                        // a small room: how much, and what into
                      .delay(wet = 0.25, time = 0.25, feedback = 0.3)     // an echo one step behind, a couple of repeats
                      .distort(0.4)                                       // driven, but not shouting
                      .onepole(3500)                                      // the glare taken off the drive
                      .gain(0.225)                                        // how loud it leaves
                    """.trimIndent(),
                ),
            ),
        ),
    ),
)
