# Engine tidy-up: the Katalyst leftovers and a backend ready for a Zig port

Status: **V1, in progress (maintainer, 2026-10-07); step 1 (dead code) done, see below.** Step 3 of the engine order in [`_v1-scope.md`](_v1-scope.md), after
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

**Deferred until the solo fix lands** (the files are in flight): B1.5 `VoiceFactory.sampleRateDouble`, B1.7
`VoiceFactory.ignitorRegistry` and the now-unused `VoiceFactory.cylinders` (all three need the constructor call in
`VoiceScheduler.kt`); the stale `Voice.kt` "Frame counters use Int" comment; the `VoiceData.kt:14` TODO.

**Stale mentions outside the engine, for the maintainer:** `/code-style` §9 says saw, square and pulse "must use
PolyBLEP", `CREDITS.MD` credits PolyBLEP, and `klang-music-writing/ref/ignitor-reference.md` calls the saw
"anti-aliased (PolyBLEP)"; the oscillators use finite-slope flanks instead, and nothing used `polyBlep` before this
step either. Three `IgnitorsTest` row names say "PolyBLEP" too, and so do the in-app Credits page (`CreditsPage.kt:337`) and the
Zawtooth KDoc (`IgnitorDsl.kt:446`).

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
