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
  (scheduler, orbits, registry forks, `MasterBus`); the engines' outputs sum into the house stage. A
  `VoiceScheduler` serves its engine's one playback: one `PlaybackCtx`, made by the first voice, dropped by
  `cleanup`, nothing filtered by id. File map:
  `docs/audio-backend-file-map.md`; data flow and isolation: `audio/ref/architecture.md`.
- **Instrument = the voice's Ignitor tree** (phase 3, done 2026-09-28; its pitch modulations too since pitch pipeline
  steps 1 to 4, the last, `fm`, 2026-10-10; step 5 removed the empty shell in front of it). `Voice` runs Ignite, (teardown fade), Send; the stages and `BlockContext` live in `voices/`. Every built-in sound is `IgnitorRegistry.builtInVoice(source)` = `source.pregain().classic()`;
  every sample voice is the same shape over `IgnitorDsl.Sample` (`IgnitorRegistry.SAMPLE_INSTRUMENT`, never
  registered under a name). An authored instrument gets the voice doors by ending in `.classic()` as its LAST call
  (`IgnitorDsl.endsInClassic()`); a tree without it plays bare: no doors, no default envelope.
- **`classic()`** (`audio_bridge/.../IgnitorDslClassic.kt`): fm, pitch envelope, accelerate, vibrato (on the source, the
  pitch stages' place, FM innermost), onepole, crush, coarse, distort, highpass, bandpass, notch, lowpass, tremolo, adsr. Its knobs are `<door>.<param>` slots the pattern fills through
  `VoiceData.ignitorParams`, the param part the engine door's word (`adsr.attack`, `crush.bits`,
  `coarse.factor`); a stage at its off value is not built. Detail: `audio/ref/voice-synthesis.md`.
- **One word per knob, node to wire** (Q21, 2026-10-09): every envelope says `attack`, `decay`, `sustain`,
  `release` (the unit in the KDoc, not the name) and `declick`; the pluck's loop gain is `feedback`, brown noise's
  white leak is `leak`. The frame-domain core keeps `sustainLevel` (`EnvelopeCore.prepare`)
  and the constants keep their `*_SEC` names. Old names: `docs/retired-names.md`.
- **The pitch doors are in the tree** (`docs/tasks/pitch-pipeline-into-the-tree.md`, steps 1 to 4): sprudel's
  pitch envelope, accelerate, vibrato and fm are `classic()`'s `PitchEnvelope`, `Accelerate`, `Vibrato` and `Fm`
  stages, filled by the `penv.*` / `penvCurves.*` (step 1), `vibrato.*` (step 2), flat `accelerate` (step 3) and
  `fm.*` (step 4) slots; nothing runs in front of the tree (step 5): the root's `IgniteContext.phaseMod` is null, and
  `ModApplyingIgnitor` is the one source of a non-null `phaseMod` (`ModBlockingIgnitor` only clears and restores it). FM is innermost, so the other
  classic pitch stages move its modulator with the carrier (step 3b's rule), and an `fm` in the instrument sits inside
  its carrier and follows it (`s("sgbell").fm(...)`). The classic FM is the node's law, NOT the strip's sound (decision
  D3): the depth envelope per sample (ledger E11 closed), an envelope-free door keeps its depth through the release
  tail (the strip collapsed it at the gate), the rounding order (at most 7.6e-15 at the output before the gate), the
  modulator sine's rng draw (a pitched voice that also draws while it renders, a supersaw, a pluck, `analog > 0`,
  takes other random values; a noise-only voice is untouched), the modulator following `vib` / `penv` / `accelerate`, and over `sgpad`'s forking detune one
  modulator for both pitches, block-size dependent (ledger E8: at 128 frames the pad loses its pitch; recorded, kept
  quiet, `sgpad` the only built-in with that shape).
- **Accelerate glides over the GATE and holds** (decision D2, with its hold, 2026-10-09): `2^(semitones / 12 *
  progress)` from the onset to the gate close (`IgniteContext.voiceDurationFrames`, which a note-off never moves),
  then the target through the release, for both doors; a gate of 0 frames holds the target from the first frame
  (Q27). The strip glided over the scheduled end (release tail
  included), so every sprudel `accelerate` under a release tail changed in step 3 (in the corpus only Kokon's
  `strike`); the Ignitor node rose on past the gate before the hold (an authored `accelerate` under a release tail,
  after the gate, no corpus song).
- **Voice lifetime** = gate end plus the tree's own release tail (`VoiceFactory.treeLifetime`, floored at 0;
  `VOICE_ADSR_RELEASE_SEC` when the tree has no static answer). `TeardownFadeRenderer` runs unless the root is a
  built amplitude envelope with a static release (`BuiltIgnitor.endsInEnvelope`). A silent release is culled: the
  voice ends at once (no zombie since lifecycle step 5). All three: `audio/ref/voice-synthesis.md`.
