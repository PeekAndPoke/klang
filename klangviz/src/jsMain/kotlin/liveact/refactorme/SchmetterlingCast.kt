/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.refactorme

import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.instruments.bass.Bass
import io.peekandpoke.klang.viz.liveact.band.instruments.guitar.Guitar
import io.peekandpoke.klang.viz.liveact.band.instruments.strings.BodyOutline
import io.peekandpoke.klang.viz.liveact.band.instruments.strings.StringDesign
import io.peekandpoke.klang.viz.liveact.song.DrumHit
import io.peekandpoke.klang.viz.liveact.song.Note
import io.peekandpoke.klang.viz.liveact.song.Part
import io.peekandpoke.klang.viz.liveact.song.Strum

// The package `refactorme` holds everything that ties the live act to one song, "Der Schmetterling": who plays which
// instrument, which of the song's parts each one plays and by which of its tags (SchmetterlingTags), and the test
// notes in their place (_test_notes.kt). It is to be emptied, not grown (the maintainer, 2026-11-17: "Things that are
// currently hardcoded against Schmetterling need to be moved into a refactorme or similar place. This would then be
// a good starting point for thinking about how to decouple the whole live act from songs and how to bridge the
// gap."). Each thing here says what a real bridge between a song and the stage would have to supply in its place.

/**
 * Der Schmetterling's cast: which member plays which instrument, which of the song's parts, and which variant of
 * the instrument. A bridge between a song and the stage would supply all of it: the song's parts, each with the
 * instrument that plays it, and who of the band takes it.
 */
object SchmetterlingCast {
    /** Who sits at the drums. A bridge: which member plays the song's drums. */
    val DRUMS: Player = Player.Wucht

    /** Who plays the marimba. A bridge: which member plays the song's tune, and on which instrument. */
    val MARIMBA: Player = Player.Rune

    /** Who plays the trumpet: the frontwoman. A bridge: which member plays the held melody, and on which instrument. */
    val TRUMPET: Player = Player.Sirene

    /**
     * One who plays a strung instrument: the [player], the instrument ([design]), the song's [tags] they play, the test
     * riff in their place (a note every [riffStep] seconds from the midi note [riffLow]), and the instrument's body
     * where theirs is not the instrument's own ([outline]).
     */
    class OnStrings(
        val player: Player,
        val design: StringDesign,
        val tags: List<String>,
        val riffStep: Double,
        val riffLow: Int,
        val outline: BodyOutline? = null,
    ) {
        /** What they play in [show]: the song's events with their [tags], else the test riff. */
        fun part(show: Show): Part<Strum> =
            show.song?.part { strumsOf(tags) } ?: Part.fixed(testRiff(step = riffStep, low = riffLow))
    }

    /**
     * Who plays a strung instrument, in the order they are moved each frame. A bridge: which of the song's parts is a
     * bass or a guitar, who plays it, and on which body.
     */
    val STRINGS: List<OnStrings> = listOf(
        OnStrings(
            player = Player.Beben,
            design = Bass.DESIGN,
            tags = listOf(SchmetterlingTags.BASS),
            riffStep = 0.5,
            riffLow = 28,
        ),
        OnStrings(
            player = Player.Skalpell,
            design = Guitar.DESIGN,
            tags = listOf(SchmetterlingTags.GUITAR1),
            riffStep = 0.25,
            riffLow = 52,
        ),
        OnStrings(
            player = Player.Klaue,
            design = Guitar.DESIGN,
            tags = listOf(SchmetterlingTags.GUITAR2, SchmetterlingTags.GUITAR3),
            riffStep = 0.125,
            riffLow = 40,
            outline = Guitar.SINGLE_CUTAWAY,
        ),
    )

    /** The drummer's hits in [show]: the song's drums (see `Song.drumHits`), else the test beat. A bridge: the song's drum part. */
    fun drumHits(show: Show): Part<DrumHit> = show.song?.part { drumHits() } ?: Part.fixed(testBeat(64, show.bpm))

    /** The marimba's notes in [show]: the song's tune, else the test tune. A bridge: the song's part for it. */
    fun marimbaNotes(show: Show): Part<Note> = show.song?.part { notesOf(SchmetterlingTags.LEAD) } ?: Part.fixed(testTune())

    /** The trumpet's notes in [show]: the song's held melody, else the test call. A bridge: the song's part for it. */
    fun trumpetNotes(show: Show): Part<Note> = show.song?.part { notesOf(SchmetterlingTags.TROMMEL) } ?: Part.fixed(testCall())

    /**
     * When [player] begins a note in [show], sorted, as far as the song is known: what the camera and the eyes go by.
     * A bridge: which of the song's parts each member plays.
     */
    fun playsAt(show: Show, player: Player): List<Double> = when (player) {
        Player.Wucht -> show.times(SchmetterlingTags.KICK, SchmetterlingTags.SNARE)
        Player.Beben -> show.times(SchmetterlingTags.BASS)
        Player.Skalpell -> show.times(SchmetterlingTags.GUITAR1)
        Player.Klaue -> show.times(SchmetterlingTags.GUITAR2, SchmetterlingTags.GUITAR3)
        Player.Rune -> show.times(SchmetterlingTags.LEAD)
        Player.Sirene -> show.times(SchmetterlingTags.TROMMEL)
    }
}
