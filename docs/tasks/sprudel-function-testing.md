# Sprudel functions that nothing exercises

> Opened 2026-08-31, from the doc-example compile sweep (`docs/tasks/dsl-doc-example-rot.md`).
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
[`future/native-interop-function-values.md`](future/native-interop-function-values.md): the
spelling that fails is the point-free one every Tidal and Strudel user reaches for first, and it
fails with an internal cast error rather than a "did you mean `rev()`?" diagnostic. The affected
surface (every function taking a `PatternMapperFn`) is listed there.

**Test gap that let it sit here:** every `pickF` case in `sprudel/src/commonTest` and in
`JsCompatTestData.kt` uses the arrow form (`x => x.add(1)`). None passes a named transform, in
either spelling.

## 2. `partials` does not exist

`randL`'s only example, and the prose around it, promised a `partials` parameter:

```
s("saw").n(irand(12)).scale("F1:minor").partials(randL(8))   // 8 random partials
```

`partials` appears nowhere in `sprudel`, `audio_bridge` or `audio_be`. `randL` itself works and
produces a list per cycle, but nothing in the codebase consumes a list-valued pattern, so the
function currently has no reachable purpose. The example and the false prose were removed; `randL`
is now documented without an example, which is itself a signal.

**Decision needed, not a bug to fix blind:** either add `partials` (and settle what it drives), or
retire `randL`, or point it at a list-consuming parameter that exists.

## 3. `filter`'s predicate cannot inspect an event from KlangScript

All three `filter` examples failed, and not on syntax:

```
s("bd sd hh cp").filter(x => x.part.begin < 0.5)   -> 'SprudelPatternEvent' has no method 'part'
note("c d e f").filter(x => x.isOnset)             -> 'SprudelPatternEvent' has no method 'isOnset'
```

`SprudelPatternEvent` is a plain Kotlin data class with `part`, `whole`, `data` and friends, but
none of it is reachable from script, so a predicate written in KlangScript has nothing it can test
and `filter` has no usable script surface at all. The Kotlin door is fine; only the script one is
empty.

`filterWhen` does the time-based half of what those examples wanted and takes the begin time as a
plain number, so it works from script and the built-in songs use it. The examples were removed and
both `filter` KDocs now say so and point there.

**Decision needed:** expose the event's useful fields to the interop (`part.begin`, `isOnset`,
maybe `data`), or drop `filter` from the KlangScript surface and keep `filterWhen` as the script
door.

## Doors now left without any example

Removing the failing examples left these with none. They are not wrong, just undocumented by
example, and they should get one when item 1 is fixed:

- `randL`
- `SprudelPattern.filter` and the top-level `filter` mapper

(The three `pickF` doors got their examples back once item 1 turned out to be a missing call.)

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
