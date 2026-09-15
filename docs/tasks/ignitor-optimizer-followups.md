# Ignitor graph optimizer — what it does NOT claim yet

The optimizer (`audio_bridge/src/commonMain/kotlin/IgnitorDslOptimizer.kt`) deliberately ships
covering the common case reliably rather than every case. This is the catalogue of what it leaves
on the table, each with the reason and the trap to watch for. Ordered by expected value.

**The invariant every entry below must respect (revised 2026-09-15):** only adjacent nodes
combine, only under linear algebra, never across a nonlinear node, never absorbing a shared
subtree, and the optimized graph renders within `OPTIMIZER_PARITY` (1e-12 relative, NaN for
NaN, infinity for infinity) of the authored one. Until 2026-09-15 the promise was bit-identity;
the maintainer replaced it with that margin ("off by a margin that has no musical meaning"), which
admits folding block-constant arithmetic and gains into neighbouring linear nodes. The guards:
`IgnitorDslOptimizerSpec` (the rule table: what folds and, the more important half, what must
not), `IgnitorDslOptimizerRenderSpec` (authored vs optimized within the margin on the shapes
people write, the warmup vocabulary, and control-rate semantics), `OptimizerSongParitySpec`
(every inlined instrument of every builtin song), `IgnitorDslOptimizerFuzzSpec` (a thousand
generated graphs with adversarial constants, the pass's laws, and `IgnitorRegistry` swallowing no
failure). Every new rule brings rows to the table and is mutation-checked against these.

## 1. R2 — parallel tap fusion (biggest win, not implemented)

> **Re-specified NON-PARITY by C2 (filter unification, 2026-08-24):** the bandpass family is
> unity-peak now (EqCore RAW_TAP, the ignitor svf kernel and SvfBPF all scale the v1 tap by
> the stored k), so R2's acceptance criterion is NO LONGER bit-parity with the legacy
> Plus/Times graph — both sides are normalised, and a fused tap must match the NORMALISED
> unfused chain. The old coupled behaviour is not an oracle for anything any more.

`Plus(base, Times(Bandpass(source, f, q, analog = 0), gain))` where the base chain reads the same
`source` is exactly a `RawTap` section, and `EqCore` already implements RAW_TAP. Without this
rule, any song that hand-built a parallel boost bank keeps paying separate `Plus`/`Times`/
`Bandpass` nodes plus a `MemoizingIgnitor` copy per extra consumer.

