/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.refactorme

import io.peekandpoke.klang.viz.liveact.song.Drum
import io.peekandpoke.klang.viz.liveact.song.DrumHit
import io.peekandpoke.klang.viz.liveact.song.Limb
import io.peekandpoke.klang.viz.liveact.song.Note
import io.peekandpoke.klang.viz.liveact.song.Strum
import kotlin.math.floor

// The test notes: what the band plays on a page without the recording (`?test`), one stand-in for each part the cast
// gives out (see SchmetterlingCast). A bridge between a song and the stage would have each instrument say what it
// plays where it is given nothing, or the band stand still.

/**
 * A test beat for [bars] bars at [bpm], for the page without the recording: hats in eighths, kick and snare, and
 * every fourth bar a fill round the toms into a crash. The kick is the right foot's.
 */
fun testBeat(bars: Int, bpm: Double): List<DrumHit> {
    val hits = mutableListOf<DrumHit>()
    val sec = 60 / bpm

    fun add(beat: Double, drum: Drum, limb: Limb, v: Double) {
        hits.add(DrumHit(t = beat * sec, drum = drum, limb = limb, v = v))
    }

    for (bar in 0 until bars) {
        val b = (bar * 4).toDouble()
        val fill = bar % 4 == 3
        val afterFill = bar % 4 == 0 && bar > 0

        for (i in 0 until (if (fill) 4 else 8)) {
            if (i == 0 && afterFill) {
                add(beat = b, drum = Drum.Crash, limb = Limb.R, v = 1.0)
            } else {
                val drum = if (bar % 8 >= 4) Drum.Ride else Drum.Hat

                add(beat = b + i * 0.5, drum = drum, limb = Limb.R, v = if (i % 2 == 0) 0.8 else 0.45)
            }
        }

        add(beat = b, drum = Drum.Kick, limb = Limb.KickR, v = 1.0)
        add(beat = b + 1, drum = Drum.Snare, limb = Limb.L, v = 0.9)

        if (!fill) {
            add(beat = b + 2, drum = Drum.Kick, limb = Limb.KickR, v = 0.9)
            add(beat = b + 2.5, drum = Drum.Kick, limb = Limb.KickR, v = 0.7)
            add(beat = b + 3, drum = Drum.Snare, limb = Limb.L, v = 1.0)
        } else {
            val order = listOf(Drum.Snare, Drum.Snare, Drum.Tom, Drum.Tom, Drum.Tom, Drum.Floor, Drum.Floor, Drum.Floor)

            order.forEachIndexed { i, drum ->
                add(beat = b + 2 + i * 0.25, drum = drum, limb = if (i % 2 == 0) Limb.R else Limb.L, v = 0.6 + 0.05 * i)
            }

            add(beat = b + 2, drum = Drum.Kick, limb = Limb.KickR, v = 0.9)
            add(beat = b + 3, drum = Drum.Kick, limb = Limb.KickR, v = 0.9)
        }
    }

    return hits.sortedBy { it.t }
}

/**
 * A test tune for the marimba, for the page without the recording: a broken chord, a note every quarter second, a
 * fourth higher in every other stretch of sixteen.
 */
fun testTune(): List<Note> = List(256) { i ->
    Note(
        t = i * 0.25,
        midi = 64 + listOf(0, 3, 7, 10, 12, 10, 7, 3)[i % 8] + (if (i % 32 >= 16) 5 else 0),
        v = if (i % 4 == 0) 1.0 else 0.6,
        // the prototype's test tune names no length; nothing reads it for the marimba
        d = 0.0,
    )
}

/** A test call for the trumpet, for the page without the recording: phrases of held notes with rests between them. */
fun testCall(): List<Note> = List(128) { i ->
    Note(
        t = 1 + i * 0.75 + (if (i % 8 >= 6) 3 else 0),
        d = 0.6,
        midi = 65 + listOf(0, 2, 3, 7, 5, 3, 10, 12)[i % 8],
        v = 1.0,
    )
}.sortedBy { it.t }

/**
 * A test riff for a strung instrument, for the page without the recording: a note every [step] seconds from the
 * midi note [low] up.
 */
fun testRiff(step: Double, low: Int): List<Strum> = List(512) { i ->
    val midi = low + listOf(0, 0, 3, 0, 5, 0, 7, 3)[floor(i * step * 2).toInt() % 8]

    // the prototype's riff names no length and no chord: its player takes 0.3 s and the one note then
    Strum(t = i * step, d = 0.3, midi = midi, v = if (i % 2 == 0) 1.0 else 0.7, all = listOf(midi))
}
