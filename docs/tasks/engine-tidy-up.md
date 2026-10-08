# Engine tidy-up: the Katalyst leftovers and a backend ready for a Zig port

Status: **V1, in progress (maintainer, 2026-10-07); steps 1 to 11 done (1 dead code, with its deferred `VoiceFactory` items; 2 the oversampler closure; 3 the RNG defaults; 4 the `KatalystSlots` helpers and the settings types; 5 constants and names; 6 the small shared helpers, the per-block copies and the audio `utils/` home; 7 the per-block iterators, the diagnostics closure and the solo ramp's curve; 8 one playback per scheduler; 9 the engine's end of life as one phase, and one render path; 10 the first-block and voice-count allocations moved to the build; 11 the twins: the shaper core, the runtime arithmetic, the Karplus string core and the unison stacks), see below.** Step 3 of the engine order in [`_v1-scope.md`](_v1-scope.md), after
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
the maintainer to confirm: the cap value 64. Confirmed by the maintainer (2026-10-08): an empty `variants()` on a Katalyst bus
knob reads 0.0, not the knob's default: "empty variants produce silence, so the current solution is correct". Report: `tmp/reviews/variants-empty-report.md`.

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

## Step 1, dead code: done 2026-10-07

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

## Step 2, the ShapeIgnitor closure (B4.1): done 2026-10-07

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

## Step 3, the RNG defaults (B4.14): done 2026-10-07

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

## Step 4, the `KatalystSlots` helpers and the settings types (B2.11, A1.7): done 2026-10-07

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

## Step 5, constants and names (A2.4, B3.9, B2.6): done 2026-10-08

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

## Step 6, small shared helpers, the per-block copies and the audio `utils/` home: done 2026-10-08

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

## Step 7, the per-block iterators, the diagnostics closure, the solo ramp (B4.3, B4.4, B4.17): done 2026-10-08

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

## Step 8, one playback per scheduler (B3.3): done 2026-10-08

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

## Step 9, the engine's end of life as one phase, one render path (C3.1, A2.11): done 2026-10-08

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

## Step 10, first-block and voice-count allocations to build time (B4.5, B4.6): done 2026-10-08

Behaviour-neutral: every random draw stays where it was, at the voice's first block, in the same order. Report, with
the per-family patches and the numbers: `tmp/reviews/tidy-step10-report.md`. Seven families, one patch each.
Reviewed in two rounds (`tmp/reviews/tidy10-r1-A.md`, `tidy10-r1-B.md`, `tidy10-r2.md`; round 2 clean, its one
minor a doc sentence, fixed). Round 2's nit, kept: of the six `countsAtBuild` arms only superpluck's has a row; the
others change allocation only.

- **The rule now:** a node allocates its storage when it is BUILT (the voice build, `VoiceFactory.makeVoice`), sized
  from what the build can read: a block-constant param answers `controlRateValueOrNull` without a render context,
  read at freq 0 (`Ignitors.sizingValueOrNull`, for sizing only; nothing reads it for sound). A COUNT is read there
  only when the DSL build vouches that it does not read the note's frequency (`countsAtBuild`, from
  `IgnitorBuildCache.usesMusicalFreq`, the memo's freq-key walk); a count that does grows at the first block, as
  before (review round 1: `30 - 0.05 * freq` voices, read at freq 0, built 30 strings for a note that plays 8, 604 KB
  against 162 KB). The factories default `countsAtBuild` to false. The first block still reads every param and draws
  what it drew, and seeds or starts what the build made.
