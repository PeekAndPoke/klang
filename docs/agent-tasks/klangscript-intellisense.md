# KlangScript IntelliSense — Full Diagnostics & Web Worker Plan

## Goal

Add real-time semantic diagnostics to the CodeMirror editor: highlight unknown functions,
wrong-context calls, and argument errors as the user types. Run analysis in a **web worker**
to avoid blocking the main thread.

## Foundation Already Built

The receiver-aware code completion work (see `receiver-aware-editor.md` — DONE) provides:

| Component                  | Location                | What It Does                                         |
|----------------------------|-------------------------|------------------------------------------------------|
| `ExpressionTypeInferrer`   | `klangscript/intel/`    | Infers types through call chains using docs metadata |
| `KlangDocsRegistry`        | `klangscript/docs/`     | Merged registry with receiver-aware lookups          |
| `AstIndex`                 | `klangscript/ast/`      | O(log n) cursor-to-node lookup, public API           |
| `KlangScriptParser`        | `klangscript/parser/`   | Full recursive-descent parser                        |
| CodeMirror linter bindings | `klangjs/.../Lint.kt`   | `Diagnostic`, `linter()`, `setDiagnostics()`         |
| Linter extension           | `CodeMirrorComp.kt:112` | Wired up but source returns `[]`                     |
| `setErrors()`              | `CodeMirrorComp.kt:228` | Converts `EditorError` → `Diagnostic` and renders    |

**All analyzer code lives in `commonMain` (Kotlin Multiplatform)** — already compiles to both JVM (for tests) and JS (
for browser/worker).

## Type Landscape

| Type                                   | Source   | Role                                                    |
|----------------------------------------|----------|---------------------------------------------------------|
| `SprudelPattern`                       | sprudel  | Main pattern type — most DSL methods return this        |
| `String`                               | built-in | String literals — many DSL functions accept as receiver |
| `PatternMapperFn`                      | sprudel  | Mapper functions — chainable transformers               |
| `IgnitorDsl`                           | stdlib   | Oscillator signal graph builder                         |
| `Number`, `Boolean`, `Array`, `Object` | built-in | Primitive types                                         |
| `Osc`, `Math`                          | stdlib   | Singleton objects                                       |

## Architecture

```
┌──────────────────────────────────────────────────────────┐
│  Main Thread                                             │
│                                                          │
│  CodeMirror Editor                                       │
│    ↓ onCodeChanged (debounced)                           │
│  EditorDocContext                                         │
│    ↓ postMessage({ code, registrySnapshot })             │
│  ┌────────────────────────────────────┐                  │
│  │  Web Worker (klang-analyzer.js)    │                  │
│  │                                    │                  │
│  │  KlangScriptParser.parse(code)     │                  │
│  │    ↓                               │                  │
│  │  KlangScriptAnalyzer.analyze(      │                  │
│  │    program, registry               │                  │
│  │  )                                 │                  │
│  │    ↓                               │                  │
│  │  List<AnalyzerDiagnostic>          │                  │
│  │    ↓ postMessage(diagnostics)      │                  │
│  └────────────────────────────────────┘                  │
│    ↓                                                     │
│  setDiagnostics(view, diagnostics)                       │
│    ↓                                                     │
│  CodeMirror renders squiggles + gutter markers           │
└──────────────────────────────────────────────────────────┘
```

### Phase 1: Main-thread analyzer (MVP)

Run the analyzer synchronously on the main thread, debounced. This is the simplest path
to get diagnostics working. Move to a web worker in Phase 3 if performance becomes an issue.

### Phase 2: Full diagnostic tiers

### Phase 3: Web worker offloading

---

## Diagnostic Tiers

### Tier 1 — Unknown symbols (high value, low complexity)

| Check                      | Example            | Message                                   |
|----------------------------|--------------------|-------------------------------------------|
| Unknown top-level function | `foo("x")`         | `Unknown function 'foo'`                  |
| Unknown method on type     | `note("c3").foo()` | `'foo' is not a method on SprudelPattern` |
| Unknown identifier         | bare `foo`         | `Unknown identifier 'foo'`                |

**Implementation**: Walk all `ExpressionStatement` nodes. For each `CallExpression`, resolve the callee via
`ExpressionTypeInferrer`. If the callable/identifier is not found in the registry, emit a diagnostic.

### Tier 2 — Wrong context (medium complexity)

| Check                    | Example           | Message                                                          |
|--------------------------|-------------------|------------------------------------------------------------------|
| Method used as top-level | `gain(0.5)`       | `'gain' must be called on a receiver, e.g. note("c3").gain(0.5)` |
| Receiver type mismatch   | `Math.note("c3")` | `'note' is not a method on Math`                                 |

