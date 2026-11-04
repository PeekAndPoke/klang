# KLANG-00214: Oversampling regions, and one core per effect

> **Status (2026-11-03): steps 1, 3a, 3b and 4 landed (section 11).** Left: merging neighbouring stages into one
> region, song by song, by ear (Der Schmetterling first); the reverb damping in a region (by ear); the warehouse items
> of step 3a.
>
> **Part of:** KLANG-00354 (The Motor's shape: signal flow and the graph)

Scheduled on 2026-09-23 for after KLANG-00210 (signal flow redesign); started 2026-10-31 (section 11).

> **2026-09-27, phase 3 step 9: what changed for the oversample knobs.** The voice strip retired, and with it
> `CrushRenderer`, `CoarseRenderer` and `DistortionRenderer` (sections 3, 4 and 7 below name them as they were).
> The typed `VoiceData` fields `distortOversample`, `crushOversample` and `coarseOversample` are gone; the knobs
> travel as `ignitorParams` slot keys written by sprudel's `toVoiceData()` (`_classic_slot_params.kt`):
> - `distort.oversample` is a `classic()` slot (the D7 stopgap), read once at voice build into the fused
>   `Distort` node's oversampler (`DistortionCore`).
> - `crush.oversample` and `coarse.oversample` are carried but READ BY NOTHING: `classic()`'s crush and coarse
>   have no oversampler and place no such slot, so `crush(oversample = ...)` and `coarse(oversample = ...)` do
>   nothing today, on every voice. The value travels so it is not lost before this task decides (section 9,
>   point 2).
> - The oversampler left in the engine: `DistortionCore`, shared by the fused `Distort` node and the `Shape` node
>   (the Ignitor `shape` and `distort` doors) since engine tidy-up step 11 (2026-10-08).

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

- **The lambda form.** `Ignitor.sine().oversample(2) { it.distort().crush() }`. The region closes itself,
  the factor is declared once, and nothing is left for the build to pair. Rejected:
  `.oversample(2) ... .downsample()`, which can be written unclosed, closed without an opener, or opened
  at two factors in two branches of one region.
- **Nesting composes literally, and the engine never rewrites it.**
  `Ignitor.sine().oversample(2) { it.crush().oversample(2) { it.distort() } }` runs the crush at 2x and the
  distort at 4x, with a second round trip inside the first. No hoisting, merging or flattening of
  regions: where a region starts and ends is sound shaping, and the author owns how it is written.
- **The factor is read once, when the note starts.** A `Constant` or a `Param`, resolved at voice build
  the way phase 3 step 2's gate reads a knob (`buildTimeKnobValue` in `IgnitorDslRuntime.kt`). It never
  changes while a voice runs, so an instrument can expose it as a "quality" slot that a pattern sets per
  note. Changing it mid-note is ruled out, not deferred: every stateful node in the region holds state
  computed for the old rate, and the resampler's group delay differs per factor (3.07 input samples at
  2x, 4.40 at 4x since the IIR half-band of 2026-10-10, `Oversampler.groupDelaySamples`), which shifts the whole signal, a click. This replaces the draft's "structural,
  compile-time only" and keeps its reason: the factor defines the timeline the region's signals are
  drawn on.
- **Factor 1 builds no region.** Bit-identical and free; one more row in the off-value table
  (KLANG-00237 (builtin instruments) section 5b) when it lands.
- **Ordering.** Once the current plan is fully complete. Phase 3 drops its oversampling sub-tasks
  except one stopgap (KLANG-00237 (builtin instruments) D7, decided (a)): distort's existing `oversample` becomes
  a knob read at voice build so `classic()` can fill it, which keeps phase 3's step 6 identity. This
  task retires that knob with the rest of the per-effect oversampling.

## 3. Phase 0: the effects audit and the factoring (first, before any region code)

The deliverable is a table with one row per effect, across every host that exists when the task
starts: the Ignitor nodes (`audio_be/.../ignitor/`), the Katalyst stages (`cylinders/katalyst/`), the
master stages (the voice strip's renderers retired in phase 3 step 9, so they are no host any more). Per row:

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
  `DistortionRenderer` (D2); the filter envelope was linear on the node and exponential on the strip
  (D3, settled 2026-09-25 by one `EnvelopeCore` and an exponential default on both). Each is one effect written twice.
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
  times the rate, and decimates. `Oversampler.upsample` and `Oversampler.decimate` are already the two
  halves a region wraps around its body (engine tidy-up step 2, 2026-10-07), and
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
- **Counted in samples:** coarse's `factor` means "hold every Nth input sample"; the strip divides its
  counter increment by the oversample factor to keep that meaning, and inside a generic region coarse has to learn
  the oversample factor from the context. The draft's list of sample-count constants (`FILTER_SMOOTH_SAMPLES`, retired
  2026-09-25 in step 5b (a2); the ADSR de-click coefficient) belongs here too. The audit finds the rest.

