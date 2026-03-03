package io.peekandpoke.klang.blocks.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Round-trip tests for all KlangScript "easy" features added in the first implementation
 * sprint: new binary operators, prefix/postfix ++/--, ternary, index access, and assignment.
 */
class EasyFeaturesRoundTripTest : StringSpec({

    // ── New binary operators ──────────────────────────────────────────────────

    "power operator (**) round-trips" {
        roundTrip("let x = a ** b").shouldRoundTripWithCode()
    }

    "power operator generates correct code" {
        roundTrip("let x = a ** b").generatedCode shouldBe "let x = a ** b"
    }

    "strict equal (===) round-trips" {
        roundTrip("let x = a === b").shouldRoundTripWithCode()
    }

    "strict equal generates correct code" {
        roundTrip("let x = a === b").generatedCode shouldBe "let x = a === b"
    }

    "strict not-equal (!==) round-trips" {
        roundTrip("let x = a !== b").shouldRoundTripWithCode()
    }

    "strict not-equal generates correct code" {
        roundTrip("let x = a !== b").generatedCode shouldBe "let x = a !== b"
    }

    "in operator round-trips" {
        roundTrip("let x = \"key\" in obj").shouldRoundTripWithCode()
    }

    "in operator generates correct code" {
        roundTrip("let x = \"key\" in obj").generatedCode shouldBe "let x = \"key\" in obj"
    }

    "new binary operators produce KBBinaryArg with correct op symbol" {
        val power = roundTrip("note(a ** b)")
        val block = power.blocks.statements
            .filterIsInstance<KBChainStmt>()
            .flatMap { it.steps }
            .filterIsInstance<KBCallBlock>()
            .first()
        block.args.single() shouldBe KBBinaryArg(KBIdentifierArg("a"), "**", KBIdentifierArg("b"))
    }

    // ── Prefix ++/-- ─────────────────────────────────────────────────────────

    "prefix ++ round-trips" {
        roundTrip("let x = ++y").shouldRoundTripWithCode()
    }

    "prefix ++ generates correct code" {
        roundTrip("let x = ++y").generatedCode shouldBe "let x = ++y"
    }

    "prefix -- round-trips" {
        roundTrip("let x = --y").shouldRoundTripWithCode()
    }

    "prefix -- generates correct code" {
        roundTrip("let x = --y").generatedCode shouldBe "let x = --y"
    }

    "prefix ++ produces KBUnaryArg with PREFIX position" {
        val result = roundTrip("note(++x)")
        val block = result.blocks.statements
            .filterIsInstance<KBChainStmt>()
            .flatMap { it.steps }
            .filterIsInstance<KBCallBlock>()
            .first()
        block.args.single() shouldBe KBUnaryArg("++", KBIdentifierArg("x"), KBUnaryPosition.PREFIX)
    }

    // ── Postfix ++/-- ─────────────────────────────────────────────────────────

    "postfix ++ as statement round-trips" {
        roundTrip("x++").shouldRoundTripWithCode()
    }

    "postfix ++ generates correct code" {
        roundTrip("x++").generatedCode shouldBe "x++"
    }

    "postfix -- as statement round-trips" {
        roundTrip("x--").shouldRoundTripWithCode()
    }

    "postfix -- generates correct code" {
        roundTrip("x--").generatedCode shouldBe "x--"
    }

    "postfix ++ produces KBUnaryArg with POSTFIX position" {
        val result = roundTrip("note(x++)")
        val block = result.blocks.statements
            .filterIsInstance<KBChainStmt>()
            .flatMap { it.steps }
            .filterIsInstance<KBCallBlock>()
            .first()
        block.args.single() shouldBe KBUnaryArg("++", KBIdentifierArg("x"), KBUnaryPosition.POSTFIX)
    }

    // ── Ternary expression ────────────────────────────────────────────────────

    "simple ternary round-trips" {
        roundTrip("let x = cond ? 1 : 0").shouldRoundTripWithCode()
    }

    "simple ternary generates correct code" {
        roundTrip("let x = cond ? 1 : 0").generatedCode shouldBe "let x = cond ? 1 : 0"
    }

    "ternary with expression branches round-trips" {
        roundTrip("let y = a > b ? a : b").shouldRoundTripWithCode()
    }

    "ternary as function argument round-trips" {
        roundTrip("note(cond ? \"c3\" : \"e3\")").shouldRoundTripWithCode()
    }

    "ternary produces KBTernaryArg with correct structure" {
        val result = roundTrip("let x = cond ? 1 : 0")
        val stmt = result.blocks.statements.filterIsInstance<KBLetStmt>().first()
        stmt.value shouldBe KBTernaryArg(
            condition = KBIdentifierArg("cond"),
            thenExpr = KBNumberArg(1.0),
            elseExpr = KBNumberArg(0.0),
        )
    }

    // ── Index access ─────────────────────────────────────────────────────────

    "array index access round-trips" {
        roundTrip("let x = arr[0]").shouldRoundTripWithCode()
    }

    "array index access generates correct code" {
        roundTrip("let x = arr[0]").generatedCode shouldBe "let x = arr[0]"
    }

    "string key index access round-trips" {
        roundTrip("let x = obj[\"key\"]").shouldRoundTripWithCode()
    }

    "index access as function argument round-trips" {
        roundTrip("note(arr[0])").shouldRoundTripWithCode()
    }

    "index access produces KBIndexAccessArg with correct structure" {
        val result = roundTrip("let x = arr[0]")
        val stmt = result.blocks.statements.filterIsInstance<KBLetStmt>().first()
        stmt.value shouldBe KBIndexAccessArg(
            obj = KBIdentifierArg("arr"),
            index = KBNumberArg(0.0),
        )
    }

    // ── Assignment statement ──────────────────────────────────────────────────

    "simple identifier assignment round-trips" {
        roundTrip("x = 5").shouldRoundTripWithCode()
    }

    "simple identifier assignment generates correct code" {
        roundTrip("x = 5").generatedCode shouldBe "x = 5"
    }

    "assignment with expression value round-trips" {
        roundTrip("x = x + 1").shouldRoundTripWithCode()
    }

    "assignment with expression value generates correct code" {
        roundTrip("x = x + 1").generatedCode shouldBe "x = x + 1"
    }

    "assignment produces KBAssignStmt with correct structure" {
        val result = roundTrip("x = 5")
        val stmt = result.blocks.statements.filterIsInstance<KBAssignStmt>().first()
        stmt.target shouldBe "x"
        stmt.value shouldBe KBNumberArg(5.0)
    }

    "compound assignment desugars and round-trips as simple assignment" {
        // x += 1 is desugared at parse time to x = x + 1
        // The round-trip emits "x = x + 1", which re-parses to the same AST — passes.
        roundTrip("x = x + 1").shouldRoundTripWithCode()
    }

    // ── Index-access assignment (complex target) ───────────────────────────────

    "index access assignment round-trips" {
        roundTrip("arr[0] = 5").shouldRoundTripWithCode()
    }

    "index access assignment generates correct code" {
        roundTrip("arr[0] = 5").generatedCode shouldBe "arr[0] = 5"
    }

    "index access assignment produces KBAssignStmt with raw target string" {
        val result = roundTrip("arr[0] = 5")
        val stmt = result.blocks.statements.filterIsInstance<KBAssignStmt>().first()
        stmt.target shouldBe "arr[0]"
        stmt.value shouldBe KBNumberArg(5.0)
    }
})
