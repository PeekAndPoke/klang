# Callable objects show both their forms in the docs

Status: **done 2026-10-07** (branch `callable-object-docs`, reviews clean in round 2); the open items at the end
done 2026-10-07 on `correctness-fixes`. Reports: `tmp/reviews/cod-report.md`, `tmp/reviews/fix-script-report.md`.

## The ask (maintainer, 2026-10-07)

"On the signals that now have f.e. perlin(100, 200) do we announce this in the docs of these objects? How do we handle
callable objects in the docs anyways? We should see two signatures: 1. Object name and type 2. the callable form."

## What the generated docs hold today (2026-10-07)

- A callable object (`@KlangScript.Object` with an `@KlangScript.Invoke` method) gets ONE docs variant on its own
  symbol: a `KlangProperty` with the object as its type, e.g. `perlin: perlin`. The callable form appears only in the
  prose ("or call it: `perlin(200, 2000)` is `perlin.range(200, 2000)`").
- Every `@KlangScript.Invoke` lands in a separate top-level docs symbol named **`invoke`**, category
  **"uncategorized"**, as a `KlangCallable` whose receiver is the object type: 51 variants in sprudel (`sine`, `perlin`,
  `adsr`, `duck`, `tremolo`, `delay`, ...) and 1 in klangscript-libs (`Katalyst(k => ...)`). Nobody looks for them there.

## The goal

Each callable object's own docs symbol carries two variants:

1. the object and its type, as today: `perlin: perlin`;
2. the callable form, named after the object: `perlin(from, to): SprudelPattern`, with its parameter docs, return doc
   and samples from the `@Invoke` method's KDoc.

The stray `invoke` symbol is gone. The docs pages, hover docs and signature help show both forms.

## The work

- `klangscript-ksp`: when the docs are built, attach each object's invoke as a second variant on that object's symbol
  (a `KlangCallable` named after the object, receiver as the editor expects for a call on the object), and emit no
  `invoke` symbol. Both modules the processor runs on.
- Check every consumer of the docs data before choosing the variant's exact shape: the docs pages, completion (the
  object offered once, not twice), hover, signature help on `perlin(`, the param tools, the analyzer's
  `receiverTypeBeforeDot` / `symbolAt`, `SignalShorthandIntelSpec`, and anything that looked up `invoke` by name.
- The objects' KDoc may then drop the "or call it" sentence where the second variant says it, or keep it: decide
  when the pages render.

## Verify

- A structural comparison of the docs data before and after: only the intended moves (the `invoke` variants onto
  their objects, the `invoke` symbol gone).
- Editor specs: completion, hover, signature help on a callable object, both modules, JVM and JS.
- A docs-page check that `perlin`, `adsr`, `duck` and `Katalyst` show both signatures.

## Also in this task (maintainer, 2026-10-07)

