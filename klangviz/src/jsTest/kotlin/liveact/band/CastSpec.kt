/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.band.instruments.bass.Bass
import io.peekandpoke.klang.viz.liveact.band.instruments.guitar.Guitar
import io.peekandpoke.klang.viz.liveact.song.DrumTags

/** A cast of five, with its own tags: Klaue has no seat in it. */
private val CAST = Cast(
    drums = DrumSeat(player = Player.Rune, plays = "Drums", kit = DrumTags(kick = "bd", snare = "sd", cymbals = listOf("cy"))),
    marimba = NoteSeat(player = Player.Wucht, plays = "Marimba", tag = "mallets"),
    trumpet = NoteSeat(player = Player.Beben, plays = "Trumpet", tag = "horn"),
    strings = listOf(
        StringSeat(player = Player.Sirene, plays = "Bass", design = Bass.B, tags = listOf("low"), riffStep = 0.5, riffLow = 28),
        StringSeat(player = Player.Skalpell, plays = "Guitar", design = Guitar.A, tags = listOf("g1", "g2"), riffStep = 0.25, riffLow = 52),
    ),
    seldom = listOf(Player.Beben),
)

/** What a cast says of each player: a check for the future, for whoever casts a song. */
class CastSpec : StringSpec({

    "each one is seen to play by their seat's tags: the drummer by the kick and the snare, not the cymbals" {
        CAST.tagsOf(Player.Rune) shouldBe listOf("bd", "sd")
        CAST.tagsOf(Player.Wucht) shouldBe listOf("mallets")
        CAST.tagsOf(Player.Beben) shouldBe listOf("horn")
        CAST.tagsOf(Player.Sirene) shouldBe listOf("low")
        CAST.tagsOf(Player.Skalpell) shouldBe listOf("g1", "g2")
    }

    "what each one plays, in words, is their seat's" {
        CAST.playsOf(Player.Rune) shouldBe "Drums"
        CAST.playsOf(Player.Wucht) shouldBe "Marimba"
        CAST.playsOf(Player.Beben) shouldBe "Trumpet"
        CAST.playsOf(Player.Sirene) shouldBe "Bass"
        CAST.playsOf(Player.Skalpell) shouldBe "Guitar"
    }

    "one without a seat plays no tags and nothing in words" {
        CAST.tagsOf(Player.Klaue) shouldBe emptyList()
        CAST.playsOf(Player.Klaue) shouldBe null
    }

    "the one who strolls is the trumpet player, and the players are counted seat by seat" {
        CAST.strolls shouldBe Player.Beben
        CAST.players shouldBe listOf(Player.Rune, Player.Wucht, Player.Beben, Player.Sirene, Player.Skalpell)
    }

    "who keeps their instrument at a change of song: the same drummer, and a strung player with the same design and outline" {
        // the same cast keeps everyone at the drums and the strings; the marimba and the trumpet are never kept
        CAST.keeps(CAST) shouldBe setOf(Player.Rune, Player.Sirene, Player.Skalpell)

        val other = CAST.copy(
            drums = CAST.drums.copy(player = Player.Klaue),
            marimba = null,
            trumpet = null,
            strings = listOf(
                // another design in the same hands
                CAST.strings[0].copy(design = Bass.A),
                // the same design in other hands
                CAST.strings[1].copy(player = Player.Beben),
            ),
        )

        CAST.keeps(other) shouldBe emptySet()
        CAST.keeps(other.copy(strings = listOf(CAST.strings[1]))) shouldBe setOf(Player.Skalpell)
    }
})
