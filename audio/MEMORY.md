# Klang Audio — Memory

## The ignitor optimizer's promise is a margin now (2026-09-15)

- `OPTIMIZER_PARITY` (audio_bridge, 1e-12 relative, NaN for NaN, infinity for infinity) replaces
  bit-identity as what `IgnitorDsl.optimize()` promises, so that block-constant arithmetic and
  gains may fold into neighbouring linear nodes (the plan and its steps:
  `docs/tasks-archive/2026-09/20260916-ignitor-optimizer-arithmetic-folds.md`, the open items in `docs/tasks/future/ignitor-optimizer-open-items.md`). The shipped rules still render bit-identical.
- Guards, all green on the current pass: the rule table (`IgnitorDslOptimizerSpec`), render
  parity within the margin plus control-rate semantics and the warmup vocabulary
  (`IgnitorDslOptimizerRenderSpec`), every builtin song's instruments (`OptimizerSongParitySpec`,
  root module), and a thousand generated graphs with adversarial constants, the pass's laws
  (idempotent, work never grows, params survive) and a zero `optimizerFailures` count
  (`IgnitorDslOptimizerFuzzSpec`, jvmTest: it counts by reflection). Mutation-checked: a fused
  coefficient one percent off goes red in the render rows and the fuzz; the sharing guard
  disabled goes red in the rule table (a forked LINEAR subtree renders the same bits, so only
  structure or the fuzz's work count can see it).
- Lesson from the first fuzz run: the `passes = N` expansion repeats a section's `Param` per
  section in `collectParams`; consumers dedupe by name, and the laws compare distinct names.
- Steps 1 and 2 (2026-09-15): `IgnitorDsl.Affine(inner, pre, mul, add)` = `mul · (x + pre) + add`
  in one pass, sanitised like the `Plus`/`Times`/`Plus` chain (`safeOut(mul · (x + pre)) + add`),
  an absent pre-add or add being `Constant(-0.0)` (the bitwise identity of the add; `+ 0.0`
  flips `-0.0`), `mul` without a default. The pre-add exists because `a·x + a·b` for
  `x.add(b).mul(a)` cancels at every zero crossing. Rule R2 folds one node per
  `x [.add] .mul [.add]`, never across an addition, composes literal multiply runs only where the
  chain's clamp cannot differ (growing runs; attenuating runs over a clamped input), folds a Param
  multiply alone, and leaves a left-hand scalar with a Param where it was written (param order).
  On its own it changes nothing measurable (a lone `mul` was one pass already); it was meant as
  the shape the Eq and shaper gain folds (steps 3 and 4) remove entirely.
- Steps 3 and 4 are WON'T IMPLEMENT (2026-09-15): the ceiling was measured before touching
  EqCore. `audio_benchmark` rows `guitar-rig*` (the rhythm rig of Der Schmetterling as an inline
  tree, `KLANG_BENCH_FILTER=guitar-rig` runs only them) DELETE every level `mul` and every
  `Drive` outright, and that buys about 1 to 2 µs of a 53 µs voice on node, under 4 %. The
  same rows put 22 µs of the rig's 35 in the shapers' oversampling, so that is where the work
  went (next entry). Measure the ceiling of a fold by deleting the nodes before building it.

## The ledger: a per-instrument harness for every optimization round (2026-09-16)

- `./gradlew runSongBenchmark --args=ledger` renders the six instrument pieces of Der Schmetterling
  (both guitar rigs, marimba, trommel, bass, drums) solo and ungated on the FROZEN song text of
  `FrozenPieces.kt` (the text at `v0.3.14`; a materially changed instrument gets a new dated
  snapshot, the old one stays) and on the live text, and APPENDS a row per piece to
  `docs/benchmarks/ledger.md` with `git describe` and the CPU. Run it after every optimization
  round; within one piece and one machine the engine is the only thing that moves between rows.
- `GraphCensus` (`ignitor/GraphCensus.kt`, a diagnostic, never on the render path) counts what a
  note asks of the engine from its OPTIMIZED tree: passes over the block, block-buffer reads plus
  writes per sample, bytes of held state, with a weight per node kind read off the runtime
  lowering (shared nodes once plus a memo read per extra consumer, scalar-only arithmetic
  nothing, the first variant only). `GraphCensusSpec` pins hand-counted graphs. The song
  benchmark sums it over the rendering voices after each measured block
  (`VoiceScheduler.renderingVoiceSounds()`, zombies excluded) and reports `voices`, `work`,
  `traffic`, `KiB`, `ns/smp/voice` and `ns/smp/pass`, the last being the engine's cost per unit
  of work, the number that must fall while a song's RTF may rise with the song.
- Why: a live song's RTF over time mixes a faster engine with a heavier song and can read as if
  the optimizations went the wrong way. The ledger holds the work still. First reading, the
  September round (`v0.3.12` by a worktree transplant against `v0.3.14`, medians of three, same
  machine, the spread of three runs about 10 %): melody guitar -18 %, rhythm guitars -14 %,
  marimba -14 % (one run caught a pause; its clean runs say -28 % and -14 %), trommel -44 %, bass
  -55 %, drums -56 % (culling: 24 active voices became 6 rendering). The drums' ns per sample per voice ROSE while their RTF
  halved, which is the case for counting work, not voices. A rhythm guitar note is 57 passes, 19
  of them the unison stack's voices, which the first census draft priced as one.

## div, minus and neg fold; a zero divisor is zero (2026-09-15)

- Engine rule (maintainer): a divisor of EXACTLY zero yields zero. `DivIgnitor` fills zero for a
  block-constant zero and renders nothing upstream (a dead branch), zeroes the sample for a zero
  in a divisor signal, and keeps the `SAFE_MIN` clamp for tiny non-zero divisors. `Ignitor.div(0.0)`
  is a block-constant zero. `Ignitor.neg()` is `mul(-1.0)`, clamp included; `NegIgnitor` is gone.
  A block-constant MULTIPLIER of exactly zero is the same dead branch in `Times`, `mul(0.0)`,
  `Affine` and an `EqCore` tap at gain zero (the band is not run, a bare `+ 0.0` is added, bit
  for bit the chain): without that, a Param divisor at zero skipped its upstream authored and rendered it
  optimized, and the voice's noise stream went out of step (round-1 review). `Recip` at zero is
  the deliberate exception, still the `SAFE_MIN` substitution.
- Rule R2 now covers `x.mul(k).minus(b)` (add `-b`, bare: `-0.0 - b` for a non-literal, never a
  `Neg`, which is a clamping multiply now), `x / k` (a multiply by the expression `1 / k`, so the
  runtime's guard applies once per block), a literal `x / 0` (a multiply by a literal zero: dead,
  but still built, so a phase pool under it and every pool after it draw as authored) and `neg()`
  (a multiply by `-1`, composing with a literal it follows; an inner flip before an attenuation
  over an unclamped input stays two nodes, the chain clamps the input first). `k - x` stays a
  Minus: the fold would clamp a bare subtract and cost more. A literal run whose product
  underflows to zero does not compose (a dead branch the chain never was). Nothing merges across
  an addition still. The lesson of the two review rounds: every place the optimizer SYNTHESIZES
  a coefficient (a reciprocal, a composed product) must not be a zero unless the authored
  coefficient was, or the dead branch renders on one side only and the noise stream slips.
- The parity oracle is relative to the block's loudest sample, at most full scale, not to the
  sample itself: the reciprocal multiply is an ulp off and a filter carries that to a zero
  crossing, where a per-sample relative measure blows up on nothing (fuzz seed 433); the cap keeps
  a saturated sample from buying its neighbours a tolerance of 1e3. Constancy and params stay
  equal on both sides, and scalar-only arithmetic, however deep, counts as no work.
- A lesson from the constant-fold parity rows: a SineIgnitor shared between the folded and the
  reference chain advances twice per block; build a fresh instance per chain.

## The oversampler's decimator is polyphase and indexed (2026-09-15)

- `Oversampler.decimate2x` no longer pushes every sample through a 15-slot ring with wrapping
  reads; output `m` is the FIR at `s[2m-13 .. 2m+1]` read straight out of the work buffer, with
  13 samples of per-stage history and a prefix view for the first 13 outputs (before that point
  an in-place write would land under an unread tap). Same taps, same summation order, so the
  output is bit-identical to the ring form for every block length; `OversamplerDecimatorParitySpec`
  keeps the ring implementation as its oracle over ragged block lengths (1 to 128, both sides of
  13 and 26) and goes red for a history one short, the in-place boundary one early, a tap off by
  one, or the short-block history shift dropped.
- Node 24, µs per block, one voice: `pluck+distort_4x` 13.4 -> 10.1, `pluck+distort_2x`
  9.4 -> 8.6, the guitar rig 53.2 -> 44.3 (its oversampling share 22 -> 12). Every oversampled
  stage takes it: the shaper on the voice doors, the shaper, crush and coarse on the strip (the
  ignitor crush and coarse doors have no oversampler). The copies into the prefix view and the
  history are plain loops: `copyInto` allocates a typed-array view per call on JS, and the
  guitar rig went from 45.5 to 44.3 µs when they went.

## Analog drift steps per block and ramps across it (2026-09-15)

- Every `AnalogDrift` lane (the two-layer OU pitch drift) is built at the BLOCK rate
  (`analogDriftStepRate(sampleRate, blockFrames)`, 375 Hz at 48k/128; `AnalogDriftCoeffs` follows
  the rate, so the time constants in seconds and the peak cents are unchanged) and stepped once
  per block (`beginBlock`: `blockStart` = the previous `blockEnd`, `blockEnd` = one step). Every
  consumer ramps the multiplier linearly across its window: `m = start; dm = (end - start) /
  length; inc = dt * m; m += dm`, one add per sample. `DriftLanes` blends per block
  (`prepareBlock(spread)`, `advanceLane`, `startOf`, `endOf`; no per-sample shared scratch, no
  `driftStep`). Sites: the mono sine, the pulse train, `WaveIgnitor`, the sine partial bank, both
  wave-engine stacks, both strings, the sample player. The filter drift (`FilterModRenderer`) ran
  per block already (a hold). Maintainer decision: per block and interpolated everywhere; not
  bit-identical, judged by ear.
- Why: `DriftLanes` stepped every lane per sample (xorshift, two one-poles, the blend), 13 lanes
  per note on the Schmetterling guitars, for a modulation whose fastest layer has a 50 ms time
  constant: 19 % of a drifting guitar, 13 % of the marimba, 15 % of the trommel.
- Measured (rig and live A/B, seeded, `docs/benchmarks/2026-09-15_1755*` = per sample,
  `_1759*`/`_1800*` = per block): rhythm rig 0.040 -> 0.036 with "no analog" at 0.035 (the drift
  is free now), lead 0.028 -> 0.024, trommel 0.042 -> 0.034, bass 0.0087 -> 0.0063, the live song
  0.106 -> 0.097, the frozen song 0.099 -> 0.082.
- Guards: `AnalogDriftRampSpec` (mono sine, saw and sample player against block-ramp reference
  accumulators; a one-voice supersine's increment read off three consecutive samples, wandering
  across blocks and only ramping within them; the ramp is not a step), `DriftLanesSpec` in block
  terms, `SinePartialBankSpec`'s drift reference on the ramp, `SuperStackDriftSpreadSpec`'s golden
  render retaken. Eleven mutations red.

## Polynomial e^x in the envelopes and the compressor, no fastLn (2026-09-15)

- `fastExp(x) = fastExp2(x · log2 e)` (`DspUtil.kt`) replaces `kotlin.math.exp` per sample in the
  envelopes' exponential curve (`adsrExpShape`, the DEFAULT curve on every stage since 2026-08-24,
  so every voice paid one `exp` per sample through attack, decay and release), the compressor's
  dB-to-linear gain (both the envelope and the lookahead path), the `exp()` ignitor and the two
  exp-based waveshapers (`expClip`, `stompBox`). Fast range `|x| < 22`, beyond it a platform `pow`.