Der Schmetterling's guitar is the live example: it still ships the hand-built
`signal.add(signal.bandpass(...).mul(...))` form in the repo (the maintainer has a `.tap()`
migration in progress locally, but song files are the maintainer's to commit). Until either
R2 lands or that migration is committed, that song pays the unfused parallel bank — and every
other song with the shape does too. R2 is what makes them all fuse with zero edits.

Preconditions, all mandatory and all learned the hard way:
- match the tap source by REFERENCE identity (`===`), never structural equality: two structurally
  equal noise nodes are independently phased and independently seeded, so a structural match
  would fuse a tap of B onto A, audibly wrong and invisible to a structural spec;
- only from a LEFT-NESTED `Plus` spine, because IEEE addition is not associative;
- `gain` must be structurally `Constant`/`Param`, never an expression: the section resolves it
  once per block while the `Times` node multiplies per sample, so an LFO gain would become a
  staircase (this is documented on `.tap()` itself);
- `Plus`, `Times` and `Bandpass` must each be refcount-1.

## 2. Merging adjacent `Eq` nodes — WITH A REAL TRAP

`Eq(Eq(x, s1), s2)` arises from `.eq(e => e.band(a)).lowpass(b).eq(e => e.band(c))` and similar. Merging the
section lists looks trivially safe for serial sections, and is.

**It is NOT safe when `s2` contains a `RawTap`.** A tap reads the input of ITS OWN Eq. In the
nested form that input is the inner Eq's OUTPUT; after merging it would be the outer input `x`.
Different sound, no error. So this rule must refuse when the outer section list contains any tap,
or reproduce the inner chain for the tap's source, which is not free.

## 3. One-pole sections

`onepole()` (the one-pole lowpass) never fuses, because `EqCore` has no one-pole section type and
substituting an SVF would change the sound. Adding `ONEPOLE_LP` / `ONEPOLE_HP` section types is
mechanical; note that `OnePoleHPF` carries a documented cutoff bias that is deliberate raw-engine
character and must be reproduced exactly, not "fixed".

## 4. `analog > 0` filters

Permanently excluded for `Lowpass`/`Highpass`: a non-zero analog switches on the state-dependent
saturating branch, which is character `EqCore` does not implement, and the house rule is that the
Motor stays raw.

`Bandpass`/`Notch` are a different case, and the reason matters because the obvious relaxation
is a trap. At the ignitor level `saturate = analogVal > 0.0 && (mode == LOWPASS || mode ==
HIGHPASS)` (`IgnitorFilters.kt`), so `analog` contributes NOTHING to a bandpass or notch output.
It is nonetheless read every block, unconditionally — and for an EXPRESSION-backed analog that
read is a full scratch render which advances LFO phase and consumes the voice RNG stream.
Dropping it therefore breaks bit-identity and shifts every later draw, which is the bug class
this workstream already shipped once.

So: only a structurally BLOCK-CONSTANT analog on Bandpass/Notch could ever be safe to relax,
plus a proof that no read is lost. Block-constant, not literally `Constant`: a `Param`-backed
analog is equally free of scratch render, state and RNG (it reports a control-rate scalar, which
is exactly why `EqIgnitor.Section.isStatic` groups `ParamIgnitor` with `ConstantIgnitor` and why
`SvfIgnitor` caches on `is ParamIgnitor`). Do not relax it on the grounds that "analog does
nothing here".

## 5. Pitch-mod nodes are walls, but they vanish at runtime (a real missed win)

`x.lowpass(a).vibrato(5, 0.2).lowpass(b)` emits TWO Eqs, because the optimizer treats `Vibrato`
as an opaque node. But `Vibrato` never becomes an Ignitor: `buildIgnitor` absorbs it into
`accumulatedMod` and bubbles it to the source, so at RUNTIME the two filters are adjacent and
could have been one Eq. Same for `Accelerate`, `PitchEnvelope`, `PitchMod` and `Fm`.

Fusing across them looks bit-safe on inspection — the fused form threads the mod through
`inner.withMod()` while section params stay `noMod()`, which is the shape `EqIgnitorSpec`
already pins — but "looks safe" is not the standard here, and it is not claimed. Anyone
implementing it must prove the mod-threading equivalence with a rendered parity row per
pitch-mod node type, not by reading the builder.

⚠ And it carries the trap that already bit this pass once with `OptimizerHint`: a pitch-mod
node that vanishes at runtime is refcount-1 EVEN WHEN THE NODE BELOW IT IS SHARED, so a guard
that refcounts `original.filterInner()` would wave a shared subtree straight through and fork
it. Any see-through rule must require EVERY unwrapped link to be exclusively owned, not just
the last — see the `while (originalInner is OptimizerHint && ...)` loop in the optimizer.

## 6. `passes` expansion

Not applicable yet — the field does not exist. When `passes` lands (plan D6), the rule "R1 learns
to expand `passes = N` into N sections in the SAME commit that adds the field" is the protection
against a window where a `passes = 2` filter fuses as one section and quietly loses 6 dB/octave.

## 7. Variants

Each variant subtree is rewritten independently, which is correct, but sections are never shared
between variants even when identical. Harmless; noted only so nobody assumes otherwise.

## 8. Cross-`Eq` section deduplication

Two identical sections in one list (the song really does write `.lowpass(5250).lowpass(5250)`)
are kept as two sections, correctly: cascading two identical filters is a steeper slope, not a
redundancy. Do NOT "optimize" this away.

## Measurement note

Before claiming a win from any of the above, read the D0 comparability rule in
`audio_benchmark/src/commonMain/kotlin/EffectBenchmark.kt`: the chained benchmark rows render a
source inside the timed step while the EqCore rows only copy, so raw row ratios overstate the win
substantially. Subtract the source baseline. This has already caused two wrong numbers to reach
user-facing docs.

## Next up (maintainer, 2026-09-15): the Schmetterling guitar stages on the Fairphone 4

After the 2026-09-15 CPU round (voice culling, the polynomial sine, `fastExp2`/`fastExp`) the FF4
can barely run Der Schmetterling again. The maintainer's read: the bottleneck is now the memory
footprint of the guitars' filter stages (pickup, pedal, preamp, power, cab: each a chain of EQ
and body sections, times the unison voices), not the per-sample arithmetic. The request: revisit
this optimizer, especially the rule that combines adjacent EQs into one `EqCore`, against the
current guitar rigs in `DerSchmetterling.kt`, and look for more that can be combined: which stages
sit adjacent and linear (fusible under the no-reorder invariant above), which are separated only
by a gain or a clip, and what an audit of the actual rendered graph (node count, filter section
count, scratch buffers per voice) says. Measure before and after with the rig suite
(`./gradlew runSongBenchmark --args=rig`) and on the device.

