# Engine tidy-up: the Katalyst leftovers and a backend ready for a Zig port

Status: **V1, in progress (maintainer, 2026-10-07); steps 1 to 9 done (1 dead code, with its deferred `VoiceFactory` items; 2 the oversampler closure; 3 the RNG defaults; 4 the `KatalystSlots` helpers and the settings types; 5 constants and names; 6 the small shared helpers, the per-block copies and the audio `utils/` home; 7 the per-block iterators, the diagnostics closure and the solo ramp's curve; 8 one playback per scheduler; 9 the engine's end of life as one phase, and one render path), see below.** Step 3 of the engine order in [`_v1-scope.md`](_v1-scope.md), after
the voice lifecycle (`../tasks-archive/2026-10/20261007-voice-lifecycle-state-machine.md`, done) and the pitch pipeline (`pitch-pipeline-into-the-tree.md`).
One exception runs first: the crash below.

## Why

The maintainer, 2026-10-07: "I want to get the engine into a nice, clean code state. At some point we want to port
everything to Zig, which means the backend codebase must be as tidy as can be." And: revisiting the engine surfaces
subtle bugs (the 16-bit browser output was one), so each pass is also quality control.

## The audit

[`../audio-audit/2026-10-07-engine-tidy-audit.md`](../audio-audit/2026-10-07-engine-tidy-audit.md), read-only, at
`4481ea25`. Section A: the Katalyst DSL leftovers (7 of 10 named items still open, 13 unnamed code leftovers).
Section B: tidiness with the Zig port in mind (dead code, duplicated laws, names, per-block allocations and closures,
the wire). Section C: `../plans/effect-state-machines.md` verified DONE as written; the flags kept on purpose are
named with their decisions (state machines only where they pay off, maintainer: "Augenmaß"); one clear finding,
`PlaybackEngine`'s end of life spread over five places. Section D: a cleanup order, behaviour-neutral steps first,
each one reviewable commit. Section E: 11 decisions for the maintainer.

## First, a bug

**Done 2026-10-07.** The engine's `Variants.pick` plays an empty `Variants` as silence (`Constant(0)`), so the wire is
safe whatever frontend sends it; the doors keep accepting zero children. Reproduced first: the throw escaped
`VoiceScheduler.scheduleVoice` / `promoteScheduled` through `VoiceFactory.makeVoice`, nothing caught it. Rows:
`EmptyVariantsDoorRenderSpec` (sprudel, both doors through `KlangOfflineRenderer`), the engine row in
`IgnitorDslRuntimeTest`. The sweep found one more user input of the same class: `shimmer(..., [])` read index 0 of an
empty array per block; it now spawns no grains (`ShimmerSchedulerSpec`). Review round 1 added, in the same change:
the shimmer's two wrap loops hung the audio thread on a huge or infinite rate (`[0, 7, 1200]` is `2^100`) and a NaN
pitch poisoned the voice, so the loops are a floor-mod and a non-finite rate reads as 1.0 (finite rates unclamped);
a unison count is capped (`coerceUnisonVoices`, `UNISON_MAX_VOICES = 64` beside `coercePasses`, non-finite is 0;
the largest authored count is 32, so every builtin sound is unchanged; guard `UnisonVoiceCapSpec`); the script
shimmer door raises its typed error (`KlangScriptTypeError`) for a pitch that is not a number, as on `wet`, `feedback`
and `tone`, instead of a cast error. Decided by the coordinator by default, for
the maintainer to confirm: the cap value 64. Report: `tmp/reviews/variants-empty-report.md`.

`Ignitor.variants()` with no children reaches `require(children.isNotEmpty())` in `IgnitorDslRuntime.kt` (the
`Variants.pick` helper) on the audio thread at note-on; nothing catches there. The KlangScript door accepts zero
arguments. Per `/code-style` §21 (coerce user input, never `require()` it): an empty `variants()` is silence.
S, with a row on both doors. Do it right after voice lifecycle step 5 lands (the audit item B4.7).

## Extract reusable helpers (maintainer, 2026-10-07)

A review dimension for every step of this task, across all `audio_*` modules (`audio_bridge`, `audio_be`,
`audio_fe`, `audio_jsworklet`): code blocks that are self-contained and could be reusable and testable helpers
are extracted into the module's `utils/` package (`io.peekandpoke.klang.<module>.utils`, the same name in every
module, `/code-style` §3; existing helpers such as `audio_be`'s `DspUtil.kt` and `js_helpers.kt` move there too), each with a spec of its exact behaviour. The model is `retainInOrder` (voice lifecycle step 5,
round 2): an `inline` extension that keeps order, allocates nothing on either platform, and is pinned by
`RetainInOrderSpec`. The audit's B2 items are the first candidates (`finiteOrZero`, the stereo add, the fade to
zero shared by the teardown and the cut, the one-pole time constant, the wrap helper, the drift-ramp prologue).
Rules: a helper earns its place by a second caller or by a behaviour worth pinning; hot-path helpers taking a lambda
are `inline` (no closure per call); no helper hides an allocation.

## The order

Audit section D, unchanged, steps 1 to 20. Steps that touch `Voice`, `VoiceScheduler` or `Cylinders` wait for
lifecycle step 5; the named-arguments pass (`code-style-named-args-pass.md`) goes before or after them, never in
parallel. Bit-identity (the 18-song corpus) is the proof for every behaviour-neutral step.

