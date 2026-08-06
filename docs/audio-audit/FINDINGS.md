# Audio backend audit — findings

**Analysis only. Nothing here has been repaired.** We triage this list together, then decide what to fix. Each finding
carries the evidence that produced it — a mutation that was actually run, or a reference trace — so triage does not have
to re-derive it.

Severity is about *what it costs us*, not about how broken the code looks:
**HIGH** = a wrong sound or a false sense of safety today · **MED** = a real trap that has not bitten yet · **LOW** =
correctness of the record.

Status: 🔴 open (untriaged) · 🟢 accepted-as-is · 🔧 to fix · ⚪ user-decision

---

## F1 — Three tuning constants are documented as "the knob" but read by nothing 🔴

**HIGH.** `audio_be` declares tuning constants with KDoc that presents them as the by-ear tuning point ("Tunable by ear,
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

## F2 — `AnalogDriftSpec`'s budget guard cannot see the values the engine uses 🔴

**HIGH.** `AnalogDriftSpec.kt:85-103`, *"analog cents budget stays tamed at analog=3 (Der Schmetterling)"*. Its own
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

## F3 — The test named after the mid-block clamp cannot detect the clamp's removal 🔴

**MED.** `FilterModulationTest`, *"voice starting mid-block handles envelope correctly"*. The behaviour it names is
`EnvelopeCalc.kt:28` — `val currentFrame = maxOf(blockStart, startFrame)`, which stops the envelope position going
negative when a block starts before the voice does.

**Evidence (mutation S1).** Replaced it with `val currentFrame = blockStart` and ran the **whole**
`FilterModulationTest`: **GREEN**. Not just that one test — no test in the file noticed.

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

## F4 — "filter without modulator is not modified" cannot fail 🔴

**LOW.** `FilterModulationTest`, test 1. It asserts that a voice built with an empty
`filterModulators` list never calls `setCutoff`.

**Evidence (mutation S3).** Deleted the `if (modulators.isNotEmpty())` guard at
`FilterPipelineBuilder.kt:43-46` so the renderer is added unconditionally: **GREEN**.

**Why:** `FilterModRenderer.render()` is `for (mod in modulators) { … }`. With an empty list the body never executes, so
adding the renderer changes nothing observable. The assertion is guaranteed by the *shape of the input*, not by any line
of production code — there is no single-line mutation that can make it fail. `AudioFilterRenderer` calls only
`filter.process(...)`, never `setCutoff`.

Consequence: the `isNotEmpty()` guard is an allocation/iteration optimisation, not a behaviour, and **nothing guards
that it stays** — removing it is invisible to the suite and costs one renderer slot plus a per-block loop entry on every
voice that has no filter modulation (the common case).

---

## F5 — Coverage holes found while verifying the filter strip 🔴

**MED**, cumulatively. None of these is exercised by any spec in `voices/`:

- **`FilterModRenderer`'s drift path** (`:32`) — `if (drift != null && drift.active)
  drift.nextMultiplier() else 1.0`. No case in `FilterModulationTest` constructs a
  `Voice.FilterModulator` with a non-null `drift`, so the per-block `AnalogDrift` advance on the filter cutoff is
  untested here. This is the path `analog > 0` turns on, and it is the same subsystem as [F2](#f2) — so the analog-drift
  feature is now **twice** implicated.
- **`VoiceFactory.toModulator()`** (~`:384-451`) — entirely untested. `FilterModulationTest`
  constructs `Voice.FilterModulator` directly, bypassing the factory; `VoiceFactoryFilterOrderSpec`
  uses filter defs with no envelope, so it never reaches it either. Untested branches: the non-`Tunable` early return
  (e.g. `Formant`), the `envData == null && drift == null` early return, the degenerate `depth = 0` envelope built for
  drift-only modulation, and `envData.resolve()`.
- **Body/Formant exclusion from the per-voice chain** (`VoiceFactory.kt:108`) — the filter that routes `FilterDef.Body`/
  `FilterDef.Formant` to the orbit-level Katalyst instead of the voice chain. No spec puts a Body or Formant def
  alongside LP/HP/BP and checks it is excluded from the baked chain while still appearing on `Voice.body` /
  `Voice.vowel`. This is the 2026-07-04 body-to-orbit move, whose whole point was that `body` stops being super-additive
  with
  `superimpose`.

---

## F6 — Nine tests contain no assertions at all 🔴

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

---

## F7 — Three known holes: all CONFIRMED 🔴

**HIGH.** Independently verified, and they are the load-bearing parts of the voice path:

- **`VoiceScheduler.kt` (407 lines) has no spec, and is not reached by any spec.** All 33 lifecycle/pipeline tests call
  `voice.render()` directly. Repo-wide, `VoiceScheduler` is constructed only in `PlaybackEngine.kt` — never in a test
  file. Its old
  `VoiceSchedulerDiagnosticsTest` was deleted when diagnostics moved to the dispatcher and nothing replaced it. This is
  the highest-churn file in the module.
- **`SendRenderer.kt` — zero test references, but not dead.** Nuance worth keeping: it *executes* on every `render()` in
  all 33 tests, because `Voice.kt:81` appends it unconditionally. So it runs blind — no test ever inspects its output
  (pan, gain, postGain, or the delay/reverb send writes). Untested, not unexercised; a bug there fails silently rather
  than visibly.
- **solo/mute and cut/choke — zero tests anywhere.** A repo-wide grep of `commonTest` for `solo`,
  `cut` and `choke` returns nothing. `VoiceScheduler.process`'s solo ramp and `promoteScheduled`'s cut-group hard-kill
  are both entirely unexercised. The hard-kill is also the site of the standing
  `VoiceScheduler.kt:388` TODO — *"Use a fade out / release phase instead of hard cut?"* — i.e. a known click source
  with no test.

---

## F8 — Three tests are named for behaviour they structurally cannot reach 🔴

**MED.** Different from a wide tolerance: in each case the chosen parameters make the named code path *unreachable*, so
the test would pass even if that path were deleted.

1. **`EnvelopeTest` — "envelope clamps negative values to zero".**
   **Evidence (mutation V1):** deleted `EnvelopeRenderer.kt:126` (`if (currentEnv < 0.0) currentEnv =
   0.0`) — the whole `EnvelopeTest` stayed **GREEN**. Why: it is a single first-ever `render()` at `blockStart = 200`
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

## F10 — An inverted pitch glide passes the entire suite 🔴

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

---

## F11 — An FM modulator running 1000× too slow passes the entire suite 🔴

**HIGH.** `FmSynthesisTest` — *"FM modulator phase advances correctly"*. Its assertion is
`afterPhase > initialPhase`: the phase moved by *some* positive amount.

**Evidence (mutation W2).** Multiplied `modInc` by `0.001` at `FmRenderer.kt:39`, so the modulator advances ~0.006 rad
over 100 frames instead of ~6.3 rad. `FmSynthesisTest`: **GREEN**. The **whole suite: GREEN.**

That is not a detuned FM patch, it is a different instrument. The same shape recurs across this spec:
several tests assert `diff > 1e-3` against a clean baseline, which any nonzero FM satisfies — so they confirm "FM is
on", not "FM is right".

**Counter-evidence, for balance:** pinning the modulator to 1:1 by dropping `fm.ratio`
(`FmRenderer.kt:38`, mutation W3) **is** caught. The ratio is genuinely guarded; the rate and the envelope shape are
not.

---

## F12 — "disabled" tests compare a value against its own default 🔴

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

---

## F13 — Copy-paste tests that duplicate a sibling instead of testing their name 🔴

**MED.** Found by W5 in the lifecycle/pipeline specs; these are wrong *records*, not just weak ones:

- `VoiceLifecycleTest` — *"voice **ending** at block boundary renders full block"* is **byte-for-byte identical** in
  setup and assertions to the preceding *"voice **starting** at block boundary…"*. No end-boundary scenario is
  constructed anywhere in it.
- `VoiceLifecycleTest` — *"voice at exact block boundaries handles edge cases"*: its third sub-case (commented *"ends
  exactly at endFrame"*) reuses the exact `blockStart = 100, blockFrames = 100` of the second. The end-boundary case is
  never queried — and the comment on `ctx4`, which *does* cover it, mislabels it as "starts exactly at endFrame".
- `VoicePipelineTest` — *"envelope is applied **after** main filter"*: its assertion (`processCalls.size shouldBe 1`) is
  identical to the unrelated *"pipeline executes main filter"*. **No ordering is checked anywhere in the spec.**
- `VoicePipelineTest` — *"filter modulation updates cutoff **before** filter processes"*: the
  "before" is never verified.
- `VoicePipelineTest` — *"voice starting/ending mid-block renders **partial buffer**"*: no buffer content is ever
  inspected, only the lifecycle boolean. (The identically-named tests in
  `VoiceLifecycleTest` *do* check the buffer — so the two specs disagree about what the name means.)

---

## F14 — Coverage holes beyond the three known ones 🔴

**MED.** Production behaviour that no spec in `voices/` claims:

- **`Voice.Ducking` (sidechain) — untested by anything at the `Voice`/`VoiceScheduler` layer.** It is carried on `Voice`
  but applied in `cylinders/Cylinder.kt`.
- **`Voice.Compressor`'s DSP is untested.** `VoiceCompressorSpec` (4 tests) tests *string parsing*
  only — consistent with its own contents, but the name reads as coverage of the compressor. The runtime effect lives in
  `Cylinder.kt:181-216`.
- **`VoiceFactory` (563 lines) is bypassed by all 37 lifecycle/pipeline tests** —
  `VoiceTestHelpers.createVoice` hand-rolls a *parallel* pipeline construction instead of calling
  `makeVoice()`. So the helper and production can drift apart silently. Untouched by that path:
  `gain = baseGain * velocity`, legato/clip duration math, sample loop/pitch-ratio resolution,
  `perVoiceCutoffOffsetMul` randomisation, and the ADSR merge with sample metadata. (`VoiceFactoryFilterOrderSpec` does
  exercise `makeVoice`, but only for filter ordering.)
- **`PipelinePreset.Pedal` is never exercised** — `Modern` is hard-coded into `VoiceTestHelpers`.
- **`Voice.Fm` is `null` in every lifecycle/pipeline test**, and `FilterModulator.drift` is `null`
  everywhere — the third independent sighting of the analog-drift gap ([F2](#f2), [F5](#f5)).

---

## F15 — Suite-wide: 25 assertion-free tests, in three distinct classes 🔴

Extends [F6](#f6) from the `voices/` pilot to the whole `audio_be` tree. The classification matters — only the first
class is a defect:

**(a) Named for a behaviour, checks nothing — 13 tests. Defects.**
The 9 from [F6](#f6), plus: `ClippingFuncsBoundsSpec` *"rectify output is always non-negative"*,
`IgnitorCombinatorsSpec` *"crush (amount) - output is quantized (fewer unique values than input)"* and *"accelerate (
amount) - pitch changes over time"*, `VoicePipelineTest` *"voice renders correct number of samples"*. Each names a
measurable property and measures nothing. Note the `accelerate` one is the **third** independent gap in accelerate
coverage ([F10](#f10)).

**(b) Honestly-named smoke tests — 5 tests. Acceptable as-is.**
`LowPassHighPassFiltersSpec` *"…zero-length buffer does not crash"* ×3 — the assertion *is* "does not throw";
`VoicePipelineTest` *"tremolo renders successfully"* / *"phaser renders successfully with defaults"*. The name promises
exactly what is delivered. Worth keeping, worth not counting as coverage — and note this leaves `TremoloRenderer` and
`StripPhaserRenderer` with no behavioural test at all.

**(c) `GuitarClickHuntTest` — 7 of 7, and a separate question.**
This is the standing click-diagnostic harness; it prints and guards nothing, by design. But it is **5.4 s of the suite's
6.9 s — 78% of total runtime for zero assertions.** Worth deciding whether a diagnostic probe belongs in the default
`jvmTest` run or behind a tag.

---

## F16 — The master limiter does not limit transients; the hard clip does 🔧

**HIGH — user-reported symptom ("knock"), root-caused 2026-08-04.** Being fixed under
[`docs/tasks/master-limiter-lookahead.md`](../tasks/master-limiter-lookahead.md).

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
