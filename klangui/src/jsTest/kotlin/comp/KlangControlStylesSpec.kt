/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.eventually
import io.peekandpoke.kraft.testing.selectCss
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.css.CSSMediaRule
import org.w3c.dom.css.CSSStyleRule
import org.w3c.dom.css.CSSStyleSheet

/**
 * The beacon's ring and the "reduce motion" rules (KLANG-00337). The reduce-motion rules live in a sheet that mounts
 * before the main one, so they win only by carrying more classes than every rule they still: a media query adds no
 * weight of its own (review round 1).
 */
class KlangControlStylesSpec : StringSpec({

    val styles = KlangControlStyles

    /** A style rule as the browser applies it: its full selector, its animation, and whether reduce motion holds it. */
    data class Rule(val selector: String, val animation: String, val inReducedMotion: Boolean)

    /**
     * Every style rule of every sheet. A nested rule (`&::after` inside `.beacon`, CSS nesting as kotlinx-css writes
     * it) gets its parent's selector in place of `&`, which is also how the browser weighs it.
     */
    fun styleRules(): List<Rule> {
        val found = mutableListOf<Rule>()

        fun collect(rule: Any?, parent: String?, inReducedMotion: Boolean) {
            if (rule is CSSStyleRule) {
                val own = rule.selectorText
                val selector = if (parent == null) own else own.replace("&", parent)

                found.add(Rule(selector, rule.style.getPropertyValue("animation-name"), inReducedMotion))

                val nested = rule.asDynamic().cssRules

                if (nested != null) {
                    for (k in 0 until (nested.length as Int)) {
                        collect(nested.item(k) as Any?, selector, inReducedMotion)
                    }
                }
            }

            if (rule is CSSMediaRule) {
                val reduced = inReducedMotion || "prefers-reduced-motion" in rule.media.mediaText

                for (k in 0 until rule.cssRules.length) {
                    collect(rule.cssRules.item(k), parent, reduced)
                }
            }
        }

        val sheets = document.styleSheets

        for (i in 0 until sheets.length) {
            val sheet = sheets.item(i) as? CSSStyleSheet ?: continue

            for (j in 0 until sheet.cssRules.length) {
                collect(sheet.cssRules.item(j), null, false)
            }
        }

        return found
    }

    fun classCount(selector: String): Int = selector.count { it == '.' }

    /** The rule that stills [target] outweighs every rule that animates it outside reduce motion. */
    fun stillingOutweighsAnimating(target: String) {
        val rules = styleRules().filter { target in it.selector }
        val animating = rules.filter { !it.inReducedMotion && it.animation.isNotEmpty() && it.animation != "none" }
        val stilling = rules.filter { it.inReducedMotion }

        withClue("rules for $target: $rules") {
            animating.shouldNotBeEmpty()
            stilling.shouldNotBeEmpty()
        }

        for (still in stilling) {
            for (anim in animating) {
                withClue("${still.selector} against ${anim.selector}") {
                    classCount(still.selector) shouldBeGreaterThan classCount(anim.selector)
                }
            }
        }
    }

    "a beacon button draws its pulsing ring as ::after" {
        TestBed.preact(view = { KlangButton(onClick = {}, beacon = true) { +"Try me" } }) { root ->
            val button = root.selectCss("button").single()

            eventually {
                val ring = window.getComputedStyle(button, "::after")

                ring.getPropertyValue("animation-name") shouldBe "kl-beacon"
                ring.getPropertyValue("content") shouldNotBe "none"
                ring.getPropertyValue("position") shouldBe "absolute"
            }
        }
    }

    "the reduce-motion rules outweigh every rule that animates the beacon ring and the try lamp" {
        // Render once, so the stylesheets are built and mounted
        TestBed.preact(view = { KlangButton(onClick = {}, beacon = true) { +"Try me" } }) { _ ->
            eventually {
                stillingOutweighsAnimating(target = "${styles.beacon}::after")
                stillingOutweighsAnimating(target = ".${styles.lampTry}")
            }
        }
    }
})
