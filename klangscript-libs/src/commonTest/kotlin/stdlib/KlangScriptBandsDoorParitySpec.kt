/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystBuilder
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.bands
import io.peekandpoke.klang.audio_bridge.ign
import io.peekandpoke.klang.audio_bridge.kat
import io.peekandpoke.klang.audio_bridge.mul
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull
import kotlin.math.sqrt

/**
 * `bands(...)` on both DSLs, both doors (`docs/tasks-archive/2026-10/20261010-parallel-serial-bands.md` step 4): the script door and
 * the Kotlin door build the same value, and the shape of the split: no cut is the one band in place, one `band()` is
 * its processor, two on one band are summed, an untouched band is only its crossover, and a broken processor is named.
 * What the split SOUNDS like (flat when untouched, a band acting on its range) is `BandsCrossoverSpec`.
 *
 * Since the typed-doors rollout S6 both doors share the builders' members (`audio_bridge/BandsBuilders.kt`), so a
 * parity row cannot see a crossover that writes the wrong cut or hands a processor the wrong band: the split is also
 * pinned against the nodes WRITTEN OUT by hand (cut frequencies, band order, processors), from both doors.
 */
class KlangScriptBandsDoorParitySpec : StringSpec({

    fun ignitor(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        return engine.execute(code).toObjectOrNull<IgnitorDsl>()!!
    }

    fun katalyst(code: String): KatalystDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        return engine.execute(code).toObjectOrNull<KatalystDsl>()!!
    }

    fun errorOf(block: () -> Unit): String = shouldThrowAny { block() }.message ?: ""

    val empty = KatalystBuilder(KatalystDsl(emptyList()))

    fun c(v: Double) = IgnitorDsl.Constant(v)
    fun k(v: Double) = KatalystKnobDsl.Constant(v)

    // The crossover's sections, written out: a Linkwitz-Riley side is two Butterworth sections (Q 1/sqrt(2)), the
    // all-pass one raw tap at gain -2.
    val q = c(1.0 / sqrt(2.0))

    fun lp(f: Double) = IgnitorDsl.EqSection.Lowpass(freq = c(f), q = q)
    fun hp(f: Double) = IgnitorDsl.EqSection.Highpass(freq = c(f), q = q)
    fun ap(f: Double) = IgnitorDsl.EqSection.RawTap(freq = c(f), q = q, gain = c(-2.0))

    // ── Ignitor ──────────────────────────────────────────────────────────────────────────────────

    "Ignitor: the Kotlin door builds the same split as the script door" {
        val saw = ignitor("Ignitor.saw()")
        val script = ignitor("Ignitor.saw().bands(b => b.band(x => x.mul(2)).cut(200).cut(2000).band(x => x.mul(3)))")
        val kotlin = saw.bands {
            it.band { x -> x.mul(2.0.ign) }.cut(200.0).cut(2000.0).band { x -> x.mul(3.0.ign) }
        }

        kotlin shouldBe script
    }

    "Ignitor: no configure, or no cut, is the one band: the signal, or its processor" {
        val saw = ignitor("Ignitor.saw()")

        ignitor("Ignitor.saw().bands()") shouldBe saw
        ignitor("Ignitor.saw().bands(b => b.band(x => x.mul(2)))") shouldBe ignitor("Ignitor.saw().mul(2)")
        // The default node, from the Kotlin door: no configure, and a configure that writes nothing, is the signal.
        saw.bands() shouldBe saw
        saw.bands { it } shouldBe saw
        saw.bands { it.band { x -> x.mul(2.0.ign) } } shouldBe IgnitorDsl.Times(left = saw, right = c(2.0))
    }

    "Ignitor: the split written out: the cuts, the bands in order, each processor on its own band, from both doors" {
        val saw = ignitor("Ignitor.saw()")
        // Band 1 is the low side of 200 Hz, aligned by the 2000 Hz all-pass; the high side of 200 Hz is ONE node, split
        // again at 2000 Hz into band 2 (its low side) and band 3 (its high side). Each processor reads its band behind
        // an empty EQ of its own.
        val high200 = IgnitorDsl.Eq(inner = saw, sections = listOf(hp(200.0), hp(200.0)))
        val low = IgnitorDsl.Eq(
            inner = IgnitorDsl.Eq(inner = IgnitorDsl.Eq(inner = saw, sections = listOf(lp(200.0), lp(200.0))), sections = listOf(ap(2000.0))),
        )
        val mid = IgnitorDsl.Eq(inner = IgnitorDsl.Eq(inner = high200, sections = listOf(lp(2000.0), lp(2000.0))))
        val high = IgnitorDsl.Eq(inner = IgnitorDsl.Eq(inner = high200, sections = listOf(hp(2000.0), hp(2000.0))))
        val expected = IgnitorDsl.Parallel(
            branches = listOf(
                IgnitorDsl.Times(left = low, right = c(2.0)),
                IgnitorDsl.Times(left = mid, right = c(3.0)),
                IgnitorDsl.Times(left = high, right = c(4.0)),
            ),
        )

        saw.bands {
            it.band { x -> x.mul(2.0.ign) }.cut(200.0).band { x -> x.mul(3.0.ign) }.cut(2000.0).band { x -> x.mul(4.0.ign) }
        } shouldBe expected
        ignitor("Ignitor.saw().bands(b => b.band(x => x.mul(2)).cut(200).band(x => x.mul(3)).cut(2000).band(x => x.mul(4)))") shouldBe
                expected
    }

    "Ignitor: the members written out: a cut alone is its two sides, two band() calls on one band are summed" {
        val saw = ignitor("Ignitor.saw()")
        val sides = IgnitorDsl.Parallel(
            branches = listOf(
                IgnitorDsl.Eq(inner = saw, sections = listOf(lp(300.0), lp(300.0))),
                IgnitorDsl.Eq(inner = saw, sections = listOf(hp(300.0), hp(300.0))),
            ),
        )
        val top = IgnitorDsl.Eq(inner = IgnitorDsl.Eq(inner = saw, sections = listOf(hp(300.0), hp(300.0))))
        val summed = IgnitorDsl.Parallel(
            branches = listOf(
                IgnitorDsl.Eq(inner = saw, sections = listOf(lp(300.0), lp(300.0))),
                IgnitorDsl.Parallel(
                    branches = listOf(IgnitorDsl.Times(left = top, right = c(3.0)), IgnitorDsl.Times(left = top, right = c(5.0))),
                ),
            ),
        )

        saw.bands { it.cut(300.0) } shouldBe sides
        ignitor("Ignitor.saw().bands(b => b.cut(300))") shouldBe sides
        saw.bands { it.cut(300.0).band { x -> x.mul(3.0.ign) }.band { x -> x.mul(5.0.ign) } } shouldBe summed
        ignitor("Ignitor.saw().bands(b => b.cut(300).band(x => x.mul(3)).band(x => x.mul(5)))") shouldBe summed
        // A falling cut is coerced up to the one before it, on both doors.
        saw.bands { it.cut(300.0).cut(100.0) } shouldBe saw.bands { it.cut(300.0).cut(300.0) }
        ignitor("Ignitor.saw().bands(b => b.cut(300).cut(100))") shouldBe saw.bands { it.cut(300.0).cut(300.0) }
    }

    "Ignitor: three bands are one Parallel of three; two processors on one band are summed" {
        val split = ignitor("Ignitor.saw().bands(b => b.cut(200).band(x => x.mul(2)).band(x => x.mul(3)).cut(2000))")
            .shouldBeInstanceOf<IgnitorDsl.Parallel>()

        split.branches.size shouldBe 3
        split.branches[1].shouldBeInstanceOf<IgnitorDsl.Parallel>().branches.size shouldBe 2
    }

    "Ignitor: the bands share the split tree by identity: a high side is one node for the band and the split above it" {
        val split = ignitor("Ignitor.saw().bands(b => b.cut(200).cut(2000))").shouldBeInstanceOf<IgnitorDsl.Parallel>()
        val middle = split.branches[1].shouldBeInstanceOf<IgnitorDsl.Eq>()
        val top = split.branches[2].shouldBeInstanceOf<IgnitorDsl.Eq>()

        (middle.inner === top.inner) shouldBe true
    }

    // ── Katalyst ─────────────────────────────────────────────────────────────────────────────────

    "Katalyst: the Kotlin door builds the same chain as the script door" {
        val script = katalyst("Katalyst(k => k.bands(b => b.cut(150).band(m => m.distort(0.15)).cut(5000)).gain(1.2))")
        val kotlin = empty.bands { it.cut(150.0).band { m -> m.distort(amount = 0.15.kat) }.cut(5000.0) }.gain(1.2.kat).node

        kotlin shouldBe script
    }

    "Katalyst: no configure, or no cut, is the one band, its stages in place" {
        katalyst("Katalyst(k => k.gain(1.2).bands())") shouldBe katalyst("Katalyst(k => k.gain(1.2))")
        katalyst("Katalyst(k => k.bands(b => b.band(m => m.gain(0.5))))") shouldBe katalyst("Katalyst(k => k.gain(0.5))")
        // The default node, from the Kotlin door.
        empty.gain(1.2.kat).bands() shouldBe empty.gain(1.2.kat)
        empty.bands { it }.node shouldBe KatalystDsl(emptyList())
        empty.bands { it.band { m -> m.gain(0.5.kat) } }.node shouldBe KatalystDsl.of(KatalystStageDsl.Gain(gain = k(0.5)))
    }

    "Katalyst: the split written out: the cuts, the bands in order, each processor on its own band, from both doors" {
        val expected = KatalystDsl.of(
            KatalystStageDsl.Gain(gain = k(1.5)),
            KatalystStageDsl.Parallel(
                branches = listOf(
                    KatalystDsl.of(
                        KatalystStageDsl.Eq(sections = listOf(lp(150.0), lp(150.0))),
                        KatalystStageDsl.Eq(sections = listOf(ap(5000.0))),
                        KatalystStageDsl.Gain(gain = k(2.0)),
                    ),
                    KatalystDsl.of(
                        KatalystStageDsl.Eq(sections = listOf(hp(150.0), hp(150.0), lp(5000.0), lp(5000.0))),
                        KatalystStageDsl.Gain(gain = k(3.0)),
                    ),
                    KatalystDsl.of(
                        KatalystStageDsl.Eq(sections = listOf(hp(150.0), hp(150.0), hp(5000.0), hp(5000.0))),
                        KatalystStageDsl.Gain(gain = k(4.0)),
                    ),
                ),
            ),
        )

        empty.gain(1.5.kat).bands {
            it.band { m -> m.gain(2.0.kat) }.cut(150.0).band { m -> m.gain(3.0.kat) }.cut(5000.0).band { m -> m.gain(4.0.kat) }
        }.node shouldBe expected
        katalyst(
            "Katalyst(k => k.gain(1.5).bands(b => b.band(m => m.gain(2)).cut(150).band(m => m.gain(3)).cut(5000).band(m => m.gain(4))))",
        ) shouldBe expected
    }

    "Katalyst: the members written out: a cut alone is its two sides, two band() calls on one band are summed" {
        val summed = KatalystDsl.of(
            KatalystStageDsl.Parallel(
                branches = listOf(
                    KatalystDsl.of(KatalystStageDsl.Eq(sections = listOf(lp(300.0), lp(300.0)))),
                    KatalystDsl.of(
                        KatalystStageDsl.Eq(sections = listOf(hp(300.0), hp(300.0))),
                        KatalystStageDsl.Parallel(
                            branches = listOf(
                                KatalystDsl.of(KatalystStageDsl.Gain(gain = k(3.0))),
                                KatalystDsl.of(KatalystStageDsl.Gain(gain = k(5.0))),
                            ),
                        ),
                    ),
                ),
            ),
        )

        empty.bands { it.cut(300.0).band { m -> m.gain(3.0.kat) }.band { m -> m.gain(5.0.kat) } }.node shouldBe summed
        katalyst("Katalyst(k => k.bands(b => b.cut(300).band(m => m.gain(3)).band(m => m.gain(5))))") shouldBe summed
        empty.bands { it.cut(300.0).cut(100.0) } shouldBe empty.bands { it.cut(300.0).cut(300.0) }
    }

    "Katalyst: an untouched band is its crossover only; the low band of three carries the upper cut's all-pass" {
        val stage = katalyst("Katalyst(k => k.bands(b => b.cut(200).cut(2000)))").stages.single()
            .shouldBeInstanceOf<KatalystStageDsl.Parallel>()
        val low = stage.branches[0].stages
        val high = stage.branches[2].stages

        low.size shouldBe 2
        low[0].shouldBeInstanceOf<KatalystStageDsl.Eq>().sections.size shouldBe 2
        // The upper cut's all-pass: one raw tap at gain -2, in an EQ of its own (a tap reads its EQ's input).
        low[1].shouldBeInstanceOf<KatalystStageDsl.Eq>().sections.single()
            .shouldBeInstanceOf<IgnitorDsl.EqSection.RawTap>().gain shouldBe IgnitorDsl.Constant(-2.0)
        high.single().shouldBeInstanceOf<KatalystStageDsl.Eq>().sections.size shouldBe 4
    }

    // ── A broken processor is named ─────────────────────────────────────────────────────────────

    "a processor that returns nothing or something else is named with its band, on both DSLs" {
        errorOf { ignitor("Ignitor.saw().bands(b => b.cut(200).band(x => { x.mul(2) }))") } shouldContain
                "processor 1 of band 2 of Ignitor bands returned nothing"
        errorOf { katalyst("Katalyst(k => k.bands(b => b.band(m => 3)))") } shouldContain
                "processor 1 of band 1 of Katalyst bands must return builder"
    }

    "the band and the processor numbers are the ones written, the check wrapped when the script calls band()" {
        errorOf { ignitor("Ignitor.saw().bands(b => b.cut(200).cut(2000).band(x => x).band(x => { x.mul(2) }))") } shouldContain
                "processor 2 of band 3 of Ignitor bands returned nothing"
        // A processor that is not a function is refused at the call, as before the wrapping.
        errorOf { ignitor("Ignitor.saw().bands(b => b.cut(200).band(null))") } shouldContain "expected a function, got null"
        errorOf { katalyst("Katalyst(k => k.bands(b => b.cut(150).band(m => m).band(m => 3)))") } shouldContain
                "processor 2 of band 2 of Katalyst bands must return builder"
        errorOf { katalyst("Katalyst(k => k.bands(b => b.band(m => m).cut(150).band(m => { m.gain(2) })))") } shouldContain
                "processor 1 of band 2 of Katalyst bands returned nothing"
    }
})
