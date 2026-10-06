# Callable objects show both their forms in the docs

Status: **done 2026-10-07** (branch `callable-object-docs`, reviews clean in round 2), except the open items at the
end. Report: `tmp/reviews/cod-report.md`.

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
  libraries share (`perlin`, `sine`, `adsr`, `duck`, `tremolo`, `gain`, `compressor`, ...) the hover prose is the
  stdlib variant's (`Ignitor.perlin`). The sentence stays either way.

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

## Open items

- **Hover prose on shared names.** The hover shows the first non-blank description in variant order, so for a name
  stdlib and sprudel share the stdlib variant's prose comes first (`perlin` hovers as `Ignitor.perlin`). A follow-up
  could prefer the variant that matches the bare identifier (top-level property or call form). Not fixed here.
- **Hover on an alias** (`Kat`, `lowpass`) lists `val Kat: Katalyst` only; the docs page shows the call form, the
  hover does not (`KlangSymbolDocsComp` gets the symbol, not the registry).
- **A closure calling a local declared after it** gets a false named-argument error (older than this change): the
  analyzer binds the name only from its declaration on, so the call inside the closure resolves to a same-named global.
- **The param tools ignore local bindings** (older): `const gain = (a) => a; note("c").superimpose(gain(0.5))` opens the
  Sprudel gain tool on `0.5`, because `argumentAt` looks the callee up by name with no scope check.
- **The editor diagnostic names the canonical object** (`Unknown parameter 'fre' on 'lpf'` for `lowpass(fre = 800)`)
  while the runtime names the call as written (`in lowpass`).
