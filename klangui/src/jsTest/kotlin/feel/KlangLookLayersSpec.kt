/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.feel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import io.peekandpoke.klang.ui.feel.KlangLookLayers.Layer

/** The look's stack (KLANG-00337): which layer wins, what clearing restores, and which values a role accepts. */
class KlangLookLayersSpec : StringSpec({

    "an empty stack resolves every role to its default" {
        val look = KlangLookLayers().resolve()

        for (role in KlangRole.entries) {
            look[role] shouldBe role.default
        }

        look shouldBe KlangLook.defaults
    }

    "a higher layer wins over a lower one, and a role it leaves out falls through" {
        val layers = KlangLookLayers()
            .with(Layer.Script, mapOf(KlangRole.Accent to "#333333"))
            .with(Layer.User, mapOf(KlangRole.Accent to "#111111", KlangRole.InkText to "#aaaaaa"))
            .with(Layer.Song, mapOf(KlangRole.Accent to "#222222"))

        val look = layers.resolve()

        look[KlangRole.Accent] shouldBe "#333333"
        look[KlangRole.InkText] shouldBe "#aaaaaa"
        look[KlangRole.Edge] shouldBe KlangRole.Edge.default
    }

    "clearing a layer restores the one below it" {
        val layers = KlangLookLayers()
            .with(Layer.User, mapOf(KlangRole.Accent to "#111111"))
            .with(Layer.Song, mapOf(KlangRole.Accent to "#222222"))

        layers.cleared(Layer.Song).resolve()[KlangRole.Accent] shouldBe "#111111"
        layers.cleared(Layer.Song).cleared(Layer.User).resolve()[KlangRole.Accent] shouldBe KlangRole.Accent.default
    }

    "setting a layer replaces what it held, it does not merge" {
        val layers = KlangLookLayers()
            .with(Layer.User, mapOf(KlangRole.Accent to "#111111"))
            .with(Layer.User, mapOf(KlangRole.InkText to "#aaaaaa"))

        layers[Layer.User] shouldBe mapOf(KlangRole.InkText to "#aaaaaa")
        layers.resolve()[KlangRole.Accent] shouldBe KlangRole.Accent.default
    }

    "a value its role does not accept is dropped, and the role falls through" {
        val layers = KlangLookLayers()
            .with(Layer.User, mapOf(KlangRole.Accent to "#111111"))
            .with(
                Layer.Song,
                mapOf(
                    KlangRole.Accent to "red; background: url(https://example.com/x.png)",
                    KlangRole.Radius to "#123456",
                    KlangRole.InkText to "3px",
                ),
            )

        layers[Layer.Song] shouldBe emptyMap()
        layers.resolve()[KlangRole.Accent] shouldBe "#111111"
        layers.resolve()[KlangRole.Radius] shouldBe KlangRole.Radius.default
    }

    "a colour role accepts the four hex forms and nothing else" {
        val role = KlangRole.Accent

        listOf("#abc", "#abcd", "#aabbcc", "#AABBCC80").forEach { role.accepts(it) shouldBe true }

        listOf("", "abc", "#ab", "#abcde", "#aabbccd", "#aabbccdde", "#ggg", "rgb(0,0,0)", "var(--x)", "#abc ")
            .forEach { role.accepts(it) shouldBe false }
    }

    "a length role accepts 0 to 999 px with an optional fraction, and nothing else" {
        val role = KlangRole.Radius

        listOf("0px", "3px", "999px", "2.5px", "10.125px").forEach { role.accepts(it) shouldBe true }

        listOf("", "3", "px", "1000px", "-3px", "3em", "3.px", "2.1234px", "3px;", " 3px", "calc(3px)")
            .forEach { role.accepts(it) shouldBe false }
    }

    "every role's default fits its own kind" {
        for (role in KlangRole.entries) {
            role.accepts(role.default) shouldBe true
        }
    }

    "every CSS variable is a --kl- name, unique, and leads back to its role" {
        val names = KlangRole.entries.map { it.cssVar }

        names.forEach { it shouldStartWith "--kl-" }
        names.toSet().size shouldBe names.size

        for (role in KlangRole.entries) {
            KlangRole.ofCssVar(role.cssVar) shouldBe role
        }

        KlangRole.ofCssVar("--klang-accent") shouldBe null
    }

    "the look lists every role as its CSS variable, in the roles' order" {
        val look = KlangLookLayers().with(Layer.User, mapOf(KlangRole.Accent to "#111111")).resolve()

        look.cssVars().map { it.first } shouldContainExactly KlangRole.entries.map { it.cssVar }
        look.cssVars().first { it.first == KlangRole.Accent.cssVar }.second shouldBe "#111111"
        look shouldNotBe KlangLook.defaults
    }
})
