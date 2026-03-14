# KlangScript IntelliSense — Semantic Analysis Plan

## Goal

Add IntelliSense-like semantic checking to the CodeMirror editor: validate function calls
against the imported libraries' documentation, checking that functions exist, are called in
the right context (top-level vs on receiver), and receive valid arguments.

## What We Have

| Piece                                                   | Location                         | Status         |
|---------------------------------------------------------|----------------------------------|----------------|
| AST with `CallExpression`, `MemberAccess`, `Identifier` | `klangscript/ast/Ast.kt`         | exists         |
| `KlangScriptParser.parse(code)`                         | `klangscript/parser/`            | exists         |
| `KlangSymbol` with `KlangCallable` variants             | `klangscript/types/`             | exists         |
| `KlangCallable.receiver: KlangType?`                    | generated docs                   | populated      |
| `KlangCallable.returnType: KlangType?`                  | generated docs                   | populated      |
| `EditorDocContext` — parses imports, builds registry    | `codemirror/EditorDocContext.kt` | exists         |
| CodeMirror linter integration                           | `CodeMirrorComp.kt`              | exists (empty) |

### Type Landscape (Strudel)

| Type                       | Role                                                    |
|----------------------------|---------------------------------------------------------|
| `StrudelPattern`           | Main pattern type — most methods return this            |
| `String`                   | String literals — many DSL functions accept as receiver |
| `PatternMapperFn`          | Mapper functions — chainable transformers               |
| `PatternLike` (type alias) | Parameter type — accepts strings, numbers, patterns     |

### Variant Structure per Symbol

Each `KlangSymbol` has multiple `KlangCallable` variants:

- **No receiver** (`receiver = null`) → top-level function: `note("c3")`
- **receiver = StrudelPattern** → method: `pattern.note("c3")`
- **receiver = String** → string extension: `"c3".note()`
- **receiver = PatternMapperFn** → mapper chain: `mapper.note("c3")`

Plus `KlangProperty` for top-level objects (`sine`, `rand`, `perlin`).

## Architecture

### Core: `KlangScriptAnalyzer`

A new class in `commonMain` (usable from both JVM tests and JS editor) that walks the AST
and produces diagnostics.

```
KlangScriptAnalyzer
  ├── analyze(program: Program, registry: KlangDocsRegistry) → List<Diagnostic>
  ├── inferType(expr: Expression) → KlangType?
  └── checkCall(call: CallExpression, receiverType: KlangType?) → Diagnostic?
```

### Type Inference (simplified)

Walk expressions bottom-up to infer types:

| Expression                                         | Inferred Type                                                                |
|----------------------------------------------------|------------------------------------------------------------------------------|
| `StringLiteral`                                    | `String`                                                                     |
| `NumberLiteral`                                    | `Number`                                                                     |
| `Identifier("sine")`                               | look up in registry → `KlangProperty.type`                                   |
| `Identifier("note")`                               | look up → top-level function                                                 |
| `CallExpression(Identifier("note"), args)`         | `KlangCallable.returnType` where `receiver = null`                           |
| `CallExpression(MemberAccess(expr, "gain"), args)` | infer type of `expr` → find `gain` variant with that receiver → `returnType` |
| `MemberAccess(expr, "property")`                   | not a call → check if property exists on inferred type                       |

### Checks to Perform

#### Tier 1 — Unknown symbols (high value, low complexity)

- **Unknown top-level function**: `foo("x")` where `foo` has no variant with `receiver = null`
- **Unknown method**: `note("c3").foo()` where `foo` has no variant with `receiver = StrudelPattern`
- **Unknown identifier**: bare `foo` that's not in the registry at all

#### Tier 2 — Wrong context (medium complexity)

- **Method used as top-level**: `gain(0.5)` — `gain` exists but only with a receiver,
  no top-level variant → "gain() must be called on a pattern, e.g. note('c3').gain(0.5)"
- **Top-level used as method**: `note("c3").note("d3")` — if `note` as a method on
  StrudelPattern has different semantics, this is valid. Check receiver compatibility.

#### Tier 3 — Argument validation (higher complexity)

- **Wrong argument count**: `note("c3", "d3", "e3")` when `note` expects 1 param
  (but many strudel functions are vararg, so check `isVararg`)
- **Type mismatch**: harder — `PatternLike` accepts almost anything

### Integration with Editor

The analyzer produces a list of diagnostics with source locations. These feed into
CodeMirror's existing linter extension (currently empty in `KlangScriptEditorComp`).

