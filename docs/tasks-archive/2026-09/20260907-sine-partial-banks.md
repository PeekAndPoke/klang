# Sine partial banks: `harmonics`, `octaves`, `suboctaves`, `fundamental`, `analogSpread` on `Osc.sine()`

> Archived 2026-09-07. Status: **COMPLETE 2026-09-07.** Built, review-looped (two rounds, coding
> and audio reviewer, 14 mutation checks red) and committed on branch `harmonics`: `6d4056f9`
> (the feature), `a0ca2abd` (plan marked shipped), `39119aef` (benchmark harness fix found on the
> way). The design record stays in `docs/plans/sine-partial-banks.md`, kept in `docs/plans/`
> because eight files cite that path (the wire node's KDoc, the engine, the builders, the spec,
> both module memories); this file is the archive pointer and the closing notes.

## What shipped

- `Osc.sine(freq, x => x.harmonics(count, rolloff).octaves(count, rolloff).suboctaves(count, rolloff).fundamental(gain).analogSpread(s))`
  on both doors, `KlangScriptSineSpec` pinning parity.
- `IgnitorDsl.Sine` grew eight fields with literal defaults; `Ignitors.sinePartials` renders the
  sine and its partials in one loop per partial, each bank owning its state; the runtime keeps
  the old `SineIgnitor` for literal defaults (`Sine.isPlainSine()`), so no existing sine changed.
- Der Schmetterling's hand-rolled seven-sine bass stack became one line; `SinePartialBankSpec`'s
  golden test pins it to the tree at 1e-12.
- Decided along the way (all in the plan): knobs on the sine rather than separate doors; counts
  are partials added above or below, 0 = off; `fundamental` is a gain, not a boolean; banks sum
  without deduplication; sub-harmonics won't-implement; band-limit at Nyquist; raw sum;
  `analogSpread` blends one shared drift walk against one walk per partial with constant-power
  weights, default 1.

## Closing notes, for the next reader

- **The "off tune" hunt of the evening was not the engine.** After the migration the bass sounded
  a semitone off in the browser. Every link was verified exact before the cause surfaced: DSL
  construction with both libraries imported, the sprudel note path (`note`, `n` plus `scale`,
  `transpose`), the JVM engine, the JS engine under Node, and the main-bundle-to-worklet wire
  round trip. The cause was in the song: the bass had been moved out of the pattern stack that
  carries `.transpose(transposition)`, so it alone lost the transposition. Lesson: when one
  instrument is off by a musical interval against the others, check what the arrangement applies
  to the others first.
- **The benchmark harness rendered every voice twice per block** (`scheduler.process()` followed by
  `renderBlock()`, which runs the scheduler again). Fixed in `39119aef`; the numbers recorded in
  `docs/benchmarks/2026-09-07_sine-partial-banks_jvm.md` are annotated as relative only.
- **The JS test compile of `audio_be` is broken by four pre-existing test files** that implement a
  function interface (`MasterRingShelfSpec`, `LazyReverbSpec`, `LazyRingSpec`,
  `ResourceWarehouseSpec`), which Kotlin/JS forbids. The engine has no JS test run today; the
  Node benchmark was used as the JS oracle instead. Worth its own small task.
- **The JVM playground main (`src/jvmMain/kotlin/Main.kt`) was silent on the maintainer's machine**
  even though READY arrived and the offline renderer of the same file is loud. Not chased; the
  live JVM audio line or the live scheduling path, not the engine.
- Open from the plan: the by-ear checks of section 8 and the gated 5.3 fast path (two trig calls
  per sample regardless of partial count).

## What we built

Designed and built together on 2026-09-06 and 2026-09-07 from a hand-rolled stack in the bass
and the question "could we make an operator out of this".
