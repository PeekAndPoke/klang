# Klang Audio: memory

What is TRUE NOW in the audio engine (`audio_bridge`, `audio_be`, `audio_fe`, `audio_jsworklet`), each point
once, with a pointer to its home. Keep it that way: when a change lands, update the section it touches IN PLACE
(replace, do not append), add ONE line to History (date, a few words, the link to the task record), and put the
narrative, the measurements and the review story in the task record, which gets archived. A rule that needs more
than two lines lives in an `audio/ref/*.md` topic file or a class KDoc, and this file points there. The full dated
record up to 2026-09-29 is `audio/ref/memory-history.md` (read it only for the history of a decision). Restructured
2026-09-29; every statement below was checked against the code that day.

## The signal flow and its owners

- **Per playback.** `PlaybackEngineDispatcher` routes every `Cmd` by `playbackId` to its own `PlaybackEngine`
  (scheduler, orbits, registry forks, `MasterBus`); the engines' outputs sum into the house stage. File map:
  `docs/audio-backend-file-map.md`; data flow and isolation: `audio/ref/architecture.md`.
- **Instrument = the voice's Ignitor tree** (phase 3, done 2026-09-28). `Voice` runs Pitch, Ignite, (teardown
  fade), Send. Every built-in sound is `IgnitorRegistry.builtInVoice(source)` = `source.pregain().classic()`;
  every sample voice is the same shape over `IgnitorDsl.Sample` (`IgnitorRegistry.SAMPLE_INSTRUMENT`, never
  registered under a name). An authored instrument gets the voice doors by ending in `.classic()` as its LAST call
  (`IgnitorDsl.endsInClassic()`); a tree without it plays bare: no doors, no default envelope.
- **`classic()`** (`audio_bridge/.../IgnitorDslClassic.kt`): onepole, crush, coarse, distort, highpass,
  bandpass, notch, lowpass, tremolo, adsr. Its knobs are `<door>.<param>` slots the pattern fills through
  `VoiceData.oscParams`; a stage at its off value is not built. Detail: `audio/ref/voice-synthesis.md`.
- **The pitch stage stays outside the tree**: vibrato, accelerate, pitch envelope and FM in `voices/strip/pitch/`
  (moving in is `docs/tasks/future/pitch-pipeline-into-the-tree.md`).
- **Voice lifetime** = gate end plus the tree's own release tail (`VoiceFactory.treeLifetime`, floored at 0;
  `VOICE_ADSR_RELEASE_SEC` when the tree has no static answer). `TeardownFadeRenderer` runs unless the root is a
  built amplitude envelope with a static release (`BuiltIgnitor.endsInEnvelope`). A silent release is culled and
  stays a zombie until its end. All three: `audio/ref/voice-synthesis.md`.
- **Channel**: `gain` is the one level word (the fader, applied once with `pan` in `SendRenderer`); a frontend's
  `velocity` is multiplied into `gain` before the wire. The orbit is the routing.
- **Bus**: each orbit (`Cylinder`) runs a `KatalystChain`, born with `KatalystDsl.classic` (body, vowel, delay,
  reverb, phaser, compressor, gain; duck in a cross-orbit pass), knobs from the lease holder's
  `VoiceData.katalystParams`. Laws: `audio/ref/katalyst.md`; the classes: `audio/ref/effects-mixing.md`.
- **Master**: the same Katalyst chain at the output (`MasterBus`, `master(Katalyst(k => ...))`, off with
  `master(Katalyst())`). The Master DSL is retired.
- **House stage**: `MasterStage`: DC blockers, the limiter (-1 dB, 20:1, 5 ms lookahead, always on, not
  authorable; `MasterLimiterDefaults.kt`, `MasterStage.HOUSE_LIMITER_*`), then clip and interleave to 16-bit.
  The lookahead delays the whole output uniformly.
- **The wire** (`VoiceData`, `KlangCommLink`): pitch, gain, pan, routing, lifetime, and two slot maps; only seconds
  cross it, never cycles. `VoiceData.soundIndex` is the one variant channel (a sample bank's variant and
  `IgnitorDsl.Variants`, which picks `children[soundIndex.mod(size)]`). Fields: `audio/ref/data-model.md`.

## Laws and constants in force

- **Block size 128, pinned everywhere** (`AudioBackendContext.RENDER_QUANTUM_FRAMES`, whose KDoc lists what
  depends on it): it is a tone parameter; never change it to speed a render.
