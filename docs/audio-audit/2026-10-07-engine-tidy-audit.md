# Engine tidy audit: Katalyst leftovers, tidiness for a Zig port, state machines

Read-only audit, 2026-10-07, branch `voice-lifecycle` at HEAD `4481ea25`. Asked by the maintainer: "Check the engine
code for anything left over from the Katalyst DSL that we did not do yet ... the backend codebase must be as tidy as
can be" (a later Zig port in mind). Scope: `audio_be` and `audio_bridge` main sources; tests only where they keep
dead code alive.

How it was done: the coordinator's audit plus three read-only sweeps run in parallel (dead code over all 2,854
non-private declarations; the Ignitor runtime with the Zig port in mind; effects, filters, Katalyst and master
duplication). Every finding marked "verified" below was checked by reading the code. Files under
`audio_be/.../cylinders/` and `audio_be/.../voices/` are being edited by voice lifecycle step 5 right now, so their
line numbers are **HEAD lines** (`git show HEAD:<path>`), not the working tree's.

Paths are shortened: `be/` = `audio_be/src/commonMain/kotlin/`, `br/` = `audio_bridge/src/commonMain/kotlin/`.
Sizes: **S** = one file, under an hour; **M** = several files or a spec sweep; **L** = cross-module or the wire.
Item ids (A1, B12, ...) are referenced by the cleanup order at the end.

Planned work that already removes items, so they are not counted twice (marked **[planned: ...]**):

- `docs/tasks/voice-lifecycle-state-machine.md` steps 5 to 7 (the lease per state; takeover; optimisation);
- `docs/tasks/pitch-pipeline-into-the-tree.md` (the pitch strip `be/voices/strip/pitch/`, `Voice.Vibrato` /
  `Fm` / `Accelerate` / `PitchEnvelope` / `Envelope`, `BlockContext.freqModBuffer` and the `phaseMod` feed,
  `EnvelopeCalc.kt`, the pitch wire fields, and the natural moment to dissolve `voices/strip/`);
- `docs/tasks/code-style-named-args-pass.md` (every swappable positional call, e.g. `VoiceFactory.buildVoice`'s
  17 positional arguments, `Cylinders.checkIn`);
- `docs/tasks/future/one-chain-host.md` (the `Cylinder` / `MasterBus` chain plumbing table, the
  `cylinders/katalyst/` package name).

---

## A. Katalyst DSL leftovers

### A.1 Open items the Katalyst record and its follow-ups name

The archived record `docs/tasks-archive/2026-09/20260928-katalyst-dsl.md` lists six moved items at its top (lines
9 to 19). Four more are named in its body and have no file of their own (A1.7 to A1.10). Each is checked against
the code.

| id | item | where it lives now | still true? | size |
|---|---|---|---|---|
| A1.1 | The delay's tail ceiling on a changing owner: (1) a lengthened tap re-reaches content the ceiling wrote off; (2) after a self-oscillating fall, the hold decays from `CEILING_MAX` | `docs/tasks/future/delay-ceiling-edges.md` | **Yes.** `KatalystDelayEffect.kt:427-433` re-measures only on a return from a self-oscillating drain, never on a lengthened `delay.time`. `TailCeiling.kt:141,196,212` saturate at `CEILING_MAX` (`:244`, 1e6), not at the cap | S + S |
| A1.2 | `duck.orbit` switch onto a sounding source clicks, plus four recorded duck-handover corners | `docs/tasks/by-ear/duck-orbit-switch-click.md` | **Yes.** No blend mechanism exists (`audio/ref/katalyst.md:53` says so). The file's code pointers are current | decision first, then S to M |
| A1.3 | A chain request during the old chain's drain waits up to about 24.5 s | `docs/tasks/by-ear/chain-swap-request-during-drain.md` | **Yes.** `Cylinder.kt:452-457` (HEAD) parks `pendingKey` while `!swap.settled`; `MasterBus.requestSwap` does the same | by ear |
| A1.4 | The `Osc` / `oscp` rename (§D1) | was `signal-flow-redesign.md` §11 and a V1 row | **Done** 2026-10-04 (`docs/plans/ignitor-katalyst-naming.md`). No `oscp` / `Osc.` remains in the engine. Only the record's top pointer is stale (history; leave it) | none |
| A1.5 | The EQ's per-sample ramps (`EqCore` is snap-only) | `docs/plans/unified-eq.md` (D4 note) | **Partly dissolved.** The orbit was closed by the bank swap (Katalyst step 4). Since phase 3 step 12 the master runs the same `KatalystEqEffect` bank swap, so the record's "still open for a future master eq" no longer applies. What remains true: a per-voice `IgnitorDsl.Eq` with a signal-driven knob zippers per block (`EqCore` snap-only). `EqCore.kt:17` and `:26-27` still say "the planned master eq stage adopts it later": stale | doc S |
| A1.6 | The pattern door that takes a configure lambda (`.katalyst(k => ...)`, `.master(k => ...)`) | `docs/tasks/katalyst-master-configure-doors.md` | Open, NICE, blocked on `klangscript-union-types.md` step 3. Not engine code | M (sprudel / KlangScript) |
| A1.7 | `Voice.Compressor` / `Voice.Ducking` live under `Voice` though only the chain uses them (record line 548: "still there" on 2026-09-27) | **no task file** | **Yes.** `be/voices/Voice.kt:538-577` (HEAD). The only main-code users are `KatalystSlots`, `KatalystSlotWriters`, `KatalystCompressorEffect`, `KatalystDuckEffect`. `Voice.Ducking.cylinderId` / `attackSeconds` carry pre-Katalyst names | S to M |
| A1.8 | An OFF Ignitor effect node still copies its upstream (record line 1082, "belongs with phase 3's node-level gate") | no task file | **Mostly dissolved.** The gate (a `Param`/`Constant` knob at its off value builds no node) removes the common case. An off-by-signal node (an LFO on `crush`) still renders its upstream into scratch and copies. Not worth a task on its own | none |
| A1.9 | The phaser's low "blub" on every transition (5c-9 listening) | `docs/tasks/future/transition-times.md` §4 | Open, by ear | by ear |
| A1.10 | The process-wide content-identity maps grow per live edit (signal-flow §11, "Housekeeping") | `docs/plans/signal-flow-redesign.md` §11 | **Yes.** `br/KatalystDslIdentity.kt:22-23` (`globalKatalystNames` plus counter); the Ignitor twin likewise. A frontend-side rule violation (the "allocate only with a way to clean up" rule), not audio-thread code | M |

Stale lines in the plan's open list, found on the way (doc only, S):

- `signal-flow-redesign.md` §11 "a placed `pregain` costs CPU at unity" is **solved**: the unity fold drops it
  (`be/ignitor/IgnitorDslRuntime.kt:1159-1206`).
- `docs/tasks/future/ducking-unfinished.md` still cites `VoiceData.ducking`, `Cylinder.kt:198-203` and describes the
  duck as a voice field; the wire is the `duck.*` slots in `katalystParams` now.

