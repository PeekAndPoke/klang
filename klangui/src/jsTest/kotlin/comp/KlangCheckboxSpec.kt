/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.eventually
import io.peekandpoke.kraft.testing.selectCss
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.html.Tag
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement

/** A checkbox held by its parent, as on the start page. */
private class HeldCheckbox(ctx: NoProps) : PureComponent(ctx) {
    private var on: Boolean by value(true)

    override fun VDom.render() {
        KlangCheckbox(label = "Enter fullscreen", checked = on, onChange = { on = it })
    }
}

@Suppress("FunctionName")
private fun Tag.HeldCheckbox() = comp { HeldCheckbox(it) }

/** The checkbox (KLANG-00349): a real checkbox input named by its label, controlled by its owner. */
class KlangCheckboxSpec : StringSpec({

    "a checkbox input, named by its label, showing what the props say" {
        TestBed.preact(view = { KlangCheckbox(label = "Enter fullscreen", checked = true, onChange = {}) }) { root ->
            val input = root.selectCss("input").single() as HTMLInputElement

            input.type shouldBe "checkbox"
            input.checked shouldBe true
            // The label wraps the input: a click on its text toggles it, a screen reader names it by it
            (input.closest("label") as HTMLElement).textContent shouldBe "Enter fullscreen"
        }
    }

    "a click hands the other value to onChange; the box keeps the props' value until its owner changes them" {
        val handed = mutableListOf<Boolean>()

        TestBed.preact(view = { KlangCheckbox(label = "x", checked = true, onChange = { handed.add(it) }) }) { root ->
            val input = root.selectCss("input").single() as HTMLInputElement

            input.click()

            handed shouldContainExactly listOf(false)
            input.checked shouldBe true
        }
    }

    "held by its owner: a click on the label turns it off, a second on" {
        TestBed.preact(view = { HeldCheckbox() }) { root ->
            val input = root.selectCss("input").single() as HTMLInputElement
            val label = input.closest("label") as HTMLElement

            label.click()

            eventually { input.checked shouldBe false }

            label.click()

            eventually { input.checked shouldBe true }
        }
    }
})
