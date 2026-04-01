# Receiver-Aware Hover Docs & Code Completion — DONE

**Status**: Implemented (2026-03-30)
**Related**: `klangscript-intellisense.md` (next phase — full diagnostics)

## What Was Built

### Foundation (klangscript module, `intel/` package)

- **`ExpressionTypeInferrer`** — walks AST call chains, resolves types from docs metadata (receiver + return types)
- **`KlangDocsRegistry` merge** — variants from multiple libraries coexist; receiver-aware lookups (`getCallable`,
  `getVariantsForReceiver`, `getSymbolWithReceiver`)
- **`AstIndex` public API** — `nodeAt()`, `parentOf()`, `offsetOf()` exposed for editor use
- **Per-variant `library` field** — `KlangDecl.library` on both `KlangCallable` and `KlangProperty`, set by both KSP
  generators
- **`KlangDocsRegistry.snapshot()`** — immutable copy for library building
- **Docs-aware Builder API** — `registerExtensionMethodWithEngine(..., docs=KlangCallable(...))` and friends on
  `KlangScriptLibrary.Builder`
- **`KlangCallable.toSymbol()`** helper

### Editor Integration (klangscript-ui module)

- **Hover docs** — detects MemberAccess context via AST (handles CallExpression wrapping), infers receiver type, shows
  correct variant
- **Code completion** — dot-context filters to matching receiver; top-level shows only top-level symbols; detail shows
  `library · category` or `library · receiverType`
- **ArgFinder** — receiver-aware variant selection for tool badges
- **Off-by-one fix** — `nodeAt(dotPos - 1)` for stale AST during completion

### KSP Changes

- **Object-level symbols** — `@KlangScript.Object("Osc")` auto-generates `KlangProperty` in docs
- **Auto-register docs** — generated `registerXxxGenerated()` auto-calls `docs { registerAll(...) }` when called from a
  library builder
- **Per-variant library** — both stdlib and sprudel KSP generators set `library` on every generated callable/property

### Test Coverage

- `ExpressionTypeInferrerTest` — 18 unit tests (all expression types, chain breakage, property access)
- `ExpressionTypeInferrerE2eTest` — 18 e2e tests (real parser → AstIndex → inference, multiline, sprudel chains)
- `StdlibDocsInferenceTest` — 22 tests (real generated docs, chain resolution, merge, builder API)
- `KlangDocsRegistryTest` — 15 tests (merge, receiver lookups, library propagation, snapshot)
- `GeneratedRegistrationTest` — existing + 5 new (object symbols, library docs)
