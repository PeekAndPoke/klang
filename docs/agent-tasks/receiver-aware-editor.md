c# Receiver-Aware Hover Docs & Code Completion

**Related**: `klangscript-intellisense.md` (full analyzer plan — this task is the prerequisite foundation)

## Problem

The editor's hover docs and code completion are **name-only**. Three concrete issues:

1. **Registry overwrites**: `register()` does `_symbols[name] = doc`. When stdlib registers `sine` (receiver: Osc) and
   another lib registers a `sine`, the second overwrites the first.
2. **No receiver context for hover**: Hovering over `lowpass` in `Osc.sine().lowpass(1000)` shows whatever entry happens
   to be stored under "lowpass" — no way to confirm it's the ExciterDsl variant.
3. **No receiver filtering for completion**: Typing `.` after `Osc.sine()` shows ALL symbols, not just ExciterDsl
   methods.

## What We Have

| Piece                                               | Status                                 |
|-----------------------------------------------------|----------------------------------------|
| AST: `CallExpression`, `MemberAccess`, `Identifier` | exists                                 |
| `AstIndex` — cursor-to-node O(log n) lookup         | exists, `nodeAt()` is private          |
| `KlangCallable.receiver: KlangType?`                | populated in generated docs            |
| `KlangCallable.returnType: KlangType?`              | populated in generated docs            |
| `KlangProperty.type: KlangType`                     | populated (for objects like Osc, Math) |
| `EditorDocContext.lastAstIndex`                     | cached from last successful parse      |
| `DslEditorExtension.wordDocAt()`                    | name-only lookup                       |
| `DslCompletionSource.completeDsl()`                 | prefix match, no receiver filter       |
| `ArgFinder.findCallArgAtAst()`                      | AST-based, name-only variant pick      |

## Architecture

### Core: ExpressionTypeInferrer (klangscript module)

Walks AST expressions, resolves types from docs metadata (not runtime):

| Expression                                        | Inferred Type                                                      |
|---------------------------------------------------|--------------------------------------------------------------------|
| `NumberLiteral`                                   | `KlangType("Number")`                                              |
| `StringLiteral` / `TemplateLiteral`               | `KlangType("String")`                                              |
| `BooleanLiteral`                                  | `KlangType("Boolean")`                                             |
| `ArrayLiteral`                                    | `KlangType("Array")`                                               |
| `ObjectLiteral`                                   | `KlangType("Object")`                                              |
| `Identifier("Osc")`                               | registry lookup → KlangProperty → `type`                           |
| `CallExpression(Identifier("note"), args)`        | top-level callable → `returnType`                                  |
| `CallExpression(MemberAccess(obj, "sine"), args)` | infer obj type → find "sine" with matching receiver → `returnType` |
| Anything else                                     | `null` (graceful fallback)                                         |

Example chain: `Osc.sine().lowpass(1000)`

- `Identifier("Osc")` → KlangType("Osc")
- `CallExpr(MemberAccess(Osc, "sine"))` → find "sine" with receiver Osc → returns KlangType("ExciterDsl")
- `CallExpr(MemberAccess(…, "lowpass"))` → find "lowpass" with receiver ExciterDsl → returns KlangType("ExciterDsl")

### Fallback Strategy

Every code path has a fallback to current name-only behavior:

- AST unavailable (parse error) → name-only
- Type inference returns null → show all variants
- Receiver lookup finds no match → fall back to firstOrNull()

## Implementation

### Phase 1: klangscript module (commonMain, no UI)

#### 1a. KlangDocsRegistry — merge + receiver lookup

**File**: `klangscript/src/commonMain/kotlin/docs/KlangDocsRegistry.kt`

```kotlin
// Change register() to merge variants
fun register(doc: KlangSymbol) {
    val existing = _symbols[doc.name]
    if (existing != null) {
        _symbols[doc.name] = existing.copy(
            variants = existing.variants + doc.variants,
            tags = (existing.tags + doc.tags).distinct(),
            aliases = (existing.aliases + doc.aliases).distinct(),
        )
    } else {
        _symbols[doc.name] = doc
    }
}

// New: find callable matching receiver
fun getCallable(name: String, receiverType: KlangType?): KlangCallable?

// New: all symbols with a variant for this receiver (for completion after '.')
fun getVariantsForReceiver(receiverType: KlangType): List<KlangSymbol>

// New: symbol filtered to variants matching receiver (for hover popup)
fun getSymbolWithReceiver(name: String, receiverType: KlangType?): KlangSymbol?
```

#### 1b. AstIndex — public API

**File**: `klangscript/src/commonMain/kotlin/ast/AstIndex.kt`

- Make `nodeAt(pos)` public
- Add `fun parentOf(node: AstNode): AstNode?`
- Add `fun offsetOf(node: AstNode): IntRange?`

#### 1c. ExpressionTypeInferrer — new

**New file**: `klangscript/src/commonMain/kotlin/intel/ExpressionTypeInferrer.kt`

All intellisense/semantic-analysis code lives in the `intel` package (sibling of `ast`, `docs`, `runtime`, etc.).

```kotlin
package io.peekandpoke.klang.script.intel

class ExpressionTypeInferrer(private val registry: KlangDocsRegistry) {
    fun inferType(expr: Expression): KlangType?
}
```

Handles: literals, Identifier, MemberAccess, CallExpression. Returns null for everything else.

#### 1d. Tests

- `ExpressionTypeInferrerTest` — synthetic AST + populated registry
- `KlangDocsRegistryTest` — merge behavior + receiver-aware lookups

### Phase 2: klangscript-ui module

#### 2a. Hover docs — receiver-aware

**File**: `klangscript-ui/.../DslEditorExtension.kt`

Change `wordDocAt()`:

1. `astIndex.nodeAt(pos)` → find AST node
2. If parent is `MemberAccess` and cursor is on `.property`:
    - Infer type of `MemberAccess.obj`
    - `registry.getSymbolWithReceiver(name, receiverType)`
3. Fall back to `docProvider(name)` if inference fails

Add `registryProvider: () -> KlangDocsRegistry?` param to `dslEditorExtension()`.

#### 2b. Completion — dot-context

**File**: `klangscript-ui/.../DslCompletionSource.kt`

Change `completeDsl()`:

1. Check char before typed word — if `.`, this is member access context
2. Member access: `astIndex.nodeAt(dotPos)` → infer receiver type → `registry.getVariantsForReceiver(type)` → filter by
   prefix
3. Top level: filter to symbols with receiver=null or KlangProperty variants
4. Fall back to show-all if inference fails

#### 2c. ArgFinder — receiver-aware

**File**: `klangscript-ui/.../ArgFinder.kt`

Add optional `registry: KlangDocsRegistry?` param to `findCallArgAtAst()`. If callee is MemberAccess, infer obj type,
use `registry.getCallable(name, receiverType)`.

#### 2d. Wire together

**File**: `klangscript-ui/.../CodeMirrorComp.kt`

Pass `registryProvider = { docContext.registry }` to `dslEditorExtension()`.

## Verification

```bash
./gradlew :klangscript:jvmTest
./gradlew :klangscript-ui:compileKotlinJs
```

Manual: type `Osc.` → only Osc methods. Type `Osc.sine().` → only ExciterDsl methods. Hover `lowpass` in chain →
ExciterDsl.lowpass docs.

## Out of Scope (deferred to klangscript-intellisense.md)

- Variable type tracking (`let x = Osc.sine(); x.lowpass()`)
- Diagnostic squiggles for unknown functions
- Argument count/type validation
- Full semantic analysis (future `KlangScriptAnalyzer` also goes in `intel` package)