- **The envelope law** is `EnvelopeCore` (its KDoc). Voice defaults `VOICE_ADSR_*` (0.01, 0.1, 1.0, 0.05); curves
  are index knobs with the reader's default, both Exponential; `ADSR_EXP_K` 3.0; de-click `ENV_DECLICK_SECONDS`
  1 ms on `classic()`. Knobs and `Adsr.on`: `audio/ref/voice-synthesis.md`.
- **The gate**: a stage whose gating knob is a leaf at its off value (or unset) is not built. The rule's text is
  the `gatedOff` KDoc in `IgnitorDslRuntime.kt`; the values are `audio/ref/off-values.md`, their one home.
- **The swap law** `ChainSwap` (both hosts): fade the leaving chain's input over 0.06 s, drain it at full weight,
  at most `MAX_DRAIN_SECONDS` 20 s. **The release law** `TailRelease`: 60 dB per 3 s from exactly 1, retired
  under -90 dB. **A stopped playback is never hard-cut**; only an endless tail triggers the release, 20 s after the
  last note (`PlaybackEngine.isIdle` KDoc). All in `audio/ref/katalyst.md`.
- **Knob glide** `KNOB_GLIDE_SECONDS` 0.05 in whole blocks (17 at 44.1 kHz, 19 at 48 kHz); **bank crossfade**
  `BANK_CROSSFADE_SECONDS` 0.02 with two banks and one parking slot. Which knob glides how:
  `audio/ref/katalyst.md`.
- **Authored lookahead** (Katalyst `compressor` / `limiter`): build-time, at most
  `Compressor.MAX_LOOKAHEAD_SECONDS` 0.05, uncompensated by the author's choice; the authored limiter defaults to
  lookahead 0 and a 1 ms attack (`AUTHORED_LIMITER_*`).
- **Analog drift**: the pitch drift is 1 cent per unit `analog` (`ANALOG_FAST_PEAK_CENTS` 0.2 plus
  `ANALOG_SLOW_PEAK_CENTS` 0.8); the filter cutoff drifts twice as far, `FILTER_DRIFT_RELATIVE_TO_OSC` 2.0 (by ear,
  2026-09-29); the voice-to-voice cutoff tolerance is `FILTER_CUTOFF_OFFSET_PER_ANALOG` (0.0002), the saturating
  SVF branch's drive `FILTER_DRIVE_PER_ANALOG` (0.25) (`audio_bridge/constants/FilterHumanizationDefaults.kt`).
  Every lane steps once per block and ramps across it.
- **`DriftLanes`** gives every multi-voice oscillator its drift; `analogSpread` 0 is one shared walk, 1 (default)
  a lane per voice, and both endpoints are exact.
- **The voice rng**: one stream per voice, and the draw ORDER is part of the sound (`audio/ref/voice-synthesis.md`,
  "The voice rng").
- **Silence culling**: a voice in its release whose output stays under `VOICE_CULL_FLOOR` (=
  `ORBIT_SILENCE_FLOOR`, 1e-5) for `VOICE_CULL_SECONDS` (0.05) stops rendering; never in the gate, never before it
  was heard, and not with a tremolo on the output unless `cull` is set.
- **The optimizer's promise** is `OPTIMIZER_PARITY` (1e-12 relative to the block's loudest sample), not bit
  identity; every registered tree renders optimized. A synthesized coefficient may be zero only when the authored
  one was. Open items: `docs/tasks/future/ignitor-optimizer-open-items.md`.
- **Numerical contract**: `SAFE_MIN` 1e-15 / `SAFE_MAX` 1e15; "safe" means finite, not small, so a consumer must be
  O(1) at any magnitude. A divisor of exactly zero yields zero (`Recip` excepted). A non-finite value a pattern
  writes reads as unset, at each reader once. All in `audio/ref/numerical-safety.md`.
- **Catalogues are index spaces, append only**: `DistortionShapes`, `LfoShapes`, `AdsrCurves`, `BodyMaterials`,
  `VowelBands` (audio_bridge). The index is the wire encoding; out of range or non-finite is index 0 for the
  shapes. Guards: `ShapeCatalogueSpec`, `CatalogueIndexSpec`, `AdsrCurvesSpec`.
- **Build-time knobs** (shapes, the oversample factor, `passes`, curves, tremolo shape and phase) are read once,
  leaf-only; a non-leaf takes the default and is not built. `Oversampler.factorOf`: non-finite is 0, a fraction
  truncates, no upper clamp (the D7 stopgap until `docs/tasks/oversampling-regions.md`).
