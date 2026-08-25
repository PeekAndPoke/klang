/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class VoiceCompressorSpec : StringSpec({

    "fromParams returns null when no field is set" {
        Voice.Compressor.fromParams(null, null, null, null, null) shouldBe null
    }

    "fromParams builds full settings" {
        val c = Voice.Compressor.fromParams(-20.0, 4.0, 6.0, 0.003, 0.1)

        c shouldNotBe null
        c!!.thresholdDb shouldBe -20.0
        c.ratio shouldBe 4.0
        c.kneeDb shouldBe 6.0
        c.attackSeconds shouldBe 0.003
        c.releaseSeconds shouldBe 0.1
    }

    "fromParams applies classic defaults for missing tails (threshold + ratio only)" {
        val c = Voice.Compressor.fromParams(-15.0, 3.0, null, null, null)

        c shouldNotBe null
        c!!.thresholdDb shouldBe -15.0
        c.ratio shouldBe 3.0
        c.kneeDb shouldBe 6.0
        c.attackSeconds shouldBe 0.003
        c.releaseSeconds shouldBe 0.1
    }

    "fromParams applies defaults for a missing head (knee only)" {
        val c = Voice.Compressor.fromParams(null, null, 2.0, null, null)

        c shouldNotBe null
        c!!.thresholdDb shouldBe -20.0
        c.ratio shouldBe 4.0
        c.kneeDb shouldBe 2.0
    }
})
