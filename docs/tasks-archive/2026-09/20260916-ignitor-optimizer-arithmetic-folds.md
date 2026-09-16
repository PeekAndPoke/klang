# Ignitor graph optimizer: the arithmetic folds and the margin (2026-09-15 / 16)

> Archived 2026-09-16. Status: **BUILT**, steps 0, 1, 2 and 4b; steps 3 and 4 **WON'T IMPLEMENT**
> by measurement; the per-block half of step 5 landed inside 4b. What stays open moved to
> `docs/tasks/future/ignitor-optimizer-open-items.md`. Commits: `bb9bdbb3` (step 0), `a645a97d`
> (step 1), `1056a1c0` (step 2), `6c1ae38b` (the oversampler's polyphase decimator, found by the
> step-3 measurement), `841cdb8b` (step 4b), `e861b139` (the chain fusion parked). The optimizer
> itself: `audio_bridge/src/commonMain/kotlin/IgnitorDslOptimizer.kt`; the memory entries in
> `audio/MEMORY.md`.
>
> The one-paragraph version. The optimizer's promise moved from bit-identity to a margin
> (`OPTIMIZER_PARITY`, 1e-12 of the block's loudest sample, at most full scale), with a harness
> that holds every rule to it (rule table, render parity, every builtin song, a seeded fuzz with
> adversarial constants). On that promise the arithmetic of a chain folds into one `Affine` pass,
> `mul · (x + pre) + add`, including `div`, `minus` and `neg`, and a zero divisor or multiplier is
> a dead branch. The folds into the Eq and the shaper were measured before being built and turned
> out to be worth under 4 % of a guitar voice; the same measurement put the cost in the shapers'
> oversampling, and the decimator rewrite that followed took the guitar voice from 53 to 44 µs per
> block on node. Three review rounds on 4b taught the rule that matters for any future fold: every
> coefficient the optimizer SYNTHESIZES must be zero exactly when the authored one is, or a dead
> branch renders on one side only and the voice's noise stream slips.

## The invariant

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
(`AnalogDrift.nextMultiplier`: xorshift, two one-poles, the blend), 20 lanes per note here (the stack's 19 unison voices and the shared lane), for
a modulation whose fastest layer has a 50 ms time constant. Stepping the lanes once per block
and interpolating the multiplier across it is the candidate; not bit-identical, audibly the same
wander (the per-sample residue is noise sidebands far below the drift depth), to be settled by
ear on the guitars and the marimba (`lead: no analog` is 13 %, `trommel: no analog` 15 %).

## Measurement note

Before claiming a win from any of the above, read the D0 comparability rule in
`audio_benchmark/src/commonMain/kotlin/EffectBenchmark.kt`: the chained benchmark rows render a
source inside the timed step while the EqCore rows only copy, so raw row ratios overstate the win
substantially. Subtract the source baseline. This has already caused two wrong numbers to reach
user-facing docs.

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

   JVM agrees (in the table's order: 10.2 / 29.7 / 30.5 / 30.9 / 28.4 / 20.6). A multiply pass over
   128 doubles is nothing next to five oversampled shapers and a dozen filter passes; the eight
   loop bodies in `EqCore` and a Shape input gain would buy a rounding error. Oversampling is the
   cost, and its decimator was the general target that paid (the polyphase pass in
   `Oversampler.kt`, 2026-09-15: the rig 53 -> 44 µs on node); the shaper evaluations at the
   high rate are the rest of that row and stay.
4. WON'T IMPLEMENT (2026-09-15, same measurement): `Affine` into `Shape` as an input gain where
   the upsampler reads its input. The `Drive` pass it would remove is the `no-drive` row: nothing.
4b. DONE 2026-09-15: `div`, `minus` and `neg` in the current form (maintainer: "fully support
   div() and minus() in the current form", "neg() is an alias of mul(-1) and we should not even
   have a dedicated ignitor for it", "div(0) should result in a constant(0) no matter what the
   graph before is"). The engine first: `DivIgnitor` renders a divisor of exactly zero as zero
   (a block-constant zero is a dead branch, nothing upstream renders; a zero sample in a divisor
   signal zeroes that sample; tiny non-zero divisors keep the `SAFE_MIN` clamp), `Ignitor.div(0.0)`
   is silence, `Ignitor.neg()` is `mul(-1.0)` and `NegIgnitor` is gone. And, so that a Param
   divisor at zero is the same dead branch after the fold: a block-constant multiplier of exactly
   zero is a dead branch in `Times`, the scalar `mul(0.0)` door and `Affine` as well (the per-block
   half of step 5, found by the round-1 review: the authored `Div` skipped the upstream noise, the
   optimized `Affine` drew from the voice's stream, and the next noise node read a different
   position). Then the rules: a subtract after the multiply fills the add with `-k`, bare: a literal
   negated, anything else as `-0.0 - k` (a `Neg` is a multiply now and would scrub a NaN the
   subtract passes through); `x / k` is a multiply by the expression `1 / k` (the runtime's own
   guard, once per block; a literal `div(4)` therefore does not compose with a following literal
   run, which would need the safety constants in the bridge); `x / 0` literal is a multiply by a
   literal zero, a dead branch that is still BUILT (a bare `Constant(0)` would skip the build-time
   draws of a phase pool under it and shift every pool built after it, round 2); a block-constant
   INFINITE divisor is the same dead branch in the engine, since its reciprocal is a zero
   multiplier (round 2); a literal run whose product underflows to zero does not compose, since
   that would be a dead branch the chain never was (round 2);
   `neg()` is a multiply by `-1` that composes with a literal it FOLLOWS (an outer sign flip keeps
   every magnitude the chain clamps; an inner one before an attenuation does not, the chain clamps
   the input first, so that stays two nodes unless the input is clamped). Not folded: `k - x` (it
   would put a clamp on a bare subtract, and cost more than the subtract), `k / x`, `x / lfo`, a
   scalar-only negation.
   The parity oracle moved with it: RELATIVE TO THE BLOCK'S LOUDEST SAMPLE, AT MOST FULL SCALE,
   instead of to the sample itself, because the reciprocal multiply is an ulp off and a filter
   carries that to a zero crossing (fuzz seed 433 found it within the first run); the cap keeps
   the law in a saturated block (both reviewers: one sample at SAFE_MAX must not buy the musical
   samples next to it a tolerance of 1e3), and every corpus passes with it. The laws did not
   move in the end (constancy and params equal on both sides, since the zero divisor keeps its
   subtree). The rules did not move: still nothing merges across an addition;
   `mul(2).add(2).mul(2).add(2)` stays two Affines. Merging them, algebraically or as a fused
   runtime pass, is parked with its reasoning in `docs/tasks/future/affine-chain-fusion.md`
   (maintainer, 2026-09-16: last in line, more complexity than gain).
