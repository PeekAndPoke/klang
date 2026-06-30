/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang.addons

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests
import io.peekandpoke.klang.sprudel.lang.apply
import io.peekandpoke.klang.sprudel.lang.seq

class LangDutySpec : StringSpec({

    "duty dsl interface" {
        val pat = "0 1"
        val ctrl = "0.25 0.75"

        dslInterfaceTests(
            "pattern.duty(ctrl)" to seq(pat).duty(ctrl),
            "script pattern.duty(ctrl)" to SprudelPattern.compile("""seq("$pat").duty("$ctrl")"""),
            "string.duty(ctrl)" to pat.duty(ctrl),
            "script string.duty(ctrl)" to SprudelPattern.compile(""""$pat".duty("$ctrl")"""),
            "duty(ctrl)" to seq(pat).apply(duty(ctrl)),
            "script duty(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(duty("$ctrl"))"""),
            // form (d): the PatternMapperFn.duty chained-mapper overload
            "chained duty(ctrl)" to seq(pat).apply(duty(ctrl).duty(ctrl)),
            "script chained duty(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(duty("$ctrl").duty("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.oscParams?.get("duty") shouldBe 0.25
            events[1].data.oscParams?.get("duty") shouldBe 0.75
        }
    }

    "duty() with no args sets nothing but is callable" {
        val events = seq("0 1").duty().queryArc(0.0, 1.0)
        events.size shouldBe 2
    }
})
