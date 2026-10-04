/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.runtime

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.peekandpoke.klang.common.SourceLocation
import io.peekandpoke.klang.script.ast.Identifier
import io.peekandpoke.klang.script.builder.registerFunction
import io.peekandpoke.klang.script.klangScriptEngine

/**
 * A script error thrown inside a native call without a source location gets the location of that call
 * ([guardNativeCall]), so the editor can point at it; one that already has a location keeps it. And
 * [withLocation], which does the rebuilding, keeps every other field of every subclass.
 */
class NativeCallErrorLocationSpec : StringSpec({

    /** Line and column (1-based) of a call's location, its opening parenthesis, found in [source] after [callee]. */
    fun callLineAndColumn(source: String, callee: String): Pair<Int, Int> {
        val lines = source.lines()
        val lineIndex = lines.indexOfFirst { it.contains("$callee(") }
        val column = lines[lineIndex].indexOf("$callee(") + callee.length + 1

        return (lineIndex + 1) to column
    }

    "an error without a location thrown inside a native call is reported at the call" {
        val engine = klangScriptEngine {
            registerFunction<Double, Double>("bad") { _ ->
                throw KlangScriptTypeError("not a good value", operation = "bad")
            }
        }

        val source = "let a = 1\nlet b = bad(a)"

        val error = shouldThrow<KlangScriptTypeError> {
            engine.execute(source)
        }

        error.message shouldBe "not a good value"
        error.operation shouldBe "bad"
        (error.location?.startLine to error.location?.startColumn) shouldBe callLineAndColumn(source, "bad")
    }

    "an error that already has a location keeps it, the very same error" {
        val own = SourceLocation(source = "lib", startLine = 9, startColumn = 9, endLine = 9, endColumn = 10)
        val thrown = KlangScriptTypeError("located already", operation = "bad", location = own)

        val engine = klangScriptEngine {
            registerFunction<Double, Double>("bad") { _ ->
                throw thrown
            }
        }

        val error = shouldThrow<KlangScriptTypeError> {
            engine.execute("let a = 1\nlet b = bad(a)")
        }

        error shouldBeSameInstanceAs thrown
        error.location shouldBe own
    }

    "withLocation keeps the subclass and every field but the location, for every subclass" {
        val old = SourceLocation(source = "old", startLine = 1, startColumn = 2, endLine = 1, endColumn = 3)
        val new = SourceLocation(source = "new", startLine = 4, startColumn = 5, endLine = 4, endColumn = 6)
        val node = Identifier("x", old)
        val trace = listOf(CallStackFrame("outer", old), CallStackFrame("inner", null))
        val cause = IllegalStateException("underneath")

        // Every field set to a non-default value, so a field the rebuild drops shows.
        val table: List<KlangScriptRuntimeError> = listOf(
            KlangScriptTypeError("type", operation = "op", location = null, astNode = node, callStackTrace = trace),
            KlangScriptReferenceError("sym", message = "reference", location = null, astNode = node, callStackTrace = trace),
            KlangScriptArgumentError(
                "fn", "argument", expected = 2, actual = 3, location = null, astNode = node, callStackTrace = trace,
            ),
            KlangScriptImportError("lib", "import", location = null, astNode = node, callStackTrace = trace),
            KlangScriptAssignmentError("v", "assignment", location = null, astNode = node, callStackTrace = trace),
            KlangScriptStackOverflowError("overflow", location = null, astNode = node, callStackTrace = trace),
            KlangScriptInternalError("internal", cause = cause, location = null, astNode = node, callStackTrace = trace),
        )

        /** Every field of [e] but the location, by name; exhaustive, so a new subclass must join the table. */
        fun fieldsOf(e: KlangScriptRuntimeError): Map<String, Any?> {
            val common = mapOf(
                "class" to e::class,
                "message" to e.message,
                "errorType" to e.errorType,
                "astNode" to e.astNode,
                "callStackTrace" to e.callStackTrace,
                "cause" to e.cause,
            )

            val own: Map<String, Any?> = when (e) {
                is KlangScriptTypeError -> mapOf("operation" to e.operation)
                is KlangScriptReferenceError -> mapOf("symbolName" to e.symbolName)
                is KlangScriptArgumentError -> mapOf("functionName" to e.functionName, "expected" to e.expected, "actual" to e.actual)
                is KlangScriptImportError -> mapOf("libraryName" to e.libraryName)
                is KlangScriptAssignmentError -> mapOf("variableName" to e.variableName)
                is KlangScriptStackOverflowError -> emptyMap()
                is KlangScriptInternalError -> emptyMap()
            }

            return common + own
        }

        // One row per subclass: a missing row fails here.
        table.map { it::class }.toSet().size shouldBe 7

        for (original in table) {
            val rebuilt = original.withLocation(new)

            withClue(original::class.simpleName) {
                rebuilt.location shouldBe new
                fieldsOf(rebuilt) shouldBe fieldsOf(original)
                original.location shouldBe null
            }
        }
    }
})
