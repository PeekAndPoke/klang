# AnalyzedAst + CompletionProvider — COMPLETED 2026-04-02

## What was done

### Phase 1: AnalyzedAst (second-pass type enrichment)

Built `AnalyzedAst` class in `klangscript/src/commonMain/kotlin/intel/` that bundles:

- Parsed `Program` AST
- `AstIndex` for cursor-to-node lookups
- `KlangDocsRegistry` (snapshot, immutable per analysis)
- Pre-computed `Map<Expression, KlangType?>` for all expressions
- `List<AnalyzerDiagnostic>` (empty for now, ready for intellisense)

Type map walker exhaustively visits all Expression and Statement subtypes.
Built once per code change via `AnalyzedAst.build(source, registry)`.

**Tests:** 48 tests in `AnalyzedAstTest.kt` covering literals, identifiers, method calls, chains, unknown symbols, chain
breakage, getTypeAt, getTypeAtOffset, binary/unary ops, assignments, ternary, index access, arrow functions, loops,
else-if chains, empty programs, empty registry, AST identity, registry field.

### Phase 2: CompletionProvider (extracted from JS-only code)

Built `CompletionProvider` in `klangscript/src/commonMain/kotlin/intel/` with:

- `topLevelCompletions(prefix)` — objects, properties, top-level functions
- `memberCompletions(receiverType, prefix)` — methods/properties on a receiver type
- `importCompletions(availableLibraries)` — library name suggestions

Returns `CompletionSuggestion` data objects (pure Kotlin, no editor dependencies).

**Tests:** 46 tests in `CompletionProviderTest.kt` organized in 6 categories:

1. Top-level completions (9 tests)
2. Member/chain completions (12 tests)
3. Multi-library merge (7 tests)
4. Import completions (4 tests)
5. Negative/suppression (6 tests)
6. Alias edge cases (5 tests)

### Phase 3: Editor integration

- `EditorDocContext` now builds `AnalyzedAst` on each parse and exposes `lastAnalysis`
- `DslCompletionSource` uses `analysis.typeOf(node)` for receiver inference (O(1) lookup)
- `DslEditorExtension` uses `analysis.typeOf(memberAccess.obj)` for hover receiver resolution
- `ArgFinder` uses `analysis.typeOf(callee.obj)` for tool badge receiver resolution
- Zero `ExpressionTypeInferrer` imports remain in `klangscript-ui`
- Registry passed as `snapshot()` to prevent stale mutation issues

### Phase 4: Comprehensive test suite (Six Hats analysis)

Six Thinking Hats analysis identified edge cases across 6 categories. All implemented and passing.

### Also fixed

- Horizontal scroll overlay bug in `CodeMirrorHighlightBuffer` (double-scrolling)

## Files created/modified

| File                                                                 | Action                |
|----------------------------------------------------------------------|-----------------------|
| `klangscript/src/commonMain/kotlin/intel/AnalyzedAst.kt`             | NEW                   |
| `klangscript/src/commonMain/kotlin/intel/AnalyzerDiagnostic.kt`      | NEW                   |
| `klangscript/src/commonMain/kotlin/intel/CompletionProvider.kt`      | NEW                   |
| `klangscript/src/jvmTest/kotlin/intel/AnalyzedAstTest.kt`            | NEW                   |
| `klangscript/src/jvmTest/kotlin/intel/CompletionProviderTest.kt`     | NEW                   |
| `klangscript-ui/src/jsMain/kotlin/codemirror/EditorDocContext.kt`    | MODIFIED              |
| `klangscript-ui/src/jsMain/kotlin/codemirror/DslCompletionSource.kt` | MODIFIED              |
| `klangscript-ui/src/jsMain/kotlin/codemirror/DslEditorExtension.kt`  | MODIFIED              |
| `klangscript-ui/src/jsMain/kotlin/codemirror/ArgFinder.kt`           | MODIFIED              |
| `klangscript-ui/src/jsMain/kotlin/codemirror/CodeMirrorComp.kt`      | MODIFIED              |
| `src/jsMain/kotlin/codemirror/CodeMirrorHighlightBuffer.kt`          | MODIFIED (scroll fix) |
