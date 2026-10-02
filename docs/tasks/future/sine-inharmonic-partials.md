# Inharmonic partials on the sine: one node for a cluster

Status: **future, idea, not started.** Asked for by the maintainer 2026-09-30, after Der Schmetterling's
snare got its thud as a cluster of 13 hand-rolled sines (commit `20d74bb8`): "the memory consumption would
hurt on the Fairphone 4 again, so that partials config would be good ... we would also need to clarify
what happens if this is in conjunction with harmonics."

## Why

The thud today (`src/commonMain/kotlin/builtinsongs/DerSchmetterling.kt`, `metalSnare`):

```javascript
let thud  = Osc.sine(Osc.freq().mul(0.6571)).mul(0.520)
  .plus(Osc.sine(Osc.freq().mul(0.7571)).mul(0.676))
  .plus(Osc.sine(Osc.freq().mul(0.8714)).mul(-0.652))
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
  oscillators" (`docs/tasks/tremolo-as-composition.md`); this is the first use.

## Shape of the idea

A fourth bank on the `Osc.sine` builder next to `harmonics`, `octaves` and `suboctaves`
(`docs/plans/sine-partial-banks.md`), with explicit ratios and gains, perhaps phases:

```javascript
Osc.sine(x => x.fundamental(0).partials(
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
  | `noiseBand(...)` ([`sine-noise-band.md`](sine-noise-band.md)) | a geometric grid | a colour law |

  So what does `Osc.sine(x => x.harmonics(7).partials(...))` play? The maintainer's two options:
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
  ([`sine-noise-band.md`](sine-noise-band.md)).
- **Drift.** How `analog` and `analogSpread` act on inharmonic partials (the banks drift as one
  oscillator at spread 0 and each partial on its own at 1; `docs/plans/sine-partial-banks.md` section 2).
- **Decided 2026-10-02: it gets built** ("we will build the `Osc.sine(x => x.partials())` in any case",
  the maintainer). The allocation measurement still belongs in the record, as the before and after.

## Follow-ups

- [`sine-noise-band.md`](sine-noise-band.md): a noise band as one more generator of partials, with the
  maths for the grid, the colour and the signs.
