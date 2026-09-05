# Audio backend audit — findings

**Analysis only. Nothing here has been repaired.** We triage this list together, then decide what to fix. Each finding
carries the evidence that produced it — a mutation that was actually run, or a reference trace — so triage does not have
to re-derive it.

Severity is about *what it costs us*, not about how broken the code looks:
**HIGH** = a wrong sound or a false sense of safety today · **MED** = a real trap that has not bitten yet · **LOW** =
correctness of the record.

Status: 🔴 open (untriaged) · 🟢 accepted-as-is · 🔧 to fix · ✅ FIXED · ⚪ user-decision

---

## F1 — Three tuning constants are documented as "the knob" but read by nothing ✅ FIXED

> **FIXED 2026-08-11** — `docs/tasks/audio-bridge-constants.md`. All three (plus `ADSR_EXP_K`, plus the shared master
> limiter defaults) now have exactly ONE declaration, in `audio_bridge/src/commonMain/kotlin/constants/`, which both
> the DSL default and the engine read. The `audio_be` twins are deleted, so there is no longer a constant that
> documents itself as the knob while production reads a different literal. The rule going forward: **a constant
> belongs in `audio_bridge/constants/` iff it is a wire default** — engine-internal tuning (`OscillatorTuning.kt`,
> `AnalogDriftCoeffs.kt`, `FILTER_SMOOTH_SAMPLES`) deliberately stays in `audio_be`.
>
> The line references in the table below are to the pre-fix files and no longer resolve; kept as the record of what
> the defect was.

