# `SvfIgnitor`'s coefficient cache never engages for DSL-authored filters

**Status:** OPEN, not started. Found during the C5 review (2026-08-24), deliberately rejected
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

Related: `docs/plans/filter-unification.md` (C5 review, finding rejected as out-of-scope),
`docs/tasks/ignitor-optimizer-followups.md` §4 (which cites this same `is ParamIgnitor` check
while arguing a DIFFERENT relaxation is unsafe — read it before touching either).
