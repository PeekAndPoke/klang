/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.filters.ResonatorBank
import io.peekandpoke.klang.audio_be.filters.ResonatorConfig
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.BodyMaterials
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.VowelBands
import io.peekandpoke.klang.audio_bridge.constants.BANK_CROSSFADE_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET

/**
 * The resonator stage ([KatalystResonatorEffect], body and vowel, one class since engine tidy-up step 12 (a)): what a
 * change of the catalogue INDEX does to the sound at chain level, and the pool of two pairs every install draws from.
 * The per-kind lifecycle rows are [KatalystResonatorBodySpec] and [KatalystResonatorVowelSpec].
 */
class KatalystResonatorEffectSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    /**
     * Renders the classic chain over [blocks] blocks of a deterministic noise, the vowel slot set to [vowelAt] of the
     * block (the body off), and returns every output sample's raw bits, both channels.
     */
    fun renderVowels(blocks: Int, vowelAt: (Int) -> String): LongArray {
        val chain = KatalystChainBuilder.build(
            dsl = KatalystDsl.classic,
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            rings = SizedBuffers.forRings(sampleRate),
            reverbs = ReverbUnits(sampleRate),
        )
        val ctx = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))
        val out = LongArray(blocks * blockFrames * 2)
        var seed = 12345
        var lastName = ""
        var params: Map<String, Double> = emptyMap()

        for (b in 0 until blocks) {
            val name = vowelAt(b)

            // A new map only when the vowel changes, the way a live owner hands its state over.
            if (name != lastName) {
                params = mapOf("vowel.vowel" to VowelBands.indexOf(name), "vowel.wet" to 0.8)
                lastName = name
            }

            chain.applyParams(params)

            for (i in 0 until blockFrames) {
                seed = seed * 1103515245 + 12345
                ctx.mixBuffer.left[i] = ((seed ushr 8) and 0xFFFF) / 65536.0 - 0.5
                seed = seed * 1103515245 + 12345
                ctx.mixBuffer.right[i] = ((seed ushr 8) and 0xFFFF) / 65536.0 - 0.5
            }

            chain.process(ctx)

            for (i in 0 until blockFrames) {
                out[(b * blockFrames + i) * 2] = ctx.mixBuffer.left[i].toRawBits()
                out[(b * blockFrames + i) * 2 + 1] = ctx.mixBuffer.right[i].toRawBits()
            }
        }

        return out
    }

    /** How many raw-bits samples differ between two renders. */
    fun differing(a: LongArray, b: LongArray): Int = a.indices.count { a[it] != b[it] }

    "switching between vowel names that share a bank installs nothing: the output is the unswitched one, bit for bit" {
        // `bass:a`, `bass:ei` and `bass:au` are three names of one formant bank, and so are `alto:*` and
        // `countertenor:*`, and `soprano:ae` and `soprano:ä`. Three indices, equal rows: a switch among them must
        // neither crossfade nor restart a bank (captured against the twins before step 12 (a), which compared the
        // rows structurally).
        val pairs = listOf(
            listOf("bass:a", "bass:ei", "bass:au"),
            listOf("alto:e", "countertenor:e"),
            listOf("soprano:ae", "soprano:ä"),
        )

        for (names in pairs) {
            val steady = renderVowels(blocks = 40) { names[0] }
            val switched = renderVowels(blocks = 40) { b -> names[(b / 7) % names.size] }

            withClue("${names.joinToString(" / ")}: index ${names.map { VowelBands.indexOf(it) }}") {
                names.map { VowelBands.indexOf(it) }.toSet().size shouldBe names.size
                differing(switched, steady) shouldBe 0
            }
        }
    }

    "a switch to a different bank does change the output (the row above can see an install)" {
        val steady = renderVowels(blocks = 40) { "bass:a" }
        val switched = renderVowels(blocks = 40) { b -> if (b < 10) "bass:a" else "bass:e" }

        differing(switched, steady) shouldBeGreaterThan 0
    }

    "a chain declaring the vowel BEFORE the body runs both, and each accessor finds its own kind" {
        // The maintainer's "a" sung through "wood", in both orders. The classic chain declares the body first, so a
        // `chain.vowel` that took the last resonator of any kind would still pass there.
        val vowelStage = KatalystStageDsl.Vowel(
            vowel = IgnitorDsl.Param("vowel.vowel", SLOT_UNSET),
            wet = IgnitorDsl.Param("vowel.wet", 0.7),
        )
        val bodyStage = KatalystStageDsl.Body(
            material = IgnitorDsl.Param("body.material", SLOT_UNSET),
            wet = IgnitorDsl.Param("body.wet", 0.6),
        )

        for (order in listOf(listOf(vowelStage, bodyStage), listOf(bodyStage, vowelStage))) {
            val chain = KatalystChainBuilder.build(
                dsl = KatalystDsl(order),
                sampleRate = sampleRate,
                blockFrames = blockFrames,
                rings = SizedBuffers.forRings(sampleRate),
                reverbs = ReverbUnits(sampleRate),
            )

            chain.applyParams(
                mapOf("vowel.vowel" to VowelBands.indexOf("a"), "body.material" to BodyMaterials.indexOf("wood"))
            )

            withClue("declared ${order.map { it::class.simpleName }}") {
                val vowel = chain.vowel.shouldNotBeNull()
                val body = chain.body.shouldNotBeNull()

                vowel.kind shouldBe ResonatorKind.VOWEL
                body.kind shouldBe ResonatorKind.BODY
                vowel.isEngaged shouldBe true
                body.isEngaged shouldBe true
                vowel.installedTable shouldBeSameInstanceAs
                    ResonatorTables.at(kind = ResonatorKind.VOWEL, slotValue = VowelBands.indexOf("a"))
                body.installedTable shouldBeSameInstanceAs
                    ResonatorTables.at(kind = ResonatorKind.BODY, slotValue = BodyMaterials.indexOf("wood"))
            }
        }
    }

    // ── The pool: two pairs, built at the first install, an install never reaches a pair that sounds ────────────

    val fadeBlocks = (sampleRate * BANK_CROSSFADE_SECONDS).toInt() / blockFrames + 2

    fun body(name: String) = ResonatorTables.at(kind = ResonatorKind.BODY, slotValue = BodyMaterials.indexOf(name))

    fun blocks(fx: KatalystResonatorEffect, count: Int) {
        val ctx = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))

        repeat(count) {
            ctx.mixBuffer.left.fill(0.25)
            ctx.mixBuffer.right.fill(-0.25)
            fx.process(ctx)
        }
    }

    "the pool is built at the stage's first install, not with the stage" {
        val chain = KatalystChainBuilder.build(
            dsl = KatalystDsl.classic,
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            rings = SizedBuffers.forRings(sampleRate),
            reverbs = ReverbUnits(sampleRate),
        )

        // The classic chain declares both stages; a voice that asks for neither must not cost their banks.
        chain.applyParams(emptyMap())
        chain.body.shouldNotBeNull().hasPool shouldBe false
        chain.vowel.shouldNotBeNull().hasPool shouldBe false

        chain.applyParams(mapOf("body.material" to BodyMaterials.indexOf("wood")))
        chain.body.shouldNotBeNull().hasPool shouldBe true
        chain.vowel.shouldNotBeNull().hasPool shouldBe false
    }

    "installs alternate between the two pairs while one sounds, and a change mid-fade takes none" {
        val fx = KatalystResonatorEffect(kind = ResonatorKind.BODY, sampleRate = sampleRate.toDouble(), blockFrames = blockFrames)

        fx.lastInstalledPair shouldBe -1
        fx.installs shouldBe 0

        fx.configure(ResonatorConfig(table = body("wood"), mix = 0.6, floor = 0.4))
        withClue("the first install takes the first pair") {
            fx.installs shouldBe 1
            fx.lastInstalledPair shouldBe 0
        }

        blocks(fx, 3)
        fx.configure(ResonatorConfig(table = body("glass"), mix = 0.6, floor = 0.4))
        withClue("wood sounds on pair 0, so glass takes pair 1") {
            fx.installs shouldBe 2
            fx.lastInstalledPair shouldBe 1
        }

        // While wood fades out on pair 0 and glass fades in on pair 1, a third change must not take either.
        blocks(fx, 1)
        fx.configure(ResonatorConfig(table = body("brass"), mix = 0.6, floor = 0.4))
        withClue("parked, nothing installed") {
            fx.isParked shouldBe true
            fx.installs shouldBe 2
        }

        blocks(fx, fadeBlocks)
        withClue("the fade landed: brass installed from the parking, on the pair wood left") {
            fx.isParked shouldBe false
            fx.installs shouldBe 3
            fx.lastInstalledPair shouldBe 0
        }

        blocks(fx, fadeBlocks)
        fx.configure(ResonatorConfig(table = body("wood"), mix = 0.6, floor = 0.4))
        fx.installs shouldBe 4
        fx.lastInstalledPair shouldBe 1

        // Re-offering what is installed, every block, installs nothing.
        repeat(5) {
            fx.configure(ResonatorConfig(table = body("wood"), mix = 0.6, floor = 0.4))
            blocks(fx, 1)
        }
        fx.installs shouldBe 4

        // Off, landed: the swap holds no pair, and the next install takes the first one again.
        // (wood is still fading in, so the off parks, then fades to dry once wood has landed: two fades.)
        fx.configure(ResonatorConfig(table = null, mix = 0.6, floor = 0.4))
        blocks(fx, 2 * fadeBlocks)
        fx.isSounding shouldBe false
        fx.configure(ResonatorConfig(table = body("glass"), mix = 0.6, floor = 0.4))
        fx.installs shouldBe 5
        fx.lastInstalledPair shouldBe 0
    }

    "a table with more bands than the pool holds renders as a bank built new for it, bit for bit" {
        // A direct caller only (the engine's tables fit the capacity, `ResonatorTablesSpec`): the pooled bank grows
        // once and is then exactly the fresh one.
        val big = SpecResonatorTables.body((0 until 12).map { BodyMaterials.Mode(freq = 140.0 * (it + 1), db = -it.toDouble(), q = 6.0 + it) })
        val fx = KatalystResonatorEffect(kind = ResonatorKind.BODY, sampleRate = sampleRate.toDouble(), blockFrames = blockFrames)

        // The pool exists at the catalogue's capacity first, and has rung.
        fx.configure(ResonatorConfig(table = body("wood"), mix = 0.6, floor = 0.4))
        blocks(fx, 4)
        fx.reset()

        val left = ResonatorBank(capacity = 12, sampleRate = sampleRate.toDouble())
        val right = ResonatorBank(capacity = 12, sampleRate = sampleRate.toDouble())

        left.install(ResonatorConfig(table = big, mix = 0.7, floor = 0.3))
        right.install(ResonatorConfig(table = big, mix = 0.7, floor = 0.3))
        fx.configure(ResonatorConfig(table = big, mix = 0.7, floor = 0.3)) // fresh after the reset: installed at once

        val ctx = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))
        val wantL = DoubleArray(blockFrames)
        val wantR = DoubleArray(blockFrames)
        var seed = 777
        var differing = 0

        repeat(20) {
            for (i in 0 until blockFrames) {
                seed = seed * 1103515245 + 12345
                ctx.mixBuffer.left[i] = ((seed ushr 8) and 0xFFFF) / 65536.0 - 0.5
                ctx.mixBuffer.right[i] = -ctx.mixBuffer.left[i] * 0.5
                wantL[i] = ctx.mixBuffer.left[i]
                wantR[i] = ctx.mixBuffer.right[i]
            }

            fx.process(ctx)
            left.process(buffer = wantL, offset = 0, length = blockFrames)
            right.process(buffer = wantR, offset = 0, length = blockFrames)

            for (i in 0 until blockFrames) {
                if (ctx.mixBuffer.left[i].toRawBits() != wantL[i].toRawBits() || ctx.mixBuffer.right[i].toRawBits() != wantR[i].toRawBits()) {
                    differing++
                }
            }
        }

        differing shouldBe 0
    }
})
