/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.scores

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.skills.CONTACT_BACK
import io.peekandpoke.klang.viz.liveact.band.skills.CONTACT_LATE_LEAST
import io.peekandpoke.klang.viz.liveact.band.skills.CONTACT_MEET

/** Every song's score. */
private val SCORES = mapOf("Der Schmetterling" to DER_SCHMETTERLING, "Kokon" to KOKON)

/**
 * What the band needs of a score: a check for the future, for whoever writes a score. Each one casts the whole band
 * and places everyone, or the band would stand without a player, a place or a walk-in.
 */
class ScoresSpec : StringSpec({

    "a score casts every player once" {
        for ((name, score) in SCORES) {
            withClue(name) {
                score.cast.players shouldContainExactlyInAnyOrder Player.entries
                Player.entries shouldContainAll score.cast.seldom
            }
        }
    }

    "a score's plot has a place and a walk-in for every player" {
        for ((name, score) in SCORES) {
            withClue(name) {
                score.plot.places.keys shouldContainExactlyInAnyOrder Player.entries
                score.plot.entrances.keys shouldContainExactlyInAnyOrder Player.entries
            }
        }
    }

    "every walk-in lies within the intro: it begins on the stage's clock and ends before the song" {
        for ((name, score) in SCORES) {
            for ((player, entrance) in score.plot.entrances) {
                withClue("$name, $player") {
                    entrance.start shouldBeGreaterThanOrEqual -score.plot.intro
                    entrance.start shouldBeLessThan entrance.end
                    entrance.end shouldBeLessThanOrEqual 0.0
                }
            }
        }
    }

    "an eye contact has room in its rise for the glance and the answer, and in its fall for the way back" {
        for ((name, score) in SCORES) {
            withClue(name) {
                score.eyeContact.rise shouldBeGreaterThanOrEqual CONTACT_MEET + CONTACT_LATE_LEAST
                score.eyeContact.fall shouldBeGreaterThanOrEqual CONTACT_BACK
            }
        }
    }
})
