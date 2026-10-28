/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.intel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.ast.ArrowFunction
import io.peekandpoke.klang.script.ast.ArrowFunctionBody
import io.peekandpoke.klang.script.ast.CallExpression
import io.peekandpoke.klang.script.ast.Expression
import io.peekandpoke.klang.script.ast.LetDeclaration
import io.peekandpoke.klang.script.ast.MemberAccess
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.generated.generatedStdlibDocs

/**
 * `Ign.fn(x => ...)` types `x` as an Ignitor, so the Quick help and the completion inside it pick the Ignitor's own doors
 * (the maintainer, 2026-10-28); a bare `x => ...` leaves `x` unknown.
 */
class IgnitorFnIntelSpec : StringSpec({

    val registry = KlangDocsRegistry().apply { registerAll(generatedStdlibDocs) }

    /** The `x` the lambda's body calls a method on, in `let s = <init>`. */
    fun receiverIn(lambda: ArrowFunction): Expression {
        val body = (lambda.body as ArrowFunctionBody.ExpressionBody).expression as CallExpression

        return (body.callee as MemberAccess).obj
    }

    "inside Ign.fn the lambda's parameter is an Ignitor" {
        val a = AnalyzedAst.build("let s = Ign.fn(x => x.lowpass(3500, 1.4))", registry)
        val call = (a.ast.statements.single() as LetDeclaration).initializer as CallExpression
        val lambda = call.arguments.single().value as ArrowFunction

        a.typeOf(receiverIn(lambda))?.simpleName shouldBe "IgnitorDsl"
        a.diagnostics.size shouldBe 0
    }

    "a bare lambda's parameter stays unknown" {
        val a = AnalyzedAst.build("let s = x => x.lowpass(3500, 1.4)", registry)
        val lambda = (a.ast.statements.single() as LetDeclaration).initializer as ArrowFunction

        a.typeOf(receiverIn(lambda)).shouldBeNull()
    }
})
