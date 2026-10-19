# `SvfIgnitor`'s coefficient cache never engages for DSL-authored filters

**Status:** CLOSED 2026-10-19, measured, not kept: the fix was built and the win does not show in a real song,
so by this task's own rule the hottest path stays as it was (see "Closed 2026-10-19" at the end). Found during the C5 review (2026-08-24), deliberately rejected
as out of scope there — it is the hottest path in the engine and deserved its own round.

## The bug

`audio_be/src/commonMain/kotlin/ignitor/IgnitorFilters.kt:169`:

```kotlin
} else if (!initialized || cutoffHz !is ParamIgnitor || q !is ParamIgnitor) {
    computeSvfCoeffs(baseCutoff, qVal, sr, coefs)   // a tan() + several divides
    initialized = true
} else {
    a1 = coefs.a1; ...                              // the cached path
}
```

The cached path is only reachable when BOTH operands are a `ParamIgnitor`. But a filter written
in a song never has one: `IgnitorDsl.Constant` builds a **`ConstantIgnitor`**, so the predicate
is false and `computeSvfCoeffs` re-runs **every block, for every SVF filter, on every voice** —
to recompute a number that provably cannot change.

The asymmetry is easy to miss: `ParamIgnitor` DOES appear when the engine's own convenience door
is used (`Ignitor.lowpass(cutoffHz: Double, …)` wraps in `ParamIgnitor("cutoffHz", …)`), which is
what tests and benchmarks call. So the cache looks like it works whenever you go looking at it,
and is dead for the actual corpus. C5 made it worse in proportion: a `passes = 4` cascade pays
the cost four times.

## The fix

Widen the predicate to the same notion `EqIgnitor.Section.isVoiceConstant` already uses — and it
is already correct there, including the two subtleties C5 had to fix:

- `ParamIgnitor` **or** `ConstantIgnitor` (both are per-voice constants)
- look THROUGH `MemoizingIgnitor`, which `buildIgnitor` wraps around every non-leaf node
- a `TimesIgnitor` of two voice-constants is voice-constant

Do it by **extracting that predicate into one shared internal helper** and calling it from both
places, rather than copying it. Two copies of this rule already drifted once; the whole point of
the filter-unification workstream was that one concept gets one implementation.

Output-identical by construction: `computeSvfCoeffs` is pure, so caching a result whose inputs
cannot change for the voice's lifetime returns the same bits.

## Traps

- **`FreqIgnitor` must stay excluded.** It is block-constant but NOT voice-constant (`.detune(lfo)`
  changes `freqHz` per block), so admitting it would freeze a note-tracking cutoff at block 0.
  `EqIgnitor`'s KDoc already spells this out — inherit the reasoning, do not re-derive it.
- **The `hasEnv` branch must keep recomputing.** It computes both ends of the block and ramps
  between them; it is not a cache candidate.
- **`analog` is not part of the cached set.** `driveScale` is derived per block outside it, and
  the coefficients depend only on cutoff/q, so a modulated `analog` is unaffected either way.
- The per-voice `cutoffOffsetMul` is folded in once at construction, not per block, so it does
  not make the cutoff voice-variable.

## How to size it

Measure before/after with the song benchmark (`runSongBenchmark`, real songs, per-block timing)
rather than reasoning about `tan()` cost — every SVF filter on every voice is a big multiplier,
but the block is 128 frames, so the per-sample share is small. If the win does not show up in a
real song, say so and close this; the tidiness argument alone does not justify touching the
hottest path in the engine.

Related: `docs/tasks-archive/2026-09/20260927-filter-unification.md` (C5 review, finding rejected as out-of-scope),
`docs/tasks/future/ignitor-optimizer-open-items.md` §8 (`analog > 0` filters) (which cites this same `is ParamIgnitor` check
while arguing a DIFFERENT relaxation is unsafe — read it before touching either).

## Closed 2026-10-19: built, measured, not kept

**The bug was still there** (the line numbers above are stale): `IgnitorFilters.kt` held
`} else if (!initialized || hasDrift || cutoffHz !is ParamIgnitor || q !is ParamIgnitor) {`.

**The fix, as planned, built and then taken out again.** One predicate, `Ignitor.isVoiceConstant()` in `audio_be/.../ignitor/_voice_constant.kt`
(Param or Constant, through `MemoizingIgnitor`, a `TimesIgnitor` or `AffineIgnitor` of voice-constants, never
`FreqIgnitor`), moved out of `EqIgnitor.Section`, which now calls it, and asked once at construction by
`SvfIgnitor` (`voiceConstantCoeffs`). The cutoff and q are still read every block; only `computeSvfCoeffs` is
skipped. No API change, no sound change. `SvfIgnitor` went from `private` to `internal` for its white-box counter
`staticCoeffComputes` (the `EqIgnitor.staticConfigureSkips` precedent). Guard: `SvfCoefficientCacheSpec` (the
predicate row by row, the cache engaging for a DSL-authored constant filter and for a slot-driven `passes` cascade,
bit identity against a node that recomputes, a `FreqIgnitor` cutoff that keeps tracking), every row
mutation-checked. The existing `EqIgnitorSpec` parity rows now compare a CACHED chained SVF against the fused core,
bit for bit.

**What it reaches in the corpus: one song.** A census of every `SvfIgnitor` block (temporary instrumentation, all 15
built-in songs, 8 cycles, 4 passes): 14 songs compute exactly as often as before. Their SVF nodes are classic-tail
filters (Param-backed, already cached), filters with a drift lane or an envelope (recomputed by design), or
note-tracking cutoffs. A filter a song writes without `analog`, `env` or `humanize` never builds an SVF node at all:
the optimizer fuses it into an `EqIgnitor`, whose cache was already right. Only "A Synth Worth Lying For" changes:
its `.lowpass(pBrightness, 1.8, x => x.analog(pAnalog))` (a Param cutoff with a literal q) is now cached, 360 of
its 3112 SVF nodes, and its static coefficient computations fall from 488,568 to 424,656 (13 percent fewer).

**Timing** (`runSongBenchmark`, the two songs below, 16 cycles, 1 warm-up and 3 measured passes per run, 6 rounds
interleaved before/after in separate JVMs inside one build lock, the same binary switched at voice build; medRTF,
lower is better):

| song                                  | before median | after median | before range    | after range     |
|---------------------------------------|---------------|--------------|-----------------|-----------------|
| A Synth Worth Lying For (the one hit) | 0.0682        | 0.0669       | 0.0629 to 0.0717 | 0.0617 to 0.0917 |
| Der Schmetterling (control, unmoved)  | 0.1156        | 0.1117       | 0.1041 to 0.1439 | 0.1032 to 0.1552 |

The affected song moved 1.9 percent, the control, whose work did not change at all, 3.3 percent: the run-to-run
spread (14 to 50 percent between the fastest and slowest run) swallows it. Estimated from the counts, the saved work is about 32,000 `tan` setups per
16-cycle pass against about 2 seconds of render, well under 0.1 percent. **Verdict: the win does not show in a real
song.** What the change buys is one predicate instead of two drifted copies and a flat cost for an authored
`analog` filter with constant knobs and `passes > 1` (no song writes one today). By the rule in "How to size it"
the change was not merged; the code stays as it was. The patch (the predicate, the SVF change and the spec, 11
mutants all RED) is kept locally in `tmp/reviews/svf-coefficient-cache-2026-10-19.patch`, for the day a song
writes such a filter or `EqIgnitor`'s predicate is touched anyway.
