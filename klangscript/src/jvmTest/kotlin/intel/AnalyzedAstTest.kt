package io.peekandpoke.klang.script.intel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.ast.ArrowFunction
import io.peekandpoke.klang.script.ast.ArrowFunctionBody
import io.peekandpoke.klang.script.ast.AssignmentExpression
import io.peekandpoke.klang.script.ast.BinaryOperation
import io.peekandpoke.klang.script.ast.CallExpression
import io.peekandpoke.klang.script.ast.ConstDeclaration
import io.peekandpoke.klang.script.ast.DoWhileStatement
import io.peekandpoke.klang.script.ast.ElseBranch
import io.peekandpoke.klang.script.ast.Expression
import io.peekandpoke.klang.script.ast.ExpressionStatement
import io.peekandpoke.klang.script.ast.ForStatement
import io.peekandpoke.klang.script.ast.Identifier
import io.peekandpoke.klang.script.ast.IfExpression
import io.peekandpoke.klang.script.ast.IndexAccess
import io.peekandpoke.klang.script.ast.LetDeclaration
import io.peekandpoke.klang.script.ast.MemberAccess
import io.peekandpoke.klang.script.ast.ReturnStatement
import io.peekandpoke.klang.script.ast.TemplateLiteral
import io.peekandpoke.klang.script.ast.TemplatePart
import io.peekandpoke.klang.script.ast.TernaryExpression
import io.peekandpoke.klang.script.ast.UnaryOperation
import io.peekandpoke.klang.script.ast.WhileStatement
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.generated.generatedStdlibDocs
import io.peekandpoke.klang.script.parser.KlangScriptParser