- **Voice lifecycle** = the state machine `Voice.State` (Pending, Sounding, Releasing, Fading, Done; Fading and
  Done terminal), advanced per block in `Voice.render`, which dispatches on it with an exhaustive `when` (the `Voice`
  KDoc). A sealed type: the states without data are `data object`s, `Releasing` (the cull's silence count) and
  `Fading` (the cut's fade window) are classes, one instance each created with the voice; a transition is
  `state = x.enter(...)`, so none allocates (the rule of `docs/plans/effect-state-machines.md` §1). The one list of
  the transitions is the table in the `Voice` class KDoc. A culled voice
  is Done at its cull block (`Voice.culled`, a latch for the scheduler's count). Onset, gate end and end live in ONE place,
  `VoiceLimits` (the voice owns and writes it, `releaseGate` included; the stages read it via `BlockContext.limits`;
  the ignite stage derives `IgniteContext.gateEndFrame` from it per block). Events from outside are `Voice` methods
  that decide by the state: the note-off (`releaseGate`, applies to Pending and Sounding) and the hard kill
  (`kill`, Done from any state), and the cut (`cutOff`: a sounding voice turns Fading, a 4 ms linear ramp to exact
  zero from the cutting voice's onset, before its send, `CUT_FADE_SECONDS`, its window held by `Fading`; a silent
  one is Done at once). The
  scheduler removes only Done voices, always keeping the list's order, by one allocation-free compaction pass,
  `retainInOrder` (`removeDoneVoices` between blocks, and the render loop).
  Plan and steps: `docs/tasks-archive/2026-10/20261007-voice-lifecycle-state-machine.md` (steps 1 to 5b done).
- **Channel**: `gain` is the one level word (the fader, applied once with `pan` in `SendRenderer`); a frontend's
  `velocity` is multiplied into `gain` before the wire. The orbit is the routing.
- **Bus**: each orbit (`Cylinder`) runs a `KatalystChain`, born with `KatalystDsl.classic` (body, vowel, delay,
  reverb, phaser, compressor, gain; duck in a cross-orbit pass), knobs from the owner's
  `VoiceData.katalystParams`: the orbit's bus settings are owned by the newest `Sounding` voice; a voice gives the
  orbit up when its gate closes or it is cut (lifecycle step 5: the block's newest offer, committed once per block
  by `Cylinders.processAndMix`; an ownerless orbit keeps its last settings). Laws: `audio/ref/katalyst.md`; the classes: `audio/ref/effects-mixing.md`.
- **Reverb**: one room for both ears: each side's combs are fed `(L + R) / 2` (`Reverb.CROSS_FEED` 0.5, by ear
  2026-09-30); an input with equal sides feeds what it did before, bit for bit. A Freeverb tail only: no pre-delay,
  no early reflections (`docs/tasks/future/reverb-models.md`).
- **Master**: the same Katalyst chain at the output (`MasterBus`, `master(Katalyst(k => ...))`, off with
  `master(Katalyst())`). The Master DSL is retired.
- **House stage**: `MasterStage`: DC blockers, the limiter (-1 dB, 20:1, 5 ms lookahead, always on, not
  authorable; `MasterLimiterDefaults.kt`, `MasterStage.HOUSE_LIMITER_*`), then the clip into the engine's output:
  a floating-point `StereoBuffer`, no quantisation (in `[-1, 1]` a sample passes, above 1 is 1.0, else -1.0). The
  worklet copies it straight into Web Audio; only the JVM line and the WAV writer go to 16 bits, at their edge
  (`writePcm16` in `_pcm16_edge.kt`, the one home). The lookahead delays the whole output uniformly.
- **The wire** (`VoiceData`, `KlangCommLink`): pitch, gain, pan, routing, lifetime, and two slot maps; only seconds
  cross it, never cycles. `VoiceData.soundIndex` is the one variant channel (a sample bank's variant and
  `IgnitorDsl.Variants`, which picks `children[soundIndex.mod(size)]`; an empty one is silence everywhere, so as a
  Katalyst bus knob it reads 0.0, not the knob's default). Fields: `audio/ref/data-model.md`.
- **A shared node stays one node across the wire** (`@WireShared` on `IgnitorDsl`, 2026-10-10): the generated codec
  encodes and decodes each instance once per message, and `postMessage`'s structured clone keeps the sharing, so a
  `let` used twice is built once in the browser as on the JVM (the build cache keys by identity). Before, the browser
  built it twice (Kokon's and Der Schmetterling's Screamer pedal: two strings). Equal but distinct nodes stay two.

## Laws and constants in force

- **Block size 128, pinned everywhere** (`AudioBackendContext.RENDER_QUANTUM_FRAMES`, whose KDoc lists what
  depends on it): it is a tone parameter; never change it to speed a render.
- **The envelope law** is `EnvelopeCore` (its KDoc). Voice defaults `VOICE_ADSR_*` (0.01, 0.1, 1.0, 0.05); curves
  are index knobs with the reader's default, both Exponential; `ADSR_EXP_K` 3.0; de-click `ENV_DECLICK_SECONDS`
  1 ms on `classic()`. Knobs and `Adsr.on`: `audio/ref/voice-synthesis.md`.
- **The gate**: a stage whose gating knob is a leaf at its off value (or unset, except where the table says
  otherwise) is not built; since 2026-10-07 the pitch arms too, five now (vibrato, accelerate, pitch envelope, fm, and
  `pitchModSemitones` at a literal mod since 7a). The
  rule's text is the `gatedOff` KDoc in `IgnitorDslRuntime.kt`; the values are `audio/ref/off-values.md`, their one home.
- **The swap law** `ChainSwap` (both hosts): fade the leaving chain's input over 0.06 s, drain it at full weight,
  at most `MAX_DRAIN_SECONDS` 20 s. **The release law** `TailRelease`: 60 dB per 3 s from exactly 1, retired
  under -90 dB. **A stopped playback is never hard-cut**; only an endless tail triggers the release, 20 s after the
  last note (`PlaybackEngine.isIdle` KDoc). All in `audio/ref/katalyst.md`. **An engine's end of life** is one
  `PlaybackEngine.Phase` (Playing, Stopped, Releasing, Released, Disposed; the table in its class KDoc); the
  dispatcher keeps only the render order and the disposal order, both exact.
- **Knob glide** `KNOB_GLIDE_SECONDS` 0.05 in whole blocks (17 at 44.1 kHz, 19 at 48 kHz); **bank crossfade**
  `BANK_CROSSFADE_SECONDS` 0.02 with two banks and one parking slot. The bank crossfade and the compressor's switch
  fade run one linear law, `utils/linear_crossfade.kt` (the chain swap's `Crossfade` and the duck's glide are other
  laws). Which knob glides how: `audio/ref/katalyst.md`.
- **Body and vowel** are one stage class with two kinds (`KatalystResonatorEffect`, `ResonatorKind`); a chain runs
  both stages as two instances. One table per catalogue index (`ResonatorTables`, equal rows share an instance, so a
  switch among aliases installs nothing); a change installs into the pooled pair nobody hears (two pairs, built at
  the stage's first install), from zero state: no allocation per change, and a sounding bank never retunes.
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
- **Two pitch laws, two words** (decision D8, pitch pipeline 7a): `pitchModSemitones(mod)` is `2^(mod / 12)`, the law
  of `vibrato`, `accelerate` and `pitchEnvelope` as a primitive (`fastExp2`, `safeOut`); `pitchMod(mod)` is the linear
  `1 + mod`, FM's law, raw. The `vibrato` node is composed from it since 7b (the tremolo's pattern):
  `pitchModSemitones(sine(rate, analog = 0) * max(semitones, 0))`, the depth per sample; not bit-identical (one
  rounding, and the sine's drift-seed draw shifts the voice's later dice); `audio/ref/voice-synthesis.md`, its edge
  rules in the `vibrato` row of `audio/ref/off-values.md`. Since 7c the LFO takes `range(from, to)` before the depth
  and the sine's own `phase`; literals at the defaults `(-1, 1)` and 0 build neither (`classic()`'s slots included),
  so the default keeps 7b's bits. `pitchModSemitones` is gated at a literal 0 or non-finite mod, `pitchMod` is not
  (`audio/ref/off-values.md`); nesting multiplies, the outer ratio the left factor. The table:
  `audio/ref/voice-synthesis.md`, "The pitch nodes and their two laws".
- **Oscillator phase** (`PhaseOffset`, `docs/tasks-archive/2026-10/20261006-oscillator-phase-knob.md`): every periodic oscillator has a `phase`
  input in cycles, wrapped to `[0, 1)`, no clamp. The literal 0 builds no input (the default renders as before, bit for
  bit); a block-constant one moves the accumulator by its change once per block (the per-sample loops untouched); a
  signal takes the oscillator's phased loop, which reads the shape at `accumulator + offset` per sample. A stack
  shifts every voice alike; the impulse spikes where the shifted phase passes 0 going forward.
- **Tremolo range**: `Tremolo.rangeFrom` / `rangeTo` (`TREMOLO_RANGE_FROM` / `_TO`, -1 and 0) place the LFO swing,
  gain `1 + depth * (from..to)`; the literal default builds the classic `range(1 - depth, 1)` itself.
- **The voice rng**: one stream per voice, and the draw ORDER is part of the sound (`audio/ref/voice-synthesis.md`,
  "The voice rng").
- **Silence culling**: a voice in its release whose output stays under `VOICE_CULL_FLOOR` (=
  `SILENCE_FLOOR`, 1e-5) for `VOICE_CULL_SECONDS` (0.05) stops rendering; never in the gate, never before it
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
- **Build-time knobs** (shapes, the oversample factor, `passes`, curves, the tremolo shape) are read once,
  leaf-only; a non-leaf takes the default and is not built. `Oversampler.factorOf`: non-finite is 0, a fraction
  truncates, no upper clamp (the D7 stopgap until `docs/tasks/oversampling-regions.md`).
- **Solo**: background gain `1 - max(live amounts)` (`solo(1.0)` is exact silence, `solo()` is 0.95); `SoloTracker` (per
  playback, fixed arrays) records "soloed at a until t" from any event, control events included; live = `end + 4 blocks >
  now`, protected = `end + SOLO_HOLD_SEC > now` for every voice of the source, and a voice soloed itself (its own amount
  positive and finite, `ActiveVoice.soloed`) for its whole life (a long release beside another solo, Q14, Q28); `SOLO_HOLD_SEC >= SOLO_RAMP_SEC` (guard: `VoiceSchedulerSoloCutSpec`). Realtime voices have no
  control events: each one whose gate is open records its source until the block's end, so a realtime solo follows the
  held gates.
- **Resource counts are capped, tones are not**: `coercePasses` (1 to 16), `coerceUnisonVoices` (0 to
  `UNISON_MAX_VOICES` = 256 since 2026-10-08, non-finite is 0) and `coerceSinePartials` (a sine plays its first
  `SINE_MAX_PARTIALS` = 256 explicit partials, the rest are not built), all in `audio_bridge/_resource_bounds.kt`, read by the runtime and the census.
- **The sine's partials** (`Ignitors.sinePartials`, `docs/plans/sine-partial-banks.md`): the fundamental and four banks,
  `harmonics`, `octaves`, `suboctaves` and the explicit `partials` (`IgnitorDsl.Sine.Partial(ratio, gain, phase)`,
  2026-10-10), summed raw, one loop per partial, every knob read once per block (an explicit partial's signal gain
  per sample, its moving phase gliding across the block), a partial whose frequency's MAGNITUDE is at or above
  Nyquist silent (one gate for the node; a non-finite frequency silent too), drift lanes in `DriftLanes` blended by
  `analogSpread`. The explicit list is fixed per note, so its storage and lanes are built with the node.
- **`pregain`** is an ordinary slot (`Param("pregain", 1.0)`) on the source, before every nonlinearity. It changes
  timbre only where a nonlinearity follows; a saturating shaper driven hard makes it inert, on a wavefolder it is
  the fold depth. A `mul` slot's default must be a safe literal: unset is NOT off for `mul`.
- **Distortion**: `classic()`'s distort is the fused `IgnitorDsl.Distort` running `DistortionCore` (drive inside
  the oversampler, DC blocker, no cap; a modulated amount at or below 0 runs at unity drive). The `distort` and
  `shape` doors build `Shape(Drive(...))`, bounded to +-1 by `ShapingFuncs.softCap`. `CrushCore` floors. Guard for
  both laws: `StripLawCoresSpec`. The Katalyst `distort` stage (`KatalystDistortEffect`, 2026-10-09) runs the fused law
  on each channel of a bus, with the house DC pole (`HOUSE_DC_BLOCK_COEFF`, near 7 Hz; the voice's is near 35 Hz) and
  the oversampler's group delay, rounded, as its latency in every state (`Oversampler.groupDelaySamples`: 4.0, 5.5,
  6.25; held as 4, 6 and 6 frames).
- **`parallel` on the Ignitor** (`IgnitorDsl.Parallel`, 2026-10-10): the branches summed, every branch reading one
  input instance; `BuiltIgnitor.latencyFrames` is collected along the signal spine (an oversampled `distort` or
  `shape` adds `Oversampler.latencyFrames`: 4, 6, 6), and the node pads every earlier branch to the latest
  (`delayedBy`). A plain `plus` stays unaligned.
- **`parallel` on the Katalyst** (`KatalystParallelEffect`, 2026-10-10): each branch is a `KatalystChain` of its own,
  run on a copy of the bus and SUMMED; branches are aligned by latency (pad rings, the longest branch is the stage's
  latency); every lifecycle question (tails, rents, reset, retire) is passed to the branches; a `duck` in a branch is
  hoisted to the orbit's duck. A `reverb` or `delay` in a branch carries the dry too. The doors write no stage for zero
  branches and inline one. `through` is `serial` since the same day (`docs/retired-names.md`).
- **`bands` on both hosts** (2026-10-10): DSL sugar in `klangscript-libs` (`BandsBuilders.kt`) over `parallel` and
  the EQ's existing sections, no DSP of its own. Linkwitz-Riley: band k is the high side of every cut below it (on a
  voice a split tree, each high side shared by identity), the low side of its own, and the all-pass of every cut
  above, one raw tap section in an EQ of its own (`x - 2 * bandpass(x)`, Q 1/sqrt(2)), so untouched bands sum to an
  all-pass, flat within 0.001 dB (`BandsCrossoverSpec`). A band's processors get the band behind a sectionless EQ, so
  an `eq()` tap in a band reads the band.
