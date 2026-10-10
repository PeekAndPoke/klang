# Inharmonic partials on the sine: one node for a cluster

Status: **designed (maintainer, 2026-10-09, Q26); in progress since 2026-10-10 on branch `sine-partials` (stacked on `pitch-composition`).** Asked for by the maintainer 2026-09-30, after Der Schmetterling's
snare got its thud as a cluster of 13 hand-rolled sines (commit `20d74bb8`): "the memory consumption would
hurt on the Fairphone 4 again, so that partials config would be good ... we would also need to clarify
what happens if this is in conjunction with harmonics."

## Why

The thud today (`src/commonMain/kotlin/builtinsongs/DerSchmetterling.kt`, `metalSnare`):

```javascript
let thud  = Ignitor.sine(Ignitor.freq().mul(0.6571)).mul(0.520)
  .plus(Ignitor.sine(Ignitor.freq().mul(0.7571)).mul(0.676))
  .plus(Ignitor.sine(Ignitor.freq().mul(0.8714)).mul(-0.652))
  ... 13 sines in all, ratios 0.66 to 3.39 of the head, the signs are the start phases
  .adsr(0.0005, 0.050, 0.0, 0.02).mul(1.245)
```

- **Memory on the phone.** Every hit builds that tree anew: per sine a `Sine` node with its own frequency
  subtree, a level product and a sum, so dozens of objects per note. On the Fairphone 4, allocation and
  first-time work have been the sore spots before (the resource warehouse, `future/first-run-spike-v2.md`).
  UNVERIFIED how much this tree costs: count the nodes after the optimizer's folds and
  measure the allocation per voice build (JVM, then the maintainer on the Fairphone) before deciding.
- **CPU is not the reason.** The harmonics bank measured 22 µs per block against 24 µs for the same seven
  sines hand-rolled (`docs/benchmarks/2026-09-07_sine-partial-banks_jvm.md`): `sin()` dominates either
  way. A real speed-up for inharmonic ratios needs other maths (a rotating phasor per partial instead of
  `sin()`); the Chebyshev fast path planned for the banks (`docs/plans/sine-partial-banks.md` 5.3) only
  works for integer multiples.
- **Authoring.** Thirteen lines for one sound, and the start phase smuggled in as a sign.
- **The start phase.** Choosing each partial's sign was worth 8 dB of onset peak (all positive: the hit
  spiked 20 dB over its level; the calmest of the 8192 sign patterns: 12 dB). Free phases could do more.
  The oscillator start-phase knob was declined for the tremolo "until a real use appears, and then on the
  oscillators" (`docs/tasks-archive/2026-10/20261002-tremolo-as-composition.md`); this is the first use.

## Shape of the idea

A fourth bank on the `Ignitor.sine` builder next to `harmonics`, `octaves` and `suboctaves`
(`docs/plans/sine-partial-banks.md`), with explicit ratios and gains, perhaps phases:

```javascript
Ignitor.sine(x => x.fundamental(0).partials(
  ratios = [0.6571, 0.7571, 0.8714, ...],
  gains  = [0.520, 0.676, -0.652, ...],
))
```

One node, growth-only arrays like the other banks, band-limited at Nyquist like them.

## To decide before implementing