## 5. Where it applies

- **Ignitor**: the region node, both doors, the wire.
- **Katalyst**: the chain is a serial stage list rather than a tree, so the region's shape there is
  decided in phase 0; the swap and drain machinery (`KatalystFilterSwap`, the chain crossfade) must keep
  working across a region boundary.
- **Master: maybe.** Phase 0 decides whether any master stage benefits.

## 6. Carried from the 2026-07-04 draft (still valid)

- **The resampler's quality becomes the sound lever.** DONE for the kernel itself 2026-10-10: the IIR half-band
  replaced the FIR everywhere (KLANG-00298 (iir oversampler)); what follows describes the FIR it
  replaced. The kernel was built cheap for waveshapers:
  linear-interpolation upsampling and a 15-tap truncated half-band decimator, about -14 dB at 0.55 pi
  (`Oversampler`'s KDoc). Once a region can hold a filter or anything clean, its top-end droop is
  audible: by arithmetic, not measured, the interpolator alone costs about 1 dB at 10 kHz and 3 dB at
  15 kHz at 48 kHz. `classic()` must keep today's kernel for identity; a better kernel is its own
  decision, and could be what a higher quality setting buys.
  The known gaps, from the distortion-oversampling review (KLANG-00087 (distortion oversampling),
  findings A1, A2, A5; folded in 2026-09-27 from KLANG-00218 (audio pipeline open topics) §5): the 15-tap half-band rejects
  about 20 dB where 50 to 60 dB is the target (A1); the linear-interpolation images, only about 11 dB down at
  20 kHz, intermodulate through a nonlinearity into in-band products the decimator cannot remove (A2); and
  nothing filters between the upsampler and the nonlinearity (A5). The recommended kernel: zero-stuffing plus
  a half-band FIR on BOTH sides, 31 to 43 taps. That is an input to open decision 3 (section 9).
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
- **Retired by this task** (one word per concept; done in step 4, 2026-11-03, with the Katalyst `distort`'s factor too): the phase 3 stopgap knob (D7), the `oversample` parameter of the Ignitor's
  `distort(amount, shape, oversample)` and `shape(shape, oversample)`, and the `IgnitorDsl.Shape.oversample`
  field. Users today: Sandsturm, ATruthWorthLyingFor, DerSchmetterling (DialogueWithTheStars left the built-in songs 2026-09-25), and eleven sites in
  `FrozenPieces` (changing those needs the maintainer's word, phase 3 D5).
- **Decided (section 9, answer 2):** the sprudel doors `crush`, `coarse` and `distort` lose their `oversample` slot. Was open: whether they keep one (read by
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

## 8a. Found 2026-10-09: the oversampler is not clean enough for a whole mix

**Resolved 2026-10-10:** the IIR half-band replaced this kernel (flat to 20 kHz, about 100 dB of rejection, latency
3.07, 4.40, 5.06 samples). The record of the old kernel follows.

Measured while building the Katalyst `distort` stage (KLANG-00292 (katalyst distort stage)):
on a clean signal the round trip (the linear-interpolation upsampler and the 15-tap half-band decimator) loses, at
48 kHz and 2x, 0.7 dB at 10 kHz, 2 dB at 16 kHz and 4 dB at 20 kHz (4x and 8x a little more). On a distorted voice that
is inaudible; on a bus or the master it dulls the whole mix even at a small drive. So the bus `distort` defaults to no
oversampling. A transparent oversampler for buses (a longer half-band, or an interpolating upsampler instead of the
linear one) belongs to this task's factoring. The alternative for the soft shapes is antiderivative anti-aliasing (no
resampling, no latency). The group delay figures were corrected the same day (`Oversampler.groupDelaySamples`: 4.0, 5.5
and 6.25 input samples).

## 8b. Found 2026-10-10: `parallel` and `bands` align by the oversampler's delay

**Resolved 2026-10-10 by the phase twins** (KLANG-00298 (iir oversampler)): a branch that lacks an
oversampler now runs through an unshaped round trip of it, on both hosts (`BuiltIgnitor.oversamplers`,
`KatalystLatentEffect.oversamplers`); whole-sample pads remain for a lookahead and for a voice branch that mixes two
oversamplers in a plain `plus`. A new oversampler type must list itself there, or report its delay as before. What
follows is the record as found.

`parallel` (both hosts, and `bands` on top of it) pads every earlier branch to the latest one, and the delays it
compares are DECLARED, not measured: an oversampled `distort` or `shape` reports `Oversampler.groupDelaySamples` (the
voice adds them up exactly, `BuiltIgnitor.latencySamples`; the bus reports `Oversampler.latencyFrames`, rounded, as a
`KatalystLatentEffect`). A new oversampler, or any new stage with a pure delay, must report its own delay there, or a
`parallel` beside it combs again (`IgnitorParallelSpec`, `KatalystParallelEffectSpec` render the alignment). An
oversampler without latency (antiderivative anti-aliasing) reports 0 and needs no pad. Maintainer, 2026-10-10: "we
will anyway change the way how oversampling works in the future so we need to keep this in mind."

## 9. Open decisions, for the maintainer when the task starts

1. The Master: in or out.
2. The sprudel `oversample` slots on crush, coarse and distort: kept (mapped onto regions by `classic()`)
   or retired.
3. The resampler kernel: today's everywhere, or a better one for regions an author writes while
   `classic()` keeps the old.
4. Whether a body may generate its own source at the region rate.

**Answered (maintainer, 2026-10-31):**
1. The master: out, for now.
2. `crush`, `coarse` and `distort` lose their own oversampling settings; a stage reads its rate from the buffer it
   receives (the buffers carry their rate, section 4).
3. The IIR half-band we have, for now; plan for configurable kernels.
4. Yes: a body may generate its own source at the region rate.

**A principle (maintainer, 2026-10-31):** up and down sampling as little as possible, and never repeatedly. A blind
test checked whether repeated round trips are audible: `tmp/listening/resample-blind/` (untouched, 4 and 8 chained 4x
round trips of the engine's `Oversampler`, music and clicks with hats, delay compensated). **Verdict: the maintainer
heard no difference in any of them.** Measured on the clicks and hats: the level of every frequency is unchanged to
22 kHz (0.00 dB); the top arrives later than the rest, after 8 trips by 7.6 samples at 10 kHz, 22 at 15 kHz and 67 at
20 kHz (the IIR's phase, section 8b). So the principle stands as a matter of CPU and design cleanliness, and a
conversion that cannot be avoided is not a sound problem.

## 10. The design (maintainer and coordinator, 2026-10-31)

Answers to the four design questions of 2026-10-31 and what followed from them.

**The buffer carries its rate.** A stage reads its rate from the buffer it receives; a conversion happens only where
two rates meet, and only for the difference (in 2x steps of the cascade).

**Two region doors** (replace the plain `oversample(n) { ... }` of section 2):
- `oversampleBy(n) { ... }`: relative, multiplies the incoming rate (1x to 2x, 4x to 8x).
- `oversampleAtLeast(n) { ... }`: a floor, steps up only to what is missing; at or above it, it is a no-op with no
  conversion. The door for reusable pieces (a preset that needs 4x adds nothing inside an 8x region).
- A region hands back the rate it was entered with.
- Factors: rounded down to a power of two (3 to 2, 5 and 7 to 4); 1 or less is a no-op. The rate stops at 16x without
  an error: a guard against absurd values only (a later "Ignitor analyzer" shows the cost).

**Sources stay at 1x** (maintainer, 2026-10-31, superseding the first answer "yes, sources at the region rate"): every
producing node (oscillators, noises, plucks, samples) and its pitch modulation runs at the base rate, also when it is
written inside a region's braces; it is converted up once where it enters the region, exactly like the region's input.
A modulation source in a knob (an LFO on a cutoff inside a region) is converted the same way. Generating sources at a
higher rate is a possible later optimization, not part of this plan: the blind test showed conversions are not
audible, and a supersaw at 4x costs four times as much. `Ign.oversampleAtLeast(4) { Ign.sine() }` as a way to a 4x
sine is off the table.

**Nesting:** a nested region steps on from the current rate, never back to 1x in between. Adjacent regions (down, then
up again) get an editor hint, never an engine merge (section 2: the engine never rewrites).

**Filters:** the SVF already computes its coefficients twice per block, so their cost does not grow with the rate;
the per-sample recurrence does, by N. A linear filter gains nothing in a region (TPT is prewarped): guidance and an
editor hint, keep clean filters outside regions.

**Orbits:** an orbit always ends at 1x before the master. Its rate comes from its own Katalyst chain: the rate its
first stage asks for on a 1x input (a head `oversampleAtLeast(4) { ... }` makes it a 4x orbit; a chain that begins
with an ordinary stage is a 1x orbit). No separate rate setting. The orbit keeps one mix bus per voice rate that
occurs; each bus is converted once to the orbit's rate (one conversion per rate per orbit, never per voice), the
buses aligned in phase like parallel branches.

**Decided (maintainer, 2026-10-31, "Ok sounds good"):**
- Where the orbit's rate is read: when the cylinder builds the chain from its `katalyst(...)` declaration (idle orbit:
  at once; sounding orbit: the chain swap). The rate is a property of the built chain; during a swap each chain
  converts the rate buses to its own rate.
- A bus above the orbit's rate is stepped down to it (the chain runs at one rate; `AtLeast` at the head of an orbit is
  read on 1x, not per bus).
- A region at the very end of a voice hands over at its rate, so a 4x voice lands on a 4x orbit's bus directly; a
  region in the middle of a voice closes normally.
- The braces rule (revised the same day, "Ok sounds good"): inside a region the processing stages run at the region
  rate; sources and their modulation run at 1x and are converted up once where they enter (see "Sources stay at 1x").

## 11. Build in steps, on Der Schmetterling (maintainer, 2026-10-31)

Built in steps, each heard in Der Schmetterling. Step 1 is the guitar preamps: several oversampled stages in a row
with filters between them (`preampHighGain`: three `distort(..., 8)` with highpasses between, so three 8x round trips
per voice today, one with a region).

**Step 1: an Ignitor region for the preamps.**
- 1a. Audit the stages the rigs use inside a region (`distort`, `highpass`, `lowpass`, `eq`, `mul`, `parallel`,
  `serial`, `Ign.fn`): anything that reads the rate at build time or counts in samples. The SVF and one-pole filters
  read `ctx.sampleRate` per block (seen 2026-10-31).
- 1b. The node in `audio_bridge`: `oversampleBy` and `oversampleAtLeast` with a body lambda, both doors (`Ign` and the
  script's `Ignitor`), the wire, the walk. The body is applied to a region-input placeholder, so the engine knows
  where the outside ends (the braces rule, section 10).
- 1c. The engine: the region renders its input at the outer rate, steps up only the missing 2x steps, runs the body
  under a child `IgniteContext` at the region rate (sample rate and frame counters scaled, buffers from the
  oversample pool), steps back down. It reports its group delay so `parallel` stays aligned (section 8b).
- Bridge until step 4: a stage's own `oversample` factor acts as `oversampleAtLeast(n)` around that stage, so inside an
  8x region `distort(0.3, "tube", 8)` converts nothing. Songs outside regions sound as before.
- 1d. Tests (mutation-checked), the review loop.
- 1e. The song: the preamps wrapped in one region each; an A/B render of the guitars for the maintainer's ears and the
  CPU before and after. A sound change is expected: the second clipper now sees the first one's harmonics above
  24 kHz, which each decimation used to remove.

**Step 1, landed (2026-11-01).** The doors, the wire node `RegionInput(up)` (a de Bruijn index, so a body lambda's
parameter keeps its meaning in a nested body), the engine (`OversampleRegion.kt`), the bridge for `Shape` and
`Distort` (their factor a floor, the DC blocker's pole mapped), `Coarse`'s hold and `Shimmer`'s ring scaled. Sources
stay at 1x inside a region already (what was step 2 below), with their pitch modulation. Inside a body every latency
counts from the region's input (`RegionScope.inputDelay`), and a constant or a slot takes no part, so a `parallel`
there lines up its branches wherever they come from. A source takes phase twins of the oversamplers the input ran
through before the outermost region, also in a nested region, so it is exact at every frequency (maintainer,
2026-11-01); what a nested region's input ran through inside the outer bodies is padded by whole samples (one case
measured within 0.3 dB to 20 kHz, because the upper stages disperse little). Review: round 2's MAJOR (a misaligned
`parallel` in a body, -27 dB at 4.75 kHz) fixed in round 3; round 3's MAJOR (a gain on the branch hid its latency)
fixed in round 4, both with the maintainer's go. A/B of the song (`tmp/listening/schmetterling-regions/`):
level the same within 0.03 dB, spectrum within 0.2 dB up to 20 kHz, the guitars 0.2 ms earlier; the render about 3%
SLOWER, since the highpasses, DC blockers and soft caps inside run at 8x. Left for later: the oscillator edges, `Pluck`,
`Sample` and the noises are not rate-mapped (they stay at 1x, so nothing reads them at a higher rate yet); the shimmer
ring is 12 MB at 16x; in a no-step region the body's input reaches a pitch modulation or a source's knob, in a real
region it is silence there.

**Which nodes know the rate** (the maintainer's question, 2026-10-31: which nodes need adjustments at a higher rate,
which do not care; asked as a question). Since sources stay at 1x (section 10), the source rows below need no
change; what is left for the region is `Shape`, `Distort`, `Coarse` and `Shimmer`. The audit of the 79 Ignitor node types (coordinator, 2026-10-31, from the code), the rule being that a node inside a
region sounds as it does outside, only with less aliasing:
- **Do not care** (pointwise or structural): `Abs`, `Affine`, `Ceil`, `Clamp`, `Constant`, `Div`, `Exp`, `Floor`,
  `Frac`, `Freq`, `Lerp`, `Log`, `Max`, `Min`, `Minus`, `Mod`, `Neg`, `Param`, `Plus`, `Pow`, `Range`, `Recip`,
  `Round`, `Select`, `Sign`, `Silence`, `Sq`, `Sqrt`, `Tanh`, `Times`, `Drive`, `Crush`, `Detune`, `Variants`,
  `OptimizerHint`. The nonlinear ones among them gain: they alias less.
- **Already read the rate from the context** (correct under the region's child context, to be pinned by tests): the
  oscillators' pitch (`Sine` with its banks, `Saw`, `Ramp`, `Square`, `Pulze`, `Tri`, `Zawtooth`, `Zamp`, `RawPulze`,
  the `Super*` family, `Impulse`'s rate), the filters (`Lowpass`, `Highpass`, `Bandpass`, `Notch`, `OnePoleLowpass`,
  `Eq`, their envelopes and the humanize drift lane), the envelopes (`Adsr`; the frame counters scaled by the child
  context), `Tremolo`, `Phaser`, `Vibrato`, `Accelerate`, `PitchEnvelope`, `Fm`, `PitchMod`, `PitchModSemitones`,
  `Dust`'s density, the Karplus delay length, the analog drift lanes, `Parallel` (twins inside one region).
- **Need a change** (something counted in samples or designed for one rate):
  - the oscillator edges: `resetSamples` (saw, ramp), `flankSamples` (square), `PULSE_MIN_FLANK_SAMPLES`, scaled by
    the factor;
  - `Shape`, `Distort`: the own factor acts as `oversampleAtLeast` (the bridge), the DC blocker's pole mapped;
  - `Coarse`: the hold factor scaled;
  - `Shimmer`: its ring is 96,000 samples, sized for the rate;
  - `Pluck`, `SuperPluck`: the brightness one-pole and the stiffness all-pass coefficients mapped;
  - `Sample`: the playback rate is fixed at build for the base rate, divided by the factor;
  - the noises: `WhiteNoise` (the level after decimation, the tilt filter), `PinkNoise` (a filter designed per sample,
    the level), `BrownNoise` (the leak per sample, the level), `PerlinNoise` and `BerlinNoise` (the step per sample),
    `Crackle` (a per-sample chaotic map, no rate mapping exists), `Dust` and `Impulse` (one-sample clicks lose level
    after decimation);
  - a pitch node outside a region over a source inside it: its modulation must reach the source at the region rate.

**Later steps:** 2, a source written inside the braces rendered at 1x and converted up where it enters; 3, a voice's
end region hands over at its rate and the orbits get their rate and per-rate buses; 4, the stages' own `oversample`
factors retired (section 9, answer 2) and the songs moved to regions; the full phase 0 (section 3) where a step needs
it.

**Step 3, cut in two (coordinator, 2026-11-03, from an audit of the Katalyst stages).** A Katalyst stage takes its
rate and block size when it is built, not from a context per block, so a region on the bus builds its body at N times
both. With that, a region at the head of a chain already makes the orbit run at its rate, one conversion up and one
down per orbit, which is most of what step 3 is for. So:
- **3a, Katalyst regions:** `oversampleBy(n) { ... }` and `oversampleAtLeast(n) { ... }` on the Katalyst, anywhere in a
  chain, on both doors and the wire. Outside every region the chain stays at 1x, so the duck (run after every orbit)
  and the chain swap need no change.
- **3b, the hand-over:** a voice whose region ends the voice lands at its rate on a bus of that rate in its orbit
  (one conversion per rate per orbit), and the chain swap blends two chains of different rates at 1x. An optimization
  on top of 3a.

**Step 3a, landed (2026-11-03).** Reviewed in two rounds: round 1 (level 2) found one MAJOR, both reviewers
independently (a reverb in a nested region mapped by the relative factor and rented from a second shelf), fixed;
round 2 (level 3, two phases) clean; its MINORs applied as one batch (a `parallel`'s pad from the exact latency, the
latency rows). 33 mutants, 32 killed; one equivalent (the chain's pure delay at a first stage above 0 rounds the
same with either kernel's group delay for every reachable step count). Corpus green on the final tree (3 frozen and
12 unedited live songs identical), its engagement control (the reverb's damping nudged by 0.01%) red on every song.
- The doors: `KatalystBuilder.oversampleBy(factor, body)` / `oversampleAtLeast(factor, body)`, the body given an empty
  builder as a `parallel` branch is; the script's `k.oversampleBy(4, r => r.distort(0.5))`. The factor is a plain
  number fixed with the chain, as the Katalyst `distort`'s `oversample` (the Ignitor's is a knob read per note);
  rounded down to a power of two, 1 or less no region, at most 16x. The wire: `KatalystStageDsl.OversampleBy`,
  `OversampleAtLeast` (factor, body); schema hash 1660499267 to -640226917.
- The engine: `KatalystRegionEffect` converts the bus up by its steps (the cascade continued from the chain's stage),
  runs its body, a `KatalystChain` built at the region's rate and block size, and converts down. A region with no
  step to take is no region: its body's stages in place, bit for bit. It reports its round trip as an oversampler,
  so a dry `parallel` branch beside it gets the exact phase twin; the body's own latency is divided down and counts
  as pure delay.
- Inside a body every stage keeps its sound: a delay its time, a filter its cutoff; the knob modulators keep the
  orbit's clock (a noise walked by a region's clock would run N times faster); a `distort`'s own factor is a floor
  (the Ignitor's bridge), its twins and DC blocker at the region's stage; a `parallel` twins from the region's stage;
  a reverb rents a unit at the region's rate from a shelf for that rate (`ReverbUnits.atFactor`, kept and housekept
  by the base shelf; nested regions reach the one shelf of their whole factor, which holds `maxIdle / factor` idle
  units), its comb damping's pole mapped (`damping^(1/N)`) and its `lowpass` read against the base Nyquist. A `duck`
  in a body is the orbit's duck, as in a branch. A region always declares a tail (its down conversion rings).
- The reverb's room inside a region, against the same reverb at 1x: broadband, its length and brightness agree within
  10% (measured: the late against early energy of the tail, and its first difference against itself, at 4x and at
  2x inside 2x). Per band the top decays faster: mapping the pole keeps the one-pole's corner but not its response
  near the base Nyquist. The audio reviewer's analytic T60 ratio (in a region against 1x, size 5, default damping,
  2 to 20 kHz): 0.98 at 2 kHz, 0.95 at 4, 0.91 at 6, 0.87 at 8, 0.84 at 10, 0.80 at 12, 0.73 at 16, 0.65 at 20 (with
  `lowpass` 5000: 0.98 to 0.71); about the same at every factor from 2x. **For the maintainer's ear:** keep the
  pole mapping, or match the one-pole's magnitude at a reference frequency instead (a closed form per block).
- Latency: a region rounds once, at its own level, its body's latency carried unrounded (`exactLatency`); held
  within half a frame of the measured centroid of its impulse response. A `parallel` written inside a body twins its
  branches exactly, from the region's stage. A `parallel` AROUND a region twins the region's round trip and pads what
  its body adds (a nested oversampler, a lookahead) by the nearest whole frame: about -1 dB at 20 kHz for a distort at
  8x in a 4x region beside a dry branch, the precedent the Ignitor's regions set.
- Left as they are, recorded: a delay's repeats are a little brighter inside a region when its time falls between
  samples (at 1x the linear read of a half sample loses 2.4 dB at 10 kHz per repeat; at 4x that time is whole); the
  compressor's two floors in samples (a lookahead under 8 frames is off; inside a
  region 8 region frames, an anti-click guard measured in samples); the phaser's feedback is one sample, so at high
  feedback its peaks sit a little differently inside a region. The master is out (section 9, answer 1), but a
  `master(...)` chain is built by the same builder, so a region written there works; nothing stops it. The warmup
  runs the classic chain only, so a first region pays its JIT.
- Memory at 16x: a delay ring grows with the rate (a 2 s delay at 16x is a 24.6 MB class, rented on first use), a
  reverb unit is about 3.5 MB. Open in the warehouse: the scaled reverb shelves are not in `WarehouseStats`, their
  units bypass an injected allocator (the out-of-memory seam), and housekeeping zeroes one whole unit per block, at
  16x about 16 base units' stores in one block.

**Step 3b, landed (2026-11-03): the hand-over.** A voice whose tree ENDS in a region (its root, under the build's
memo) does not convert down: it lands on its orbit's rate bus, and its teardown fade, cut fade, solo ramp and pan run at
that rate (the same lines read between the frames). An orbit's chain brings every bus to its own rate once: a chain
that begins with a region runs at that region's rate, which converts the mix up one stage at a time and adds each
lower bus on the way; `KatalystBusLadder` steps the higher buses down in place, through the `Oversampler`'s own
stages, and rings out on a silent bus for `Oversampler.tailFrames` after the last voice above. A chain swap hands the
leaving chain every bus ramped by the same line; the blend and the duck stay at 1x. Buses and the raised voice buffers
are made on first use and kept, one per rate per host.
- Phase: a bus continues its voice's cascade, so at or below the orbit's rate it meets the voices converted at the
  orbit in phase, with no pad. A bus ABOVE the orbit's rate keeps the round trip of the stages above (an 8x voice on a
  4x orbit: 0.665 samples, an equal correlated sum -0.86 dB at 10 kHz, -3.99 dB at 20 kHz, no notch, swept 20 Hz to
  20 kHz in 10 Hz steps), untwinned. Before 3b the same pair differed by a whole 8x round trip (notches to -57.7 dB).
  Twinning it would cost a round trip on all lower content, against "as few conversions as possible": kept as is,
  open for the maintainer.