- **`blend` on both hosts** (2026-10-10): `x.blend(wet, f)` is `parallel(dry * (1 - wet), f * wet)`, the linear
  law; `wet` a slot or a signal on the Ignitor, a plain number on the Katalyst (no param arithmetic there).
- **The sample instrument**: a sample voice is bit-identical to the built-in `sine` when it plays the sine's own
  output at rate 1.0 (`SampleInstrumentSpec`, the oracle that outlived the strip). Its playback knobs `begin`,
  `end`, `speed`, `loop` are slots; `n` and `cut` stay wire fields.

## Guardrails and traps still in force

The list a reviewer pastes is `.claude/skills/review-loop/audio-constraints.md`; the project-wide guardrails
(block size, reverb `+ ANTI_DENORMAL`, OnePole HPF bias, the house limiter, script-door literal defaults, `min`/`max`
crossing) are in `CLAUDE.md`. Not repeated here. In addition:

- **A per-block copy is `copyRangeInto`** (`audio_be/.../utils/buffer_copy.kt`), never `copyInto`, whose JS form makes a
  `subarray` view per call. The domain-free helpers (fast math, numeric guards, phase wraps, fades) live in `utils/`.
- **The resonator stage is handed its config in a holder, not as double arguments** (`ResonatorConfig`, tidy-up step
  12 (a)): passing them made a steady body allocate about 40 bytes per block on V8, where a non-integral double
  crossing a call V8 does not inline is a heap number. The delay, reverb and phaser writers do the same since the V8
  allocation pass (`DelayConfig`, `ReverbConfig`, `PhaserConfig`, the phaser's `PhaserBlock`). The stages still
  allocate on V8 (production, engine level: delay 82, reverb 22, phaser 144 bytes per block), an open probe:
  `docs/tasks/engine-follow-ups.md` section 1. `audio/ref/performance.md`.
- **A per-block walk is an index loop** over an array or a list, never `for (x in ...)` over a collection or a map,
  which makes an iterator per call on JS. `Cylinders` keeps its orbits in rent order: that order is the mix's
  summation order, so changing it changes bits.
- **A node builds its storage with the voice and draws at its first block** (tidy-up step 10): drift lanes
  (`AnalogDrift()` then `seed`, `DriftLanes(capacity, sharedLane)` then `start`, only when `analog` may be above 0
  and, for the shared lane, the spread below 1), a stack's voice states and scratch, the superpluck's strings, the
  partial banks' arrays, the phaser kernel and a caching memo's buffer are allocated at build, sized from what the
  build can read (a count only when it reads no `Freq`, `countsAtBuild`); the first block reads the depth and draws,
  when and in the order it always did. Still allocated at render: a count signal's rise past that size, the shared drift lane's `Random` below
  spread 1 (D7), the phase pool's vocabulary while it grows. Guards: `FirstBlockAllocationSpec`, `SeededVoiceRngSpec`.
- **The SVF**: bandpass, notch and the resonators are linear. Lowpass and highpass at `analog > 0` take a
  state-dependent DAMPING path (a diode-pair term grows `k` with the state; `IgnitorFilters.kt`, `Ignitor.svf`).
  Never saturate by capping the feedback signal with tanh: two such attempts went unstable and were reverted
  (2026-05-28, history).
- **The tube shape's constants are Pade-consistent** with `fastTanh` (`ShapingFuncs.kt`), so `tube(0)` is exactly
  0; recompute them if `fastTanh` changes.
- **The unity-`mul` fold drops a `safeOut`** only over a signal survivor; in a parameter position the clamp does
  work and the fold does not fire (`survivesUnityFold`). The Karplus family's raw `feedback` is authored character.
- **The active list's order still reaches the sound** through one path: unison phase-pool takes are drawn on a
  voice's first rendered block, so a change to the removal order re-deals phases (open:
  `docs/tasks/engine-follow-ups.md`, item 14). Ownership no longer depends on it (newest onset wins).
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
- **The memo's freq key** (`MemoizingIgnitor.freqInvariant`, resolved at a share, never per wrap: per wrap made a voice
  build five times slower): a shared subtree that reads no `Freq` and carries no pitch mod ignores the caller's freq,
  so one LFO in the `phase` or `duty` of two oscillators at different pitches runs once per block. The author rule for
  the rest: a shared modulator that reads `Ignitor.freq()` anywhere (its rate, its depth, a scaling next to it)
  renders once per pitch; build it once per layer. The predicate is the detune fold's
  (`IgnitorBuildCache.usesMusicalFreq`): a node that consumed the freq ARGUMENT without a `Freq` leaf would break
  both. Guard: `SharedModulatorRateSpec`.
