# Plan: surface runtime errors in the editor, with a clickable source location

**Status: phases 1-3 SHIPPED 2026-08-22. Phase 4 open, plus one known gap (below).**
Supersedes the capture note in `silent-shape-discard-on-error.md` (that file describes the
symptom; this is the fix).

What shipped: `osaDistance` + `NameSuggestions` (ranked "Did you mean ...?" replacing a 429-name
unranked dump), `Environment.getDisplayTypeName` (anonymous patterns report `SprudelPattern`
rather than `Unknown`), `SprudelDiagnostics` (collect what the catch swallows, carrying the raw
throwable so the source location survives), the three swallow sites rerouted, and the editor
wiring in `KlangCodePlaybackCtrl`.

Two design notes differ from the plan below, both because measurement said so: the API shipped as
`collectingInto(list, block)` rather than `collecting(sink, block)`, and the ranking uses the NEW
`osaDistance` rather than the existing `levenshtein` — plain Levenshtein scores a transposition
as 2 edits and could not suggest `oscp` for `ocsp` at all.

## KNOWN GAP: query-time transforms are not captured yet

The editor wiring wraps `SprudelPattern.compile`, which is sufficient for transforms applied at
BUILD time (`.apply(shape)` — measured). It is NOT sufficient for transforms applied per QUERY:
`sometimesBy` routes through `_innerJoin` -> `BindPattern`, whose lambda runs on every query, so
the swallow happens after compile has returned. For those shapes the user still gets only a
console trace, which is the original bug unfixed for that family.

`SprudelDiagnosticsTest`'s "KNOWN GAP" row asserts the current behaviour (nothing at compile,
one diagnostic at query). Note what that row does and does not do: it pins that `sometimesBy`'s
mapper resolves per query, but it is NOT an acceptance criterion for either fix below — both act
outside `SprudelPattern.compile`, so implementing either leaves the row green. Verifying a fix
needs a test that exercises the frontend wiring.

Two ways out, neither free:
1. Wrap a warm-up query in the collector at play time. Cheap, but it evaluates one extra cycle
   and probabilistic transforms (`sometimesBy(0.4, ...)`) may not hit the failing branch in the
   sampled cycle, so coverage is luck-dependent.
2. Give the player a collector around its real query batches. Correct, but it crosses into
   `audio_engine` and the playback loop, and needs care not to allocate per query.

Triggered 2026-08-21 by a real debugging session: a single transposed letter (`.ocsp` instead of
`.oscp`) in Der Schmetterling's `lead_shape` silently discarded the entire shape function. The
lead played with no sound, no gain, no envelope, no filters and no osc params, and every
subsequent parameter edit appeared to do nothing. It cost a full session and was misdiagnosed as
a `.tap()` bug. The error was in the browser console the whole time.

## What the user should get

A red underline in the editor, on the exact call, that can be clicked to jump there. Same
treatment compile errors already receive.

## What already exists (this is mostly a wiring job)

Measured, not assumed:

- `KlangScriptTypeError` is thrown with `location = memberAccess.location` and carries it
  through. **Verified inside the swallow**: instrumenting the catch printed
  `type=KlangScriptTypeError loc=3:33-37` for a typo inside an arrow function. The position is
  there; nothing is lost at throw time.
- `mapToEditorError` (`src/jsMain/kotlin/comp/editor_helpers.kt`) already converts any
  `SourceLocationAware` throwable into an `EditorError` with line, column and length.
- `KlangCodePlaybackCtrl` already exposes `errors: Stream<List<EditorError>>`, and
  `KlangCodeEditorComp` already subscribes to it and calls `setErrors`.
- The errors stream is already cleared on each new submission.

**So the only break is the middle:** three catch sites turn a structured error into a
`println(stackTraceToString())` and return the input unchanged, discarding the object that
carries the location.

| Site | Message seen in console |
|---|---|
| `sprudel/.../lang/lang_structural.kt:2015` | "Error applying layer transform: ..." (the one hit in this case) |
| `sprudel/.../lang/lang_helpers.kt:148` | "Error while chaining pattern mappers: ..." |
| `sprudel/.../lang/lang_helpers.kt:172` | "Error while invoking pattern mapper: ..." |

There are 8 `catch (e/_: Exception)` sites across `sprudel` + `klangscript` commonMain; the other
five need an audit to see which can hide a user-authored mistake.

## Keep the catch

The catch exists for live-coding resilience: a bad edit mid-performance must not kill the audio.
That is correct and stays. The bug is not that the error is caught, it is that it is invisible
where the user is looking. **Do not "fix" this by rethrowing.**

## Design

### Transport: a diagnostics sink in sprudel

The catch sites are deep in pattern internals and have no access to the UI. `QueryContext`
(`SprudelPattern.kt:102`) has a typed `Key<T>` map and would be the tidy seam, but `applyLayer`
and `PatternMapperFn.chain` do not receive a `QueryContext`, so threading it means changing a lot
of signatures for one feature.

**Recommendation:** a small sprudel-level sink with a scoped installer.

```kotlin
object SprudelDiagnostics {
    fun report(e: Throwable, context: String)
    fun <T> collecting(sink: (Diagnostic) -> Unit, block: () -> T): T   // installs, runs, restores
}
```