Related, still open in `docs/tasks/master-dsl-followups.md`: §1 (the parameter parity audit), §3 (a chain-cache miss
rebuilds a chain on the audio thread; it applies to both hosts, `Cylinder.kt:944` HEAD documents the same
exception), §6 (doc debts), §7 (Greensleeves' inert limiter, by ear).

### A.2 Leftovers in the code that no task names

**Old orbit and bus mechanisms beside the chain**

- **A2.1** `be/cylinders/Cylinder.kt:198-238` (HEAD): the fixed-slot accessors `body`, `vowel`, `delay`, `reverb`,
  `phaser`, `compressor`, `pipeline`. Production reads none of them; only the specs do (production reads `duck`,
  `mixBuffer`, `deniedRents`). They are the shape of the pre-Katalyst hardcoded `listOf(body, vowel, ...)`. The
  same holds for `KatalystChain.kt:125-135`: `body`, `vowel`, `phaser`, `compressor` are test-only. `delay` and
  `reverb` are read only by `MasterBus.kt:229` `declaresTail`, which is a chain property living on one host
  (A2.6). Verified. **S** (move the specs to a `stage<T>()` test helper).
- **A2.2** `be/cylinders/Cylinder.kt:22-24` (HEAD) KDoc: "Mixing channel / Effect bus, called Cylinder in
  strudel". Strudel calls it an orbit, and the user-facing word is orbit everywhere (`duck.orbit`, `.orbit()`, the
  docs). See decision D1. **Doc S, rename L.**
- **A2.3** `br/FilterDef.kt:17-100`: `FilterDef` was the per-voice filter union. It now carries only the orbit's
  resonator configs (`Formant`, `Body`), built only in `KatalystSlots.kt:150/167`. Its `floor: Double? = null` is
  never null in production, and that nullability forces a boxed `unsetFloor` and an extra branch in
  `KatalystBodyEffect.kt:72/88/172` and `KatalystFormantEffect.kt:41/51/118`. The file also carries unrelated
  `FILTER_MAX_PASSES` / `coercePasses`. **M.**