- **One pitch mod per pitch-mod node** (`combineMods`): the mod a vibrato, fm, pitch envelope, accelerate,
  `pitchMod` or `pitchModSemitones` hands to the pitched sources under it sits behind one memo that always caches per block, so its LFO or
  modulator advances once per block for every source at one pitch (it ran once per source until 2026-10-07; Sakura and
  Irish Lament were retuned to keep their sound). Except: a source detuned under a mod that keeps its freq key (an
  `fm`, or a mod whose knobs read `Freq`) renders the whole mod again. Guard: the B-1 rows of `SharedModulatorRateSpec`.
- **FM: a pitch node means what it wraps** (decision D1 and the placement rule, pitch pipeline step 3b, 2026-10-09):
  above an `fm` it moves the whole operator (the modulator follows the carrier, the ratio stays exact; an outer `fm`
  counts; a modulator with an absolute `freq` stays put), on the modulator the modulator alone, on the carrier the carrier alone. The modulator builds under the
  outer mod through a `CarrierFreqMod`, which asks it at the CARRIER's frequency, pinned by the fm every block before
  it renders the modulator (one field write); asked at the modulator's own frequency a `Freq`-keyed mod rendered twice
  per block and broke the carrier too (a chain of N fms rendered 2^N times; now N + 1). One wrapper per fm node and
  outer mod (`IgnitorBuildCache.carrierFreqMod`), so `let f = x.fm(m); f + f` keeps `m` one instance. The one shape
  not processed, an author rule: one fm whose carrier holds two pitches (`(x + x.detune(7)).fm(m)`) serves both with one
  modulator (and under a mod on the note, a pitch node inside the modulator reads the first pitch's mod) (give each layer its own fm, inside its detune, and sum them: `x.fm(m1) + x.fm(m2).detune(7)`). Reference: `audio/ref/voice-synthesis.md`; guards: `FmModulatorFollowsPitchSpec`,
  `FmModulatorTopologySpec`.
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

- **By ear** (`docs/tasks/by-ear/README.md`): `chain-swap-request-during-drain.md`,
  `duck-orbit-switch-click.md`, and the owed rounds listed there.
- **Engine pass 1 follow-ups**: `docs/tasks/engine-follow-ups.md` (the V8 residues, the JVM box per block-constant
  read, the phase-pool order, the audit's later steps and open decisions).
- **Open, correctness**: `docs/tasks/audit-audio-backend-leftovers.md` (§2 worklet tests waits on the maintainer;
  §4, the cut-group fade, done by lifecycle step 4), `docs/tasks/svf-coefficient-cache-never-engages.md`,
  `docs/tasks/bugfix-non-finite-pitch-strip-and-signals.md` (two NaN signal paths; its strip half closed with pitch
  pipeline step 4),
  `docs/tasks-archive/2026-10/20261007-shared-modulator-memo-rate.md` (two residues, both an author rule today: a shared modulator that reads
  `Ignitor.freq()` anywhere renders once per pitch; a layer detuned under an `fm` or a `Freq`-reading pitch mod renders
  the mod again); the build-time diagnostic for the second, an fm above a forking detune:
  `docs/tasks/fm-above-forking-detune-diagnostic.md`.
- **Scheduled or designed**: `docs/tasks/oversampling-regions.md`, `docs/tasks/master-dsl-followups.md`,
  `docs/tasks/katalyst-master-configure-doors.md`, `docs/tasks/pluck-release-tail.md`,
  `docs/tasks/voice-takeover.md` (blocked on a design decision), `docs/tasks/playback-layer-decomposition.md`.
- **Katalyst, future**: `reverb-models.md` (with `room-reverb.md`), `one-chain-host.md`, `delay-ceiling-edges.md`, `transition-times.md`,
  `ducking-unfinished.md`, `general-eq-core.md`, `flanger-chorus.md` (all in
  `docs/tasks/future/`). A wide rising compressor-threshold swing sits about 16 to 21 dB above its floor, a law
  decision left open (`docs/plans/knob-glide.md`).
- **Voice and instruments, V1 high priority**: `docs/tasks/pitch-pipeline-into-the-tree.md` (promoted 2026-10-07).
- **Voice and instruments, future**: `envelope-shape-followups.md`, `new-oscillators.md`, `onepole-highpass-door.md`,
  `cut-group-semantics.md`, `live-voice-modulation.md`, `soundfont-zone-selection.md`, `string-slot-readers.md`.
- **Engine, future**: `ignitor-optimizer-open-items.md`, `optimize-affine-chain-fusion.md`,
  `optimize-constant-control-fast-path.md`, `audit-parked-decisions.md`, `worklet-clock-divergence.md`,
  `high-performance-audio-backend.md` (parked behind the sound-first priorities).
- **Keep possible** (maintainer, 2026-10-08): stereo voices, signal knobs on a bus and on the master, routing,
  a delay line on the voice, glide, velocity in the tree, an Ignitor on a bus. Where each assumption lives and what a
  change must not do: `docs/plans/aaa-production-tricks.md` §11. Crossing one of those lines is a question for the
  maintainer first.
- **Never measured**: the build-time cost of the gate's ON path (the per-block numbers are in the history's gate
  entry).

## History

One line per step, newest first. A link to the archived task record where one exists, else to the entry in
`audio/ref/memory-history.md`. "Superseded" marks an entry whose rules no longer hold as written.

- 2026-10-10 Engine follow-ups 10a and 10b: `adsrCurveShape` is a statement `when`; its expression form boxed
  every sample of a moving envelope on V8 (the chain `adsr`, `classic()`'s envelope, the pitch and FM envelopes,
  about 2.1 KB per block each, now about 0), bit for bit, corpus identical: `docs/tasks/engine-follow-ups.md` items
  10a and 10b
- 2026-10-10 Engine follow-up 3: no noise box reproduces; the house limiter's per-sample `lookaheadStep`, never
  inlined by V8, boxed about 2 KB per block whenever anything sounded and 4 KB from its first reduction on (the
  likely cause of the old figure, unproven), now about 0; it is `inline`, bit for bit, corpus identical:
  `docs/tasks/engine-follow-ups.md` item 3
- 2026-10-10 Engine follow-up 9: `DistortionCore` dispatches its shape once per block to one loop per shape; the
  per-sample `when` boxed every shaped sample on V8 (2 to 33 KB per block, now about 0), bit for bit, corpus
  identical: `docs/tasks/engine-follow-ups.md` item 9
- 2026-10-10 The sine's explicit partials, `partial(ratio, gain, phase)` (a fourth bank, wire `Sine.partials`, cap
  256, both doors), corpus identical: `docs/tasks-archive/2026-10/20261010-sine-inharmonic-partials.md`