The catch sites call `report(...)` instead of `println(...)`. The app wraps its query batch in
`collecting { }` and pushes what it gets into the existing errors stream. The CLI installs a
printing sink so behaviour there is unchanged.

Open fork for the maintainer: a global sink is exact on JS (single-threaded) but would interleave
across concurrent playbacks on the JVM. Options are a thread-local on JVM via `expect/actual`, or
accepting the interleave because the editor is a JS-only consumer. **Recommend accepting it for
v1** and noting the limit, since the JVM path only prints.

### Deduplication: cheap insurance, NOT mandatory (corrected by measurement)

An earlier draft of this plan claimed a broken shape throws every cycle and that dedup was
therefore mandatory. **That was wrong, and measuring it is what corrected it.** A shape function
is applied once, when the pattern graph is built, not per query. Measured over five query cycles:

```
Error applying layer transform: ... (ONE stack trace, at build)
PROBE-FIRE|cycle=0|events=1
PROBE-FIRE|cycle=1|events=1     <- no further errors
PROBE-FIRE|cycle=2|events=1
PROBE-FIRE|cycle=3|events=1
PROBE-FIRE|cycle=4|events=1
```

So it fires once per build, and execution is NOT stopped: every cycle keeps producing events,
using the untransformed pattern. One rebuild happens per code submit, so the natural rate is one
diagnostic per broken submit, which is exactly what the editor wants.

Keep a `(location, message)` dedup anyway as cheap insurance for the other catch sites and for
patterns rebuilt mid-playback, but it is not load-bearing and must not drive the design.

### Message quality: the "did you mean" exists, but not on this path

The project already has it, and already has the helper: `Environment.loadLibrary`
(`Environment.kt:277-287`) does a `levenshtein` nearest-match and appends
`Did you mean "x" or "y"?`. It is used for LIBRARY and symbol lookup.

**The method-not-found path does not use it.** `Interpreter.kt:~1405` instead dumps every
available method as a flat list, which for a `ControlPattern` is over 400 names and roughly 4 KB
of console output with no ranking. `.ocsp` → `.oscp` is a single adjacent transposition, so the
existing `levenshtein` helper would have put `oscp` first and ended the session in seconds.

Fix: reuse the same helper in the method-not-found branch, lead with the suggestion, and keep
(or truncate) the full list behind it.

### JS-only degradation — the message is worst exactly where users are

On the JVM the error reads `Native type 'ControlPattern' has no method 'ocsp'. Available
methods: <400 names>`. In the browser the maintainer saw `Native type 'Unknown' has no method
'ocsp'. Available methods:` with nothing after it.

Cause of the NAME half: `NativeInterop.kt:330` computes `qualifiedName = kClass.simpleName ?:
"Unknown"`, and `simpleName` is null for an ANONYMOUS class — sprudel builds patterns from 21
`object : SprudelPattern { ... }` expressions. Not a JS quirk at all, as first assumed.

**Correction to an earlier draft of this document:** it claimed the empty name "fails to key the
extension registry", emptying the method list. That is wrong. Extension methods are keyed by
`KClass`, never by name (`Environment.getExtensionMethodNames` -> `getAllRegisteredSupertypes` ->
`nativeTypes.keys.filter { it.isInstance(obj) }`), so a null `simpleName` cannot empty the list.
The two halves are unrelated, and only the name half was ever broken. The empty list observed in
the console remains unexplained and was not reproduced on the JVM.

## Phases

1. **`SprudelDiagnostics` + the three known sites.** Sink, scoped installer, dedup. CLI gets a
   printing sink so nothing regresses. Spec: a typo in a shape produces one diagnostic carrying
   the right line and column.
2. **Wire the app.** `KlangCodePlaybackCtrl` collects during query and feeds the existing errors
   stream through `mapToEditorError`. Verify by eye that the marker lands on the right token and
   the click-to-jump works.
3. **Message quality** (SHIPPED): rank suggestions in the method-not-found branch, and resolve
   the reported type through the registered supertype so anonymous patterns name themselves.
   Ranking needed a NEW `osaDistance`, not the existing `levenshtein`: a transposition costs 2
   there and could not be suggested at all. The method LIST was never broken (see the correction
   above); only the type name was.
4. **Audit the remaining five catch sites** and route the ones that can hide a user mistake.

## Traps

- **The location is source-relative.** Confirm it maps to editor coordinates when the failing
  code came from an imported module rather than the open document, or the marker will land on the
  wrong line in the wrong file. This is the one thing most likely to be wrong.
- **Do not let a diagnostic sink allocate per query on the audio path.** Queries feed playback;
  the dedup set must be bounded and the fast path (no error) must cost nothing.
- **`e.message` is not always the display string.** During instrumentation the message field came
  back as a `BindPattern` toString rather than the human text, while the console showed the good
  message. Use `format()` / `formatHeader()` and verify what actually reaches the editor.
- A diagnostic must never escape into the worklet's `onmessage` or the render loop.

## Testing

- Spec: typo inside a shape function yields exactly one diagnostic, with the expected line/column,
  and the pattern still queries (audio survives).
- Spec: the same broken pattern queried 50 times yields one diagnostic, not 50.
- Spec: a correct shape yields zero diagnostics and no allocation on the sink path.
- Mutation-check each: they are guards against silence, so a green test proves nothing until it
  has been red.