- `fastExp2`'s polynomial was refitted as `1 + f + f(f-1)·r(f)` (r degree 5, 4.7e-11) so that
  `p(0) = 1` and `p(1) = 2` are EXACT in floating point: `fastExp(0) = 1`, `fastExp2(n) = 2^n` bit
  for bit. The envelope specs pin `g(0) = 0`, `g(1) = 1` and the sustain level to 1e-12, and the
  normaliser `adsrExpNorm` now goes through `fastExp` too (same expression above and below the
  line: `g(1)` is `A · (1/A)`, 1.0 or one ulp under). For callers: the pinned start makes the
  absolute error of `fastExp(x) - 1` shrink with `x` (6e-15 at 1e-6); what stays is parts in
  1e-9 of that small difference (no expm1 accuracy).
- Measured (voices and live A/B, same run): pink -10 %, hats -6 %, bass -3 %, the live song
  0.1096 -> 0.1073 (-2 %), frozen 0.0970 -> 0.0949. Less than the linear-curve experiment
  (`docs/benchmarks/2026-09-15_1539*`, 15 to 20 % on a simple voice) promised: the JVM's `exp` is
  an intrinsic. Per call (`audio_benchmark`'s `runMathBenchmark`, ns, library -> polynomial,
  2026-09-15, Ryzen 9 7940HS): JVM sin 6.1 -> 1.7, 2^x 8.9 -> 2.7, e^x 3.7 -> 3.5 (near parity);
  node 24 (V8, the worklet's engine) sin 7.2 -> 1.8, 2^x 12.1 -> 3.8, e^x 7.1 -> 4.5. The e^x
  path costs more than 2^x on both platforms because an envelope's `k · x` spans octaves 0 to 4
  and only octaves 0 and -1 skip the table (on V8 a lazy-init accessor of the top-level val plus
  `numberToInt` per read); the next step there, if wanted, is a branch ladder for the envelope's
  octaves or a bit-free `2^n` by conditional doublings.
- No `fastLn`: the song's orbit compressors (three calls, one instance per orbit covered, nine
  instances) together are 2.6 % of the song with `fastExp` in place
  (`docs/benchmarks/2026-09-15_171710`, the rig suite's `song` group, "no compressors": the song
  ungated and without its count-in so all eight cycles play the band, and its wall-clock seed
  pinned so both arms render the same 913 onsets; the earlier 5.8 %, `_162314`, was gated, seeded
  per pass and with the library `exp`), and `ln` is at most half of that. Every rig case renders
  the pinned seed now: an A/B on the live song was not controlled before. Without bit extraction (`Long` is banned) a log needs a compare
  ladder for the exponent, a divide and an odd series, about 40 cycles against a 60-cycle
  library `ln` on the JVM and near parity on V8: under 1 % of the song for real complexity.

## Table-and-polynomial 2^x in the pitch paths (2026-09-15)