- 2026-10-10 Pitch pipeline 7c: the vibrato's `range(from, to)` and `phase` (node fields, both doors, `classic()`
  slots `vibrato.rangeFrom|rangeTo|phase`); the default builds neither, corpus 18 of 18 identical:
  `docs/tasks/pitch-pipeline-into-the-tree.md` section 7c
- 2026-10-10 Pitch pipeline 7b: the `vibrato` node's runtime is a composition, `VibratoModIgnitor` gone; seven corpus
  songs move, by the sine's rng draw (all seven), one rounding (five) and the per-sample depth (Die Kirschblüte):
  `docs/tasks/pitch-pipeline-into-the-tree.md` section 7b
- 2026-10-10 Pitch pipeline 7a: `pitchModSemitones(mod)`, the exponential pitch primitive (`2^(mod / 12)`), a node on
  both doors and the wire beside the linear `pitchMod` (D8); the corpus identical:
  `docs/tasks/pitch-pipeline-into-the-tree.md` section 7a
- 2026-10-10 Pitch pipeline step 5: the strip's shell goes (`PitchPipelineBuilder`, `BlockContext.freqModBuffer`,
  `Voice.RenderContext.freqModBuffer`, `IgniteRenderer`'s bridge) and `voices/strip/` dissolves into `voices/` (D7);
  a pure removal, the corpus identical: `docs/tasks/pitch-pipeline-into-the-tree.md` step 5