### Census and costs, 2026-09-15 (Der Schmetterling, rhythm rig, seeded rig suite `2026-09-15_172412`)

One rhythm guitar note's graph after the optimizer: 90 nodes, 11 `Eq` carrying 18 sections (the
serial fusion already collapsed every one of the 9 standalone `Lowpass`/`Highpass` into them),
5 `Drive` + 5 `Shape` (the distortion stages: 2x, 4x, 4x, 4x, 2x), 7 `Times` (the `mul` level
knobs), 2 `Plus`, 2 `Adsr`, `PitchEnvelope`, `Crackle`, `SuperSaw`; about 35 passes over the
block per note. The unison count is NOT a factor: 13+11 -> 7+7 voices moved the rhythm guitars
from 0.0395 to 0.0392 RTF (the stack is one node; everything after it runs once per note).

Where the rhythm guitars' 0.040 RTF goes:

| slice | RTF | share | how measured |
|---|---|---|---|
| analog drift (`analog(feel)`, feel = 15) | 0.0075 | 19 % | `rhythm: no analog` |
| the rig: preamp 0.0050, pedal 0.0028, power 0.0021, cab 0.0019, pickup 0.0007 | 0.012 | 30 % | one stage stock at a time |
| string extras (pitch envelope, crackle burst) | 0.0025 | 6 % | `rhythm: no string extras` |
| the rest: supersaw stack, ADSR, stock chain, per-node overhead | 0.018 | 45 % | remainder |

The rig's cost is in its distortion stages, not its EQs: a 4x stage is one linear-interpolation
upsample, the shaper on 512 samples and two polyphase half-band decimators (9 multiply-adds per
output each), roughly nine filter sections' worth; the decimator is already polyphase.

What is left for the optimizer on the EQ side is small: the 7 `Times` walls (a pass each to
scale a block; foldable into the neighbouring Eq's output gain only by giving up the
bit-identity invariant above), the `Drive` pass in front of every `Shape` (foldable into the
shaper's oversampled loop), and the 3 tone-stack bands at 0 dB (a passthrough branch per
sample, still one Eq pass). A rule that fuses two oversampled stages separated by a linear
section into ONE up/down cycle (`Shape(hp(Shape(x)))` with the highpass run at 4x) would remove
a decimator pair per fused pair, at the price of the invariant and of running the interstage
filter at the oversampled rate.

The analog drift (DONE 2026-09-15, same day: every lane steps per block and ramps across it,
the rhythm rig 0.040 -> 0.036 with the drift now free, the live song -8 %) was the largest
single general item: `DriftLanes` stepped every lane per sample
(`AnalogDrift.nextMultiplier`: xorshift, two one-poles, the blend), 13 lanes per note here, for
a modulation whose fastest layer has a 50 ms time constant. Stepping the lanes once per block
and interpolating the multiplier across it is the candidate; not bit-identical, audibly the same
wander (the per-sample residue is noise sidebands far below the drift depth), to be settled by
ear on the guitars and the marimba (`lead: no analog` is 13 %, `trommel: no analog` 15 %).