**Implementation**: When a symbol IS found but has no variant matching the call context (no receiver for top-level,
wrong receiver for method call), emit a context-specific diagnostic with a suggestion.

### Tier 3 — Argument validation (higher complexity)

| Check              | Example          | Message                                |
|--------------------|------------------|----------------------------------------|
| Too few arguments  | `note()`         | `'note' expects at least 1 argument`   |
| Too many arguments | `Math.abs(1, 2)` | `'Math.abs' expects 1 argument, got 2` |

**Implementation**: After resolving the callable, compare `args.size` against `params.size` (respecting `isVararg`).
Skip type checking — `PatternLike` accepts almost anything.

### Tier 4 — Variable type tracking (future)

| Check                 | Example                                   | What's needed                                        |
|-----------------------|-------------------------------------------|------------------------------------------------------|
| Variable inference    | `let x = Osc.sine(); x.lowpass(1000)`     | Track `let`/`const` initializer types in a scope map |
| Reassignment          | `let x = note("c3"); x = 42; x.gain(0.5)` | Track latest assignment type                         |
| Function return types | `const f = (x) => x.gain(0.5)`            | Infer arrow function return types                    |

---

## Implementation Plan

### Step 1: `KlangScriptAnalyzer` (commonMain)

**New file**: `klangscript/src/commonMain/kotlin/intel/KlangScriptAnalyzer.kt`

```kotlin
data class AnalyzerDiagnostic(
    val message: String,
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int,
    val severity: DiagnosticSeverity,
)

enum class DiagnosticSeverity { ERROR, WARNING, INFO, HINT }

class KlangScriptAnalyzer(private val registry: KlangDocsRegistry) {

    private val inferrer = ExpressionTypeInferrer(registry)

    fun analyze(program: Program): List<AnalyzerDiagnostic> {
        val diagnostics = mutableListOf<AnalyzerDiagnostic>()
        for (stmt in program.statements) {
            analyzeStatement(stmt, diagnostics)
        }
        return diagnostics
    }

    private fun analyzeStatement(stmt: Statement, out: MutableList<AnalyzerDiagnostic>) {
        when (stmt) {
            is ExpressionStatement -> analyzeExpression(stmt.expression, out)
            is LetDeclaration -> stmt.initializer?.let { analyzeExpression(it, out) }
            is ConstDeclaration -> analyzeExpression(stmt.initializer, out)
            // ... other statement types with expressions
        }
    }

    private fun analyzeExpression(expr: Expression, out: MutableList<AnalyzerDiagnostic>) {
        when (expr) {
            is CallExpression -> checkCall(expr, out)
            is MemberAccess -> checkMemberAccess(expr, out)
            // recurse into sub-expressions
        }
    }
}
```

Reuses the existing `ExpressionTypeInferrer` for type resolution.

### Step 2: Wire into editor (main thread, debounced)

**File**: `klangscript-ui/.../EditorDocContext.kt` or new `EditorAnalysisContext.kt`

- On code change (debounced 500ms — longer than the 300ms import debounce), run analyzer
- Convert `AnalyzerDiagnostic` → CodeMirror `Diagnostic` (line/col → offset)
- Call `setDiagnostics(view, diagnostics)` to update the editor

**File**: `klangscript-ui/.../CodeMirrorComp.kt`

- Replace the empty linter source with one that reads from the analysis context
- OR use `setDiagnostics()` directly (simpler, avoids the linter callback model)

### Step 3: Web worker offloading

#### Why a web worker?

The analyzer parses + walks the full AST on every change. For large files (500+ lines) this could
cause ~50-100ms jank on the main thread. A web worker keeps the editor responsive.

#### Build strategy

Follow the `audio_jsworklet` pattern:

1. **New module**: `klangscript-worker/`
  - Depends on `klangscript` (parser, analyzer, types)
  - Compiles to a standalone JS bundle via Webpack
  - Entry point: `init.kt` with `self.onmessage` handler

2. **Webpack config** (like `audio_jsworklet/webpack.config.d/worklet.js`):
   ```javascript
   config.output.globalObject = "(typeof self !== 'undefined' ? self : globalThis)"
   config.optimization.runtimeChunk = false
   config.optimization.splitChunks = false
   ```

3. **Build integration** (root `build.gradle.kts`):
   ```kotlin
   val copyWorkerProd by registering(Copy::class) {
       dependsOn(":klangscript-worker:jsBrowserProductionWebpack")
       from(project(":klangscript-worker").layout.buildDirectory.dir("..."))
       into(layout.projectDirectory.dir("src/jsMain/resources"))
       include("klang-analyzer.js", "klang-analyzer.js.map")
   }
   ```

#### Worker protocol