- Cost: one conversion per rate per orbit, but in stereo after the pan, where a voice decimated mono: with one voice
  of a rate on an orbit the hand-over costs about twice the conversion (a 4x voice: 5120 against 2560 section updates
  per block); break-even is two voices of a rate sounding together.
- Review: round 1 (level 2) clean from both reviewers; its MINORs applied as one batch (the phase wording above, two
  KDocs naming the lazy allocations, a dead branch, two members made private). Left as they are: a ladder is built for
  every base-rate chain (a master's, a branch's), which never receives a bus; the ladder's one ring hold for all its
  stages (what a lower stage loses when a higher hold ends starts from a signal faded to zero, under -120 dB).
- Tests: `VoiceHandOverSpec`, 13 rows, oracles the voice converting down itself and bare `Oversampler`s fed with the
  captured buses; 22 mutants killed (one survivor in the first campaign, the leaving chain's buses left uncleared, killed
  by a row added for a leaving chain whose steps ring out). Corpus green on the tree (3 frozen and 12 live songs
  identical; no song hands over yet), its control (the base-rate teardown fade nudged by 0.01%) red on 5 songs.

**Step 4, landed (2026-11-03): the stages' own factors retired, the songs moved one-to-one.** Decided with the
maintainer the same day: the Katalyst `distort`'s factor retires too (its 2x default with it), the live songs move one
region per stage (merging neighbours is a sound change, song by song, by ear, later), and the frozen songs are
re-snapshotted from the live songs ("You could also fully update the frozen songs ... the engine has evolved quite a
bit").
- Gone: the `oversample` parameter of the Ignitor's `distort` and `shape` and of the Katalyst's `distort`, on both
  doors; the fields `IgnitorDsl.Shape.oversample`, `IgnitorDsl.Distort.oversample`, `KatalystStageDsl.Distort.oversample`
  (wire schema hash -640226917 to 210338569); the `classic()` slot `distort.oversample`; sprudel's `oversample` slots on
  `crush`, `coarse` and `distort`, their `SprudelVoiceData` fields and wire keys, and the distort editor's factor
  buttons; `SHAPE_OVERSAMPLE`, `DISTORT_OVERSAMPLE`. In the engine `DistortionCore` lost its oversampler (and its
  scratch-buffer parameter), the Ignitor build its floor bridge, and `KatalystDistortEffect` its latency, phase twins
  and ring hold (it is no longer a `KatalystLatentEffect`). `docs/retired-names.md` has the row.
- The songs: every Ignitor and Katalyst stage with a factor became `oversampleAtLeast(n, y => y.stage(...))` around
  that one stage (Der Schmetterling, Kokon, Sandsturm, A Truth Worth Lying For, and the master `bands` of six songs);
  IrishLamentTechno's bare master `distort`, 2x by default until now, got an explicit 2x region. Sprudel's
  `distort(a, s, n)` lost its third argument (Tetris, TetrisRemix, IrishLamentTechno, A Truth Worth Lying For): those
  voices now distort at 1x, decided on 2026-10-31 (section 9, answer 2). The dead `oversample = 1` on `crush` and
  `coarse` went with its comment.
- Measured, the edited songs before and after (first 60 s as the corpus renders them, mono sum, the residual after the
  best whole-sample alignment; `tmp/listening/oversample-step4/` holds five pairs for the ear): every level within 0.03
  dB and every octave band within 0.05 dB, except Sandsturm's lead (`distort(1.0, "soft", 4)`, its soft cap and DC
  blocker now inside the region) +0.1 to +0.3 dB above 2.5 kHz. The one-to-one regions alone leave a residual of -70 dB
  (the master `bands`: Sakura, Seltsamere Dinge) and -49 to -52 dB (the guitar rigs: Der Schmetterling, Kokon, both
  frozen pieces). The sprudel voices now at 1x leave -17.5 dB (TetrisRemix's 32-voice bass), -22 dB
  (IrishLamentTechno), -32 dB (A Truth Worth Lying For), -40 dB (Tetris): the changed aliasing, and the voices arriving 3
  to 4 samples earlier, without the stage's oversampler delay.
- **The soft cap of a door moved into a region** (review round 1, audio MAJOR; maintainer, 2026-11-04: "Accept,
  listen later"): the `distort` and `shape` doors end in `softCap`, a ceiling that is identity up to 0.95. With the
  stage's own oversampler it ran after the down conversion; inside a region it runs before the region's, whose
  half-band rings on a hard-clipped edge, so a hard-driven door can peak past 1 again (the reviewer's model: Sandsturm's
  lead from 1.00 to about 1.5, +3.5 dB at the voice; A Truth Worth Lying For's guitar about +2 dB). The tone barely
  moves (the mix spectrum within 0.3 dB, Sandsturm only); the peaks reach the orbit and the master limiter. Recorded as
  a sound change, for the ear with the merges: Sandsturm, A Truth Worth Lying For, the fuzz pedals, the gummi bucket
  and the peak clippers of Der Schmetterling and Kokon. The doors' KDoc says the ceiling holds before a region's down
  conversion.
- Frozen: `FrozenSongs` holds new snapshots of Der Schmetterling and Seltsamere Dinge (2026-11-03); the 2026-09-25 Der
  Schmetterling moved to `FrozenPieces` for the rig suite (its trommel anchor follows the region), migrated
  one-to-one like the 2026-09-16 piece; the 2026-07-03 Seltsamere Dinge is gone (git keeps it). The corpus reference
  was rewritten with `corpus-accept-engine` for the frozen rows only.
- Tests: rows that pinned the stage's own oversampler deleted (the oversampled shaper parity rows, the factor knob
  rows, the bus distort's latency, twin and hold rows, the bridge rows); rows that used it as a stand-in oversampler
  moved to regions (the parallel twins, the region spec's nested floors, the click hunt); the region DC row now
  measures from 64 frames after the peak, past the converters' ring on the step's edge, and the twin-tail row uses a
  20 ms lookahead, since a region counts its hold from its body's output. 6 mutants on the new and changed rows, 6
  killed. JVM and JS suites green; the wire schema hash pinned anew. Corpus: the five unedited live songs identical,
  its control (the voice distort's DC pole nudged by 0.01%) red on exactly the two predicted (Gestrandet, Drunken
  Synthlor); the frozen rows accepted with `corpus-accept-engine`.
- Review: round 1 (level 1, blind): the audio MAJOR above (the soft cap), decided by the maintainer; a code MAJOR, three
  allocation cases (`E-distort`, `E-parallel`, `E-bands`) that relied on the bus distort's 2x default and no longer ran
  an oversampled path, fixed (each in a 2x region); checking it found `tube4x` silent in the allocation harness, whose
  hand-rolled rigs read only the base mix while a voice ending in a region hands over to its rate bus (the rigs now
  clear and read the rate buses); a code MAJOR on the song files missing from the pack, rejected (the pack skips that
  folder; the songs are in the commit). Round 2 (level 2, two phases) clean from both; its MINORs and round 1's applied
  as one batch (stale KDocs in `Oversampler.kt` and elsewhere, two Kotlin examples, the whitepaper's code samples and
  two figures at the maintainer's word, the recipes of two tickets).
- Found, not caused here: the allocation check is red at this step's base (6ada80833) and before step 3b (5f8581e85)
  with the same figures as on this step's tree (33 to 35 of 80 cases over budget; every engine case about 85 to 120 B
  per block on V8 where the last scoreboard row, 2026-10-25, read 20 to 60, and 23 B on the JVM where it read 0; the
  JVM runs now on 17.0.17, the earlier rows on 21). Its cause lies between 2026-10-25 and step 3a; open for the
  maintainer (a bisect).

## History

- 2026-10-20: numbered KLANG-00214 (was `docs/tasks/oversampling-regions.md`).
- 2026-10-27: part of KLANG-00354 (The Motor's shape: signal flow and the graph), the epic grouping.
- 2026-10-31: the maintainer answered section 9, set the principle of as few conversions as possible, and asked four design questions; the blind test rendered.
- 2026-10-31: blind test verdict (no difference heard); section 10, the design: two region doors, power-of-two factors capped at 16x, the orbit's rate from its chain.
- 2026-10-31: section 10's proposals decided; section 11, build in steps on Der Schmetterling, step 1 the guitar preamps.
- 2026-10-31: section 11, the audit of which Ignitor nodes know the rate.
- 2026-10-31: sources stay at 1x, also inside the braces, converted up where they enter (maintainer).
- 2026-11-01: step 1 landed: the region doors and engine, Der Schmetterling's preamps in regions.
- 2026-11-03: step 3 cut in two; step 3a landed: regions on the Katalyst.
- 2026-11-03: step 3b landed, the hand-over: a voice ending in a region lands on its orbit's rate bus; the orbit's chain converts each rate once.
- 2026-11-03: step 4 landed: the stages' own oversampling factors retired, the songs moved one region per stage, the frozen songs re-snapshotted.