- **A2.4** `br/constants/SendEffectDefaults.kt`: the constants are live, but the file name and wording ("delay
  send", "a voice that never touches an effect still sends nothing") describe the send buffers that retired in
  5b-2. The `BusEffectDefaults.kt:8-37` header names readers that no longer read these constants (`VoiceFactory`,
  `Cylinder`, `SprudelVoiceData.toVoiceData`, "the engine's fill for a voice field"). Fold the two into one file.
  **S.**
- **A2.5** The "send" word on insert stages: `KatalystChainBuilder.kt:357` `sendStageRuns` (with comments at
  `:130/:159` "until a voice asks for one"); `be/voices/strip/send/SendRenderer.kt`, which pans the voice into the
  orbit mix and has no send (the package moves with the pitch task's strip dissolution). **S.**
- **A2.6** Stale voice-path KDocs: `KatalystBodyEffect.kt:122/127` and `KatalystFormantEffect.kt:78` ("Configure
  from the OWNER voice's body", "the voice path can carry one: `SprudelVoiceData.toVoiceData`");
  `KatalystRegistry.kt:20-22` (step 1 to 3 history); `KatalystContext.kt:10-19` ("Created once per cylinder": also
  per `MasterBus` and per `ChainSwap`). **S.**
- **A2.7** `effects/Ducking.kt:30-33, :41`: the duck's "attack" is a release, "named for Strudel compatibility",
  carried on `Voice.Ducking.attackSeconds`, `DUCK_ATTACK_SECONDS`, the `duck.attack` slot. A door rename, so a shape
  change. **S to M [planned: `ducking-unfinished.md` item 1].**
- **A2.8** `effects/Compressor.kt:128` `makeupGainDb`: a public `var` nothing in the repo ever writes; read only at
  `:636`, so makeup gain is always unity. The voice compressor that once set it is gone. Delete (bit-identical).
  Verified. **S.**
- **A2.9** Test-only mono or two-argument DSP entry points left from the per-voice era: `Compressor.kt:461`
  `process(buffer, offset, length)`, `Ducking.kt:156` `process(input, sidechain, blockSize)` (one benchmark
  caller), `Phaser.kt:178` `process(buffer, frames)`, `Reverb.kt:140` and `DelayLine.kt:226` `hasTail()`.
  **S** (move the specs onto the production entry points).

**The master path and the orbit path**

There is **one chain** (`KatalystChain`), **one registry** (`KatalystRegistry`, forked per playback) and **one swap
law** (`ChainSwap`), but **two hosts**, as `one-chain-host.md` records. The table there is accurate; what it misses:

- **A2.10** Two tail polls of one law ("silent for N blocks, then ask `hasTail`") with different floors:
  `MasterBus.kt:462-481` `updateTailState` (10-block interval, 1e-4) and `Cylinder.kt:673-729` (HEAD)
  `tryDeactivate` (10 cleanup visits, 1e-5). Two silence scans: `Cylinder.kt:731` `isMixBufferSilent` and
  `MasterBus.kt:484` `isAudible`. Two `buildChain`s (`MasterBus.kt:242`, `Cylinder.kt:1007` HEAD) and two contexts
  over the bus (`MasterBus.kt:442` `contextOver`, `Cylinder.kt:261`). **S to M [extend `one-chain-host.md`].**
- **A2.11** `be/PlaybackEngine.kt:117-160`: three branches each call `cylinders.processAndMix` (no master, master,
  releasing), plus a one-line wrapper `markMasterBusRendered` (`:174`). One path with `if (masterBus.isActive)`
  around the master call is the same arithmetic as the releasing branch already uses. **S, bit-identical** (the
  no-master branch sums straight into `target`; the merged form must keep that, or the sum order changes).
- **A2.12** The duck is inert at the output: a `master(Katalyst(k => k.duck(...)))` builds a duck stage that never
  runs (`audio/ref/katalyst.md:20` documents it). Fine, but there is no diagnostic. Note only.
- **A2.13** Two block drivers: `be/KlangAudioRenderer.kt:60-72` (offline: one engine, used by
  `klang/KlangOfflineRenderer` and the benchmarks) and `be/PlaybackEngineDispatcher.kt:158-191` (live: many
  engines). Both do clear, render, `MasterStage.process`, housekeep and clock advance. The offline renders
  therefore never run the dispatcher's code. **M, decision D4.**

**Markers in `audio_be` and `audio_bridge` main (`TODO`, `FIXME`, "for now", "legacy", "deprecated",
"temporary")**

There are few, and all are deliberate or historical:

- `br/VoiceData.kt:14` `// TODO: note can also be numbers -> Midi and detune`: stale. `freqHz` is the pitch;
  `note` only names a sample (`asSampleRequest`). Delete the TODO. **S.**
- `br/constants/OscillatorTuning.kt:45, :112` "seeded to the saw values for now": tuning placeholders, by-ear
  territory, not debt.
- "D7 stopgap" (`IgnitorDslRuntime.kt:764, :1509`, `IgnitorDslClassic.kt:99`, `IgnitorDsl.kt:1966`): the
  oversample factor as a build-time knob; tracked by `docs/tasks/oversampling-regions.md`.
- "legacy" in `EqCore.kt:72-386`, `LowPassHighPassFilters.kt:58, :397`, `VowelBands.kt:15`, `FilterDef.kt:39`,
  `Ignitors.kt:1570-1722`, `IgnitorDsl.kt:671-931`: they name a bit-parity contract (the vowel Q-peak fold, the
  phase-pool bypass draw order) that is still in force. Keep the meaning. The word could become "the historical"
  or name the law, but that is optional.
- `effects/Ducking.kt:33, :41` "Strudel compat": A2.7.
- `be/voices/Voice.kt:25-27` (HEAD) "Frame counters use Int instead of Long ... Int overflows after ~12.4 hours": stale,
  because the voice's frames are `Double` since the cursor fix. Delete. **S.**

---

## B. Engine tidiness, with the Zig port in mind

The overall picture is good. The runtime has no SAM ignitors, no `fun interface`, no anonymous `object : Ignitor`,
no reflection, no `expect`/`actual` in `audio_be`, and no param map read at render time. Every Ignitor node is a
`private class` with its children injected, so it maps 1:1 to a struct. What remains: dead code, near-identical
twins, first-block allocations, a handful of per-block allocations, and the multi-playback leftovers in the
scheduler.

### B.1 Dead code (no caller anywhere; verified)

- **B1.1** `be/ignitor/IgnitorFilters.kt:589-679`: `Ignitor.formant(bands)`, `FormantIgnitor`, `FormantBand`. No
  caller in production, tests or benchmarks. It is also a fourth SVF copy, with its own Q clamp (0.1..50 against
  `clampSvfQ`'s 0.1..200) and a `List` iteration per block. **S.**
- **B1.2** `be/DspUtil.kt:283` `Double.polyBlep`: no caller; its KDoc claims the saw, square and pulse use it.
  **S.**
- **B1.3** `br/KlangAudioDebug.kt:15` `KLANG_AUDIO_DEBUG`: the file's only declaration, no reader. **S.**
- **B1.4** `be/PlaybackEngineDispatcher.kt:149` `masterLatencyMs`: no reader. That makes `MasterStage.latencyMs`
  (`:81`) and `Compressor.latencyMs` (`:164`) test-only. **S.**
- **B1.5** `be/voices/VoiceFactory.kt:51` (HEAD) `sampleRateDouble`: never read; 18 test rigs pass it. **S.**
- **B1.6** `be/voices/strip/BlockContext.kt:58` (HEAD) `val cylinders`: no reader (`Voice.kt:324` reads
  `RenderContext.cylinders`). With it, `VoiceFactory.cylinders` (`:54`, `:447`) only feeds the dead field. **S.**
- **B1.7** `be/voices/VoiceFactory.kt:53, :131` (HEAD) `ignitorRegistry`: the same fork instance as
  `PlaybackCtx.ignitorRegistry`, which `:212` uses (both are `VoiceScheduler.ignitorFork`, `:177` and `:555`). Two
  references to one registry, read at two places in one function. Keep one. **S.**
- **B1.8** Smaller ones:
  - `effects/Compressor.kt:733` private `LN10`, unused;
  - `br/KlangPlaybackSignal.kt:106` `Custom`, never built or matched;
  - `br/jsMain/externals.kt:28, :30, :37-42, :86`: four external members nobody uses;
  - `be/AudioAnalyzer.kt:20` `fftSize`, never read through the interface;
  - `ShapingFuncs.kt:107` `nativeTanh` and `:357` `softCapTo`, test-only;
  - `filters/ParallelMixFilter.kt:9`, an unused import;
  - `KatalystChainBuilder.kt:274` `duckStage ?: KatalystStageDsl.Duck()`, unreachable;
  - `be/cylinders/Cylinders.kt:75-78` (HEAD), a doubled KDoc ("Clear all cylinders" above `releaseAll`);
  - `:41/:43`, `MAX_CYLINDERS = 256` immediately clamped to 255;
  - `:161`, an unused destructured `cylinderId`.

  **S** together.
- **B1.9** Never-read fields:
  - written in production but read by tests only: `KatalystGainEffect.ramps`, `KatalystEqEffect.installs` /
    `lastInstalledBank`, `KatalystChain.resolveCount`, `KatalystRegistry.namesNormalized`,
    `IgnitorRegistry.optimizerFailures`, `EqIgnitor.staticZeroDbSkips` / `staticConfigureSkips`,
    `WarmupRunner.readyWhileDirty`, the warehouse `doubleReturns` / `housekeptUnits` / `housekeptFrames`,
    `ScratchBuffers.doubleHighWater`;
  - on the wire but read nowhere, the frontend included: `KlangCommLink.kt:296-297`
    `WarehouseStats.reverbFailures` / `reverbDropped`;
  - `br/KlangPlaybackSignal.kt:68` `CycleCompleted.atTimeSec`, never read;
  - `br/VoiceData.kt:162` `tags`: the backend ignores it by design, but its KDoc's "consumed by UI subscribers" is
    not true today.

  Each is a counter seam or a diagnostic. Keep a seam that a spec needs; drop the others. **S.**
- **B1.10** `KlangCommLink.Cmd.ScheduleVoice` and `Cmd.ClearScheduled` (`br/infra/KlangCommLink.kt:43, :60`):
  handled by the backend, sent by no production code, only by tests. `ScheduleVoice` is also the single-voice
  twin of `ScheduleVoices`. **S, wire.**

Test-only seams in main code (84 counted: each overload and member separately). The bulk:
- the `Double` overloads of the Ignitor extension functions (`IgnitorFilters.kt:335-596`,
  `IgnitorEffects.kt:58-771`, `IgnitorEnvelopes.kt:145`, `Ignitor.kt:419-1559`);
- the `*ForTest` / `currentState` / `isParked` / `installed*` seams;
- `BlockContext.updateOffset` / `updateLength` and the `IgniteContext` twins;
- `VowelBands.bandsFor`, `IgnitorDsl.endsInClassic`, `IgnitorDsl.getParamSlots`.

The documented seams are fine. The overloads are a policy question (decision D10), because they are a second
spelling of every node that a Zig port would not carry.

### B.2 Duplicated logic (two implementations of one law)

- **B2.1 SVF kernel, four copies:**
  - `ignitor/IgnitorFilters.kt:258-345` (`SvfIgnitor`, six loops);
  - `filters/EqCore.kt` (the bandpass section);
  - `filters/LowPassHighPassFilters.kt:511, :577` (`BaseSvf` / `SvfBPF`; its loop reads fields, not locals,
    `:583-608`);
  - the dead `FormantIgnitor` (B1.1).

  `ResonatorBank`, the one production user of `SvfBPF`, could run on `EqCore` bandpass sections, which compute
  identical coefficients. **M [planned in part: `future/svf-resonator-class-collapse.md`, which covers the sweep
  removal only].**
- **B2.2 Oversampled shaper:** `ignitor/IgnitorEffects.kt:157-198` (`ShapeIgnitor`) and `DistortionCore.kt:38-86`.
  Each owns an `Oversampler`, a `DcBlocker` and the same `applyDistortionShape(...).nanGuard()` loop. They differ
  only in drive placement and the soft cap (decision D2 keeps the two laws). One core with `drive = 1.0` is
  bit-identical (`x * 1.0` is exact) and keeps both laws. **M.**
- **B2.3 Fade to exact zero, twice:** `be/voices/TeardownFadeRenderer.kt:92-96` and `be/voices/Voice.kt:437-452`
  (HEAD, `applyCutFade`, lifecycle step 4). The same linear law, "zero on the last frame that renders". Two
  constants of one value: `br/constants/EnvelopeDefaults.kt:141` `VCA_OFF_TEARDOWN_FADE_SECONDS` (named after the
  VCA that retired in step 9) and `:158` `CUT_FADE_SECONDS`. One `rampToZero(buffer, from, end, zeroIdx, scale)`
  helper; rename the first constant `TEARDOWN_FADE_SECONDS`. **S, bit-identical, after lifecycle step 5 lands.**
- **B2.4 Stereo "add bus B into bus A", three copies:** `Cylinders.kt:133-153` (HEAD), `PlaybackEngine.kt:130-138`,
  `ChainSwap.kt:572-584`. **S.**
- **B2.5 The "sterilised tap" `if (abs(x) <= Double.MAX_VALUE) x else 0.0`, 15 times** in `Crossfade.kt` (10),
  `TailRelease.kt:77-78`, `effects/Compressor.kt`, `effects/Reverb.kt`. `DspUtil` has `nanGuard` (NaN only) and
  `safeOut`. A `finiteOrZero()` inline would give it one home. **S.**
- **B2.6 Silence floors, five declarations of one idea:**
  - `TailCeiling.kt:237` `SILENCE`, `Reverb.kt:497` `TAIL_THRESHOLD`, the literal `0.00001` twice in
    `DelayLine.kt:226, :299`, and `br/constants/VoiceCullingDefaults.kt:27` `ORBIT_SILENCE_FLOOR`, all 1e-5;
  - `MasterBus.kt:116` `TAIL_SILENCE_THRESHOLD`, which is 1e-4.

  Two closed-form "samples until silent" formulas: `DelayLine.kt:296-319` and `Reverb.kt:239-252`. **S** (one
  constant at 1e-5; the master's 1e-4 is decision D9).
- **B2.7 One-pole time constant `1 - exp(-1/(t*sr))`, three times:** `AdsrCurveMath.kt:129`,
  `effects/Compressor.kt:686-690`, `effects/Ducking.kt:170`. **S.** (The bilinear `onePoleLpfCoeff`, the Shimmer and
  Karplus one-poles and `EnvelopeDeclick` are deliberately different laws. Name them as such, nothing more.)
- **B2.8 Linear on/off fade with a turnaround, twice:** `KatalystFilterSwap.kt:305-380` (Crossfading) and
  `KatalystCompressorEffect.kt:316-397` (Fading). The same law, `out = dry + w * (wet - dry)`, written twice.
  **S to M.**
- **B2.9 Delay and reverb lifecycles:** `KatalystDelayEffect.kt:269-473` and `KatalystReverbEffect.kt:180-338`.
  About 150 parallel lines: feed, silent input, wet glide, tail-ceiling observe, drain-on-deactivate, countdown, rent
  with refused latch. The delay has one extra arm (resume and re-measure). The plan accepted this shape per effect,
  and nobody has listed the twin. **M, decision D8.**
- **B2.10 Body and vowel versus the EQ (three hosts of `KatalystFilterSwap`):** each carries its own copy of "park
  while a fade runs, latest wins, offer again on landing" (`KatalystBodyEffect.kt:136-217`,
  `KatalystFormantEffect.kt:88-156`, `KatalystEqEffect.kt:150-231`; plus `hasParked` three times). The EQ installs
  into a preallocated two-bank pool. Body and vowel instead build two new `ParallelMixFilter` + `ResonatorBank`
  pairs **per change on the audio thread** (`:210-212` / `:149-151`, plus `bands.map` in
  `LowPassHighPassFilters.kt:360/372`). The EQ's pool shape would remove both the allocation and most of the twin.
  **M.**
- **B2.11 `KatalystSlots` versus `KatalystSlotWriters`:** `KatalystSlots.kt:145-221` (`bodyDef`, `vowelDef`,
  `compressorSettings`, `duckSettings`) each have exactly one caller, the matching writer. Their NaN substitutions
  are then applied a second time:
  - in the effects: `KatalystBodyEffect.kt:170-172` and `KatalystFormantEffect.kt:116-118` (whose own comments say
    no production caller can pass non-finite there);
  - in the reverb: `KatalystReverbEffect.kt:394` re-guards `lowpass`;
  - the phaser depth three times.

  The compressor settings make three hops for one rule. Fold the helpers into the writers and drop the second
  guards. **S to M, bit-identical.**
- **B2.12 Two Karplus string engines:** `ignitor/Ignitors.kt:2282-2410` and `:2416-2620` share the excite burst,
  the fractional read, the brightness one-pole and the stiffness allpass. The copy is acknowledged by
  `@Suppress("DuplicatedCode")` at `:2271` and `:2400`. One `KsString` struct. **M.**
- **B2.13 Two banded-phase selectors:** `ignitor/Ignitors.kt:1760-1815` and `ignitor/PhasePool.kt:442-475`, the
  "same acceptance logic" per their KDoc. **M** (needs a parity row).
- **B2.14 Sine loop, four copies:** `SineIgnitor` (`Ignitors.kt:146-300`), `PartialBank` (`:413-480`),
  `SineStackIgnitor` (`:2008-2110`), and the vibrato LFO (`PitchModFactories.kt:71-110`). **M [the vibrato copy is
  planned: pitch task step 2].**
- **B2.15 Scalar law written twice in every arithmetic node** (`generate` and `controlRateValueOrNull`;
  `PowIgnitor`'s signed pow five times, `Ignitor.kt:670-724`). This folds into B4.1. **S to M.**
- **B2.16 Smaller twins (each S):**
  - the `if (safeWrap) wrapPhase else smallNumFastMod` pair, 12 times (9 in `ignitor/`, 3 in the pitch strip,
    planned);
  - the drift-ramp prologue, 9 times (`SampleIgnitor.kt:55`, `Ignitors.kt:182, 237, 763, 806, 853, 1060, 1154,
    2351`);
  - semitone to ratio in two forms, `2.0.pow(s/12)` and `fastExp2(s/12)`: one name per role, never mixing them in
    one role;
  - `SampleIgnitor.kt:51-110`, whose drift and no-drift loops are copies (one loop with `m = 1, dm = 0` is
    bit-identical);
  - Box-Muller twice with different zero guards (`AnalogDriftCoeffs.kt:99-103`, `PhasePool.kt:430-431`; merging
    them changes draws, so only document it);
  - the warehouse shelves' counters and dirty/housekeep scaffolding in `SizedBuffers.kt` and `ReverbUnits.kt`
    (M);
  - the "ramp written from the block end" idiom, five times (`KnobGlide.kt:154`, `Phaser.kt:271`,
    `Ducking.kt:139`, `Compressor.kt:357`): plain; leave it, and the port writes it once.

### B.3 Leftover packages, names and one concept spread over several homes

- **B3.1** `be/voices/strip/` keeps its name after the strip retired (`BlockContext`, `BlockRenderer`,
  `IgniteRenderer`, the pitch renderers, `SendRenderer`, `EnvelopeCalc`). **[planned: pitch task, "the natural
  moment to rename or dissolve it"].** After the pitch task the voice's stages are fixed (ignite, optional teardown
  fade, cut fade, send). `Voice.stages: List<BlockRenderer>` (`Voice.kt:124, :390` HEAD) can then become plain code
  in `Voice.render`, with no interface and no list. **M, after the pitch task.**
- **B3.2 Three contexts per voice block:**
  - `Voice.RenderContext` (`Voice.kt:496` HEAD: cylinders, sampleRate, blockFrames, the voice buffer, the
    freq-mod buffer, scratch);
  - `BlockContext` (the same buffers and sampleRate again, the limits, the window, flags);
  - `IgniteContext`.

  Several fields are duplicated: `sampleRate`, `cylinders`, `scratchBuffers`, `freqModBuffer`. After the pitch
  task, `BlockContext` and `RenderContext` fold into one. **M [follows the pitch task].**
- **B3.3 The scheduler still serves many playbacks:** each `PlaybackEngine` has its own `VoiceScheduler`
  (`PlaybackEngine.kt:284`; lifecycle note L3). Yet the scheduler still keeps:
  - `playbackContexts: Map<String, PlaybackCtx>` (`VoiceScheduler.kt:148` HEAD);
  - `ActiveVoice.playbackId` (`:84`);
  - `playbackId` parameters on `cleanup`, `cleanupHard`, `clearScheduled`, `replaceVoices`, `startRealtimeVoice`,
    `stopRealtimeVoice`, `droppedVoiceCount` (`:242-417`), each filtering on an id that can only match;
  - `scheduled.removeWhen { it.playbackId == ... }`.

  This is the pre-per-playback-engine design. One `PlaybackCtx` per scheduler, created with the engine. **M,
  bit-identical** (after lifecycle step 5).
- **B3.4 "cylinder" against "orbit":**
  - the code says `Cylinder`, `Cylinders`, `CylinderUnits`, `Voice.cylinderId`,
    `KatalystDuckEffect.duckCylinderId`, `Voice.Ducking.cylinderId`, and the wire field `VoiceData.cylinder`
    (`br/VoiceData.kt:100`);
  - the user-facing word is orbit (`duck.orbit`, `.orbit()`, `audio/MEMORY.md` "each orbit (`Cylinder`)").

  "One word per concept". **L, decision D1.**
- **B3.5 "master" for two things:** `MasterStage` is the house stage (DC blockers, limiter, clip). `MasterBus` is
  the authored Katalyst at the output. Add `PlaybackEngineDispatcher.resetPostChain` and `KlangAudioRenderer`'s
  "master limiter". **S, decision D2.**
- **B3.6 The vowel stage has two words:** the stage, slot and writer say `vowel`, while the effect class says
  `Formant` (`KatalystFormantEffect`, `LowPassHighPassFilters.createFormant`, `FilterDef.Formant`). **M.**
- **B3.7 `filters/LowPassHighPassFilters.kt`** holds `createBody` / `createFormant`, the gain rules, `DcBlocker`,
  `SvfBPF` and the SVF coefficient helpers, and no lowpass or highpass any more. **S to M [in part:
  svf-resonator-class-collapse].**
- **B3.8 Host plumbing loose in the package root:** `ChainSwap.kt`, `Crossfade.kt`, `KnobGlide.kt`,
  `TailRelease.kt`, `MasterStage.kt`. `master/` holds only `MasterBus`; `TailCeiling` (in `effects/`) serves only
  the Katalyst delay and reverb. A `katalyst/` (chain, stages, swap, glide, release) plus `dsp/` split would read
  true. **M [in part: one-chain-host].**
- **B3.9 `KatalystChain.statics`** (`KatalystChain.kt:69`) are the slot writers, not statics. **S.**
- **B3.10 Two spellings of "unset":** the house rule is NaN (`SLOT_UNSET`), but nullable `Double?` carriers remain:
  - `Reverb.lowpass`, `KatalystReverbEffect.configure(lowpass: Double?)`, the writer's `damping`;
  - `FilterDef` floor (A2.3);
  - `Voice.Compressor.fromParams(Double?)`;
  - `KatalystDuckEffect.duckCylinderId: Int?`, read per block in `Cylinders.kt:127`;
  - `br/KatalystDsl.kt:378`: `Reverb.lowpass` is the only nullable bus knob.

  One convention would remove the double guards of B2.11. **S to M.**

### B.4 Zig-port concerns

**Allocation and closures on the render path (bugs, per block)**

- **B4.1 (bug, verified)** `be/ignitor/IgnitorEffects.kt:178`: `ShapeIgnitor` passes a capturing lambda to the
  non-inline `Oversampler.process` (`be/Oversampler.kt:88-92`) on every block when oversampling is on. That is one
  closure per block per voice. `DistortionCore.kt:54-63` already avoids it with a field lambda, which reads its
  drive through a field set just before the call (a side channel). The fix that serves the port: split
  `Oversampler.process` into `upsample` and `decimate` halves, so the caller owns the loop. That removes the only
  function-typed parameter on the audio path and lets B2.2 share one loop. **S to M, bit-identical.**
- **B4.2** `be/voices/VoiceScheduler.kt:126-128` (HEAD) `SoloSourceTracker.update`: allocates a `SourceState` per
  active solo source **every block**, plus `Pair` and `copy` on each transition. Iterates map entries and hashes
  `String` ids per voice per block (`:500`). Solo is rare, but when it is on, it allocates per block. **S.**
- **B4.3 Iterator allocation per block (Kotlin/JS):**
  - `Voice.kt:390` (HEAD) `for (renderer in stages)` over a `List`, per voice per block;
  - `VoiceScheduler.kt:480`;
  - `Cylinders.kt:115/126/133/161` (HEAD), four loops over a `LinkedHashMap` per block, with boxed `Int` keys and
    an entry-destructuring loop under a comment that says "no allocation";
  - `PlaybackEngineDispatcher.kt:177`.

  `KatalystChain` shows the house fix: an `Array` with an index loop. For `Cylinders`, `maxCylinders <= 255`, so
  an `Array<Cylinder?>` lookup plus a dense list kept in **insertion order** (the order is the mix's summation
  order, so changing it changes bits). **S to M, bit-identical** if the order is kept.
- **B4.4** `be/PlaybackEngineDispatcher.kt:212-223`: `emitDiagnostics` declares a local `fun count` closing over
  three `var`s, plus a new list, every 20 ms (not every block). **S.**
- **B4.5 Allocated behind a first-block guard** (allowed by `audio/ref/performance.md`, but a Zig render loop would
  need an allocator for each; every input exists at build time):
  - lazy `AnalogDrift` (`Ignitors.kt:156, 676, 1034, 2308`);
  - `DriftLanes` (`Ignitors.kt:497, 1609, 2517`, `DriftLanes.kt:155`);
  - lazy `PhaserCore` (`IgnitorEffects.kt:441`);
  - the memo cache buffer (`MemoizingIgnitor.kt:114`);
  - `Bank.resize` (`Ignitors.kt:348`);
  - the oversample sub-pool (`ScratchBuffers.kt:159-167`, a `Map<Int, ...>` looked up per block);
  - `ResonatorBank.kt:54-61` and `ParallelMixFilter.kt:44-52`, whose scratch starts at size 0;
  - `KatalystDelayEffect.kt:177` (a `DelayLine` wrapper per rent).

  **M, bit-identical.**
- **B4.6 Allocated when a stack's `voices` count changes** (per block if a signal modulates it):
  - `Ignitors.kt:1564-1581` (`Array(v)` plus new voice states);
  - `:2455-2471` (a new `StringState` with a fresh 2500-sample buffer each);
  - `:1773`;
  - `:1634` `parsePhasePoolSelection(String)`, which splits and parses a string on the audio thread;
  - `PhasePool.kt:178-218` (a data-class key plus a HashMap lookup per request).

  Preallocate at the maximum and parse at build. **M.**

**Exceptions**

- **B4.7 (probable crash, verified up to the call chain)** `be/ignitor/IgnitorDslRuntime.kt:281`:
  `require(children.isNotEmpty())` in `Variants.pick`.
  - KlangScript `Ignitor.variants()` is a vararg that accepts zero arguments
    (`klangscript-libs/.../KlangScriptIgnitor.kt:481-483`).
  - The voice is built at note-on (`VoiceFactory.kt:212`), and no catch exists on that path
    (`PlaybackEngineDispatcher.kt:163` is `try/finally` only).
  - `KatalystSlots.kt:94` knows an empty variants throws, but guards only its own path.

  Fix: build an empty `Variants` as silence, or reject it at the door. **S, a behaviour fix** (a crash becomes
  silence). Not a tidy item, but the most urgent find of this audit.
- Otherwise clean:
  - `ScratchBuffers.use` is try/finally (it becomes `defer` in Zig);
  - four unreachable `error()` arms (`IgnitorDslRuntime.kt:993-1002`);
  - out-of-memory catches on the warehouse and sample allocation;
  - the `optimize()` fallback catch at registration;
  - no `runCatching`, no `check()`;
  - `!!` on `PhasePool.kt:363-434` (invariant checks; S).

**Hierarchies and generic machinery**

- **B4.8 Arithmetic node classes:** 12 unary classes (`Ignitor.kt:630-1433`: Abs, Exp, Log, Sqrt, Sign, Tanh,
  Floor, Ceil, Round, Frac, Recip, Sq) and 8 binary ones (Plus, Minus, Times, Div, Min, Max, Pow, Mod) have one
  shape. Make them `Unary(op)` and `Binary(op)` with one scalar law each. `ParamIgnitor` and `ConstantIgnitor`
  (`ParamIgnitor.kt:21`, `ConstantIgnitor.kt:24`) render identically and differ only for `is ParamIgnitor`
  predicates: one leaf. **M.**
- **B4.9** The stacks use the template-method pattern (`Ignitors.kt:1497`, `:1846`: an abstract base three levels
  deep, `renderVoice` called virtually per voice per block). One `Stack` with a kind enum would replace it. **M.**
- **B4.10** `ModApplyingIgnitor` and `ModBlockingIgnitor` mutate `ctx.phaseMod` and restore it: dynamic scope by
  side effect, coupling between nodes. **M [the strip feed is planned to go with the pitch task; the tree's own
  vibrato and fm keep it unless composed from primitives].**
- **B4.11 Two parallel nine-way hierarchies:** `KatalystEffect` (9 implementations) and `KatalystSlotWriter` (9,
  one per stage). For Zig, one stage-kind tagged union holding effect plus writer. **M** (a decision, not a
  cleanup). `AudioFilter` adds a four-deep wrapper stack per resonator (`KatalystFilterSwap` → `ParallelMixFilter`
  → `ResonatorBank` → `SvfBPF[]`). **S to M with B2.10.**
- **B4.12** `be/BackendClock.kt:17` `RenderClock`: an interface with one implementation. **S.**
- **B4.13 Bus knobs are typed `IgnitorDsl`:** `br/KatalystDsl.kt:311-450` types every bus knob as the whole
  ~90-variant Ignitor expression union, folded at chain build by building an exciter graph and probing it
  (`KatalystSlots.kt:99-121`). A Zig backend would need the Ignitor builder to read a reverb's `wet`. **L, decision
  D5.**
- Two `when` walkers over `IgnitorDsl` per node type: about 380 arms in five walkers (the runtime 88, `GraphCensus`
  72, `IgnitorDslWalk` 168, the optimizer 55). A new node touches five places. Known, structural, fine for a sealed
  union. Note only.

**Hidden global or shared state**

- **B4.14 The global RNG fallback:** about 16 `random: Random = Random` defaults fall back to the process-wide
  unseeded `Random.Default`. Sites: `IgniteContext.kt:54`, `IgnitorDslRuntime.kt:64/103/130`,
  `IgnitorRegistry.kt:156`, `AnalogDrift.kt:55`, `SampleIgnitor.kt:38`, `Ignitors.kt:1427` to `:2411`. Production
  passes the per-voice stream. Removing the defaults turns a forgotten argument into a compile error. **S.**
- **B4.15 The RNG algorithm is Kotlin's `XorWowRandom`.** It is drawn per sample in noise, the string excite and the
  phase pool. A bit-identical Zig port must reproduce it exactly. Owning it as a project class (the same algorithm,
  so Kotlin output stays bit-identical) makes that explicit. **M, decision D7.**
- **B4.16** `be/voices/Voice.kt:585` (HEAD) `Voice.idCounter`: a companion `var`, process-wide, the one mutable
  global on the voice path. Wrap-safe (audit leftovers §3). For Zig, a per-scheduler counter. **S.**
- **B4.17 `ValueRamp` and `io.peekandpoke.ultra.maths.Ease`** (`VoiceScheduler.kt:22-23, :94` HEAD): the solo/mute
  ramp, the one place the render path depends on an external library curve (`Ease.InOut.cubic`). Inline the cubic.
  **S.**
- **B4.18** `be/SampleStore.kt:72`: per backend, grow-only, no remove path. Samples are shared on purpose
  (`audio/MEMORY.md`, "state lives at the granularity it is bound to"), but nothing ever frees one. **S to M,
  decision D11.**
- Clean: no companion `var` other than B4.16; the init-once tables (`EXP2_POW_TABLE`, `ADSR_EXP_NORM`) are
  comptime in Zig; `object Ignitors` holds only immutable defaults.

**Platform**

- `audio_be` has no `expect`/`actual`. `audio_bridge` has three (`KlangTime`, `AnalyzerBuffer`,
  `createAnalyzerBuffer`), all used. The JVM `AnalyzerBuffer` actuals exist only to satisfy the expect. Both
  `index_jvm.kt` files hold only a package line. **S.**
- Port note, not a finding: the render path uses `kotlin.math` `tan`, `tanh`, `sin`, `pow`, `exp` and `ln`, which
  already differ in the last bit between JVM and JS, and will in Zig. The `fastSin`, `fastExp2` and `fastTanh`
  polynomials port bit-exactly.
- **B4.19** `TailRelease.kt:72` calls `exp(-k / tau)` per sample. A per-sample multiply by a constant ratio is the
  same curve to rounding. Not bit-identical, so only with a listening-free identity argument (it is a release under
  -60 dB/3 s). **S, optional.**

### B.5 The wire (`audio_bridge`)

- **B5.1** `br/VoiceData.kt:28` `legato`: read as `clip` in `VoiceFactory.kt:104-107` (HEAD), where it scales the
  gate in frames. The plan's minimal wire (`signal-flow-redesign.md` §4: "timing: start, duration ... Everything else
  leaves") has no room for it. Folding it into `gateEndTime` on the frontend changes the gate's frame rounding, so it
  is not bit-identical. **M, wire, decision D6.**
- **B5.2** The `br/VoiceData.kt:14` TODO and the B1.10 commands: see above.

---

## C. Effect lifecycles: is `docs/plans/effect-state-machines.md` really done?

### C.1 The plan's section 3, row by row, against the code

**Verified true.** Every converted effect has the plan's shape:
- a private `sealed class State`;
- one preallocated `inner class` instance per state;
- a `state` pointer;
- a states-by-events transition table in the class KDoc.

| row (plan line) | the code | table in the KDoc |
|---|---|---|
| `KatalystDelayEffect`, Off / Active / Draining (`:204`) | `KatalystDelayEffect.kt:269-487` | `:252` |
| `KatalystReverbEffect`, Off / Active / Draining (`:205`) | `KatalystReverbEffect.kt:180-352` | `:170` |
| `KatalystFilterSwap`, Off / Engaged / Crossfading (`:206`) | `KatalystFilterSwap.kt:126-394` | `:120` |
| `KatalystCompressorEffect`, Off / Engaged / Fading (`:207`) | `KatalystCompressorEffect.kt:211-408` | `:199` |
| chain swap, Idle / Fading / Draining / Releasing (`:211`) | `ChainSwap.kt:113-462`, used by `Cylinder` and `MasterBus` alike | `:44` |
| `KatalystGainEffect`, without state classes (`:208`) | `KnobGlide` alone | n/a |
| `KatalystPhaserEffect`, `KatalystDuckEffect`, without state classes (`:210`) | `KnobGlide` plus `Phaser.engaged` (`Phaser.kt:117`); the duck's Off is `ducking == null` | n/a |
| `ResonatorBank`, retired (`:209`) | no morph, no state | n/a |

The three locals rules were spot-checked on the per-sample loops of `KatalystFilterSwap.Crossfading`
(`:340-366`), `KatalystCompressorEffect.Fading` (`:350-381`) and `ChainSwap` (`:572-584`). Each copies knobs,
buffers and positions into locals before its loop and writes back once after it. One loop outside a state class
breaks rule 1: `SvfBPF.process` (`LowPassHighPassFilters.kt:583-608`) reads its coefficients and integrators as
fields inside the per-sample loop (B2.1).

**Added since 2026-09-28.** `ChainSwap.Releasing` (step 12, decision (i)) follows the shape (`ChainSwap.kt:402`).
The voice's lifecycle (2026-10-07) deliberately does not; see C.4. No new Katalyst effect or stage arrived after
the plan closed.

### C.2 Flags kept on purpose (a recorded decision; not findings)

- The `fresh` snap flags ("the first initialisation is instant"), part of each effect's transition table:
  - `KatalystFilterSwap.kt:101` (table `:118-123`);
  - `KatalystCompressorEffect.kt:181` (table `:199-201`);
  - `KatalystPhaserEffect.kt:113` and `KnobGlide.kt:79` `snapNext` (plan `:208`, `:210`: "fresh, ramping and settled
    are exactly `KnobGlide`'s snap flag, countdown and rest").
- `Phaser.engaged` (`effects/Phaser.kt:117`): plan `:210`, "`Phaser`'s own `engaged` latch next to the cascade it
  guards".
- The duck's `handedOver` / `duckedLastBlock` (`KatalystDuckEffect.kt:131, :154`): plan `:210` (the duck done
  without state classes). The ducking design is being rethought as composition
  (`docs/tasks/future/ducking-unfinished.md`), so this is not the moment.
- `hasParked` in body, vowel and EQ (`KatalystBodyEffect.kt:83`, `KatalystFormantEffect.kt:48`,
  `KatalystEqEffect.kt:113`): the 5c-11 "two banks and one parking slot" decision. The flag tells "a parked OFF"
  from "nothing parked" (its KDoc, `KatalystBodyEffect.kt:79`). Its triplication is a duplication item (B2.10), not a
  lifecycle item.
- `DelayLine.snapTap` / `fading` (`effects/DelayLine.kt:165, :168`): the tap crossfade on a time change (snap,
  settled, crossfading) is `KnobGlide`'s three situations, the shape the plan accepted for the gain under its
  complexity rule (plan `:178`). It lives inside the delay's own state machine.
- `MasterBus.hasRendered` (`:172`): a one-way latch for "adoption at full weight before the first block" (plan risk
  R4, `one-chain-host.md` rule 1). One edge, no lifecycle. `MasterBus.ringing` / `silentBlocks` is a tail poll, not
  a lifecycle (A2.10).
- Warehouse `clean` flags (`ReverbUnits.kt:33`, `SizedBuffers.kt:158-160`): data about a unit, not a lifecycle.
- `EnvelopeCore.primed`, `DriftLanes.useShared/useOwn`, the Ignitor latches (`analogLatched`, `excited`,
  `started`, `initialized`, `stateDirty`): build or first-block latches inside DSP nodes. A tree node's lifecycle is
  "its own design question, not decided here" (plan `:212`).

### C.3 Where state classes (or a state enum) would clearly pay off

- **C3.1 `PlaybackEngine` and its dispatcher: one lifecycle spread over five places.** The engine's end of life is
  `stopped`, `isReleasing` and `released` (`PlaybackEngine.kt:50, :56, :60`), plus the `quietBlocks` hold counter
  (`:47`), plus two dispatcher collections that encode the same lifecycle from outside: the `draining` set and the
  `detached` list (`PlaybackEngineDispatcher.kt:30, :35`, edges at `:56-69` and `:249-271`).
  - **The states:** Playing → Stopped (holding, `resume()` returns to Playing) → Releasing → Released → disposed,
    plus "detached while releasing".
  - **Why it pays off:** this is a real multi-state lifecycle. It decides disposal, and disposal has had bugs (the
    truncated delay tail and the leaked engine of `master-dsl-followups.md` §2, fixed by step 12 decisions (i) and
    (j)).
  - **Today's edges are implicit:** `engineFor` moves an engine from `draining` to `detached` only when
    `isReleasing`; `isIdle` reads three flags in a fixed order.
  - **The proposal:** one `Engine.Phase` enum on the engine (the voice's precedent: a closed, parameter-less set,
    `when` dispatch). The dispatcher asks the phase instead of keeping its own set. Nothing needs inner classes:
    `quietBlocks` is the only datum, and it dies with Stopped.
  - **S to M, bit-identical.**

### C.4 Judgement calls (for the maintainer, not findings)

- **C4.1 The orbit's own activity (`Cylinder`).** `isActive` (`Cylinder.kt:276` HEAD), `silentBlockCount`
  (`:285`), the lease, `ownerParams` / `ownerParamsAge` (`:311-318`), `swap.settled` and `pendingKey` together
  decide when an orbit deactivates and resets (`tryDeactivate`, `:673-729`). There are four situations: inactive,
  sounding, silent grace, held by a voice.
  - **For states:** the logic is spread over several flags and counters. A bug came from exactly this spread: the
    fader through 0 that reset a playing orbit (`signal-flow-redesign.md` §11, fixed in 5c-8). Lifecycle step 5 is
    reworking the lease now.
  - **Against:** step 5 and `one-chain-host.md` will both reshape this code soon. The current version is commented
    edge by edge. A state machine now would be rewritten twice.
  - **Suggestion:** decide it with `one-chain-host.md`, after step 5.
- **C4.2 `WarmupRunner`** (`WarmupRunner.kt:123-124, :234, :241`). `started`, `finished`, `disposed` and
  `readyWhileDirty` describe four phases: not started, running, disposed (waiting for the warehouse to zero), done.
  - **For an enum:** four booleans for four phases.
  - **Against:** it runs once per backend start, it is guarded by specs, and no bug is recorded.
  - **Suggestion:** a cheap enum if someone is in the file; otherwise leave it.
- **C4.3 Two state-machine shapes in one engine.**
  - **The two shapes:** the effects use inner-class instances with virtual dispatch (the plan, "no `when`"). The
    voice uses a plain enum with a `when` (`be/voices/Voice.kt:301-330, :462-483` HEAD; recorded in
    `voice-lifecycle-state-machine.md` step 1: "a closed param-less set, no allocation per block").
  - **For the Zig port:** an enum plus a switch maps 1:1 to a Zig `enum` / tagged union and `switch`. The inner
    classes map to a tagged union whose payload holds the state's own data, plus an explicit self pointer for the
    outer fields.
  - **For keeping both:** each shape was chosen for its case. The effects' states carry data and reach the
    effect's resources; the voice's states carry none.
  - **No action needed now.** Worth one sentence in the plan, so the next state machine knows which to copy.

---

## D. Proposed cleanup order (each step one reviewable commit)

The order is behaviour-neutral (bit-identical) steps first, then shape and sound changes, each marked. "After step
5" means after `voice-lifecycle-state-machine.md` step 5 lands, because it is editing `cylinders/` and `voices/` now.
The named-args pass is queued for the same moment; steps marked "after step 5" can go before or after it, but not
in parallel with it.

**Behaviour-neutral (bit-identical; `audio_be` suites green, corpus render unchanged)**

1. **Delete dead code.**
   - B1.1 (`FormantIgnitor`), B1.2, B1.3, B1.4, B1.8, B1.9 (the unused counters), A2.8 (`makeupGainDb`);
   - the stale comments: A2.x doc lines, `VoiceData.kt:14`, `Voice.kt:25-27`.

   S.
2. **Fix the ShapeIgnitor closure (B4.1)** by splitting `Oversampler.process` into halves, with `DistortionCore`
   moved onto them. S to M.
3. **Remove the RNG defaults (B4.14).** Compile-time only. S.
4. **Fold `KatalystSlots` helpers into their writers and drop the second NaN guards (B2.11)**; move `Voice.Compressor` /
   `Voice.Ducking` to the katalyst package as settings types (A1.7), keeping their names until the duck rename.
   After step 5 (touches `Voice.kt`). S to M.
5. **Constants and names that change no shape:**
   - `SendEffectDefaults` folded into `BusEffectDefaults` with a true header (A2.4);
   - `VCA_OFF_TEARDOWN_FADE_SECONDS` renamed `TEARDOWN_FADE_SECONDS`, `sendStageRuns` renamed, `statics` renamed
     (B3.9);
   - one silence constant at 1e-5 (B2.6, the master's 1e-4 untouched).

   S.
6. **Small shared helpers:** `finiteOrZero` (B2.5), the stereo add (B2.4), `rampToZero` for teardown and cut (B2.3,
   after step 5), the one-pole time constant (B2.7), one wrap helper and the drift-ramp prologue (B2.16). S each;
   one commit or two.
7. **Voice factory and scheduler leftovers:** B1.5, B1.6, B1.7; the solo tracker allocation (B4.2); the
   per-block iterators (B4.3, `Cylinders` kept in insertion order); the diagnostics closure (B4.4); the inlined
   solo ramp curve (B4.17). After step 5. S to M.
8. **One playback per scheduler (B3.3).** After step 5 and the named-args pass. M.
9. **The engine's lifecycle as one phase enum (C3.1)**; the single render path in `PlaybackEngine` (A2.11). S to M.
10. **First-block and voice-count allocations to build time (B4.5, B4.6).** M; one commit per node family.
11. **Collapse the twins:**
    - the shaper core (B2.2);
    - the Karplus engines (B2.12);
    - the stacks (B4.9);
    - the unary and binary arithmetic nodes, and the `Param` / `Constant` leaf (B4.8, B2.15).

    M each, one commit each.
12. **Katalyst twins:**
    - body and vowel on the EQ's preallocated bank pool, with the parking written once (B2.10, B4.11's
      `AudioFilter` depth);
    - the FilterSwap / compressor fade law shared (B2.8);
    - the `FilterDef` carriers made non-null and moved (A2.3).

    M each.
13. **Test seams and overloads (B1 tail, decision D10)**, and the fixed-slot accessors moved to a test helper (A2.1).
    S to M.

**Shape and sound changes (each needs the maintainer)**

14. **Behaviour fix:** an empty `Ignitor.variants()` becomes silence or a door error instead of an audio-thread
    crash (B4.7). S. **Do this first, ahead of the order above.** It is a bug, not tidying.
15. **Planned work, in its own order:** lifecycle steps 5 to 7; `code-style-named-args-pass.md`;
    `pitch-pipeline-into-the-tree.md` (sound and shape, listening pairs). After it: dissolve `voices/strip/`, make
    the voice's stages plain code, fold the contexts (B3.1, B3.2). Then `one-chain-host.md`, extended by A2.10 and
    the package move (B3.8).
16. **Shape: the duck's "attack" renamed** to what it is (A2.7, `ducking-unfinished.md`).
17. **Shape: the vowel / Formant word unified (B3.6).**
18. **Wire and shape: "cylinder" renamed to "orbit"** (B3.4, decision D1), `legato` off the wire (B5.1, D6), the
    unused `ScheduleVoice` / `ClearScheduled` commands (B1.10).
19. **Sound, optional:** the per-sample `exp` in `TailRelease` (B4.19); one silence floor for the master (D9).
20. **Katalyst by-ear items:** A1.1 (two small correctness fixes, each with a render row), A1.2, A1.3, A1.9.

---

## E. Decisions the maintainer needs to make

- **D1 "Cylinder" or "orbit".** One word per concept says one of them goes. "Orbit" is the user's word; the code,
  the wire field `VoiceData.cylinder` and five classes say cylinder. L, wire. (B3.4, A2.2)
- **D2 `MasterStage` against `MasterBus`.** Two "masters". Suggest `HouseStage` for the always-on DC, limiter and
  clip, keeping "master" for the authored chain. S. (B3.5)
- **D3 Which state-machine shape the next lifecycle copies:** inner classes (the effects) or an enum with `when`
  (the voice). The enum maps more directly to Zig. (C4.3)
- **D4 One block driver or two.** Should the offline renderer run a one-playback dispatcher, so offline renders
  exercise the live code path? M. (A2.13)
- **D5 Bus knobs typed as full Ignitor expressions.** Today every bus knob is an `IgnitorDsl` folded at build, so a
  native backend must carry the Ignitor builder to read a reverb's `wet`. Keep (one vocabulary, and patterns may
  later drive bus knobs), or narrow to a literal-or-slot type. L, wire. (B4.13)
- **D6 `legato` on the wire, or folded into the gate time by the frontend.** Not bit-identical: frame rounding.
  (B5.1)
- **D7 Own the RNG:** a project class that reproduces `XorWowRandom` exactly, as the precondition for a
  bit-identical Zig port. M. (B4.15)
- **D8 The delay and reverb lifecycles:** share one drain-and-ceiling core, or keep two readable twins. The plan
  said "copy the shape, not the condition". (B2.9)
- **D9 Silence floors:** the master's tail poll uses 1e-4, everything else 1e-5. Align, or record why the output
  differs. (B2.6)
- **D10 Test-only overloads and seams in main code (84):** keep them as a test convenience, or move the
  `Double`-overload Ignitor extensions to test sources so main carries one spelling per node. (B.1)
- **D11 `SampleStore` grows for the life of the backend.** By design (samples are shared), but nothing frees one.
  Accept, or bound it. (B4.18)
- **C4.1 and C4.2** (the orbit's activity flags, `WarmupRunner`): state classes or not; see C.4.
- **Already parked elsewhere, listed so they are not lost:**
  - A1.2 (duck switch), A1.3 (chain request during drain), A1.9 (the phaser "blub");
  - the cut fade's curve (lifecycle step 4);
  - the lease rule (step 5);
  - `cut-group-semantics.md`;
  - the `wasmJs { }` block left in `audio_be/build.gradle.kts:24-28` (audit leftovers §5).

## Counts

- **A, Katalyst leftovers:** 10 named items (A1.1 to A1.10). 1 is done (A1.4), 2 are dissolved or mostly dissolved
  (A1.5, A1.8), 7 are still open. Plus 13 unnamed code leftovers (A2.1 to A2.13) and 6 marker comments, of which 2
  need action.
- **B, tidiness:**
  - 10 dead-code items covering about 30 symbols, plus 84 test-only seams;
  - 16 duplication items;
  - 10 naming and spread items;
  - 19 Zig-port items: 1 per-block closure bug, 1 probable crash, 1 per-block solo allocation;
  - 2 wire items.
- **C, lifecycles:** the plan's table is verified true. 1 clear finding (C3.1). 3 judgement calls.
- **E:** 11 decisions, plus the already parked ones.