**HIGH (at time of finding).** `audio_be` declares tuning constants with KDoc that presents them as the by-ear tuning
point ("Tunable by ear,
like `ADSR_EXP_K`"). The engine does not read them. The live values are duplicated literals in
`audio_bridge/PipelineDsl.kt`, each carrying a comment that names the twin it duplicates:

| Constant (`audio_be`)                                                | Value | Live twin            | Read by production code?                                              |
|----------------------------------------------------------------------|-------|----------------------|-----------------------------------------------------------------------|
| `ENV_DECLICK_SECONDS` — `AdsrCurveMath.kt:66`                        | 0.001 | `PipelineDsl.kt:107` | **No** — only `EnvelopeRenderer`'s default param, which is never used |
| `FILTER_CUTOFF_OFFSET_PER_ANALOG` — `FilterHumanizationCoeffs.kt:37` | 0.001 | `PipelineDsl.kt:98`  | **No** — only `AnalogDriftSpec` (a test)                              |
| `FILTER_DRIFT_RELATIVE_TO_OSC` — `FilterHumanizationCoeffs.kt:89`    | 2.5   | `PipelineDsl.kt:100` | **No** — only `AnalogDriftSpec` (a test)                              |

`EnvelopeRenderer` has exactly one construction site — `FilterPipelineBuilder.kt:86-91` — and it always passes
`declickSeconds = stage.declickSeconds` and `expK = stage.expK` explicitly, so both default parameters are dead.

**Evidence (mutation M1 / M1b).** `ENV_DECLICK_SECONDS: 0.001 → 0.0` — `EnvelopeDeclickSpec` stayed **GREEN**. The same
edit applied to `PipelineDsl.Vca.declickSeconds` turned **both** its tests **RED**. The engine reads the bridge literal;
the documented constant is inert.

**Why it costs us:** someone tuning by ear edits the constant whose KDoc says to, rebuilds, and hears no change. The
failure is silent and self-reinforcing (it looks like "the effect is too subtle").

**Not the same for two of the five duplicates** — `ADSR_EXP_K` and `FILTER_DRIVE_PER_ANALOG` *are*
live in other paths, which is arguably worse than fully dead: editing `ADSR_EXP_K` changes the filter/FM and ignitor
envelopes but **not** the amp VCA, so a by-ear tune produces a partial change.

**Note before triage:** the duplication itself may be deliberate — `audio_bridge` is the wire module and cannot depend
on `audio_be`. If so, the defect is the *absence of a sync guard plus misleading KDoc*, not the duplication. The
`master/` work shipped exactly such a guard (`MasterDefaultsSyncSpec`); nothing equivalent exists here.

---

### Agreed resolution (user, 2026-08-11): move the wire defaults into `audio_bridge`

The duplication exists because `audio_bridge` is the wire module and cannot depend on `audio_be`. But the dependency
runs the *other* way — `audio_be` has `api(project(":audio_bridge"))`, and
`audio_bridge` depends only on `:common` and `:tones`. So the constants can live in `audio_bridge`
and be read from both sides, with **one** home and no sync spec needed.

**The rule for what moves:** a constant belongs in `audio_bridge` **iff it is a wire default** — it appears as a default
in a `@WireFormat` class *and* the engine needs the same number. Everything else stays where it is used.

| moves                                                                             | stays                                                      |
|-----------------------------------------------------------------------------------|------------------------------------------------------------|
| `cutoffOffsetPerAnalog`, `drivePerAnalog`, `driftRelToOsc` (`PipelineDsl.Filter`) | `OscillatorTuning.kt` (36) — engine-internal, no wire twin |
| `expK`, `declickSeconds` (`PipelineDsl.Vca`)                                      | `AnalogDriftCoeffs.kt` (9) — ditto                         |
| the `MasterStageDsl.Limiter` / `MasterStage.LIMITER_*` pairs                      | `FilterHumanizationCoeffs`' non-duplicated members         |

⚠️ **Do not move engine-internal tuning into the wire module.** It would leak implementation detail into the module that
defines the protocol, and the wire module would start carrying numbers no message ever transmits.

**What this retires:** `MasterDefaultsSyncSpec` exists only because two copies had to be kept equal. Where a constant
becomes single-homed, the sync assertion becomes meaningless and should go — but **keep the parts that encode a
deliberate ASYMMETRY** (house limiter 5 ms lookahead vs authored 0), because those are real facts about intent, not
duplication artefacts.

**Suggested home:** a `constants/` package under `audio_bridge/src/commonMain/kotlin/`, so there is one obvious place to
reach for. Name the file after the concept, not the module — e.g.
`FilterHumanization.kt`, `EnvelopeShaping.kt`, `MasterLimiter.kt`.

---

## F2 — `AnalogDriftSpec`'s budget guard cannot see the values the engine uses ✅ FIXED

> **FIXED 2026-08-11** — same change as [F1](#f1). The spec now imports the same declaration the DSL default reads, so
> its ceilings track the engine by construction. The bounds were also retightened (`filterOffsetPeak` 6.0 → 2.0,
> `filterDriftPeak` 9.0 → 1.5) — they had been left behind when the values were lowered, leaving 6x and 12x of slack.
> Mutation-checked after the fix: offset 0.0002 → 0.0005 and drift 0.25 → 0.6 each turn it RED, with clean
> no-mutation sanity runs on both sides. **Mutation M2 below is no longer reproducible as written** — the file it
> mutated no longer declares the constant.

**HIGH (at time of finding).** `AnalogDriftSpec.kt:85-103`, *"analog cents budget stays tamed at analog=3 (Der
Schmetterling)"*. Its own
comment states the intent: *"Post-tuning ceilings — if a constant gets cranked back up, this fails loudly."* For two of
its three ceilings that is false.

`filterOffsetPeak` (`:88`) and `filterDriftPeak` (`:89`) are computed from
`FILTER_CUTOFF_OFFSET_PER_ANALOG` and `FILTER_DRIFT_RELATIVE_TO_OSC` — the dead constants from
[F1](#f1). The engine's actual values are `PipelineDsl.kt:98,100`.

**Evidence (mutation M2).** Cranked the **engine's** values 10× — `cutoffOffsetPerAnalog`
0.001 → 0.01 and `driftRelToOsc` 2.5 → 25.0 — and ran `AnalogDriftSpec`: **BUILD SUCCESSFUL**. The regression the guard
exists to catch, at 10× magnitude, does not move it.

**Why it costs us:** memory and the archived task doc both record `AnalogDriftSpec` as *the* guard for the 2026-06-17
analog-drift tuning. It is load-bearing for the osc-pitch ceiling only (`ANALOG_FAST_PEAK_CENTS` /
`ANALOG_SLOW_PEAK_CENTS` *are* live —
`AnalogDriftCoeffs.kt:95-96`), and inert for both filter ceilings. We have been trusting it for something it does not
do.

**Also in the same test:** lines 96-102 are a `println` loop over `analog = 1,2,3,5,8` with **no assertions** — an
eyeball table. Useful as documentation, contributes nothing as a guard, and it inflates the apparent coverage of the
test.

**Verdict on the spec's other 3 tests:** not yet checked (attack-centring mono/poly, no-runaway). They exercise
`AnalogDrift`/`PolyAnalogDrift` directly and look genuine; pending mutation.

---

## F3 — The test named after the mid-block clamp cannot detect the clamp's removal ✅ FIXED

**MED.** `FilterModulationTest`, *"voice starting mid-block handles envelope correctly"*. The behaviour it names is
`EnvelopeCalc.kt:31` — `val currentFrame = maxOf(blockStart, startFrame)` *(anchor re-verified 2026-08-31; code unchanged)*, which stops the envelope position going
negative when a block starts before the voice does.

**Evidence (mutation S1).** Replaced it with `val currentFrame = blockStart` and ran the **whole**
`FilterModulationTest`: **GREEN**. Not just that one test — no test in the file noticed.

> **Mutation RE-RUN 2026-08-31 against the entire suite (1373 tests): still GREEN.** The finding
> stands unchanged, and is now known to be suite-wide rather than file-wide.

> ✅ **FIXED 2026-08-31 by `voices.strip.MidBlockOnsetControlRateSpec`**, written for block-framing
> **P4**. The same mutation is now **RED on both of its rows**.
>
> **And the second question above is answered: NO, the clamp is not redundant.** The finding
> speculated that `maxOf(blockStart, startFrame)` might be decoration next to the trailing
> `coerceIn(0.0, 1.0)`. It is not, and the case that separates them is **`attackFrames == 0`**:
>
> | | `absPos` | branch taken | result |
> |---|---|---|---|
> | with the clamp | `0` | `0 < 0` is false → falls through | **sustain** |
> | without it | `blockStart - startFrame` (negative) | `negative < 0` is **true** → attack | coerced to **0.0** |
>
> A zero attack is the ordinary case for a filter or FM envelope, so removing the clamp would drop
> those two renderers to zero modulation for the whole onset block of any voice starting mid-block.
> The trailing `coerceIn` masks it only when the attack is non-zero.
>
> **Why the clamp exists at all, which the finding did not identify:** it is the *offset
> compensation* for the two control-rate callers. `FilterModRenderer` and `FmRenderer` pass
> `calculateControlRateEnvelope` the raw `ctx.blockStart`, while `Voice.render` derives
> `offset = maxOf(blockStart, startFrame) - blockStart`. Those are the same expression, so the
> callee's clamp is what makes the raw `blockStart` mean "the voice's position in this block".
> Neither end of that coupling says so; the new spec's KDoc now does.
>
> The finding's *first* question — that `FilterModulationTest`'s test does not guard its own name —
> is untouched and remains true. It is simply no longer the only thing standing there.

**Why:** with the clamp gone, `absPos` goes negative, which lands in the *attack* branch of
`envelopeLevelAtPosition`. Every built-in curve is monotonic increasing with `shape(0) = 0`, so a negative `p` yields
`≤ 0`, and the function's trailing `envValue.coerceIn(0.0, 1.0)` floors it back to exactly `0.0` — the same value the
clamp produces. The release branch is unreachable for a negative `absPos` (`gateEndPos ≥ 0`).

**Two things to decide, and they point in opposite directions:**

1. The test does not guard what its name says. Either it needs a case that can distinguish the two (the parameters would
   have to reach the release branch), or the name is wrong.
2. **The clamp may be genuinely redundant** with the trailing `coerceIn`. If so, one of the two is the real guard and
   the other is decoration — worth knowing which, since `EnvelopeCalc` is on the control-rate path.

---

## F4 — "filter without modulator is not modified" cannot fail ❌ WITHDRAWN

**LOW.** `FilterModulationTest`, test 1. It asserts that a voice built with an empty
`filterModulators` list never calls `setCutoff`.

**Evidence (mutation S3).** Deleted the `if (modulators.isNotEmpty())` guard at
`FilterPipelineBuilder.kt:47` *(anchor re-verified 2026-08-31)* so the renderer is added unconditionally: **GREEN**.

> ❌ **WITHDRAWN 2026-08-31 — wrong when written, not stale.** The same mutation re-run against
> the **whole** suite is **KILLED**, by `PipelinePresetSpec`: *"Modern minimal: with all effects off,
> envelope is still last"* and *"Pedal minimal: … envelope is still first"*. Both assert the exact
> renderer list for an effects-free pipeline, so an unconditionally-added `FilterModRenderer` shows
> up as a surplus stage.
>
> The pilot ran mutation S3 against `FilterModulationTest` only and reported the result as
> "**nothing** guards that it stays" — a file-scoped run stated as a suite-wide absence.
> `PipelinePresetSpec` was added **2026-06-29**, five weeks before the pilot.
>
> **Second finding falsified by that same spec** (see [F14](#f14)'s withdrawn Pedal bullet). What
> survives is only the narrow, already-recorded observation that `FilterModulationTest` test 1 is
> guaranteed by the shape of its input rather than by production code — true, harmless, and not a
> coverage hole, because the guard it worried about is guarded elsewhere.

**Why:** `FilterModRenderer.render()` is `for (mod in modulators) { … }`. With an empty list the body never executes, so
adding the renderer changes nothing observable. The assertion is guaranteed by the *shape of the input*, not by any line
of production code — there is no single-line mutation that can make it fail. `AudioFilterRenderer` calls only
`filter.process(...)`, never `setCutoff`.

Consequence: the `isNotEmpty()` guard is an allocation/iteration optimisation, not a behaviour, and **nothing guards
that it stays** — removing it is invisible to the suite and costs one renderer slot plus a per-block loop entry on every
voice that has no filter modulation (the common case).

---

## F5 — Coverage holes found while verifying the filter strip ✅ CLOSED *(1 withdrawn, 2 fixed — 2026-08-31)*

**MED**, cumulatively. None of these is exercised by any spec in `voices/`:

- ~~**`FilterModRenderer`'s drift path** (`:32`)~~ ❌ **WITHDRAWN 2026-08-31.** `FilterEnvSemitoneSpec`
  has a row — *"strip path: drift stays a pure multiplier OUTSIDE the semitone exponent"* — that
  builds a `Voice.FilterModulator` with a real `AnalogDrift`, drives `FilterModRenderer`, and pins
  both that the depth-12 / depth-0 cutoff ratio is exactly 2.0 (so drift is outside the exponent) and
  that drift actually moved the cutoff off the pure formula value. That is a better guard than the
  finding asked for. The finding said "no case in **`FilterModulationTest`**" and then generalised;
  the spec that covers it lives in `voices/strip/filter/`, outside the pilot's survey. **Fifth
  finding in this audit whose "untested" claim did not survive checking** (cf. [F4](#f4), [F7](#f7),
  [F14](#f14), [F20](#f20)). The analog-drift feature is therefore implicated once, not twice.
- **`VoiceFactory.toModulator()`** (~`:430-480`) — ✅ **FIXED 2026-08-31** by
  `VoiceFactoryFilterEnvWireSpec`. The claim held: every `makeVoice` spec omits `FilterDef.envelope`,
  so the function took its `envData == null && drift == null` early return every time and no real
  branch ran. `FilterEnvSemitoneSpec` covers the semitone law well but builds `Voice.FilterModulator`
  by hand — it starts *after* this wire.
  **That is the [F18](#f18) shape exactly: both ends covered, the join empty.** The mutation proving
  it — `is FilterDef.LowPass -> this.envelope` becomes `-> null`, so the parameter silently never
  arrives — is now red, as is a depth sign flip. Measured as output energy rather than as a cutoff,
  since `makeVoice` builds the filter internally and there is no spy to inject: a sawtooth through a
  300 Hz lowpass opened by +24 semitones (×4) is a large, sign-fixed energy change.
  *(Remaining, and deliberately not chased: the drift-only branch — `analog > 0` with no envelope —
  cannot be isolated behaviourally, because `analog` also drives oscillator drift and unison jitter.
  It needs an injectable filter, not a bigger render.)*
  Original text follows. `FilterModulationTest`
  constructs `Voice.FilterModulator` directly, bypassing the factory; `VoiceFactoryFilterOrderSpec`
  uses filter defs with no envelope, so it never reaches it either. Untested branches: the non-`Tunable` early return
  (e.g. `Formant`), the `envData == null && drift == null` early return, the degenerate `depth = 0` envelope built for
  drift-only modulation, and `envData.resolve()`.
- **Body/Formant exclusion from the per-voice chain** (`VoiceFactory.kt:108`) — ✅ **FIXED 2026-08-31**
  by `VoiceFactoryBodyVowelRoutingSpec` (4 rows, 2 mutations, both killed). The claim was accurate:
  nothing put a Body or Formant alongside LP/HP through `makeVoice`. The rows pin that the resonators
  leave the baked chain, that the survivors keep their authored order, and that the defs still ride on
  `Voice.body` / `Voice.vowel` for the Katalyst. **Mutation Mu — leaving the resonators in the chain —
  is the 2026-07-04 regression itself, and it is now red.**

  > Learned while writing it: `mainFilter` is only a `ChainAudioFilter` when more than one filter
  > survives the split. With one it is that filter unwrapped, with none it is null — so removing a
  > Body changes the *shape* of `mainFilter`, not just its length, and a helper that assumed a chain
  > fails on exactly the case the spec exists to test.

---

## F6 — Nine tests contain no assertions at all ✅ FIXED

**HIGH.** They render and then check nothing; the expected values exist only as comments. No mutation can ever turn them
red — they pass as long as the code does not throw. Counted directly from source, not inferred:

| Spec                      | Tests with zero assertions                                                                                                                                                                                                                              | of |
|---------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---:|
| `SampleVoiceSpecificTest` | `SampleVoice performs linear interpolation` · `…playhead advances correctly` · `…with vibrato modulates playback rate` · `…with FM modulates playback rate` · `…getBaseFrequency returns sample base pitch` · `…with all modulations renders correctly` | 16 |
| `SynthVoiceTest`          | `SynthVoice getBaseFrequency returns freqHz` · `…with filter affects signal output` · `…with all modulations renders correctly`                                                                                                                         | 12 |

**9 of those 28 tests — 32% — are smoke tests wearing behavioural names.** Two of them state the problem in their own
body: *"(Can't directly verify playhead without access to private field)"*.

The names are the damage: `…performs linear interpolation` reads as a guard on the interpolation formula, and
`…with filter affects signal output` reads as a guard that the filter is wired in. Neither checks anything. Anyone
auditing coverage by reading test names — which is what everyone does — is misled.

**Note:** as smoke tests they are not worthless (a crash or an exception still fails). The defect is that they are
indistinguishable from real guards.

> ✅ **FIXED 2026-08-31 — all nine now assert, and each has a mutation that kills it.**
>
> | test | designated killer |
> |------|-------------------|
> | `…performs linear interpolation` | interpolator truncates instead of lerping |
> | `…playhead advances correctly` | starting playhead ignored |
> | `…with vibrato modulates playback rate` | vibrato depth → 0 |
> | `…with FM modulates playback rate` | FM depth → 0 |
> | `…freqHz shapes the FM modulation` *(renamed)* | FM depth → 0 |
> | `…with all modulations renders correctly` (×2) | voice renders silence |
> | `SynthVoice passes its freqHz to the ignitor` *(renamed)* | oscillator pitch hard-coded |
> | `SynthVoice with filter affects signal output` | main filter never runs |
>
> **Two fixtures were changed on purpose**, because the old ones could not show what the test was
> named for: a constant sample became a ramp (which is why the old body could only say *"can't
> directly verify playhead without access to private field"* — with a ramp, the output value *is*
> the playhead), and 100-frame blocks became 4000 (a 5 Hz vibrato over 2.3 ms barely leaves zero,
> so the test could not have observed its own subject).
>
> **Two tests were renamed because `getBaseFrequency` does not exist anywhere in the codebase.**
> They were named for a symbol that is not there, so nothing could ever have guarded them. Rather
> than delete the coverage, both now guard what their comments actually described.
>
> **One test was ALSO renamed for overstating its guard**, and this is the useful part: mutation Md
> (`modFreq = freqHz * ratio` → `modFreq = ratio`) **survived**. `FmRenderer` uses `freqHz` twice —
> once to set the modulator's speed, once to normalise its depth (`fmMult = 1 + modSignal/freqHz`) —
> and killing the first leaves the second still separating the renders. The row is now
> *"freqHz shapes the FM modulation"*, which is what it proves.
> **Residual gap, recorded not fixed: nothing in the suite distinguishes those two roles of
> `freqHz`.** Md is an un-killed mutation and stays on the books.

---

## F7 — Three known holes: all CONFIRMED 🟡 *(solo + cut/choke now guarded 2026-08-31)*

**HIGH.** Independently verified, and they are the load-bearing parts of the voice path:

- **`VoiceScheduler.kt` (407 lines) has no spec, and is not reached by any spec.** All 33 lifecycle/pipeline tests call
  `voice.render()` directly. Repo-wide, `VoiceScheduler` is constructed only in `PlaybackEngine.kt` — never in a test
  file. Its old
  `VoiceSchedulerDiagnosticsTest` was deleted when diagnostics moved to the dispatcher and nothing replaced it. This is
  the highest-churn file in the module.

  > **Re-verified 2026-08-31 — the first half holds, the second half is WRONG.**
  >
  > **Holds:** the file is now **576 lines** (+41% in the four weeks the finding sat unreviewed) and
  > still has no spec of its own. The hard-cut TODO moved to `:558`.
  >
  > **Wrong: "is not reached by any spec".** It is reached and *driven*, by at least two —
  > `SampleVoiceOnsetSpec` and `SeededPlaybackReproducibilitySpec`. Both build a `Rig` around
  > `AudioBackendContext.create(...)` + `PlaybackEngine.create(context)`, call
  > `engine.scheduler.scheduleVoice(...)` (that is `VoiceScheduler.scheduleVoice`), and drive
  > `process` → `promoteScheduled` → `activateVoice` on every rendered block.
  >
  > **How both the finding and its re-verification got this wrong:** a grep for the string
  > `VoiceScheduler` in `commonTest` returns only comments, because these specs reach the object
  > through `engine.scheduler` and never name the type. The finding's own phrasing —
  > "constructed only in `PlaybackEngine.kt`" — is literally true and led to a false conclusion.
  > **Third instance in this audit of a grep standing in for reading the code** (see [F14](#f14),
  > [F15](#f15)); the first two were the pilot's, this one is the re-verification's own.
  >
  > **What survives, and it is still worth fixing:** no spec takes the scheduler's own logic as its
  > *subject*, and **solo/mute and cut/choke specifically still have zero coverage** (re-grepped:
  > `solo`, `choke`, `cutGroup` return nothing across `commonTest`). But the cost collapses — the
  > `Rig` fixture already exists and is copyable, so this is a spec to write, not a harness to build.
- **`SendRenderer.kt` — zero test references, but not dead.** Nuance worth keeping: it *executes* on every `render()` in
  all 33 tests, because `Voice.kt:81` appends it unconditionally. So it runs blind — no test ever inspects its output
  (pan, gain, postGain, or the delay/reverb send writes). Untested, not unexercised; a bug there fails silently rather
  than visibly.
- **solo/mute and cut/choke — zero tests anywhere.** A repo-wide grep of `commonTest` for `solo`,
  `cut` and `choke` returns nothing. *(Re-verified 2026-08-31: `solo`, `choke` and `cutGroup` still
  return nothing across all of `commonTest`.)* `VoiceScheduler.process`'s solo ramp and `promoteScheduled`'s cut-group hard-kill
  are both entirely unexercised. The hard-kill is also the site of the standing
  `VoiceScheduler.kt:558` TODO — *"Use a fade out / release phase instead of hard cut?"* — i.e. a known click source
  with no test.

  > ✅ **CLOSED 2026-08-31** by `VoiceSchedulerSoloCutSpec` — 11 rows, 7 mutations, 7 killed.
  > Six rows cover the solo duck (ramp shape, the 0.05 floor, max-not-min across sources, the
  > `sourceId != null` conjunct, and that the soloed source is itself exempt); four cover cut/choke.
  > Row 1 is a deliberate positive control with no mutation of its own: it exists so that a wrong pan
  > convention or a 0 Hz oscillator cannot quietly make the other ten vacuous.
  > **Writing it found [F18](#f18) on the first run** — cut was inert on every synth voice.
  > Still open in this bullet: the hard-kill's *click* is now observable but not guarded as an audio
  > property, and mute has no rows of its own.

---

## F8 — Three tests are named for behaviour they structurally cannot reach 🔴

**MED.** Different from a wide tolerance: in each case the chosen parameters make the named code path *unreachable*, so
the test would pass even if that path were deleted.

1. **`EnvelopeTest` — "envelope clamps negative values to zero".**
   **Evidence (mutation V1):** deleted `EnvelopeRenderer.kt:147` (`if (currentEnv < 0.0) currentEnv =
   0.0`) *(anchor re-verified 2026-08-31; code unchanged)* — the whole `EnvelopeTest` stayed **GREEN**.
   **Mutation RE-RUN 2026-08-31 against the entire suite (1373 tests): still GREEN.** Stands. Why: it is a single first-ever `render()` at `blockStart = 200`
   with no priming render, so
   `env.releaseStartLevel` is still its default `0.0` when the release branch primes it. Every release output is
   `0.0 * shape` = `0.0` for *any* shape. The clamp cannot be observed.

2. **`EnvelopeTest` — "full ADSR cycle works correctly".** Its asserted indices are 20, 80, 90, 700, 850, 899. The decay
   window is frames **100–199**. Nothing is sampled inside it, and the post-decay plateau at 700 is curve-independent —
   so a decay-curve bug produces no failing assertion. It tests attack direction, sustain value and release direction.
   Not decay.

3. **`EnvelopeShapeTest` — "decay/sustain boundary — all curves arrive at sustain".** It loops
   `for (curve in AdsrCurve.entries)` at `absPos = 200` where `attackFrames + decayFrames = 200`. The guard
   `absPos < attack + decay` is `200 < 200` → **false for every curve**, so the loop hits the curve-agnostic `else`
   branch six times. It can never distinguish a per-curve boundary bug; the
   `for` loop over all six curves is decoration.

> ✅ **ALL THREE FIXED 2026-08-31.**
>
> **(1) The clamp's REACH was the missing piece, not the test's parameters.** Walking
> `EnvelopeRenderer`'s branches shows no amount of release tuning could have helped: attack has
> `p` in `[0,1)`, decay has `omp` in `(0,1]`, and release clamps `p` with `coerceAtMost(1.0)` so
> `omp >= 0`. Every path is non-negative **unless `sustain` itself is negative** — and the VCA path
> allows exactly that, because `Voice.kt:247` passes `sustainLevel = adsr.sustain` straight through.
> The ignitor door coerces to `[0,1]` (`IgnitorEnvelopes.kt:75`); the strip VCA does not, and under
> the raw-Motor rule that is a choice rather than an oversight. So a **negative sustain** is the
> clamp's actual reach, and without the clamp the voice renders **phase-inverted at half level**
> rather than silent. A new row covers it; the original is kept and annotated, since it does document
> the past-release case.
>
> **(2)** Two samples now land inside the decay window, which nothing did before. They sit at 150 and
> 190 rather than straddling the window's start: the gain smoother lags, so the rendered peak trails
> the raw envelope's and a 99-vs-120 comparison measures the smoother catching up, not the decay.
> *(Learned by writing it that way first and watching it fail.)*
>
> **(3)** A mid-decay row pins the golden values the documented law predicts (Linear 0.65, Square
> 0.475, Cube 0.3875 at sustain 0.3) and asserts the six curves are distinct — **at the quarter point,
> not the midpoint.** At the midpoint only five of six values differ, and that is mathematics rather
> than a defect: `SCurve` is piecewise around 0.5 and evaluates to exactly 0.5 there, which is also
> Linear's value, because an S-curve passes through its own midpoint by construction.
>
> Mutations: disabling the negative clamp, and perturbing the decay law. Both red.

---

## F9 — A misattribution worth keeping as a method note 🟢

**Not a defect — the suite is fine here.** Recorded because it nearly became a false finding.

`SampleVoiceSpecificTest`'s vibrato and FM tests have zero assertions ([F6](#f6)), so severing
`phaseMod` from the sample playhead (`SampleIgnitor.kt:95`) leaves that spec **GREEN**. But the same mutation run
against the **whole** `audio_be` suite comes back **RED** — the behaviour is guarded elsewhere (the pitch/FM specs).

**Method consequence:** a green single-spec result proves the *spec* is weak, never that the *behaviour* is unguarded.
Every candidate coverage hole must be re-run suite-wide before it is called a hole. The full suite is 6.9 s, so this is
cheap — do it every time.

---

## F10 — An inverted pitch glide passes the entire suite ✅ CLOSED (by later work)

**HIGH — the most serious finding of the pilot.** `AccelerateRenderer` glides pitch over the voice's lifetime. Two tests
are named for its *direction*:

- `PitchModulationTest` — *"accelerate with positive amount increases pitch over time"*
- `PitchModulationTest` — *"accelerate with negative rate decreases pitch over time"*

Neither checks direction. Both assert only magnitude — `diffFirstSecond > 1e-4`,
`diffFromRef > 1e-4`.

**Evidence (mutation W1).** Negated the glide exponent at `AccelerateRenderer.kt:30` and `:37`
(`2.0.pow(amount / totalFrames)` → `2.0.pow(-amount / totalFrames)`), so every rising glide falls and every falling
glide rises. `PitchModulationTest`: **GREEN**. The **whole `audio_be` suite: GREEN.**

A note that swoops the wrong way is not subtle — it is the kind of thing you hear on the first bar. Nothing in 943 tests
would tell us.

> **CLOSED 2026-08-31 — re-verified, and it no longer reproduces.** The same mutation (negate the
> glide exponent at both sites) now turns the WHOLE suite RED. The killer is
> `AccelerateSemitoneLawSpec > "strip path: accelerate(24 st) is exactly ratio 2.0 at half the
> voice"`, a spec that did not exist when the pilot ran. The pilot's most serious finding was
> closed incidentally by later work. **Method note: this is why every finding gets its mutation
> re-run before it costs a triage decision — three of seventeen had moved in four weeks.**

---

## F11 — An FM modulator running 1000× too slow passes the entire suite ✅ FIXED

**HIGH.** `FmSynthesisTest` — *"FM modulator phase advances correctly"*. Its assertion is
`afterPhase > initialPhase`: the phase moved by *some* positive amount.

**Evidence (mutation W2).** Multiplied `modInc` by `0.001` at `FmRenderer.kt:39`, so the modulator advances ~0.006 rad
over 100 frames instead of ~6.3 rad. `FmSynthesisTest`: **GREEN**. The **whole suite: GREEN.**

That is not a detuned FM patch, it is a different instrument.

> **RE-CONFIRMED 2026-08-31** against the current tree (whole suite still GREEN under the
> mutation), then **FIXED**: the test now asserts the phase QUANTITY, derived from the definition
> of an FM modulator (`frames × TWO_PI × freq × ratio / sr`, wrapped once) rather than from the
> renderer, to 1e-9. The pilot's own mutation is now killed by it. Renamed to
> *"FM modulator phase advances by the EXPECTED amount, not merely upward"*.

The same shape recurs across this spec:
several tests assert `diff > 1e-3` against a clean baseline, which any nonzero FM satisfies — so they confirm "FM is
on", not "FM is right".

**Counter-evidence, for balance:** pinning the modulator to 1:1 by dropping `fm.ratio`
(`FmRenderer.kt:38`, mutation W3) **is** caught. The ratio is genuinely guarded; the rate and the envelope shape are
not.

---

## F12 — "disabled" tests compare a value against its own default ✅ FIXED

**MED.** A recurring shape across `PitchModulationTest` and `FmSynthesisTest`: a test asserts that some feature at
zero/null "produces no modulation" by comparing two voices — but *both* voices are configured identically, because the
explicit value equals the default.

- *"vibrato with depth 0 produces no modulation"* — both voices have `depth = 0.0`.
- *"accelerate with rate 0 produces no pitch change"* — both have `amount = 0.0`.
- *"pitch envelope with null is disabled"* — `pitchEnvelope = null` explicitly vs. `= null` by default. Literally the
  same configuration.
- *"FM with depth 0 produces no modulation"* / *"FM with null is disabled"* — same.

The renderer is never constructed in either branch (the pipeline gate excludes it), so the comparison is between two
identical runs. And even if the gate were relaxed, the math is an identity at zero (`2^(sin(φ)·0/12) = 1`,
`effectiveDepth = 0`). **No mutation can falsify these** — they are true by construction, not by behaviour.

> **FIXED 2026-08-31 by adding a POSITIVE CONTROL to each.** A zero-claim is only meaningful next
> to a run that proves the comparison can see a difference at all, so each test is now a two- or
> three-way: bare ≡ zero-configured, and bare ≢ really-configured. The two FM tests were MERGED —
> *"FM with depth 0 produces no modulation"* and *"FM with null is disabled"* were the same claim,
> and the second only asserted the voice made some sound, which its name does not promise.
>
> Mutation-checked: disabling vibrato / accelerate / pitch-envelope / FM outright in
> `PitchPipelineBuilder` now turns the respective test RED — under the old tests every one of
> those was green.
>
> **One half remains unfalsifiable, and correctly so.** Relaxing the FM gate to `if (fm != null)`
> so a depth-0 FM really does build an `FmRenderer` SURVIVES: `effectiveDepth = 0` makes
> `fmMult = 1.0`, so the renderer is a mathematical identity and no output oracle can see it. That
> gate is an allocation/iteration optimisation, not a behaviour — the same category as
> [F4](#f4), and recorded rather than chased.

---

## F13 — Copy-paste tests that duplicate a sibling instead of testing their name ✅ FIXED

**MED.** Found by W5 in the lifecycle/pipeline specs; these are wrong *records*, not just weak ones:

- `VoiceLifecycleTest` — *"voice **ending** at block boundary renders full block"* is **byte-for-byte identical** in
  setup and assertions to the preceding *"voice **starting** at block boundary…"*. No end-boundary scenario is
  constructed anywhere in it.
- `VoiceLifecycleTest` — *"voice at exact block boundaries handles edge cases"*: its third sub-case (commented *"ends
  exactly at endFrame"*) reuses the exact `blockStart = 100, blockFrames = 100` of the second. The end-boundary case is
  never queried — and the comment on `ctx4`, which *does* cover it, mislabels it as "starts exactly at endFrame".
> ✅ **`VoiceLifecycleTest`'s two items FIXED 2026-08-31.**
> *"voice ending at block boundary"* now renders the block containing the end **and** the next block,
> which starts exactly at `endFrame` — the half that was missing, since the test was byte-identical
> to its "starting" sibling. *"voice at exact block boundaries"*' third sub-case is now a block that
> **straddles** `endFrame` (frames 150–250 on a voice ending at 200), asserting signal in the first
> half and silence in the second.
>
> **One correction to this finding while fixing it:** it claimed `ctx4`'s comment mislabels the case
> as *"starts exactly at endFrame"*. That comment is accurate — `ctx4` does start at `endFrame`. The
> real gap was different: `ctx2`'s block `[100, 200)` already both starts at `startFrame` and ends at
> `endFrame`, so *"ends exactly at endFrame"* had no distinct scenario left, and the straddle is what
> was genuinely untested.
>
> Mutations: `>=` → `>` on the end test, and `minOf(blockEnd, endFrame)` → `blockEnd`. Both kill the
> rewritten rows. **Honest scope: sibling tests catch both mutations too**, so the gain here is that
> the tests stop being false records of coverage, not that a new regression became detectable.
>
> 🔴 **Still open: all three `VoicePipelineTest` items below.** No ordering is verified anywhere in
> that spec.

- `VoicePipelineTest` — *"envelope is applied **after** main filter"*: its assertion (`processCalls.size shouldBe 1`) is
  identical to the unrelated *"pipeline executes main filter"*. **No ordering is checked anywhere in the spec.**
- `VoicePipelineTest` — *"filter modulation updates cutoff **before** filter processes"*: the
  "before" is never verified.
- `VoicePipelineTest` — *"voice starting/ending mid-block renders **partial buffer**"*: no buffer content is ever
  inspected, only the lifecycle boolean. (The identically-named tests in
  `VoiceLifecycleTest` *do* check the buffer — so the two specs disagree about what the name means.)

> ✅ **The `VoicePipelineTest` half FIXED 2026-08-31**, which closes F13.
>
> **A counting spy cannot see order.** `SpyFilter` recorded only how many times it ran, so both
> ordering rows asserted `processCalls.size shouldBe 1` — true under either order. It now also
> records **what it was handed** (`seenAtProcess`), which makes order observable: a stage running
> after the VCA sees an enveloped signal, one running before sees the raw exciter. With a 100-frame
> attack the VCA's gain at frame 0 is ~0, so the filter seeing `1.0` proves it ran first.
> `TunableSpyFilter` additionally records how many `setCutoff` calls had landed **when `process`
> began** — two counters compared afterwards are both 1 either way.
>
> **The partial-buffer row needed a sentinel, and finding that out is the lesson.** Asserting zeros
> before the onset looked right and was toothless: a mutant ignoring the onset entirely
> (`vStart = ctx.blockStart`) still produced zeros there, because the *envelope* floors a negative
> position — the zeros were never evidence of windowing. Against a buffer pre-filled with a sentinel,
> "the voice writes only `[offset, offset+length)`" becomes checkable, and that mutation is now red.
> **The first version of this fix was itself a toothless test, caught only by mutating it.**
>
> Mutations: Modern's stage list reordered to run the VCA before the filter (which is genuinely
> `Pedal`'s order, so the claim is preset-specific and correctly so); the `FilterMod` stage removed;
> and the onset ignored. Each kills exactly its own row.

---

## F14 — Coverage holes beyond the three known ones 🔴

**MED.** Production behaviour that no spec in `voices/` claims:

- ~~**`Voice.Ducking` (sidechain) — untested by anything at the `Voice`/`VoiceScheduler` layer.**~~
  📋 **REMOVED FROM SCOPE 2026-08-31 (maintainer): "it is not used yet and was never really
  tested yet, so let us not waste our time right now."** The observation was correct — the DSL, the
  bus effect and the DSP all have specs, and the *join* between them (`Cylinder.kt:198-203`,
  `Cylinders.kt:87-89`) has none, which is the same both-ends-covered-middle-empty shape that hid
  [F18](#f18). But no shipped song uses ducking, so this is unfinished work rather than a coverage
  hole in something live. Moved with its design questions to
  [`docs/tasks/future/ducking-unfinished.md`](../tasks/future/ducking-unfinished.md) — including the
  one worth fixing before anyone writes a song against it: `duckattack` sets the **release**, and
  `Compressor` in the same directory has an `attackSeconds` that means something else again.
- ~~**`Voice.Compressor`'s DSP is untested.**~~ ❌ **WITHDRAWN 2026-08-31.** The DSP has two dedicated
  specs: `effects/CompressorSpec` asserts gain reduction above threshold, transparency below it,
  stereo behaviour, the soft knee, `reset`, and the `+Inf` guard; `effects/CompressorSmoothnessSpec`
  covers the coefficient blend. The orbit wiring at the very lines this bullet cites
  (`Cylinder.kt:181-216`) is covered by `CylinderCompressorSpec` — creation, parameter application,
  first-writer-wins, reuse, clearing, and envelope preservation across voices — and the bus effect by
  `KatalystCompressorEffectSpec`. **Sixth "untested" claim in this audit that did not survive
  checking.**
  What survives is only the naming half, and it is fair: `VoiceCompressorSpec` tests *string parsing*
  while its name reads as coverage of the compressor.
- **`VoiceFactory` (563 lines) is bypassed by all 37 lifecycle/pipeline tests** —
  `VoiceTestHelpers.createVoice` hand-rolls a *parallel* pipeline construction instead of calling
  `makeVoice()`. So the helper and production can drift apart silently. Untouched by that path:
  `gain = baseGain * velocity`, legato/clip duration math, sample loop/pitch-ratio resolution,
  `perVoiceCutoffOffsetMul` randomisation, and the ADSR merge with sample metadata. (`VoiceFactoryFilterOrderSpec` does
  exercise `makeVoice`, but only for filter ordering.)
- ~~**`PipelinePreset.Pedal` is never exercised** — `Modern` is hard-coded into `VoiceTestHelpers`.~~
  **❌ WITHDRAWN 2026-08-31 — this was wrong when written, not stale.** `PipelinePresetSpec` builds an
  active pipeline from `PipelinePreset.Pedal` at `:66` and `:75`, and that spec was added **2026-06-29**,
  five weeks *before* the pilot ran. The pilot grepped `voices/` and phrased the result suite-wide.
  What survives: `VoiceTestHelpers` does hard-code `Modern`, so the *voice-strip* path is Modern-only.
- ~~**`Voice.Fm` is `null` in every lifecycle/pipeline test**, and `FilterModulator.drift` is `null`
  everywhere~~ — **largely OVERTAKEN 2026-08-31.** `Voice.Fm` is now driven by
  `SampleVoiceSpecificTest` (two rows, mutation-checked) and by the strip-door rows of
  `BlockFramingInvarianceSpec`; `FilterModulator.drift` was already covered by `FilterEnvSemitoneSpec`
  (see the withdrawal in [F5](#f5)). The "third independent sighting of the analog-drift gap" reading
  does not hold: two of the three sightings were the same missed spec.

> **Where F14 stands after re-verification: two bullets withdrawn (Pedal, compressor), one parked to
> a future task (ducking), one overtaken (Fm/drift). One survives** — `VoiceFactory` is still bypassed
> by the lifecycle/pipeline tests, because `VoiceTestHelpers.createVoice` hand-rolls a *parallel*
> pipeline construction instead of calling `makeVoice`, so the helper and production can drift apart
> silently. That one is real and unfixed. Fixing it means rewriting the foundation those ~37 tests
> stand on, which is a maintainer call rather than an audit repair.

---

## F15 — Suite-wide: 21 assertion-free tests, in three distinct classes 🔴

> **Recounted 2026-08-31, and the original count was too high.** The pilot's census matched
> `shouldBe`-shaped assertions and missed this repo's **custom infix matchers** — `intShouldBeLessThan`,
> `intShouldBeGreaterThan`, `should beGreaterThanOrEqualTo`. Three tests filed as defects below assert
> perfectly well through those. Recount method: strip `//` and `/* */` first (these tests keep their
> expected values in comments, so matching `should` unstripped reads a comment as an assertion), then
> match the infix forms too. **Corrected: 21 assertion-free of 1373 tests** (the suite has grown from
> 943). A further 4 hits in `IgnitorFilterEnvSemitoneSpec` are **not** defects either — all four
> delegate to a helper, `envEqualsStatic()`, which carries the `maxDiff shouldBeLessThan 1e-12`.
> A line-level census cannot see through a helper call; only reading the body settles it.

Extends [F6](#f6) from the `voices/` pilot to the whole `audio_be` tree. The classification matters — only the first
class is a defect:

**(a) Named for a behaviour, checks nothing — ~~10~~ 0 tests. ✅ ALL FIXED 2026-08-31.**
~~The 9 from [F6](#f6)~~ — fixed, see [F6](#f6) — and ~~`VoicePipelineTest` *"voice renders correct
number of samples"*~~, which rendered and asserted absolutely nothing. It now fills the buffer with a
sentinel first and counts what changed, so "how many samples" is literally countable; shortening the
render window by one sample turns it red.

> Classes **(b)** and **(c)** are unchanged and are NOT defects: (b) is five honestly-named smoke
> tests, and (c) is the `GuitarClickHuntTest` diagnostic harness, whose only open question is whether
> it belongs in the default `jvmTest` run — **a maintainer decision, not a fix.**

> **Three former members of this class are WITHDRAWN 2026-08-31 — they do assert:**
> `ShapingFuncsBoundsSpec` *"rectify output is always non-negative"* (renamed from
> `ClippingFuncsBoundsSpec`) loops every input through
> `withClue(...) { y should beGreaterThanOrEqualTo(0.0) }`; `IgnitorCombinatorsSpec` *"crush"* ends on
> `wetUnique intShouldBeLessThan dryUnique`; *"accelerate"* counts zero crossings per half and ends on
> `crossingsSecondHalf intShouldBeGreaterThan crossingsFirstHalf`. The accelerate one was cited as the
> "third independent gap in accelerate coverage" — **that claim is withdrawn too**; accelerate is
> covered here AND by `AccelerateSemitoneLawSpec`.

**(b) Honestly-named smoke tests — 5 tests. Acceptable as-is.**
`LowPassHighPassFiltersSpec` *"…zero-length buffer does not crash"* ×3 — the assertion *is* "does not throw";
`VoicePipelineTest` *"tremolo renders successfully"* / *"phaser renders successfully with defaults"*. The name promises
exactly what is delivered. Worth keeping, worth not counting as coverage — and note this leaves `TremoloRenderer` and
`StripPhaserRenderer` with no behavioural test at all.

> **Half closed 2026-08-31:** `TremoloRenderer` now has `TremoloRendererSpec` (15 rows, added by
> the block-framing W10 round). `StripPhaserRenderer` still has none.

**(c) `GuitarClickHuntTest` — 6 of 7 (one has gained an assertion since), and a separate question.**
This is the standing click-diagnostic harness; it prints and guards nothing, by design. But it is **5.4 s of the suite's
6.9 s — 78% of total runtime for zero assertions.** Worth deciding whether a diagnostic probe belongs in the default
`jvmTest` run or behind a tag.

---

## F16 — The master limiter did not limit transients; the hard clip did ✅ FIXED

**HIGH — user-reported symptom ("knock"), root-caused 2026-08-04, FIXED 2026-08-06** (`53834ba9` + follow-up) under [
`docs/tasks/master-limiter-lookahead.md`](../tasks/master-limiter-lookahead.md). By-ear confirmed.

> **Everything below describes the DEFECT as it was**, kept because it is the evidence and the
> measurement baseline. After the fix, the same +12 dB kick exits at **−0.37 dBFS with zero samples
> clipped**, and `MasterStage` runs **DC blockers → limiter → clip** with 5 ms of lookahead. Guarded
> by `LimiterLookaheadSpec`, which was written first and watched failing at 176 samples over.

`Compressor` (`effects/Compressor.kt`) is feed-forward with **no lookahead and no delay line**, so the detector sees a
sample at the same instant the signal does. `MasterStage.process` is limiter → DC blocker → **hard clip at ±1.0**.

**Evidence — measured against the real `Compressor` with `MasterStage`'s own constants**
(−1 dB, 20:1, 2 dB knee, 1 ms attack, 100 ms release), 55 Hz kick-like transient:

| Kick peak in | Peak out        | Hard-clipped for |
|--------------|-----------------|------------------|
| 0 dBFS       | −0.33 dBFS      | 0 ms             |
| +6 dBFS      | **+5.67 dBFS**  | **3.99 ms**      |
| +12 dBFS     | **+11.67 dBFS** | **5.22 ms**      |
| +18 dBFS     | **+17.67 dBFS** | **5.90 ms**      |

At t = 1 ms into a +18 dB transient the gain is still **exactly 0.00 dB**. The ceiling is enforced by the clip, not the
limiter — so every loud transient is a 2–6 ms hard-clipped burst, and the window grows with the amount of limiting. On
low-frequency content that is the reported knock.

**Ruled out** (both measured, so they do not get re-investigated):

- *Envelope ripple / low-frequency pumping* — steady-state gain ripple is ≤ 1 dB pk-pk even at 40 Hz.
- *Cold-envelope startup* — with the envelope fully warm (bed at −1 dBFS, already limiting) a +12 dB kick still reaches
  +8.43 dBFS and clips for 3.27 ms.
- *The `ENV_COEFF_BLEND_DB` crackle fix* — disabling it changes the peak by 0.14 dB.

It is structural: a feed-forward limiter cannot reduce a peak it has not seen.

**Sub-finding — a documented invariant that is false.** `MasterStage.kt:58` states the DC blockers *"run AFTER the
limiter so input is already ±1-bounded — no rail-edge transient, no need for downstream softCap."* They are in fact
receiving up to +8 dBFS. A 7 Hz high-pass fed a clipped asymmetric burst rings with a low-frequency tail, compounding
the thump.

---

## F17 — REFUTED: the blend is innocent. The real cause is the detector. 🟡 *(b) closed by ear 2026-08-31*

**The hypothesis was wrong, and the measurement is worth keeping.** A by-ear session (2026-08-06)
found a glue compressor at 2:1 / −8 dB with a 30 ms attack producing audible "shocks" and needing 15 ms. I proposed
`ENV_COEFF_BLEND_DB`'s ±2 dB coefficient blend as the cause, reasoning that a low-ratio stage produces 1–3 dB of
reduction and therefore sits inside the blend window.

**Measured effective attack (t90 of gain reduction), blend on vs off:**

| step                            | ratio 2 @ −8 | ratio 4 @ −4 | ratio 20 @ −1 |
|---------------------------------|--------------|--------------|---------------|
| from silence                    | 1.04×        | 1.04×        | 1.04×         |
| 8 dB step                       | 1.05–1.07×   | 1.05–1.07×   | 1.05–1.08×    |
| +3 dB step at the working point | 1.15–1.24×   | 1.15–1.24×   | 1.15–1.24×    |

**Identical across every ratio** — as the mechanism requires, since the blend is a function of the envelope error in dB,
which `ratio` does not touch. Worst case 1.24×, tracking *step size* not ratio. **Do not scale `ENV_COEFF_BLEND_DB`;
there is nothing there to fix.**

### The two real causes

**(a) A convention gap — `attackSeconds` is a one-pole τ, not a rise time.** Measured t10-90 is **2.33–2.54 × τ** at
every setting. So a configured 30 ms behaves like a ~74 ms attack in the units a DAW would label it, and the 15 ms the
user landed on gives t10-90 = 37.6 ms — almost exactly what a DAW would call "30 ms". The ear found the right number for
a misleadingly-labelled knob.

**(b) HIGH — the configured attack silently moves the effective threshold.** The dB-domain follower is fed instantaneous
`|x|` with no rectifier smoothing, so every zero crossing yanks it toward
`SILENCE_DB` and it settles at an attack-dependent level:

| signal                                | 1 ms  | 3 ms  | 8 ms   | 30 ms      | 100 ms |
|---------------------------------------|-------|-------|--------|------------|--------|
| dense mix (peak +1.1, RMS −13.9 dBFS) | −7.50 | −9.96 | −12.65 | **−16.63** | −20.21 |
| 220 Hz sine @ 0 dBFS                  | −0.68 | −1.06 | −1.68  | −3.33      | −6.05  |

Going 8 → 30 ms drops the detector's reading by **4.0 dB on real material**, so the same `threshold`
yields ~4 dB less reduction. On a dense fixture a 2:1 @ −8 dB stage at 30 ms attack produced **no gain reduction at
all**. "Slower attack" therefore also means "weaker and later" — which is exactly the reported *passes the hit, then
clamps the body*.

This is inherent to a log-domain follower with no rectifier pre-smoothing. **The fix is the peak-detector RMS smoothing
already listed as deferred in `Compressor.kt`'s file KDoc** — not the blend. Affects every per-orbit compressor, not
just the master.

> **Re-verified 2026-08-31 — mechanism unchanged, so the 2026-08-06 measurements still describe the
> live code.** `envelopeStep(abs(...))` at `:332`/`:351` and `lookaheadStep(max(abs(l), abs(r)))` at
> `:312` still feed instantaneous `|x|` straight into the dB follower (`:423`), with no rectifier
> smoothing anywhere between. `Compressor.kt` has taken five commits since the measurement (the master
> limiter's lookahead, the wire-format split, two renames, and the `flushState` guard) and **not one of
> them touches the detector's input path**. The RMS smoothing is still only the KDoc note at `:41`.
> **This is the one open finding that is an audible engine defect rather than a test defect.**

> ✅ **CLOSED BY EAR 2026-08-31 (maintainer): "at least when currently listening to the songs I
> cannot hear any issues."** No change made, and the RMS smoothing stays deferred.
>
> **The measurement is not withdrawn — it is correct, and the mechanism is still there.** What the
> ear settled is the question the measurement cannot answer: whether it matters on the material we
> actually have. It does not. This is the same shape as the master-limiter pump recorded at the
> bottom of this file, where everything measured badly and the level-matched A/B came back "really
> subtle", and it is the project's standing rule that measurements find mechanisms while ears decide
> whether they count.
>
> **What would make it resurface**, recorded so the next person does not re-derive it: the effect
> scales with the configured attack. Nothing in the shipped songs leans on a slow attack, and the
> detector's reading only drops ~4 dB on real material once you get to 30 ms. A future patch that
> wants a genuinely slow glue compressor is where this comes back, and the fix is already named in
> `Compressor.kt`'s KDoc.
>
> 🔴 **Part (a) is untouched and still open**: `attackSeconds` is a one-pole τ, not a rise time
> (measured t10-90 = 2.33–2.54 × τ), so a configured 30 ms behaves like a ~74 ms attack in the units
> a DAW would print. That is a documentation and naming question, not a sound one, and it costs
> nothing to fix.

---

## F18 — Cut/choke groups were silently inert on every SYNTH voice ✅ FIXED

**HIGH — a real production defect, and the first one this audit found in shipped DSP rather than in
its tests.** Found 2026-08-31 by the very first run of the spec written for [F7](#f7): the row
*"a new voice hard-kills the voice already sounding in its cut group"* came back `expected:<1> but
was:<2>`.

**The defect.** `VoiceFactory.buildVoice` declares `cut: Int? = null`. There are exactly two call
sites, and only one passed it:

| call site | branch | `cut` argument |
|-----------|--------|----------------|
| `VoiceFactory.kt:372` | sample voice | `cut = data.cut` ✅ |
| `VoiceFactory.kt:280` | synth / oscillator voice | **absent — silently defaulted to `null`** ❌ |

So `Voice.cut` was always `null` for oscillator voices.

**Why it produced an asymmetry rather than a clean no-op**, which is what settles that it was a slip
and not a design choice: `VoiceScheduler.activateVoice` reads the **trigger** from
`absoluteVoice.data.cut` but each **victim** from `activeVoice.voice.cut`. A synth voice therefore
choked sample voices in its group perfectly well, while being immune to ever being choked itself.
No reading of cut/choke intends "cuts others, cannot be cut".

**Blast radius: none today.** A repo-wide grep found **zero** uses of `.cut(` in any shipped song, so
the fix moves no shipped sound. The DSL surface lives in `sprudel/lang_sample.kt` and all its KDoc
examples are sample-based, which is the likeliest reason the gap survived: cut reads as a
sample-only feature, and on samples it always worked.

**Fix.** One argument, at `VoiceFactory.kt:285` — `cut = data.cut` on the synth branch, matching the
sample branch.

**Guard.** `VoiceSchedulerSoloCutSpec`. Mutation **M1** (revert the fix) is the designated killer and
takes down exactly one row.

> **Method note — this is the payoff for the whole campaign.** The bug is invisible to inspection:
> both call sites *look* complete, because a defaulted parameter is exactly as readable when it is
> wrong. Nothing but an executed behavioural test could see it. It is also the second time in this
> audit that a **default parameter** hid a defect (cf. [F1](#f1), constants read by nothing) — worth
> treating `= null` defaults on wide internal builders as a smell in their own right.

---

## F19 — `cut(0)` is documented as "no choke" and the engine treats it as a normal group 🔴

**MED — a contract mismatch between the DSL's documented promise and the engine, not a crash.**
Found 2026-08-31 while explaining cut/choke to the maintainer, and verified rather than assumed.

**The DSL says:**

> *"Group `0` means no choke."* — `sprudel/lang_sample.kt:690`
>
> ```
> s("bd sd").cut("<0 1>")   // alternate between no-cut and cut-group-1
> ```

**The engine says otherwise.** `VoiceScheduler.activateVoice:553` gates the sweep on
`if (cut != null)`, and nothing on the path maps `0` to `null`:

| stage | what happens to `cut(0)` |
|-------|--------------------------|
| `lang_sample.kt:681` | `cut = it?.asIntOrNull()` → `0` |
| `VoiceData.cut` | `Int?` → `0` |
| `VoiceScheduler:553` | `0 != null` → **the sweep runs** |
| `VoiceScheduler:557` | victims are voices with `voice.cut == 0` → **group 0 chokes group 0** |

So group `0` is an ordinary group. In the KDoc's own second example the `bd` would have its tail
chopped by the following `sd` on the `0` cycle — exactly what the comment promises will not happen.

**Not currently audible anywhere:** zero shipped songs use `.cut(`, same as [F18](#f18). And until
F18 was fixed this was doubly invisible on synth voices, which could not be cut at all.

**Two ways to settle it, and it is a maintainer decision because they differ in what users expect:**

1. **Engine follows the docs** — gate on `cut != null && cut != 0`. Keeps `0` as a natural "off"
   value that can be produced by a pattern (`"<0 1>"`), which is why the doc wanted it, and matches
   what a Tidal/Strudel user would assume.
2. **Docs follow the engine** — delete the "0 = no choke" sentence and fix the example. "Off" then
   means *not calling* `.cut()` at all, and `0` is just a group like any other.

Option 1 is the one that keeps a *pattern* able to switch choking on and off per event; option 2
cannot express that at all.

> 📋 **PARKED 2026-08-31 (maintainer): "it was never used yet, and needs some thinking and
> design, not an on-the-fly judgement."** Moved to
> [`docs/tasks/future/cut-group-semantics.md`](../tasks/future/cut-group-semantics.md), which opens
> out the four questions this finding does not ask: whether per-event switchable choking is wanted at
> all (the only thing `0` buys over simply not calling `.cut()`), how far a cut group reaches (today
> it is **global to the scheduler** — two unrelated parts picking group `1` choke each other, which
> nobody decided), the hard-kill click still carrying its TODO at `VoiceScheduler.kt:558`, and
> whether a trigger must belong to the group it chokes. No code touched.

---

## F20 — The declick crossfade's ramp was unguarded after its first sample ✅ FIXED

**MED, and the first finding from the `cylinders/` + `katalyst/` subsystem.** Not a bug — the code is
correct — but a proven hole with a silent failure mode, closed the same session it was found.

**The campaign brief (§6.2) lists `KatalystFilterSwap.kt` as "102 lines, zero tests". Half true, and
the false half matters** — it has no *eponymous* spec, but `KatalystBodyEffectSpec` and
`KatalystFormantEffectSpec` both exercise it, and the former has a genuinely good row,
*"a live material change does not step the output (declick crossfade)"*, which swaps a resonant bank
while the orbit is ringing. **Fourth instance in this audit of "no spec named after it" being read as
"untested"** (cf. [F7](#f7), [F14](#f14), [F4](#f4)).

**What those rows could not see.** The declick test inspects **one sample** — the first after the
swap. At 44100 Hz a 12 ms fade is **529 frames, 4.1 blocks of 128**. Everything after that first
sample was unguarded, and the gap has a failure mode that is invisible exactly where the test looks:

> If the ramp never advanced (`t` stuck at 0), the swap boundary would be **perfectly continuous** —
> the output is simply still the old bank, which is what "no step" measures. The damage lands four
> blocks later, when `fadePos` crosses `fadeLen`, the old pair is dropped, and the output snaps to the
> new bank in one sample. A click, relocated to where nothing was looking.

**Evidence, not argument.** Mutation `t = 0.0`:

| spec | verdict |
|------|---------|
| `KatalystBodyEffectSpec` (the existing declick row) | **SURVIVED** |
| `KatalystFormantEffectSpec` (its twin) | **SURVIVED** |
| `KatalystFilterSwapSpec` (new) | killed, 2 rows |

**Fix.** `KatalystFilterSwapSpec` — 5 rows, 4 mutations, 4 killed. The two "filters" are plain gains
(old ×1.0, new ×0.0), which makes the expected output exactly `1 - t` and lets every constant be
checked against a number recomputed from the class's own definition rather than from a recorded run.
Mutations killed: ramp stalled at 0, `fadePos` not accumulating across blocks, the `coerceAtMost(1.0)`
clamp dropped, and the blend running backwards.

> **One row was written and then deleted, which is the method note worth keeping.** *"The fade end is
> continuous when the old pair is dropped"* survived all four mutations: the linear-ramp row already
> pins every sample to 1e-12, including the two either side of that release, so it contributed a name
> and no kill power. Whether the old pair is *actually* released is not observable from the output at
> all — a retained pair keeps blending at a clamped `t = 1` and sounds identical; it is a cost
> property, not a behaviour. Writing a row that reads as a guard and is not is the defect class this
> audit exists to find, so it went rather than shipping inside the fix for it.

---

## F21 — A flaky test in the suite the audit reads its verdicts from ✅ FIXED

**MED as a bug, HIGH as a threat to this campaign's method.** `IgnitorDefaultsTest` →
*"predefined 'dust' produces non-zero output"* failed once during a routine full-suite run on
2026-08-31, then passed alone and passed again in the full suite. Not an interaction — genuinely
random.

**The arithmetic.** `dust` is a sparse stochastic impulse generator. Its default `density` is `0.2`,
`rateHz = density * 200 = 40`, so the per-sample fire probability is `40 / 44100 = 9.07e-4`. The spec
renders `blockFrames = 4410`, giving an expected **4.0** impulses per block and

> **P(no impulse at all) = e^-4 ≈ 1.8% — a failure roughly one run in fifty.**

**Why it matters more here than in an ordinary suite.** This suite is the instrument every mutation
verdict in this audit is read off. A random red is indistinguishable from a killed mutant, so at
~2% per run a long mutation campaign will eventually record a mutation as "killed" that in fact
survived — and that verdict then justifies a decision, or closes a finding, on noise. The ledger has
already been wrong six times from bad *reasoning* ([F4](#f4), [F7](#f7), [F14](#f14), [F15](#f15));
it should not also be wrong from bad *sampling*.

**Fix.** `dust` moves out of the default-density list and is driven at `density = 1.0`, where
`rateHz = 200`, the expected count is ~20 and `P(silence) = e^-20 ≈ 2e-9`. The claim under test is
unchanged — that the registered name builds an exciter which emits audio. Mutation-checked: forcing
the fire probability to 0 turns the row red.

**Worth a sweep, not done here:** any other row asserting a property of a stochastic generator over a
short window has the same shape. `crackle` is the obvious neighbour, though it is a chaotic rather
than a sparse generator so its density argument differs.

---

## F22 — Every chunked sample lost its metadata at the worklet boundary ✅ FIXED

**HIGH — a live production defect in shipped code, found 2026-09-03 while chasing "the accordion
still does not loop".** `SampleStore`'s chunk reassembly built `MonoSamplePcm(sampleRate, pcm)`
and let `meta` default. Every chunk carries `meta`; the receiver never read it. `JsAudioBackend`
chunks **every** `Sample.Complete` before the worklet boundary regardless of size, so in the browser
**no soundfont had ever had a loop, an envelope, or an anchor.** The JVM backend passes `Complete`
in-process and was never affected — which is why the offline renderer looped while the browser did
not, and why two earlier, correct fixes upstream (`aa93eef8`, `c1b503d8`) were inaudible.

**Why no spec saw it.** Three specs sat on this path and none crossed the join: `SoundFontZoneMetadataTest`
proves the metadata is computed, `SamplePlayheadStartSpec` hands the PCM to `VoiceFactory` directly,
and `SampleChunkRoundTripSpec` — the one that does cross the wire — asserted the PCM bytes and never
mentioned `meta`. **Third instance of the [F18](#f18) shape in this audit: both ends covered, the
handoff empty.** The pattern is now strong enough to be the narrowed audit's target: not "is there a
spec named after it", but "does anything test the *handoff*".

**Fix.** One argument, `meta = msg.meta`. Guard: three rows in `SampleChunkRoundTripSpec` asserting
loop, adsr and anchor arrive intact across single-, two- and four-chunk samples; reverting the
argument turns all three red. Full record: `docs/tasks-archive/2026-09/20260903-soundfont-looping-investigation.md`, round 3.

---

## Note — the pump was real on paper and marginal by ear ✅ RESOLVED

Recorded because the *shape* of this result is worth remembering, not just the outcome.

Adding lookahead to the master limiter created a measurable pump: the limiter went from doing ~nothing during transients
to ducking the whole summed mix on every hit, and with a single 100 ms release the bed swelled between kicks. Everything
about it measured badly — 0.67 dB of bed movement, 1.59 dB below the ceiling on average.

**The level-matched by-ear A/B, on deliberately worst-case material, came back "really subtle".**

The fix (a dual program-dependent release) shipped anyway — but for a *different* reason than the one that motivated it:
it recovers ~1 dB of loudness at an identical peak. Had that second benefit not existed, the honest call would have been
to drop it.

**The lesson: a measurement can be entirely correct and still not describe something that matters.**
Two sessions of this feature ran the other way — the user reported the knock and the "shocks" by ear *before* either was
measured. Measurements found the mechanism; ears decided whether it counted.

---

## Notes (not findings — recorded so they are not re-derived)

- **`VoiceFactoryFilterOrderSpec` test 2 is redundant, not toothless.** Reinstating the canonical highpass-first sort in
  `VoiceFactory` (mutation S2) turns the spec **RED** — but only via test 1, whose input is deliberately non-canonical.
  Test 2's input is already sorted, so the sort is a no-op on it. The spec as a whole guards the contract; test 2 adds
  no kill-power against the one regression the class KDoc names. Harmless.
- **`FilterModulationTest` test 7 ("modulation called once per render")** is structure-guaranteed:
  `FilterModRenderer` is invoked once per `Voice.render()` by construction, so no operator/constant mutation of current
  code can break it. It is a legitimate guard against a *future* refactor that moves modulation into a per-sample loop.
  Keep, but do not count it as coverage of present behaviour.

---

*(list continues as the audit proceeds)*
