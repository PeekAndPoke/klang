# CodeMirror Code Completion & Import-Aware Docs

## Goal

1. Make hover docs and code completion **import-aware**: only show symbols from libraries
   that the user has actually imported in their code.
2. Add autocompletion to the CodeMirror editor using the per-library `KlangDocsRegistry` data.

## Current State

- Each `KlangScriptLibrary` has its own `docs: KlangDocsRegistry` (per-library symbols)
- `KlangDocsRegistry.global` is populated at startup with all strudel docs (always available)
- The editor hardwires `docProvider = { KlangDocsRegistry.global.get(it) }` — shows all docs
  regardless of what's imported
- `KlangScriptParser` already parses `import * from "strudel"` into `ImportStatement` AST nodes
- `@codemirror/autocomplete` npm package is installed, external declarations in `Autocomplete.kt`
- Available libraries: `stdlibLib`, `strudelLib` (passed to block editor already)

## Architecture

### Core: `EditorDocContext`

A new class that manages the active doc provider based on imports in the editor code.

```
EditorDocContext
  ├── availableLibraries: Map<String, KlangScriptLibrary>   // all registered libs
  ├── activeRegistry: KlangDocsRegistry                      // merged from imported libs
  ├── onCodeChanged(code: String)                            // debounced re-parse
  └── docProvider: (String) -> KlangSymbol?                  // for hover + completion
```

**Flow:**

1. User types code → `onCodeChanged(code)` fires (debounced ~300ms)
2. Parse code with `KlangScriptParser` — extract `ImportStatement` nodes only
3. For each imported library name, look up `availableLibraries[name]?.docs`
4. Merge all imported library docs into `activeRegistry`
5. Both hover docs and code completion query `activeRegistry`

### Import Extraction (lightweight)

We don't need a full parse — just extract import statements. Two approaches:

**Option A: Full parse, extract imports from AST**

- Use `KlangScriptParser(code).parseProgram()`
- Filter `program.body` for `ImportStatement` nodes
- Pro: handles all import forms correctly
- Con: full parse on every edit (mitigated by debounce)

**Option B: Regex extraction**

- Match `import\s+.*\s+from\s+"([^"]+)"` patterns
- Pro: very fast
- Con: fragile, could match inside strings/comments

**Recommendation:** Option A. The parser is fast enough, and debouncing at 300ms means
we parse at most ~3 times per second during active typing.

## Implementation Plan

### Step 1: Create `EditorDocContext`

**File:** `src/jsMain/kotlin/codemirror/EditorDocContext.kt`

```kotlin
class EditorDocContext(
    availableLibraries: List<KlangScriptLibrary>,
) {
    private val libsByName = availableLibraries.associateBy { it.name }
    private val activeRegistry = KlangDocsRegistry()
    private var lastImports: Set<String> = emptySet()

    fun docProvider(name: String): KlangSymbol? = activeRegistry.get(name)

    fun onCodeChanged(code: String) {
        val imports = extractImports(code)
        if (imports == lastImports) return  // no change
        lastImports = imports
        rebuildRegistry(imports)
    }

    private fun extractImports(code: String): Set<String> {
        return try {
            val program = KlangScriptParser(code).parseProgram()
            program.body
                .filterIsInstance<ImportStatement>()
                .map { it.libraryName }
                .toSet()
        } catch (_: Throwable) {
            lastImports  // keep previous on parse error
        }
    }

    private fun rebuildRegistry(imports: Set<String>) {
        activeRegistry.clear()
        for (libName in imports) {
            val lib = libsByName[libName] ?: continue
            activeRegistry.registerAll(lib.docs.symbols)
        }
    }
}
```

### Step 2: Add debounced code change handler

Integrate with `CodeMirrorComp`'s `onCodeChanged` callback. Use `window.setTimeout` /
`window.clearTimeout` for debouncing (~300ms).

### Step 3: Create DSL completion source

**File:** `src/jsMain/kotlin/codemirror/DslCompletionSource.kt`

```kotlin
fun dslCompletionSource(docContext: EditorDocContext): (CompletionContext) -> CompletionResult?
```

Logic:

1. `context.matchBefore(regex)` to get the word prefix being typed
2. Skip if inside a string literal (quote-counting check)
3. Query `docContext.docProvider` to find matching symbols (prefix match)
4. Map `KlangSymbol` → `Completion` objects:
    - `label` = symbol name
    - `type` = "function" or "variable"
    - `detail` = category
    - `info` = description (first variant)
    - `apply` = for callables: `name()` with cursor between parens
5. Return `CompletionResult(from, options)`

### Step 4: Wire into editor

Update `CodeSongPage.kt` and `PlayableCodeExample.kt`:

```kotlin
val docContext = EditorDocContext(
    availableLibraries = listOf(stdlibLib, strudelLib),
)

// On code change (debounced):
docContext.onCodeChanged(newCode)

// Extensions:
dslEditorExtension(
    docProvider = { docContext.docProvider(it) },   // was: KlangDocsRegistry.global.get(it)
    ...
)
autocompletion(jsObject<CompletionConfig> {
    override = arrayOf(dslCompletionSource(docContext))
    activateOnTyping = true
})
```

### Step 5: Import statement completion

Special case: when typing `import * from "`, offer library name completions from
`availableLibraries.keys`. This helps discoverability.

### Step 6: Styling (optional)

Add completion popup styling to match the Klang dark theme via `CodeMirrorTheme.kt`.

## Key Files

| File                                                            | Role                            |
|-----------------------------------------------------------------|---------------------------------|
| `src/jsMain/kotlin/codemirror/EditorDocContext.kt`              | Import-aware doc context (new)  |
| `src/jsMain/kotlin/codemirror/DslCompletionSource.kt`           | Completion source (new)         |
| `src/jsMain/kotlin/codemirror/DslEditorExtension.kt`            | Hover docs (update docProvider) |
| `src/jsMain/kotlin/codemirror/ext/Autocomplete.kt`              | External declarations (done)    |
| `src/jsMain/kotlin/codemirror/CodeMirrorComp.kt`                | Editor setup                    |
| `src/jsMain/kotlin/pages/CodeSongPage.kt`                       | Main editor page (wire up)      |
| `src/jsMain/kotlin/comp/PlayableCodeExample.kt`                 | Docs examples (wire up)         |
| `klangscript/src/commonMain/kotlin/KlangScriptLibrary.kt`       | Library with per-lib docs       |
| `klangscript/src/commonMain/kotlin/parser/KlangScriptParser.kt` | Import parsing                  |
| `klangscript/src/commonMain/kotlin/docs/KlangDocsRegistry.kt`   | Symbol registry                 |

## Data Flow

```
User types code
    ↓ (debounced 300ms)
EditorDocContext.onCodeChanged(code)
    ↓
KlangScriptParser → extract ImportStatement nodes
    ↓
Look up KlangScriptLibrary.docs for each imported lib
    ↓
Rebuild activeRegistry (merged docs)
    ↓
┌─────────────────────┐     ┌──────────────────────┐
│ Hover docs popup    │     │ Code completion       │
│ docProvider(name)   │     │ dslCompletionSource() │
└─────────────────────┘     └──────────────────────┘
```

## Behavior

| Scenario                  | Hover docs            | Code completion       |
|---------------------------|-----------------------|-----------------------|
| No imports                | none                  | none                  |
| `import * from "strudel"` | strudel functions     | strudel functions     |
| `import * from "stdlib"`  | stdlib functions      | stdlib functions      |
| Both imported             | both                  | both                  |
| Parse error in code       | keep previous imports | keep previous imports |
