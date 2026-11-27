/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.strings

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.song.Envelope
import kotlin.math.exp

private const val EPS = 1e-9

/** A long, sustained voice (as the glass guitar's own envelope in Noch seltsamere Dinge is): two thirds held, 3 s of ring. */
private val SUSTAINED = Envelope(attack = 0.01, decay = 1.0, sustain = 0.66, release = 2.75)

/** Sirene's arpeggio there: a quick attack, a slow decay to a half, and a fifth of a second of release. */
private val ARP = Envelope(attack = 0.008, decay = 3.0, sustain = 0.5, release = 0.2)

class NoteLightSpec : StringSpec({

    "without an envelope a short note's light falls as it always did: from the pluck, and quicker from its end" {
        for (age in listOf(0.0, 0.1, 0.2, 0.3, 0.6, 1.2)) {
            NoteLight.level(age = age, held = 0.2, envelope = null) shouldBe
                (exp(-age / 0.25 - maxOf(0.0, age - 0.2) / 0.06) plusOrMinus EPS)
        }
    }

    "without an envelope a long held note keeps a glow as long as it is held: the glass guitar's, as the song has it" {
        // held 5.4 s; the band cannot read the instrument's own envelope, the songs are not written for it
        NoteLight.level(age = 3.0, held = 5.4, envelope = null) shouldBe (0.35 plusOrMinus EPS)
        NoteLight.level(age = 5.4, held = 5.4, envelope = null) shouldBe (0.35 plusOrMinus EPS)
        NoteLight.level(age = 5.4 + 0.12, held = 5.4, envelope = null) shouldBe (0.35 * exp(-0.12 / 0.25 - 2.0) plusOrMinus EPS)
        NoteLight.lasts(held = 5.4, envelope = null) shouldBe (5.4 + 5 * 0.06 plusOrMinus EPS)
    }

    "a long note with a sustain stays lit as long as it sounds" {
        // held 5.4 s (4 cycles of 1.94 s times a clip of 0.7), then nearly 3 s of release
        withClue("held, after the decay: at the sustain") {
            NoteLight.level(age = 4.0, held = 5.4, envelope = SUSTAINED) shouldBe (0.66 plusOrMinus 0.01)
        }

        withClue("a second into the release: a third of where it began left") {
            (NoteLight.level(age = 6.4, held = 5.4, envelope = SUSTAINED) > 0.66 / 3) shouldBe true
        }

        withClue("at the end of the release: 5 % of where it began") {
            NoteLight.level(age = 5.4 + 2.75, held = 5.4, envelope = SUSTAINED) shouldBe (0.66 * 0.0498 plusOrMinus 0.002)
        }

        withClue("in the release, where the band knows the envelope, it is lit; where not, the glow is out") {
            (NoteLight.level(age = 6.4, held = 5.4, envelope = null) < 0.01) shouldBe true
        }
    }

    "a short note goes out soon after it ends: Sirene's" {
        // held a quarter of a second, a fifth of a second of release
        val atEnd = NoteLight.level(age = 0.25, held = 0.25, envelope = ARP)

        // (the slow decay toward the half has begun: 0.89)
        (atEnd > 0.85) shouldBe true
        (NoteLight.level(age = 0.45, held = 0.25, envelope = ARP) < 0.05 * atEnd + EPS) shouldBe true
    }

    "it rises over the attack, then falls toward the sustain over the decay, 5 % of the way left at its end" {
        val env = Envelope(attack = 0.1, decay = 0.3, sustain = 0.4, release = 0.1)

        NoteLight.level(age = 0.05, held = 1.0, envelope = env) shouldBe (0.5 plusOrMinus EPS)
        NoteLight.level(age = 0.1, held = 1.0, envelope = env) shouldBe (1.0 plusOrMinus EPS)
        NoteLight.level(age = 0.4, held = 1.0, envelope = env) shouldBe (0.4 + 0.6 * exp(-3.0) plusOrMinus EPS)
    }

    "a release of nothing still takes a few frames: the light never goes out in one" {
        val env = Envelope(attack = 0.0, decay = 0.0, sustain = 1.0, release = 0.0)

        NoteLight.level(age = 1.0 + NoteLight.QUICKEST, held = 1.0, envelope = env) shouldBe (exp(-1.0) plusOrMinus EPS)
    }

    "no time, a time before the pluck, or an envelope that says nothing" {
        NoteLight.level(age = Double.NaN, held = 1.0, envelope = SUSTAINED) shouldBe 0.0
        NoteLight.level(age = -0.1, held = 1.0, envelope = SUSTAINED) shouldBe 0.0
        NoteLight.level(age = 0.3, held = Double.NaN, envelope = null) shouldBe (exp(-0.3 / 0.25 - 0.3 / 0.06) plusOrMinus EPS)
        NoteLight.level(age = 0.3, held = 1.0, envelope = SUSTAINED.copy(decay = Double.NaN)) shouldBe
            NoteLight.level(age = 0.3, held = 1.0, envelope = null)
    }

    "how long a note is lit: its gate and five falls of its release; without an envelope, the four seconds of old" {
        NoteLight.lasts(held = 5.4, envelope = SUSTAINED) shouldBe (5.4 + 5 * 2.75 / 3 plusOrMinus EPS)
        // never under the four seconds the player always looked back: its eyes' glow counts them
        NoteLight.lasts(held = 0.25, envelope = ARP) shouldBe 4.0
        NoteLight.lasts(held = 0.3, envelope = null) shouldBe 4.0
        NoteLight.lasts(held = Double.NaN, envelope = SUSTAINED) shouldBe (5 * 2.75 / 3 plusOrMinus EPS)
        // under 1 % left where it ends
        (NoteLight.level(age = NoteLight.lasts(held = 5.4, envelope = SUSTAINED), held = 5.4, envelope = SUSTAINED) < 0.01) shouldBe true
    }

    "the look back reaches a long note plucked long ago: at six seconds it is still lit" {
        // a roots note at 0 s, lit for 5.4 s and its release, then a short arpeggio note every quarter second
        val starts = doubleArrayOf(0.0, 1.0, 1.25, 1.5)
        val lasts = doubleArrayOf(NoteLight.lasts(5.4, SUSTAINED), 0.6, 0.6, 0.6)
        val reach = NoteLight.reach(starts = starts, lasts = lasts)

        // at 6 s the last note's light is long out, but the reach up to it still holds the roots note
        (reach[3] > 6.0) shouldBe true
        (NoteLight.level(age = 6.0, held = 5.4, envelope = SUSTAINED) > 0.3) shouldBe true
        // where nothing long came before: the reach is the latest note's own
        NoteLight.reach(starts = doubleArrayOf(0.0, 1.0), lasts = doubleArrayOf(0.5, 0.5)).toList() shouldBe listOf(0.5, 1.5)
    }
})
