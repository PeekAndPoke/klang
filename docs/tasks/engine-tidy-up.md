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
a unison count is capped (`coerceUnisonVoices`, `UNISON_MAX_VOICES = 64` beside `coercePasses`, 256 since 2026-10-08, non-finite is 0;
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
  - On V8 a double that comes from a call result, a field or an argument of a function V8 does not inline, and
    seeds a loop-carried variable (the delay, the drift ramp), stays tagged and boxes a heap number every sample
    (the optimized code shows the allocation after `m += dm` and after `dl / m`). Each double value now passes
    through `* 1.0` before the loop, which V8 types as a number. This also removes the boxing the superpluck had
    before the step (its drift ramp started from a call result): 424 to 634 scavenges per run before, 7 to 13 now.
    Review round 2 confirmed the `* 1.0` is load-bearing in the inline shape too (without it: 633 scavenges, 40 to
    76 percent more time) and that computing the seed in the same function is no remedy. No test can pin it (the
    sound is bit-identical either way); the KDoc and the V8 rule in `audio/ref/performance.md` are the guard.
  - `render` is `inline` again, each node keeping its own compiled loop as before. Round 2 measured that this pays
    on V8 (as one shared method 3 to 18 percent slower, 9 to 18 on five of six Karplus rows), and is neutral on the
    JVM.
  - The JVM's own cost was the constant modulus `% 2500` (the write position is a loop-carried modulo): the wrap is
    by the line's length read at run time.
  - The state sits in locals during the loop (9 to 16 percent faster on V8 than the fields), the measured exception
    to the performance rules' "no snapshot into locals", named there.
  - Result, V8 (one case per process, against HEAD and a HEAD-against-HEAD control): no allocation, 0.54 to 0.91 of
    HEAD's time on a bundle of the probe alone, 0.61 to 0.80 on the full test bundle reviewer B measured on. JVM:
    within the control's noise, isolated and under mixed profiles. Tables in
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
- **Review round 2: the stiffness allpass, a gap older than the step.** No spec rendered `stiffness > 0` through an
  output check, so dropping the allpass state's write-back (`apPrevOut` or `apPrevIn`) left the suite green, and so
  would have a broken allpass law in HEAD. Two rows now: `BlockFramingInvarianceSpec` I2 renders a stiff pluck and a
  stiff superpluck through ragged blocks, and `BuiltInVoiceMatrixSpec` pins `pluck` and `superpluck` at stiffness 0.5
  by their raw bits, pins captured from the code before step 11 (`fb24e509`). Mutation-checked in one lock call:
  each write-back dropped goes red on both specs, the allpass sign flipped on the matrix row. Proof again: the goldens
  (`cmp`), both suites, the corpus (label `ep1-t11c3`, Der Schmetterling from HEAD's text) bit-identical.

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
  `trapezoidLoopPhased`, `sineLoop`, `sineLoopPhased`. Each loop is the old subclass method's body, in its order:
  the voice's fields and `safeWrap`, then its own drift prologue (`advanceLane(n)` right before voice n renders; in
  the phased path after `phaseIn.render(offsets)`, as before), then its samples. Plain and phased stay separate
  loops, every per-sample expression copied verbatim.
  `configureShape` is a `when`: the saw's flyback with its NaN guard, the pulse's shape, nothing for the sine.
- **Performance (review round 1, reviewer B).** The first shape ran the prologue once, in a shared `renderVoice`, and
  handed the ramp to the loops as arguments. V8 inlined the plain loops but not the phased ones, so there the
  arguments arrived untyped (see the V8 rule in `audio/ref/performance.md`), and the phased stacks ran 20 to 29
  percent slower; supersine plain was about 5 percent slower on both platforms. With the prologue back inside each
  loop, as before the step, the phased rows came back; supersine plain did too only once each loop also read its
  fields in the old order again (the fields and `safeWrap` before the prologue: on the full test bundle the
  prologue-first order measured 9 to 11 percent slower on V8, three rounds). Now every stack row is within the
  control's noise on V8 (trimmed and full bundle) and on the JVM (isolated and mixed). `inline` loops were not the
  fix: they allocated on V8 (reviewer B). Tables in `tmp/reviews/tidy-step11-report.md`, "Round 1 fixes".
- **Method sizes (JVM):** `generate` 1,209 bytes (1,036 before; it now holds the two per-voice `when`s), the loops
  499, 574, 484 and 559 (the virtual `renderVoice` and `renderVoicePhased` were 512 and 594 for the trapezoid, 497 and
  579 for the sine; each carries the drift prologue, as they did). No loop has a `new`, a call through a function
  value or a box; the dispatch is once per voice and block. In the compiled JS the loops are module functions called
  directly, where they were methods on three classes.
- **Proof:** a raw-bits golden captured from the code before the change (scratch, not committed; 157 cases, 30 MB):
  every kind plain, at one voice, with a constant and with a moving phase (with and without drift, spreads 0.4 and
  1), under a vibrato (also with drift and a moving phase), with drift at spreads 0.4, 0 and 1, voice counts that walk
  (a signal through 1 to 7, with the drift, the pool and a moving phase; and 3, 1, 4 built directly), the pool on
  (pooled, pooled with drift, pooled and stateless with a walking count), |dt| at or above 1 (60 kHz, -60 kHz, 47 kHz
  with drift, 60 kHz with a moving phase, a spread typed in cents), a NaN frequency (plain, with drift and a moving
  phase, under a vibrato), a spread signal, ragged windows with a sentinel, the white noise after each source.
  Bit-identical (`cmp`), the Karplus golden too. The 18-song corpus and Kokon bit-identical (label `ep1-t11c`), and
  again after review round 1 (label `ep1-t11c2`; Der Schmetterling from HEAD's text, as for (b)).
  `:audio_be:jvmTest` (2,384 with (b)'s round-2 row) and `:audio_be:jsBrowserTest` (2,282) green.
- **Row:** `UnisonStackShapeSpec` (commonTest): at a NaN frequency each kind holds its shape at phase 0, the same
  double on every sample: the saw -1 and the ramp +1 through the flyback's NaN guard, the square +1 and the triangle -1
  because a NaN flank floor drops out (`coerceAtLeast(NaN)` keeps the flank, so the pulse needs no guard), the sine 0.
  The phased path was already pinned bit for bit by `OscillatorPhaseSpec`. A second row (review round 1) closes a gap
  older than the step: at ±60 kHz (an increment of 1.25 cycles per sample) a moving phase that is 0 everywhere renders
  the plain stack bit for bit, every kind, so the phased loops' safe wrap is pinned.
- **Mutation-checked:** `advanceLane` moved after the voice render: red on `BuiltInVoiceMatrixSpec` and
  `SuperStackDriftSpreadSpec`. The saw's NaN guard dropped: red on `ExcitersTest` and the new row. A NaN guard on the
  pulse's floor with a wrong fallback (1.0): red only on the new row. The sine's phased path routed to the plain loop,
  and the moving phase not handed to the voices: red on `OscillatorPhaseSpec`. Re-run on the round-1 shape:
  `advanceLane` after the render in `trapezoidLoop` (red on `BuiltInVoiceMatrixSpec`, `OscillatorPhaseSpec` and
  `SuperStackDriftSpreadSpec`) and in `sineLoopPhased` (red on `OscillatorPhaseSpec`), both phased paths routed to the
  plain loops (red on `OscillatorPhaseSpec`), the saw guard again (red as before). The phased loops' safe wrap without
  its `|dt| >= 1` term survived every committed spec before the new row (the same line in HEAD did too); now red on
  it, in the trapezoid and the sine loop alike.

## Step 12, the Katalyst twins (B2.8, B2.10, B4.11)

Scope: `tmp/reviews/tidy-step12-scope.md` (items (b), then (a), then (c); its "Coordinator decisions" at the top).
Report: `tmp/reviews/tidy-step12-report.md`.

### (b) One fade law for the bank swap and the compressor (B2.8): done 2026-10-08

`KatalystFilterSwap.Crossfading` (the bank fade under body, vowel and the orbit EQ, 20 ms) and
`KatalystCompressorEffect.Fading` (the compressor's switch, 50 ms) each wrote the same linear law in their own loop,
one-ended in the swap (`w = w0 * (r * inv)`, `mix += w * (entry - mix)`) and two-ended in the compressor
(`w = to + (from - to) * (r * inv)`, `mix = dry + w * (mix - dry)`). The law is written once now, in
`utils/linear_crossfade.kt`: `linearFadeWeight(weightFrom, weightTo, remaining, invLength)` and the mono
`crossfadeLinear(target, base, other, count, ...)`, both `inline`, no lambda, which reads `base[i]` and `other[i]`
before it writes `target[i]`. The swap calls it per channel with `base = target = mix`, `other` the outgoing entry and
`weightTo = 0.0`; the compressor with `base` the dry mix and `other = target = mix`. The turned-around weights
(`Crossfading.targetWeight`, `Fading.weightNow`) are the same law and call `linearFadeWeight`. The landing, the
turnaround and the state code stay where they were. The KDoc names the chain swap's `Crossfade` (input ramped down,
output ramped up) and the duck's glide (the reduction scaled, weights from the block's end) as laws that are not
twins of this one.

- **Why bit-identical:** with `weightTo = 0.0` the shared weight is `0.0 + (w0 - 0.0) * x`, the same double as
  `w0 * x` for every `w0` but -0.0 (and a NaN's payload); the swap's `w0` is 1.0 or a target weight in `[0, 1]`, +0.0
  at worst. `mix[k] += e` is `mix[k] = mix[k] + e`.
- **Proof:** a raw-bits golden captured from the code before the change (scratch, not committed; 2,532 lines): the
  swap driven with two stateful stub filters per pair through every cell of its table (set, resume, clear and reset
  in Off, Engaged and Crossfading, fresh and not, the refusals, both turnarounds, both landings), in whole blocks and
  in ragged ones (37, 200, 1 and 64 frames, so the scratch grows); the compressor without and with a lookahead
  (5 ms) through the fresh snap, a fade out, both turnarounds, the landing, ON from Off, a knob glide mid-fade and
  while engaged, OFF while gliding, a reset mid-fade; both at 44.1 and 48 kHz on noise, sines and a hostile source
  (NaN, both infinities, ±1e300, a denormal, -0.0). Identical (`cmp`); the off-by-one mutant below changed 1,302 of
  its lines. The 18-song corpus and Kokon bit-identical (label `ep1-t12b`; Der Schmetterling from HEAD's text).
  `:audio_be:jvmTest` (2,390) and `:audio_be:jsBrowserTest` (2,288) green.
- **Row:** `LinearCrossfadeSpec` (commonTest): the blend and the weight against the law written in the spec, raw
  bits, on a hostile source and six fades (both directions, mid-fade windows, a 49-sample fade); both aliasing forms
  against separate arrays; the first weight is `weightFrom` exactly at the engine's four fade lengths (882, 960,
  2205, 2400) and the landing `weightTo` exactly, while at 49 samples the first weight is one ulp short (the law, not
  the end); the window is `count` samples with a sentinel after it; `weightTo = 0` equals `w0 * (r * inv)` over a
  sweep of `w0`, -0.0 being the one double where they part; NaN, the infinities and -0.0 through the blend and the
  weight.
- **Mutation-checked:** the blend reassociated as `(1 - w) * base + w * other` (2 rows red), the weight as
  `from - (from - to) * ...` (5 red), `remaining` off by one (3 red), the target written before `other` is read (red
  on the aliasing row).
- **Performance:** neutral. HEAD against HEAD plus (b), on the resonator change cases (where the fades run): JVM 0.98
  to 1.00, V8 0.98 to 1.00, each against a HEAD-against-HEAD control of 0.98 to 1.00; V8 allocates the same bytes.
  `Crossfading.process` grew from 655 to 806 bytes and `Fading.process` from 531 to 686 (two mono passes where one
  fused loop was); both were above the JVM's inlining size before.

### (a) Body and vowel as one implementation (B2.10, B4.11): done 2026-10-08

The maintainer agreed (2026-10-08, "Decided" below). `KatalystBodyEffect` and `KatalystFormantEffect` were
token-identical twins around one DSP; they are one class now, `KatalystResonatorEffect(kind: ResonatorKind, ...)` with
`enum class ResonatorKind { BODY, VOWEL }` (the kind supplies the unset wet and floor, at control rate), and one writer,
`KatalystResonatorWriter`. A chain still declares a body stage AND a vowel stage, two instances with their own slots
and banks: `vowel("a").body(material = "wood")` runs both, and the golden below renders exactly that.
`KatalystChain.body` / `.vowel` find their stage by kind.

- **The DSP** (shape R of the scope). `ResonatorBank` is one MONO bank: per band `a1`, `a2`, `a3`, `k`, the gain and
  the two integrators in flat arrays, the dry/wet blend (`WetDryMix`, p = 2) and a wet scratch sized at construction.
  `install(config)` makes it a fresh bank on a table, a mix and a floor (every integrator zeroed); `process` keeps the
  `amount <= 0` bypass first, runs each band in its own small method (`runBand`, state in locals, written back once)
  and blends. `ParallelMixFilter`, `BaseSvf` and `SvfBPF` are gone (that closes
  `svf-resonator-class-collapse.md`, archived as `../tasks-archive/2026-10/20261008-svf-resonator-class-collapse.md`);
  `SvfCoeffSweep` stays for `Ignitor.svf`. `createBody` / `createFormant` stay as one-shot builders for the specs and
  the benchmark, through `install`.
- **The tables.** `ResonatorTable` holds one row list's `freq` and `q` (raw) and gains (`bodyGain` / `vowelGain`, the
  rules unchanged). `ResonatorTables` builds one per catalogue index once, and indices whose rows are equal share ONE
  instance, so the stage compares tables by reference and a switch among aliases (`bass:a`, `bass:ei`, `bass:au`;
  `alto:*` and `countertenor:*`; an umlaut's two spellings) installs nothing, as the structural compare did. The
  catalogues expose `slotIndexAt(index): Int` (`BodyMaterials`, `VowelBands`), which `modesAt` / `bandsAt` use too, on
  the existing shared rule `catalogueIndexAt` with fallback 0 (the scope proposed a new copy of the rounding).
- **The pool** (coordinator decision): two pairs, built at the stage's FIRST install (the classic chain declares both
  stages and most voices use neither), the EQ's `freeBank` rule (here `freePair`): an install takes the pair the swap
  does not hold.
  One allocation per stage life. The rest of the lifecycle (the OFF arm, resume by identity, the parking, the landing
  re-offer) is the twins' code; parking keeps a table reference and two numbers.
- **`ResonatorBank`'s rule, reworded, not dropped:** a SOUNDING bank never retunes; only a bank nobody hears is
  reconfigured, from zero, which is bit-identical to building it new. The maintainer's rejection of the 5c-10 morph
  stays quoted.
- **The guardrail** "`KatalystBodyEffect` / `KatalystFormantEffect` are intentional un-deduped twins" in
  `.claude/skills/review-loop/audio-constraints.md` is retired in this change; one line says one class, two kinds.
- **Changed for specs only:** a null floor and an explicit `BODY_FLOOR` / `VOWEL_FLOOR` are one config now (the stage
  takes a number; the scope placed this in (c)), and a spec's two equal band lists are one table only through the
  spec helper `SpecResonatorTables` (equal rows, one table, the engine's rule). Production never passed either.
- **Performance, and what V8 taught.** The first shape passed the mix and the floor to `configure` as arguments every
  block. Bit-identical and faster, but on V8 a non-integral double handed to a function it does not inline is a heap
  number per call: about 40 bytes per block of a steady body, where the twins (one carrier object per change) took none. The
  stage is offered a `ResonatorConfig` now (table, mix, floor; the writer owns one and fills it at resolve, the stage
  keeps its own for what it installed and what it parked), and the bank installs from one. Then `install`, with the
  coefficient math inlined by Kotlin, used up V8's inlining budget and boxed the wet/dry law's doubles, about 140 bytes
  per change; split into `installBand` and `installBlend`, it boxes none. The rule is in `audio/ref/performance.md`.
  Final numbers (one case per process, against HEAD plus (b), with an old-against-old control; tables in
  `tmp/reviews/tidy-step12-report.md`):
  - **V8:** 0.56 to 0.57 of the old time on every case (steady body, vowel, both; a change every 16 blocks; a change
    every block), control 0.97 to 1.01. Scavenges per 42,000 blocks: 8 to 16 before on the change cases, 0 to 1 now,
    the steady cases' level. Allocation per change, over 4.2 million blocks: about 18 KB (body) and 12.5 KB (vowel)
    before, about 110 bytes now, all of it outside the stage: about 94 bytes in the chain's re-resolve of a new param
    map (shared code; about 155 before) and the rest in the swap's fade, as at HEAD. The stage's install allocates
    nothing.
  - **JVM:** 0.82 to 0.93 isolated, 0.85 to 0.89 with every case warmed in one JVM, control 0.99 to 1.05. Bytes per
    change: 13,000 (body) and 10,700 (vowel) before, 0 now (a constant 96 bytes per 16,000-block run, not per
    change; the stage's own row measures 0).
  - **Method sizes (JVM):** `runBand` 246 bytes, `ResonatorBank.process` 131, `install` 76, `installBand` 393,
    `installBlend` 73; `KatalystResonatorEffect.configure` 330 (the body twin's 323). Before, per sample:
    `SvfBPF.process` 284 per band, `ResonatorBank.process` 247, `ParallelMixFilter.process` 154, and an `AudioFilter`
    call per band per block. Nothing in a per-sample loop allocates, boxes or calls through a function value.
- **Proof:** a raw-bits golden at CHAIN level captured before the change (scratch, not committed; 2,064 lines): the
  classic chain through on, a change, a change mid-fade (parked, then replaced), the alias switches `bass:a` to
  `bass:ei` to `bass:au`, a vowel change and an OFF parked mid-fade and taken back, a body return mid fade-out, a
  return mid fade-in, landings to Off and a reinstall after them, a reset mid-fade and the fresh snap after it, a
  burst of a change every block on both stages, wet 0, below 0, above 1, NaN, -0.0; floor NaN, 0.05, -0.0 and 0; no
  owner; and a chain declaring a constant vowel `a` and body `wood`, in both orders; at 44.1 and 48 kHz on noise, sines
  and a hostile source (NaN, both infinities, ±1e300, a denormal, -0.0), with the stages' seams per block.
  Identical (`cmp`) for the first shape and again for the final one; dropping the install's state zeroing changed 784
  of its lines. The (b) golden identical too. The 18-song corpus and Kokon bit-identical (label `ep1-t12a`; Der
  Schmetterling from HEAD's text). `:audio_be:jvmTest` (2,395), `:audio_be:jsBrowserTest` (2,291) and
  `:audio_bridge:jvmTest` (146) green.
- **Rows** (each mutation-checked):
  1. `ResonatorBankSpec`: the bank against the law written in the spec, raw bits, on a hostile source in ragged blocks
     at eight mix and floor cases (the bypass included); the band order is observable. Red under a reassociated tap,
     a reassociated blend, a skipped bypass.
  2. A reinstalled bank renders as one built new, raw bits. Red without the state zeroing (also on four rows each of
     `KatalystResonatorBodySpec` and `KatalystResonatorVowelSpec`).
  3. `KatalystResonatorEffectSpec`: installs alternate between the pairs while one sounds, a change mid-fade takes
     none, a re-offer installs nothing, after a landing to Off the first pair again (the `lastInstalledPair` and
     `installs` seams); the pool is built at the first install, not with the stage. Red when every install takes pair
     0 (also six rows of `KatalystResonatorBodySpec`), and when the pool is built eagerly.
  4. `KatalystResonatorAllocationSpec` (jvmTest, the `FirstBlockAllocationSpec` way): material and vowel changes every
     few blocks, bursts, offs, over ten runs: the fewest bytes a later run took is 0. Red when `install` allocates. It
     holds in the shared test JVM (it measures the stage directly, no megamorphic boxing on its path).
  5. `ResonatorTablesSpec`: two indices share a table exactly when their rows are equal (and the vowel catalogue has
     such pairs); every table's freq and q raw and its gains bit-exact against the rules written with literal
     constants; the capacity is the largest row count (8 and 5); a slot value reads the catalogue's index rule. Red
     without the sharing and under a regrouped vowel gain.
  6. `KatalystResonatorEffectSpec`: a switch among names of one bank renders as no switch, raw bits, at chain level,
     with a control that a real switch does change the output. Captured against the twins first (green there, and
     red there with the compare made `===`); red now without the sharing.
  7. A table larger than the pool's capacity renders as a bank built new for it, raw bits, on the bank and through
     the stage. Red when the bank does not grow.
  Also: `CatalogueIndexSpec` pins `slotIndexAt`; a writer that does not fill the mix goes red on
  `KatalystSlotResolverSpec` and `KatalystClassicMatchesUntouchedVoiceSpec`; an offered parking slot not cleared first
  goes red on the body spec's parking rows. Review round 1 added a row in `KatalystResonatorEffectSpec`: a chain that
  declares the vowel before the body (and the other way round) runs both, and `chain.vowel` / `chain.body` each find
  their own kind; red when the vowel accessor drops its kind filter (that mutant survived every spec before) and when
  the body accessor takes the first resonator.
- **Specs moved with the classes:** `KatalystBodyEffectSpec` and `KatalystFormantEffectSpec` are
  `KatalystResonatorBodySpec` and `KatalystResonatorVowelSpec` (the assertions unchanged but for their spelling and
  the null floor; the oracles stay at 1e-12, which is why rows 1 and 2 are raw bits); the `SvfBPF` rows of
  `LowPassHighPassFiltersSpec` and the parallel-mix wrapper's spec moved into `ResonatorBankSpec` (the sweep and
  `cutoffOffsetMul` rows went with the sweep); the order rows compare a stage name with its kind. `EffectBenchmark`'s
  `SvfBPF` case is a one-band bank.

### (c) The `FilterDef` carriers retired (A2.3): done 2026-10-08

After (a) the engine read only the ROWS of `FilterDef.Body` / `FilterDef.Formant`; the carriers (rows, mix, floor)
were left to the specs. The rows now live with the catalogues that own them, `BodyMaterials.Mode` and
`VowelBands.Band` ("Vowel", not "Formant", so B3.6 is not pre-empted), with their gain-convention KDoc. The resource
bounds that shared the file (`FILTER_MAX_PASSES`, both `coercePasses`, `UNISON_MAX_VOICES`, `coerceUnisonVoices`)
moved to `audio_bridge/.../_resource_bounds.kt` in the same package, so no import changed (`Ignitors.kt` included).
`FilterDef.kt` is deleted and `FilterDef` is in `docs/retired-names.md`.

- **The specs' carrier:** `SpecBody` / `SpecVowel` (rows, mix, nullable floor) in `_resonator_spec_helpers.kt`,
  test-only, beside `SpecResonatorTables`; the body and vowel stage specs read as before.
  `KatalystClassicMatchesUntouchedVoiceSpec` compared the chain against a hand-built `FilterDef` "the wire carries"
  (the wire stopped carrying it in phase 3 step 9); its four rows now compare against the catalogue's rows and the
  literal mix and floor, the same assertions without the carrier.
- **Docs:** `audio/CLAUDE.md`, `audio/ref/data-model.md` (the section is "The body and vowel rows"),
  `audio/MEMORY.md`, `ir-to-modal-table-extraction.md` (its stale `BodyFilter.kt` line too), the named-args task's
  helper bodies, the `scaledBy` KDoc, `KatalystSlots` / `KatalystChain` KDoc (the composite is a `ResonatorConfig`),
  the `BusEffectDefaults` header. Historic records keep their words; sprudel's KDoc sentences that say what the
  `filters` field once carried stay, as history.
- **Proof:** no behaviour change, bit-identical. Compiled: `audio_bridge`, `audio_be`, `klangscript-libs`,
  `sprudel`, `audio_benchmark`, `audio_fe`, `audio_jsworklet`, `klangui`, `klang-notebook` and the root module, JVM
  and JS. `:audio_bridge:jvmTest` (146), `:audio_be:jvmTest` (2,396), `:sprudel:jvmTest` (3,471),
  `:audio_be:jsBrowserTest` (2,292) and the root `KatalystDoorFillRenderSpec`, `KatalystBodyNonFiniteWetSpec`,
  `DslDocExamplesSpec` green. The corpus (label `ep1-t12c`): the 16 rows identical to `corpus-ep1-before.txt`, Der
  Schmetterling from HEAD's text `94dfc72ae5637e91`, Kokon `2f8d88cd2458fce2`. The wire is untouched: the generated
  codec names none of the moved types, so `WIRE_SCHEMA_HASH` cannot move.

### Found during tidy-up step 12

- **A param map change allocates on V8 in the chain's re-resolve** (about 94 bytes per new map instance, the
  resonator's table lookup excluded by experiment; about 155 before this step, when the writer also built a carrier).
  It is `KatalystChain.resolveParams` and `KatalystKnob.resolve` for every slot writer, so a `.katp` burst pays it
  per block. Not measured per stage; its own probe, behaviour-neutral.
- **B4.5's orbit-side item for the resonators is closed:** the bank's wet scratch is sized at construction (it grew
  at its first `process`), and the parallel-mix wrapper and its scratch are gone. The other B4.5 items stand.
- **The delay, reverb and phaser allocate on V8 in steady state, before and after this step alike** (review round 1,
  reviewer B, `tmp/reviews/tidy12-r1-B.md`, the classic chain with one stage set and nothing changing, V8 at
  `--max-semi-space-size=1`): delay 99 to 118 bytes per block (5 to 6 scavenges per 420,000 blocks), reverb 79 (4),
  phaser 99 (5); the gain (`gain.gain` 0.8) 0; the compressor 0 steady and 60 bytes per block when toggled every 16
  blocks (about 960 bytes per switch). That is about 30 to 45 KB/s per orbit with a delay, reverb or phaser. Their
  writers pass doubles to `configure` every block, the shape that cost the resonator stage 40 bytes per block (see
  (a) and `audio/ref/performance.md`); the boxing site is not located yet. Its own probe, behaviour-neutral.
- **Accepted (coordinator, round 1): the compressor's switch fade is 2 to 8 percent slower on the JVM** (reviewer B,
  MINOR 1). `Fading.process` runs two mono `crossfadeLinear` passes where one fused stereo loop was. On the classic
  chain with the compressor toggled every 16 blocks (fading nearly all the time): old 3,834 and new 4,000 ns/block
  over 5 rounds (1.04), 3,830 and 4,138 over 8 (1.08, every new round above every old one); attributed, (b) alone
  1.01 to 1.025 against a control of 0.99 to 1.00, the rest of the new tree's spread bimodal per process (about 3,900
  or 4,150), which looks like a JIT decision. V8 0.99 to 1.01 against a 0.99 to 1.01 control; no fade at all, 1.00 on
  both. At most about 300 ns per block for the 50 ms after a switch, on the JVM only. A stereo variant of the helper
  is the shape if the JVM number is ever wanted back.
- **Accepted (coordinator, round 1): a shelved cylinder keeps the resonator pool** (reviewer B, MINOR 2). `retire()`
  is `reset()`, which keeps the pool, and the classic chain lives as long as its cylinder: about 7.3 KB for the body
  and 6.4 KB for the vowel once both have been used, about 13.8 KB per cylinder, at most about 440 KB process-wide
  across the warehouse shelf's 32 idle cylinders. A cached declared chain (`MAX_CACHED_CHAINS`, 8 per cylinder) gets
  its own pools at its first install, freed when it is evicted or at `startNewLife`. It meets the cleanup rule: the
  pool is the stage's own, built once per stage life, nothing grows per edit, and it goes with the chain or the
  cylinder. Before the step a shelved cylinder held no resonator memory (a released bank was dropped); now it holds
  the pool. Dropping the pool in `retire()` would cost one 7 KB build at the next life's first install.

## Step 13, the stage accessors to a test helper and a plain number at every constant door (A2.1, D10)

Report: `tmp/reviews/tidy-step13-report.md` (with the door table for (b)).

### (a) The chain's stage accessors move to a test helper (A2.1)

- **Production read a serial stage's typed accessor in one place only:** `MasterBus` asked a chain for its `reverb`
  and `delay` to know whether a tail is possible at all. That is now a chain property, `KatalystChain.declaresTail`,
  computed once at build with one walk of the stage array (no list). Chosen over keeping the two accessors because
  the master needs the yes or no, not the stages, and a property of the chain belongs on the chain (the audit's A2.6
  remark); main code then keeps no typed accessor for a serial stage (the duck, which runs outside the pipeline, keeps
  its own, and `declaresTail` and `sustainsItself` still test kinds). `latencyFrames` sums with the same walk instead
  of a filtered list.
- **The six typed accessors on `KatalystChain` and the seven on `Cylinder` are gone from main code.** The specs read
  the same names from `audio_be/src/commonTest/kotlin/cylinders/katalyst/_katalyst_test_helpers.kt`: extension
  properties of the same name and type over `KatalystChain.pipeline` (the last of each kind; body and vowel by
  kind), and over the chain in service for a `Cylinder`, through one new test seam, `Cylinder.currentChain`. Same
  names, so no read changed its words: 14 spec files gained imports, the about 280 reads are untouched, and
  `KatalystResonatorEffectSpec`'s "each accessor finds its own kind" row now pins the helper.
- **Allocation:** a classic chain build on the JVM, 24,240 bytes at HEAD, 23,752 after (488 bytes, the seven lists).
- **Row:** `KatalystChainBuilderSpec`, "a chain declares a tail exactly when it declares a reverb or a delay", with
  negatives for a gain, a compressor, a phaser and every other classic stage; red under each mutant (the check
  without the delay, without the reverb, with a phaser counting as a tail).
- **Proof:** `:audio_be:jvmTest` 2,400 and `:audio_be:jsBrowserTest` 2,296 green (the browser disconnected before any
  test four times first; HEAD and this tree each ran one spec green next, so it is the environment); the corpus, label
  `ep1-t13a`: the 16 other rows identical to `corpus-ep1-before.txt`, Der Schmetterling from HEAD's text identical,
  Kokon from `kokon-head.ks` identical to `corpus-ep1-before-kokon.txt`.

## The V8 allocation pass

Scope: `tmp/reviews/v8-allocation-pass-scope.md` (its "Coordinator decisions" first). Report, with every table:
`tmp/reviews/v8-allocation-pass-report.md` (its "Round 1 fixes" for the series as landed). Reviews:
`tmp/reviews/v8pass-r1-A.md`, `tmp/reviews/v8pass-r1-B.md`. The worklet runs on V8, where a steady per-block path
allocated what the JVM does not. Measured on node 22.12, on the development test bundle AND the production one
(`:audio_be:compileTestProductionExecutableKotlinJs`, the shape the worklet ships), in a chain probe and at engine
level (one sustained note through `PlaybackEngine`), at a 1 MB semi-space, N = 300,000 blocks, with the process
pinned to one core AND unpinned (review round 1: the two disagree for the stages). Exact refactors only, the sound
bit-identical (proof below). The rules learned are in `audio/ref/performance.md`.

- **Landed, in commit order** (one patch per family):
  1. **The loop seeds** (by rule, the coordinator's decision): `* 1.0` on all 14 drift seeds (the four unison stack
     loops, the sine's two loops, the partial bank's two, the wave oscillator's three, the impulse's two,
     `SampleIgnitor`), the two drift recipes in the `AnalogDrift` and `DriftLanes` KDoc, and, measured, the partial
     bank's phase, which `renderPartial` and `renderPartialPhased` seed from an argument. The partial bank with drift
     took about 10.5 KB per block at HEAD (one heap number per sample per partial, `sine(harmonics = 4)`; without
     drift 2.1 KB alone, 10.5 KB under a mixed profile), 304 bytes after (48 without drift), on both bundles, pinned
     and unpinned, and runs 0.73 to 0.92 of its old time on V8. The stacks boxed one heap number per sample per
     voice under a MIXED profile (every oscillator case warmed in one process): on the development bundle in 2 of 3
     processes before and 0 of 3 after (review; 4 of 8 and 0 of 7 in the pass); on the production bundle in 2 of 3 in
     one sample and 0 of 3 in another, so not reproduced there. A one-case run did not show it.
  3. **The phaser's block hop:** `PhaserBlock`, the stage's own holder, filled per block and handed to
     `Phaser.process`; the eight-argument `process` stays as the door and fills `Phaser`'s own.
  4. **The writer-to-stage holders:** `DelayConfig`, `ReverbConfig` (`lowpass` stays `Double?`) and `PhaserConfig`,
     each writer's own, filled when it is built and at every resolve; each double-argument `configure` stays as a door
     that fills the stage's own holder, and the body lives only in the holder overload.
  5. **The param resolve (S1):** `KatalystKnob.resolve` in two branches instead of `fromState ?: authored`; the heap
     profiler attributed the bytes of a new map to it on both bundles.
- **Result, bytes per block, HEAD then the pass.** Production pinned: the median of review round 2's runs (3 to 6
  per arm, four pinned processes on separate physical cores), the range in brackets. Development pinned: one run per
  arm, HEAD's control run in brackets. Unpinned: the median of three runs, the range in brackets.

  | Row | prod, pinned | prod, unpinned | dev, pinned | dev, unpinned |
  |---|---|---|---|---|
  | engine, all three stages | 277 (277) to 197 (196 to 197) | 229 (182 to 229) to 149 (132 to 149) | 356 (356) to 276 | 356 (356 to 388) to 260 (228 to 276) |
  | engine, delay | 81 (81 to 113) to 82 (34 to 98) | 112 (97 to 145) to 98 (98 to 114) | 129 (113) to 131 | 144 (112 to 177) to 131 (115 to 131) |
  | engine, reverb | 21 (21) to 22 (22) | 21 (21 to 53) to 38 (22 to 55) | 147 (147) to 102 | 132 (52 to 164) to 103 (86 to 117) |
  | engine, phaser | 176 (160 to 176) to 144 (144 to 192) | 112 (112 to 128) to 112 (48 to 112) | 128 (128) to 96 | 112 (96 to 128) to 112 (96 to 112) |
  | engine, phaser with floor 0.5 | 240 (208 to 256) to 192 (160 to 192) | 176 (128 to 256) to 128 (128 to 144) | 176 (176) to 128 | 192 (160 to 192) to 144 (128 to 144) |
  | `classic-remap16` (a new map every 16 blocks) | 82 (81 to 82) to 71 (70 to 71) | 82 (82) to 71 | 84 (81) to 71 | 82 (82) to 71 |
  | `partials-drift` | 10,464 (10,464) to 304 | the same | the same | the same |

  The drifting stacks run alone read the same before and after (0 or 16, one box per block from the constant read
  below). The whole chain is better in both conditions on both bundles; the single stages move within the spread of
  their own condition. The development delay at chain level (`delay-steady`) moves by up to one heap number either
  way (97 to 131 unpinned in the pass; 96 to 98 unpinned and 96 to 114 pinned in review round 2), and not from the
  holders: without them it reads the same (98 unpinned, 99 pinned), and at engine level they help the development
  delay (130 without against 114 pinned, 146 against 131 unpinned). **What the stages still allocate** is the "pass"
  column above: they are not clean (`audio/ref/performance.md`).
- **Dropped after review round 1** (coordinator, by reviewer B's leave-one-out in both conditions): an inline
  finiteness compare in place of Kotlin's `isFinite()` in the per-block guards (better pinned, worse unpinned on the
  phaser and on all three stages together: 166 without against 213 with), and a Unit-returning glide step in place
  of `KnobGlide.advance()` (within noise, a new method).
- **Rejected:** `TailCeiling.fresh` `* 1.0` (scope fix 1): no gain, the windowed profile put 0.4 bytes per block on
  `fresh` at HEAD, and on the final tree the delay reads 66 with it and 66 without (it moved other boxes on the stage
  it was first measured on). The resonator's split remedy on the delay (worse). A "nothing changed" short-circuit was
  not tried (coordinator, scope §2).
- **The compressor toggle, split** (`classic-remap16`: two equal gain maps alternated every 16 blocks): a new map
  costs about 1,310 bytes in the classic chain's re-resolve alone (C1), two equal compressor maps about 1,440 (the
  settings and their `configure`, C2 and C3, about 130), the toggle about 850 (an empty map resolves cheaper). The
  fade (C4) is not measurable. S1 takes about 180 bytes per new map off all three (toggle 54 to 43 bytes per block).
  A `CompressorSettings` per switch stays, by design.
- **Time:** within the control's noise on V8 (both bundles, old, old against old, new, 3 rounds, 28 cases) and on
  the JVM (the rows at the edge rerun with 7 rounds, the 200 ns idle and gain chains with ten times the blocks:
  1.015 and 1.005 against a control of 1.005); the partial bank faster on V8 (0.75 with drift, 0.91 to 0.92 without).
  The JVM allocates nothing per block in steady state, old and new (the engine rows show a fixed 13 or 26 KB per
  16,000-block run on both sides alike; the remap rows 1.5 to 5 bytes per block, the same old and new).
- **Proof:** the probe's 71 sound rows (the t12rB chain set, its engine scenarios, every perf case, the oscillator
  cases, the engine stage cases) and reviewer B's 35 (full voices through `PlaybackEngine`, hostile knob values,
  per-note stage changes) bit-identical old against new on JS development, JS production and the JVM; the step 12
  goldens (a: 2,064 lines, b: 2,532) and the step 11 stack golden (`r2`, both parts) identical; the corpus (label
  `ep1-v8b`): the 16 other rows identical to `corpus-ep1-before.txt`, Der Schmetterling from HEAD's text
  `94dfc72ae5637e91`, Kokon from `kokon-head.ks` `2f8d88cd2458fce2`. `:audio_be:jvmTest` (2,399) and
  `:audio_be:jsBrowserTest` (2,295) green.
