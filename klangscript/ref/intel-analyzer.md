# Editor intelligence — analyzer-owns-decisions

The `intel/` package builds `AnalyzedAst` once per code change and serves hover docs,
code completion, named-argument diagnostics, and tool badges from position-keyed lookups.
The consumers (CodeMirror extension, completion source, popup component) MUST stay dumb —
no MemberAccess sniffing, no scope walking, no library-vs-local branching in the UI layer.
All that logic lives inside `AnalyzedAst.build`.

## Public surface of `AnalyzedAst`

| Method                                             | Purpose                                                                           |
|----------------------------------------------------|-----------------------------------------------------------------------------------|
| `typeOf(expr)`                                     | Pre-computed type for any Expression.                                             |
| `getTypeAt(line, col)` / `getTypeAtOffset(offset)` | Cursor → type, deepest node.                                                      |
| `getExpressionTypeEndingAt(offset)`                | For dot-completion: type of the chain that ends just before [offset].             |
| **`symbolAt(pos)`**                                | **Hover entry point.** Returns the `KlangSymbol` to render in the popup, or null. |
| **`receiverTypeBeforeDot(pos)`**                   | **Completion entry point.** Wraps `getExpressionTypeEndingAt`.                    |
| `diagnostics`                                      | Named-arg checker output.                                                         |

`symbolAt(pos)` internally handles:

1. **Member-access receiver filter** — if cursor is on the `property` side of a `MemberAccess`
   and the receiver's type is known, look up via `registry.getSymbolWithReceiver(name, type)`.
   Strict: returns null when the receiver is known but no variant matches (so unrelated DSL
   variants don't leak — e.g. hovering `.distort` on an `IgnitorDsl` chain doesn't show the
   sprudel `String.distort` variant).

2. **Local-binding shadowing** — if the cursor sits on an Identifier resolved by the scope
   walk to a local `let` / `const` / `export` / arrow-parameter binding, returns a
   synthesised `KlangSymbol(origin = Origin.Local, variants = [KlangProperty(name, type)])`.
   Locals shadow same-named registry entries even when the local's inferred type is null.

3. **Bare-name fallback** — for everything else, `registry.get(name)`.

## Lexical scoping in `TypeMapBuilder`

`AnalyzedAst.build` walks the AST through `TypeMapBuilder`, which threads a `TypeScope`
(parent-linked map of `name → LocalBinding(name, type, declPos?)`) — mirroring the
interpreter's `runtime/Environment.kt`.

A child scope is pushed when entering:

- `ArrowFunction` body (parameters bind into the child scope, untyped)
- `IfExpression.thenBranch` and `ElseBranch.Block.statements`
- `WhileStatement.body`, `DoWhileStatement.body`
- `ForStatement` (init/cond/update/body all share one scope started at the for)

Bindings are added on visiting:

- `LetDeclaration` / `ConstDeclaration` / `ExportDeclaration` — binding type = inferred
  type of the initializer (may be null if uninferable; the binding still shadows).
- `ArrowFunction.parameters` — bound with type = null.

`TypeScope.contains(name)` returns true even when the bound type is null — "bound with
unknown type" is meaningfully different from "not bound", and only the former should
shadow the registry. This is why `ExpressionTypeInferrer.inferIdentifier` checks
`scope.contains(id.name)` before falling through to the registry, instead of just doing
`scope.resolve(id.name)?.type ?: registry.get(...)`.

## `ExpressionTypeInferrer` contract

```kotlin
fun inferType(expr: Expression, scope: TypeScope? = null): KlangType?
```

The scope parameter is optional — callers without one (e.g. standalone tests that aren't
walking statements) still get registry-only inference. `AnalyzedAst.TypeMapBuilder` passes
its current scope on every call so the typeMap reflects shadowing as the walk progresses.

For `inferCallExpression` on `Identifier(name)`: if `name` is locally bound, returns null
(we don't infer return types of locally-bound arrow functions yet). Crucially, it does NOT
fall through to `registry.getCallable(name, null)` in that case — otherwise `f()` on a
local arrow `let f = ...` would resolve to a same-named global like sprudel's `signal()`.

## `KlangSymbol.Origin`

`KlangSymbol.origin: Origin?` (nullable) — sealed:

- `Origin.Library(name: String)` — registered via a `KlangScriptLibrary` and emitted by KSP.
- `Origin.Local(kind: LocalKind)` — synthesised by `AnalyzedAst.symbolAt` for cursor-resolved local bindings.
  `LocalKind` is `LET | CONST | EXPORT | PARAM` and drives the popup chip label.
- `null` — unknown / unclassified (test fixtures, hand-rolled symbols that haven't been classified).

Helper: `fun getLibrary(): Origin.Library? = origin as? Origin.Library` — returns null for
both `Local` and `null` origins.

The variant-level `KlangCallable.library` / `KlangProperty.library` String fields still
exist for dedup in `mergeWith` — those are NOT unified into Origin.

## Consumer rules

When adding a new editor feature (hover variant, completion mode, badge, navigation,
quick-fix), first check: **can the analyzer pre-compute this and expose it as a
position-keyed lookup?** If yes — add a method to `AnalyzedAst`; don't replicate the
walking/filtering/shadowing logic in the consumer.

Existing consumers that demonstrate the pattern:

- `klangscript-ui/.../DslEditorExtension.kt::wordDocAt` — collapses to
  `analysis.symbolAt(pos)?.let { word to it }` with a bare-name fallback only when no
  analysis is available (initial render / parse error).
- `klangscript-ui/.../DslCompletionSource.kt::inferReceiverTypeBeforeDot` — one-liner
  delegating to `analysis.receiverTypeBeforeDot(...)`.
- `src/jsMain/.../KlangSymbolDocsComp.kt` — switches purely on `symbol.origin` to render
  the library / LOCAL / Built-in chip; suppresses "View docs" for `Origin.Local`.

## Where the tests live

- `intel/AnalyzedAstTest.kt` — scope tracking, shadowing, symbolAt cases including
  chains rooted at locals.
- `intel/ExpressionTypeInferrerTest.kt` / `ExpressionTypeInferrerE2eTest.kt` —
  inferrer contract.
- `docs/KlangDocsRegistryTest.kt` — `getSymbolWithReceiver` strict policy, `Origin` /
  `getLibrary()` helper, `registry.libraries` / `getByLibrary` filtering.
- `intel/CompletionProviderTest.kt` — completion suggestion shape (uses `getLibrary()?.name`).