## Step 1, dead code: done 2026-10-07 (uncommitted, awaiting review and the corpus render)

Each item re-verified against the working tree (not the audit's `4481ea25`) over every module, the tests, the
docs, the stdlib registrations and the KSP output. Report: `tmp/reviews/tidy-step1-report.md`.

**Deleted:** B1.1 `Ignitor.formant` / `FormantIgnitor` / `FormantBand` (not a script door: the Ignitor DSL has no
formant node; the vowel is the Katalyst stage); B1.2 `Double.polyBlep`; B1.3 `KlangAudioDebug.kt`; B1.4
`PlaybackEngineDispatcher.masterLatencyMs` with `MasterStage.latencyMs` and `Compressor.latencyMs` (the FE adds
`HOUSE_LIMITER_LOOKAHEAD_SECONDS`; `latencyFrames` and its spec row stay); B1.6 `BlockContext.cylinders`; A2.8
`Compressor.makeupGainDb` and its block multiplier (it was always exactly 1.0, so dropping `* makeupLinear` is
bit-identical); from B1.8: `Compressor.LN10`, `KlangPlaybackSignal.Custom`, four unused browser externals
(`AudioContext.currentTime`, `getOutputTimestamp`, `AudioTimestamp`, `AnalyserNode.frequencyBinCount`),
`AudioAnalyzer.fftSize`, the `ParallelMixFilter` import, the unreachable `?: KatalystStageDsl.Duck()`, the doubled
`Cylinders` KDoc, `MAX_CYLINDERS` set to the 255 it was clamped to (effective value unchanged), the unused
destructured `cylinderId`. Stale KDocs fixed: A2.2 (`Cylinder`), A2.6 (`KatalystBodyEffect`,
`KatalystFormantEffect`, `KatalystRegistry`, `KatalystContext`).

**Kept, with the reason:**
- B1.8 `ShapingFuncs.nativeTanh` and `softCapTo`: `/code-style` §14 accepts unused members on that API object, and
  `DelayLine`'s KDoc names `softCapTo` as the law its feedback path inlines.
- B1.9 counters read by specs (`KatalystGainEffect.ramps`, `KatalystEqEffect.installs` / `lastInstalledBank`,
  `KatalystChain.resolveCount`, `KatalystRegistry.namesNormalized`, `IgnitorRegistry.optimizerFailures`,
  `EqIgnitor.staticZeroDbSkips` / `staticConfigureSkips`, `WarmupRunner.readyWhileDirty`, the warehouse
  `doubleReturns` / `housekeptUnits` / `housekeptFrames`, `ScratchBuffers.doubleHighWater`): each is a spec seam,
  which the audit's own rule keeps.
- B1.9 `WarehouseStats.reverbFailures` / `reverbDropped`: a wire change, and the ring twins are shown in
  `PlayerWarehouseStats`, so the likelier fix is to show these too. For the coordinator.
- B1.9 `CycleCompleted.atTimeSec` and the `VoiceData.tags` KDoc: not counters, out of this step.

**Deferred until the solo fix landed, done 2026-10-07 (uncommitted, awaiting review and the corpus render):** B1.5
`VoiceFactory.sampleRateDouble` and the dead `VoiceFactory.cylinders` are gone from the constructor, the
`VoiceScheduler.kt` call and the 18 `VoiceFactory(...)` constructions in 16 spec files. B1.7: `isOsci` now asks
`playbackCtx.ignitorRegistry`, the registry the build already used, and `VoiceFactory.ignitorRegistry` is gone (every
spec rig handed both places the same registry, so no rig loses a case). The stale `Voice.kt` "Frame counters use Int"
comment and the `VoiceData.kt:14` TODO are deleted. Done with the named-arguments pass, as its own commit (the patch
`tmp/reviews/named-args-taskB.patch`; report `tmp/reviews/named-args-report.md`).

**Stale mentions outside the engine, for the maintainer:** `/code-style` §9 says saw, square and pulse "must use
PolyBLEP", `CREDITS.MD` credits PolyBLEP, and `klang-music-writing/ref/ignitor-reference.md` calls the saw
"anti-aliased (PolyBLEP)"; the oscillators use finite-slope flanks instead, and nothing used `polyBlep` before this
step either. Three `IgnitorsTest` row names say "PolyBLEP" too, and so do the in-app Credits page (`CreditsPage.kt:337`) and the
Zawtooth KDoc (`IgnitorDsl.kt:446`).

## Step 2, the ShapeIgnitor closure (B4.1): done 2026-10-07 (uncommitted, awaiting review and the corpus render)

`Oversampler.process(buffer, offset, length, scratch, transformBlock)` is gone. In its place are two halves:
`upsample(source, offset, length, work): Int` and `decimate(work, target, offset, length)`. The caller holds
the oversampled scratch lease (`ScratchBuffers.use`, inline) across both and runs its shaping loop between them.
That leaves no function-typed parameter on the audio path. Both production callers use the halves: `ShapeIgnitor`
had built a capturing closure every block, and `DistortionCore` loses its field lambda and the `blockDrive` side
channel. The copy back into the caller's buffer is a plain loop now. It was `copyInto`, which on JS allocated a
`subarray` view every block (`audio/ref/performance.md`). Stage 0 still does nothing in either half. Report:
`tmp/reviews/tidy-step2-report.md`.

- **Callers, every module:** only those two in production. The Katalyst chain has no distort or crush stage, and
  crush (`CrushCore`, `CrushIgnitor`) never oversampled. The specs (`OversamplerSpec`,
  `OversamplerDecimatorParitySpec`, `DoorDistortionLawSpec`, `StripLawCoresSpec`, `GuitarClickHuntTest`) go
  through one test helper, `Oversampler.roundTrip` (`audio_be/src/commonTest/kotlin/_oversampler_test_helpers.kt`):
  the halves composed the way the callers compose them, `inline`.
- **No `utils/` helper:** nothing self-contained fell out with a second caller. The copy loop is the candidate (see
  below), but it has one caller in this step.
- **Proof:** a probe compared HEAD's classes (copied from `git show HEAD:`) with the new code. It covered the DSL
  runtime's `Shape`, `Distort` and `Crush`-after-either nodes over every shape, factors 2, 3, 4, 8, 16 and 32,
  constant and LFO amounts, and 60 ragged windows. A second part ran the nodes over a hostile source (NaN,
  infinities, 1e300, a denormal, -0.0) at stages 1 to 5 and five amounts, NaN among them. 2,400 configurations and
  9.8 million samples matched bit for bit. In the compiled JS (`klang-engine-audio_be.js`, test build),
  `ShapeIgnitor.generate` and `DistortionCore.process` now call `upsample_*` and `decimate_*` with the loop
  inlined. `ShapeIgnitor$generate$lambda`, `DistortionCore$oversampledTransform$lambda` and the `arrayCopy` in
  the round trip are gone.
- **Rows:** `OversamplerDecimatorParitySpec` pins both production nodes, oversampled (stages 1 to 4, every shape,
  the fused node at a drive and at unity), against the independent ring oracle, bit for bit. The windows are
  ragged and start mid-block, and the source carries NaN. `OversamplerSpec` pins the stage-0 no-op of both
  halves. Seven mutants each went red on the intended row. The three mutants in the callers (the loop bound, the
  decimate offset, the distort NaN guard) went red only on the new rows.
- **Found, not done:** the other `copyInto` sites on per-block paths, which make the same JS view. The
  inventory and the decision (step 6) live in "Found during tidy-up step 2" below.
- **The halves can be combined wrongly** (an offset or length that differs between the two calls, a missing
  `decimate`): the contract is in the `upsample` KDoc and pinned by the parity rows. If a third caller ever
  appears, promote the test helper `Oversampler.roundTrip` (`_oversampler_test_helpers.kt`) to the main source
  set as the one wrapper, `inline`.

## Step 3, the RNG defaults (B4.14): done 2026-10-07 (uncommitted, awaiting review and the corpus render)

The 15 `random: Random = Random` / `rng: Random = Random` defaults are gone (the audit's "about 16"): `IgniteContext`,
`buildExciter`, `toExciter`, `IgnitorBuildCache`, `IgnitorRegistry.createExciter`, `AnalogDrift`, `SampleIgnitor`, and
the eight unison and string factories in `Ignitors.kt`. A forgotten stream is now a compile error. Report:
`tmp/reviews/tidy-steps3-5-report.md`.

- **Production callers:** every voice path already passed its voice's stream (`VoiceFactory`, `IgnitorRegistry`, the
  DSL runtime). ONE caller did not: `KatalystSlots.coerce` built an orbit knob's graph with `buildExciter()` bare, so
  its draws came off the process-wide `Random`. Not an audible bug: the nodes that draw at build (noise, the unison
  stacks, a humanized filter) answer null at control rate, so the knob takes its fallback whatever was drawn, and no
  production code reads the global stream. It now builds with a fixed-seed stream, `Random(KNOB_BUILD_SEED)`
  (0), per coerced knob, so nothing hidden is left for a second backend to reproduce.
- **Specs** (59 files) pass one seeded stream per file (`private val testRandom = Random(0x5EED)`), not a fresh
  `Random(0)` per call: several specs average over repeated builds and need successive builds to draw differently,
  as they did from the global stream. A fresh `Random(0)` per call collapsed `PhasePoolDslSeamSpec`'s 60-note means
  to one note and turned it red; the per-file stream keeps that meaning and makes every run repeatable. The
  benchmark's two `IgniteContext`s take `Random(0)`.
- **Bit-identity:** the production change is the knob build's stream, which no answer reads.

## Step 4, the `KatalystSlots` helpers and the settings types (B2.11, A1.7): done 2026-10-07 (uncommitted, awaiting review and the corpus render)

- **Folded:** `bodyDef`, `vowelDef`, `compressorSettings`, `duckSettings` and `finiteOrNull` left `KatalystSlots`;
  each rule lives in its one caller, the writer (`KatalystSlotWriters.kt`). `Voice.Compressor.fromParams` and its
  `Double?` hop went with them: the compressor writer reads the five slots as `Double`s ("any finite = on, each
  non-finite = its constant"). `VoiceCompressorSpec` (which tested `fromParams`) is replaced by a
  `KatalystSlotResolverSpec` row, one sub-case per slot.
- **One NaN rule per knob:** body and vowel substituted their unset `wet` / `floor` twice, in the writer and again
  in the effect's `configure`; the reverb's lowpass likewise. The writer's copy is the one dropped, the stage's
  kept, because the stage is where the compare and the cache are (`/review-loop`: compare and store SUBSTITUTED
  values), and its born-from-a-bug rows (`KatalystBodyEffectSpec`, `KatalystFormantEffectSpec`, root
  `KatalystBodyNonFiniteWetSpec`) pin it there. The writers now hand those slots through raw (the reverb writer
  boxes its `Double?` once per resolve, never per block). Same numbers reach the DSP: the effect substitutes the
  same constants the writer did.
- **Not folded, the phaser:** the audit's "phaser depth three times" is not one rule written thrice. The writer
  turns an unset `wet` into `PHASER_WET` (off); `Phaser.depth`'s setter DROPS a non-finite value and keeps the old
  one; the effect's gate is a threshold. Only the writer substitutes, so there is no second guard to drop.
- **Moved:** `Voice.Compressor` and `Voice.Ducking` are `CompressorSettings` and `DuckSettings` in
  `cylinders/katalyst/` (their own files). The type names had to change: the katalyst package already uses
  `effects.Compressor` and `effects.Ducking` next to them (`KatalystCompressorEffect`, `KatalystDuckEffect`), so the
  old names would clash. The FIELD names stay (`cylinderId`, `attackSeconds`) until the duck rename (A2.7).
- **Rows:** the four helper-level resolver rows now go through a declared chain and read what the stage installed.
  Seven mutants (the body and vowel stage guards, the body floor and vowel mix pass-through, the compressor gate
  and one substitution, the duck attack) each went red on the intended row; the body guard mutant also turns
  root `KatalystBodyNonFiniteWetSpec` red, as its rewritten KDoc says.

## Step 5, constants and names (A2.4, B3.9, B2.6): done 2026-10-08 (uncommitted, awaiting review and the corpus render)

- **`SendEffectDefaults.kt` is gone:** delay and reverb live in `BusEffectDefaults.kt`, whose header now names the
  readers that exist (the stage DSL and script slots, the sprudel doors and editor tools, `KatalystChainBuilder`,
  the slot writers and stages for a raw `katp` write, the shared DSP). `VoiceFactory`, `Voice.Compressor.fromParams`,
  `Cylinder` and `SprudelVoiceData.toVoiceData` are no longer named: none reads these constants. "Send" left the
  delay and reverb KDocs.
- **Renamed:** `VCA_OFF_TEARDOWN_FADE_SECONDS` is `TEARDOWN_FADE_SECONDS` (`CUT_FADE_SECONDS`'s KDoc links the new
  name); `sendStageRuns` is `stageAskedFor` (it decides whether anybody asked for the delay or reverb stage);
  `KatalystChain.statics` is `writers`. The builder's "until a voice asks for one" comments say what gates the
  rent now.
- **One silence floor:** `SILENCE_FLOOR` (1e-5, -100 dBFS) in `VoiceCullingDefaults.kt` replaces
  `ORBIT_SILENCE_FLOOR`, `TailCeiling.SILENCE`, `Reverb.TAIL_THRESHOLD` and `DelayLine`'s two `0.00001` literals
  (the same double). The master's `TAIL_SILENCE_THRESHOLD` (1e-4) is untouched: decision D9.
- **Grepped** for every old name over the code, docs, skills and refs; the dated records (`tasks-archive/`,
  `memory-history.md`, the audit, the blog) keep their words.
- **Left:** the spec `VcaOffTeardownSpec` keeps its file name.

## Step 6, small shared helpers, the per-block copies and the audio `utils/` home: done 2026-10-08 (uncommitted, awaiting review and the corpus render)

Behaviour-neutral. Report, with the file list of each reviewable commit (the moves, the helpers, the copy sites):
`tmp/reviews/tidy-step6-report.md`. Every NEW helper below is `inline`, allocates nothing, and has a spec in
`audio_be/src/commonTest/kotlin/` (`utils/`, and `StereoBufferAddFromSpec` for the one member). `utils/` imports
nothing from the rest of `audio_be`, in the main sources and in its specs (review rounds 1 and 2): it is a leaf a
port can take on its own.

- **The audio `utils/` home** (`/code-style` §3). `audio_be`'s `DspUtil.kt` is split by content into
  `utils/math_constants.kt` (`TWO_PI`, `HALF_PI`), `utils/fast_math.kt` (`fastSin`, `fastExp2`, `fastExp`),
  `utils/numerical_safety.kt` (`DENORMAL_THRESHOLD`, `nanGuard`, `flushState`, `SAFE_MIN`, `SAFE_MAX`, `safeDiv`,
  `safeOut`) and `utils/phase_wrap.kt` (`wrapPhase`, `wrapToUnitCycle`, `smallNumFastMod`), package
  `io.peekandpoke.klang.audio_be.utils`; `AudioSample` is written `Double` there. `wrapPhase` lands in
  `[0, period)` only up to rounding at both ends: `period` itself (a tiny negative plus `period`, or the modulo
  branch) and, from the modulo branch, a tiny negative (at most one ulp of the input in a sweep, not a proof);
  `smallNumFastMod`, within its
  one-overshoot precondition, lands in `[0, period]`. Unchanged; the KDoc says by how much, and the spec pins
  each edge. Their moved KDoc lost its dashes
  and `wrapPhase` got its braces. `applySemitoneDetuneToFrequency` is deleted: it was `common.math.semitones()`
  times the frequency, so its two callers and the three inline `2.0.pow(x / 12.0)` copies (`Ignitor.kt` 2,
  `IgnitorEffects.kt` 1) call `semitones()`, the same expression. `SvfCoeffSweep`'s `2.0.pow(d / 12.0 * x)`
  rounds in another order and stays.
  `waveTrapezoid` is the oscillators' waveform, a feature helper by the same rule, so it sits next to its one
  state class in `ignitor/WaveVoiceState.kt`. `jsMain`'s `js_helpers.kt` is `utils/js_objects.kt`. `audio_fe`'s
  `utils/utils.kt` is `utils/url_checks.kt`; its two enum helpers (`safeEnumOf`, `safeEnumOrNull`) had no caller
  in any module and are deleted. `_pcm16_edge.kt` stays at the root: `writePcm16` takes a `StereoBuffer`, and the
  file is the output edge (the clip's partner), a feature. Specs: `DspUtilSpec` is `utils/NumericalSafetySpec`,
  `FastSinSpec`, `FastExpSpec` and `FastExp2Spec` moved along, their integration rows next to the features they
  drive (`ignitor/SineOscillatorFastSinSpec`, `AdsrExpShapeFastExpSpec`, `ignitor/PitchEnvelopeModFastExp2Spec`,
  `voices/strip/pitch/PitchEnvelopeRendererFastExp2Spec`); new `PhaseWrapSpec`, `JsObjectsSpec` (`jsTest`),
  and `audio_fe`'s `UrlChecksSpec`; `NumericalSafetySpec` gained `safeDiv` and `safeOut` rows.
- **New helpers:**
  - `finiteOrZero` (B2.5, `numerical_safety.kt`): the 15 `if (abs(x) <= Double.MAX_VALUE) x else 0.0` taps
    (`Crossfade` 10, `TailRelease` 2, `Reverb` 2, `Compressor` 1) and the 9 `if (x.isFinite()) x else 0.0`
    sites with the same truth table (`Compressor` ring write and detector, `PhaserCore`, the three dB guards in
    `LowPassHighPassFilters`, two partial gains in `Ignitors.kt`).
  - `StereoBuffer.addFrom` (B2.4, a member next to `clear()` and `fill()`, not in `utils/`): the orbits into the fusion mix (`Cylinders`), the engine's
    own bus into the output (`PlaybackEngine`), the draining chain's ring-out (`ChainSwap`; its one-line
    `addLeavingMix` wrapper is gone).
  - `fadeToZero` (B2.3, `fade_to_zero.kt`): the loop of the teardown fade and of the cut. Its window is
    `startIndex` / `endIndex`, the words of `copyRangeInto`.
  - `timeConstantCoeff` (B2.7, `time_constant.kt`): the compressor's attack, release and fast release, the
    ducker's release, the envelope de-click (`envDeclickCoeff` is gone).
  - `wrapPhaseFastOrSafe` (B2.16, `phase_wrap.kt`): the 12 `if (safeWrap) wrapPhase else smallNumFastMod` pairs
    (9 in `ignitor/`, 3 in the pitch strip).
  - `rampStep` (B2.16, `ramp_step.kt`): the drift ramp's per-frame step at 16 sites (9 single `AnalogDrift`
    walks, 7 `DriftLanes` lanes). The `beginBlock` / `advanceLane` call and the start read stay at each site: they
    set two locals, which a helper could only return by allocating.
- **Kept apart, with the edge rule that differs:** `nanGuard` (NaN only, an infinity passes) and `flushState`
  (also zeroes denormals) beside `finiteOrZero`; the "finite or a non-zero fallback" substitutions (`finiteOr` in
  `IgnitorEnvelopes`, the Katalyst writers and effects, `Compressor` and `Ducking`'s settings guards) are another
  law, not folded; `Ducking`'s 1 ms floor and non-finite fallback stay at its caller, only the law moved; the
  bilinear `onePoleLpfCoeff` and the shimmer's `exp(-2 pi f / sr)` take a frequency, not a time; the teardown fade
  and the cut each compute their own zero index and scale (`floor(endFrame) - 1` with a start held to the second
  half of the voice, against `ceil(fadeEnd) - 1`), only the loop is shared; the decimator's prefix and history
  loops in `Oversampler` were plain loops already (one an in-place shift) and stay loops; the "ramp written from
  the block end" idiom stays (the audit's call).
- **The per-block copies:** `copyRangeInto` (`utils/buffer_copy.kt`), a plain forward loop with `copyInto`'s
  parameter names, replaces the 19 sites listed below, `Compressor.kt`'s unreachable no-lookahead fallback (2,
  converted with the rest rather than dropped) and the oversampler's copy back. What it does not do that
  `copyInto` did: no range check (JVM throws on a bad index, JS reads NaN and drops the write) and no overlap
  handling (the spec pins the forward copy); no site copies within one array. `EqCore`'s window-guard KDoc says so.
  Not converted, as the list says: `KatalystEqEffect` (control rate), `PhasePool` (once per note), the `copyOf`
  growth and build sites, `SampleStore` (an upload message).
- **Proof:** every new spec row mutation-checked (the report lists the mutants; two rows that a first mutant could not see
  were strengthened and re-checked; review round 1 added raw-bits rows that pin `rampStep`'s and `fadeToZero`'s
  operation order against a reassociated mutant); the compiled
  `klang-engine-audio_be.js` (test build) shows the helpers inlined into their callers with no `subarray`,
  `arrayCopy` or new object in the converted loops; the suites in the report. Bit-identity: the corpus render is
  the coordinator's.

## Step 7, the per-block iterators, the diagnostics closure, the solo ramp (B4.3, B4.4, B4.17): done 2026-10-08 (uncommitted, awaiting review and the corpus render)

Behaviour-neutral. Report: `tmp/reviews/tidy-steps7-9-report.md`. The `VoiceFactory` items (B1.5 to B1.7) and the
solo tracker's allocation (B4.2) were done before this step.

- **Iterators (B4.3).** Every per-block walk named by the audit is an index loop now, and so are the scheduler's
  other walks over its active list (cleanup, hard kill, note-off, the cut, the realtime solo, the batch schedule):
  - `Voice`: the stages before the send are an `Array`, built once per voice.
  - `Cylinders`: the map is gone. The rented cylinders sit in a list in RENT order, which is the mix's summation
    order and the round-robin cleanup's (a cylinder is only appended; `releaseAll` clears all at once), and an array
    finds one by its folded id. The fold keeps a negative orbit's sign (`-3 % 255` is `-3`), so the array spans
    `-(max - 1)` to `max - 1`. The duck still finds its sidechain by the RAW id: an id outside that range finds
    nothing, as the map never held it. `cylinders` is a `List`; `cylindersIds` (specs only) builds its set on read.
  - `PlaybackEngineDispatcher`: the engines render from a list kept in step with the map, in the map's order.
- **The diagnostics closure (B4.4).** The local `fun count` is a private method summing into three fields; the
  message's own list of cylinder states stays (it crosses to the frontend). Every 20 ms, not per block.
- **The solo ramp (B4.17).** `easeInOutCubic` (`utils/ease_in_out_cubic.kt`) is the library's `Ease.InOut.cubic`,
  the same expression bit for bit (its factor `2^(3 - 1)` is the literal 4); `SoloRamp` (`voices/`) is the old
  `ValueRamp` law with the curve inlined, no call through an interface on the render path. The `common` module's
  `ValueRamp` had no other caller anywhere and is deleted.
- **Proof.** In the compiled `klang-engine-audio_be.js` (test build) none of the converted functions creates an
  iterator or a closure. New rows, each mutation-checked: `CylindersOrderSpec` (the mix and cleanup order, the
  fold and its sign, the duck's raw id at both ends of the range, `releaseAll`'s return order),
  `EaseInOutCubicSpec` (bit parity with the library), `SoloRampSpec` (the law, against the library curve),
  `PlaybackEngineDispatcherOrderSpec` (render order read through the warehouse shelf, exact diagnostics with a
  detached engine, a disposed engine leaving the render set) and a realtime-solo row in
  `VoiceSchedulerSoloCutSpec` with the soloed key first in the list. Mutants of the converted scheduler and voice
  loops went red on existing rows.

## Step 8, one playback per scheduler (B3.3): done 2026-10-08 (uncommitted, awaiting review and the corpus render)

Behaviour-neutral for everything production builds. Report: `tmp/reviews/tidy-steps7-9-report.md`.

- **The scheduler serves one playback.** `playbackContexts` is one nullable `playback`; `ActiveVoice.playbackId` is
  gone; `cleanup`, `cleanupHard`, `clearScheduled`, `replaceVoices`, `stopRealtimeVoice` and `droppedVoiceCount`
  take no id, and `droppedVoicesTotal` folded into `droppedVoiceCount`. The class KDoc states the contract: the
  dispatcher routes every command by id to its engine, the offline renderer has one engine and its caller one id.
- **Kept exactly:** the context is made by the playback's first voice, timeline or realtime, from THAT voice's
  `playbackId` (it seeds the voices' random streams, `PlaybackCtx.coreRandom`, and the phase pools), and is kept
  for every later voice; `cleanup` drops it, so a resume makes a fresh one with a fresh epoch and a fresh dropped
  count. `startRealtimeVoice` keeps its id parameter: a realtime voice carries none, and the context may be made from
  it. `clearScheduled` is `scheduled.clear()` (every entry matched the old filter); the replace cutoff reads the one
  epoch. The callers (`PlaybackEngineDispatcher`, eleven spec sites) pass no id; the worklet and the JVM backend
  reach the scheduler only through the dispatcher, the offline renderer only through `scheduleVoice` and
  `addSample`, which did not change.
- **The specs that hosted several playbacks on one scheduler,** adapted to one playback, keeping what they pin:
  `VoiceSchedulerRemovalSpec`'s hard-kill row now kills every voice and checks they leave before the next block;
  the between-blocks sweep with survivors, which that row was there to see, has its own row through a cut that finds
  a voice not yet rendered (`Done` at once, swept in place). `RealtimeVoiceSpec`'s one-scheduler note-off row
  matches by `liveId` within the playback.
- **New rows,** each mutation-checked: `VoiceSchedulerPlaybackSpec` (the context and its epoch across timeline and
  realtime voices, `cleanup` and a resume, `clearScheduled`, the replace cutoff on an epoch that is not 0). The
  dispatcher's `ClearScheduled` row could not fail (it rendered one block of a voice 10 s ahead); it now renders past
  the start, with a positive control.

## Step 9, the engine's end of life as one phase, one render path (C3.1, A2.11): done 2026-10-08 (uncommitted, awaiting review and the corpus render)

Behaviour-neutral, the dispatcher's disposal order included. Report: `tmp/reviews/tidy-steps7-9-report.md`.

- **One phase.** `PlaybackEngine.Phase` is a sealed class of five data objects, `Playing`, `Stopped`, `Releasing`,
  `Released`, `Disposed` (the state-shape rule of 2026-10-07: no state carries data of its own), read through
  exhaustive `when`s. It replaces `stopped`, `isReleasing` and `released`; the class KDoc carries the transition
  table (states times `renderInto`, `stop`, `resume`, `dispose`), the hold and idleness. `Disposed` is new: the
  table has an end, and a disposed engine answers idle, ignores a stop and a resume.
- **Not in a phase, with the reason:** `quietBlocks` stays on the engine. The audit read it as Stopped-only data;
  it is counted in every phase, `Playing` included, and a stop after a long silence holds only the rest of the 20 s.
  Moving it into `Stopped` would change that. The `TailRelease` is a resource created with the engine.
- **The dispatcher keeps no lifecycle.** The `draining` set and the `detached` list are gone. It asks the phase
  whether a playback scheduled again resumes (`Stopped`) or is detached (`Releasing`, `Released`), and whether a
  stopped engine may go (`isIdle`). What it still keeps are the two ORDERS the output depends on: `rendering`, the
  render (summation) order, attached engines in creation order then detached ones in detach order; and `ending`,
  the disposal order, detached engines newest first, then stopped ones in stop order, exactly the old sweep's
  order. Disposal returns units to the warehouse's last-in, first-out shelves, and which unit a later rent gets
  (a clean or a dirty ring, of which size) depends on that order, so it is kept, not simplified to render order.
  To remove a disposed engine from the map the sweep needs its id, so `PlaybackEngine` carries its `playbackId`
  (`KlangAudioRenderer`'s one engine: `ENGINE_PLAYBACK_ID`, read by nothing offline; the scheduler's context still
  takes its id from the voices). A stop for an unknown id no longer parks the id in a set for one block (it was a
  no-op there).
- **One render path (A2.11).** `renderInto` has one flow: straight into the shared mix while neither a master nor a
  release is in play (the orbits sum into the target, the order the audit warned must stay), otherwise through the
  engine's bus, the master on it when active, joined whole or under the release. `markMasterBusRendered` and
  `renderReleased` are folded in; the master's "has rendered" is still told at the end of the call. `isActive` is
  read once, before the orbits render, which cannot change it.
- **Rows** (`PlaybackEnginePhaseSpec`, each mutation-checked): stop and a second stop, resume, a playing engine
  never released, the endless tail through Stopped, Releasing and Released to Disposed (block by block), finite
  tails ringing out, the quiet stop, the detach (a stop and a resume while releasing change nothing), `cleanupHard`,
  `Disposed` as the end, the disposal order of stopped engines and of detached ones (read through the warehouse
  shelf), and the straight sum into the shared mix (bit for bit, with a positive control that a bus would differ).
  `PlaybackEngineDispatcherOrderSpec`'s detach row now has a second attached engine created before the detach, which
  pins where a fresh engine renders. The release specs ask `releaseStarted` (a test helper over the phase) where they
  asked `isReleasing`.

## Decisions for the maintainer

Audit section E, D1 to D11, and the judgement calls C4.1 and C4.2. The ones that change the most:

- **D1** "cylinder" or "orbit" (one word per concept; the wire field `VoiceData.cylinder`).
- **D3** which state-machine shape the next lifecycle copies: inner classes (the effects) or an enum with `when`
  (the voice). The enum maps more directly to Zig.
- **D5** bus knobs typed as full Ignitor expressions (a native backend would need the Ignitor builder to read a
  reverb's `wet`).
- **D7** own the RNG (reproduce `XorWowRandom` in a project class), the precondition for a bit-identical port.

## Decided (maintainer, 2026-10-07)

- **D1, the word:** "cylinder" is the engine's and the user's word. `orbit()` stays only as an alias in sprudel, to
  honour its Strudel origin. Today sprudel has it the other way round (`orbit` is the object, `cylinder` the
  alias), so the rename is: sprudel's canonical door becomes `cylinder` (aliases `orbit`, `o`), and the docs, KDoc,
  tutorials, UI text and the Katalyst vocabulary say cylinder. L; plan it as its own step.
- **D3, the state-machine shape:** "as it fits". A state that carries data only it may see is a class; a state
  without data is a `data object`. Classes are the usual case, because they extend without a rewrite. Recorded in
  `../plans/effect-state-machines.md`. Consequence: the voice's `State` enum becomes a sealed type in a second,
  bit-identical round (`../tasks-archive/2026-10/20261007-voice-lifecycle-state-machine.md`, step 5b, done).
- **D7, owning the RNG:** a prerequisite for the Zig port, not needed now. Deferred to the port's preparation.
- **D5, bus knobs typed as Ignitor expressions:** KEPT as they are. The maintainer: "the Zig side will in any case
  need to understand this data model and the contract ... I would not bend our implementation on this side just
  because another backend has things to solve to use the inputs. The duty is on the other side, not here."
  A general rule for the port: the Kotlin engine defines the contract; a second backend adapts to it.


## Found during voice lifecycle step 5

- **Phase draws depend on the render order of the active list.** A unison oscillator takes its start phases from
  the orbit's phase pool on the voice's first rendered block (`Ignitors.kt`, the `pool.next(...)` take in the
  super-oscillator's first generate). The pool is shared per orbit and voice count, so which entry a note gets
  depends on the order the voices render in, and every change to that order (a removal law, a voice leaving
  earlier) changes which phases notes get. Retiring the zombie and making removal order-keeping (lifecycle step 5,
  2026-10-07) changed several songs this way, audibly in places (reviewer B, `tmp/reviews/vl5-r1-B.md`: Der
  Schmetterling 0.54 peak with identical settings). Accepted as a one-time change; not a correctness bug. The
  tidy-up: a note's phase take should not depend on list order (for example drawn at promotion, in onset order,
  or keyed by the voice), so a scheduling change never re-deals phases. Not fixed yet.

## Found during the empty-variants fix

- **`DelayLineMigrationSpec`'s "the timeout is the assertion" row may not be able to fail.** A kotest `timeout`
  cannot interrupt a busy loop on the JVM (found 2026-10-07: a shimmer row under the old wrap loops hung the run
  until a shell timeout killed it, its 30 s kotest timeout never fired). If that row's mutant spins, the suite
  hangs instead of going red. Not checked yet.

## Found during tidy-up step 2

- **`copyInto` on per-block paths makes a JS `subarray` view every block** (the house rule in
  `audio/ref/performance.md`; Kotlin/JS `arrayCopy` calls `source.subarray(...)` for typed arrays). This list is
  the one home of the inventory: every `copyInto`, `copyOf` and `copyOfRange` in `audio_be` (`commonMain`;
  `jsMain` and `jvmMain` have none), re-grepped after review round 1 of step 2. Step 2 turned the `Oversampler`'s
  into a plain loop. Converting the per-block sites together earns a `utils/` copy helper with a spec,
  bit-identical; it belongs with step 6 (small shared helpers). Coordinator decision 2026-10-07: do it there.
  **Done in step 6 (2026-10-08):** every per-block site below, and `Compressor.kt:405-406`, calls
  `copyRangeInto`; the line numbers are those of the inventory, before step 6.

  **Per block, steady state (19 sites in all with the fades below):**
  - `ignitor/MemoizingIgnitor.kt:123`: per voice, per shared node, every block. Probably the hottest.
  - `filters/ResonatorBank.kt:64` once per block, and `:71` once per BAND per block (body and vowel).
  - `filters/ParallelMixFilter.kt:54`.
  - `filters/EqCore.kt:273` (`captureInput`).
  - `cylinders/katalyst/KatalystCompressorEffect.kt:280-281`: every block while a lookahead compressor is Off.

  **Per block, only while a fade runs:**
  - `ChainSwap.kt:249-250` and `:275-276` (the duck's fade in and out across a chain swap).
  - `cylinders/katalyst/KatalystFilterSwap.kt:327-328` and `:346-347` (4 sites).
  - `cylinders/katalyst/KatalystCompressorEffect.kt:365-366`, and `:388-389` on the landing block only.

  **Not per block (leave them, or convert only for uniformity):**
  - `effects/Compressor.kt:405-406`: the no-lookahead fallback of `processLookahead`. Unreachable today: every
    production caller reaches `processLookahead` only with a lookahead (`Compressor.process` guards on
    `delayFrames > 0`, `KatalystCompressorEffect` on `latent`, which is `latencyFrames > 0`). Convert it with the
    rest, or drop the branch with a spec of the guard.
  - `cylinders/katalyst/KatalystEqEffect.kt:164, 170`: control rate (`configure`, on a knob change).
  - `ignitor/PhasePool.kt:468, 472`: once per note, at the phase draw of its first block.
  - `ignitor/Ignitors.kt:351-354` (`copyOf`): `Bank.resize` growth, which allocates anyway (audit B4.5).
  - `cylinders/katalyst/KatalystChain.kt:91` (`copyOf`): chain build. `SampleStore.kt:172`: a sample upload
    message.

## Found during tidy-up steps 7 to 9

- **`Cmd.ReplaceVoices` on a stopped engine** schedules its voices without resuming the engine (review round 1, older
  than steps 7 to 9, behaviour unchanged by them). Decide whether a replace means "resume" or is ignored after a stop.
- **`Voice` copies its stage list into an `Array`** once per voice start (`pipeline.toTypedArray()`), one small
  allocation on the audio thread per note, not per block. Left as is: the voice build allocates its states and
  context anyway; folding it into the builder churns every test rig. Revisit with step 10 (allocations to build time).
