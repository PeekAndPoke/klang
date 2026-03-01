# KlangBlocks — Memory

## Current Status

- Visual block editor for KlangScript (Kotlin Multiplatform, JVM + JS)
- Core pipeline implemented: parse → blocks → code generation
- `CodeGenResult` tracks per-block and per-slot-content char ranges for editor hit-testing

## Architecture Decisions

- **Immutable model**: `KBProgram` is replaced wholesale on every edit; previous version pushed onto undo stack
- **IDs are session-local**: `KBCallBlock.id` (and other `KBStmt.id`) are random UUIDs generated at
  `AstToKBlocks.convert()` time; do not persist across parse/convert cycles
- **Fallback encoding**: `ObjectLiteral`, `ArrayLiteral`, and unrecognised expressions fall back to
  `KBStringArg(toSourceString())` — opaque but round-trip-safe
- **`KBPocketLayout`**: detected from source line positions during AST→blocks; `VERTICAL` if any arg is on a different
  line than the call

## Lessons Learned

- Round-trip equality must be checked at the **AST level** (not string or KBProgram level) because IDs are regenerated
  and whitespace may vary
- `findAt()` resolves **innermost** (smallest) range for nested blocks — critical for correct editor hit-testing
- Blank lines are preserved as `KBBlankLine` nodes based on line-gap detection between consecutive statements
- **`NullLiteral` is a singleton `object`** — its `location` is always `null`. `layoutForLink` must treat
  null-location args as same-line (HORIZONTAL), not as cross-line (VERTICAL). Fixed in `AstToKBlocks.kt`.
- **`KBLetStmt`/`KBConstStmt` code gen** initially omitted the value — only the name was emitted.
  Fixed by adding `" = ${value.toCode()}"` in `KBCodeGen.kt`.

## Test Infrastructure

- **`RoundTripSupport.kt`** — shared test helper in `jvmTest/kotlin/model/`
  - `roundTrip(source)` runs all 6 steps, wraps each in a `try/catch` that names the failing step
  - Returns `RoundTripResult(source, originalAst, blocks, generatedCode, resultAst)`
  - `result.shouldRoundTrip()` asserts AST equality with `source` + `generatedCode` in the error message

## Key Rules

1. Every new feature **must** pass the 6-step round-trip test (see `ref/round-trip-testing.md`)
2. Every new block feature must correspond to a KlangScript language feature (see `klangscript/ref/adding-features.md`)
3. Use `RoundTripSupport.roundTrip()` (not a local helper) in all new round-trip tests