- **1. Drift lanes.** `AnalogDrift` is built, then seeded: `seed(analog, stepRate, rng)` writes every field and
  allocates nothing (the coefficient law is pure functions in `AnalogDriftCoeffs.kt`, no holder object); the
  three-argument constructor is both at once (the sample playhead and the filter humanization, built where their inputs
  are known). The sine, impulse and pluck build their lane with the node and seed it at the first block
  (`Ignitors.seedAnalogDrift`), at every depth as before. The wave, the partial bank, the stacks and the superpluck
  build drift storage only when `analog` may be above 0 at build (`mayDrift`: a signal, or a value above 0); the shared
  lane only when `analogSpread` may drop below 1 (`mayShare`; the default 1 never reads it). A build that ruled drift
  out and was wrong (a knob that reads the frequency) makes the lane or the container at the first block, as before
  the step (`startDriftLanes`, the shared lane in `prepareBlock`). `DriftLanes(capacity, sharedLane)`:
  `start(analog, stepRate, rng)` at the first block draws the shared seed, and `ensureLanes` seeds the lanes it raises
  instead of building them (a retired lane is re-seeded, which leaves it exactly a fresh one).
- **2. The phaser.** `PhaserIgnitor` builds its `PhaserCore` at `DEFAULT_BUILD_SAMPLE_RATE` and binds the context's
  rate at the first block (`PhaserCore.bindSampleRate`), before the kernel first runs.
- **3. The memo.** A memo that starts caching (`incConsumers(blockFrames)`, `cachePerBlock(blockFrames)`, both at
  build) allocates its cache there, at the voice's block size; the render's growth guard stays for a larger buffer
  (only a test rig hands one in).
- **4. The partial banks.** Each bank's four arrays are built at the count the build can read (`partialCapacity`).
- **5. The unison stacks.** The voice states, the base gain profile (`superSawVoiceGainsInto`, the allocating
  `superSawVoiceGains` kept for the pool and the specs) and the stateless banded draw's best candidate are built at
  the count the build can read (`unisonCapacity`). A shrink keeps the states; a voice that comes back re-uses its
  state and draws its phase and jitter as a new voice does, in index order. No reset: every field the stack reads is
  rewritten before the next render (review round 1 removed a `reset` that no render could observe).
- **6. The superpluck.** The strings (a 2500-sample delay line each) are built at that count.
- **7. The phase pool.** The `selection` string is parsed at build when the pool is on (never when it is off, as
  before); `PhasePools.pool` fills one probe key in place instead of allocating a key per request, and inserts a copy.
- **Still allocated at render, left on purpose:**
  - a count signal the build cannot read, or a count that reads the note's frequency: its first rise past what the
    build sized grows the arrays at that block (a count that changes within its maximum allocates nothing now; before,
    every change made a new array);
  - the shared drift lane's own `Random(sharedSeed)`, at the first block below spread 1 (about 40 bytes): its seed is
    drawn at render, and a generator a seed can restart is decision D7;
  - the phase pool's vocabulary while it grows (one entry per top-up, lazy by design so the first note stays inside
    its block) and a pool's creation at the first note of its key: both playback-level, shared, not per voice.
- **Cost moved, not removed.** The voice build runs on the audio thread, in the render callback that also renders the
  voice's first block (`VoiceScheduler.process` to `promoteScheduled` to `VoiceFactory.makeVoice`), so the bytes move
  from the first block to the build of the same callback. Where the build cannot rule a lane out (an `analog` signal)
  it builds lanes that may stay unused. At `analog` 0 the stacks, the partial bank, superpluck and the wave build no drift
  storage, and at spread 1 no shared lane (review round 1; the sine, impulse and pluck still build their one
  `AnalogDrift` at every depth); what a note costs more there on the JVM, 32 to 64 bytes, is the box of the build's own reads of the knobs. JVM bytes per
  note, build plus first block, in the report.