- **Rows** (each mutation-checked, each red only on its own row under its mutant):
  - `KatalystSlotResolverSpec`, "a writer applied before its first resolve writes the authored values": the
    resonator, delay, reverb and phaser writers fill their holder when they are built. No engine path applies before
    it resolves today, so dropping that fill was green on every spec before (the resonator's since step 12 (a),
    review round 2).
  - `KatalystDelayEffectSpec`, "the four-number door hands time, feedback and cap to the line": the door dropping
    `cap` was green on every spec (reviewer A, NIT 4).
  - `KatalystPhaserEffectSpec`, "the cores run each block at the breakpoint the glide holds for that block": a
    centre or a sweep taken before the glide's advance lagged one block and was green on every spec, at HEAD too
    (reviewer A, NIT 5).
  Also red on existing specs: the delay writer filling feedback from the wet knob, the reverb writer dropping its
  lowpass, the phaser stage reading its dry coefficient after the advance, the eight-argument door dropping `wetFrom`.

### Found during the V8 allocation pass

- **`ConstantIgnitor.controlRateValueOrNull` is a heap number per read on V8** when the call is not inlined and the
  constant is non-integral (scope §4.5; logged, not this pass: it is an interface shape, `Double?`). Measured on the
  development bundle under a mixed profile through `blockStartValue` in the unison stacks, about 18 bytes per block.
  The `Ignitor.kt` KDoc that said "JS does not box" is corrected.
