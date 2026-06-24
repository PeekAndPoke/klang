# late()/early() duplicate onsets at cycle boundaries — TimeShiftPattern part-clip fix

> **Status: SHIPPED 2026-06-20.** Root cause found and fixed: `late()`/`early()` (and `off()`/`echo()`,
> all via `TimeShiftPattern`) re-emitted a step straddling a cycle boundary as a **full onset in BOTH
> adjacent cycles** when the engine queries cycle-by-cycle — duplicating the hit (plus a phantom from
> the alternation wrap-around slot). Fix = clip the shifted `part` to the query's output window in
> `TimeShiftPattern`. `:sprudel:jvmTest` green (4196). Guard: `LangLateAlternationSpec`. Golden
> regenerated (only the time-shift corpus entries changed). See memory `project_timeshift_boundary_dedup.md`.

## Context

Reported against this pattern (Der Schmetterling hats), "should produce hits twice every cycle … does
not produce any events in the first 8 cycles", suspected a `CycleTime`-era regression in `late()`:

```
sound("<[~!2]!2  [~!4]!2  [~!8]!2  [~!16]  [~!24]  [~ sd ~ sd]!24 [~ sd ~ sd]!24>")
    .mute("<0!128 1!32>").late(0.002).orbit(6)…
```

Two red herrings, ruled out with evidence before finding the real bug:

- **"No events in the first 8 cycles" is by design.** `<…>` expands each `!N` into separate alternation
  slots (`MnPatternToSprudelPattern.expandRepeat`), giving **56 one-cycle slots**: slots 0–7
  (`2+2+2+1+1`) are all-rest groups; `sd` only starts at slot 8.
- **A "tolerant `isOnset`" was the wrong fix.** Post-CycleTime every time is an exact integer tick, so
  there is no float drift to absorb; a tolerance band would mis-classify legitimately-clipped fragments
  → double/ghost triggers. The **playback controller** was also already exact (it queries whole integer
  cycles `[n,n+1)`), so "count time as CycleTime" would not have changed anything.

A reproduction spec querying the live path cycle-by-cycle revealed the *actual* symptom — duplicates,
not drops:

```
cycle 8:  bare=[8.25, 8.75]   late=[8.252, 8.752]            ✓
cycle 9:  bare=[9.25, 9.75]   late=[8.752, 9.252, 9.752]     ✗ 8.752 fired again
cycle 0:                      late=[-0.247999]               ✗ phantom (wrap-around slot)
wide [0,12): 9 onsets ≠ per-cycle union of 12               ✗ over-counts
```

## Root cause

The engine queries cycle-aligned `[n, n+1)` and filters `isOnset`. `TimeShiftPattern` queries its
source over **shifted, non-cycle-aligned** windows (`late(0.002)` of cycle 9 → source `[8.998, 9.998)`).

sprudel's convention is that an event's `part` is its **step span**, *not* clipped to the query arc
(intentional and tested — `SequencePatternWeightSpec`, `BindPatternSpec` assert a step's part extends
past a partial query). That convention is only safe for cycle-aligned queries: a step straddling a
window boundary (`sd` whole `[8.75, 9.0)`) is returned as a **full onset** (`part.begin == whole.begin`)
by *both* the `[7.998, 8.998)` and `[8.998, 9.998)` source windows → the same hit fires in cycle 8 and
cycle 9. The phantom is the same effect on the alternation's wrap-around slot (`items[(-1).mod(56)]`).

## The fix (shipped)

Clip the shifted `part` to **this query's output window** `[outStart, outEnd]`
(= `[from, to) ∩ controlEvent.part`) instead of the much wider `controlEvent.part` — in
`TimeShiftPattern.queryArcContextual`:

```kotlin
// was: val clippedPart = shiftPart.clipTo(controlEvent.part) ?: return@mapNotNull null
val clippedPart = shiftPart.clipTo(outStart, outEnd) ?: return@mapNotNull null
```

`whole` is left intact (timing/onset position preserved). For a real onset whose `whole.begin` is inside
the window, `part.begin == whole.begin` → still an onset; for a boundary slice whose onset is outside
the window, `part.begin > whole.begin` → non-onset (it already fired in the window that contains its
onset). Each onset is therefore emitted by exactly one window. Varying offsets (control patterns) stay
correct because `[outStart, outEnd]` already incorporates each control segment's span.

**Wrong layer, rejected:** clipping `part` at the leaf (`AtomicPattern`) also fixes it but **breaks the
part-span convention** (fails `SequencePatternWeightSpec`/`BindPatternSpec`). `AtomicInfinitePattern`
keeps the same unclipped convention as `AtomicPattern` — intentional, left alone.

## Files

| Concern                | File                                                                                                                                                                   |
|------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Fix                    | `sprudel/src/commonMain/kotlin/pattern/TimeShiftPattern.kt`                                                                                                            |
| Guard (new)            | `sprudel/src/commonTest/kotlin/lang/LangLateAlternationSpec.kt`                                                                                                        |
| Corrected expectations | `sprudel/src/commonTest/kotlin/lang/LangEarlySpec.kt`, `…/lang/LangOffSpec.kt` (boundary fragment now `isOnset=false`, part clipped — matches the tests' own comments) |
| Golden                 | `sprudel/src/jvmTest/resources/golden/voicedata_golden.txt` (regenerated; only `der-schmetterling` + `echo` entries changed)                                           |

## Verification

- `LangLateAlternationSpec` — per-cycle onset counts equal the no-`late` pattern; `late` shifts onset
  *times* (+0.002) not *counts*; a wide `[0,12)` query equals the per-cycle union (no cross-boundary
  dupes). Plus the by-design build-up (silent 0–7, two `sd`/cycle from 8).
- `./gradlew :sprudel:jvmTest` green (4196), incl. varying-offset `late`/`early` (pattern + continuous)
  cases → per-segment clipping covered.
- Golden diff audited: only the two time-shift corpus entries changed, all changes are the dedup/clip
  signature (`isOnset true→false` on boundary fragments, `part` clipped to the cycle boundary,
  net −2 duplicate lines). Non-time-shift entries (`ply`, `jux`, `superimpose-ply`) untouched.
- By-ear (user): pattern plays correctly now.

## Possible follow-up (not this bug)

Other non-cycle-aligned operators (`swing`, `nudge`, …) could share the same latent boundary-dedup
pattern; not audited.