- **Proof.** A golden render of 33 voice configurations (every family; drift on and off, spreads below 1, count
  signals that walk, pooled and stateless banded phases, a white noise on the same stream after each source so a moved
  draw shows), 4 notes each, mid-block onset: bit-identical to the baseline sources (6,449,505 bytes, `cmp`). Every
  intermediate patch state compiles and passes `:audio_be:jvmTest`. In the compiled `klang-engine-audio_be.js` (test
  build) no touched `generate` creates an object outside the growth branches. JVM, a voice of each family whose knobs
  are signals (a block-constant knob's `Double?` is boxed by the JVM, see below): 0 bytes on the first block and on
  the 64 after it, and 0 bytes over 31 blocks of count changes within the maximum.
- **Rows** (each mutation-checked): `FirstBlockAllocationSpec` (jvmTest, a row per family, and one for what the build
  must NOT build: drift at analog 0, the shared lane at spread 1, strings for a count that reads the frequency),
  `AnalogDriftSeedSpec`, `DriftStorageFallbackSpec` (a build that guessed drift away renders and draws as one that
  saw it), `DriftLanesSpec` (the capacity and the shared lane change nothing), `SeededVoiceRngSpec` (the oscillators draw
  nothing at build), `SuperStackTransitionSpec` (grow, shrink, regrow: exactly one phase and one jitter draw per
  voice that is new or back). The phaser's bind is pinned by `PhaserCoreLawSpec`'s node row (48 kHz against the
  44.1 kHz placeholder), the memo's growth guard by `MemoizingIgnitorSpec`'s sub-block row.

## Step 11, collapse the twins (B2.2, B4.8, B2.15, B2.12, B4.9): done 2026-10-08

Scope: `tmp/reviews/tidy-step11-scope.md` (items (a), (b), (c) and (d1) to do, in the order (a), (d1), (b), (c), one
commit each; (d2) to (d4) won't-do, logged in `_maintainer-questions.md`). Report for every item:
`tmp/reviews/tidy-step11-report.md`. (a) and (d1) reviewed in two rounds (`tmp/reviews/tidy11-r1-A.md`,
`tidy11-r1-B.md`, `tidy11-r2.md`): round 1 moved (d1) to the scope's fallback, round 2 clean.

### (a) The shaper core (B2.2): done 2026-10-08

`ShapeIgnitor` (the `Shape` node, under the Ignitor `shape` and `distort` doors) was a twin of `DistortionCore` (the
fused `Distort` node): the same oversampler, the same NaN guard, the same DC blocker, the same order. It now holds a
`DistortionCore` and calls it at drive 1.0 (its gain comes from an upstream `Drive` node), then runs its own soft cap.
`x * 1.0` is exact for every value (-0.0, the infinities and the denormals included; a NaN is zeroed by the guard
either way), so the node renders as before, bit for bit. The soft cap stays the `Shape` node's: the two nodes are
different laws on purpose (decision D2). The `DistortionCore` KDoc now describes one core with two hosts; the comments
that named the `Shape` node's own loop (`ResourceWarehouse.WARM_OVERSAMPLE_FACTORS`, `LowPassHighPassFilters`' DC
blocker notes, `DEFAULT_DC_BLOCK_COEFF`) and `oversampling-regions.md`'s inventory name the shared core.

- **Proof:** the 18-song corpus and Kokon render bit-identical to `corpus-ep1-before` (label `ep1-t11a`).
  `:audio_be:jvmTest` and `:audio_be:jsBrowserTest` green.
- **Rows:** `OversamplerDecimatorParitySpec` gains the stage-0 gap the scope found: both nodes, every shape, the
  ragged windows, on a hostile source (NaN, both infinities, 1e300, a denormal, -0.0), bit for bit against the plain
  law written in the spec (the fused node at a drive and at unity). Mutation-checked: dropping `* d` on the plain path
  goes red on the new fused row (and `StripLawCoresSpec`), dropping it on the oversampled path goes red on the existing
  oversampled rows, a soft cap inside the core goes red on every node row, and dropping the plain path's NaN guard goes
  red only on the two new rows.

### (d1) The runtime arithmetic (B4.8, B2.15): done 2026-10-08

The 8 binary and 12 unary arithmetic nodes of `Ignitor.kt` (`PlusIgnitor`, `MinusIgnitor`, `TimesIgnitor`,
`DivIgnitor`, `PowIgnitor`, `MinIgnitor`, `MaxIgnitor`, `ModIgnitor`; `AbsIgnitor` to `SqIgnitor`) keep one class
each, but none writes a law of its own any more. Each law is one inline function in `ignitor/_arithmetic_laws.kt`
(`plusLaw` to `maxLaw`, `signedPow` once where it was written five times; `absLaw` to `sqLaw`), and every node calls
it on both paths: its `generate` through the one shared inline ladder (`binaryLadder`) or `unaryMap`, its
`controlRateValueOrNull` directly. Runtime `audio_be` only; the doors, the wire and every other module are unchanged.

- **Why one class per op (the scope's fallback, chosen in review round 1).** The first shape collapsed the 20 classes
  into one `BinaryIgnitor(op, a, b)` and one `UnaryIgnitor(op, upstream)`. It was bit-identical, but its scalar path
  was one shared, recursive method (`controlRateValueOrNull` through a 559-byte `binaryLaw`) that neither HotSpot
  ("hot method too big", "recursive inlining is too deep") nor V8 inlines through. A block-constant subtree then
  boxed its scalars: the optimizer's shape for `x.div(param)`, `x.affine(mul = 1.div(param))`, took 960 bytes per
  block on the JVM for 20 nodes where it took 0, nested scalars doubled their JVM time, and V8 scavenged in large
  graphs (reviewer B, `tmp/reviews/tidy11-r1-B.md`, MINOR 2). With a class per op every scalar is a small method of
  its own again, as before the step.
- **The ladder.** `binaryLadder` holds the four arms (both constant, right constant, left constant, scratch) and takes
  everything op-specific as inline lambdas: `law`; the dead branches `deadOnRight` and `deadOnLeft` (Times 0 on either
  side, Div 0 or an infinity on the right, nothing else); and, for the right-constant arm, `prepareRight` and
  `lawRight`. Div and Mod split their law into a divisor half (`divisorOf`, the `safeDiv`) and a quotient half
  (`divQuotient`, `modRemainder`), so a constant divisor is guarded once per block, as before the step, while each law
  is still written once (`divLaw` and `modLaw` compose the halves). Without the split, V8 paid the guard per sample:
  +64 % per Div node, +17 % per Mod node (reviewer B, MINOR 1). In the compiled JS and on the JVM each node's loops
  run their law inline, with no lambda object, no indirect call and no box.
- **Kept verbatim:** the clamps (Plus and Minus bare; Times, Div, Pow, Recip, Sq and Exp `safeOut`; Mod `safeDiv`
  without `safeOut`), Min and Max with `a` first under NaN, the unary edges (Abs keeps -0.0; Log and Sign map NaN and
  both zeros to 0; Sqrt keeps NaN; Round ties to even), the breach policy (a null scalar despite the flag falls
  through to the next arm), the class names and visibilities (`TimesIgnitor` stays `internal` for `EqIgnitor`).
- **Changed, and proven neutral:** the left-constant arm of Plus and Times runs `law(ka, y)` (it computed `y + ka`
  and `safeOut(y * ka)`); every node reads the window end after its child render (Floor, Ceil, Round, Frac, Recip, Sq
  and Mod read it before; no Ignitor moves the window).
- **Around it:** `MulConstIgnitor` (the scalar `mul(k)` door) runs `timesLaw` on both paths, so it and the Times node
  still agree by construction; the shared `mulConstInPlace` loop and `addConstInPlace` are gone, and the
  `ConstantFoldParitySpec` comments that named them name the laws.
- **Proof:** a raw-bits golden captured from the code before the change (`ZzScratchArithmeticGoldenSpec`, scratch, not
  committed): every op on every arm, contract breaches and nested constants, the unary ops on constants, signals and
  breaches, eight ragged windows with a sentinel outside the window, the scalar path, `isBlockConstant`, and how far
  each source rendered (what a dead branch skipped), over 30 hostile operands (NaN, both zeros, both infinities,
  ±1e308, ±SAFE_MAX, denormals, negative bases, the round ties). 19,625,952 bytes, bit-identical for the first shape
  and again for this one (`cmp`). The 18-song corpus and Kokon bit-identical (labels `ep1-t11d` and `ep1-t11d2`).
  `:audio_be:jvmTest` (2,378) and `:audio_be:jsBrowserTest` (2,277) green.
- **The NaN payload:** a second golden with two more NaNs (negative, and a payload of `0x123`) shows that a payload
  is no stable observable on the JVM: its Plus both-constant cell differed where the old and the new code compute the
  same `x + y` (the JIT decides which NaN survives), and its Round cell changed between two runs of the same code.
  The left-constant arm, the one whose operand order changed, did not differ. Nothing in the engine reads a payload,
  and the corpus is identical, so the uniform `law(ka, y)` stays.
- **Rows:**
  - `ArithmeticLawSpec` (commonTest, both platforms): every binary law on every arm and the scalar path, every unary
    law on both paths, each against an oracle written in the spec with literal clamp constants; the edges as literal
    values; the dead branches fill +0.0 by its bits where the law would give -0.0 and render nothing; Div's constant 0
    on the LEFT is not dead (the divisor renders, `0 / -2` is -0.0).
- **The scalar path's allocation, measured, no row.** 20 nested `x.affine(mul = 1.div(param))` (the optimizer's
  shape for `x.div(param)`) take 0 bytes per block once warm in a fresh JVM, as before the step; the first shape (one
  shared recursive scalar method) took 960 bytes per block. Not kept as a row: inside the test JVM the other specs
  leave these call sites megamorphic and the code before the step allocates the same there, so only a child JVM can
  see it, and a row that pins what HotSpot inlines is fragile across JDKs (coordinator, round 1). Numbers in
  `tmp/reviews/tidy-step11-report.md`.
- **Mutation-checked** (on this shape): Min's operands swapped in its `law` (red on `ArithmeticLawSpec` and
  `ConstantFoldParitySpec`) and on its scalar only (red only on `ArithmeticLawSpec`: the parity specs compare paths
  with each other, as the scope said); the ladder's left-constant arm swapped; Div's infinity no longer dead; `safeOut`
  on Plus; Div's right arm without its divisor guard. Every one red. The binary scalars routed through one shared
  non-inline method allocated 960,000 bytes per 2,000 blocks in the fresh-JVM probe.