The internal KlangScript symbol of a callable object's call becomes `__invoke__` (today `invoke`): KSP emits the
`@KlangScript.Invoke` method under `__invoke__`, the interpreter's call dispatch looks it up by that name
(`NativeOperatorNames`), and nothing user-facing says `invoke` any more. The Kotlin side keeps `operator fun invoke`.
The naming scheme (Kotlin's operator words, `__plus__` / `__rplus__` and friends) is decided in `docs/tasks/klangscript-native-object-operators.md`.

## What was done (2026-10-07)

What we built, in one pass with the coordinator's brief:

- **The shape.** Each callable object's symbol carries two variants: `KlangProperty` (`val perlin: perlin`) and a
  receiver-less `KlangCallable` named after the object (`perlin(from: Number?, to: Number?): SprudelPattern`), with the
  `@Invoke` method's params, return doc and samples. It is the shape the editor already resolves a top-level call with,
  so `perlin(`, `adsr(`, `duck(`, `Katalyst(` resolve through `getCallable(name, null)` with no special case. A value
  holding the object (`Kat(...)`, a local) resolves through `KlangDocsRegistry.getCallForm(type)`: the symbol named like
  the type whose top-level property has that type. `KlangSymbol.callForm` names the variant for the docs page ("Call
  Form" badge). The stray `invoke` symbol is gone; `KlangCallable.signature` lost its `invoke` special case; the docs
  page lost its merge of the `invoke` symbol; completion lost its `invoke` filter.
- **`__invoke__`.** `KlangScript.Invoke.NAME` is `__invoke__` (`NativeOperatorNames.INVOKE` reads it); the Kotlin side
  keeps `operator fun invoke` (`InvokeShape.KOTLIN_OPERATOR`). Errors and the call stack name the call as written
  (`Katalyst`, not `Katalyst.invoke`), the generated argument errors of a call form name the object
  (`SpecAwareItem.errorName`), and the non-callable error speaks to the script user (round 1). No script, song, tutorial, doc or
  KDoc sample called `.invoke(...)` explicitly; nothing there changed.
- **Two side decisions.** `@Invoke` is allowed in an `@Object` only (it was `@Object` or `@TypeExtensions`; none sat in a
  type extension, and a type extension has no symbol to carry the call form). A `@Method` registering as `invoke` or
  `__invoke__` is refused. And the docs merge key now tells a property from a callable of the same name (it collapsed
  them, which would have dropped the call form on a re-registration).
- **Param tools.** A top-level call of a callable object binds to its call form's own parameters, except where a
  method has the very same parameter list and declares tools for the argument: the field accessors and compounds
  mirror their pattern method (`pan(0.7)` inside `superimpose` keeps its pan tool, `ParamToolArgumentSpec`); the eight
  signals (`perlin(from, to)` mirrors `range`) and `Katalyst(configure)` mirror none and bind to their own
  parameters. A call form is never a candidate for an untyped member call.
- **KDoc "or call it" sentences kept.** On the sprudel docs page the object's description comes first and the
  sentence is the only prose there naming the shorthand before the call form's card. In the editor hover the first
  non-blank description in variant order wins, and the editor registers stdlib before sprudel, so on a name both
  libraries share (`perlin`, `sine`, `adsr`, `duck`, `tremolo`, `gain`, `compressor`, ...) the hover prose was the
  stdlib variant's (`Ignitor.perlin`; fixed since, see the last section). The sentence stays either way.

Numbers: the docs data before and after differ only by the 52 moves (51 sprudel, 1 stdlib, order included) and the
removed `invoke` symbol; the registries only by `invoke` -> `__invoke__` (52 registrations); production bundle
(`:jsBrowserProductionWebpack`) 8,117,004 -> 8,110,355 bytes (-6,649); the 18-row corpus identical (Kokon proven from
its committed text, the maintainer was editing it).

## Round-1 fixes (2026-10-07)

- Alias cards (`Kat`, `lowpass`, `vel`, `d`, `o`, `comp`, ...) show the object's call form on the docs page again
  (`KlangDocsRegistry.callFormFor`).
- The hover's parameter table takes, per name, the first non-blank description (`parametersByName`), and the 30 call
  forms that lacked `@param` lines carry them now (copied from their pattern method, `Katalyst` from `build`).
- A local is never a same-named global in `NamedArgumentChecker` either (`const gain = (amount) => amount;
  gain(level = 1)` is clean); a local holding a callable object (`let d = duck; d(orbitt = 1)`) resolves through its
  type's call form in the inferrer and the checker; a bare name is typed by its top-level property only (`duck` is
  sprudel's `duck`, not the stdlib's `Katalyst.slot.duck` group).
- Operator symbols (`__x__`) are not reachable by member access and are left out of every "Available ..." list and
  completion; the non-callable error reads "'x' cannot be called: it is not a function."; `getCallForm` also matches
  by FQCN; an alias that is its own top-level symbol (`density` listed as an alias of `d`) is offered once.

## Round-2 fixes (2026-10-07)

`body` / `vowel` call forms point to the list at `SprudelPattern.body` / `SprudelPattern.vowel`; one wording for every
"not callable" error (`'x' cannot be called: it is not a function.`, else the value, `5 cannot be called: ...`); KSP
refuses a `@Method` named like any operator symbol (`__x__`); `NamedArgumentChecker` takes the local identifiers with
no default; `getCallForm` stops once it has found the object (an object that is not callable is the answer, the FQCN
scan runs only for a type no symbol of its name holds); the whole-call rewrite on a tooled top-level setter
(`superimpose(tremolo(5, 0.3))`) is asserted.

## Open items, done (2026-10-07, branch `correctness-fixes`)

The five items left open above, fixed in one pass; report `tmp/reviews/fix-script-report.md`. All live in the
analyzer (`klangscript/intel`, `KlangDocsRegistry`), none in the UI.

- **Hover prose on shared names.** `AnalyzedAst.symbolAt` narrows the symbol to what the name is where it stands: a
  bare name (called or not) shows its top-level variants only (`KlangDocsRegistry.topLevelView`: the property, a
  function, the call form; the object first, as decided above), a member of a known receiver the receiver's method (as
  before), a member of an unknown receiver the methods (`memberView`). The origin chip follows the first variant. So
  `perlin`, `sine`, `adsr`, `duck`, `tremolo`, `gain`, `compressor` hover with sprudel's prose and chip, and
  `Ignitor.perlin` with the stdlib's. A member of a receiver of unknown type is narrowed to the methods only when the
  receiver is a local; a namespace import (`sp.note(...)`) keeps the whole symbol (round 1). The receiver identifier of a same-named member (`gain` in `gain.gain`) is no
  longer read as the member.
