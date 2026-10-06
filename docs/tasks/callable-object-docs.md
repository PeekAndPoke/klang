# Callable objects show both their forms in the docs

Status: **queued 2026-10-07 (maintainer).** To start right after the housekeeping branch of 2026-10-07, on a branch cut
from it.

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
