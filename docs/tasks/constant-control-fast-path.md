# Constant-control fast-path (sprudel query)

Status: **planned / not started.** Created 2026-06-07. Designed during the mutable-VoiceData effort but never built
(we did the grouping refactor + the worklet codec instead). Self-contained plan so it can be picked up later.

## Context

When a modifier gets a **constant** argument — `gain(0.5)`, `lpf(1625)`, `distort("0.3:tube:4")` — the current
lift/control path (`SprudelPattern._liftNumericField` / `_applyControlFromParams`) builds a control pattern and
**`sampleAt` + `clone()`s it once per source event** to recover a value that never changes. On a modifier-heavy
voice (e.g. Der Schmetterling) that's ~15–25 redundant control-atom clones per event. The value is constant, so the
sampling + cloning is pure waste.

**Value note (important):** this was originally the "do first, low-risk" item. Since then the **SprudelVoiceData
grouping** landed, which made each clone far cheaper and dropped `SprudelVoiceData` copy/clone from ~25%+ of the
query frame to **~7%** (browser-confirmed). So this fast-path is now a **modest** win — it removes per-event
allocation *count* (the control-atom clones), not per-clone cost. Worth doing for allocation/GC pressure on dense
patterns, but measure first; it may not be worth the surface area. Decide build-vs-drop against a fresh profile.

## Design — detect constants polymorphically (no `is` checks, no string heuristics)

Add to the `SprudelPattern` interface:

```kotlin
/** Non-null iff this pattern emits a single time-invariant value (a constant). Default: not constant. */
fun constantValueOrNull(): SprudelVoiceData? = null
```

Override **only** in `AtomicPattern` to return its stored `data` — an `AtomicPattern` emits the same `data` every
cycle, so it is constant by construction. This reuses the parser's verdict: whatever `args.toPattern()` collapses to
a single atom *is* a constant, regardless of how it was written (`"0.3:tube:4"`, `1625`, …). Exclude atoms whose
`data.value` is a nested `SprudelVoiceValue.Pattern` (return null) — those aren't truly constant.

(Polymorphic method, not `is AtomicPattern`: instanceof checks are footguns long-term, and a method also hands back
the value without a follow-up cast.)

## Shape — in each lift/control helper, after `val control = args.toPattern(modify)`

```kotlin
control.constantValueOrNull()?.let { constData ->
    // numeric (_liftNumericField):  return reinterpretVoice { it.update(constData.value?.asDouble); it }
    // _applyControlFromParams:      val mapped = mapper(constData.clone())
    //                               return reinterpret { evt -> evt.copy(data = combine(evt.data, mapped))
    //                                                              .prependLocations(controlAtom.sourceLocations) }
}
return < existing sample / outer -join path >
```

One `clone()` + map **total** instead of per-event; the source event's (single-owner, leaf-cloned) data is mutated
in place. Sites (`SprudelPattern.kt`): `_liftNumericField`, `_liftOrReinterpretNumericalField`, `_liftStringField` /
`_liftOrReinterpretStringField`, `_applyControlFromParams`, `_liftData`.

## Subtleties

- **Reuse the mapped control** across all events — relies on the `VoiceMergerFn` contract (combine *mutates `src`,
  only reads `ctrl`*). All Part-2/3 combiners obey it; the golden catches a violation (a combiner that mutated the
  shared `ctrl` would corrupt later events → byte diff).
- **`sourceLocations`** — the sampling path does `.prependLocations(ctrl.sourceLocations)` per event for live
  highlighting. Use the event-level `reinterpret { … }` (not `reinterpretVoice`) so the constant atom's
  `sourceLocations` are preserved. `sourceLocations` is `@Transient`, so the golden won't catch a regression here —
  verify highlighting by eye.
- Detection must be conservative — only fast-path provably-constant atoms; anything time-varying
  (`"<0.2 0.5>"`, `"0.2 0.5"`, `saw`, `.fast(2)`) is NOT an `AtomicPattern`, so it falls through to sampling untouched.

## Verification

- Golden byte-identical:
  `./gradlew :sprudel:jvmTest --tests io.peekandpoke.klang.sprudel.golden.MutableVoiceDataGoldenSpec`
  (constant applied directly == constant sampled per event → identical output).
- Full `:sprudel:jvmTest`; browser-profile Der Schmetterling before/after to confirm the (modest) allocation drop.

## Effort

Small: ~1 interface default method + 1 `AtomicPattern` override + ~5 lift-helper sites. Golden-guarded throughout.
