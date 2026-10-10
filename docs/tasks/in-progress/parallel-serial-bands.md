# `parallel`, `serial` and `bands`: branches side by side, and frequency bands

Status: **built, all five steps, on the branch `parallel-serial-bands` (2026-10-10); waiting for the maintainer's ear
and the open decisions below before it is archived.** The corpus renders bit-identical to the branch point on the JVM
(18 rows: raw, pcm and edge hashes). In the browser a `let` used twice now builds once (`@WireShared`), as on the JVM.
The design, the decisions and the reasons live in
[`../../plans/future/signal-graph-engine.md`](../../plans/future/signal-graph-engine.md) §6.9; this file holds the steps.

## Why

Listening to the Katalyst `distort` stage on Kokon's master, the maintainer heard the glue, and the hats and the kick
distorted with it. Engineers solve that two ways: saturating only some bands, or saturating per group. One operator,
`parallel`, gives multiband saturation, the exciter, parallel compression, parallel saturation and any dry/wet as
one-liners (the plan's back-pocket table, also material for the tutorials).

## Decided (maintainer, 2026-10-09)

- `parallel` and `serial` are the names. `through` retires for `serial`: the same behaviour, removed, not deprecated.
- `parallel` SUMS its branches; an empty `parallel()` returns the signal unchanged; one branch is that branch's output.
- `bands` keeps its name: `b.band(...).cut(f).band(...)`, read from the bottom up, one builder type. `band().band()`
  sums both processors on one band, an unprocessed band passes untouched, and a cut below the one before it is
  coerced up to it.
- Every step ships something to hear: recipes in the writing references and listening material for the maintainer.
- The credits land with the code that uses them (Linkwitz and Riley with `bands`).

## Steps

1. **`serial`:** the rename on both hosts (Ignitor and Katalyst). This migrates the built-in songs (Kokon 4 calls,
   Der Schmetterling 2), both doors, the parity spec, the two writing references and a benchmark case, and adds an
   entry in `docs/retired-names.md`.
2. **`parallel` on the Ignitor:** the sum law, the empty identity, and branches ALIGNED BY LATENCY (an oversampled
   branch is 4 to 6 samples late; Kokon's Screamer pedal shows the comb this avoids).
3. **`parallel` on the Katalyst:** a stage holding branches of stages. A branch's tail and latency count for the chain,
   and one block buffer per branch is allocated at build. This makes "distort only the mids on the master" possible.
4. **`bands`, on both hosts,** built on `parallel`.
5. **Optional:** a dry/wet helper, `x.blend(wet, y => ...)`.
   With it lands a recipe in the writing reference, next to the `bands` recipes (maintainer, 2026-10-10): a
   "Soundgoodizer-style" master, parallel multiband compression (FL Studio's Soundgoodizer is a one-knob front end
   for Maximus: three compressed bands blended with the dry input, per its manual), as `blend` over `bands` with a
   compressor per band. The numbers in it are a starting point, not a tuned preset.

## Defaults taken when the work started (coordinator, 2026-10-10; the maintainer may reverse any of them)

The maintainer asked for the steps to run in a loop, stopping only at a real decision. These three were open; the
coordinator took its leans so the loop could start:

- **The crossover:** Linkwitz-Riley. An untouched `bands` is then an all-pass: flat in level, the phase turned at each
  cut, which is documented. Complementary by subtraction and linear phase stay the alternatives.
- **The helper:** `blend`, linear law only, built last.
- **Step 3:** built directly, not in the Motor Lab first.

## Found on the way (2026-10-10): a shared subtree does not survive the wire to the browser

Read in the generated codec (`audio_bridge/build/generated/ksp/js/.../WireCodecGenerated.kt`): `encode_IgnitorDsl_*`
encodes every child as a fresh JS object and `decode_IgnitorDsl_*` builds a fresh Kotlin object per occurrence, so a
node referenced twice in a tree (`let s = ...; s + s.shimmer()`) arrives in the worklet as TWO equal objects. The
backend's build cache shares by identity (`IgnitorBuildCache`, `MemoizingIgnitor`), so in the browser such a `let`
builds two instances; on the JVM (no codec) it builds one. No test, plan or memory records it.

What it means:
- Deterministic sources (an oscillator without drift or random phases) sound the same, at twice the CPU.
- A noise, an analog drift, a supersaw's random phases, a shimmer's grain clock: two independent instances in the
  browser, one on the JVM, so the renders the maintainer listens to (JVM) and the browser differ.
- `parallel` on the Ignitor shares its input with every branch by construction, so `bands` with three bands would
  build the whole instrument three times in the browser, and a split supersaw would not sum back to itself.

Options, for the maintainer (stone rule: generated sources are consulted first):
- **(a) The codec keeps sharing** (the coordinator's lean): the encoder gives a node it meets a second time a
  back-reference (`{"#ref": n}`), the decoder keeps a table. One change in the KSP processor, for `IgnitorDsl` only, and
  every `let` in every song behaves in the browser as on the JVM. Mandatory mutation tier (KSP).
- **(b) A `Parallel` node that holds its input once** and hands the branches a placeholder leaf: fixes `parallel`
  only, adds a binding mechanism to the build, the walkers and the optimizer. More machinery for less.
- **(c) Accept it**: `parallel` and `let` fork in the browser.

**Decided (maintainer, 2026-10-10):** consulted with an example (Kokon's Screamer), "no preference", so the
coordinator's lean (a) was taken. Built as `@WireShared` (`audio_bridge/.../WireShared.kt`): the codec of every
`@WireFormat` root opens a per-message identity scope, the codec of a `@WireShared` type encodes and decodes each
instance once per scope, and the structured clone of `postMessage` keeps the shared references, so the message format
is unchanged (an explicit `$ref` would be needed only for a JSON transport). Guard: `WireSharedSpec` (JS). The
processor refuses a field typed as a subtype of a `@WireShared` type. Kokon's and Der Schmetterling's heavy guitar now
play in the browser as the JVM renders them (one string through the Screamer, not two).

**Listening, for the maintainer:** Kokon's Screamer sums its clean and its 2x-oversampled dirty path with a plain
`plus`, 4 samples apart (a comb, first notch near 6 kHz). `parallel` would align them. Migrating the pedal changes the
sound, so it is the maintainer's call by ear; a pair can be rendered.

## Open, found in the reviews (2026-10-10)

- **Shared nodes make structural walks grow with the paths, not the nodes** (audio review of step 2, MINOR). A data
  class's `hashCode`, `toString` and `collectParams` walk every path through a tree, so k nested two-branch
  `parallel`s reach the bottom input 2^k times. `IgnitorDsl.uniqueId()` hashes the whole tree per scheduled event that
  carries an inline instrument (`KlangPatternScheduler`). True of `let` sharing before; `parallel` makes it idiomatic.
  `bands` nests one level (linear). Watch it if songs nest parallels deeply; a cached hash on the node is the fix.
- **Latency over-count through parameter operands** (both reviews, MINOR): an oversampled modulator in a branch's `mul`
  makes the branch report a delay its signal does not have. Recorded in `BuiltIgnitor.latencySamples`.
- **The Katalyst sums rounded latencies** (audio review of step 2): a branch of two 4x `distort` stages reports 12
  frames for a true 11. Rare on a bus; the voice keeps exact delays since the same review.

- **`bands` cost** (audio review of step 4): SETTLED in the same step. The all-pass that aligns three bands or more
  is one existing EQ section, a raw tap at the cut with Q 1/sqrt(2) and gain -2 (`x - 2 * bandpass(x)`, found by the
  coding review), and the Ignitor shares each high side by identity (a split tree): a 4-band voice split runs 15
  sections per sample (12 for the three cuts, 3 all-passes), where the first build ran 30. No new section type.