### (b) The Karplus string core (B2.12): done 2026-10-08

`KarplusStrongIgnitor` (the `pluck` node) and `SuperKarplusStrongIgnitor` (`superpluck`) each carried the whole string:
the burst, the delay line, the fractional read, the brightness one-pole, the stiffness allpass, the write-back, each
under a `@Suppress("DuplicatedCode")`. The string is now one plain class, `KarplusString` (`ignitor/KarplusString.kt`):
the delay line, `writePos`, `excited`, `lpState`, `apPrevIn` and `apPrevOut`; `excite(baseDelay, pickPos, rng)`; and
`render(...)`, an `inline` function called with named arguments, which writes `sample * gain` or adds it to the
buffer. The pluck holds one
string and renders it at `gain = 1.0` without accumulation (`x * 1.0` is exact); the superpluck holds one per voice,
at its voice gain, the first string writing and the others adding. Its companion holds the line's length
(`MAX_DELAY`, 2500), the base delay law and the three coefficient laws (`lpAlphaOf`, `hasStiffnessOf`, `apCoeffOf`;
three functions, because one could hand back three values only through an allocation or a holder). Both suppressions
are gone.

- **Kept in the node shells, verbatim:** the param reads (order, count and cadence: the pluck reads its pick position
  once, at the pluck, and `analog` once, in `seedAnalogDrift`; the superpluck returns at no voices before any other
  read and reads every knob every block), the drift (one `AnalogDrift`, or `DriftLanes` with `ensureLanes(n + 1)`
  before that string's burst and `advanceLane(n)` after it), the unison detune of the base delay, and who sets
  `excited`. A string that comes back after a shrink is plucked again and keeps its filter state: `excite` writes the
  burst and `writePos` only.
- **Not done, as the scope said:** the pluck as a superpluck with one voice (its reads and its drift differ).
- **Proof:** a raw-bits golden captured from the code before the change (scratch, not committed; 67 cases, 12 MB):
  both nodes at stiffness 0 and 0.5, pick positions 0, 0.3 and 1, analog 0 and 2 (spread 0.4), with and without a
  vibrato, voice counts that walk (a signal through 1 to 7, and 3, 1, 4), a NaN and a 15 Hz frequency, a brightness and
  a decay signal, ragged windows (mid-block starts, 1-frame and 0-frame windows) with a sentinel outside the window, a
  white noise drawn from the same stream after each source, all built through the DSL runtime build. Bit-identical
  (`cmp`), and the stacks' golden untouched. The 18-song corpus and Kokon bit-identical (label `ep1-t11b`).
  `:audio_be:jvmTest` (2,381) and `:audio_be:jsBrowserTest` (2,280) green. After review round 1 the same goldens and
  the corpus again (label `ep1-t11b2`; the built-in Der Schmetterling row moved with the maintainer's uncommitted edit
  of the song, so that song was rendered from HEAD's text as well, bit-identical). On the JVM, with `render` inlined,
  the pluck's `generate` is 782 bytes (777 before), the superpluck's 1,209 (1,232 before); no loop has a `new`, a call
  through a function value or a box.
- **Performance (review round 1, reviewer B).** The first shape, `render` as one shared method, allocated per block
  on V8 and ran 28 to 37 percent slower with vibrato or drift; on the JVM it ran up to 20 percent slower once plain and
  vibrato notes shared one profile. Measured causes and fixes, all in `render` (its KDoc keeps the reasons):
  - V8 types the arguments of a function it does not inline as "any". A loop variable that starts from one (the
    delay, the drift ramp) stays tagged and boxes a heap number every sample (the optimized code shows the
    allocation after `m += dm` and after `dl / m`). Each double argument now passes through `* 1.0` before the loop,
    which V8 types as a number. This also removes the boxing the superpluck had before the step (its drift ramp
    started from a call result): 424 to 634 scavenges per run before, 7 to 13 now.
  - On the JVM the shared method and the constant modulus `% 2500` cost the rest (the write position is a
    loop-carried modulo); `render` is `inline` again, each node keeping its own compiled loop as before, and wraps by
    the line's length read at run time.
  - The state sits in locals during the loop.
  - Result, V8 (one case per process, against HEAD and a HEAD-against-HEAD control): no allocation, 0.54 to 0.91 of
    HEAD's time. JVM: within the control's noise, isolated and under mixed profiles. Tables in
    `tmp/reviews/tidy-step11-report.md`, "Round 1 fixes".
- **Rows:** `KarplusStringSpec` (commonTest): the burst draws exactly `burstLen` values in index order with zeros
  around it and the line past the delay untouched (six geometries worked out by hand, pick positions outside 0 to 1
  included); `excite` keeps `lpState` and the allpass state, and leaves a set `excited` flag set; gain 1 without
  accumulation writes the raw sample bit for bit, and accumulation adds `sample * gain`.
- **Mutation-checked:** `excite` resetting `lpState` goes red only on the new row: before this step no spec
  pinned the filter state a re-plucked superpluck string carries over (the scratch golden saw it in all 8 cases whose
  voice count walks). `ensureLanes` moved after the burst draw goes red on `BuiltInVoiceMatrixSpec`. A burst one
  sample longer, the filtered value written instead of the raw sample, the accumulation dropped: red on the new rows
  and `BuiltInVoiceMatrixSpec`. Re-run on the round-1 shape: the drift ramp stepped before the divide, the brightness
  state not written back after the loop, and the read index wrapped by a wrong length, each red on
  `BuiltInVoiceMatrixSpec` (the write-back also on `BlockFramingInvarianceSpec`).

### (c) The unison stacks (B4.9): done 2026-10-08

Five unison oscillators (`supersaw`, `superramp`, `supersquare`, `supertri`, `supersine`) ran on an abstract engine
(`DetunedStackIgnitor`) with a trapezoid layer (`TrapezoidStackIgnitor`), two classes that only configured the shape
(`SawStackIgnitor`, `PulseStackIgnitor`) and the sine (`SineStackIgnitor`), whose plain and phased loops were the
trapezoid's with another sample expression. They are one node now, `UnisonStackIgnitor(kind: StackKind, ...)` with
`private enum class StackKind { SAW, PULSE, SINE }`, after the `WaveIgnitor` precedent in the same file. The five
factories keep their signatures and pass named arguments. The shape knobs are constructor fields read only by their
kind (`resetSamples` and `shapeMax` by SAW, `duty`, the flanks and `flankSamples` by PULSE), as `WaveIgnitor` has it;
the sealed `StackShape` the coordinator's brief offered would type them per kind, but it is a second idiom beside the
precedent for six doubles, so the enum stays.

- **The voice loop:** per voice, in index order, a `when (kind)` calls one of four private loops: `trapezoidLoop`,
  `trapezoidLoopPhased`, `sineLoop`, `sineLoopPhased`. Each loop runs its own drift prologue (`advanceLane(n)` right
  before voice n renders; in the phased path after `phaseIn.render(offsets)`, as before), then `m`, `dm` and
  `safeWrap`, then its samples. Plain and phased stay separate loops, every per-sample expression copied verbatim.
  `configureShape` is a `when`: the saw's flyback with its NaN guard, the pulse's shape, nothing for the sine.
- **Performance (review round 1, reviewer B).** The first shape ran the prologue once, in a shared `renderVoice`, and
  handed the ramp to the loops as arguments. V8 inlined the plain loops but not the phased ones, so there the
  arguments arrived untyped (see the V8 rule in `audio/ref/performance.md`), and the phased stacks ran 20 to 29
  percent slower; supersine plain was about 5 percent slower on both platforms. With the prologue back inside each
  loop, as before the step, every stack row is within the control's noise on V8 and the JVM. `inline` loops were
  not the fix: they allocated on V8 (reviewer B). Tables in `tmp/reviews/tidy-step11-report.md`, "Round 1 fixes".
- **Method sizes (JVM):** `generate` 1,037 bytes (1,036 before), `renderVoice` 243, the loops 413, 488, 398 and 473
  (the virtual `renderVoice` and `renderVoicePhased` were 512 and 594 for the trapezoid, 497 and 579 for the sine:
  each carried the drift prologue). No loop has a `new`, a call through a function value or a box; the dispatch is
  once per voice and block. In the compiled JS the loops are module functions called directly, where they were
  methods on three classes.
- **Proof:** a raw-bits golden captured from the code before the change (scratch, not committed; 157 cases, 30 MB):
  every kind plain, at one voice, with a constant and with a moving phase (with and without drift, spreads 0.4 and
  1), under a vibrato (also with drift and a moving phase), with drift at spreads 0.4, 0 and 1, voice counts that walk
  (a signal through 1 to 7, with the drift, the pool and a moving phase; and 3, 1, 4 built directly), the pool on
  (pooled, pooled with drift, pooled and stateless with a walking count), |dt| at or above 1 (60 kHz, -60 kHz, 47 kHz
  with drift, 60 kHz with a moving phase, a spread typed in cents), a NaN frequency (plain, with drift and a moving
  phase, under a vibrato), a spread signal, ragged windows with a sentinel, the white noise after each source.
  Bit-identical (`cmp`), the Karplus golden too. The 18-song corpus and Kokon bit-identical (label `ep1-t11c`).
  `:audio_be:jvmTest` (2,382) and `:audio_be:jsBrowserTest` (2,281) green.
- **Row:** `UnisonStackShapeSpec` (commonTest): at a NaN frequency each kind holds its shape at phase 0, the same
  double on every sample: the saw -1 and the ramp +1 through the flyback's NaN guard, the square +1 and the triangle -1
  because a NaN flank floor drops out (`coerceAtLeast(NaN)` keeps the flank, so the pulse needs no guard), the sine 0.
  The phased path was already pinned bit for bit by `OscillatorPhaseSpec`.
- **Mutation-checked:** `advanceLane` moved after the voice render: red on `BuiltInVoiceMatrixSpec` and
  `SuperStackDriftSpreadSpec`. The saw's NaN guard dropped: red on `ExcitersTest` and the new row. A NaN guard on the
  pulse's floor with a wrong fallback (1.0): red only on the new row. The sine's phased path routed to the plain loop,
  and the moving phase not handed to the voices: red on `OscillatorPhaseSpec`.

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
  allocation on the audio thread per note, not per block. Re-checked in step 10: it is part of the voice BUILD, not of
  the render, so it stays (building the array in `VoiceFactory` saves one array of about four references per note
  and churns the test rigs). The build itself runs on the audio thread (see step 10, "Cost moved"), and it allocates
  the whole voice: every Ignitor node, the contexts, the pipeline lists, the bound `::getCompleteSample` reference per
  promotion (`VoiceScheduler.promoteScheduled`). A full "no allocation after build" on the audio thread means no
  allocation in the render callback at all: build voices outside it (on the JVM another thread; in the worklet the
  port's message handler, the same thread between callbacks), or keep built voices in pools per sound and reset them
  per note, which needs a reset contract on every Ignitor node and a replay of the build's draw order. L to XL;
  worth deciding with the Zig port, where an arena per voice is the natural shape.

## Found during tidy-up step 10

- **The JVM boxes a `Double` per block-constant param read.** `Ignitor.controlRateValueOrNull` returns `Double?`;
  `Ignitor.blockStartValue` (`readParam`) calls it every block for every control-rate knob, and the JVM allocates a
  box (24 bytes) wherever the JIT does not inline it away: a voice built from constants measured 0 to 48 bytes per
  block, varying from run to run, and its first block, which reads `analog` once more, one box more. Kotlin/JS does
  not box there. Not changed: it is the interface's shape (the KDoc of `isBlockConstant` names the cost) and costs
  nothing in the browser. A non-null `controlRateValue(freqHz): Double` beside `isBlockConstant` would remove it, if a
  JVM backend ever needs a render without allocation.
- **B4.5's orbit-side items were not in this step:** the scratch of `ResonatorBank` and `ParallelMixFilter` still
  starts at size 0 and grows at their first `process`, `KatalystDelayEffect` makes a `DelayLine` per ring rent, and
  `ScratchBuffers.oversample` looks its sub-pool up in a map per block (its first use per factor allocates, once per
  warehouse). All are per orbit or per backend, not per voice.
- **The active list's order still reaches the phase pool takes** (lifecycle step 5's finding above): unchanged by this
  step, which kept every draw at the first block.


## Found during tidy-up step 11

- **The Shape path allocates on V8, before and after this step alike** (review round 1, reviewer B): about 4 KB per
  block at stage 0 in the development JS build (52 scavenges per 105,000 blocks), 835 scavenges per 105,000 blocks at
  stage 4. Old and new code allocate the same, so step 11 did not cause it. It needs its own probe on the production
  bundle before anything is changed (`tmp/reviews/tidy11-r1-B.md`, "Outside this change").
