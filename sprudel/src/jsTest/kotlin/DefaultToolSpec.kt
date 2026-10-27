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
import io.peekandpoke.klang.script.types.KlangParam
import io.peekandpoke.klang.sprudel.ui.registerSprudelUiTools
import io.peekandpoke.klang.ui.KlangUiToolRegistry
import io.peekandpoke.klang.ui.defaultTool
import io.peekandpoke.ultra.semanticui.SemanticIcon

/**
 * The tool the Sprudel tab opens on for an argument (KLANG-00374): every tooled param declares a value tool and the
 * mini-notation tool that wraps it (C0.3), and the wrapper borrows the value tool's icon. The tab opens on the wrapper
 * for a string literal and on the value tool for anything else; its keys offer both.
 */
class DefaultToolSpec : StringSpec({

    registerSprudelUiTools()

    /** An argument's tools as the editor lists them: the call's own (`@tool A`), then the parameter's. */
    fun KlangCallable.toolsFor(param: KlangParam): List<String> = (uitools + param.uitools).distinct()

    fun toolsOf(symbol: String, param: String) =
        generatedSprudelDocs[symbol].shouldNotBeNull()
            .variants.filterIsInstance<KlangCallable>()
            .flatMap { callable -> callable.params.map { callable.toolsFor(it) to it } }
            .first { (tools, p) -> p.name == param && tools.isNotEmpty() }
            .let { (tools, _) -> KlangUiToolRegistry.resolve(tools) }

    "reverb wet: the param has two tools with one icon, the tab opens on one of them" {
        val tools = toolsOf("reverb", "wet")

        tools shouldHaveSize 2
        SemanticIcon.cssClassOf(tools[0].second.iconFn) shouldBe SemanticIcon.cssClassOf(tools[1].second.iconFn)

        tools.defaultTool("0.3").shouldNotBeNull().first shouldBe "SprudelReverbEditor"
        tools.defaultTool("\"<0.2 0.5>\"").shouldNotBeNull().first shouldBe "SprudelReverbSequenceEditor"
    }

    "body material: a quoted material opens the sequence tool, as before C0.3" {
        val tools = toolsOf("body", "material")

        tools.defaultTool("\"wood\"").shouldNotBeNull().first shouldBe "SprudelBodySequenceEditor"
        tools.defaultTool("'wood'").shouldNotBeNull().first shouldBe "SprudelBodySequenceEditor"
        tools.defaultTool("`wood`").shouldNotBeNull().first shouldBe "SprudelBodySequenceEditor"
        tools.defaultTool("mat").shouldNotBeNull().first shouldBe "SprudelBodyEditor"
    }

    "a param with a single tool shows that tool for any argument" {
        val tools = toolsOf("scale", "name")

        tools shouldHaveSize 1
        tools.defaultTool("\"C:major\"").shouldNotBeNull().first shouldBe "SprudelScaleEditor"
        tools.defaultTool("s").shouldNotBeNull().first shouldBe "SprudelScaleEditor"
    }

    "every tooled sprudel param: a number gets a value tool, a string gets a sequence tool when there is one" {
        val params = generatedSprudelDocs.values
            .flatMap { it.variants.filterIsInstance<KlangCallable>() }
            .flatMap { callable -> callable.params.filter { it.uitools.isNotEmpty() }.map { callable.toolsFor(it) to it } }

        params.size shouldBeGreaterThan 40

        params.forEach { (names, param) ->
            val tools = KlangUiToolRegistry.resolve(names)

            withClue("${param.name} $names") {
                tools shouldHaveSize names.size
                tools.defaultTool("1").shouldNotBeNull().second.editsSequence shouldBe false

                if (tools.any { it.second.editsSequence }) {
                    tools.defaultTool("\"1 2\"").shouldNotBeNull().second.editsSequence shouldBe true
                }
            }
        }
    }

    "the mini-notation wrapper reads differently from its value tool on the tab's keys" {
        val tools = toolsOf("reverb", "wet")

        tools[1].second.title shouldNotBe tools[0].second.title
    }
})