- Vibrato (`2^(sin · depth / 12)`) and the pitch envelope (`2^(semitones · level / 12)`), in the
  voice strip (`VibratoRenderer`, `PitchEnvelopeRenderer`) and the Ignitor twins
  (`PitchModFactories.kt`), call `fastExp2(x)` (`DspUtil.kt`) instead of `2.0.pow(x)`: integer
  octave from a 64-entry table (`n` in `[-32, 32)`), fraction by a degree-7 minimax polynomial,
  relative error 4.0e-11 (7e-8 cents), bound `FAST_EXP2_MAX_REL_ERROR` = 1e-10 (1.7e-7 cents) asserted by
  `FastExp2Spec` (sweep, octave boundaries, both renderers against the pow law). Outside
  `(-32, 32)`, NaN and the infinities fall back to `pow` itself.
- Both pitch-envelope renderers skip the per-sample work once a block starts past attack + decay:
  the anchor ratio once per block. Same law (mutation-checked on both renderers, both buffer paths).
- Measured (one rig A/B pair, back to back, `docs/benchmarks/2026-09-15_1509*` = pow,
  `_1508*` = fastExp2): trommel 0.046 -> 0.044 medRTF (-5.5 %). Cases without a pitch path moved
  between -2 % and +14 % between the same two runs, so the whole-song figure (-1 %) is inside that
  noise. `pow` was a twentieth of a pitch-enveloped voice; the drum's cost is its partial bank,
  drift and body. The octaves 0 and -1 skip the table read (on JS a top-level val is read through
  a lazy-init accessor per call).
- Still on `pow`: the `accelerate` renderers (one `pow` per block, then a multiply per sample),
  `applySemitoneDetuneToFrequency` (per note), the generic `PowIgnitor`/`ExpIgnitor` (user
  arithmetic, any base).

## Polynomial sine in the oscillators (2026-09-15)

- Every oscillator sine (`SineIgnitor`, the partial bank `sinePartials`, the wave-engine sine behind
  `supersine`) calls `fastSin(phase)` (`DspUtil.kt`) instead of `kotlin.math.sin`: a degree-11 odd
  minimax polynomial on the folded half period, max error 1.3e-11 (-217 dB), bound asserted at
  `FAST_SIN_MAX_ERROR` = 1e-10 by `FastSinSpec` against a dense sweep and against the ignitor's own
  render. The phase accumulator, drift and modulation are untouched; only the function changed, so
  the per-sample drift multiplier and per-sample pitch modulation keep working (a rotation
  oscillator would not: it needs a constant increment). The function is bit-identical on JVM
  and JS (pure arithmetic; `Math.sin` never promised that); a whole voice still is not, `pow`,
  `ln` and `cos` upstream of the phase remain platform transcendentals.
- The fold is exact for a quarter period past either end of `[0, 2π)` and diverges fast beyond, so
  every oscillator wraps first. Review found the one site that did not always: the wave-engine
  stacks used the one-subtract `smallNumFastMod` wrap, unsafe once `|dt| >= 1` (a frequency past
  the sample rate in either sign, or `spread(200)` with cents typed into the semitone door), where
  the library sine gave bounded aliasing and the polynomial gave 1.8e34; the trapezoids (stacks and
  the single-voice `WaveIgnitor`) parked on the low plateau (a DC offset) for a positive dt and rode
  the rise ramp without bound for a negative one, and now alias instead. All three sites hoist
  `safeWrap = pm != null || drift != null || !(abs(dt) < 1.0)` per block (drift can hold a near-1
  dt over the edge for seconds). Guard: `SuperSineOutOfRangePhaseSpec` (red first, both signs,
  drift, sine and trapezoid, stack and single voice). Trap for a later
  "finish the job": `FmRenderer` accumulates its modulator phase per sample and wraps only at block
  end, so swapping its `sin` needs a per-sample wrap first.
- Why not a lookup table: same op count with linear interpolation, worse accuracy (3e-7 at 4096
  entries), memory traffic against the audio buffers, bounds checks on JS.
- The modulators followed (2026-09-15, later the same day): the FM modulator (`FmRenderer`), both
  vibrato LFOs (`VibratoRenderer`, `VibratoModIgnitor`), both tremolo LFOs (`TremoloRenderer` via
  `lfoNorm`, the `tremolo` ignitor) and the grain Hann window (`cos(2πp)` as `fastSin(2πp + π/2)`,
  inside the fold for `p` in `[0, 1)`). The FM modulator wrapped at block end only and the
  vibratos never wrapped a negative rate, which `sin` tolerated and the polynomial does not: each
  now wraps per sample with the same hoisted `safeWrap` as the stacks (`!(abs(inc) < TWO_PI)`).
  Guard: `ModulatorPhaseWrapSpec` (FM against a `sin` accumulator, both signs past the sample
  rate, negative LFO rates over seconds, both vibrato buffer paths). Measured (`voices` A/B):
  FM bell 0.0026 -> 0.0022, vibrato + tremolo pad 0.0064 -> 0.0042, the lead 0.022 -> 0.019.
  `TremoloRendererSpec`'s DrunkenSailor guard now holds the shipped tremolo to the polynomial's
  bound instead of bit-identity (the four spellings still must be bit-identical to each other).
- The phase-pool selection at note-on (`Ignitors.kt`, `im += g * sin(a)`), the phaser LFO (two
  `sin` per block) and the `sineshaper` waveshaper keep the library `sin`: not per sample, or
  not a phase.

## Silence culling (2026-09-15)

- A voice stops rendering once it is in its RELEASE and its own output has stayed under
  `VOICE_CULL_FLOOR` (-100 dBFS, the same constant as the cylinder's silence test) for the cull window (`VOICE_CULL_SECONDS` = 50 ms, per voice via
  `cull(seconds)`, off via `noCull()` = `VOICE_CULL_NEVER`; a voice with a `tremolo` is excluded
  unless `cull` is set). Measured in `SendRenderer` (`BlockContext.voiceOutputPeak`, only until
  the voice has been heard and then in its release, pre solo-multiplier, bounded by the largest send), decided in
  `Voice.render`, counted by `VoiceScheduler.culledVoicesTotal`; the benchmark tables carry a
  `culled` column.
- **A culled voice is a zombie, never an early removal.** It keeps its active-list slot and renews
  its orbit lease until its scheduled end. Removing it early reorders the active list, and the
  orbit lease goes to whoever renders first after an owner dies: measured on Der Schmetterling, a
  culled hat changed which of guitar 3 and the bass owned orbit 3, a -32 dBFS difference. With the
  zombie the null-diff against no culling sits at the floor. Lesson: any change to WHEN a voice
  leaves `active` is a mix change on every orbit with mixed bus configs.
- **The gate is never culled, and a voice that has not sounded yet is never culled** (`Voice.heard`
  latch). Decided over the July `cullAfter` fraction: the gate says "told to stop", the latch says
  "has started"; together they need no parameter and protect slow attacks, delayed sample onsets and
  ignitor attacks that outlive a short gate. Residual risk: a release tail with gaps longer than the
  window (a `tremolo` voice is excluded for that reason).
- Where it pays (Der Schmetterling, 2026-09-15 rig suite): sample drums with 2 s releases (85 to 90%
  silent), the Orchestertrommel (about 40%), the marimba (20 to 40%). Where it cannot: the guitars,
  whose notes live 170 ms (gate 116 ms + 30 to 50 ms release) and are audible throughout.
- Guard: `VoiceCullingSpec` (mutation-checked: gate rule, pre-multiplier peak, frames-not-blocks,
  negative-means-never). Doors: `LangCullSpec`. Record: `docs/tasks-archive/2026-09/20260915-voice-culling.md`.

