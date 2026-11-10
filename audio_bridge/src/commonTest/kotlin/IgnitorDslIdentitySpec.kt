/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * The inline instrument's name ([uniqueId]): one name per tree the ENGINE builds alike, which is the tree's structure
 * and its sharing (`IgnitorDslNameKey`). The engine builds by identity, so a node instance held twice is one signal
 * read twice, and two equal copies are two signals with their own dice (audit L5-02). A noise node's `uid` is not part
 * of the name, so a script evaluated again keeps its noise instrument's name (audit L5-10).
 */
class IgnitorDslIdentitySpec : StringSpec({

    fun pluck(): IgnitorDsl = IgnitorDsl.Pluck()

    "audit L5-02: two trees equal as data and different in sharing get two names" {
        val p = pluck()
        val shared = IgnitorDsl.Plus(left = p, right = p.lowpass(freq = 800.0.ign))
        val twins = IgnitorDsl.Plus(left = pluck(), right = pluck().lowpass(freq = 800.0.ign))

        shared shouldBe twins
        shared.uniqueId() shouldNotBe twins.uniqueId()
    }

    "the same shape built again, sharing included, keeps its name" {
        fun sharedOnce(): IgnitorDsl {
            val p = pluck()

            return IgnitorDsl.Plus(left = p, right = p.lowpass(freq = 800.0.ign))
        }

        fun twinsOnce(): IgnitorDsl = IgnitorDsl.Plus(left = pluck(), right = pluck().lowpass(freq = 800.0.ign))

        sharedOnce().uniqueId() shouldBe sharedOnce().uniqueId()
        twinsOnce().uniqueId() shouldBe twinsOnce().uniqueId()
    }

    "the sharing is read where it sits: a node held twice deeper in the tree, or held twice by two other nodes" {
        val s = IgnitorDsl.Saw()
        val deep = IgnitorDsl.Plus(left = s.lowpass(freq = 900.0.ign), right = s.highpass(freq = 90.0.ign)).mul(0.5.ign)
        val apart = IgnitorDsl.Plus(left = IgnitorDsl.Saw().lowpass(freq = 900.0.ign), right = IgnitorDsl.Saw().highpass(freq = 90.0.ign)).mul(0.5.ign)

        deep shouldBe apart
        deep.uniqueId() shouldNotBe apart.uniqueId()
    }

    "a leaf instance held twice changes nothing: the build makes a leaf anew at every read" {
        val k = IgnitorDsl.Constant(800.0)
        val sharedLeaf = IgnitorDsl.Plus(left = IgnitorDsl.Saw().lowpass(freq = k), right = IgnitorDsl.Sine().lowpass(freq = k))
        val twoLeaves = IgnitorDsl.Plus(
            left = IgnitorDsl.Saw().lowpass(freq = IgnitorDsl.Constant(800.0)),
            right = IgnitorDsl.Sine().lowpass(freq = IgnitorDsl.Constant(800.0)),
        )

        sharedLeaf.uniqueId() shouldBe twoLeaves.uniqueId()
    }

    "audit L5-10: a noise instrument keeps its name when the script is evaluated again" {
        // Every `whitenoise()` call takes a fresh uid, so the two evaluations are not equal as data; the name drops it.
        fun evaluate(): IgnitorDsl = IgnitorDsl.WhiteNoise().lowpass(freq = 2000.0.ign).plus(IgnitorDsl.PinkNoise()).plus(IgnitorDsl.BrownNoise())

        val first = evaluate()
        val second = evaluate()

        first shouldNotBe second
        first.uniqueId() shouldBe second.uniqueId()
    }

    "audit L5-10: two noise calls and one noise read twice are still two names" {
        val n = IgnitorDsl.WhiteNoise()
        val readTwice = IgnitorDsl.Plus(left = n, right = n.lowpass(freq = 2000.0.ign))
        val twoCalls = IgnitorDsl.Plus(left = IgnitorDsl.WhiteNoise(), right = IgnitorDsl.WhiteNoise().lowpass(freq = 2000.0.ign))

        readTwice.uniqueId() shouldNotBe twoCalls.uniqueId()
    }

    "different trees keep different names" {
        IgnitorDsl.Saw().uniqueId() shouldNotBe IgnitorDsl.Sine().uniqueId()
        IgnitorDsl.Saw().lowpass(freq = 800.0.ign).uniqueId() shouldNotBe IgnitorDsl.Saw().lowpass(freq = 801.0.ign).uniqueId()
    }
})