- **With the other banks.** Today the banks are siblings: each is a series on the sine's own frequency,
  and they are summed raw, so a partial two banks share is there twice (decided 2026-09-07; the plan's
  words: "two banks never have to pick a winner"). The maintainer's observation (2026-10-02): the existing
  banks are special cases of `partials`, each a generator of (ratio, gain) pairs:

  | bank | ratios | gains |
  |---|---|---|
  | `harmonics(n, r)` | 2, 3, ..., n + 1 | `m ^ -r` |
  | `octaves(n, r)` | 2, 4, 8, ... | `m ^ -r` |
  | `suboctaves(n, r)` | 1/2, 1/4, 1/8, ... | `m ^ -r` |
  | `fundamental(g)` | 1 | `g` |
  | `partials(ratios, gains)` | any | any |
  | `noiseBand(...)` ([`sine-noise-band.md`](../future/sine-noise-band.md)) | a geometric grid | a colour law |

  So what does `Ignitor.sine(x => x.harmonics(7).partials(...))` play? The maintainer's two options:
  1. **the last bank wins**: every bank sets the one partial list, so the `partials` replace the
     harmonics;
  2. **the sine supports all of them, summed**.

  Recommendation (Claude, for the maintainer to decide): option 2 in meaning, option 1's simplicity
  inside. Option 2 is the rule already decided for the banks; "last one wins" stays true within one knob
  (`harmonics(3).harmonics(7)` is `harmonics(7)`), but different banks are different knobs, and option 1
  would change what `harmonics(7).octaves(3)` plays today (no song combines banks yet, so nothing
  breaks). Inside the engine the sine can render ONE list, the concatenation of every bank's pairs, in
  one pass. The catch: bank knobs are signals read every block (a count or a rolloff can move), an
  explicit list is fixed at build, so the concatenation grows per block (the growth-only arrays already
  allow it), and the integer-ratio fast path (`docs/plans/sine-partial-banks.md` 5.3) applies to the
  integer banks' part only.

  Not on the table any more: partials of partials (every inharmonic partial carrying the harmonic
  series), a different instrument, better as a helper.
- **What `fundamental` means** when the cluster has no partial at ratio 1 (the thud has none).
- **Lists as knob values.** Every bank knob today is a signal read once per block; a list of ratios is
  new on the wire and on both doors (two doors, parameter parity, `/dsl-design`). Fixed at build time, or
  modulatable?
- **The phase knob.** Per partial here, and for the plain oscillators (the tremolo note): one word, one
  scale (turns or radians) on every surface. For a noise band, a sign per partial is enough: alternating
  signs come within 1.8 dB of the best pattern, and free (Schroeder) phases gained nothing in the study
  ([`sine-noise-band.md`](../future/sine-noise-band.md)).
- **Drift.** How `analog` and `analogSpread` act on inharmonic partials (the banks drift as one
  oscillator at spread 0 and each partial on its own at 1; `docs/plans/sine-partial-banks.md` section 2).
