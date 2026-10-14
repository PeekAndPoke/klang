# Sprudel functions that nothing exercises

**Archived 2026-10-14: nothing left open; `randL` and `filter` were cut (maintainer, C5 / Q41).**

> Opened 2026-08-31, from the doc-example compile sweep (`docs/tasks-archive/2026-09/20260927-dsl-doc-example-rot.md`).
> `DslDocExamplesSpec` compiled the 1425 playable examples in sprudel's own KDoc and found 42
> broken, and querying a cycle found 4 more. Most were doc rot and were fixed. The rest were
> **call forms and properties no test has ever run**, which is why nobody noticed the docs
> advertising them had gone stale.

The pattern worth keeping in mind: a function having "tests" is not the same as a function's
**surface** having tests. `pickF` has a healthy spec file. Every case in it passes an arrow
lambda. Not one passes the other two things the docs say you may pass, and both of those are
broken.

## 1. `jux(rev)` needed parentheses, and failed unhelpfully (3 examples) — RESOLVED

**Corrected 2026-08-31.** The first reading of this was wrong and is worth recording, because the
error message led straight to it: `Internal error in native function 'jux(p1=[native function
rev])': ... cannot be cast to kotlin.jvm.functions.Function1` reads like an interop defect. It is
not. `rev` is `fun rev(n: PatternLike = 1): PatternMapperFn`, so a bare `rev` is a native
*function* value, while `jux` wants a `PatternMapperFn`. A probe settled it: `jux(rev())` passes,
`jux(rev)` fails. The examples were simply missing a call.

All three `pickF` examples are back in the KDoc, spelled `jux(rev())`, and pass the gate.

What is left is a papercut, parked in
[`future/bugfix-native-interop-function-values.md`](../../tasks/future/bugfix-native-interop-function-values.md): the
spelling that fails is the point-free one every Tidal and Strudel user reaches for first, and it
fails with an internal cast error rather than a "did you mean `rev()`?" diagnostic. The affected
surface (every function taking a `PatternMapperFn`) is listed there.

**Test gap that let it sit here:** every `pickF` case in `sprudel/src/commonTest` and in
`JsCompatTestData.kt` uses the arrow form (`x => x.add(1)`). None passes a named transform, in
either spelling.

## 2. `randL`

Cut 2026-10-14 (maintainer, C5).

## 3. `filter`

Cut 2026-10-14 (maintainer, C5).

## Doors now left without any example

None. `randL` and `filter` were cut 2026-10-14 (maintainer, C5), and the three `pickF` doors got
their examples back once item 1 turned out to be a missing call.

## Why this class of gap survives

Nothing ran the DSL's own examples until 2026-08-31. They are rendered with a real play button on
the library docs page and feed the editor's hover-help, so they are the most-read code in the
project and were the least verified. `DslDocExamplesSpec` closes that specific hole and ratchets.

It compiles each example **and queries one cycle**, which is what found item 3: a lambda argument
is stored, not run, at compile time, so a predicate or a tweak body full of nonsense compiles
happily. Querying forces the ones the arc reaches.

Its reach was measured with mutations rather than assumed, and two blind spots remain:

- A lambda the one-cycle query does not invoke is still unchecked. A deliberate typo inside
  `plyWith(4, x => x.NOSUCHFN(7))` passes; the identical typo outside a lambda is caught.
- Nothing here proves an example is *right*. Three reverb and delay examples compiled and queried
  perfectly while being silent, because the send's gate parameter was missing.

A cheap next step, if this list grows: assert a compiled example produces at least one event,
which would have caught those silent sends without needing audio.
