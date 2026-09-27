/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.soundName

class LangOnepoleSpec : StringSpec({

    "onepole() default is null when not set" {
        val p = s("supersaw")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.oscParams?.get("onepole") shouldBe null
    }

    "apply(mul().onepole())" {
        val p = seq("2000 4000").apply(mul("2").onepole())
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.oscParams?.get("onepole") shouldBe (4000.0 plusOrMinus EPSILON)  // 2000*2
            events[1].data.oscParams?.get("onepole") shouldBe (8000.0 plusOrMinus EPSILON)  // 4000*2
        }
    }

    "script apply(mul().onepole())" {
        val p = SprudelPattern.compile("""seq("2000 4000").apply(mul("2").onepole())""")!!
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.oscParams?.get("onepole") shouldBe (4000.0 plusOrMinus EPSILON)  // 2000*2
            events[1].data.oscParams?.get("onepole") shouldBe (8000.0 plusOrMinus EPSILON)  // 4000*2
        }
    }

    "onepole() can be applied to different oscillators" {
        val p = s("sine triangle square").onepole("17814 3700 4916")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events[0].data.soundName shouldBe "sine"
        events[0].data.oscParams?.get("onepole") shouldBe 17814.0
        events[1].data.soundName shouldBe "triangle"
        events[1].data.oscParams?.get("onepole") shouldBe 3700.0
        events[2].data.soundName shouldBe "square"
        events[2].data.oscParams?.get("onepole") shouldBe 4916.0
    }
})