class AnalyzedAstTest : StringSpec({

    fun stdlibRegistry(): KlangDocsRegistry = KlangDocsRegistry().apply {
        registerAll(generatedStdlibDocs)
    }

    fun analyze(code: String): AnalyzedAst = AnalyzedAst.build(code, stdlibRegistry())

    /** Helper: get the single top-level expression from a one-statement program. */
    fun AnalyzedAst.topExpr(): Expression {
        val stmt = ast.statements.firstOrNull() ?: error("No statements in program")
        return (stmt as? ExpressionStatement)?.expression
            ?: error("First statement is ${stmt::class.simpleName}, expected ExpressionStatement")
    }

    // ── Literal types ──────────────────────────────────────────────────────

    "literal: number has type Number" {
        val a = analyze("42")
        a.typeOf(a.topExpr())?.simpleName shouldBe "Number"
    }

    "literal: string has type String" {
        val a = analyze("\"hello\"")
        a.typeOf(a.topExpr())?.simpleName shouldBe "String"
    }

    "literal: boolean has type Boolean" {
        val a = analyze("true")
        a.typeOf(a.topExpr())?.simpleName shouldBe "Boolean"
    }

    "literal: array has type Array" {
        val a = analyze("[1, 2, 3]")
        a.typeOf(a.topExpr())?.simpleName shouldBe "Array"
    }

    "literal: object has type Object" {
        val a = analyze("{a: 1, b: 2}")
        a.typeOf(a.topExpr())?.simpleName shouldBe "Object"
    }

    "literal: template literal has type String" {
        val a = analyze("`hello \${42}`")
        a.typeOf(a.topExpr())?.simpleName shouldBe "String"
    }

    "literal: template literal — interpolated expression is also typed" {
        val a = analyze("`hello \${42}`")
        val tmpl = a.topExpr() as TemplateLiteral
        val interp = tmpl.parts.filterIsInstance<TemplatePart.Interp>().first()
        a.typeOf(interp.expression)?.simpleName shouldBe "Number"
    }

    "literal: null has type null" {
        val a = analyze("null")
        a.typeOf(a.topExpr()).shouldBeNull()
    }

    // ── Identifier types ───────────────────────────────────────────────────

    "identifier: Osc resolves to Osc" {
        val a = analyze("Osc")
        a.typeOf(a.topExpr())?.simpleName shouldBe "Osc"
    }

    "identifier: Math resolves to Math" {
        val a = analyze("Math")
        a.typeOf(a.topExpr())?.simpleName shouldBe "Math"
    }

    "identifier: unknown resolves to null" {
        val a = analyze("unknownThing")
        a.typeOf(a.topExpr()).shouldBeNull()
    }

    // ── Simple method calls ────────────────────────────────────────────────

    "call: Osc.sine() returns IgnitorDsl" {
        val a = analyze("Osc.sine()")
        a.typeOf(a.topExpr())?.simpleName shouldBe "IgnitorDsl"
    }

    "call: Math.sqrt(16) returns Number" {
        val a = analyze("Math.sqrt(16)")
        a.typeOf(a.topExpr())?.simpleName shouldBe "Number"
    }

    // ── Method chains ──────────────────────────────────────────────────────

    "chain: Osc.sine().lowpass(1000) — outer is IgnitorDsl" {
        val a = analyze("Osc.sine().lowpass(1000)")
        a.typeOf(a.topExpr())?.simpleName shouldBe "IgnitorDsl"
    }

    "chain: Osc.sine().lowpass(1000) — inner Osc.sine() is also IgnitorDsl" {
        val a = analyze("Osc.sine().lowpass(1000)")
        val outerCall = a.topExpr() as CallExpression
        val memberAccess = outerCall.callee as MemberAccess
        val innerCall = memberAccess.obj
        a.typeOf(innerCall)?.simpleName shouldBe "IgnitorDsl"
    }

    "chain: Osc.supersaw().lowpass(2000).adsr(0.01, 0.2, 0.5, 0.5) returns IgnitorDsl" {
        val a = analyze("Osc.supersaw().lowpass(2000).adsr(0.01, 0.2, 0.5, 0.5)")
        a.typeOf(a.topExpr())?.simpleName shouldBe "IgnitorDsl"
    }

    // ── Unknown symbols / chain breakage ───────────────────────────────────

    "unknown: unknownFunc() returns null" {
        val a = analyze("unknownFunc()")
        a.typeOf(a.topExpr()).shouldBeNull()
    }

    "unknown: Osc.unknownMethod() returns null" {
        val a = analyze("Osc.unknownMethod()")
        a.typeOf(a.topExpr()).shouldBeNull()
    }

    "chain break: Osc.sine().unknownMethod().lowpass(1000) — outer is null" {
        val a = analyze("Osc.sine().unknownMethod().lowpass(1000)")
        a.typeOf(a.topExpr()).shouldBeNull()
    }

    "chain break: inner Osc.sine() is still IgnitorDsl even when chain breaks later" {
        val code = "Osc.sine().unknownMethod().lowpass(1000)"
        val a = analyze(code)

        val outer = a.topExpr() as CallExpression
        val outerCallee = outer.callee as MemberAccess
        val middle = outerCallee.obj as CallExpression
        val middleCallee = middle.callee as MemberAccess
        val innerCall = middleCallee.obj as CallExpression

        a.typeOf(innerCall)?.simpleName shouldBe "IgnitorDsl"
    }

    // ── getTypeAt (1-based line/col) ───────────────────────────────────────

    "getTypeAt: position on Osc identifier" {
        val a = analyze("Osc.sine()")
        a.getTypeAt(1, 1)?.simpleName shouldBe "Osc"
    }

    "getTypeAt: multi-line — number literal on line 1" {
        val code = "let x = 42\nOsc.sine()"
        val a = analyze(code)
        a.getTypeAt(1, 9)?.simpleName shouldBe "Number"
    }

    "getTypeAt: multi-line — Osc on line 2" {
        val code = "let x = 42\nOsc.sine()"
        val a = analyze(code)
        a.getTypeAt(2, 1)?.simpleName shouldBe "Osc"
    }

    "getTypeAt: non-expression position returns null" {
        val code = "let x = 42"
        val a = analyze(code)
        a.getTypeAt(1, 1).shouldBeNull()
    }

    "getTypeAt: line 0 (below valid range) returns null" {
        val a = analyze("42")
        a.getTypeAt(0, 1).shouldBeNull()
    }

    "getTypeAt: negative line returns null" {
        val a = analyze("42")
        a.getTypeAt(-1, 1).shouldBeNull()
    }

    "getTypeAt: line past end of file returns null" {
        val a = analyze("42")
        a.getTypeAt(999, 1).shouldBeNull()
    }

    // ── getTypeAtOffset ────────────────────────────────────────────────────

    "getTypeAtOffset: offset 0 on number literal" {
        val a = analyze("42")
        a.getTypeAtOffset(0)?.simpleName shouldBe "Number"
    }

    "getTypeAtOffset: offset past end of source returns null" {
        val a = analyze("42")
        a.getTypeAtOffset(99999).shouldBeNull()
    }

    // ── Nested expressions ─────────────────────────────────────────────────

    "nested: Math.sqrt(42) — call is Number, arg is Number, Math is Math" {
        val a = analyze("Math.sqrt(42)")
        val call = a.topExpr() as CallExpression
        a.typeOf(call)?.simpleName shouldBe "Number"

        val arg = call.arguments.first()
        a.typeOf(arg)?.simpleName shouldBe "Number"

        val callee = call.callee as MemberAccess
        val mathId = callee.obj as Identifier
        a.typeOf(mathId)?.simpleName shouldBe "Math"
    }

    // ── Variable declarations ──────────────────────────────────────────────

    "declaration: let x = 42 — initializer has type Number" {
        val a = analyze("let x = 42")
        val decl = a.ast.statements.first() as LetDeclaration
        val init = decl.initializer.shouldNotBeNull()
        a.typeOf(init)?.simpleName shouldBe "Number"
    }

    "declaration: const y = Osc.sine() — initializer has type IgnitorDsl" {
        val a = analyze("const y = Osc.sine()")
        val decl = a.ast.statements.first() as ConstDeclaration
        a.typeOf(decl.initializer)?.simpleName shouldBe "IgnitorDsl"
    }

    // ── Empty program ──────────────────────────────────────────────────────

    "empty program: getTypeAt returns null" {
        val a = analyze("")
        a.getTypeAt(1, 1).shouldBeNull()
    }

    "empty program: diagnostics are empty" {
        val a = analyze("")
        a.diagnostics.shouldBeEmpty()
    }

    // ── Diagnostics structure ──────────────────────────────────────────────

    "diagnostics: initially empty for valid code" {
        val a = analyze("Osc.sine().lowpass(1000)")
        a.diagnostics.shouldBeEmpty()
    }

    // ── Multiple statements ────────────────────────────────────────────────

    "multi-statement: all expressions are typed" {
        val code = "42\n\"hello\"\nOsc.sine()"
        val a = analyze(code)

        val stmts = a.ast.statements
        val expr1 = (stmts[0] as ExpressionStatement).expression
        val expr2 = (stmts[1] as ExpressionStatement).expression
        val expr3 = (stmts[2] as ExpressionStatement).expression

        a.typeOf(expr1)?.simpleName shouldBe "Number"
        a.typeOf(expr2)?.simpleName shouldBe "String"
        a.typeOf(expr3)?.simpleName shouldBe "IgnitorDsl"
    }

    // ── Binary operation ───────────────────────────────────────────────────

    "binary op: 1 + 2 — operands are typed, result is null (not yet inferred)" {
        val a = analyze("1 + 2")
        val binOp = a.topExpr() as BinaryOperation
        a.typeOf(binOp).shouldBeNull() // inferrer doesn't handle binary ops yet
        a.typeOf(binOp.left)?.simpleName shouldBe "Number"
        a.typeOf(binOp.right)?.simpleName shouldBe "Number"
    }

    // ── Unary operation ────────────────────────────────────────────────────

    "unary op: -42 — operand is typed" {
        val a = analyze("-42")
        val unaryOp = a.topExpr() as UnaryOperation
        a.typeOf(unaryOp.operand)?.simpleName shouldBe "Number"
    }

    "unary op: !true — operand is typed" {
        val a = analyze("!true")
        val unaryOp = a.topExpr() as UnaryOperation
        a.typeOf(unaryOp.operand)?.simpleName shouldBe "Boolean"
    }

    // ── Assignment expression ──────────────────────────────────────────────

    "assignment: x = 42 — value expression is typed" {
        val code = "let x = 1\nx = 42"
        val a = analyze(code)
        val assignStmt = a.ast.statements[1] as ExpressionStatement
        val assign = assignStmt.expression as AssignmentExpression
        a.typeOf(assign.value)?.simpleName shouldBe "Number"
    }

    // ── Ternary expression ─────────────────────────────────────────────────

    "ternary: true ? 42 : \"hello\" — all sub-expressions typed" {
        val a = analyze("true ? 42 : \"hello\"")
        val ternary = a.topExpr() as TernaryExpression
        a.typeOf(ternary.condition)?.simpleName shouldBe "Boolean"
        a.typeOf(ternary.thenExpr)?.simpleName shouldBe "Number"
        a.typeOf(ternary.elseExpr)?.simpleName shouldBe "String"
    }

    // ── Index access ───────────────────────────────────────────────────────

    "index access: arr[0] — obj and index sub-expressions are visited" {
        // Use let + index access since [1,2,3][0] may not parse as IndexAccess at top level
        val code = "let arr = [1,2,3]\narr[0]"
        val a = analyze(code)
        val exprStmt = a.ast.statements[1] as ExpressionStatement
        val indexAccess = exprStmt.expression as IndexAccess
        a.typeOf(indexAccess.index)?.simpleName shouldBe "Number"
    }

    // ── Arrow function ─────────────────────────────────────────────────────

    "arrow function: expression body — body expression is typed" {
        val a = analyze("(x) => 42")
        val arrow = a.topExpr() as ArrowFunction
        val body = arrow.body as ArrowFunctionBody.ExpressionBody
        a.typeOf(body.expression)?.simpleName shouldBe "Number"
    }

    "arrow function: block body — return expression is typed" {
        val a = analyze("(x) => { return 42 }")
        val arrow = a.topExpr() as ArrowFunction
        val body = arrow.body as ArrowFunctionBody.BlockBody
        val retStmt = body.statements.first() as ReturnStatement
        val retVal = retStmt.value.shouldNotBeNull()
        a.typeOf(retVal)?.simpleName shouldBe "Number"
    }

    // ── Control flow: loops ────────────────────────────────────────────────

    "while loop: condition and body are typed" {
        val code = "while (true) { 42 }"
        val a = analyze(code)
        val whileStmt = a.ast.statements.first() as WhileStatement
        a.typeOf(whileStmt.condition)?.simpleName shouldBe "Boolean"
        val bodyExpr = (whileStmt.body.first() as ExpressionStatement).expression
        a.typeOf(bodyExpr)?.simpleName shouldBe "Number"
    }

    "do-while loop: body and condition are typed" {
        val code = "do { 42 } while (true)"
        val a = analyze(code)
        val doWhile = a.ast.statements.first() as DoWhileStatement
        a.typeOf(doWhile.condition)?.simpleName shouldBe "Boolean"
        val bodyExpr = (doWhile.body.first() as ExpressionStatement).expression
        a.typeOf(bodyExpr)?.simpleName shouldBe "Number"
    }

    "for loop: init, condition, update, and body are typed" {
        val code = "for (let i = 0; i < 10; i = i + 1) { 42 }"
        val a = analyze(code)
        val forStmt = a.ast.statements.first() as ForStatement

        // init: let i = 0
        val initDecl = forStmt.init as LetDeclaration
        val initExpr = initDecl.initializer.shouldNotBeNull()
        a.typeOf(initExpr)?.simpleName shouldBe "Number"

        // condition: i < 10
        val cond = forStmt.condition.shouldNotBeNull()
        a.typeOf(cond).shouldBeNull() // binary ops not inferred yet, but operands visited

        // update: i = i + 1
        val update = forStmt.update.shouldNotBeNull()
        val assign = update as AssignmentExpression
        a.typeOf(assign.value).shouldBeNull() // binary op

        // body: 42
        val bodyExpr = (forStmt.body.first() as ExpressionStatement).expression
        a.typeOf(bodyExpr)?.simpleName shouldBe "Number"
    }

    // ── Control flow: else-if chains ───────────────────────────────────────

    "else-if chain: all conditions and branches are typed" {
        val code = "if (true) { 1 } else if (false) { 2 } else { 3 }"
        val a = analyze(code)

        val ifExpr = a.topExpr() as IfExpression
        a.typeOf(ifExpr.condition)?.simpleName shouldBe "Boolean"

        val thenExpr = (ifExpr.thenBranch.first() as ExpressionStatement).expression
        a.typeOf(thenExpr)?.simpleName shouldBe "Number"

        val elseIf = ifExpr.elseBranch as ElseBranch.If
        a.typeOf(elseIf.ifExpr.condition)?.simpleName shouldBe "Boolean"

        val elseIfThen = (elseIf.ifExpr.thenBranch.first() as ExpressionStatement).expression
        a.typeOf(elseIfThen)?.simpleName shouldBe "Number"

        val elseBlock = elseIf.ifExpr.elseBranch as ElseBranch.Block
        val elseExpr = (elseBlock.statements.first() as ExpressionStatement).expression
        a.typeOf(elseExpr)?.simpleName shouldBe "Number"
    }

    // ── Expressions inside control flow ────────────────────────────────────

    "control flow: if condition and branches are typed" {
        val code = "if (true) { 42 } else { \"hello\" }"
        val a = analyze(code)

        val ifExpr = a.topExpr() as IfExpression
        a.typeOf(ifExpr.condition)?.simpleName shouldBe "Boolean"

        val thenStmt = ifExpr.thenBranch.first() as ExpressionStatement
        a.typeOf(thenStmt.expression)?.simpleName shouldBe "Number"

        val elseBranch = ifExpr.elseBranch as ElseBranch.Block
        val elseStmt = elseBranch.statements.first() as ExpressionStatement
        a.typeOf(elseStmt.expression)?.simpleName shouldBe "String"
    }

    // ── Build from existing Program ────────────────────────────────────────

    "build from Program: same results as build from source" {
        val code = "Math.sqrt(42)"
        val program = KlangScriptParser.parse(code)
        val registry = stdlibRegistry()
        val a = AnalyzedAst.build(program, code, registry)

        // Verify top-level type
        a.typeOf(a.topExpr())?.simpleName shouldBe "Number"

        // Also verify a nested expression
        val call = a.topExpr() as CallExpression
        a.typeOf(call.arguments.first())?.simpleName shouldBe "Number"

        val callee = call.callee as MemberAccess
        a.typeOf(callee.obj)?.simpleName shouldBe "Math"
    }

    "build from Program: ast is the same object that was passed in" {
        val code = "42"
        val program = KlangScriptParser.parse(code)
        val a = AnalyzedAst.build(program, code, stdlibRegistry())
        (a.ast === program) shouldBe true
    }

    // ── Registry field ─────────────────────────────────────────────────────

    "registry: stores the registry passed to build" {
        val registry = stdlibRegistry()
        val a = AnalyzedAst.build("42", registry)
        a.registry.get("Osc").shouldNotBeNull()
        a.registry.get("Math").shouldNotBeNull()
    }

    // ── Empty registry ─────────────────────────────────────────────────────

    "empty registry: all types resolve to null for identifiers and calls" {
        val emptyReg = KlangDocsRegistry()
        val a = AnalyzedAst.build("Osc.sine()", emptyReg)
        // With no symbols registered, nothing can be resolved
        a.typeOf(a.topExpr()).shouldBeNull()
    }

    "empty registry: literals still have types" {
        val emptyReg = KlangDocsRegistry()
        val a = AnalyzedAst.build("42", emptyReg)
        a.typeOf(a.topExpr())?.simpleName shouldBe "Number"
    }
})