- **The plain wave oscillators box one heap number per block** on the production bundle, pinned (`WaveIgnitor`
  hands its non-integral `dt` to its loop methods), about 16 bytes per block per oscillator. The same argument class
  as the stages; not converted.
- **A drifting stack's ramp can box twice per voice per block after the fix** (221 to 515 bytes per block in some
  production mixed-profile processes, and in some development ones): `DriftLanes.startOf` and `endOf` return a
  double, and in those processes V8 did not inline `blendOf`. A holder for the ramp is the shape if it is wanted.
- **The PWM loop boxes one heap number per sample on the development bundle** under a mixed profile (about 2 KB per
  block, `pulze` with a duty signal): the per-sample `setPulseShape(duty = ...)` call. Not seen on the production
  bundle in isolation.
- **The superpluck allocates per block**, 450 to 700 bytes per block with 8 voices and drift, the pluck about 65, old
  and new alike; not located.
- **A new param map still costs about 1,130 bytes** on the classic chain after S1, which the profiler attributes to
  `KatalystKnob.resolve` on both bundles; its code (two field stores) does not explain it.
- **A noise voice allocates about 2 KB per block** at engine level (the probe's noise-plus-saw sound); not located.
- **Kotlin's `isFinite()` is a stdlib call on Kotlin/JS**, and V8 left it out of the stages' budget; replacing it was
  condition-dependent (above). Worth a second look only with a measurement that holds pinned and unpinned.

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
- **D10, the number overloads (maintainer, 2026-10-08): KEPT in main code.** "Keep the Double in the main code; this
  is also handy for the user and should be possible everywhere a constant value is accepted, otherwise the instrument
  authoring code becomes very verbose." So step 13 does not move them. It checks the other way round: wherever a door
  takes an Ignitor for a value that may be constant, a plain number works too (the Kotlin `IgnitorDsl` doors, the
  KlangScript doors through `IgnitorDslLike`, and the runtime `Ignitor` extensions), and fills the gaps it finds.
  The test-only seams (`*ForTest`, `currentState`, `installed*`) are a separate question and stay with A2.1.
- **The unison cap (maintainer, 2026-10-08): 256, not 64.** `UNISON_MAX_VOICES` becomes 256 (`FilterDef.kt`, later
  `_resource_bounds.kt` with step 12 (c)). Done 2026-10-08, after step 11 was committed. Cost to keep in mind: a superpluck string carries a 2,500-sample delay line, so 256
  strings build about 5 MB per note on the audio thread (64 built about 1.3 MB). No song comes near it (the largest
  count is 32).
- **Step 12 (a), body and vowel as one implementation (maintainer, 2026-10-08): agreed** ("I agree to unify the
  implementation"), retiring the "intentional un-deduped twins" review guardrail in the same commit. The maintainer
  asked whether an "a" sung through "wood" stays possible: yes, the chain keeps a body stage AND a vowel stage, two
  instances of the one class with their own slots and banks (`vowel("a").body(material = "wood")` runs both). The
  step's before-golden covers a vowel and a body on the same chain.
- **Step 11 won't-dos, confirmed (maintainer, 2026-10-08):** the 20 `IgnitorDsl` arithmetic wire types stay ("it
  would build a second-level discriminator, and I do not see the use for this"). `Param` and `Constant` stay separate
  on every level: the params are collected and exposed (useful for UI work later), and authors often want constants
  that are not exposed to the user. The one runtime place that treats them differently (`scaledBy`, the `passes`
  q ladder) only keeps the fused and the chained `passes` doors bit-identical for a non-finite q.


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
  starts at size 0 and grows at their first `process` (closed by step 12: the bank's scratch is sized at
  construction, the wrapper is gone), `KatalystDelayEffect` makes a `DelayLine` per ring rent, and
  `ScratchBuffers.oversample` looks its sub-pool up in a map per block (its first use per factor allocates, once per
  warehouse). All are per orbit or per backend, not per voice.
- **The active list's order still reaches the phase pool takes** (lifecycle step 5's finding above): unchanged by this
  step, which kept every draw at the first block.


## Found during tidy-up step 11

- **The stacks' analog drift allocates on V8, before and after step 11 alike** (step 11 (b) and (c), review round 1,
  reviewer B): supersaw-7 with drift 92 and supersaw-16 with drift 210 scavenges per 105,000 blocks; the JVM 0 bytes
  per block. Probably the class of the Karplus fix (a drift ramp seeded from a call result stays tagged on V8, step
  11 (b) and `audio/ref/performance.md`). Its own probe and fix, behaviour-neutral.

- **The Shape path allocates on V8, before and after this step alike** (review round 1, reviewer B): about 4 KB per
  block at stage 0 in the development JS build (52 scavenges per 105,000 blocks), 835 scavenges per 105,000 blocks at
  stage 4. Old and new code allocate the same, so step 11 did not cause it. It needs its own probe on the production
  bundle before anything is changed (`tmp/reviews/tidy11-r1-B.md`, "Outside this change").