- **Hover on an alias.** `topLevelView` adds the object's call form (`callFormFor`) for a second name of a callable
  object, so `Kat` hovers as `val Kat: Katalyst` and `Katalyst(configure: ...)`, and `lowpass` with `lpf`'s call
  form, as the docs page shows them. The hover component still gets a symbol only; the analyzer hands it the right one.
- **A closure calling a local declared after it.** The runtime defines a `let` / `const` when execution reaches it and
  looks a closure's names up when the closure runs, so a closure run after the declaration calls the local, code
  before it the global (pinned on the runtime too). The analyzer announces each statement list's declarations first
  (`TypeScope.declareAhead`). Only an arrow that is the whole initialiser of a `let` / `const` / `export` sees a local
  declared further down (`TypeScope.deferredBody()`), with the declaration's type once the walk reaches it; which
  scopes' later locals it sees is stated once in the rule's home (below; round 2 corrected a helper inside a block, an
  IIFE or a transform body, round 3 the wording). Every other arrow (a call argument such as a sprudel
  transform or a configure lambda, an IIFE, an arrow in an array or object, a returned arrow) sees only what is
  declared above it, as before. A recursive local sees itself. Where
  the editor and the runtime still disagree: such a deferred arrow called before the declaration runs (`const f = () =>
  gain(); f(); const gain = ...`) calls the global at runtime, while the editor reads the local. The rule's home:
  `klangscript/ref/intel-analyzer.md`, "Locals declared further down" (round 1 narrowed it from every arrow, which read
  eager call-argument arrows wrongly).
- **The param tools and local bindings.** `argumentAt` asks the analysis for the callee's local binding
  (`AnalyzedAst.localBindingOf`): a local function has no tools (`const gain = (a) => a; ...superimpose(gain(0.5))`
  opens nothing), a local holding a callable object (`let d = lpf; d(800)`) binds through the object's symbol, and so
  does a second name of the object (`lowpass(800)` opens `lpf`'s filter tool, round 1).
- **The diagnostic's name.** `NamedArgumentChecker` names the call as written (`Unknown parameter 'fre' on 'lowpass'`,
  `on 'Kat'`), as the runtime does; the mixing message lost its dash too.

Specs: `PositionAwareIntelSpec` (klangscript, common, so JVM and JS, a hand-built registry with the KSP shapes) and
`EditorPositionIntelSpec` (sprudel, JVM, the real registries in the editor's order). Eleven mutations in the report,
twelve more in its round-1 section, each red; the two existing specs that pin "the object first, then the call" (`InvokeAnalysisTest`,
`KatalystCallFormSpec`) stay as they are.
