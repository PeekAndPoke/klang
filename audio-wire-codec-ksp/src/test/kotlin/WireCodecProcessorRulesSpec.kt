/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge.ksp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith

/**
 * The processor's rules that need no symbol tree: which scalar the wire refuses by name (KLANG-00433 Decide 1) and the
 * codec format in the schema signature (KLANG-00431 D8). That the walk asks [WireCodecProcessor.refusedScalar] first is
 * what a wire type with a `Long` field shows at the build; the format's place in the hash is pinned by
 * `WireCodecRoundTripSpec` in `audio_bridge`.
 */
class WireCodecProcessorRulesSpec : StringSpec({

    "a Long or a ULong is refused by name, the field and the stone rule named in the error" {
        for (name in listOf("kotlin.Long", "kotlin.ULong")) {
            val error = WireCodecProcessor.refusedScalar(qualifiedName = name, ctx = "VoiceData.startFrame").shouldNotBeNull()

            error shouldStartWith "'$name' at VoiceData.startFrame is not a wire type"
            error shouldContain "the stone rule bans boxed types on the wire"
        }
    }

    "every scalar the wire carries passes, and an unknown name is left to the walk" {
        for (name in listOf("kotlin.String", "kotlin.Double", "kotlin.Int", "kotlin.Boolean", "kotlin.Float", "kotlin.DoubleArray", null)) {
            WireCodecProcessor.refusedScalar(qualifiedName = name, ctx = "X.y") shouldBe null
        }
    }

    "the schema signature starts with the codec's format, then the types in the order given" {
        WireCodecProcessor.WIRE_CODEC_FORMAT shouldBe 1
        WireCodecProcessor.schemaSignature(listOf("A(x:kotlin.Int)", "B|b1,b2")) shouldBe "format=1;A(x:kotlin.Int);B|b1,b2"
    }
})