- **Decided 2026-10-02: it gets built**: the maintainer decided to build the partials knob on the sine in any case (in
  today's names it would be written `Ignitor.sine(x => x.partials())`). The allocation measurement still belongs in the record, as the before and after.

## Decided (maintainer, 2026-10-09, Q26)

- **a.** Together with the other banks, all of them play, summed (the banks' existing rule).
- **b.** `fundamental` is unchanged: the sine's own ratio-1 partial; a pure cluster writes `fundamental(0)`.
- **c.** No neighbouring arrays ("I really do not like this python style of having two arrays as neighbours"): a
  builder where every partial is added on its own. **Decided shape** ("yes this looks like the better dsl surface"):
  `partial(ratio, gain = 1.0, phase = 0.0)` per call on the sine builder, each call adds ONE partial, kept in the order
  written; `ratio` required, `phase` a fraction of one cycle (the oscillators' `phase` unit); every knob takes a number
  or a signal (D10's rule); a negative gain equals `phase = 0.5`; a resource cap of 256 partials (a song that adds more
  plays the first 256); on the wire the `Sine` node carries a list of `Partial(ratio, gain, phase)`.

  ```
  Ign.sine(x => x.fundamental(0).partial(0.6571, 0.520).partial(0.8714, 0.652, phase = 0.5))
  ```

  The Kotlin door is the same function shape as every builder door, `(OscSineBuilder) -> OscSineBuilder`, with `it`
  and a chain (the builders are immutable):

  ```kotlin
  KlangScriptIgnitor.sine(configure = {
      it.fundamental(0.0)
          .partial(ratio = 0.6571, gain = 0.52)
          .partial(ratio = 0.8714, gain = 0.652, phase = 0.5)
  })
  ```
- **d.** Optional start phases, per partial, in c's shape.
- **e.** Drift as the other banks: `analog` drifts them, `analogSpread(0)` moves the cluster as one oscillator,
  `analogSpread(1)` gives each partial its own lane ("rest of the design seems fine").

## What was done (2026-10-10, implementation; review pending)

Full worker report with every table: `tmp/reviews/partials-report.md`.

- **Surface** (Q26 c, d): `partial(ratio, gain = 1, phase = 0)` on `OscSineBuilder`, one `@KlangScript.Function` for
  both doors, each call one more `IgnitorDsl.Sine.Partial`, order kept. KlangScript calls are all positional or all
  named, so the decided example is written `partial(0.8714, 0.652, 0.5)` or fully named; the Kotlin door mixes.
  No sprudel door (the banks have none), no `audio_bridge` extension (the sine has none).
- **Wire**: `Sine.partials: List<Sine.Partial>`, a plain nested data class (not a sealed leaf, so no `@WireName`:
  the processor reads the tag only on sealed leaves). `isPlainSine()` asks for an empty list. Walk: three children
  per partial. `WIRE_SCHEMA_HASH` -1124275580 to -338100098.
- **Engine** (Q26 a, b, e): the fourth `Bank` of `PartialBankIgnitor`, sized from the list at build (arrays and
  drift lanes), the banks' loops, their raw sum, their drift blend; lanes after the banks', in list order. Every
  knob is read once per block (the banks' rule); a phase's change moves the accumulator (the start phase when
  constant) (superseded by round 1 below: a signal gain is read per sample, a later phase change glides). Silent while `|ratio * f|` is at or above Nyquist or not finite, phase held, resumed where it
  stopped (the banks' gate). A non-finite gain or phase reads as 0. Cap `SINE_MAX_PARTIALS` = 256
  (`coerceSinePartials`, `_resource_bounds.kt`): the first 256 are built, the rest not at all.
- **Measured, the thud as written against one sine with 13 partials** (final tree): optimized DSL 260 nodes (209
  distinct, 53 inner) against 65 (64 distinct, 3 inner); the voice build allocates 24.9 KB against 6.5 KB on the
  JVM and 57.3 KB against 16.4 KB on V8 (production, pinned and unpinned alike; the whole `metalSnare` 52.0 KB
  against 35.1 KB on the JVM); a block renders at 0.79 of the time on the JVM and 0.85 to 0.87 on V8, against an
  untouched control row. On V8 the bank with constant gains allocates no more than a plain sine: at engine level on
  the pre-round-1 tree (production bundle, one voice through the benchmark renderer, review round 1 reviewer B)
  2,155 bytes per block unpinned (pinned 2,131) against the plain sine's 2,142 (pinned 2,130), inside its run-to-run
  band of 1,993 to 2,199, the hand-rolled thud 2,337; re-measured on the round-2 tree, see "Review round 2, applied". The ported thud renders the original within -283 dB (RMS of the difference against the
  thud's RMS): the phases from the signs are right. Porting it would still move the song's render, because the 13
  plain sines draw from the voice rng and the bank does not, so the snare's noise gets other dice. The song is the
  maintainer's; the ported text is in the report.
- **Proof**: corpus 18 of 18 identical against `tmp/naming/corpus-pp-7c.txt`; `SineExplicitPartialsSpec` (16 oracle
  rows), rows in `SinePartialBankSpec`, `FirstBlockAllocationSpec`, `IgnitorDslWalkSpec`, `IgnitorDslWireCodecSpec`,
  `KlangScriptSineSpec`; every row red under at least one mutation (the list in the report). Benchmark cases
  `thud-13-sines` and `thud-13-partials` in `IgnitorBenchmark` (the C4 precedent).
- **Open for the maintainer**: a block-constant gain of exactly 0 at a block start holds that partial's phase (the
  banks' skip); a signal gain never skips (review round 1).

### Review round 1, applied (2026-10-10)

Reviews `tmp/reviews/partials-r1-A.md` (1 MINOR, 3 NIT) and `tmp/reviews/partials-r1-B.md` (1 MAJOR, 2 MINOR, 1 NIT);
triage by the coordinator.

- **B1 (MAJOR), a signal gain plays its envelope**: chosen option (b), a per-sample read, in a loop of its own
  (`renderPartialGained`). A gain that is not block-constant renders its block once per block into a scratch buffer
  (the pool, no allocation) and is read per sample, a non-finite sample read as 0; it is rendered every block whether
  the partial sounds or not, and the partial never skips for its gain. A block-constant gain goes through the
  unchanged loops, so it stays bit for bit and costs one null check per partial per block. Option (a), a per-block
  ramp, would have needed the same block render (a ramp to the block's END value) and is only an approximation; (b)
  is exact and no larger. Measured against the same partial as its own sine (`Ign.sine(Ign.freq().mul(1.25), x =>
  x.phase(p))` under the same `adsr`, or times the same tremolo), 48 kHz, 210 Hz: for `adsr(0.0005, 0.05, 0, 0.02)`
  at phase 0 and 0.25, `adsr(0.002, 0.3, 0, 0.02)` at phase 0.25 and a `0.5 + 0.5 sin` tremolo at 3 and 12 Hz, the
  first sound is the second sample on both, the largest sample-to-sample jump the same (0.112, 0.062, 0.034, 0.034,
  0.034), and the renders are BIT-IDENTICAL (error minus infinity dB). Before: first sound at sample 128 or 129, jumps
  up to 0.993, a 375 Hz staircase at -23.5 to -46 dB.
- **B2 (MINOR), a moving phase glides**: from the second block on, a phase change, taken the short way round the cycle
  (folded into (-0.5, 0.5]), is spread over its block as a frequency offset; the first block's change stays the start
  phase (a jump of the accumulator); a silent partial's change stays a jump (it would lose the glide). Exact without
  drift and phase modulation. A constant phase renders bit for bit as before.
- **B3 (MINOR), one Nyquist law per node**: `gate` judges the magnitude (`|f| < sampleRate / 2`, NaN silent) for the
  fundamental and all four banks; recorded in `docs/plans/sine-partial-banks.md` section 9. Corpus
  (`CORPUS_LABEL=partials-r1`): 18 of 18 identical against `tmp/naming/corpus-pp-7c.txt`.
- **B4 (NIT)**: the V8 allocation line above now quotes reviewer B's engine-level numbers.
- **A1**: a lane-order row (`harmonics(1)` plus two explicit partials at spread 1 against the reference with multiples
  `[1, 2, r1, r2]`). **A2**: the census walks only the knobs of the first 256 explicit partials (`renderedChildren`, the
  `Variants` precedent), with a row. **A3**: "harmonic" dropped from the two `analogSpread(0)` KDocs. **A4**: the
  benchmark helpers are private, "as Der Schmetterling wrote it on 2026-09-30".
- **Rows and mutations**: new rows in `SineExplicitPartialsSpec` (an LFO gain from 0, per sample; the two `adsr`
  attacks against their own sines; the 12 Hz tremolo; the phase glide with two wraps; a negative base frequency on a
  bank and on a ratio-1 partial; a gain signal's NaN and infinite samples), `SinePartialBankSpec` (lane order),
  `GraphCensusSpec` (the cap). Each red under its mutation: the gain read at the block start in the gained loop
  (3 rows), the signal path switched off (3), the per-sample guard removed (1), the fold removed (1), the glide made a
  jump (1), the signed gate (3), the resize order swapped (1), the census walking every partial (1). The two
  `FirstBlockAllocationSpec` rows now run the per-sample path (their `Hold` gains are signals) and stay at 0 bytes.
- **Suites and re-measure after round 1**: `audio_be` jvmTest 2,616 and jsBrowserTest 2,511, `audio_bridge` jvmTest 148,
  `klangscript-libs` jvmTest 853, `DslDocExamplesSpec` 1, all green; `compileTestKotlinJs` of `audio_bridge`, `audio_be`,
  `klangscript-libs` and the root, and `audio_benchmark` JVM and JS, compiled. Render per block (the thud's gains are
  constants, so it runs the unchanged loop): JVM 8.72 to 8.76 against 6.71 to 6.87 µs (0.77 to 0.79); V8 production
  14.8 to 15.6 against 13.0 to 13.3 µs, pinned and unpinned (0.83 to 0.90).

### Review round 2, applied (2026-10-10)

Review `tmp/reviews/partials-r2.md` (0 MAJOR; 2 MINOR, 2 NIT).

- **Test gaps (MINOR 1)**: four rows in `SineExplicitPartialsSpec`: a signal gain holding 0.7 renders bit for bit the
  constant 0.7 under `analog(20)` at spread 1, under a non-null `phaseMod` and under a moving sine `phase` (each with an
  engagement check that the condition moved the render); a signal gain keeps running while its partial sits past
  Nyquist for a block (an `adsr`, and a stateful 30 Hz LFO: the `adsr` reads the voice clock, so it lands right even
  when skipped, which is why the first form of this row left the reviewer's M2 green); a gain of 0 for one block while
  the phase changes, back at the new phase; a phase change in a zero-length window (reachable: a gate clipped to 0
  frames still runs the pipeline) kept as a jump. The reviewer's mutants, one lock call each, `cp` restore, `cmp` ok:
  M1 (a silent partial glides) red 1 (the gain-0 row); M2 (the gain rendered only while sounding) red 1 (the Nyquist
  row, once the LFO joined it); M3 (`offsets = null`), M4 (`pm = null`), M5 (`drift = null`) red 1 each (the held
  0.7 row), run again on the final call shape; M6 (`glide = explicitStarted`) red 1 (the zero-window row).
- **Warmup (MINOR 2)**: the explicit-partials entry has a partial with an `adsr` gain and one with `slowPhase()`, so
  `renderPartialGained` and the glide run before the first note.
- **V8 allocation (NIT 3)**: re-measured on the round-2 tree, production bundle, one built voice rendering 50,000 and
  100,000 blocks (`--trace-gc-nvp`, 1 MB semi-space, the difference over 50,000), 207.65 Hz, medians of 3, pinned and
  unpinned. Constant gains: the thud 0 and 18 bytes per block against a plain sine's 22 and 17, inside the noise band
  (about plus or minus 100). A signal gain on every partial (the thud with 13 `adsr` gains) first measured about
  1,400 to 1,560 bytes per block, against 780 to 890 for the same 13 partials as hand-rolled sines each under its
  `adsr`: the gained loop took its phase and increment as double arguments and returned a double, which V8 boxed
  per call. It now reads and writes them in the bank's arrays (an index crosses the call, no double): 845 and 849
  against 778 and 887, the same as the hand-rolled sines (the rest is the 13 envelopes' own cost). Bit for bit as
  before (the spec's rows).
- **Braces (NIT 4)**: in the glide oracle.
- **Suites**: `audio_be` jvmTest 2,620 and jsBrowserTest 2,515, all green; `audio_benchmark` JVM and JS compiled (results cleared with `find ... -name '*.xml' -delete`).

### The ported thud, for the maintainer (the song file is untouched)

Der Schmetterling's `metalSnare` thud as one sine (the same ratios, the gains' magnitudes, a negative sign as phase
0.5; renders the original within -283 dB). Porting it moves the song's corpus hash: the 13 plain sines each took
three draws from the voice's random stream, the bank takes none, so the snare's other layers get other random values.

```javascript
  let thud  = Ign.sine(x => x.fundamental(0)
      .partial(0.6571, 0.520)
      .partial(0.7571, 0.676)
      .partial(0.8714, 0.652, 0.5)
      .partial(0.9476, 0.826)
      .partial(1.0857, 0.938)
      .partial(1.2524, 1.000, 0.5)
      .partial(1.4333, 0.839)
      .partial(1.6524, 0.746)
      .partial(1.8952, 0.692, 0.5)
      .partial(2.1952, 0.591, 0.5)
      .partial(2.5333, 0.494)
      .partial(2.9381, 0.474)
      .partial(3.3905, 0.358, 0.5)
    ).adsr(0.0005, 0.050, 0.0, 0.02).mul(1.245)
```

### Review round 3 (2026-10-10)

Clean (`tmp/reviews/partials-r3.md`, 0 MAJOR, 2 NIT, applied by the coordinator: the engine bullet above marks what
round 1 superseded; the gained loop's KDoc says only the phase is written back). The reviewer's four mutants on the
holder change (the phase write-back dropped, and each array read at the wrong index) each turn a row red.

## Follow-ups

- [`sine-noise-band.md`](../future/sine-noise-band.md): a noise band as one more generator of partials, with the
  maths for the grid, the colour and the signs.