```kotlin
// Main thread → Worker
sealed class AnalyzerRequest {
    data class Analyze(val code: String, val registryJson: String) : AnalyzerRequest()
    data class UpdateRegistry(val registryJson: String) : AnalyzerRequest()
}

// Worker → Main thread
sealed class AnalyzerResponse {
    data class Diagnostics(val items: List<AnalyzerDiagnostic>) : AnalyzerResponse()
}
```

#### Registry serialization

The `KlangDocsRegistry` needs to be serialized to the worker. Options:

- **JSON**: Serialize symbols map to JSON, deserialize in worker. Simple but potentially large.
- **Build-time**: Bake the registry into the worker bundle at compile time (like how generated docs are compiled in).
  Faster startup, but requires rebuild when docs change.
- **Hybrid**: Bake generated docs into the worker, send only runtime-registered docs (manual registrations) via
  postMessage.

**Recommended**: Build-time baking. The worker module depends on `klangscript` which already has
`generatedStdlibDocs`. The worker can import these directly. Only the set of imported library names
needs to be sent via postMessage.

#### Cancellation

When the user types faster than the analyzer runs, stale analyses should be discarded:

- Each `Analyze` request carries a `version: Int` (monotonically increasing)
- The worker includes the version in its response
- Main thread ignores responses where `version < latestSentVersion`

#### Fallback

If the Worker fails to load (e.g., CSP restrictions), fall back to main-thread analysis with
longer debounce (1000ms).

### Step 4: Tests (commonMain)

All analyzer tests run on JVM (fast, no browser needed):

```kotlin
"analyzer flags unknown top-level function" {
    val code = """foo("x")"""
    val diagnostics = analyzer.analyze(KlangScriptParser.parse(code))
    diagnostics shouldHaveSize 1
    diagnostics[0].message shouldContain "foo"
}

"analyzer accepts valid sprudel chain" {
    val code = """note("c3").gain(0.5)"""
    val diagnostics = analyzer.analyze(KlangScriptParser.parse(code))
    diagnostics shouldHaveSize 0
}

"analyzer flags unknown method on known type" {
    val code = """note("c3").unknownFn()"""
    val diagnostics = analyzer.analyze(KlangScriptParser.parse(code))
    diagnostics shouldHaveSize 1
    diagnostics[0].message shouldContain "unknownFn"
    diagnostics[0].message shouldContain "SprudelPattern"
}
```

---

## Challenges & Decisions

| Challenge                                     | Approach                                                        |
|-----------------------------------------------|-----------------------------------------------------------------|
| Variables (`let x = note("c3"); x.gain(0.5)`) | Phase 1: skip. Phase 2: single-assignment scope map             |
| `PatternLike` accepts almost anything         | Don't validate argument types, only counts                      |
| Parse errors in incomplete code               | Analyzer skips unparseable code gracefully                      |
| Performance on large files                    | Phase 1: main thread + 500ms debounce. Phase 3: web worker      |
| String extensions (`"c3".note()`)             | Already handled by `ExpressionTypeInferrer`                     |
| Noise from transient errors while typing      | Longer debounce (500ms+) + version-based cancellation           |
| Worker bundle size                            | Worker includes klangscript (parser + analyzer) but NOT UI code |
| Registry sync to worker                       | Build-time baking; only imported library names sent via message |

## Incremental Rollout

1. **v1**: Tier 1 diagnostics (unknown symbols) on main thread — immediate value
2. **v2**: Tier 2 diagnostics (wrong context) + helpful error messages with suggestions
3. **v3**: Web worker offloading — performance for large files
4. **v4**: Tier 3 (argument count validation)
5. **v5**: Tier 4 (variable type tracking)

## Key Files

| File                                                                 | Role                                |
|----------------------------------------------------------------------|-------------------------------------|
| `klangscript/src/commonMain/kotlin/intel/KlangScriptAnalyzer.kt`     | Core analyzer (new)                 |
| `klangscript/src/commonMain/kotlin/intel/ExpressionTypeInferrer.kt`  | Type inference (exists)             |
| `klangscript/src/commonMain/kotlin/intel/AnalyzerDiagnostic.kt`      | Diagnostic data class (new)         |
| `klangscript/src/commonTest/kotlin/intel/KlangScriptAnalyzerTest.kt` | Tests (new)                         |
| `klangscript-ui/src/jsMain/kotlin/codemirror/EditorDocContext.kt`    | Trigger analysis on change          |
| `klangscript-ui/src/jsMain/kotlin/codemirror/CodeMirrorComp.kt`      | Feed diagnostics to linter          |
| `klangscript-worker/`                                                | Web worker module (new, Phase 3)    |
| `klangjs/src/jsMain/kotlin/.../Lint.kt`                              | CodeMirror linter bindings (exists) |
