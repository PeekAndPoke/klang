/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.sprudel.lang.gain
import io.peekandpoke.klang.sprudel.lang.note
import io.peekandpoke.klang.sprudel.lang.velocity

/**
 * `velocity` is sprudel's word and stops at the wire: [SprudelVoiceData.toVoiceData] multiplies it
 * into `gain`, which is the one level word the backend knows (signal-flow plan section 6).
 *
 * The four null/non-null combinations pin the fold, including the one that matters for the wire's
 * sparseness: both unset stays unset, so the engine's own `?: 1.0` answers and nothing is written.
 * The product is asserted by raw bits because an unaccented voice must keep the bits the voice
 * factory used to give it. What that pins is the OPERANDS and the substitution for each of them,
 * not their order: an IEEE multiply of two operands is bit-commutative, so `gain * velocity` and
 * `velocity * gain` are the same double. Order only starts to matter once a THIRD factor joins
 * the product, which is exactly what the song migration ran into and why its levels are allowed
 * to move in the last bits.
 */
class WireGainFoldSpec : StringSpec({

    "both unset: the wire's gain stays unset" {
        createSprudelVoiceData { }.toVoiceData().gain shouldBe null
    }

    "gain alone crosses unchanged" {
        val wire = createSprudelVoiceData { gain = 0.4 }.toVoiceData().gain

        wire.shouldNotBeNull().toRawBits() shouldBe 0.4.toRawBits()
    }

    "velocity alone becomes the wire's gain" {
        val wire = createSprudelVoiceData { velocity = 0.7 }.toVoiceData().gain

        wire.shouldNotBeNull().toRawBits() shouldBe 0.7.toRawBits()
    }

    "both set: the wire's gain is gain * velocity, bit for bit" {
        val g = 0.37
        val v = 0.83
        val wire = createSprudelVoiceData { gain = g; velocity = v }.toVoiceData().gain.shouldNotBeNull()

        wire.toRawBits() shouldBe (g * v).toRawBits()

        // engagement: the fold is neither side on its own
        wire shouldNotBe g
        wire shouldNotBe v
    }

    // ── A non-finite operand reads as unset, PER OPERAND and before the product ───────────────

    "a NaN velocity leaves the gain the author asked for" {
        val wire = createSprudelVoiceData { gain = 0.5; velocity = Double.NaN }.toVoiceData().gain

        // Substituting AFTER the product would hand the engine a NaN, which it reads as 1.0:
        // full level where the author wrote half.
        wire.shouldNotBeNull().toRawBits() shouldBe 0.5.toRawBits()
    }

    "an infinite velocity leaves the gain the author asked for" {
        val wire = createSprudelVoiceData { gain = 0.5; velocity = Double.POSITIVE_INFINITY }.toVoiceData().gain

        wire.shouldNotBeNull().toRawBits() shouldBe 0.5.toRawBits()
    }

    "a NaN gain leaves the velocity alone" {
        val wire = createSprudelVoiceData { gain = Double.NaN; velocity = 0.7 }.toVoiceData().gain

        wire.shouldNotBeNull().toRawBits() shouldBe 0.7.toRawBits()
    }

    "a non-finite operand next to an unset one stays unset" {
        createSprudelVoiceData { velocity = Double.NaN }.toVoiceData().gain shouldBe null
        createSprudelVoiceData { gain = Double.NEGATIVE_INFINITY }.toVoiceData().gain shouldBe null
    }

    "a patterned velocity folds per event" {
        val events = note("c e g").gain(0.5).velocity("1 0.5 0.25").queryArc(0.0, 1.0)

        events.size shouldBe 3
        events.map { it.data.toVoiceData().gain.shouldNotBeNull().toRawBits() } shouldBe listOf(
            (0.5 * 1.0).toRawBits(),
            (0.5 * 0.5).toRawBits(),
            (0.5 * 0.25).toRawBits(),
        )
    }
})
