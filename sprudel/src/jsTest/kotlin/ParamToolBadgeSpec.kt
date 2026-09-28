/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.script.generated.generatedSprudelDocs
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.sprudel.ui.registerSprudelUiTools
import io.peekandpoke.klang.ui.KlangUiToolRegistry
import io.peekandpoke.klang.ui.badgeTool
import io.peekandpoke.ultra.semanticui.SemanticIcon

/**
 * The editor's inline badge offers ONE tool per argument. Since C0.3 every tooled param declares a
 * value tool and the mini-notation tool that wraps it, and the wrapper borrows the value tool's
 * icon; listing both drew every icon twice. The badge picks the wrapper for a string literal and
 * the value tool for anything else; the context menu still lists both.
 */
class ParamToolBadgeSpec : StringSpec({

    registerSprudelUiTools()

    fun toolsOf(symbol: String, param: String) =
        generatedSprudelDocs[symbol].shouldNotBeNull()
            .variants.filterIsInstance<KlangCallable>()
            .flatMap { it.params }
            .first { it.name == param && it.uitools.isNotEmpty() }
            .let { KlangUiToolRegistry.resolve(it.uitools) }

    "reverb wet: the param has two tools with one icon, the badge shows one of them" {
        val tools = toolsOf("reverb", "wet")

        tools shouldHaveSize 2
        SemanticIcon.cssClassOf(tools[0].second.iconFn) shouldBe SemanticIcon.cssClassOf(tools[1].second.iconFn)

        tools.badgeTool("0.3").shouldNotBeNull().first shouldBe "SprudelReverbEditor"
        tools.badgeTool("\"<0.2 0.5>\"").shouldNotBeNull().first shouldBe "SprudelReverbSequenceEditor"
    }

    "body material: a quoted material opens the sequence tool, as before C0.3" {
        val tools = toolsOf("body", "material")

        tools.badgeTool("\"wood\"").shouldNotBeNull().first shouldBe "SprudelBodySequenceEditor"
        tools.badgeTool("'wood'").shouldNotBeNull().first shouldBe "SprudelBodySequenceEditor"
        tools.badgeTool("`wood`").shouldNotBeNull().first shouldBe "SprudelBodySequenceEditor"
        tools.badgeTool("mat").shouldNotBeNull().first shouldBe "SprudelBodyEditor"
    }

    "a param with a single tool shows that tool for any argument" {
        val tools = toolsOf("scale", "name")

        tools shouldHaveSize 1
        tools.badgeTool("\"C:major\"").shouldNotBeNull().first shouldBe "SprudelScaleEditor"
        tools.badgeTool("s").shouldNotBeNull().first shouldBe "SprudelScaleEditor"
    }

    "every tooled sprudel param: a number gets a value tool, a string gets a sequence tool when there is one" {
        val params = generatedSprudelDocs.values
            .flatMap { it.variants.filterIsInstance<KlangCallable>() }
            .flatMap { it.params }
            .filter { it.uitools.isNotEmpty() }

        params.size shouldBeGreaterThan 40

        params.forEach { param ->
            val tools = KlangUiToolRegistry.resolve(param.uitools)

            withClue("${param.name} ${param.uitools}") {
                tools shouldHaveSize param.uitools.size
                tools.badgeTool("1").shouldNotBeNull().second.editsSequence shouldBe false

                if (tools.any { it.second.editsSequence }) {
                    tools.badgeTool("\"1 2\"").shouldNotBeNull().second.editsSequence shouldBe true
                }
            }
        }
    }

    "the mini-notation wrapper reads differently from its value tool in the context menu" {
        val tools = toolsOf("reverb", "wet")

        tools[1].second.title shouldNotBe tools[0].second.title
    }
})