- **`pregain`** is an ordinary slot (`Param("pregain", 1.0)`) on the source, before every nonlinearity. It changes
  timbre only where a nonlinearity follows; a saturating shaper driven hard makes it inert, on a wavefolder it is
  the fold depth. A `mul` slot's default must be a safe literal: unset is NOT off for `mul`.
- **Distortion**: `classic()`'s distort is the fused `IgnitorDsl.Distort` running `DistortionCore` (drive inside
  the oversampler, DC blocker, no cap; a modulated amount at or below 0 runs at unity drive). The `distort` and
  `shape` doors build `Shape(Drive(...))`, bounded to +-1 by `ShapingFuncs.softCap`. `CrushCore` floors. Guard for
  both laws: `StripLawCoresSpec`.
- **The sample instrument**: a sample voice is bit-identical to the built-in `sine` when it plays the sine's own
  output at rate 1.0 (`SampleInstrumentSpec`, the oracle that outlived the strip). Its playback knobs `begin`,
  `end`, `speed`, `loop` are slots; `n` and `cut` stay wire fields.

## Guardrails and traps still in force

The list a reviewer pastes is `.claude/skills/review-loop/audio-constraints.md`; the project-wide guardrails
(block size, reverb `+ ANTI_DENORMAL`, OnePole HPF bias, the house limiter, script-door literal defaults, `min`/`max`
crossing) are in `CLAUDE.md`. Not repeated here. In addition:

- **The SVF**: bandpass, notch and the resonators are linear. Lowpass and highpass at `analog > 0` take a
  state-dependent DAMPING path (a diode-pair term grows `k` with the state; `IgnitorFilters.kt`, `Ignitor.svf`).
  Never saturate by capping the feedback signal with tanh: two such attempts went unstable and were reverted
  (2026-05-28, history).
- **The tube shape's constants are Pade-consistent** with `fastTanh` (`ShapingFuncs.kt`), so `tube(0)` is exactly
  0; recompute them if `fastTanh` changes.
- **The unity-`mul` fold drops a `safeOut`** only over a signal survivor; in a parameter position the clamp does
  work and the fold does not fire (`survivesUnityFold`). The Karplus family's raw `decay` is authored character.
- **A culled voice is a zombie** (keeps its active-list slot and renews its orbit lease): any change to WHEN a
  voice leaves `active` changes which voice owns an orbit, and so the mix.
- **Do not remove the past-cutoff in `VoiceScheduler.promoteScheduled`**: it stops `ReplaceVoices` from
  re-promoting voices that already played. `replaceVoices` dedups against active voices
  (`ScheduledVoice.isDuplicate`); the frontend's resync grace window is 0.2 s (`KlangPatternScheduler`).
- **State lives at the granularity it is bound to**: playbackId-bound state in `PlaybackEngine` (and the
  per-playback controller on the frontend), global state in `AudioBackendContext` / `KlangPlayer`. Share only what
  is expensive to recreate and safe to outlive a playback (samples, the built-in instruments). The `CLAUDE.md`
  cleanup rule applies to every new map.
- **The Katalyst traps** (a same-value retarget must be free; a per-block retarget low-passes the knob; a config
  cache compares substituted values; every door into a terminal state sets the same precondition):
  `audio/ref/katalyst.md`.
- **`phaseMod` save and restore has no try/finally** (`ModApplyingIgnitor`, `ModBlockingIgnitor`): accepted, an
  exception on the audio thread is fatal anyway.
- **The click-hunt harness** `GuitarClickHuntTest` is tagged `ClickHunt`, out of the default run
  (`./gradlew :audio_be:jvmTest -Pkotest.tags=ClickHunt --tests io.peekandpoke.klang.audio_be.ignitor.GuitarClickHuntTest`);
  add a setup to it for every new click symptom or ignitor archetype.
- **KlangScript doors forbid mixing positional and named arguments**: `distort(amount = 0.5, oversample = 4)`.
- **Platform facts**: `KlangTime.internalMsNow()` is monotonic, not wall clock; the JS targets compile to
  ES2015 classes (an `AudioWorkletProcessor` must extend a real class); `MonoSamplePcm` is always mono, stereo
  happens at pan and mix; a null `VoiceData` field means "the engine default".
