package io.peekandpoke.klang.script.intel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangParam
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.script.types.KlangType

/**
 * Coverage for [NamedArgumentChecker] — the static analyzer that surfaces
 * named-arg mistakes in the editor before the user runs the code.
 *
 * Uses a small hand-built [KlangDocsRegistry] with known callables so the
 * tests don't depend on the full stdlib.
 */
class NamedArgumentCheckerTest : StringSpec({

    // ── Registry fixture ────────────────────────────────────────────────────

    fun registry(): KlangDocsRegistry = KlangDocsRegistry().apply {
        // Top-level function: filter(cutoff: Number, q: Number? = 1)
        register(
            KlangSymbol(
                name = "filter",
                category = "test",
                tags = emptyList(),
                aliases = emptyList(),
                library = "test",
                variants = listOf(
                    KlangCallable(
                        name = "filter",
                        receiver = null,
                        params = listOf(
                            KlangParam(name = "cutoff", type = KlangType("Number")),
                            KlangParam(name = "q", type = KlangType("Number"), isOptional = true),
                        ),
                        returnType = KlangType("Number"),
                    )
                ),
            )
        )

        // Method on Pattern: Pattern.gain(amount: Number)
        register(
            KlangSymbol(
                name = "gain",
                category = "test",
                tags = emptyList(),
                aliases = emptyList(),
                library = "test",
                variants = listOf(
                    KlangCallable(
                        name = "gain",
                        receiver = KlangType("Pattern"),
                        params = listOf(
                            KlangParam(name = "amount", type = KlangType("Number")),
                        ),
                        returnType = KlangType("Pattern"),
                    )
                ),
            )
        )

        // Top-level function with no params: answer()
        register(
            KlangSymbol(
                name = "answer",
                category = "test",
                tags = emptyList(),
                aliases = emptyList(),
                library = "test",
                variants = listOf(
                    KlangCallable(
                        name = "answer",
                        receiver = null,
                        params = emptyList(),
                        returnType = KlangType("Number"),
                    )
                ),
            )
        )

        // Top-level creator that returns Pattern: note(pattern: String)
        register(
            KlangSymbol(
                name = "note",
                category = "test",
                tags = emptyList(),
                aliases = emptyList(),
                library = "test",
                variants = listOf(
                    KlangCallable(
                        name = "note",
                        receiver = null,
                        params = listOf(
                            KlangParam(name = "pattern", type = KlangType("String")),
                        ),
                        returnType = KlangType("Pattern"),
                    )
                ),
            )
        )
    }

    fun analyze(code: String): AnalyzedAst = AnalyzedAst.build(code, registry())

    fun diagnosticsOf(code: String): List<AnalyzerDiagnostic> = analyze(code).diagnostics

    // ── Positive: clean calls produce no diagnostics ────────────────────────

    "clean positional call — no diagnostics" {
        diagnosticsOf("filter(800, 0.5)").shouldBeEmpty()
    }

    "clean all-named call — no diagnostics" {
        diagnosticsOf("filter(cutoff = 800, q = 0.5)").shouldBeEmpty()
    }

    "clean named call omitting optional — no diagnostics" {
        diagnosticsOf("filter(cutoff = 800)").shouldBeEmpty()
    }

    "zero-arg function called with no args — no diagnostics" {
        diagnosticsOf("answer()").shouldBeEmpty()
    }

    "unknown callee — silent skip, no diagnostics" {
        diagnosticsOf("unknownFunc(x = 1)").shouldBeEmpty()
    }

    "script-defined arrow function (not in registry) — silent skip" {
        diagnosticsOf("((x) => x)(x = 1)").shouldBeEmpty()
    }

    // ── Rule 1: mixing positional + named ───────────────────────────────────

    "mixing positional then named → diagnostic" {
        val d = diagnosticsOf("filter(800, q = 0.5)")
        d shouldHaveSize 1
        d[0].severity shouldBe DiagnosticSeverity.ERROR
        d[0].message shouldContain "both positional and named"
        d[0].message shouldContain "filter"
    }

    "mixing named then positional → diagnostic" {
        val d = diagnosticsOf("filter(cutoff = 800, 0.5)")
        d shouldHaveSize 1
        d[0].message shouldContain "both positional and named"
    }

    "mixing — only one diagnostic (no cascades)" {
        val d = diagnosticsOf("filter(800, q = 0.5)")
        d shouldHaveSize 1
    }

    // ── Rule 2: unknown named parameter ─────────────────────────────────────

    "unknown named param → diagnostic with expected-list hint" {
        val d = diagnosticsOf("filter(nope = 1)")
        d.any { it.message.contains("Unknown parameter 'nope'") } shouldBe true
        d.any { it.message.contains("expected: cutoff, q") } shouldBe true
    }

    "unknown named param on zero-param function → 'takes no parameters'" {
        val d = diagnosticsOf("answer(bogus = 42)")
        d.any { it.message.contains("takes no parameters") } shouldBe true
    }

    "unknown named — location points at the name token" {
        val d = diagnosticsOf("filter(nope = 1)")
        val unknown = d.first { it.message.contains("Unknown") }
        // "filter(nope = 1)" — "nope" starts at column 8
        unknown.startColumn shouldBe 8
    }

    // ── Rule 2: duplicate named parameter ───────────────────────────────────

    "duplicate named param → diagnostic" {
        val d = diagnosticsOf("filter(cutoff = 1, cutoff = 2)")
        d.any { it.message.contains("Duplicate named argument: 'cutoff'") } shouldBe true
    }

    "duplicate of an unknown name → both unknown and duplicate flagged" {
        val d = diagnosticsOf("filter(nope = 1, nope = 2)")
        d.any { it.message.contains("Unknown parameter 'nope'") } shouldBe true
        d.any { it.message.contains("Duplicate named argument: 'nope'") } shouldBe true
    }

    // ── Rule 3: missing required parameter ──────────────────────────────────

    "missing required param in named call → diagnostic" {
        val d = diagnosticsOf("filter(q = 0.5)")
        d.any { it.message.contains("missing required parameter(s): cutoff") } shouldBe true
    }

    "all required supplied — no missing diagnostic" {
        diagnosticsOf("filter(cutoff = 800, q = 0.5)")
            .filter { it.message.contains("missing") }
            .shouldBeEmpty()
    }

    "optional omitted — no missing diagnostic" {
        diagnosticsOf("filter(cutoff = 800)")
            .filter { it.message.contains("missing") }
            .shouldBeEmpty()
    }

    // ── Cascade suppression ─────────────────────────────────────────────────

    "unknown named suppresses missing-required cascade" {
        // filter(bogus = 1) against filter(cutoff, q?) — "Unknown 'bogus'" fires,
        // but "missing required 'cutoff'" does NOT fire because the unknown-hint
        // already shows the expected param list.
        val d = diagnosticsOf("filter(bogus = 1)")
        d.any { it.message.contains("Unknown") } shouldBe true
        d.none { it.message.contains("missing required") } shouldBe true
    }

    // ── Nested calls ────────────────────────────────────────────────────────

    "nested call arguments are also checked" {
        // filter(cutoff = answer(bogus = 1)) — inner call's "bogus" should fire.
        val d = diagnosticsOf("filter(cutoff = answer(bogus = 1))")
        d.any { it.message.contains("'bogus'") && it.message.contains("'answer'") } shouldBe true
    }

    // ── Method calls with receiver resolution ───────────────────────────────

    "method on known receiver — named call checked" {
        // note("c") returns Pattern; Pattern.gain(amount) is registered.
        // gain(nope = 1) → unknown.
        val d = diagnosticsOf("note(\"c\").gain(nope = 1)")
        d.any { it.message.contains("Unknown parameter 'nope'") && it.message.contains("'gain'") } shouldBe true
    }

    "method on known receiver — clean named call" {
        diagnosticsOf("note(\"c\").gain(amount = 0.5)").shouldBeEmpty()
    }

    "method on unresolvable receiver — silent skip" {
        // somethingUnknown().gain(nope = 1) — receiver type can't be inferred.
        diagnosticsOf("somethingUnknown().gain(nope = 1)").shouldBeEmpty()
    }

    // ── computeDiagnostics opt-out ──────────────────────────────────────────

    "computeDiagnostics=false produces empty diagnostics" {
        val ast = AnalyzedAst.build("filter(bogus = 1)", registry(), computeDiagnostics = false)
        ast.diagnostics.shouldBeEmpty()
    }
})
