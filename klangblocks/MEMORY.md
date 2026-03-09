# KlangBlocks — Memory

## Current Status

- Visual block editor for KlangScript (Kotlin Multiplatform, JVM + JS)
- Core pipeline implemented: parse → blocks → code generation
- `CodeGenResult` tracks per-block and per-slot-content char ranges for editor hit-testing
- DnD fully refactored: `DropAction` / `DropDestination` / `KBProgramEditingCtx.execute()`
- All cases from `ref/dnd-behaviour.md` implemented (P1–P5, A1–A6, B1–B6, C1)

## Architecture Decisions

- **Immutable model**: `KBProgram` is replaced wholesale on every edit; previous version pushed onto undo stack
- **IDs are session-local**: `KBCallBlock.id` (and other `KBStmt.id`) are random UUIDs generated at
  `AstToKBlocks.convert()` time; do not persist across parse/convert cycles
- **Fallback encoding**: `ObjectLiteral`, `ArrayLiteral`, and unrecognised expressions fall back to
  `KBStringArg(toSourceString())` — opaque but round-trip-safe
- **`KBPocketLayout`**: detected from source line positions during AST→blocks; `VERTICAL` if any arg is on a different
  line than the call
- **`KBPocketLayout.VERTICAL` only affects arg rendering** inside the block's `()` — it does NOT force `\n  .` chain
  separators; only a `KBNewlineHint` between steps does that

## Lessons Learned

- Round-trip equality must be checked at the **AST level** (not string or KBProgram level) because IDs are regenerated
  and whitespace may vary
- `findAt()` resolves **innermost** (smallest) range for nested blocks — critical for correct editor hit-testing
- Blank lines are preserved as `KBBlankLine` nodes based on line-gap detection between consecutive statements
- **`NullLiteral` is a singleton `object`** — its `location` is always `null`. `layoutForLink` must treat
  null-location args as same-line (HORIZONTAL), not as cross-line (VERTICAL). Fixed in `AstToKBlocks.kt`.
- **`KBLetStmt`/`KBConstStmt` code gen** initially omitted the value — only the name was emitted.
  Fixed by adding `" = ${value.toCode()}"` in `KBCodeGen.kt`.

## DnD Architecture

- **`DropDestination`** sealed interface: `RowGap`, `ChainEnd`, `ChainInsert`, `EmptySlot`, `ReplaceBlock`
- **`DropAction`**: `CreateBlock`, `MoveBlocks`, `MoveRow`, `Compound`
- **Clone-before-remove**: all `moveBlocks*` operations clone blocks with fresh UUIDs before removing originals (
  eliminates same-ID edge cases)
- **No string-literal preservation**: dropping into a slot always creates a plain nested chain (old string arg is
  discarded)
- **`ReplaceBlock`**: replaces target block in-place; includes self-replace guard (early return) and cycle guard (
  `blockContains`)
- **`sourceChainId` in `DndState`**: drop zones belonging to the source chain are suppressed during drag
- **`DropTarget.ReplaceBlock`** on each block shows white outline ring; fires `DropDestination.ReplaceBlock` on mouseUp

## Test Infrastructure

- **`RoundTripSupport.kt`** — shared test helper in `jvmTest/kotlin/model/`
  - `roundTrip(source)` runs all 6 steps, wraps each in a `try/catch` that names the failing step
  - Returns `RoundTripResult(source, originalAst, blocks, generatedCode, resultAst)`
  - `result.shouldRoundTrip()` asserts AST equality with `source` + `generatedCode` in the error message
  - `result.shouldRoundTripWithCode()` additionally asserts `generatedCode == source` (use for canonical sources)
- **DnD test split** — `jvmTest/kotlin/dnd/`:
    - `DropActionSupport.kt` — `ctx()`, `code()`, `rowCount()`, `block()`, `chain()` (recursive at all depths), `tail()`
      helpers
    - `DropActionTest.kt` — P1–P4, A1–A5, B1–B5, C1, Compound, Undo/Redo + deep-nesting variants + A4/B4 self-insert (40
      tests)
    - `ReplaceBlockTest.kt` — P5, A6, B6 + deep-nesting variants at levels 2 and 3 (15 tests)
    - All tests cover at least 3-level nested scenarios to verify recursive tree-walking

## Key Rules

1. Every new feature **must** pass the 6-step round-trip test (see `ref/round-trip-testing.md`)
2. Every new block feature must correspond to a KlangScript language feature (see `klangscript/ref/adding-features.md`)
3. Use `RoundTripSupport.roundTrip()` (not a local helper) in all new round-trip tests