- **Proving a change**: identity renders in raw doubles with an engagement control, a metric floor before a
  measurement, an oracle spec beside every parity spec: `audio/ref/verification.md`. Benchmarks and the fast-math
  contracts (wrap a phase before `fastSin`): `audio/ref/performance.md`.

## Open threads

- **By ear** (`docs/tasks/by-ear/README.md`): `phase3-end-checkpoint.md` (retire or regenerate
  `ClassicVoiceBaselineSpec` and `BuiltInVoiceMatrixSpec`, the strip's frozen sound), `chain-swap-request-during-drain.md`,
  `duck-orbit-switch-click.md`, `c3-depth-migration-flags.md`, and the owed rounds listed there.
- **Open, correctness**: `docs/tasks/audit-audio-backend-leftovers.md`,
  `docs/tasks/bugfix-ignitor-non-finite-pitch-amount.md`, `docs/tasks/svf-coefficient-cache-never-engages.md`.
- **Scheduled or designed**: `docs/tasks/oversampling-regions.md`, `docs/tasks/master-dsl-followups.md`,
  `docs/tasks/katalyst-master-configure-doors.md`, `docs/tasks/pluck-release-tail.md`,
  `docs/tasks/voice-takeover.md` (blocked on a design decision), `docs/tasks/playback-layer-decomposition.md`.
- **Katalyst, future**: `one-chain-host.md`, `delay-ceiling-edges.md`, `transition-times.md`,
  `ducking-unfinished.md`, `general-eq-core.md`, `flanger-chorus.md`, `idea-master-saturation.md` (all in
  `docs/tasks/future/`). A wide rising compressor-threshold swing sits about 16 to 21 dB above its floor, a law
  decision left open (`docs/plans/knob-glide.md`).
- **Voice and instruments, future**: `pitch-pipeline-into-the-tree.md`, `svf-resonator-class-collapse.md`,
  `envelope-shape-followups.md`, `new-oscillators.md`, `onepole-highpass-door.md`, `tremolo-rate-naming-parity.md`,
  `cut-group-semantics.md`, `live-voice-modulation.md`, `soundfont-zone-selection.md`, `string-slot-readers.md`.
- **Engine, future**: `ignitor-optimizer-open-items.md`, `optimize-affine-chain-fusion.md`,
  `optimize-constant-control-fast-path.md`, `audit-parked-decisions.md`, `worklet-clock-divergence.md`,
  `high-performance-audio-backend.md` (parked behind the sound-first priorities).
- **Never measured**: the build-time cost of the gate's ON path (the per-block numbers are in the history's gate
  entry).

## History

One line per step, newest first. A link to the archived task record where one exists, else to the entry in
`audio/ref/memory-history.md`. "Superseded" marks an entry whose rules no longer hold as written.

