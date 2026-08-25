/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.FILTER_MAX_PASSES
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.sprudel.SprudelPattern

/**
 * C5 sprudel-door pin: `passes` reaches the wire from the head's third slot AND the
 * `lpx`/`hpx` tails, on both doors; absent = 1 (bit-identical single stage); non-positive
 * values coerce to 1 at the wire boundary.
 */
class LangPassesSpec : StringSpec({

    fun firstLp(p: SprudelPattern?): FilterDef.LowPass =
        (p ?: error("no pattern")).queryArc(0.0, 1.0).first().data.toVoiceData()
            .filters.filters.filterIsInstance<FilterDef.LowPass>().first()

    fun firstHp(p: SprudelPattern?): FilterDef.HighPass =
        (p ?: error("no pattern")).queryArc(0.0, 1.0).first().data.toVoiceData()
            .filters.filters.filterIsInstance<FilterDef.HighPass>().first()

    "head slot 3: lpf(freq, q, passes) reaches the wire on both doors" {
        firstLp(note("c").lpf(800, 1.0, 2)).passes shouldBe 2
        firstLp(SprudelPattern.compile("""note("c").lpf(800, 1.0, 2)""")).passes shouldBe 2
        firstHp(note("c").hpf(200, 1.0, 3)).passes shouldBe 3
        firstHp(SprudelPattern.compile("""note("c").hpf(200, 1.0, 3)""")).passes shouldBe 3
    }

    "tails: lpx/hpx set the count without touching the head fields" {
        val lp = firstLp(note("c").lpf(800).lpx(2))
        lp.passes shouldBe 2
        lp.freq shouldBe 800.0
        val viaScript = firstHp(SprudelPattern.compile("""note("c").hpf(200).hpx(2)"""))
        viaScript.passes shouldBe 2
    }

    "absent = 1 (the untouched single-stage path)" {
        firstLp(note("c").lpf(800)).passes shouldBe 1
        firstHp(note("c").hpf(200)).passes shouldBe 1
    }

    "non-positive counts coerce to 1 at the wire boundary — no throw" {
        firstLp(note("c").lpf(800).lpx(0)).passes shouldBe 1
        firstLp(note("c").lpf(800).lpx(-3)).passes shouldBe 1
    }

    "an absurd count coerces to the resource ceiling — no throw, no billion-stage allocation" {
        firstLp(note("c").lpf(800).lpx(1_000_000)).passes shouldBe FILTER_MAX_PASSES
        firstHp(note("c").hpf(200).hpx(1e9)).passes shouldBe FILTER_MAX_PASSES
    }

    "fractional counts ROUND, they do not truncate (and NaN falls back to 1)" {
        // Sprudel carries every control value as a Double and pattern arithmetic lands on
        // things like 2.9999999996; truncating there silently drops a cascade stage.
        firstLp(note("c").lpf(800).lpx(2.7)).passes shouldBe 3
        firstLp(note("c").lpf(800).lpx(2.4)).passes shouldBe 2
        firstLp(note("c").lpf(800).lpx(2.9999999996)).passes shouldBe 3
        // roundToInt() THROWS on NaN, and this runs on the render thread.
        firstHp(note("c").hpf(200).hpx(Double.NaN)).passes shouldBe 1
    }

    "a tail-only head call must NOT reinterpret the pattern's values as cutoff" {
        // The head condition is `freq != null || (q == null && passes == null)`. Relaxing it
        // to `(q == null)` makes `lpf(passes = 2)` run the bare-call reinterpret, and a numeric
        // pattern silently acquires a cutoff nobody asked for.
        val p = seq("400 800").lpf(passes = 2)
        val vd = (p ?: error("no pattern")).queryArc(0.0, 1.0).first().data.toVoiceData()
        vd.filters.filters.filterIsInstance<FilterDef.LowPass>().isEmpty() shouldBe true
        val hp = seq("400 800").hpf(passes = 2)
        (hp ?: error("no pattern")).queryArc(0.0, 1.0).first().data.toVoiceData()
            .filters.filters.filterIsInstance<FilterDef.HighPass>().isEmpty() shouldBe true
    }

    "mapper forms (c) and (d): the standalone and the CHAINED mapper both carry passes" {
        // Form (d) — the chained mapper — is the project-wide coverage gap; these eight
        // functions are brand new, so it gets pinned here rather than inherited.
        firstLp(note("c").apply(lpf(800, 1.0, 2))).passes shouldBe 2
        firstLp(note("c").apply(lpf(800).lpx(3))).passes shouldBe 3
        firstHp(note("c").apply(hpf(200, 1.0, 2))).passes shouldBe 2
        firstHp(note("c").apply(hpf(200).hpx(3))).passes shouldBe 3
    }
})