## The arithmetic folds (plan, 2026-09-15, maintainer: "folding pure arithmetic into each ignitor")

Step 0 (DONE 2026-09-15): the invariant above, `OPTIMIZER_PARITY`, the corpora and the fuzz.
Each later step is its own deliverable: rule-table rows first (red), then the rule, its render
parity, a mutation check, a rig A/B, a commit.

1. DONE 2026-09-15: `IgnitorDsl.Affine(inner, pre, mul, add)` = `mul · (x + pre) + add`, one
   pass, sanitised exactly like the `Plus`/`Times`/`Plus` chain (`safeOut(mul · (x + pre)) + add`),
   an absent pre-add or add being `Constant(-0.0)`, the bitwise identity of the add (`+ 0.0` would
   turn `-0.0` into `+0.0`); `mul` has no default. The pre-add exists because review showed that
   folding `x.add(b).mul(a)` as `a·x + a·b` cancels near `x = -b`, every zero crossing of an
   offset-then-scale shape, which no relative margin survives. Wire type, KSP codec, walk arms,
   warmup vocabulary (all three fast shapes), `EqIgnitor` sees through it for the static
   cascade q, `AffineIgnitorSpec` (both orders bit for bit, the sign of zero included).
2. DONE 2026-09-15 (rule R2 in `IgnitorDslOptimizer.kt`; 14 rule-table rows, 5 render-parity
   rows, the fuzz and the song corpus at the margin, nine mutations red). Measured (rig and live
   A/B, seeded): no change on its own (rhythm rig 0.0345 -> 0.0348, `all stock` 0.0219 ->
   0.0219, the live song 0.0786 -> 0.0791, all inside noise), and that is the arithmetic: a lone
   `mul(k)` was already one pass (`generate` plus an in-place multiply) and an Affine over it is
   one pass with two more adds. The rule pays only where it merges (`mul` then `add` in one pass,
   a composed run), which the guitar has few of; its job is to be the shape steps 3 and 4 fold
   into the Eq and the shaper, where the pass disappears. One thing the fuzz taught the rule: a
   scalar on the LEFT of a multiply or an add that carries a `Param` stays where it was written,
   because an Affine lists its signal's params before its coefficients' and first-occurrence
   order is a contract the UI keys on. The rule, as derived by the round-2 review of step 1
   (what one node covers exactly, what composition loses, where the clamp bites):
   - One node folds exactly the authored shape `x [.add(p)] .mul(m) [.add(a)]`: at most one add
     before the multiply, exactly one multiply, at most one add after. `minus(b)` folds as
     `pre = -b` (a bare subtract is a bare add of the negation, bitwise); a leading
     `Constant(k).plus(x)` as `pre = k`.
   - Two nodes MUST NOT merge across an addition: not an inner `add` with an outer `pre`, not an
     inner `add` pushed through an outer `mul`, not two adjacent adds. An addition fold has no
     relative bound (cancellation at the sample; measured 4.6e-2 relative on ordinary values). A
     second add stays a second `Affine`, still cheaper than the chain.
   - A chain with no multiply stays `Plus`: `mul = 1` would insert the clamp `Plus` refuses.
     `neg()` is not `mul = -1` for the same reason (and it maps `+0.0` to `-0.0`, bare).
   - A run of literal-constant multiplies composes into one `mul` only when the chain's
     intermediate clamp cannot differ from the fold's: the condition is on `x · product`, not on
     the product (`m1 = 100, m2 = 0.01` with `x = 1e14` differs by 0.9 relative). With the run's
     input bounded by `SAFE_MAX` (the output of a clamping op or a bounded oscillator): every
     prefix product `|m1 … mk| <= 1`, an attenuating run, composes freely; otherwise only a purely
     growing run (`|m| >= 1` throughout); the mixed run, up then down, does not compose. Rounding
     is never the binding constraint (about `2n` ulp for `n` factors). A `Param` multiply folds
     alone (its value is unknown at optimize time).
   - A rule builds an ABSENT pre-add or add as the node's default (`Constant(-0.0)`), never as an
     explicit `Constant(0.0)`, which would reintroduce the sign-of-zero divergence the node's
     design removed; the rule-table row for the forward fold must assert the default.
   - `mul(lfo)`, `add(signal)` stay `Times`/`Plus`; refcount 1 only; never elide a multiply
     because its constant is 0: `x · 0.0` is `-0.0` for every negative sample and `+0.0` for
     every positive one, which a `Silence` cannot reproduce, and the upstream's state advances
     (a NaN or infinite upstream is already scrubbed to 0 by the multiply's `safeOut`, so that is
     not the reason); `safeOut` once, at the multiply.
