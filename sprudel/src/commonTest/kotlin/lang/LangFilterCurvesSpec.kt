/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.FilterEnvDef
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceData
import io.peekandpoke.klang.sprudel.dslInterfaceTests

/**
 * The four filter curves doors, `lpfCurves`, `hpfCurves`, `bpfCurves` and `notchCurves(attack, decay, release)`
 * (phase 3 step 5b (c2), decision D3): the `adsrCurves` and `penvCurves` rule on each filter's cutoff envelope.
 * Door parity across the eight forms per door, then the rule's clauses, then the wire.
 */
class LangFilterCurvesSpec : StringSpec({

    /** One door: its name, the Kotlin forms, where its curves live on the voice data, and its wire definition. */
    class Door(
        val name: String,
        val filter: String,
        val pattern: (SprudelPattern, String?, String?, String?) -> SprudelPattern,
        val string: (String, String?, String?, String?) -> SprudelPattern,
        val mapper: (String?, String?, String?) -> PatternMapperFn,
        val chained: (PatternMapperFn, String?, String?, String?) -> PatternMapperFn,
        val curves: (SprudelVoiceData) -> Triple<AdsrCurve?, AdsrCurve?, AdsrCurve?>,
        val wire: (List<FilterDef>) -> FilterEnvDef?,
    )

    val doors = listOf(
        Door(
            "lpfCurves", "lpf",
            { p, a, d, r -> p.lpfCurves(a, d, r) }, { s, a, d, r -> s.lpfCurves(a, d, r) },
            { a, d, r -> lpfCurves(a, d, r) }, { m, a, d, r -> m.lpfCurves(a, d, r) },
            { Triple(it.lpAttackCurve, it.lpDecayCurve, it.lpReleaseCurve) },
            { fs -> fs.filterIsInstance<FilterDef.LowPass>().single().envelope },
        ),
        Door(
            "hpfCurves", "hpf",
            { p, a, d, r -> p.hpfCurves(a, d, r) }, { s, a, d, r -> s.hpfCurves(a, d, r) },
            { a, d, r -> hpfCurves(a, d, r) }, { m, a, d, r -> m.hpfCurves(a, d, r) },
            { Triple(it.hpAttackCurve, it.hpDecayCurve, it.hpReleaseCurve) },
            { fs -> fs.filterIsInstance<FilterDef.HighPass>().single().envelope },
        ),
        Door(
            "bpfCurves", "bpf",
            { p, a, d, r -> p.bpfCurves(a, d, r) }, { s, a, d, r -> s.bpfCurves(a, d, r) },
            { a, d, r -> bpfCurves(a, d, r) }, { m, a, d, r -> m.bpfCurves(a, d, r) },
            { Triple(it.bpAttackCurve, it.bpDecayCurve, it.bpReleaseCurve) },
            { fs -> fs.filterIsInstance<FilterDef.BandPass>().single().envelope },
        ),
        Door(
            "notchCurves", "notch",
            { p, a, d, r -> p.notchCurves(a, d, r) }, { s, a, d, r -> s.notchCurves(a, d, r) },
            { a, d, r -> notchCurves(a, d, r) }, { m, a, d, r -> m.notchCurves(a, d, r) },
            { Triple(it.nfAttackCurve, it.nfDecayCurve, it.nfReleaseCurve) },
            { fs -> fs.filterIsInstance<FilterDef.Notch>().single().envelope },
        ),
    )

    for (door in doors) {
        val n = door.name

        "$n dsl interface: sets the three stage curves, on both doors, in all eight forms" {
            val pat = "0 1"
            val args = """"linear", "square", "cube""""

            dslInterfaceTests(
                "pattern.$n(a, d, r)" to door.pattern(seq(pat), "linear", "square", "cube"),
                "script pattern.$n(a, d, r)" to SprudelPattern.compile("""seq("$pat").$n($args)"""),
                "string.$n(a, d, r)" to door.string(pat, "linear", "square", "cube"),
                "script string.$n(a, d, r)" to SprudelPattern.compile(""""$pat".$n($args)"""),
                "$n(a, d, r)" to seq(pat).apply(door.mapper("linear", "square", "cube")),
                "script $n(a, d, r)" to SprudelPattern.compile("""seq("$pat").apply($n($args))"""),
                "mapper.$n(a, d, r)" to seq(pat).apply(door.chained(gain(1), "linear", "square", "cube")),
                "script mapper.$n(a, d, r)" to SprudelPattern.compile("""seq("$pat").apply(gain(1).$n($args))"""),
            ) { _, events ->
                events.shouldNotBeEmpty()
                door.curves(events[0].data) shouldBe Triple(AdsrCurve.Linear, AdsrCurve.Square, AdsrCurve.Cube)
            }
        }

        "$n by name: each stage lands in its own slot" {
            val p = SprudelPattern.compile("""note("c").$n(release = "cube", attack = "linear", decay = "square")""")!!

            door.curves(p.queryArc(0.0, 1.0)[0].data) shouldBe Triple(AdsrCurve.Linear, AdsrCurve.Square, AdsrCurve.Cube)
        }

        "$n: an omitted stage keeps its current curve" {
            val p = door.pattern(door.pattern(note("c"), "linear", "linear", "linear"), null, "scurve", null)

            door.curves(p.queryArc(0.0, 1.0)[0].data) shouldBe Triple(AdsrCurve.Linear, AdsrCurve.SCurve, AdsrCurve.Linear)
        }

        "$n: an unknown name keeps the stage's current curve, it is not an error" {
            val p = door.pattern(door.pattern(note("c"), "cube", "cube", "cube"), "xyz", "square", "nope")

            door.curves(p.queryArc(0.0, 1.0)[0].data) shouldBe Triple(AdsrCurve.Cube, AdsrCurve.Square, AdsrCurve.Cube)
        }

        "$n: an unknown name on EVERY stage keeps every current curve" {
            val p = door.pattern(door.pattern(note("c"), "square", "scurve", "cube"), "xyz", "nope", "zzz")

            door.curves(p.queryArc(0.0, 1.0)[0].data) shouldBe Triple(AdsrCurve.Square, AdsrCurve.SCurve, AdsrCurve.Cube)
        }

        "$n: names are case-insensitive and take the catalogue's aliases" {
            val p = door.pattern(note("c"), "LIN", " Exp ", "sigmoid")

            door.curves(p.queryArc(0.0, 1.0)[0].data) shouldBe Triple(AdsrCurve.Linear, AdsrCurve.Exponential, AdsrCurve.SCurve)
        }

        "$n: each stage is patternable" {
            val p = door.pattern(note("c e"), null, "<linear cube>", null)

            p.queryArc(1.0, 2.0).map { door.curves(it.data).second } shouldBe listOf(AdsrCurve.Cube, AdsrCurve.Cube)
        }

        "$n: a bare call changes nothing and reinterprets nothing" {
            val events = door.pattern(seq("5 7"), null, null, null).queryArc(0.0, 1.0)

            events.map { door.curves(it.data) } shouldBe listOf(Triple(null, null, null), Triple(null, null, null))
            events.map { it.data.toVoiceData().filters.filters } shouldBe listOf(emptyList(), emptyList())
        }

        "$n: the curves reach the wire's FilterEnvDef when the filter has an envelope" {
            val p = SprudelPattern.compile("""note("c").${door.filter}(freq = 800, env = 12, decay = 0.2).$n("linear", "scurve", "square")""")!!
            val env = door.wire(p.queryArc(0.0, 1.0)[0].data.toVoiceData().filters.filters)

            assertSoftly {
                env?.attackCurve shouldBe AdsrCurve.Linear
                env?.decayCurve shouldBe AdsrCurve.SCurve
                env?.releaseCurve shouldBe AdsrCurve.Square
                env?.depth shouldBe 12.0
            }
        }

        "$n: a curve alone switches no envelope on" {
            val p = SprudelPattern.compile("""note("c").${door.filter}(800).$n("linear", "linear", "linear")""")!!
            val env = door.wire(p.queryArc(0.0, 1.0)[0].data.toVoiceData().filters.filters)

            withClue("the filter is built, its envelope is not") { env shouldBe null }
        }
    }

    "each door writes only its own filter's curves" {
        val data = note("c")
            .lpfCurves(attack = "linear").hpfCurves(attack = "square").bpfCurves(attack = "cube").notchCurves(attack = "scurve")
            .queryArc(0.0, 1.0)[0].data

        doors.map { it.curves(data).first } shouldBe listOf(AdsrCurve.Linear, AdsrCurve.Square, AdsrCurve.Cube, AdsrCurve.SCurve)
    }
})
