# KLANG-00384: Typed stage helpers for the other DSLs: Kat.fn and a sprudel fn

> **Status (2026-10-28): NOT SCHEDULED.** follows Ign.fn (370b8dc84); the same door for Katalyst and sprudel patterns

## Why this exists

`Ign.fn(x => x...)` (370b8dc84) hands its function back as it is; its declared parameter type types the lambda's `x`
as an Ignitor, so the Quick help and the completion pick the Ignitor's doors (the maintainer, 2026-10-28: "a helper
Ign.fn(x => x...) simply returning the inner function so that x gets a type"). A bare `let stage = x => x...` leaves
`x` unknown. The maintainer, 2026-10-28: "file a follow up task for Kat.fn and sprudelFn doing that same for the other
dsls".

What `Ign.fn` already brought, and the others reuse: the analyzer types a lambda argument from the callee's declared
function type (`AnalyzedAst`, `expectedArgumentTypes`), and the interpreter calls a Kotlin function a native handed back
(`Interpreter.callHeldFunction`), so a stage made with `fn` is called as the bare lambda was.

## Decide before implementing

1. sprudel's word: `fn` on the sprudel object, if it has one the script reaches as `Ign` and `Kat` are reached, or a
   global (`sprudelFn`, `patternFn`)? `/dsl-design` has the rules (one word, two doors, parameter parity).
2. Which type sprudel's `fn` declares: a pattern to a pattern (as `.apply(...)` takes), so `x` in
   `let room = sprudel.fn(x => x.reverb(...))` is a pattern.

## The work

1. **`Kat.fn`:** `Katalyst.fn(f: (KatalystBuilder) -> KatalystBuilder)` in `KlangScriptKatalyst`, its Kotlin twin on
   `Kat` in `audio_bridge`, as `Ign.fn` (`KlangScriptIgnitor`, `Ign.kt`).
2. **sprudel's fn:** per decision 1, a pattern to a pattern, both doors.
3. **Specs:** as `KlangScriptIgnitorFnSpec` (the stage plays as the bare lambda, in `serial` or `apply` and called by
   itself; the Kotlin door hands back the same function) and `IgnitorFnIntelSpec` (`x` typed inside, unknown in a bare
   lambda). Mutation-check them (light tier).
4. **Songs:** wrap the bare stages of the matching kind, as Der Schmetterling and Kokon got `Ign.fn` (`drumRoom` in
   Kokon is a pattern stage, `x => x.reverb(wet = 0.3, size = 5)` applied with `.apply(...)`).
5. **Notes left from `Ign.fn`:** its specs' mutation check; a line in `klangscript/ref/interpreter-impl.md` on calling
   a Kotlin function a native returns.

## Acceptance

- `Kat.fn(x => x...)` and sprudel's fn type `x` for the Quick help and the completion; a bare lambda's `x` stays
  unknown.
- A stage made with either plays exactly as the bare lambda, in its chain and called directly.
- The built-in songs smoke test passes after the songs are wrapped.

## History

- 2026-10-28: created.
