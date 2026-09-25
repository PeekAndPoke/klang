# Oversampling regions, and one core per effect

Status: **scheduled, once the current plan (`../plans/signal-flow-redesign.md`, phase 3 included) is
fully complete** (maintainer, 2026-09-23). Not started.

Rewritten 2026-09-23 from the future draft of 2026-07-04 (`future/pipeline-oversampling-regions.md`,
kept in git history). That draft was written for the Pipeline DSL, which phase 3 retires, and used an
open/close marker form the maintainer has since rejected; what it got right is carried in section 6.

## 1. The goal: two halves of equal weight

**(a) Oversampling as a region, not a per-effect knob.** A region runs everything inside it at N times
the base rate, with one up-conversion entering and one down-conversion leaving. Today every nonlinear
stage owns its own oversampler, so a distort followed by a crush, both oversampled, pays two full round
trips and band-limits the signal between them. Regions must work for the Ignitor, for the Katalyst, and
possibly for the Master (decided in this task).

**(b) The factoring.** Before any region code is written, every existing effect in the three hosts is
revisited, duplicated code is identified, and it is consolidated: one DSP core per effect, wrapped
thinly by the Ignitor, Katalyst and Master effects. In the maintainer's words, getting regions to work
is a given; getting the factoring right is evenly important. A region implemented three times, over
three differently factored sets of effects, is the outcome this task exists to prevent.

## 2. Decided (maintainer, 2026-09-23)

- **The lambda form.** `Osc.sine().oversample(2) { it.distort().crush() }`. The region closes itself,
  the factor is declared once, and nothing is left for the build to pair. Rejected:
  `.oversample(2) ... .downsample()`, which can be written unclosed, closed without an opener, or opened
  at two factors in two branches of one region.
- **Nesting composes literally, and the engine never rewrites it.**
  `Osc.sine().oversample(2) { it.crush().oversample(2) { it.distort() } }` runs the crush at 2x and the
  distort at 4x, with a second round trip inside the first. No hoisting, merging or flattening of
  regions: where a region starts and ends is sound shaping, and the author owns how it is written.
- **The factor is read once, when the note starts.** A `Constant` or a `Param`, resolved at voice build
  the way phase 3 step 2's gate reads a knob (`buildTimeKnobValue` in `IgnitorDslRuntime.kt`). It never
  changes while a voice runs, so an instrument can expose it as a "quality" slot that a pattern sets per
  note. Changing it mid-note is ruled out, not deferred: every stateful node in the region holds state
  computed for the old rate, and the resampler's group delay differs per factor (4.0 input samples at
  2x, 5.75 at 4x), which shifts the whole signal, a click. This replaces the draft's "structural,
  compile-time only" and keeps its reason: the factor defines the timeline the region's signals are
  drawn on.
- **Factor 1 builds no region.** Bit-identical and free; one more row in the off-value table
  (`builtin-instruments.md` section 5b) when it lands.
- **Ordering.** Once the current plan is fully complete. Phase 3 drops its oversampling sub-tasks
  except one stopgap (`builtin-instruments.md` D7, decided (a)): distort's existing `oversample` becomes
  a knob read at voice build so `classic()` can fill it, which keeps phase 3's step 6 identity. This
  task retires that knob with the rest of the per-effect oversampling.

## 3. Phase 0: the effects audit and the factoring (first, before any region code)

The deliverable is a table with one row per effect, across every host that exists when the task
starts: the Ignitor nodes (`audio_be/.../ignitor/`), the Katalyst stages (`cylinders/katalyst/`), the
master stages, and the voice strip renderers (`voices/strip/filter/`) if phase 3 has not yet retired
them. Per row:

- **the DSP core** and every host wrapper around it, with file and line;
- **duplication**: code that computes the same thing in two places, and above all where the copies
  have DRIFTED;
- **rate class**: memoryless (a pointwise shaper), rate-dependent (reads the sample rate: filters,
  LFOs, envelopes, delays), or counted in samples (a knob or constant whose meaning is a sample count,
  which is wrong inside a region unless it is scaled);