## Body / Vowel resonators + live-update fixes (2026-07-04)

- **Body & vowel are ORBIT-level Katalyst effects**, not per-voice filters: `KatalystBodyEffect` /
  `KatalystFormantEffect` (`cylinders/katalyst/`) run once on the summed orbit mix; the owner voice
  configures them via `VoiceLease` (first-writer-wins). `VoiceFactory` pulls `FilterDef.Body`/`Formant`
  OUT of the per-voice chain — its `toFilter` arms for them are unreachable (`error()`). The two
  effects are **intentional un-deduped twins** — change one, mirror the other.
- **Materials** = pure data in `SprudelBodyMaterials` (sprudel commonMain): `names` / `descriptions`
  / `modesFor(name)` → `List<FilterDef.Body.Mode>(freq,db,q)`; `none` = reset (vowel `none` too). Blend
  = `ParallelMixFilter(inner, mix, floor)`; `floor` user-settable via `bodyFloor()`/`vowelFloor()`
  (`FilterDef.Body/Formant.floor`, null → `BODY_FLOOR`/`VOWEL_FLOOR`), `bodyMix` uncapped >1 (raw).
  Sprudel fields grouped in `SvdBody`/`SvdVowel`. UI: `SprudelBodyEditorTool` (sprudel jsMain).
- **Live-change declick**: `KatalystFilterSwap` crossfades the bank on any material/mix/floor rebuild.
- **Live-update double-voice fix**: `VoiceScheduler.replaceVoices` now dedups incoming voices vs
  already-active ones (`ScheduledVoice.isDuplicate` = startTime+data); grace window 50→200 ms in
  `KlangPatternScheduler`. Root cause + the per-playback engine model: `ref/architecture.md`.
- **Benchmarks**: isolated `Body`/`Vowel` cases in `EffectBenchmark`; a `+vowel(a)` case in the song
  benchmark. Body (8-band) is the priciest single filter (~= reverb), vowel ~62% of it. **The old
  "superimpose × body = super-additive" finding is now OBSOLETE** — body moved to orbit-level, so
  cost(body|super) ≈ cost(body|no-super) (applied once per orbit, not per copy). `analog` still
  multiplies (per-voice). Refresh: `docs/benchmarks/2026-07-04_*_song_jvm.md`.

## Song-level CPU benchmark + Der Schmetterling deep-dive (2026-07-03)

