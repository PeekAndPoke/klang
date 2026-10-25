/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.attr
import io.peekandpoke.kraft.testing.classes
import io.peekandpoke.kraft.testing.selectCss
import io.peekandpoke.kraft.testing.textContent
import kotlinx.css.px
import org.w3c.dom.HTMLElement

/** The lamp (KLANG-00337): one class per state, hidden from screen readers, its label printed beside it. */
class KlangLampSpec : StringSpec({

    val styles = KlangControlStyles

    "each state has its class; off has only the lamp's own" {
        val expected = mapOf(
            KlangLamp.State.Off to null,
            KlangLamp.State.On to styles.lampOn,
            KlangLamp.State.Try to styles.lampTry,
            KlangLamp.State.Warning to styles.lampWarning,
            KlangLamp.State.Critical to styles.lampCritical,
        )

        for ((state, cls) in expected) {
            TestBed.preact(view = { KlangLamp(state = state) }) { root ->
                val lamp = root.selectCss("span[data-lamp]")

                lamp.classes().single() shouldBe setOfNotNull(styles.lamp.toString(), cls?.toString())
                lamp.attr("aria-hidden") shouldContainExactly listOf("true")
            }
        }
    }

    "the size sets width and height" {
        TestBed.preact(view = { KlangLamp(state = KlangLamp.State.On, size = 12.px) }) { root ->
            val lamp = root.selectCss<HTMLElement>("span[data-lamp]").single()

            lamp.style.width shouldBe "12px"
            lamp.style.height shouldBe "12px"
        }
    }

    "a label is printed beside the lamp; without one there is only the lamp" {
        TestBed.preact(
            view = {
                KlangLamp(state = KlangLamp.State.On, label = "Live")
                KlangLamp(state = KlangLamp.State.Off)
            },
        ) { root ->
            root.textContent() shouldBe "Live"
            root.selectCss("span[data-lamp]").attr("data-lamp") shouldContainExactly listOf("On", "Off")
        }
    }
})