- **rate captures**: anything that reads the sample rate once, at build or at first render, instead of
  on every render; and any buffer sized in samples at construction.

Then the consolidation, one effect at a time, each an identity step with the usual net (the whole-corpus
HEAD against tree render, raw doubles): one core, and host wrappers that only adapt the host's block
contract. **The test of the factoring is what a region then costs to add:** if it needs a change in
more than one place per effect, the factoring is not done.

**Known before the audit** (examples found while writing this, not the inventory):
- The oversampler is owned separately by `CrushRenderer`, `CoarseRenderer`, `DistortionRenderer` and the
  Ignitor's `ShapeIgnitor`; each builds its own and wires its own up and down.
- Twins that drifted, and each cost a maintainer decision in phase 3: the crush quantizer floors in the
  strip and rounds in the Ignitor (D1); the distort soft cap is applied by `ShapeIgnitor` and not by
  `DistortionRenderer` (D2); the filter envelope is linear on the node and exponential on the strip
  (D3). Each is one effect written twice.
- The ADSR curve law (`when (curve)` over the `AdsrCurve` kinds) was inlined three times in `AdsrIgnitor`
  and three more times on the strip. Phase 3 step 3d(i) extracted the Ignitor's into `AdsrCurveMath.kt`
  and shares it with the filter and pitch envelopes; the strip's three copies remain (found 2026-09-24).
- **The distort's soft cap, deferred here by the maintainer (D2, 2026-09-25).** Phase 3 kept two laws: the
  strip's (and `classic()`'s fused `Distort` node) has no cap and drives inside the oversampler; the Ignitor
  `Shape(Drive(...))` path caps every sample above 0.95 and drives before it. When this task builds the one
  distort core, it picks ONE law, and that choice changes either the strip-distort songs (verified in phase 3 step 4 by a control: ATruthWorthLyingFor, DerSchmetterling's sample kick, DrunkenSailor, the three frozen rows, IrishLamentTechno, SoundOfTheSea's sample glockenspiel, StrangerThings, Tetris, TetrisRemix; the list that followed here was short: Tetris,
  TetrisRemix, IrishLamentTechno, the frozen songs) or the Ignitor-distort songs (DerSchmetterling's guitars,
  Sandsturm, ATruthWorthLyingFor): a listening checkpoint.
- The two coarse classes document a deliberate difference in their bypass thresholds (`CoarseRenderer`'s
  KDoc) that exists only because they are two classes.

## 4. The mechanism, as far as the code shows it today (2026-09-23)

- **The region node** upsamples its input, renders its body under a child `IgniteContext` at factor
  times the rate, and decimates. `Oversampler.process` already takes a block callback, and
  `ScratchBuffers.oversample(factor)` already keeps a scratch pool at factor times the block.
- **How the rate travels.** The maintainer's phrasing: the buffers carry their rate. In the Ignitor that
  is the context that accompanies the buffer: `IgniteContext.sampleRate` is read at render time by the
  filters, envelopes, oscillators and pitch mods. The Katalyst and the master have block contracts of
  their own. Choosing ONE shape for "a block and its rate" across the three hosts is part of phase 0,
  not something each host invents.
- **The frame counters** in `IgniteContext` (`voiceElapsedFrames`, `gateEndFrame`, `releaseFrames`,
  `voiceDurationFrames`) are in base-rate frames and must be scaled in the child context. `gateEndFrame`
  is re-derived every block, because a realtime note-off moves it (its KDoc: "do NOT bake copies").
- **Rate captures found so far:** the drift lanes take the rate from the build cache
  (`FilterHumanization`, `SampleIgnitor`, via `analogDriftStepRate`); the phaser builds its `PhaserCore`
  at first render from the context it is given, which is correct inside a region because the factor
  never changes. A build-time factor lets the build hand the drift lanes the region's rate.
- **Counted in samples:** coarse's `amount` means "hold every Nth input sample"; the strip divides its
  counter increment by the factor to keep that meaning, and inside a generic region coarse has to learn
  the factor from the context. The draft's list of sample-count constants (`FILTER_SMOOTH_SAMPLES`, retired
  2026-09-25 in step 5b (a2); the ADSR de-click coefficient) belongs here too. The audit finds the rest.