New song-level benchmark harness in the **root** module: `src/jvmMain/kotlin/SongBenchmark*.kt` +
`FrozenSongs.kt` (byte-exact frozen song code so the baseline doesn't move when `builtinsongs/*` change).
Run: `./gradlew runSongBenchmark [--args=voices|ladders|experiments|songs|all]`. It compiles real
song code → `KlangPattern` → drives the actual offline DSP graph, timing every block (medRTF =
steady-state avg, peakRTF = busiest block; first 32 blocks skipped to drop the delay-ring alloc spike).
Unlike `:audio_benchmark` (isolated effects from hand-made VoiceData) this measures whole voices/songs.
Full writeup: `docs/benchmarks/2026-07-03_der-schmetterling-cpu-analysis.md`.

**Findings (JVM; browser ≈ ×2.7):** Der Schmetterling's cost is ~98% in its 3 super-synth voices
(GTR2 > GTR1 > LEAD); bass/drums/pink are negligible. Cost ranking:
`superimpose` (voice-count multiplier — each copy re-runs the WHOLE per-voice chain; GTR2's nested
superimpose = 1088 voices) ≫ `body` (8-band parallel SVF/voice) ≈ `analog` (per-voice drift) >
multi-band filters > distort-oversample/room > `unison` (cheap — shares one effect chain) >
`pipeline("pedal")` (~free, just reorders). **Key combination = per-voice effect (`body`/`analog`)
UNDER a `superimpose` stack → paid once per copy (proven super-additive by a 2×2).** Fix levers: cut
superimpose depth on the guitars, prefer `unison`/`spread` over width-superimposes, don't recompute
body/analog per superimposed copy.

## Current Status

- **JVM**: Full audio output via javax.sound.sampled ✅
- **JS**: Full audio output via Web Audio API + AudioWorklet ✅
- Synthesis: oscillators (sine, saw/ramp/square/pulze/triangle + raw zaw/zamp, super* unison,
  karplus, noise family) + sample playback. Sine partial banks (2026-09-07): `IgnitorDsl.Sine` carries
  `harmonics`/`octaves`/`suboctaves` counts with rolloffs, `fundamental` gain and `analogSpread`;
  `Ignitors.sinePartials` renders the bank in one pass, the runtime builds the plain `SineIgnitor` for
  literal defaults (`Sine.isPlainSine()`), partials at Nyquist are silent. Plan and decisions:
  `docs/plans/sine-partial-banks.md`; guard: `SinePartialBankSpec` (golden test against the hand-rolled
  Der Schmetterling stack).
- Effects: delay, reverb, phaser, compressor, ducking, distortion, bit-crush, tremolo

## One drift-lane container: `DriftLanes` (2026-09-10)

Every multi-voice oscillator takes its analog drift from one component
(`audio_be/.../ignitor/DriftLanes.kt`): N own `AnalogDrift` lanes plus one shared lane, blended per
sample by `analogSpread` with constant-power weights, in ratio-minus-one space. The unison stacks,
the sine partial bank and the superpluck strings all plug into it, and `analogSpread` is a knob on
the whole super family now (`supersaw`, `supersine`, `supersquare`, `supertri`, `superramp`,
`superpluck`), same word and same 0 to 1 scale as on `Osc.sine`: 0 is one shared walk, so the stack
wobbles as a single physical oscillator and its unison detune stays static; 1 is a lane per voice,
the default.

**What the default does and does not promise.** Depth, spread and character at spread 1 are exactly
what they were. A seeded RENDER is not. A container takes one int off the voice rng when it is built
(the shared lane's seed, below), so every drifting oscillator seeds its lanes one draw later than it
did before this change. On a unison stack that int lands right before `drawGainJitterFor`, so the
per-voice GAIN BALANCE of every drifting supersaw, supersine, supersquare, supertri and superramp is
redrawn as well, inside its usual range (`SUPERSAW_GAIN_JITTER` 0.15, up to 15 percent per voice
before renormalisation), not just the drift walk. A stack with `analog = 0` builds no container,
draws nothing extra and is untouched. `SuperStackDriftSpreadSpec` pins
one supersaw case sample for sample against the engine as it stands now, which catches an accidental
change to the drift path; it is not a claim about the pre-DriftLanes sound, and the sine stack, the
partial bank and the pluck have no pin of their own. The shipped layer this actually reaches is Der
Schmetterling's bass harmonics (`harmonics(9, 1.0).fundamental(0).analogSpread(0.1)` under
`.analog(feel)` with feel 12): same depth, same spread, different walks.

Two things a reader needs. The endpoints are EXACT, not a limit of the blend: at spread 1 only the
own lane is advanced, at spread 0 only the shared one, which makes spread 0 measurably CHEAPER
(JVM, supersaw 8v with analog: 5.34 against 6.90 µs per block; both platform runs are in
`docs/benchmarks/2026-09-10_drift-lanes_jvm.md` and `..._nodejs.md`). And the draw order is part of
the contract: one int
for the shared lane's SEED at construction, then an own lane whenever `ensureLanes` first reaches its
index. The shared lane is built lazily from that seed and takes nothing further from the voice
stream, so WHEN the spread first drops below 1 cannot shift what any other consumer of the voice rng
gets: without that, lowering `analogSpread` on a superpluck re-rolled its string excitation bursts.
A stack that shrinks retires those lanes (`retireLanes`) and rebuilds them fresh on regrow, so a
returning voice attacks in tune, and the superpluck does the same, because a regrown string
re-plucks. The bank never retires: a partial that comes back resumes its walk, which is inaudible at
cent scale and keeps a count sweep from re-seeding the whole spectrum.

**The JS lesson, a sibling of the loop-shape one below.** The first cut read the blend's per-block
values (the two weights, the two mode flags, the lane out of its array) off the container INSIDE the
sample loop, through a public inline method. On the JVM that is free; on Kotlin/JS it cost a drifting
8-voice supersaw about 16 percent and the superpluck about 12, measured against the pre-change engine
in the same sitting. The fix is the same shape as the loop-shape lesson: hoist every per-block value
into a LOCAL before the sample loop (`ownLane(n)`, `sharedWalk()`, the two weights) and blend through
one `inline fun driftStep(...)` whose inputs are all locals. Node run-to-run variance is wide enough
(two runs of one engine differed by 28 percent on a row) that a claim like this needs the untouched
`sine+analog` row as a control and a ratio, not a raw number.

`PolyAnalogDrift` went with this change. Nothing ever used it: it advanced all lanes sample-major
while every hot loop here is voice-major. Its rationale, that independent lanes are what keeps a
unison stack organic, is now the reason `DriftLanes` defaults to spread 1.
Task: `docs/tasks-archive/2026-09/20260910-drift-lanes-analog-spread.md`.

## Loop shape beats block-pass count (2026-09-07, sine partial banks)

`IgnitorBenchmark` case `sine-harmonics7` against `sine-harmonics7-tree` (the hand-rolled
`Sine + Sine(2f)*1/2 + ...` tree it replaces): a sample-major bank loop (outer loop over samples,
inner loop over partials reading/writing `phase[]`, `gain[]`, `inc[]`) rendered at 41 µs/block, the
tree at 24. Partial-major (outer loop over partials, inner tight loop over samples with the phase
in a local, the `SineStackIgnitor` shape) brought the bank to 22 µs. So: twenty-one block passes
cost less than one loop that keeps its phase in an array. When writing a multi-voice or multi-partial
oscillator, render voice-major with locals, accumulate into the buffer (`if (first) s else buffer[i] + s`),
and sample any per-sample shared state (here the shared drift walk) into a scratch array first.
`sin()` dominates the rest; the 2-trig-per-sample recurrence in `docs/plans/sine-partial-banks.md`
§5.3 is the remaining lever, gated on a song profile.

## VCA Gain De-click Smoother (2026-06-08)

`EnvelopeRenderer` (amp VCA) now runs a one-pole low-pass on the final gain to
de-click ADSR segment joins. Root cause of the "plop": the shape curves are
value-continuous (C0) but **not slope-continuous (C1)** — at the attack→decay
peak, gate-off, and instant cutoff the gain changes slope abruptly. That corner
is a fixed-size event; on a **low note** the slow carrier can't mask it, so it
reads as a "plop" (2nd-difference corner/floor ratio ~525x at 40Hz vs ~4x at
880Hz). `exp` curves are worst (steepest joins).

- Constant `ENV_DECLICK_SECONDS = 0.001` (1ms) in
  `audio_bridge/constants/EnvelopeDefaults.kt` (moved there 2026-08-11 — it is a
  `StageDsl.Vca` wire default). The MATH stayed in `AdsrCurveMath.kt`:
  `envDeclickCoeff(declickSeconds, sampleRate)`, `adsrExpNorm`, `ADSR_EXP_NORM` and both `adsrExpShape` overloads.
  Tunable by ear like `ADSR_EXP_K`. The 25x-corner- reduction-at-40Hz / 0-residual-tail measurement was taken at 0.5ms.
- `Voice.Envelope` gained `smoothedLevel` + `smoothPrimed` state. Primed to the
  **first rendered gain** (not env.level) so always-on voices and mid-phase block
  starts don't fade in; only segment-join corners get rounded.
- Applies to the amp VCA only. `EnvelopeCalc` (filter-mod, control-rate) and
  `IgnitorEnvelopes` (per-ignitor `.adsr()`) are NOT de-clicked — separate
  surfaces, less audible. Hard cuts (`releaseFrames == 0`, chokes) ARE de-clicked
  (single code path, 0.5ms fade instead of 1-sample stop) — chosen deliberately.
- Guard: `EnvelopeDeclickSpec` (renders through the real renderer, asserts gain
  per-sample slew < 0.1). `EnvelopeTest` rewritten to assert phase *behaviour*
  (monotonic direction, settled endpoints, the de-click fade) since mid-ramp
  values now lag; exact raw-curve shape stays in `EnvelopeShapeTest` (generator).

## Ignitor ADSR knobs — declick + expK as Slots (2026-07-04)

`.adsr(...)` (per-ignitor envelope, `IgnitorEnvelopes.AdsrIgnitor`) gained two knobs on
`IgnitorDsl.Adsr`, wired as **`IgnitorDsl.Slots` Params** (like the noise knobs), so they're
`oscParam`-addressable, patternable, and discoverable via `collectParams()`. Both
**behaviour-identical by default**:

- `declickSeconds` (`Slots.declickSeconds`, default `0.0` = off — this surface is intentionally
  NOT de-clicked; see the VCA entry above). >0 runs the same one-pole (`envDeclickCoeff`) on the
  output gain, primed to the first rendered level (no fade-in on always-on / mid-phase starts).
- `expK` (`Slots.expK`, default = `ADSR_EXP_K`, the single declaration in `audio_bridge/constants/`). Feeds
  the parameterized `adsrExpShape(x, k, norm)` the amp VCA already used.

Both are `IgnitorDsl` fields read per-block via `readParam` in `AdsrIgnitor` (declick coeff + `expNorm`
derived once per block — NOT ctor-precomputed, since they can now be modulated). KlangScript
`.declickSeconds(x)` / `.expK(x)` take `IgnitorDslLike` (copy-onto-`Adsr` or wrap, same idiom as
`adsrCurves`). Guards: `AdsrIgnitorKnobsSpec` (defaults identical, declick rounds the attack→decay
corner, larger expK steepens the exp decay, + oscParam override reaches both slots), `StdLibOscTest`
(dual-language build), `IgnitorDslWireCodecSpec` (round-trip). First of the `engine-tuning-profile`
Part-A wrapper knobs; filter feel knobs + analog-drift carriers still open.

## Oscillator Engine Unified (2026-06-05)

The `audio_be` oscillator code was consolidated (branch `dedicated-cycle-time`) — see
`docs/tasks-archive/2026-06/20260605-oscillator-engine-unification.md` for the full writeup:

1. **One shape engine** — `analogSawShape` + `pulseTrapezoidShape` → one `waveTrapezoid`
   (`DspUtil.kt`); `SawVoiceState` + `PulseWaveState` → one `WaveVoiceState`. One `WaveIgnitor` behind
   saw/ramp/square/pulze/triangle (+ raw zaw/zamp). Finite-slope edges, no PolyBLEP.
2. **Control-rate value on `interface Ignitor`** — `controlRateValueOrNull(freqHz, ctx)` (non-null iff
   block-constant) + `blockStartValue(...)`; the `ControlRateIgnitor` marker is gone. Pointwise
   combinators fold, so control-rate param reads (freq/analog/duty/detune/spread) no longer render a
   scratch buffer.
3. **One unison engine** — all five super-oscillators (supersaw/ramp/square/tri/sine) share
   `DetunedStackIgnitor` (→ `TrapezoidStackIgnitor` → `SawStackIgnitor`/`PulseStackIgnitor`;
   `SineStackIgnitor`). Each variant has its own `SUPER*_{SIDE_ATTEN,GAIN_JITTER,DETUNE_POWER}` consts
   in `OscillatorTuning.kt`, seeded to the supersaw values. supersquare/supertri/supersine dropped
   PolyBLEP/flat-gain → now center-dominant, per-voice drift, on-note tuning (sound changed by design).

## Architecture Decisions

**State lives at the granularity it is bound to** (FE/BE state placement, 4 steps done 2026-08).
playbackId-bound state sits in `PlaybackEngine` (BE) and the per-playback controller (FE);
global state in `AudioBackendContext` (BE) and `KlangPlayer` (FE). Share only what is expensive
to recreate AND safe to outlive a playback: samples (content-keyed) and built-in oscillators.
Custom oscillators and engines are per-playback so they are collected with the engine. Do not
remove the past-cutoff in `VoiceScheduler.promoteScheduled`: it stops `ReplaceVoices` from
re-promoting already-played voices (duplicate burst). Open follow-up:
`docs/tasks/future/worklet-clock-divergence.md`.

- **Block-based processing**: fixed-size blocks (128–256 frames). No per-sample allocation in hot paths.
- **Ring-buffer IPC**: `KlangCommLink` uses two `KlangRingBuffer`s — no locking between threads.
- **Voice pipeline**: `Voice` interface + `VoiceImpl` runs a **Pitch → Excite → Filter** pipeline.
  Filter stage is a composable `List<BlockRenderer>` built by `buildFilterPipeline()`.
  Pitch stage is still inline (pending extraction). Excite delegates to `Ignitor`.
- **Cylinders = effect buses**: up to 16 mixing channels, each with independent delay/reverb/phaser/compressor/ducking.
- **Master limiter**: −1 dB threshold, 20:1 ratio, **5 ms lookahead + 5 ms gain-smoothing**, 100 ms release — always
  last in chain, on the summed mix. The lookahead delays the whole output by 5 ms (uniform, so nothing desyncs). The
  *authored* `limiter` stage (`Master(m => m.limiter(...))`) differs on purpose: no lookahead, 1 ms one-pole attack, because it is per-playback.
- **`NullLiteral` / singletons**: `audio_bridge` data types use data classes; expect/actual for platform types.
- **Every DSL is immutable at construction time (maintainer principle, 2026-09-05)**: nodes,
  builders, `MasterDsl`, `PipelineDsl`, patterns. A "mutating" call returns a new instance; never
  a `var`, never a mutable builder, never a `MutableList` escaping a DSL type. Why: it removes an
  entire bug class (shared-state mutation at a distance) and therefore an entire chapter of
  explaining; composition falls out of it. Runtime data may be mutable for performance
  (single-owner `SprudelVoiceData`), that is engine-internal and stays. Receiver lambdas were
  parked for exactly this reason (they need mutable builders).
- **Configure lambdas + builder types (decided 2026-09-05, BUILT 2026-09-06, steps S1 to S7)**: sub-type knobs
  (`analog`, `voices`, `spread`, `phasePool`, `band`/`tap`, `wet`/`dryFloor`, master stage and
  pipeline stage knobs) move OFF `IgnitorDsl`/`MasterStageDsl`/`StageDsl` onto immutable
  `Osc*Builder`/`EqBuilder`/`Master*Builder`/`Pipeline*Builder` classes in `klangscript-libs`,
  annotated for KlangScript directly in `klangscript-libs` (module split 2026-09-06). `MasterFx`, `Stage`,
  `Master.of`, `Pipeline.of` and all 17 sub-type extension objects are DELETED, no back-compat.
  Plan: `docs/tasks-archive/2026-09/20260906-dsl-configure-lambdas.md`.

## Filter Saturation Dead-End — Linear SVF is the Right Choice (2026-05-28)

Two attempts to add tanh saturation to `SvfLPF`/`SvfHPF` (for "analog warmth"
inside the filter) both failed and were reverted:

1. **Saturating `v1` in the `ic1eq` integrator state update** broke the SVF
   spectral function. `ic1eq` no longer represents the linear bandpass output,
   so `v2` (LPF tap) becomes a corrupted-LPF; the HPF formula `v0 - k·v1 - v2`
   subtracts a corrupted-LPF from input → lows leak through, notch appears at
   cutoff. Symptom matched user reports exactly ("dip in the middle, lows
   passing through HPF").
2. **z⁻¹-delayed saturated feedback** (Zavalishin §5.5 form,
   `bpFb = tanh(drv·v_BP)/drv` used as `v_HP = (v0 - k·bpFb - g·ic1eq - ic2eq)/(1+g²)`)
   — `fastTanh` hard-caps `bpFb` at ±1/drv ≈ ±0.286. Linear feedback `k·v_BP`
   grows with `v_BP` to provide damping; capped feedback loses ~95% of its
   damping strength at hot resonance peaks. Result: filter goes unstable at
   Q ≥ 5 with `analog>0`, peak amplitude ~40× linear.

**Conclusion**: filter feedback nonlinearity needs either (a) a non-hard-capping
saturator like `asinh` (growth-friendly), (b) proper Newton iteration on the
implicit equation, or (c) oversampling. None are trivial. **Filter is now
purely linear** at all `analog` values; the `analog` parameter and
`bpFb`/`g`/`invOnePlusGsq` infrastructure remain in `BaseSvf` / `SvfLPF` /
`SvfHPF` for future re-introduction.

**Where the "warmth" comes from now** — and it works: upstream `.distort()` /
`.onepole()` (ex-warmth) / `.clip()` shapers, oscillator OU drift (per-voice), per-voice
cutoff offset (`FILTER_CUTOFF_OFFSET_PER_ANALOG`), and the coefficient ramp
(`FILTER_SMOOTH_SAMPLES`) on cutoff changes. These collectively give the
"plastic pipe → wooden warm" transformation without needing the filter itself
to be nonlinear.

**Files touched**: `audio_be/.../filters/LowPassHighPassFilters.kt` (kdoc on
`SvfLPF`/`SvfHPF` documents the dead-end inline so future attempts know to
either change topology or change saturator), `docs/agent-tasks/plastic-pipe-hunt.md`.

## Numerical Safety Contract + Distort DC-Lock Fix (2026-04-27)

Established `SAFE_MIN = 1e-15f` / `SAFE_MAX = 1e15f` as the engine's numerical
safety bounds (matches SuperCollider/ChucK convention — see
`audio/ref/numerical-safety.md` for the reasoning and framework precedents).

Two helpers in `Ignitor.kt`: `safeDiv(d)` (sign-preserving magnitude clamp ≥ SAFE_MIN,
scrubs NaN) and `safeOut(v)` (output clamp to ±SAFE_MAX, scrubs NaN to 0).
Applied to divisor-class ops (`Div`, `Mod`, `Recip`) and output-clamp ops
(`Times`, `Pow`, `Exp`, `Sq`, `mul-by-const`).

Distort/clip path now applies the DC blocker **unconditionally** (was previously
only for `diode`/`rectify` shapes). Fixes user-reported rail-lock at extreme
drive amounts where output got stuck at -1 due to envelope-decay asymmetries.
Trade-off: transient overshoots up to ~2x at sharp transitions of square-like
signals — existing tests updated, master limiter handles the spike.

**Edge-overshoot bounder moved to ignitor output (2026-04-28)**: the 2×
DC-blocker transient was audible as per-cycle clicks on heavy-drive
`distort()`/`clip()` users (reported on the rhythm-pattern guitar ignitor in
`TestTextPatterns.kt`). The fix is a single `coerceIn(-1f, 1f)` (hard clip)
pass at the **`IgniteRenderer` boundary** — caps the entire ignitor output
once per output sample at base rate. This is cheaper than tanh-saturating
inside each `distort()`/`clip()` stage at oversampled rate, and identity for
`|x| ≤ 1` so existing tests + clean ignitors are untouched.

- **Why hard clip vs soft tanh wrap**: tanh compresses everywhere
  (`tanh(1)=0.78`, ~22% reduction at peak), changing exact-amplitude
  expectations across ~20 voice/envelope tests. Hard clip is identity for
  `|x| ≤ 1` (preserves all those expectations) and only acts on the rare
  rail-edge transients above ±1 — exactly the click case. Cheaper too
  (`coerceIn` is 2 FLOPs vs `fastTanh`'s ~6).

- **Why at the wrapper, not per-stage**: per-stage cap would fire inside the
  ignitor on `dcOut` peaks of ~±2; the wrapper fires on the final ignitor
  output (post the user's internal LPF/HPF/ADSR), once per output sample at
  base rate (vs `factor×` per oversampled sample inside each distort). Per-stage
  code is back to the pre-2026-04-28 simple `dcOut.toFloat()` write — no
  in-oversampler placement, no α rate-compensation, no per-stage tanh.

- **Trade-off**: hard clip adds a small derivative-discontinuity at ±1, which
  technically aliases. Mitigated because the wrapper sees the signal *after*
  the user's internal LPF/HPF (already band-limited), and the master limiter
  on the cylinder bus handles residuals.

- **Industry context** (researched while designing this): Surge XT and Vital
  don't post-DC-block at all — Klang's click symptom only exists because of
  the 2026-04-27 rail-lock guard. ChowDSP / Jatin Chowdhury use
  Antiderivative Antialiasing (ADAA) to side-step both oversampling *and*
  post-shape DC issues — captured as future direction.

- **Future**: ADAA migration to replace the oversampler+DC-block stack
  entirely (see `chowdsp_waveshapers` and `jatinchowdhury18/ADAA`). Drive
  smoothing (Surge XT's `lipol` pattern) for parameter-change clicks. Soft
  saturator option (tanh) at the wrapper as opt-in for "warmth" character.

- **Scope**: the wrapper lives in `audio_be/.../voices/strip/ignite/IgniteRenderer.kt`.
  `Ignitor.distort()`/`Ignitor.clip()` are responsible only for shape +
  DC-block (no longer for bounding to ±1) — see
  `audio_be/.../ignitor/IgnitorEffects.kt`. The legacy filter-stage
  `DistortionRenderer` (covered by `effects/DistortionSpec.kt`) is a separate
  code path and unchanged. End-to-end coverage: `IgnitorsTest.kt` has both a
  direct-call test (asserts `< 2.5`, the pre-cap envelope) and an
  IgniteRenderer-wrapped test (asserts `< 1.05`, the in-engine invariant).

- **Regression harness — grow it over time**:
  `audio_be/src/commonTest/kotlin/ignitor/GuitarClickHuntTest.kt` is the
  click-hunt harness. **Standing intent: keep adding setups to it whenever a
  new click symptom is reported or a new ignitor archetype is introduced.** It
  is the safety net for any future change to `distort`/`clip`/`IgniteRenderer`
  / DC-blocker / oversampler.

**Pitch-mod factories closed (same day)**: code review identified four pre-existing
hazards in `PitchModFactories.kt` (`vibratoModIgnitor`, `accelerateModIgnitor`,
`pitchEnvelopeModIgnitor`, `fmModIgnitor`) with the same overflow pattern. All
four now apply `safeOut`/`safeDiv`. Stress test for extreme depth exposed a
**latent O(N) hazard in `wrapPhase`** (`DspUtil.kt`) — the `while (p >= period)
p -= period` loop hung the audio thread for huge phase increments coming out of
a SAFE_MAX-clamped pitch-mod ratio. Rewritten as O(1) modulo with `Inf`/`NaN`
recovery; common-case fast subtraction path preserved.

**Generalised lesson** (see `audio/ref/numerical-safety.md` "Why this matters"):
the per-op safety contract guarantees **finite + bounded**, NOT **small**. Any
audio-rate consumer of a value clamped to `±SAFE_MAX` must run in O(1)
regardless of the value's magnitude — no subtraction loops, no retry loops, no
state scaling with input. The `wrapPhase` rewrite is the canonical pattern.

## Ignitor Composable Architecture (2026-03-19)

New package `audio_be/.../ignitor/` — composable per-voice effect combinators.
Files: Ignitor, IgniteContext, ScratchBuffers, IgnitorEnvelopes, IgnitorFilters,
IgnitorEffects, IgnitorPitchMod, IgnitorFm. Phase 0+1 complete (additive, nothing wired in yet).

## Ignitor Param Slots — "Everything is a Signal" (2026-03-26)

All numeric ignitor parameters converted from `Double` to `IgnitorDsl` (Param slots).
`IgnitorDsl.Param(name, default, description)` is a new leaf node that produces a constant signal
by default, but can be replaced with any ignitor subtree for audio-rate modulation.

Key changes:

- **`ParamIgnitor`** runtime class fills buffer with constant value
- **`getParamSlots()`** walks DSL tree to discover all Param leaves (for generic UI)
- **Gain separated from oscillators**: factories produce raw output, gain applied via `withGain()`
- **`analog`** param: lazy `AnalogDrift` init on first block via `initAnalogDrift()`
- **Control-rate params** (filter cutoff, ADSR times, etc.): read once per block via `readParam()`
- **oscParams override**: `toIgnitor(oscParams)` propagates through tree; Param nodes check map by name
- **New DSL nodes**: Distort, Crush, Coarse, Phaser, Tremolo, Vibrato, Accelerate, PitchEnvelope
- **Convenience wrappers**: Double-accepting extension functions on IgnitorDsl still work

### Known Issues to Revisit

- ~~**Filter envelope release jumps from sustainLevel**~~ **FIXED (2026-03-23)**:
  `FilterModRenderer` now calculates the actual envelope level at gate end using
  `levelAtPosition()`, matching the amplitude ADSR's capture behavior.
- **`pitchEnvelope()` per-sample `pow()`**: `2.0.pow(amount * envLevel / 12.0)` called per sample.
  Expensive (~50-100ns per call). Could optimize sustain phase (constant value, compute once).
  `accelerate()` was already optimized to multiplicative stepping.
- **phaseMod save/restore lacks exception safety**: No try/finally wrapper around the
  save→set→generate→restore pattern in vibrato/accelerate/pitchEnvelope/fm combinators.
  Low risk (exceptions in audio hot paths are rare and fatal), but not structurally safe.
- **Stringly-typed distortion shape**: `distort(amount, shape = "soft")` uses String for shape
  selection. Typos silently fall through to default. Consider enum in the future.

## BlockRenderer Pipeline Architecture (2026-03-23)

Voice rendering refactored into **Pitch → Excite → Filter** pipeline using composable `BlockRenderer` stages.

Key files:

- `voices/strip/BlockRenderer.kt` — `fun interface BlockRenderer { fun render(ctx: BlockContext) }`
- `voices/strip/BlockContext.kt` — shared context (buffers, timing, ignitor)
- `voices/strip/EnvelopeCalc.kt` — shared control-rate envelope calculation
- `voices/strip/pitch/` — VibratoRenderer, AccelerateRenderer, PitchEnvelopeRenderer, FmRenderer
- `voices/strip/excite/IgniteRenderer.kt` — wraps Ignitor as BlockRenderer
- `voices/strip/filter/` — FilterModRenderer, AudioFilterRenderer, EnvelopeRenderer, FilterPipelineBuilder

Status: **Complete.** `Voice` (merged from Voice interface + VoiceImpl) runs a `List<BlockRenderer>` pipeline:
Pitch renderers → IgniteRenderer → Filter renderers → SendRenderer.
Bus pipeline: composable `KatalystEffect` pipeline (`cylinders/bus/`).
`VoiceScheduler` split into `VoiceScheduler` (scheduling) + `VoiceFactory` (voice construction).
Legacy effect filters (BitCrush, SampleRateReducer, Distortion, Tremolo, Phaser) replaced by
BlockRenderer implementations. ~426 tests across 35 files.
See `docs/agent-tasks/audio-pipeline-open-topics.md` for remaining open topics.

## Distortion Shape Catalog Extension (2026-05-21)

Added 7 new waveshapers to `ClippingFuncs` (+ `DistortionShape` enum + parser dispatch):

- **softSat** — `x / √(1+x²)`. Gentler than softClip; close to identity at low levels.
- **tube** — Shifted-tanh asymmetric: `(fastTanh(x+0.5) − fastTanh(0.5)) / (1 + fastTanh(0.5))`.
  Normalised so negative rail = −1, positive peak ≈ +0.37. Generates even harmonics + DC.
- **linearFold** — Triangle wavefolder, period 4, identity in [−1,1]. Sharper creases than `sineFold`.
- **zeroSquare** — `fastTanh(8·x)`. Crossover-region timbre, near-square at high drive.
- **sineShaper** — `sin(π·x/2)`. Normalised fold; peak at x=±1, folds outside.
- **asym** — Piecewise polynomial: positive cubic clip, negative sqrt-knee. Even harmonics + DC.
- **stompBox** — Asymmetric diode-pedal: `1 − e^(−1.5·x)` pos / `−(1 − e^(3·x))` neg. Pedal grit.

**Tube bias constant lesson**: Tube uses Padé-consistent constants (`fastTanh(0.5) = 13.625/29.25 ≈ 0.46581` and
`1/(1 + fastTanh(0.5)) = 29.25/42.875 ≈ 0.68222`), NOT the real `tanh(0.5) ≈ 0.46211`. Necessary so `tube(0) = 0`
exactly when the underlying shape is the Padé approximation. If `fastTanh`'s Padé form ever changes, these
constants must be recomputed (or `tube(0) ≠ 0` will fail a bounds test).

Parser accepts canonical lowercase names + underscore/short aliases (e.g. `linearfold`, `linear_fold`, `lfold`;
`zerosquare`, `zero_square`, `square`; `stompbox`, `stomp_box`, `stomp`). See
`DistortionShape.parseDistortionShape()`.

SVG visualisers in `sprudel/.../SprudelDistortEditorTool.kt` + `SprudelDistortShapeEditorTool.kt` mirror the
shapes with the *real* `tanh` (their local helper), so they use real-tanh constants for tube — different
numeric constants from the engine, same logical behaviour (visualisation reference, not audio).

## Ignitor Variants — Dispatch on `soundIndex` (2026-05-22)

New `IgnitorDsl.Variants(children: List<IgnitorDsl>)` sealed-interface node
(`audio_bridge/.../IgnitorDsl.kt`). Lets a single ignitor expose multiple
flavours selectable per note — same mechanism that picks sample-bank variants
(`bd:0`, `bd:1`), now extended to ignitor graphs.

- `cache.soundIndex` rides on `IgnitorBuildCache` (per-call invariant, not
  threaded through every recursive call).
- `toExciter(oscParams, soundIndex = 0)` — new optional param; default 0.
- `buildIgnitor` dispatches `Variants` in the leaf prologue (no cache entry on
  the Variants node itself): `children[cache.soundIndex.mod(children.size)]`.
  Kotlin stdlib `Int.mod` = floor-mod (negative wraps from the end).
- `IgnitorRegistry.createExciter` passes `data.soundIndex ?: 0` into
  `toExciter`. Missing soundIndex → variant 0 (intuitive default).
- `maxReleaseSec()` takes max across all children (conservative — voice may
  live longer than the picked variant needs, silent tail harmless).
- `buildRaw` errors loudly if Variants ever leaks past `buildIgnitor`.

Nested Variants all dispatch on the same `soundIndex` (single switching axis).
Empty children list throws at build time. Shared post-effects like
`Osc.variants(a, b).lowpass(400)` wrap whichever variant got picked.

Archive: `docs/agent-tasks-archive/2026-05/20260522-ignitor-variants.md`.

## Sprudel-side scale + variant split (2026-05-25)

Follow-on to the Variants work. With variants in place, `soundIndex` started
serving two unrelated jobs: scale-step input to `.scale()` AND variant pick to
`Variants` / sample banks. Resolved by splitting on the sprudel side without
touching the bridge.

- `SprudelVoiceData.resolveNote()` now parses `value` lazily at consumption
  time: `step:variant:gain` form when `value` is a String. Step becomes the
  scale-step input; variant overrides `soundIndex`; gain overrides `gain`.
  Parsed null parts never overwrite existing voice fields.
- Priority inverted: `n = newIndex ?: value?.asInt ?: soundIndex`. Value
  wins; soundIndex is only used as the step input when nothing else provides
  one (preserves the strudel-port `n("0").scale(...)` path).
- Scale branch in `resolveNote()` clears `soundIndex` only when soundIndex
  itself was the step source. When `value` provided the step, soundIndex
  survives — it's a variant override now, not a consumed step.
- `applyNote` empty branch (`.note()` reinterpret) gained an "already
  resolved" guard: skip `resolveNote` when `note != null && value == null`
  to prevent double-resolution wiping the variant.
- `nMutation`, `voiceValueModifier`, `noteMutation`, `soundMutation`, and the
  audio_bridge `VoiceData.soundIndex: Int?` type all unchanged. JsCompat
  baseline preserved.

Canonical scale + variant syntax: `seq("0 2 4 4:1 5:1").scale("c4:minor")`
(notes c/d/e/e/f, last two on variant 1).

## Lessons Learned

- `KlangTime.internalMsNow()` is monotonic, NOT wall-clock — use only for relative timing.
- JS target requires ES2015 classes for AudioWorkletProcessor inheritance (KMP default is ES5 — override needed).
- `MonoSamplePcm` is always mono; stereo is handled at the `Cylinders` pan/mix level.
- `FilterDefs.addOrReplace()` is additive — calling it twice with the same filter type replaces, not duplicates.
- `VoiceData` fields are nullable with defaults — omitting a field means "use engine default".
- `duckCylinder` in `VoiceData` sets the cylinder ID to duck when a voice plays; ducking is cross-cylinder sidechain.
- `VoiceData.soundIndex: Int?` is the universal variant channel — consumed by `SampleRequest` for sample-bank picking
  AND by `IgnitorRegistry.createExciter` → `IgnitorDsl.Variants` dispatch.
