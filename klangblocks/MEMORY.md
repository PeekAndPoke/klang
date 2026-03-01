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

## Key Rules

1. Every new feature **must** pass the 6-step round-trip test (see `ref/round-trip-testing.md`)
2. Every new block feature must correspond to a KlangScript language feature (see `klangscript/ref/adding-features.md`)
