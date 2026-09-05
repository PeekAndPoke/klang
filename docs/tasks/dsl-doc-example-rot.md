# DSL doc examples that do not compile

> Status 2026-08-31: **46 found, 42 fixed, 4 removed.** The baseline is now EMPTY.
>
> The first pass compiled the examples and found 42. Strengthening the gate to also **query one
> cycle** (compiling stores a lambda argument without running it) found 4 more, in `filter` and
> `tweaks`. Four were not doc rot at all: they documented things that do not work, so those
> examples were deleted and the gaps are tracked in
> [`sprudel-function-testing.md`](sprudel-function-testing.md).

`DslDocExamplesSpec` compiles every ```` ```KlangScript ```` example the sprudel KDoc carries.
These are not decorative snippets: `klangscript-ksp`'s KDocParser lifts each fence into
`KlangDecl.samples`, and `KlangScriptLibraryDocsPage` renders it as a `PlayableCodeExample` with
a real play button, beside the text the editor's hover-help shows. A broken example is a button
that does nothing.

Known-broken examples are baselined in `src/jvmTest/resources/dsl-doc-examples-baseline.txt`
(currently empty, keep it that way), so the gate **ratchets**: a newly broken example fails the build, and a baselined one that starts
compiling also fails until it is deleted from the list. On failure the current failing set is
written to `build/reports/dsl-doc-examples-failing.txt`, so refreshing the baseline is one copy.

> ⚠️ Compiling is the cheap half. It cannot tell you an example is *wrong*. Three reverb and delay
> examples compiled perfectly while being **silent** (a send whose gate parameter was missing), and
> `analog`'s documented range was off by a unit while compiling fine. Both were found by hand while
> writing lessons A6 and A7. Semantics still need the render gate or a human.

## What was fixed (42)

### Kotlin written inside a KlangScript fence (16)

The dominant cause by far, and the one worth remembering: someone wrote the example in the host
language and nothing ever ran it.

- **Trailing lambdas (13).** KlangScript has no trailing-lambda syntax; the transform is an
  ordinary second argument in arrow form. `chunk(4) { it.add(7) }` became
  `chunk(4, x => x.add(7))`. Same for `inside`, `outside`, `plyWith`, and `signal`. Multi-parameter
  arrows are parenthesised: `plyForEach(4, (pat, i) => pat.add(i * 2))`.
- **`listOf(...)` (3).** KlangScript uses array literals: `wchoose(["sine", 10], ["triangle", 1])`.
- `kotlin.math.sin` / `kotlin.math.PI` do not exist in KlangScript. `Math.sin` does; there is **no
  `Math.PI`**, so the signal example now spells the constant out.

### Calls to functions that never existed (14)

Doc rot, mostly from renames, and in one case from a documented alias that was never written.

- `nres` (7 examples) was documented as "alias for `nresonance`" on **`notchq`**, the function whose
  own KDoc block it sits in. `nres` exists nowhere and is used by no song or test. The examples now
  call `notchq`. (The alternative was to register `nres` as a real alias; that is a public-API
  addition, so it stayed the maintainer's call and the examples were corrected instead.)
- `nf` → `notchf`. (`nfenv` and `nfadsr` in the same example were fine.)
- `.detune()` on a pattern went away in the detune-to-spread rename. The `rand2` example that used
  it now shows the bipolar range doing what the surrounding prose promises, on a function that
  exists: `note("c4*8").transpose(rand2.range(-2, 2))`.
- `"0 1 2 3".i` (2, on `slice` and `splice`). There is no `.i` accessor on a string anywhere. Both
  index parameters take a pattern, so the `.i` was simply dropped.
- `tweaks`' `bend` example called the same removed `.detune()` **inside a tweak lambda**, so only
  the querying gate saw it. Now `x => x.transpose(-2).accelerate(0.5)`.
- `jux(rev)` (3, on `pickF`) was missing a call: `rev` has defaulted parameters, so a bare `rev`
  is a function value, not a transform. Now `jux(rev())`. The unhelpful error it produced is
  parked in `future/native-interop-function-values.md`.

### Examples that were not a whole pattern (12)

Valid script that evaluates to a mapper or a value, so the play button had nothing to play.

- The six time-of-day constants (`timeOfDay`, `sinOfDay`, `sinOfDay2`, `timeOfNight`, `sinOfNight`,
  `sinOfNight2`) showed `gain(timeOfDay)` with no pattern. Now `s("hh*8").gain(timeOfDay)`.
- `flipSign` documents the **constant** mapper, not a function; calling it failed. Now
  `"…".apply(flipSign)`.
- `freq("440 550 660")` had no receiver. Now `"440 550 660".freq()`.
- `inhabit` (2) called the top-level mapper form with no pattern. Both now mirror the working
  sibling in the same file: `"<0 1>".apply(inhabit(…)).s()`.
- `steady("c")` passed a string to a `Number` parameter. Now `steady(60)`, with the comment saying
  c4 is MIDI 60.

## What was removed instead of fixed (4)

These were correct as written, or documented something that does not exist, so rewriting them
would have hidden a real gap. The examples are gone from the KDoc; the gaps are tracked in
[`sprudel-function-testing.md`](sprudel-function-testing.md).

### 1. `filter` has no usable KlangScript surface (3 examples)

Found only once the gate started querying. `SprudelPatternEvent`'s properties are not reachable
from script, so `x.part.begin` and `x.isOnset` both fail and a predicate written in KlangScript has
nothing it can test. `filterWhen` does the time-based half and works, so both `filter` KDocs now
point there instead. Details in `sprudel-function-testing.md`.

### 2. `randL` documents a `partials` consumer that does not exist (1 example)

```
s("saw").n(irand(12)).scale("F1:minor").partials(randL(8))   // 8 random partials
```

`partials` exists nowhere in sprudel, audio_bridge or audio_be. `randL` itself works, and its prose
says it is "useful for passing multiple random values to parameters that accept lists, such as
`partials`" — so the prose is wrong too, not just the example. Two ways out, both the maintainer's
call, which is why it was not guessed at:

- add `partials` (there is a real question of what it would drive), or
- rewrite the example and the prose around a list-consuming parameter that does exist, or
- retire `randL`.

The example and the false prose are removed; `randL` now has no example, which is itself a signal.
