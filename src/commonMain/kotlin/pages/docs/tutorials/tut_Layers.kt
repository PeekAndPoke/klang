/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

/**
 * Curriculum slot B6 — see docs/tasks/tutorial-curriculum.md
 *
 * Keeps two promises: B1's "balancing them with gain() is most of what mixing
 * is", and B3's "also written to sit on top of the groove" — the running
 * example IS the B2 groove plus the B3 melody.
 *
 * Engine truth behind §§3–4 (verified in audio_be): the reverb
 * is per-orbit; `room` is a per-voice SEND into it, and a bare room() is
 * silent — the gate needs roomsize (KatalystReverbEffect). Orbit bus settings
 * are first-writer-wins (Cylinder.kt), so the lesson only ever demos
 * uncontested configurations and teaches the engine's own craft rule — an
 * effect-carrying layer gets its own orbit. The contested shared-channel case
 * (who wins the bus) is an internal ordering detail and is never claimed.
 */
val layersTutorial = Tutorial(
    slug = "layers",
    title = Tut.layers,
    description = "stack() plays several patterns at once — and orbit() gives a layer its own channel.",
    difficulty = TutorialDifficulty.Beginner,
    scope = TutorialScope.Quick,
    tags = listOf(TutorialTag.Patterns, TutorialTag.Mixing),
    teaches = listOf("stack", "orbit"),
    previews = listOf("room", "rsize"),
    sections = listOf(
        TutorialSection(
            heading = "Two lines at once",
            blocks = listOf(
                Block.Text(
                    "Every lesson so far ended with one pattern playing. ${Tut.yourFirstBeat} even " +
                    "made it a rule: with two live lines, only the lower one plays. stack() is the way " +
                    "past it — hand it several patterns, separated by commas, and they all play " +
                    "together, sharing the same cycle.\n\nThe two patterns below are old friends: the " +
                    "groove shaped in ${Tut.spaceAndRests}, and the melody from ${Tut.firstNotes} — " +
                    "which was written to sit on this groove.\n\nListen for: the meeting points. The " +
                    "kick and the melody's first note strike together, and the snare lands with the " +
                    "melody's final note. Two lines, written in separate lessons, locking into one " +
                    "piece of music.",
                ),
                Block.Code(
                    code = """
                    stack(                                      // stack: several patterns, one shared cycle
                      sound("bd ~ hh ~  ~ hh sd ~").gain(0.8),  // the groove from ${Tut.spaceAndRests}
                      note("a3 c4 d4 ~  e4 d4 c4 ~")            // the melody from ${Tut.firstNotes}
                        .sound("sine").gain(0.5)                // its voice and its level, unchanged
                    )
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "Balance is the mix",
            blocks = listOf(
                Block.Text(
                    "${Tut.yourFirstBeat} promised that once several lines run at the same time, " +
                    "balancing them with gain() is most of what mixing is. This is that moment. " +
                    "Nothing below changes what plays or when — only how loud the melody sits next " +
                    "to the drums.\n\nTry it: swap the // and press Update. Then find your own " +
                    "balance: nudge the melody's gain a little at a time until it sits where you " +
                    "want it.\n\nListen for: the same two lines trading jobs. At 0.5 the melody leads and " +
                    "the drums back it; at 0.2 it turns into colour behind the beat. Neither is " +
                    "wrong — in front and behind are both places a layer can live.",
                ),
                Block.Code(
                    code = """
                    stack(sound("bd ~ hh ~  ~ hh sd ~").gain(0.8), note("a3 c4 d4 ~  e4 d4 c4 ~").sound("sine").gain(0.5))    // melody in front
                    // stack(sound("bd ~ hh ~  ~ hh sd ~").gain(0.8), note("a3 c4 d4 ~  e4 d4 c4 ~").sound("sine").gain(0.2)) // melody tucked behind — swap
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "A room for the melody",
            blocks = listOf(
                Block.Text(
                    "Set the drums aside for a moment — just the melody. room() puts a sound in a " +
                    "room: reverb, the wash of reflections a space adds. It comes as a pair with " +
                    "rsize(), and both are borrowed from a Sound-track lesson still to come: room() " +
                    "sets how much of the sound goes into the room, rsize() how big that room " +
                    "is.\n\nTry it: swap the // and press Update. Then, with the first line live, " +
                    "grow rsize to 8, and shrink it to 1.\n\nListen for: the tail. Dry, each note " +
                    "stops and leaves silence; in the room, a wash hangs where the silence was, and " +
                    "the melody sounds further away. That distance is what a room does to a " +
                    "sound.",
                ),
                Block.Code(
                    code = """
                    note("a3 c4 d4 ~  e4 d4 c4 ~").sound("sine").gain(0.5).room(0.4).rsize(4)    // room: how much goes in; rsize: how big it is
                    // note("a3 c4 d4 ~  e4 d4 c4 ~").sound("sine").gain(0.5)                    // dry — swap to compare
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "A channel of your own: orbit",
            blocks = listOf(
                Block.Text(
                    "Now put the drums back. Every line plays into a channel, and unless it says " +
                    "otherwise it plays into channel 0 — so far, everything in this course has. The " +
                    "room itself belongs to the channel, not to the line: a channel has one room, " +
                    "with one size, shared by every layer that plays into that room. A layer that " +
                    "wants a space of its own needs a channel of its own.\n\norbit() picks the " +
                    "channel. Give the melody orbit(1) and its room settings go with it — channel " +
                    "1's room, with only the melody playing into it. The drums keep channel 0 for " +
                    "themselves, dry and untouched.\n\nTry it: swap the // to hear the " +
                    "roomed mix against the dry one. Then move .orbit(1).room(0.4).rsize(4) over " +
                    "to the drum line instead — a wet beat under a dry melody is a real sound too. " +
                    "You choose who lives in the room.\n\nListen for: dry drums under a wet lead. " +
                    "The kick and snare snap shut exactly as they did before the room existed, " +
                    "while every melody note rings out into its own space.",
                ),
                Block.Code(
                    code = """
                    stack(sound("bd ~ hh ~  ~ hh sd ~").gain(0.8), note("a3 c4 d4 ~  e4 d4 c4 ~").sound("sine").gain(0.5).orbit(1).room(0.4).rsize(4))    // wet lead on channel 1
                    // stack(sound("bd ~ hh ~  ~ hh sd ~").gain(0.8), note("a3 c4 d4 ~  e4 d4 c4 ~").sound("sine").gain(0.5))                             // all dry — swap
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "A tiny mix",
            blocks = listOf(
                Block.Text(
                    "Three layers now — one of them new. A bass joins on the low floors from " +
                    "${Tut.firstNotes}: the same plain voice, an octave below the melody's opening " +
                    "a3. Its first hit lands with the kick, its second falls into a gap both other " +
                    "layers leave open, and its e2 sits under the snare, pulling the cycle back to " +
                    "a2. It moves in with the drums on channel 0, dry; the lead keeps its own " +
                    "channel and its room; the groove returns with its accents from " +
                    "${Tut.spaceAndRests}. (The bass sits at 0.6 — low pure tones read quieter " +
                    "than high ones.)\n\nTry it: put // in front of any one layer and press " +
                    "Update — the other two keep playing. Listening to every pair like this is the " +
                    "fastest way to hear what each layer contributes.\n\nListen for: three layers " +
                    "doing three jobs — the groove keeps time, the bass gives it a floor, the lead " +
                    "rings above both. Balancing the three gains is the mix, exactly as " +
                    "${Tut.yourFirstBeat} promised.",
                ),
                Block.Code(
                    code = """
                    stack(
                      sound("bd ~ hh ~  ~ hh sd ~").gain("0.8 0.8 0.4 0.8  0.8 0.5 0.8 0.8"),             // the accented groove — channel 0, dry
                      note("a2 ~ ~ a2  ~ ~ e2 ~").sound("sine").gain(0.6),                                // new: the bass — on the kick, in the gap, then e2 under the snare
                      note("a3 c4 d4 ~  e4 d4 c4 ~").sound("sine").gain(0.5).orbit(1).room(0.4).rsize(4)  // the lead — its own channel, its own room
                    )
                    """.trimIndent(),
                ),
            ),
        ),
    ),
)
