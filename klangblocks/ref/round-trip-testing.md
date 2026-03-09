# KlangBlocks — Round-Trip Testing

## The 6-Step Round-Trip Requirement

Every implemented block feature **must** pass a round-trip test:

```
1. Write KlangScript source code
2. Parse → originalAst  (KlangScriptParser.parse())
3. Convert originalAst → blocks  (AstToKBlocks.convert())
4. Convert blocks → code  (KBProgram.toCode())
5. Parse generated code → resultAst  (KlangScriptParser.parse())
6. Assert: originalAst == resultAst
```

**Why**: guarantees that the AST→blocks→code pipeline is lossless for every supported construct.

## Test Template

```kotlin
class MyFeatureRoundTripTest : StringSpec({

    fun roundTrip(source: String): Program {
        val originalAst = KlangScriptParser.parse(source)
        val blocks      = AstToKBlocks.convert(originalAst)
        val generated   = blocks.toCode()
        return KlangScriptParser.parse(generated)
    }

    "my feature should round-trip" {
        val source = """
            note("c3 e3").gain(0.5)
        """.trimIndent()
        roundTrip(source) shouldBe KlangScriptParser.parse(source)
    }
})
```

## Test Location

Place round-trip tests in:

```
klangblocks/src/jvmTest/kotlin/model/
```

Convention: `<Feature>RoundTripTest.kt`

## What to Test

For each new block feature, write tests in two categories:

### 1. Round-trip (AST fidelity)

1. **Basic case** — the happy path
2. **Nested chains** — `note(cat("c3", "e3"))` as nested arg
3. **String/identifier heads** — `"C4".transpose(1)`, `sine.range(0.25, 0.75)`
4. **Vertical layout** — multi-line argument lists
5. **Edge cases** — empty args, blank lines between statements

### 2. Source map (live feedback fidelity)

Every implemented feature **must also** verify that `CodeGenResult.findAt()` returns correct hits.
Use `toCodeGen()` (not `toCode()`) and test:

- **Function name position** → `HitResult(blockId, slotIndex=null, offsetInSlot=null)`
- **Opening/closing quote** → `slotIndex=null` (quote characters are not part of the content range)
- **Inside string content** → correct `slotIndex` and `offsetInSlot`
- **Non-string arg content** (numbers, bools) → `slotIndex=null`
- **Chain separator** (`.`) → `null` (not inside any block)
- **Past end of code** → `null`
- **Nested block** → resolves to the innermost block's id

**Source map test template:**

```kotlin
private fun compile(source: String): Pair<KBProgram, CodeGenResult> {
    val ast     = KlangScriptParser.parse(source)
    val program = AstToKBlocks.convert(ast)
    return program to program.toCodeGen()
}

private fun KBProgram.allBlocks(): List<KBCallBlock> { /* depth-first, incl. nested */ }

"my feature: source map resolves function name" {
    val src = """note("c3")"""
    val (program, result) = compile(src)
    val noteId = program.allBlocks().first { it.funcName == "note" }.id
    result.findAt(1, 1)!!.let { hit ->
        hit.blockId shouldBe noteId
        hit.slotIndex shouldBe null
    }
}

"my feature: source map resolves slot content" {
    val src = """note("c3")"""
    val (program, result) = compile(src)
    val noteId = program.allBlocks().first { it.funcName == "note" }.id
    // col 7 = '"' (opening quote) → outside content range
    result.findAt(1, 7)!!.slotIndex shouldBe null
    // col 8 = 'c' → first char of slot 0 content
    result.findAt(1, 8)!!.let { hit ->
        hit.blockId shouldBe noteId
        hit.slotIndex shouldBe 0
        hit.offsetInSlot shouldBe 0
    }
}
```

See `KBCodeGenTest.kt` for the full set of patterns.

## Running Tests

```bash
./gradlew :klangblocks:jvmTest
./gradlew :klangblocks:jvmTest --tests MyFeatureRoundTripTest
```

## Common Failure Causes

- **Fallback to `KBStringArg(toSourceString())`** — expression not handled by `extractChain`; the fallback re-serialises
  correctly but the inner structure is opaque
- **ID mismatch** — IDs are regenerated on every `convert()` call; never compare `KBProgram` instances directly for
  round-trip equality; compare the **ASTs** (step 6), not the blocks
- **Whitespace differences** — parser location info affects blank-line detection; normalise by comparing parsed ASTs,
  not generated strings
- **Multiline vs single-line strings** — `KBStringArg` with `\n` inside uses backtick quoting; ensure the parser can
  parse both `"..."` and `` `...` ``
