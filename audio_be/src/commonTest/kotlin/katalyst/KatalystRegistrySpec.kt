/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.peekandpoke.klang.audio_be.cylinders.Cylinder
import io.peekandpoke.klang.audio_be.cylinders.offerAndCommit
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers
import io.peekandpoke.klang.audio_bridge.Kat
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.kat
import io.peekandpoke.klang.audio_bridge.uniqueId

/**
 * The per-playback cylinder-chain registry: what `Cmd.RegisterKatalyst` writes into, and what a
 * cylinder will read from in step 2.
 *
 * The two properties that matter are the two the master registry has, for the same reasons: a fork
 * sees its parent's chains without the parent seeing the fork's (so a playback's chains die with
 * it), and an unknown name resolves to NULL rather than to some fallback chain, so "the register
 * command has not arrived yet" stays distinguishable from "this is the chain".
 *
 * And what it stores (KLANG-00468): a chain in its OPTIMIZED form, adjacent `eq` stages of numbers merged into one, so
 * every cylinder and the output build that form; a chain the pass changes nothing in as the very instance registered.
 * The expected chains are written out here from the registered chain's own sections (the rule's text), never through
 * the pass.
 */
class KatalystRegistrySpec : StringSpec({

    val chain = KatalystDsl.of(KatalystStageDsl.Reverb(wet = KatalystKnobDsl.Constant(0.3)))
    val other = KatalystDsl.of(KatalystStageDsl.Gain(gain = KatalystKnobDsl.Constant(1.4)))

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

    // ── What is stored: the optimized chain (KLANG-00468) ──────────────────────────────────────────────────────────

    /** A cabinet as the chain doors write it: three `eq` stages, each holding numbers only. */
    fun cabinet(): KatalystDsl = Kat {
        it.eq { e -> e.band(freq = 900.kat, db = 3.kat) }
            .lowpass(freq = 5000.kat) { f -> f.passes(2.0) }
            .highpass(freq = 80.kat)
    }

    /** The cabinet merged, written out: one `eq` stage holding every section of [written], in written order. */
    fun mergedByHand(written: KatalystDsl): KatalystDsl =
        KatalystDsl.of(KatalystStageDsl.Eq(sections = written.stages.flatMap { (it as KatalystStageDsl.Eq).sections }))

    "a registered chain is found in its optimized form" {
        val registry = KatalystRegistry()
        val written = cabinet()

        registry.register("cab", written)

        withClue("the hook: stored optimized") {
            registry.find("cab") shouldBe mergedByHand(written)
        }

        registry.optimizerFailures shouldBe 0
    }

    "a chain the pass changes nothing in is found as the instance that was registered" {
        val registry = KatalystRegistry()

        registry.register("reverb", chain)
        registry.register("classic", KatalystDsl.classic)

        registry.find("reverb") shouldBeSameInstanceAs chain
        registry.find("classic") shouldBeSameInstanceAs KatalystDsl.classic
        registry.optimizerFailures shouldBe 0
    }

    "the name is the written chain's: two patterns naming equal chains share one name and find one optimized chain" {
        // Each pattern builds its own instance; the frontend names a chain by its CONTENT before any pass
        // (`KatalystValue.Dsl.name`), and the backend stores the optimized form under that name.
        val fromOnePattern = cabinet()
        val fromAnother = cabinet()
        val registry = KatalystRegistry()

        fromAnother.uniqueId() shouldBe fromOnePattern.uniqueId()

        registry.register(fromOnePattern.uniqueId(), fromOnePattern)

        withClue("the hook: the other pattern's name finds the optimized chain") {
            registry.find(fromAnother.uniqueId()) shouldBe mergedByHand(fromAnother)
        }
    }

    "what a cylinder runs is the optimized chain: idle it installs it, sounding it fades it in" {
        val written = cabinet()

        fun cylinder(registry: KatalystRegistry) = Cylinder(id = 0, blockFrames = 128, sampleRate = 48000, katalysts = registry)

        fun Cylinder.eqEffects(): Int = currentChain.pipeline.count { it is KatalystEqEffect }

        // Idle: the request installs at once.
        val idle = cylinder(KatalystRegistry().also { it.register("cab", written) })

        idle.requestChain("cab")

        withClue("the hook: an idle cylinder runs one eq stage") {
            idle.eqEffects() shouldBe 1
            idle.currentChain.pipeline.size shouldBe 1
        }

        // Sounding: the swap fades the chain in, and the chain it fades in is the optimized one, from its first block
        // to the end of the fade.
        val sounding = cylinder(KatalystRegistry().also { it.register("cab", written) })
        val voice = VoiceTestHelpers.createSynthVoice()
        var blockStart = 0.0

        sounding.offerAndCommit(voice, blockStart)
        sounding.requestChain("cab")

        withClue("the swap began") { sounding.isFading shouldBe true }

        var blocks = 0

        while (sounding.isFading && blocks < 1000) {
            withClue("the hook: block $blocks of the fade runs one eq stage") { sounding.eqEffects() shouldBe 1 }

            sounding.offerAndCommit(voice, blockStart)
            sounding.mixBuffer.fill(0.25)
            sounding.pollPendingChain()
            sounding.processEffects()
            blockStart += 128
            blocks++
        }

        withClue("the fade ended") { sounding.isFading shouldBe false }
        withClue("the hook: after the fade") { sounding.eqEffects() shouldBe 1 }
    }
})