## 5. Where it applies

- **Ignitor**: the region node, both doors, the wire.
- **Katalyst**: the chain is a serial stage list rather than a tree, so the region's shape there is
  decided in phase 0; the swap and drain machinery (`KatalystFilterSwap`, the chain crossfade) must keep
  working across a region boundary.
- **Master: maybe.** Phase 0 decides whether any master stage benefits.

## 6. Carried from the 2026-07-04 draft (still valid)

- **The resampler's quality becomes the sound lever.** Today's kernel was built cheap for waveshapers:
  linear-interpolation upsampling and a 15-tap truncated half-band decimator, about -14 dB at 0.55 pi
  (`Oversampler`'s KDoc). Once a region can hold a filter or anything clean, its top-end droop is
  audible: by arithmetic, not measured, the interpolator alone costs about 1 dB at 10 kHz and 3 dB at
  15 kHz at 48 kHz. `classic()` must keep today's kernel for identity; a better kernel is its own
  decision, and could be what a higher quality setting buys.
- **Latency coherence.** Layers at different factors have different group delays, and summed they comb.
  Compensate, or fix one latency budget.
- **Everything inside a region runs at N times the rate.** A region over-scoped around a clean filter is
  a CPU loss. Keep regions minimal, and say so in the docs.
- **Powers of two** (2, 4, 8, 16) for v1: half-band cascades and integer block lengths.
- **Modulation across the boundary.** In the tree form, a modulator INSIDE the body renders under the
  child context, so it runs at the region rate with no extra machinery. A modulator shared across the
  boundary (memoized, read both outside and inside) is the case to design.
- **Upsampling after the source only interpolates; it does not un-alias an oscillator.** Open: whether a
  body may ignore `it` and generate its own source, which is how an oscillator would run natively at the
  high rate.
- **DC blocking** once per region rather than once per shaper, to decide.

## 7. Migration

- **`classic()` reproduces today's strip exactly** with one region per stage: crush, coarse and distort
  each in a region of its own, the factor read from its sprudel slot. That is bit-identical because the
  region uses the same `Oversampler` the strip renderers use. Merging a run of stages into one region is
  a sound change an AUTHOR makes; the engine never does it (section 2).
- **Retired by this task** (one word per concept): the phase 3 stopgap knob (D7), the `oversample` parameter of the Ignitor's
  `distort(amount, shape, oversample)` and `shape(shape, oversample)`, and the `IgnitorDsl.Shape.oversample`
  field. Users today: Sandsturm, ATruthWorthLyingFor, DerSchmetterling (DialogueWithTheStars left the built-in songs 2026-09-25), and eleven sites in
  `FrozenPieces` (changing those needs the maintainer's word, phase 3 D5).
- **Open:** whether the sprudel doors `crush`, `coarse` and `distort` keep an `oversample` slot (read by
  `classic()` into a one-stage region, so the word keeps its meaning) or lose it. Every shipped crush and
  coarse use pins `oversample = 1`; distort's is used by Tetris, TetrisRemix, IrishLamentTechno,
  ATruthWorthLyingFor (its bass, `distort(0.8, "tube", 4)`) and the frozen songs.

## 8. Tests

- The null test: factor 1 against no region, raw doubles.
- Per effect, as it moves onto its core: base rate against up, effect at N times, down, within the
  resampler's stated tolerance.
- Nesting: the maintainer's example runs the crush at 2x and the distort at 4x, and a render shows the
  inner round trip is really there (it differs from the flattened form).
- A held note keeps its factor when the slot changes; the next note takes the new one.
- Latency: two layers at different factors summed, no unexpected comb.
- The whole-corpus net for every identity step of phase 0.

## 9. Open decisions, for the maintainer when the task starts

1. The Master: in or out.
2. The sprudel `oversample` slots on crush, coarse and distort: kept (mapped onto regions by `classic()`)
   or retired.
3. The resampler kernel: today's everywhere, or a better one for regions an author writes while
   `classic()` keeps the old.
4. Whether a body may generate its own source at the region rate.