- 2026-10-10 Pitch pipeline step 4: sprudel's `fm(depth, ratio, attack, decay, sustain, release)` is `classic()`'s
  innermost FM stage (`fm.*` slots), its five wire fields, `FmRenderer`, `Voice.Fm`, `Voice.Envelope` and
  `EnvelopeCalc` gone; the node's law (per-sample envelope, E11 closed; full depth through the tail without an envelope;
  the modulator follows the other pitch doors; the rng shift); the corpus identical:
  `docs/tasks/pitch-pipeline-into-the-tree.md` step 4
- 2026-10-10 `serial` (was `through`), `parallel` on the Ignitor and the Katalyst (branches summed and aligned by
  latency), `bands` (Linkwitz-Riley, flat untouched), `blend` (linear dry/wet), and a shared node kept one node
  across the wire (`@WireShared`): `docs/tasks-archive/2026-10/20261010-parallel-serial-bands.md` steps 1 to 5
- 2026-10-10 v0.6.1: pitch pipeline steps 1 to 3b, the Katalyst `distort` stage (merged from `katalyst-distort`), a
  soloed voice protected to its end, the warehouse panel's reverb counters, `analog(character)` on every door and
  `variants` with plain numbers; the corpus identical except Kokon's two landing strikes (accelerate, at most 2.5
  cents): the entries below and `DEV-DIARY.MD`
- 2026-10-09 A `distort` stage on the Katalyst (bus and master), the voice's law at the house DC pole, the oversampler's
  latency held in every state: `docs/tasks-archive/2026-10/20261009-katalyst-distort-stage.md`
- 2026-10-09 A soloed voice is protected for its whole life (`ActiveVoice.soloed`, Q14 and Q28); the window serves between events:
  `docs/tasks-archive/2026-10/20261009-solo-protects-whole-voice.md`
