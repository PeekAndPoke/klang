/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.generated.generatedStdlibDocs
import io.peekandpoke.klang.script.intel.AnalyzedAst
import io.peekandpoke.klang.script.intel.CompletionProvider
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.BooleanValue
import io.peekandpoke.klang.script.runtime.RuntimeValue
import io.peekandpoke.klang.script.runtime.StringValue
import io.peekandpoke.klang.script.types.KlangCallable

/**
 * Methods called on a boolean, end to end: every method of [KlangScriptBooleanExtensions] resolves.
 *
 * `true.toString()` failed with "Cannot access property 'toString' on non-object value: true" because the
 * interpreter looked up extensions for numbers, strings and arrays only (`docs/tasks-archive/2026-10/20261007-boolean-member-access.md`).
 * The editor side (completion after `true.`, the hover on the method) is checked per method too. The last test keeps
 * the list complete: a new boolean method without a row here fails it.
 */
class StdLibBooleanMethodsTest : StringSpec({

    fun engine() = klangScript {
        registerLibrary(KlangStdLib.create())
    }

    fun eval(code: String): RuntimeValue = engine().execute("import * from \"stdlib\"\n$code")

    val rows = listOf(
        "toString" to listOf(
            "true.toString()" to "true",
            "false.toString()" to "false",
            "(1 < 2).toString()" to "true",
            "let b = 2 > 3\nb.toString()" to "false",
            "`flag: ${'$'}{true.toString()}`" to "flag: true",
        ),
    )

    "every boolean method resolves" {
        rows.forEach { (_, cases) ->
            cases.forEach { (code, expected) ->
                withClue(code) {
                    eval(code).shouldBeInstanceOf<StringValue>().value shouldBe expected
                }
            }
        }
    }

    "the editor offers and documents every boolean method after a boolean" {
        val registry = KlangDocsRegistry().apply { registerAll(generatedStdlibDocs) }

        rows.forEach { (method, _) ->
            val code = "true.$method()"
            val analysis = AnalyzedAst.build(code, registry)
            // While `true.` does not parse, the editor completes from the last good analysis (`true`), at the
            // receiver's last character (`DslCompletionSource`)
            val receiverType = AnalyzedAst.build("true", registry).receiverTypeBeforeDot(3).shouldNotBeNull()

            withClue(code) {
                receiverType.simpleName shouldBe "Boolean"
                CompletionProvider(registry).memberCompletions(receiverType, "").map { it.name } shouldContain method

                val hover = analysis.symbolAt(code.indexOf(method)).shouldNotBeNull()
                hover.variants.map { (it as KlangCallable).receiver?.simpleName } shouldBe listOf("Boolean")
                analysis.diagnostics.shouldBeEmpty()
            }
        }
    }

    "every registered boolean method has a row" {
        val engine = engine()

        engine.execute("import * from \"stdlib\"\nnull")
        engine.getExtensionMethodNames(BooleanValue(true)) shouldBe rows.map { it.first }.toSet()
    }
})
