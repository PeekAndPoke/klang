/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.constants.RANGEX_FLOOR
import io.peekandpoke.klang.audio_bridge.range
import io.peekandpoke.klang.audio_bridge.rangex
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull

/**
 * The Ignitor `range` on both doors, with its values named `from` and `to` as on every range door
 * (`docs/tasks-archive/2026-10/20261005-sprudel-signals-range-cleanup.md`, maintainer 2026-10-05): positional, named in either order and the
 * Kotlin door build the same node, and `from` is the value at the low end of the swing. The same for `rangex`, the
 * exponential twin, which is composed of existing nodes (decision 16).
 */
class KlangScriptIgnitorRangeDoorParitySpec : StringSpec({

    fun ignitor(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        return engine.execute(code).toObjectOrNull<IgnitorDsl>()!!
    }

    fun c(v: Double) = IgnitorDsl.Constant(v)

    "range takes from and to by name on the script door, the same node as positional and the Kotlin door" {
        val sine = ignitor("Ign.sine(2)")
        val expected = IgnitorDsl.Range(inner = sine, from = c(0.25), to = c(1.0))

        ignitor("Ign.sine(2).range(0.25, 1)") shouldBe expected
        ignitor("Ign.sine(2).range(from = 0.25, to = 1)") shouldBe expected
        ignitor("Ign.sine(2).range(to = 1, from = 0.25)") shouldBe expected
        ignitor("Ignitor.sine(2).range(from = 0.25, to = 1)") shouldBe expected
        sine.range(from = c(0.25), to = c(1.0)) shouldBe expected
    }

    "from is the low end of the swing and to the high end, so swapping them turns the swing upside down" {
        val sine = ignitor("Ign.sine(2)")
        val upsideDown = IgnitorDsl.Range(inner = sine, from = c(1.0), to = c(0.25))

        ignitor("Ign.sine(2).range(from = 1, to = 0.25)") shouldBe upsideDown
        sine.range(from = c(1.0), to = c(0.25)) shouldBe upsideDown
        upsideDown shouldNotBe ignitor("Ign.sine(2).range(from = 0.25, to = 1)")
    }

    "range takes signals as its values, by name too" {
        val expected = IgnitorDsl.Range(
            inner = ignitor("Ign.sine(2)"),
            from = ignitor("Ign.saw(0.5)"),
            to = c(400.0),
        )

        ignitor("Ign.sine(2).range(from = Ign.saw(0.5), to = 400)") shouldBe expected
    }

    // ── rangex: the exponential twin, composed ──────────────────────────────────────────────────────────

    /** The tree rangex is composed of, written out by hand: exp of a range between the logs of the floored values. */
    fun composed(inner: IgnitorDsl, from: IgnitorDsl, to: IgnitorDsl): IgnitorDsl {
        val floor = c(RANGEX_FLOOR)

        return IgnitorDsl.Exp(
            inner = IgnitorDsl.Range(
                inner = inner,
                from = IgnitorDsl.Log(inner = IgnitorDsl.Max(left = from, right = floor)),
                to = IgnitorDsl.Log(inner = IgnitorDsl.Max(left = to, right = floor)),
            ),
        )
    }

    "rangex: the script door, named or positional, and the Kotlin door build the same composed tree" {
        val sine = ignitor("Ign.sine(0.2)")
        val expected = composed(sine, c(200.0), c(3200.0))

        ignitor("Ign.sine(0.2).rangex(200, 3200)") shouldBe expected
        ignitor("Ign.sine(0.2).rangex(from = 200, to = 3200)") shouldBe expected
        ignitor("Ign.sine(0.2).rangex(to = 3200, from = 200)") shouldBe expected
        ignitor("Ignitor.sine(0.2).rangex(200, 3200)") shouldBe expected
        sine.rangex(from = c(200.0), to = c(3200.0)) shouldBe expected
    }

    "rangex: the floor is the mathematical Max node, never the clamp door's Min" {
        val tree = ignitor("Ign.sine(0.2).rangex(0, 1000)") as IgnitorDsl.Exp
        val range = tree.inner as IgnitorDsl.Range
        val logFrom = range.from as IgnitorDsl.Log

        logFrom.inner shouldBe IgnitorDsl.Max(left = c(0.0), right = c(RANGEX_FLOOR))
        RANGEX_FLOOR shouldBe 0.0001
    }

    "rangex: swapped values turn the swing upside down, and signals are values too" {
        val sine = ignitor("Ign.sine(0.2)")

        ignitor("Ign.sine(0.2).rangex(from = 3200, to = 200)") shouldBe composed(sine, c(3200.0), c(200.0))
        ignitor("Ign.sine(0.2).rangex(from = 3200, to = 200)") shouldNotBe ignitor("Ign.sine(0.2).rangex(200, 3200)")
        ignitor("Ign.sine(0.2).rangex(from = Ign.saw(0.5).range(100, 200), to = 3200)") shouldBe
            composed(sine, ignitor("Ign.saw(0.5).range(100, 200)"), c(3200.0))
    }
})