3. WON'T IMPLEMENT (2026-09-15, measured): `Affine` into `Eq` (an input gain/offset in the first
   section's read, an output gain/offset in the last section's write, the serial rule fusing the
   Eqs on both sides of a former `mul` wall). The ceiling was measured before touching EqCore's
   section-major loops: `audio_benchmark` rows `guitar-rig*` build the rhythm rig of Der
   Schmetterling as an inline tree and DELETE the nodes outright, which is more than any fold can
   buy. Node 24, one voice, µs per block (three runs, the spread is about 1 µs):

   | row                          | µs/block | what it says                                   |
   |------------------------------|---------:|------------------------------------------------|
   | `guitar-rig-string-only`     |     18.7 | the 19-voice supersaw, burst and envelope       |
   | `guitar-rig`                 |     53.2 | the rig adds 34.5                               |
   | `guitar-rig-no-mul`          |     54.3 | all seven level knobs deleted: nothing          |
   | `guitar-rig-no-drive`        |     53.3 | all five drives deleted: nothing                |
   | `guitar-rig-no-mul-no-drive` |     52.3 | both: about 1 to 2, under 4% of the voice       |
   | `guitar-rig-no-oversample`   |     31.6 | every shaper at 1x: 21.6, 63% of the rig's cost (the upsampler, the FIR stages and the 2x to 4x shaper evaluations together) |

   JVM agrees (29.7 / 30.5 / 30.9 / 28.4 / 20.6 / 10.2 in the same order). A multiply pass over
   128 doubles is nothing next to five oversampled shapers and a dozen filter passes; ten loop
   variants in `EqCore` and a Shape input gain would buy a rounding error. Oversampling is the
   cost, and its decimator was the general target that paid (the polyphase pass in
   `Oversampler.kt`, 2026-09-15: the rig 53 -> 44 µs on node); the shaper evaluations at the
   high rate are the rest of that row and stay.
4. WON'T IMPLEMENT (2026-09-15, same measurement): `Affine` into `Shape` as an input gain where
   the upsampler reads its input. The `Drive` pass it would remove is the `no-drive` row: nothing.
5. Dead and identity nodes (maintainer, 2026-09-15): `add(Constant(0))` and `mul(Constant(1))`
   drop; `mul(Constant(0))` makes its upstream `Silence`. And at BUILD time, where `Osc.param`
   values are known (per voice, constant for the voice): a `Times` whose block-constant operand
   is exactly 0 never renders its upstream, a `Plus` skips a dead branch, so a stage switched off
   by a param costs nothing instead of being rendered and zeroed. Two consequences the maintainer
   accepted in principle: the zeros lose the sign the upstream sample would have given them
   (`x · 0.0` is `±0.0` with `x`'s sign; a NaN or infinite upstream was already 0 through the
   multiply's `safeOut`), and a dead branch with a noise node stops drawing from the voice's
   stream (other noise in the voice gets a different, still seeded, realisation).
6. The remaining linear neighbours only if the numbers say so (`Affine` into `Adsr`, the tap
   gain); then MEMORY, this catalogue, the census redone, a before/after on the device.