```kotlin
// In KlangScriptEditorComp, replace the empty linter:
val linterSource: (EditorView) -> Array<Diagnostic> = { view ->
    val code = view.state.doc.toString()
    analyzer.analyze(code).toTypedArray()
}
```

The analysis runs on the same debounced parse that `EditorDocContext` already does —
extend it to also run the analyzer when imports change or code changes.

## Implementation Plan

### Step 1: `KlangScriptAnalyzer` (commonMain)

Create `klangscript/src/commonMain/kotlin/analyzer/KlangScriptAnalyzer.kt`:

- Input: `Program` AST + `KlangDocsRegistry`
- Walk all `ExpressionStatement` nodes
- For each `CallExpression`, resolve the callee:
    - `Identifier(name)` → top-level call → check `registry.get(name)` has a variant with `receiver = null`
    - `MemberAccess(obj, name)` → method call → infer type of `obj`, check variant with matching receiver
- Return list of `AnalyzerDiagnostic(message, location, severity)`

### Step 2: Type inference

Add `inferType(expr: Expression): KlangType?`:

- `StringLiteral` → `KlangType("String")`
- `NumberLiteral` → `KlangType("Number")`
- `Identifier(name)` → look up property type in registry
- `CallExpression` → find matching callable variant → `returnType`
- `MemberAccess` → defer to call resolution (most member accesses are part of call chains)

This enables chained validation: `note("c3").gain(0.5).foo()` — infer `note()` returns
`StrudelPattern`, `gain()` on `StrudelPattern` returns `StrudelPattern`, `foo()` on
`StrudelPattern` → not found → error.

### Step 3: Wire into editor

Extend `EditorDocContext` or create a sibling `EditorAnalysisContext`:

- On code change (debounced), run analyzer
- Convert `AnalyzerDiagnostic` → CodeMirror `Diagnostic`
- Feed into the linter extension

### Step 4: Context-aware completion (enhancement)

Use the same type inference to improve code completion:

- After typing `.` on a `StrudelPattern` expression → only show methods with
  `receiver = StrudelPattern`, not top-level functions
- At top level → only show functions with `receiver = null` and properties

### Step 5: Tests (commonMain)

Test the analyzer on JVM with known code snippets:

```kotlin
"analyzer flags unknown top-level function" {
    val code = """import * from "strudel"; foo("x")"""
    val diagnostics = analyzer.analyze(code, registry)
    diagnostics shouldHaveSize 1
    diagnostics[0].message shouldContain "foo"
}

"analyzer accepts valid chain" {
    val code = """import * from "strudel"; note("c3").gain(0.5)"""
    val diagnostics = analyzer.analyze(code, registry)
    diagnostics shouldHaveSize 0
}
```

## Challenges & Decisions

| Challenge                                        | Approach                                                                            |
|--------------------------------------------------|-------------------------------------------------------------------------------------|
| Variables (`let x = note("c3"); x.gain(0.5)`)    | Phase 1: skip variable tracking. Phase 2: simple single-assignment inference        |
| Functions returning different types per overload | Use first matching variant's returnType                                             |
| `PatternLike` accepts almost anything            | Don't validate argument types initially, just counts                                |
| Parse errors in incomplete code                  | Analyzer silently skips unparseable code (same as EditorDocContext)                 |
| Performance on large files                       | Cache analysis results alongside import cache in EditorDocContext                   |
| String extensions (`"c3".note()`)                | Infer `String` type for string literals, match against `receiver = String` variants |

## Incremental Rollout

1. **v1**: Unknown function/method warnings only (Tier 1)
2. **v2**: "Wrong context" warnings (Tier 2) + context-aware completion
3. **v3**: Argument count validation (Tier 3)
4. **v4**: Variable type tracking

## Key Files

| File                                                                | Role                            |
|---------------------------------------------------------------------|---------------------------------|
| `klangscript/src/commonMain/kotlin/analyzer/KlangScriptAnalyzer.kt` | Core analyzer (new)             |
| `klangscript/src/commonMain/kotlin/analyzer/TypeInference.kt`       | Type inference (new)            |
| `klangscript/src/commonTest/kotlin/analyzer/AnalyzerSpec.kt`        | Tests (new)                     |
| `src/jsMain/kotlin/codemirror/EditorDocContext.kt`                  | Trigger analysis on code change |
| `src/jsMain/kotlin/codemirror/CodeMirrorComp.kt`                    | Feed diagnostics to linter      |
| `klangscript/src/commonMain/kotlin/ast/Ast.kt`                      | AST nodes                       |
| `klangscript/src/commonMain/kotlin/docs/KlangDocsRegistry.kt`       | Symbol lookup                   |
