/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl

/**
 * The per-playback orbit-chain registry: what `Cmd.RegisterKatalyst` writes into, and what a
 * cylinder will read from in step 2.
 *
 * The two properties that matter are the two the master registry has, for the same reasons: a fork
 * sees its parent's chains without the parent seeing the fork's (so a playback's chains die with
 * it), and an unknown name resolves to NULL rather than to some fallback chain, so "the register
 * command has not arrived yet" stays distinguishable from "this is the chain".
 */
class KatalystRegistrySpec : StringSpec({

    val chain = KatalystDsl.of(KatalystStageDsl.Reverb(wet = IgnitorDsl.Constant(0.3)))
    val other = KatalystDsl.of(KatalystStageDsl.Gain(gain = IgnitorDsl.Constant(1.4)))

    "an unknown name resolves to null, never to a fallback chain" {
        val registry = KatalystRegistry()

        registry.find("nobody").shouldBeNull()
        registry.find(null).shouldBeNull()
    }

    "a registered chain comes back, and the lookup is case-insensitive" {
        val registry = KatalystRegistry()
        registry.register("Katalyst-7", chain)

        registry.find("Katalyst-7") shouldBe chain
        registry.find("katalyst-7") shouldBe chain
    }

    "a fork sees the parent's chains; the parent never sees the fork's" {
        val parent = KatalystRegistry()
        parent.register("shared", chain)

        val fork = parent.fork()
        fork.register("mine", other)

        fork.find("shared") shouldBe chain
        fork.find("mine") shouldBe other
        // The playback-local chain must not leak back up, or it would outlive its playback.
        parent.find("mine").shouldBeNull()
    }

    "a fork shadows a parent name without changing it" {
        val parent = KatalystRegistry()
        parent.register("bus", chain)

        val fork = parent.fork()
        fork.register("bus", other)

        fork.find("bus") shouldBe other
        parent.find("bus") shouldBe chain
    }
})
