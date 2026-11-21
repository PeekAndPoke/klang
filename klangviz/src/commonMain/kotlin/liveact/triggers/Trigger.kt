/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.triggers

/**
 * When something in the show happens: one small vocabulary for every performer (KLANG-00479: "a skill is two things:
 * what and when"). A trigger is plain data. What it gives is a pure function of the show's time, its seed and the
 * song, so any moment of the show gives the same answer whenever it is asked for, and a still can be taken again.
 *
 * The kinds in use:
 * - By chance, in slots of time ([ChanceInSlots]): the looks, the idle skills and the straightening up on the
 *   members' sheets, and the band's eye contact in the score.
 * - By the part, before a phrase ([BeforePhrase]): the count in.
 *
 * What goes by the part goes by its pauses ([PartPause]), each with its own numbers: a phrase begins after a pause
 * long enough ([BeforePhrase]), the crossed mallets come at every pause long enough for them (`CrossedMallets`), and
 * an idle skill, timed by chance, is used only within a pause (`idleAt`).
 *
 * To come, each a kind of its own here once something uses it (nothing is built for them yet):
 * - By the song's form, in rounds of bars: the camera's rounds and the lamps' moods (KLANG-00479, steps 7 and 8).
 * - By what sounds: a run of bass drum hits (the strobe, step 8), and the music's energy (the maintainer, 2026-11-21:
 *   kept in mind, not read yet).
 * - Always: the breath, the eyes' waver, the nod.
 * - On a cue: a named moment of a song, written in its score (step 9).
 */
sealed interface Trigger