- 2026-10-09 Pitch pipeline step 3b: a pitch node means what it wraps; above an `fm` it moves the whole operator
  (`CarrierFreqMod`, the modulator reads the outer mod at the carrier's frequency, once per block). A sound change for
  authored fm trees under a pitch node and for sprudel's pitch doors over an fm instrument (the strip moved the modulator too),
  and for two shared-modulator shapes that rendered one modulator at two pitches; the corpus identical; one author rule
  (an fm above a forking detune): `docs/tasks/pitch-pipeline-into-the-tree.md` step 3b
- 2026-10-09 Pitch pipeline step 3: sprudel's `accelerate` is `classic()`'s accelerate stage (the flat `accelerate`
  slot), its wire field and the strip's `AccelerateRenderer` gone; the node holds its target from the gate on (D2):
  a sound change for an `accelerate` under a release tail on both doors (corpus: Kokon's `strike` only, a listening
  pair) and for a zero gate (`legato(0)`: the target from the first frame, Q27), every other corpus row and every
  non-accelerate matrix row identical, apart from the shapes the step record names (D1, D6, a non-finite amount now
  the bare voice where the strip froze the oscillator, the clamp from about 598 semitones, one temporary regrouping
  with sprudel `fm`): `docs/tasks/pitch-pipeline-into-the-tree.md` step 3
- 2026-10-09 Pitch pipeline step 2: sprudel's `vib` is `classic()`'s vibrato stage (`vibrato.*` slots), its two wire
  fields and the strip's `VibratoRenderer` gone, bit-identical on the corpus and the door matrix apart from the
  shapes the step record names (D1, D6, regroupings up to about 7.3e-13, the raw edges: a depth past about 598
  semitones, non-finite rates, a +Infinity depth): `docs/tasks/pitch-pipeline-into-the-tree.md` step 2
- 2026-10-09 Pitch pipeline step 1: sprudel's `penv` is `classic()`'s pitch envelope stage (`penv.*`, `penvCurves.*`
  slots), its eight wire fields and the strip's `PitchEnvelopeRenderer` gone, bit-identical on the door matrix and
  the corpus, apart from the accepted shapes (an fm modulator under `penv` until step 3b, three pitch factors
  regrouped, bare instruments, D6): `docs/tasks/pitch-pipeline-into-the-tree.md` step 1
- 2026-10-09 v0.6.0: the engine tidy-up is done (steps 1 to 13: the twins written once, `FilterDef` retired, a plain
  number at every constant door; the V8 allocation pass), bit-identical on the corpus; the open items:
  `docs/tasks/engine-follow-ups.md`; the record: `docs/tasks-archive/2026-10/20261009-engine-tidy-up.md`
- 2026-10-09 The `classic()` slot renames: the envelope words and `declick`, `crush.bits`, `coarse.factor`, the
  pluck's `feedback`, brown noise's `leak`, on every door, node, wire field and runtime factory, bit-identical:
  `docs/tasks-archive/2026-10/20261009-classic-slot-names-check.md`
- 2026-10-08 One fade law for the bank swap and the compressor (`utils/linear_crossfade.kt`); body and vowel are one
  stage class with two kinds on a pooled bank, no allocation per change; the band rows live with their catalogues
  (`BodyMaterials.Mode`, `VowelBands.Band`) and `FilterDef` is retired: `docs/tasks-archive/2026-10/20261009-engine-tidy-up.md` step 12
- 2026-10-08 First-block and voice-count allocations moved to the build (drift lanes, phaser, memo, partial banks,
  stacks, strings, the phase pool's parse and key): `docs/tasks-archive/2026-10/20261009-engine-tidy-up.md` step 10
- 2026-10-08 An engine's end of life is one `PlaybackEngine.Phase`; the dispatcher's `draining` set and `detached`
  list are gone; `renderInto` is one path: `docs/tasks-archive/2026-10/20261009-engine-tidy-up.md` step 9
- 2026-10-08 One playback per scheduler: one `PlaybackCtx`, no `playbackId` filters or parameters
  (`startRealtimeVoice` keeps its id, the context may be made from it): `docs/tasks-archive/2026-10/20261009-engine-tidy-up.md` step 8
- 2026-10-08 Per-block walks are index loops (`Voice` stages, `Cylinders` in rent order with an id array, the
  scheduler, the dispatcher); the diagnostics closure is gone; the solo ramp is `SoloRamp` on an inlined
  `easeInOutCubic`, `ValueRamp` deleted: `docs/tasks-archive/2026-10/20261009-engine-tidy-up.md` step 7
- 2026-10-08 The audio helpers live in `utils/` (`DspUtil.kt` split by content; `finiteOrZero`, `fadeToZero`,
  `timeConstantCoeff`, `wrapPhaseFastOrSafe`, `rampStep`, and `copyRangeInto` for every per-block copy, no `copyInto` view
  on JS); the stereo add is the member `StereoBuffer.addFrom`: `docs/tasks-archive/2026-10/20261009-engine-tidy-up.md` step 6
- 2026-10-08 One silence floor, `SILENCE_FLOOR` (1e-5; the master's 1e-4 stays, D9); `BusEffectDefaults` holds
  delay and reverb; `TEARDOWN_FADE_SECONDS`, `stageAskedFor`, `KatalystChain.writers`: `docs/tasks-archive/2026-10/20261009-engine-tidy-up.md` step 5
- 2026-10-07 The `KatalystSlots` composites live in their writers, one NaN rule per knob (body, vowel, reverb
  lowpass: the stage's); `CompressorSettings` / `DuckSettings` left `Voice`: `docs/tasks-archive/2026-10/20261009-engine-tidy-up.md` step 4
- 2026-10-07 No `Random` default anywhere in the engine; an orbit knob's build draws from a fixed seed:
  `docs/tasks-archive/2026-10/20261009-engine-tidy-up.md` step 3
- 2026-10-07 The oversampler is two halves, `upsample` and `decimate`, with the caller's shaping loop between
  them inline (no closure per block, no `copyInto` view on JS): `docs/tasks-archive/2026-10/20261009-engine-tidy-up.md` step 2
- 2026-10-07 The gate covers the four pitch arms, a fold (a non-finite vibrato depth stays built, its default);
  a gated pitch arm's inner shares with the same node elsewhere: `docs/tasks/pitch-pipeline-into-the-tree.md` step 0
- 2026-10-07 Solo is engine state per source: the rest fillers are control-only events, `SoloTracker` records from any
  event before the control drop and the late guard, the others play at `1 - amount`; `ActiveVoice.soloAmount` gone,
  audit B4.2 closed: `docs/tasks-archive/2026-10/20261009-bugfix-solo-rests-and-amount.md`
- 2026-10-08 The unison cap is 256 (maintainer; it was 64): `docs/tasks-archive/2026-10/20261009-engine-tidy-up.md` (Decided)
- 2026-10-07 An empty `Ignitor.variants()` is silence, no longer a `require` at note-on; the shimmer survives an empty,
  a huge or a non-finite pitch (no index error, no hang); a unison count is capped at `UNISON_MAX_VOICES` (64):
  `docs/tasks-archive/2026-10/20261009-engine-tidy-up.md` ("First, a bug")
- 2026-10-07 The voice's states are a sealed type; the fade window lives in `Fading`, the silence count in
  `Releasing` (lifecycle step 5b, no sound change by design; the 18-song corpus bit-identical to step 5,
  coordinator, 2026-10-07): `docs/tasks-archive/2026-10/20261007-voice-lifecycle-state-machine.md`
- 2026-10-07 The newest Sounding voice owns its orbit's bus settings, gives them up at its gate or cut; the zombie
  is retired and every removal keeps the list's order (lifecycle step 5, maintainer, a sound change):
  `docs/tasks-archive/2026-10/20261007-voice-lifecycle-state-machine.md`
- 2026-10-07 A cut fades its victim over 4 ms (`Fading`) instead of removing it (lifecycle step 4; no shipped song
  uses cut): `docs/tasks-archive/2026-10/20261007-voice-lifecycle-state-machine.md`
- 2026-10-07 Note-off and hard kill are events on the voice; the scheduler removes only Done voices (lifecycle
  step 3, no sound change by design): `docs/tasks-archive/2026-10/20261007-voice-lifecycle-state-machine.md`
- 2026-10-07 One home for a voice's time limits, `VoiceLimits` (lifecycle step 2, no sound change by design):
  `docs/tasks-archive/2026-10/20261007-voice-lifecycle-state-machine.md`
- 2026-10-07 The voice's lifecycle is a state machine inside `Voice` (step 1, read-only, no sound change by design):
  `docs/tasks-archive/2026-10/20261007-voice-lifecycle-state-machine.md`
- 2026-10-07 The engine's output is floating point: the master clips into a `StereoBuffer`, the browser hears the
  floats, the 16-bit step lives only at the JVM/WAV edge (`docs/tasks-archive/2026-10/20261007-float-output.md`)
- 2026-10-07 One pitch mod over several pitched sources at one pitch advances once per block; Sakura's `shaku` and Irish Lament's
  `blockfloete` vibrato rates written as heard (`docs/tasks-archive/2026-10/20261007-shared-modulator-memo-rate.md`, B-1)
- 2026-10-07 A shared modulator that reads no `Freq` runs once per block at any pitch; a non-finite pitch amount reads
  as unset; silence floors on the Eq parity specs; the wasm stub deleted (`docs/tasks-archive/2026-10/20261007-shared-modulator-memo-rate.md`,
  `docs/tasks-archive/2026-10/20261007-bugfix-ignitor-non-finite-pitch-amount.md`, `docs/tasks/audit-audio-backend-leftovers.md`)
- 2026-10-07 The saw's node is `Saw` end to end: the node `IgnitorDsl.Saw` (wire name `saw`), the factory
  `Ignitors.saw`; the sound names `sawtooth` / `saw` and the LFO shape `sawtooth` stay
  (`docs/tasks-archive/2026-10/20261006-oscillator-names-across-dsls.md`, decision 3)
- 2026-10-06 The triangle oscillator is `tri` end to end: the node `IgnitorDsl.Tri` (wire name `tri`), the factory
  `Ignitors.tri`; the sound name and the LFO shape `triangle` stay
  (`docs/tasks-archive/2026-10/20261006-oscillator-names-across-dsls.md`)
- 2026-10-06 A `phase` input on every periodic oscillator and a `range` on the tremolo's swing, both defaults bit-identical
  (`docs/tasks-archive/2026-10/20261006-oscillator-phase-knob.md`)
- 2026-10-05 The Ignitor's `rangex(from, to)`, the exponential twin of `range`, composed of `Exp`, `Range`, `Log` and
  `Max` (no node of its own), floor `RANGEX_FLOOR` (`docs/tasks-archive/2026-10/20261005-sprudel-signals-range-cleanup.md` decision 16)
- 2026-10-05 One `range`: the Ignitor's `unipolar()` / `bipolar()` go with their wire nodes; `range(0, 1)` and
  `mul(2).minus(1)` spell them (`docs/tasks-archive/2026-10/20261005-sprudel-signals-range-cleanup.md`)
- 2026-10-04 The Ignitor/Katalyst naming: the wire field is `ignitorParams`, `SoundValue.Dsl` holds an inline tree,
  Katalyst params are their own type, `KatalystDsl.Slots` builds `classic` (`docs/plans/ignitor-katalyst-naming.md`)
- 2026-09-30 The reverb is one room for both ears (`CROSS_FEED` 0.5): [record](../docs/tasks-archive/2026-09/20260930-stereo-reverb.md)
- 2026-09-29 The tremolo is composed from the oscillators (its own LFO removed, `skew`/`phase` gone, 16 ms edges, every depth floored at 0): [task](../docs/tasks-archive/2026-10/20261002-tremolo-as-composition.md)
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
