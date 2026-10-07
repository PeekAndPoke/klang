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
   sprudel `String.distort` variant). With the receiver type unknown and the receiver a local
   (`x => x.gain(...)`) the symbol's member variants (`registry.memberView`); any other receiver
   of unknown type (a namespace import, `sp.note(...)`, which calls the top-level `note`) the
   whole symbol. The receiver identifier of a same-named member (`gain` in `gain.gain`) is no
   member.

2. **Local-binding shadowing** — if the cursor sits on an Identifier resolved by the scope
   walk to a local `let` / `const` / `export` / arrow-parameter binding, returns a
   synthesised `KlangSymbol(origin = Origin.Local, variants = [KlangProperty(name, type)])`.
   Locals shadow same-named registry entries even when the local's inferred type is null.

3. **Bare name**: for everything else, `registry.topLevelView(registry.get(name))`: the
   top-level variants only (a property, a function, a callable object's call form; the object
   first), plus the object's call form for a second name of a callable object (`Kat`,
   `lowpass`, via `callFormFor`), with the first variant's library as the origin. A name stdlib
   and sprudel share (`perlin`, `gain`, `duck`, ...) so hovers as sprudel's object at the top
   level, not as the stdlib method registered first (2026-10-07, guards `PositionAwareIntelSpec`,
   sprudel's `EditorPositionIntelSpec`).

## Lexical scoping in `TypeMapBuilder`

`AnalyzedAst.build` walks the AST through `TypeMapBuilder`, which threads a `TypeScope`
(parent-linked map of `name → LocalBinding(name, type, declPos?)`) — mirroring the
interpreter's `runtime/Environment.kt`.

A child scope is pushed when entering:

- `ArrowFunction` body (parameters bind into the child scope; typed when the arrow is an
  argument to a callable whose parameter is function-typed, untyped otherwise)
- `IfExpression.thenBranch` and `ElseBranch.Block.statements`
- `WhileStatement.body`, `DoWhileStatement.body`
- `ForStatement` (init/cond/update/body all share one scope started at the for)

**Locals declared further down (the one home of this rule).** A statement list announces its
`let` / `const` / `export` names first (`TypeScope.declareAhead`). Code written before a
declaration does not see it: the interpreter defines a local when it reaches it, and a closure
looks its names up when it runs. A scope's later locals are visible to everything written inside an arrow that is a direct `let` /
`const` / `export` initialiser of that scope, eager bodies nested in it included (they run when the
arrow runs, or later), and to nothing else: a block, an IIFE or a call-argument body that is not
inside such an arrow of that scope finishes before the scope reaches the declaration.
Such an arrow's scope is `TypeScope.deferredBody()`. So `const f = () => gain(level = 1); const
gain = (level) => level` checks `gain` as the local, so does a helper inside an eager call inside
`f` (`const f = () => { run(() => { const h = () => gain(); h() }) }`), a helper declared and
called inside a block or an IIFE at the top level (`if (c) { const h = () => gain(); h() }`,
`(() => { const h = ...; return h() })()`) calls the outer or library name, and a recursive local
sees itself. Every other arrow runs
where it is written and gets a plain child scope, seeing only what is declared above it: a call
argument (a sprudel transform, a configure lambda, `run(f)`), an IIFE, an arrow in an array or
object, a returned arrow (the only later declarations it could see follow its `return` and never
run). Once the walk reaches the declaration, the identifiers that read it early take its
binding and type (hover, param tools, the check); an expression built on them (the return type
of `d(800)`) stays untyped. Where the editor and the runtime still disagree: a deferred arrow
that is called before the declaration runs (`const f = () => gain(); f(); const gain = ...`, or a
declared function handed to an eager call above the declaration) calls the global at runtime,
while the editor reads the local. Guards: `PositionAwareIntelSpec`, sprudel's
`EditorPositionIntelSpec`.

Bindings are added on visiting:

- `LetDeclaration` / `ConstDeclaration` / `ExportDeclaration` — binding type = inferred
  type of the initializer (may be null if uninferable; the binding still shadows).
- `ArrowFunction.parameters` — bound with the declared lambda parameter types when the arrow
  is passed to a registered callable whose `KlangParam.type.functionParams` is set (KSP emits
  these for every `FunctionN` / function-typealias parameter, e.g. `superimpose(vararg
  transforms: PatternMapperFn)` types `x` in `.superimpose(x => x.` as `SprudelPattern`).
  Positional arguments are aligned to parameters through `runtime/ArgAlignment` (the SAME
  function the interpreter uses, including the trailing-lambda rule: a sole trailing lambda
  floats to the single trailing function-typed parameter, so `Ignitor.supersaw(x => ...)` types
  `x` as the builder although `freq` comes first). Arguments past the declared list take the
  trailing vararg parameter's type. Named arguments bind by name. Unknown callee, mixed
  named/positional, or a bare arrow (not a call argument): type = null.

`TypeScope.resolve(name)` returns a binding even when its type is null: "bound with
unknown type" is meaningfully different from "not bound", and only the former should
shadow the registry. This is why `ExpressionTypeInferrer.inferIdentifier` returns the
resolved binding's type, null included, before falling through to the registry, instead of
`scope.resolve(id.name)?.type ?: registry.get(...)`.

## `ExpressionTypeInferrer` contract

```kotlin
fun inferType(expr: Expression, scope: TypeScope? = null): KlangType?
```

The scope parameter is optional — callers without one (e.g. standalone tests that aren't
walking statements) still get registry-only inference. `AnalyzedAst.TypeMapBuilder` passes
its current scope on every call so the typeMap reflects shadowing as the walk progresses.

`resolveCallable` (used by `inferCallExpression` and by the typed-lambda binding) finds a callable
object's call form like any top-level callable (`Katalyst(...)`: the receiver-less `KlangCallable`
named after the object on the object's own symbol), and falls back to `registry.getCallForm(type)` on
the callee's type when no plain callable matches (`Kat(...)`, a local holding the object), so the call
gets its return type and typed lambda params like a method call. `NamedArgumentChecker` follows the
same rule.

For `inferCallExpression` on `Identifier(name)`: if `name` is locally bound, a local holding a
callable object (`let d = duck; d(1)`) resolves through `registry.getCallForm` on the local's type,
and any other local returns null (we don't infer return types of locally-bound arrow functions yet).
Crucially, it does NOT fall through to `registry.getCallable(name, null)` in that case: otherwise `f()`
on a local arrow `let f = ...` would resolve to a same-named global like sprudel's `signal()`.
`NamedArgumentChecker` applies the same rule through the analyzer's locally bound identifiers, and so
does `argumentAt` for the param tools (`AnalyzedAst.localBindingOf`): a local function has no tools, a
local holding a callable object binds through the object's symbol, and so does a second name of the
object with no top-level callable of its own (`lowpass(800)` binds through `lpf`, `callFormFor`). A diagnostic names the call as
written (`lowpass(fre = 800)` says `on 'lowpass'`, not the `lpf` it resolves to), as the runtime does.

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