- 2026-09-29 Filter drift is twice the pitch drift, by ear: [record](../docs/tasks-archive/2026-09/20260929-analog-drift-ratio-tuning.md)
- 2026-09-28 The master is a Katalyst at the output; the Master DSL retires (phase 3 step 12): [record](../docs/tasks-archive/2026-09/20260928-phase3-step12-master-as-katalyst.md)
- 2026-09-27 The voice strip and the Pipeline DSL retire; every voice is its tree (step 9): [entry](ref/memory-history.md#the-voice-strip-and-the-pipeline-dsl-retire-every-voice-is-its-tree-phase-3-step-9-2026-09-27)
- 2026-09-26 Authored instruments ending in `classic()` leave the strip (step 10 c1; superseded by step 9): [entry](ref/memory-history.md#authored-instruments-ending-in-classic-leave-the-strip-phase-3-step-10-commit-1-2026-09-26)
- 2026-09-26 The sample instrument (step 7): [entry](ref/memory-history.md#the-sample-instrument-the-strip-off-for-samples-phase-3-step-7-2026-09-26)
- 2026-09-26 The built-ins on `classic()` (step 6; the strip parts superseded): [entry](ref/memory-history.md#the-built-ins-on-classic-the-strip-off-for-them-phase-3-step-6-2026-09-26)
- 2026-09-25 One envelope law, `EnvelopeCore` (D3): [entry](ref/memory-history.md#one-envelope-law-envelopecore-for-every-envelope-phase-3-d3-commit-a1-2026-09-25)
- 2026-09-25 One crush law, one distort loop (step 4): [entry](ref/memory-history.md#one-crush-law-one-distort-loop-d1-and-d2-landed-phase-3-step-4-2026-09-25)
- 2026-09-25 `classic()` as a slotted tail, the filter envelope's slot-layer fill (step 5): [record](../docs/tasks-archive/2026-09/20260928-builtin-instruments.md)
- 2026-09-25 One adsr shape, curve index knobs, an on switch, one exp bend (step 3c): [record](../docs/tasks-archive/2026-09/20260928-builtin-instruments.md)
- 2026-09-25 One tremolo law, index shapes, a build-time factor (step 3b): [record](../docs/tasks-archive/2026-09/20260928-builtin-instruments.md)
- 2026-09-20 Two banks, one parking slot, 20 ms: the filter swap after the morph (Katalyst 5c-11): [entry](ref/memory-history.md#two-banks-one-parking-slot-20-ms-the-filter-swap-after-the-morph-was-rejected-2026-09-20)
- 2026-09-20 The filter nodes grew a cutoff envelope and a per-voice lane (step 3a): [record](../docs/tasks-archive/2026-09/20260928-builtin-instruments.md)
- 2026-09-20 The gate: a stage at its off value is not built (step 2): [record](../docs/tasks-archive/2026-09/20260928-builtin-instruments.md)
- 2026-09-20 A material change morphs the resonator bank (5c-10; superseded, rejected by ear): [record](../docs/tasks-archive/2026-09/20260928-katalyst-dsl.md)
- 2026-09-20 The phaser and the duck switch and change without clicking (5c-9): [entry](ref/memory-history.md#the-phaser-and-the-duck-switch-and-change-without-clicking-2026-09-20)
- 2026-09-19 An orbit never deactivates while a voice plays on it; the fader glides (5c-8): [entry](ref/memory-history.md#an-orbit-never-deactivates-while-a-voice-plays-on-it-the-fader-glides-2026-09-19)
- 2026-09-19 The orbit compressor switches and changes by gliding (5c-7): [record](../docs/tasks-archive/2026-09/20260928-katalyst-dsl.md)
- 2026-09-19 Body, vowel and the orbit EQ fade from what sounds now (5c-6; its 10-bank pool superseded by 5c-11): [record](../docs/tasks-archive/2026-09/20260928-katalyst-dsl.md)
- 2026-09-19 The tail ceiling never under-reports a delay tail (5c-5): [entry](ref/memory-history.md#the-tail-ceiling-never-under-reports-a-delay-tail-2026-09-19)
- 2026-09-19 The orbit-bus fields left the wire (5b-3): [entry](ref/memory-history.md#the-orbit-bus-fields-left-the-wire-2026-09-19)
- 2026-09-19 The orbit delay and reverb are insert-style stages (5b-2): [entry](ref/memory-history.md#the-orbit-delay-and-reverb-are-insert-style-stages-2026-09-19)
- 2026-09-19 The filter swap as a state machine (5c-4; superseded by 5c-6 and 5c-11): [entry](ref/memory-history.md#the-filter-swap-is-a-state-machine-off-engaged-crossfading-2026-09-19)
- 2026-09-19 Body and vowel run on one resonator bank (5c-3): [entry](ref/memory-history.md#body-and-vowel-run-on-one-resonator-bank-2026-09-19)
- 2026-09-19 The orbit reverb is a state machine (5c-2): [entry](ref/memory-history.md#the-orbit-reverb-is-a-state-machine-too-2026-09-19)
- 2026-09-19 Orbit knobs glide: the pilot on the reverb size: [entry](ref/memory-history.md#orbit-knobs-glide-the-pilot-on-the-orbit-reverb-2026-09-19)
- 2026-09-19 An effect lifecycle as a state machine, the delay as template (5c-1): [entry](ref/memory-history.md#an-effect-lifecycle-as-a-state-machine-the-delay-is-the-template-2026-09-19)
- 2026-09-19 The born-with chain is slot-driven (5b-1): [entry](ref/memory-history.md#the-born-with-chain-is-slot-driven-one-way-a-bus-knob-reaches-a-stage-2026-09-19)
- 2026-09-19 The pregain slot, the `Param` leaf guard, the orbit's group fader: [entry](ref/memory-history.md#the-pregain-slot-the-leaf-guard-and-the-orbits-group-fader-2026-09-19)
- 2026-09-19 The wire carries one level word: [entry](ref/memory-history.md#the-wire-carries-one-level-word-2026-09-19)
- 2026-09-16 The ledger, a per-instrument benchmark harness: [entry](ref/memory-history.md#the-ledger-a-per-instrument-harness-for-every-optimization-round-2026-09-16)
- 2026-09-15 The optimizer's promise is a margin: [record](../docs/tasks-archive/2026-09/20260916-ignitor-optimizer-arithmetic-folds.md)
- 2026-09-15 div, minus and neg fold; a zero divisor is zero: [entry](ref/memory-history.md#div-minus-and-neg-fold-a-zero-divisor-is-zero-2026-09-15)
- 2026-09-15 The oversampler's decimator is polyphase: [entry](ref/memory-history.md#the-oversamplers-decimator-is-polyphase-and-indexed-2026-09-15)
- 2026-09-15 Analog drift steps per block and ramps across it: [entry](ref/memory-history.md#analog-drift-steps-per-block-and-ramps-across-it-2026-09-15)
- 2026-09-15 Polynomial e^x in the envelopes and the compressor, no fastLn: [entry](ref/memory-history.md#polynomial-ex-in-the-envelopes-and-the-compressor-no-fastln-2026-09-15)
- 2026-09-15 Table-and-polynomial 2^x in the pitch paths: [entry](ref/memory-history.md#table-and-polynomial-2x-in-the-pitch-paths-2026-09-15)
- 2026-09-15 Polynomial sine in the oscillators: [entry](ref/memory-history.md#polynomial-sine-in-the-oscillators-2026-09-15)
- 2026-09-15 Silence culling: [record](../docs/tasks-archive/2026-09/20260915-voice-culling.md)
- 2026-09-10 One drift-lane container, `DriftLanes`: [record](../docs/tasks-archive/2026-09/20260910-drift-lanes-analog-spread.md)
- 2026-09-07 Loop shape beats block-pass count: [entry](ref/memory-history.md#loop-shape-beats-block-pass-count-2026-09-07-sine-partial-banks)
- 2026-07-04 Body and vowel resonators on the orbit, live-update fixes: [entry](ref/memory-history.md#body--vowel-resonators--live-update-fixes-2026-07-04)
- 2026-07-04 Ignitor ADSR knobs, declick and expK as slots (expK superseded by 3c): [entry](ref/memory-history.md#ignitor-adsr-knobs--declick--expk-as-slots-2026-07-04)
- 2026-07-03 Song-level CPU benchmark and the Der Schmetterling deep-dive: [entry](ref/memory-history.md#song-level-cpu-benchmark--der-schmetterling-deep-dive-2026-07-03)
- 2026-06-08 The VCA gain de-click smoother (superseded: the strip VCA retired): [entry](ref/memory-history.md#vca-gain-de-click-smoother-2026-06-08)
- 2026-06-05 The oscillator engine unified: [record](../docs/tasks-archive/2026-06/20260605-oscillator-engine-unification.md)
- 2026-05-28 The filter saturation dead end (its "purely linear" superseded, see the SVF guardrail): [entry](ref/memory-history.md#filter-saturation-dead-end--linear-svf-is-the-right-choice-2026-05-28)
- 2026-05-25 The sprudel-side scale and variant split (superseded here, a sprudel matter): [entry](ref/memory-history.md#sprudel-side-scale--variant-split-2026-05-25)
- 2026-05-22 Ignitor variants dispatch on `soundIndex`: [record](../docs/tasks-archive/2026-05/20260522-ignitor-variants.md)
- 2026-05-21 The distortion shape catalogue extension: [entry](ref/memory-history.md#distortion-shape-catalog-extension-2026-05-21)
- 2026-04-27 The numerical safety contract and the distort DC lock (the `IgniteRenderer` clip superseded): [entry](ref/memory-history.md#numerical-safety-contract--distort-dc-lock-fix-2026-04-27)
- 2026-03-26 Ignitor param slots, "everything is a signal" (superseded as written): [entry](ref/memory-history.md#ignitor-param-slots--everything-is-a-signal-2026-03-26)
- 2026-03-23 The BlockRenderer pipeline (superseded: the strip retired): [entry](ref/memory-history.md#blockrenderer-pipeline-architecture-2026-03-23)
- 2026-03-19 The Ignitor composable architecture (superseded as written): [entry](ref/memory-history.md#ignitor-composable-architecture-2026-03-19)
- Undated sections of the old file, folded into the sections above: [Current Status](ref/memory-history.md#current-status), [Architecture Decisions](ref/memory-history.md#architecture-decisions), [Lessons Learned](ref/memory-history.md#lessons-learned)
