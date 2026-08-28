# Block-framing invariance — every voice must sound the same wherever the blocks fall

> **Status (2026-08-28): PLAN, not started. Scope settled with the maintainer.** Opened after the
> SECOND instance of the same bug class in three weeks (`IgniteRenderer.kt:36` 2026-08-27, surfaced by
> `.adsrOff()`; sample-voice onset quantisation 2026-08-07). An audit workstream, deliberately
> incremental: build the harness, run it, fix one node per commit.
>
> **Target: A, structurally correct at any block size — NOT B, bit-identical.** See that section.

## The one-sentence property

**A voice's output must depend only on when the note starts and what it is made of — never on how the
renderer happens to chop time into blocks.**

Everything below is a way of testing that sentence.

## The bug class, precisely

The audio backend renders in blocks. A voice rarely starts on a block boundary, so its first block is
partial: `Voice.render` places its samples at buffer indices `[offset, offset+length)`.

```kotlin
val vStart = maxOf(ctx.blockStart, startFrame)
val offset = (vStart - ctx.blockStart).toInt()
val length = (vEnd - vStart).toInt()
```

`offset` is non-zero **only on a voice's first block**. That is what makes this class of bug so good
at hiding: it is wrong once per note, at the onset, and then self-corrects.

Two distinct failure modes, and they need different tests:

**(a) Wrong clock.** A node derives time from `IgniteContext.voiceElapsedFrames` but the value does
not correspond to buffer index `ctx.offset`.

**(b) Wrong transition offset.** A node has an internal state change (release start, sample-and-hold
reload, delay wrap, retrigger, filter-coefficient switch) that must happen at a specific SAMPLE. If it
is decided per block instead, the transition snaps to a block boundary and moves by up to
`blockFrames` depending on where the note fell. *This is the maintainer's point and it is not covered
by fixing clocks: a correct clock read once per block still gives a block-quantised transition.*

### Confirmed instance 1 — `IgniteRenderer.kt:36` (FIXED 2026-08-27)

```kotlin
// was
signalCtx.voiceElapsedFrames = (ctx.blockStart - startFrame).toInt()
// now
signalCtx.voiceElapsedFrames = (ctx.blockStart + ctx.offset - startFrame).toInt()
```

Every consumer reads this as the elapsed count **at `ctx.offset`**. Computed at index 0, it went
NEGATIVE on the first block: the exponential shape returned a negative value, clamped to zero, and the
note's first `offset` samples rendered silent and then stepped.

Measured, 44.1 kHz / 128-frame blocks, DC source with a 10 ms attack:

| start frame | first 6 rendered samples | block seam (124..131) |
|---|---|---|
| 0 (aligned) | 0.0000 0.0004 0.0007 0.0011 0.0014 0.0018 | 0.0694 … 0.0753, continuous |
| 76 (mid-block), before fix | 0.0000 ×6 | 0 0 0 0 **0.0222** 0.0227 … |
| 76, after fix | 0.0000 0.0004 0.0007 0.0011 0.0014 0.0018 | 0.0202 0.0207 0.0212 0.0217 0.0222 … |

Average 64 frames of attack lost, worst case 127. The step (0.0222) was **larger than the 0.015
teardown step** the envelope-ownership work was built to remove.

**Why nobody found it: one wrong clock, five consumers, none individually wrong.**
`IgnitorEnvelopes.kt:100` (ignitor ADSR), `IgnitorFilters.kt:655` (ignitor filter envelope, evaluated
at both `sampleOffsetWithinBlock = 0` and `= length`), `PitchModFactories.kt:263` (FM depth),
`PitchModFactories.kt:121` (accelerate progress). Every one of them read the shared value correctly.
Guard: `IgniteOnsetOffsetSpec`.

**And why it survived so long:** the strip VCA was simultaneously in its own correctly-timed,
de-clicked attack, attenuating the step 10–20×. `.adsrOff()` replaced that with unity and exposed it.
A masking layer is not a fix, and this whole plan exists because we cannot rely on one.

### Confirmed instance 2 — the sample path (fixed 2026-08-07)

`docs/tasks-archive/2026-08/20260807-sample-voice-onset-quantization.md`. Sample voices started at the
render block boundary, because `VoiceFactory` passed `nowFrame` instead of the scheduled `startFrame`
— up to `blockFrames-1` frames of per-hit onset jitter. Fix: `maxOf(startFrame, nowFrame)`. Sub-block
`late()` values on samples were previously swallowed entirely.

### And instance 1 was already on the deferred list

The same 2026-08-07 write-up records, under *known follow-ups, deliberately NOT done*:
**"`IgniteRenderer.voiceElapsedFrames` misses `+ ctx.offset` (pre-existing, osc-only)"**. Found, judged
low-priority, deferred — and it took three weeks and an unrelated feature (`.adsrOff()`, which removed
the masking layer) to surface it audibly as the guitar knocks.

That is the argument for this workstream in one line: **this class of bug does not announce itself, so
it will not be prioritised correctly by ear.** It needs a mechanical sweep.

Three siblings are still on that deferred list and want triage here; two are audible:

- oscillator voices never receive `data.cut` — choke groups are sample-only;
- a `cut`-tagged hit on an unloaded sample silences its group with nothing to replace it;
- `SongBenchmark`'s peak window cannot cover `filterWhen`-gated orbit allocation (wants a percentile peak).

## The target: structurally correct at ANY block size — NOT bit-identical

Settled with the maintainer, 2026-08-28.

Block size is 128 in the browser because the Web Audio render quantum is handed to the worklet, but
**other platforms may choose it, so nothing may be implemented against 128.** At the same time, block
size remains a TONE parameter (`docs/tasks-archive/2026-08/20260807-block-size-parity.md`): several
things derive their RATE from it, and the consequences are understood and accepted.

| derives its rate from `blockFrames` | shifts when block size changes |
|---|---|
| `driftUpdateRate = sampleRate / blockFrames` (`VoiceFactory:61`) | analog drift time constants (tuned by ear at 128) |
| SVF cutoff smoothing | filter movement speed |
| `oldestAllowedSec` = 5 blocks | late-voice drop window (RETIRED by the no-late-voices rule below) |
| MasterBus crossfade granularity | crossfade rate |
| every `readParam` / `blockStartValue` | control-rate modulation step rate |

So the goal is **A, not B**:

- **A (the target).** Structure is correct at any block size: onsets land on the right sample, state
  transitions land on the right sample, nothing renders silent, no step is introduced, no state is
  corrupted.
- **B (explicitly NOT the target).** Bit-identical output at any block size. That would mean
  re-deriving every row of the table above from `sampleRate`, and re-tuning analog drift by ear.
  Rejected: the tone dependence is deliberate.

**SUPERSEDED (2026-08-28): there is no late-voice quantisation any more, because there are no late
voices.** An earlier revision accepted "a late voice floors to the block start" as the one exception.
The maintainer replaced that with a hard rule: see "Enforced at ONE place" below. The sample-path
floor (`maxOf(startFrame, nowFrame)`) becomes dead code once that lands.

## Enforced at ONE place: the scheduler (decided 2026-08-28)

The audit exposed that we were mixing two problems, and the maintainer split them:

**Problem A (DSP):** every node may ASSUME the contract below and must be correct under it.
**Problem B (scheduler):** the scheduler GUARANTEES the contract, at the admission check.

**The contract:** a voice's first `generate` call has `voiceElapsedFrames == 0`, and rendering is
contiguous thereafter (each block advances state by exactly `ctx.length`). Everything a voice does is
a function of its note-relative sample position; the scheduler owns where `startFrame` falls.

**The guarantee: no late voices, ever.** Admission becomes `startFrame >= blockStart` of the block
being scheduled; anything older is DROPPED and counted on a per-playback dropped-voice counter
(observability, not a clamp). This replaces the 5-block `oldestAllowedSec` window.

What the hard rule erases, verified 2026-08-28:

- The `AdsrIgnitor` release-level silent-note finding AND its identical latent twin in the strip VCA
  (`EnvelopeRenderer:95`, `releaseStartLevel = currentEnv` from an initial `level = 0.0`) both become
  unreachable. Two bugs closed with zero DSP edits.
- The sample floor `maxOf(startFrame, nowFrame)` and the "ADSR ahead of the sample playhead"
  divergence class it guarded: dead.
- `oldestAllowedSec`: deleted, including its row in the tone table above.
- The harness loses the whole "arbitrary first `absPos`" sweep dimension.
- Nothing musically real is lost: a late oscillator voice today renders an incoherent hybrid anyway
  (phase starts fresh while the envelope evaluates mid-note; neither "on time" nor "shifted").

**Accepted trade:** arrival jitter becomes cleanly dropped notes instead of smeared ones. An FE stall
produces a diagnosable gap plus a counter increment, not a 15 ms smear.

**Sequencing is FIXED: the startup fix lands before (or with) the admission flip, never after.**
Flipping first would eat the first notes of every playback.

**Startup direction (ROUGH SKETCH, maintainer 2026-08-28, deliberately not designed yet):** send the
first batch of voices, and only then send a "start playback" command; the arrival of the start
command is the playback's true zero point, and note start times are expressed relative to it. The
epoch mechanism is already most of this (`VoiceScheduler.ensureEpoch`, `:317-343`, snaps the epoch to
"now" when the first voice arrives); what remains is that the anchor lands exactly ON the boundary
(fractionally late by render time) instead of strictly in the future. Do not implement from this
sketch; it gets its own design pass.

## Classifying every node — the thing that makes "structurally correct" testable

Without bit-identity the assertions get vague, so the sweep classifies each node type instead. The
classification is the deliverable of P2, and it is compiler-checked (`when` with no `else`).

**Class 1 — sample-deterministic.** No block-rate internal state. With `analog = 0` and block-constant
parameters these must be **bit-identical across block sizes and alignments**. Expected to be the large
majority: oscillators, envelopes, arithmetic, waveshapers, delay lines. A Class 1 node that is not
bit-identical is a bug, full stop.

**Class 2 — block-rate by design.** Something inside ticks once per block: analog drift, SVF
smoothing, a control-rate read of a varying signal. Not bit-identical, and must not be made so. For
these the assertions are the structural ones below, plus: **the block-rate dependence must be NAMED**
(which knob, which rate) rather than merely observed.

## The invariants

These are mechanical and apply to every node without case-by-case reasoning. That is the point: the
catalogue is 78 DSL node types over 33 runtime classes, and hand-auditing them is exactly how the
first two got missed.

**I1 — Onset placement.** The note's first sample lands at exactly `startFrame`, for every
`S mod blockFrames` and every block size. Class 1 additionally: the samples relative to the note's own
start are bit-identical.

**I2 — Block-size independence.** Render the same voice at 128, 64, 37 and a ragged split. Class 1
must be bit-identical. Class 2 must satisfy I1/I3/I4 and introduce **no discontinuity that the
block-aligned 128 reference does not have** — changing block size may change tone, never structure.

**I3 — Teardown placement.** Same as I1 for the end frame: sweep `endFrame mod blockFrames` and block
size. The last rendered sample is `floor(endFrame) - 1` and the envelope has reached its endpoint
there.

**I4 — Transition placement.** For any node with a gate-relative state change, sweep the gate end
across every offset in a block and assert the transition lands at the same sample relative to the
note. This is the one that catches failure mode (b), and I1/I2 catch it only by luck — a transition
that snaps to a block boundary is invisible if the test never places one mid-block.

## Second-order effects — classify, do not chase

Expect these and put them in Class 2 with a name, rather than treating them as failures:

- **`MemoizingIgnitor`** keys on `(voiceElapsedFrames, offset, length, freqHz)`, so a different block
  size changes the key and forces recompute. Correct, and invisible if the node is otherwise sound.
- **Stateful RNG** (whitenoise, dust, crackle, supersaw jitter): draw order must depend only on the
  number of samples produced. A node drawing `buffer.size` instead of `length` is a Class 1 failure
  and exactly the slip worth finding.
- **Delay-line nodes** (shimmer, phaser, pluck): write/read positions must advance by `length`.
- **`IgnitorFilters` lerps** its envelope between `sampleOffsetWithinBlock = 0` and `= length`
  (`:141-147`). An earlier revision called this the good Class 2 precedent. **Half right:** the lerp
  is continuous across seams, but a chord across a block STRAIGHTENS any envelope knee inside it, so
  a segment shorter than a block is erased and the peak height depends on alignment and block size.
  See the findings ledger. Seam continuity is necessary, not sufficient.

## Harness design

One table-driven spec, `BlockFramingInvarianceSpec`, over a canonical instance of every node type.

**Make the sweep exhaustive by the compiler, not by discipline.** Build the node table from an
exhaustive `when` over `IgnitorDsl` with **no `else` arm**, the way `IgnitorDslWalk.childNodes()`
already does it, so adding a node type breaks the file and forces someone to give it a canonical
instance. A sweep that can silently skip a node is worth very little — this whole plan is a reaction
to silent gaps.

Render helper shape (the `IgniteOnsetOffsetSpec` harness generalised):

```
renderVoice(dsl, startFrame, blockFrames, gateFrames, releaseFrames) -> DoubleArray  // note-relative
```

Drive it through the real `Voice.render` where possible, so the framing under test is the production
framing rather than a hand-built `BlockContext`. Hand-built contexts are how a spec ends up asserting
a framing that never occurs.

## Findings ledger

### Envelope class — audited 2026-08-28 (two lenses: clock/indexing + state transitions)

Scope: `IgnitorEnvelopes.kt`, `IgnitorFilters.computeFilterEnvelope` + callers, `PitchModFactories.kt`.
Clean with no findings: `AdsrIgnitor` framing (Class 1), `pitchEnvelopeModIgnitor` (Class 1),
`computeFilterEnvelope` itself (Class 1, pure; the quantisation is entirely in its callers),
`SvfIgnitor` non-env path (Class 1).

| # | finding | severity | disposition |
|---|---|---|---|
| E1 | FM depth envelope held flat per block, no interpolation (`PitchModFactories:262-267`); LIVE on the registered `sgbell` preset: first block of every FM note has zero FM, peak depth never produced, per-hit head length 1..blockFrames | CRITICAL | **FIXED 2026-08-28** on the IGNITOR door: per-sample evaluation via `sampleOffsetWithinBlock`; "fm with envelope" graduated to the harness's bit-identical green list. **The STRIP fm door still block-holds the same envelope — E11 (P4).** Per-sample cost accepted; if profiling ever objects, the agreed shape is a per-block precompute + thin `at(absPos)` body, ONE law, two entry points |
| E2 | `fmModIgnitor` early returns skip `modulator.generate`, freezing its phase for whole blocks; `VibratoModIgnitor` same shape | MINOR | **FIXED 2026-08-28**: modulator/LFO advance unconditionally; phase-continuity guards in `PitchModFactoriesSpec` (a depth gap no longer freezes the phase). **Accepted costs (round-2 review, own record on request):** a structurally constant `depth = 0` fm now renders its discarded modulator every block, and the modulator's drift init consumes **3** per-voice rng draws it previously never took (`analogDriftGaussian` = 2 × nextDouble + one nextInt; corrected from 2 in the oscillator audit) — within-voice draw-order shift only. Note the unconditional-at-analog-0 construction holds only for the `initAnalogDrift` callers (sine/impulse/pluck/superpluck); `WaveIgnitor` and `DetunedStackIgnitor` guard on `amt > 0` and draw nothing. A safe skip would need a LITERAL-constant test — `isBlockConstant` is NOT sufficient, its value may legally change between blocks. **Accepted residual:** a nested fm whose OUTER ratio is modulated to <= 0 sends `freqHz * ratio <= 0` to the inner node, which then takes the note-less bypass and freezes its modulator for those blocks — exotic by construction, recorded at the bypass comment |
| E3 | SVF cutoff-env chord straightens knees; segment shorter than a block erased; peak height alignment- and block-size-dependent (`IgnitorFilters:140-171`) | MAJOR (latent: no production door passes an env here; benchmarks/tests only) | **PIN with a spec, decide before any door wires an env**. Fix shape if taken: split the chord at breakpoints in `(E, E+length)` |
| E4 | `AdsrIgnitor` takes `releaseStartLevel` from history; first rendered sample past gate end renders the note silent (`IgnitorEnvelopes:104-108`) + identical latent twin in strip `EnvelopeRenderer:95` | MAJOR | **CLOSED BY PROBLEM B** (unreachable once no-late-voices lands); no DSP edit |
| E5 | `blockStartValue` reads stale scratch when `ctx.length == 0` (`Ignitor.kt:89-91`); reachable via small `legato` | MINOR | **FIXED 2026-08-28**: zero-length windows return 0.0 deterministically; guard in `ControlRateValueSpec`, mutation-checked |
| E6 | Five dead block-start-only accessors on `IgniteContext` (`voiceProgress`, `isInRelease`, `releaseProgress`, ...), zero callers, shaped exactly like instance 1 | MINOR | **DONE 2026-08-28**: deleted with zero callers; tombstone comment demands any future per-sample variant take the offset explicitly |
| E7 | `accelerateModIgnitor`: per-block-anchored multiplicative recurrence differs by 1-2 ulp between block sizes | note | **HARNESS RULE**: recurrence-based Class 1 nodes get a relative tolerance (~1e-12) + an endpoint pin, never raw bit-identity |
| E8 | `MemoizingIgnitor` re-runs a stateful shared inner when two consumers use different `freqHz` in one block (fm, detune); double-advance distance = `length` | MINOR | **RECORD**, assess in the P2 sweep (exposure in this class: only opt-in `declickSeconds`) |
| E9 | Harness lesson from E1/E3: an onset-only sweep sees NONE of this | rule | **P0 must sweep `gateEndFrame mod blockFrames` and an interior breakpoint (`attackFrames mod blockFrames`) from the first commit** |
| E10 | FM env `release = 0` collapses the modulation depth in ONE sample at gate end (a ±depth·level Hz frequency step at full amplitude). The E1 fix EXPOSED it: block-held, the collapse landed at a random modulator phase per alignment (a per-note click lottery, often small); sample-exact, it is deterministic and consistent | found by ear (maintainer, sgbell A/B) | **RAW BY DESIGN** (maintainer 2026-08-28: attribute to the envelope, 0 means 0). `sgbell` given `envReleaseSec = 0.05` (it is a test sound); guards in `PitchModFactoriesSpec` pin that a nonzero release ramps instead of stepping AND that a release-ONLY envelope is honoured (the `hasEnv` gate now counts `envReleaseSec` — it used to drop a release-only env silently, the exact remedy shape this row points users at). **On the STRIP door the collapse is UN-ESCAPABLE today — E11** |
| E11 | The STRIP fm door (sprudel `fmh`/`fmEnv`/`fmAttack`/`fmDecay`/`fmSustain` via `FmRenderer:43` + `calculateControlRateEnvelope`) carries BOTH defects this class just fixed on the ignitor door: the depth envelope is block-held (the E1 shape, and clocked from `blockStart` without `ctx.offset`), and `VoiceFactory:244` hardcodes the FM env's `releaseFrames = 0` with **no `fmRelease` control on any surface** — so the E10 note-off collapse cannot be ramped there at all | MAJOR (live on every `fmh` voice in every song) | **P4 scope, do not fix piecemeal**: per-sample (or lerped) env with the strip-renderer pass, plus a surface decision on adding `fmrelease` — maintainer input needed at P4 |

### Oscillator-source class — audited 2026-08-28 (two lenses: clock/indexing + transitions/RNG)

Scope: `Ignitors.kt` (all 20 source classes), `PhasePool.kt`, `AnalogDrift.kt`, the detune/mod
wrappers, `blockStartValue`. Clean with NO findings: `Sine`, `Impulse` (per-sample retrigger latch,
`+Inf` seed), `WaveIgnitor` mono incl. both duty branches (flyback and pulse edges are evaluated
from phase PER SAMPLE — not discrete transitions at all), `Brown`/`Pink`/`Perlin`/`Berlin` noise,
`Dust`, `Crackle`, mono `KarplusStrong`, `AnalogDrift` (oscillator-side drift is SAMPLE-clocked —
draws = samples × voices in every branch — unlike the block-rate filter-stage instance the tone
table names), `PhasePool`/`PhasePools` (no block-rate state at all; every mutation is indexed by
served notes), `ModApplyingIgnitor`, `DetuneConst`. Zero `buffer.size` loops, zero
`voiceElapsedFrames` reads, all 16 window computations `[offset, offset+length)` — verified
exhaustively.

| # | finding | severity | disposition |
|---|---|---|---|
| O1 | `SuperKarplusStrongIgnitor` reads `voices` by raw scratch render + `voicesBuf[ctx.offset]` (`Ignitors:1325-1326`), bypassing the E5 fix: on a zero-length terminal window the read is arbitrary cross-voice pool residue and drives `Array(newV) { StringState(AudioBuffer(2500)) }` — a control-rate residue means an unbounded synchronous allocation on the render thread. Live on the default `superpluck`, ~1/blockFrames of notes. Sibling `DetunedStackIgnitor:724` does it right via `readParam` | MAJOR (CRITICAL branch on large residue) | **FIXED 2026-08-28**: `readParam(voices, actualFreq, ctx)`. Guard `SuperPluckParamReadSpec`, mutation-checked — two lessons from the kill: the raw read is OUTPUT-invisible on the pluck alone (the phantom transition reverses cleanly and settled strings keep their state), so the guard pairs superpluck with whitenoise on the shared voice rng and pins the draw stream (the mutation burns ~18k phantom draws on a poisoned zero-length window, shifting all later noise); and the poison must be planted several slots DEEP (nested `use`), because builder wrappers hold outer scratch slots while the pluck reads |
| O2 | Same node: `initAnalogDrift` (`:1353`) and `readParam(pickPosition)` (`:1361`) sit INSIDE the per-string excitation loop — a modulated param advances `v x length` and each string samples a different point; excitation differs between block sizes (I2). `DetunedStackIgnitor:749` shows the correct hoist | MINOR | **FIXED 2026-08-28** with O1: `analogAmt` + `pickPosVal` hoisted above the per-string loop, one read per block |
| O3 | Modulated `voices` on BOTH stacks: the voice-count transition is decided at the block-start observation, so the added/removed voice lands on the next block boundary (700 -> 768/704/703 at 128/64/37) and the accompanying rng bursts (~19 draws supersaw, ~110 superpluck incl. a full re-excitation pluck + 20 KB audio-thread alloc) fire a framing-dependent number of times — thousands of draws of divergence per second under a fast `voices` signal, desynchronising every later draw in the voice. Superpluck additionally DISCARDS tail strings on shrink and re-excites on regrow | MAJOR (latent: sprudel `.voices()` writes per-note constants; only the KlangScript KDoc promises "audio-rate modulation", `KlangScriptSuperSawExtensions:18-20`) | **DECIDED 2026-08-28 (maintainer)**: neither (a) nor (b) — `voices` STAYS modulatable ("for very long notes it might make sense"), and the block-start observation IS the semantics ("fine if the change happens at block start"): the knob is an explicit Class 2 control. DECIDED comments sit at both stack transition sites so later reviews do not re-judge it; the KlangScript KDoc now says control-rate/block grid instead of promising audio-rate. Consequence: transitions are LEGAL mid-note, so O4's survivor-preservation fix was required, not optional |
| O4 | `DetunedStackIgnitor:750-753`: any voice-count change reallocates the `AnalogDrift` of EVERY voice (survivors' slow OU layer re-seeds to centre mid-note — the exact "breathes after the attack" state the class protects) and `computeVoiceGains()` re-jitters surviving voices' gains: an audible amplitude step at a block boundary. `SuperKarplusStrongIgnitor:1353` preserves existing state with `?:` — the asymmetry is the tell | MAJOR (reachable only with signal-valued `voices`) | **FIXED 2026-08-28**: transitions construct drift + jitter only for `n >= old.size`; survivors keep their OU state and their gain-jitter draw (`WaveVoiceState.jitDraw` captures the draw once; `computeVoiceGains` re-reads it instead of re-rolling). Guard `SuperStackTransitionSpec` (steady-state draws == 0; a transition draws only for the new voices), mutation-checked |
| O5 | Phase-pool note-on gate is `old.isEmpty()` (`Ignitors:732`) — reads "no block observed v > 0 yet", not "note-on": a `voices` signal starting below 2 silently disables `.phasePool(...)` for the whole note or defers the pool serve to a framing-dependent block; shrink-regrow never repools | MINOR | **DOCUMENTED 2026-08-28**: gate KEPT as `old.isEmpty()` with a DECIDED comment at the site — under O3's block-start semantics, a `voices` signal starting below 2 defers the pool serve to the first block that observes v >= 2, and shrink-regrow keeps the already-served phases (no repool). Accepted behavior, not a bug |
| O6 | THREE `freqHz` conventions for control-rate reads: the whole noise family hardcodes `0.0` (12 sites), wave/super/KS pass `actualFreq`, the spine passes the voice freq. Consequences: `Osc.freq()` inside any noise param silently reads 0 Hz, and a stateful node shared between a noise param and the spine splits the `MemoizingIgnitor` key and advances `2 x length` per block (continues E8 — exposure here is the ENTIRE noise family, not one opt-in knob). Framing-INVARIANT, so `BlockFramingInvarianceSpec` is blind to it | MINOR | **FIXED 2026-08-28**: the noise family passes the real `freqHz` through all 12 sites; `Osc.freq()` inside noise params reads the note again. Guard in `MemoizingIgnitorSpec`: a node SHARED between a whitenoise param and the spine advances exactly once per block (counting ignitor), mutation-checked |
| O7 | Perlin/Berlin/Dust/Crackle read params by raw `generate` + `buf[ctx.offset]` (E5-bypass shape, currently benign, perf-negative: Perlin renders three scratch blocks per block for three constants) | MINOR | **FIXED 2026-08-28** with O6: Perlin/Berlin/Dust/Crackle read params via `readParam` (E5-safe, one scratch render less per constant) |
| O8 | "A zero-length window is always a voice's TERMINAL block" is an undocumented emergent property (integral blockStart + integral startFrame + fractional endFrame) that caps E5/O1/O7 below CRITICAL. B1's startup redesign could make `startFrame` fractional — then zero-length FIRST blocks become reachable and six lazy-init latches (`Ignitors:79,152-154,342,1212,1226-1240,1353`) freeze wrong for the note's life | rule | **PINNED 2026-08-28**: `ZeroLengthWindowSpec` — proves the emergent property on today's VoiceFactory (integral startFrame; zero-length only as the terminal window) AND demonstrates the violation shape (a fractional 399.5 startFrame yields a MID-NOTE zero-length window). B1 must keep this spec green |
| O9 | `WhiteNoiseIgnitor:287-300` freezes its tilt-LP on the exact-`0.0` color fast path; on return to non-zero the one-pole resumes stale at a block-quantised instant (E2's shape). Draw parity unaffected. Needs a quantised `color` to reach | note | **RECORD**; if touched, keep the LP running in the bypass arm |
| O10 | Record corrections applied to the E2 row: drift init draws 3 (not 2), and unconditional-at-analog-0 holds for only half the family | rule | **DONE** (row amended above) |

**Also recorded:** the `PlaybackCtx.coreRandom` reproducibility caveat ("identical pid + note order +
block segmentation") reduces to "identical pid + note order" once O3 is resolved — segmentation
moves control-rate OBSERVATIONS, never draw counts. And two framing-INVARIANT surface/engine
mismatches for the pipeline-coefficient backlog, out of this workstream: audio-rate `analog` is
inert after the first block despite the KlangScript KDoc, and `WaveIgnitor`'s flyback fraction
derives from `actualFreq` only, blind to `ctx.phaseMod` (never tracks a pitch bend).

**Harness graduation queue (P2), in value order:** `superpluck` (after O1/O2), `supersaw`,
`pulze` with an audio-rate `duty`, `impulse`, `crackle`/`dust`, and an `analog = 0.9` sine — the
last belongs on the bit-identical GREEN list because oscillator drift is sample-clocked.

### Delay-line effect class — audited 2026-08-28 (two lenses: clock/indexing + state/pointer-advance) — FIXES APPLIED 2026-08-28 (only D13 still open)

Scope: `PhaserIgnitor` + `ShimmerIgnitor` (`IgnitorEffects.kt`) with their DSL wiring
(`IgnitorDslRuntime:515,517`), `StripPhaserRenderer`, `SendRenderer`, the shared cores
(`DelayLine`, `PhaserCore`, `Phaser`), and the block-continuous consumers checked rather than
assumed safe (`KatalystDelayEffect`, `KatalystPhaserEffect`, the `Cylinder` bus, `MasterChain`
delay shells, `MasterBus` swap/tail). Inventory honesty: **no chorus and no flanger exist in
audio_be** (both are aspirational words in `DelayLine.kt` KDoc only); the complete set of delay
rings is `DelayLine` (orbit bus + master chain), the shimmer 96 000-sample grain ring, and the two
Karplus-Strong string lines (audited in the oscillator round).

Clean with NO findings: `SendRenderer` indexing (exact `[offset, offset+length)` window, sends land
where the voice sounds, stateless), `StripPhaserRenderer` (bypass gate is a constructor constant so
it can never flip mid-note; LFO note-relative, I1 clean), `DelayLine` core pointer/wrap/interpolator
(writePos advances by exactly `length`, wrap is buffer-size-only, Class 1), the shimmer grain
scheduler on the LIVE path (fully sample-anchored: first grain at note-relative sample 0 for every
alignment), the param-read shape of BOTH ignitors (every read via `readParam` with the real note
`freqHz` — zero E5-bypass sites in the class), the DSL wiring (modulatable params survive as full
runtime Ignitors), per-voice instance isolation (fresh build cache per note-on), katalyst framing
(**proven from every call site: katalyst effects never see a partial block** — voices write partial
windows into full-size per-block-cleared buffers), the orbit delay tail while active (renders full
blocks with zeroed send), `MasterChain`'s delay stage (advance unconditional, presence decided at
build time), and the `MasterBus` crossfade (per-sample `fadePos`, ends at the exact sample —
I4-clean; swap-start block quantisation + A→B→A tail retention are documented in-file). No
`buffer.size` / `0 until blockFrames` loop exists in any per-voice path in scope; I1/I3 hold
class-wide.

| # | finding | severity | disposition |
|---|---|---|---|
| D1 | `PhaserIgnitor` `wet <= 0` bypass returns BEFORE `prepareBlock` and before the `rate`/`center`/`sweep` reads (`IgnitorEffects:418-434`): the LFO time base becomes "summed length of non-bypassed blocks", not note-relative time, and a STATEFUL param ignitor in those slots freezes too (the per-block `readParam` is what ticks it). Freeze length is block-quantised, the error ACCUMULATES per wet-crossing (up to `blockFrames` each), so two renders differing only in alignment or block size diverge for the rest of the note (I2+I4). Needs a modulated `wet` dipping to 0 to reach | MAJOR | **FIXED 2026-08-28**: reads + `prepareBlock(ctx.length)` hoisted above the bypass — the LFO and every param ignitor tick unconditionally. Guard `PhaserClockSpec` ("wet gap resumes exactly like a first engagement", bit-identical), mutation-killed. Round 3 restored the HISTORICAL read order (wet, rate, center, sweep, dryFloor): the hoist had reordered the per-block reads, which is observable for a hand-built Kotlin graph sharing one bare stateful ignitor across two slots (the DSL door memoizes shared nodes and never sees it); round 4 PINNED the order on both doors (shared-instance rows in `PhaserClockSpec`/`ShimmerSchedulerSpec`, reorder mutations killed) |
| D2 | Bus phaser, same shape twice: `KatalystPhaserEffect` gates at `depth < 0.01` and `Phaser.process` at `depth <= 0.0`, BOTH above the only `prepareBlock` calls — and here `depth` IS re-applied every block from the lease owner, so a patterned `phaserdepth` freezes the LFO mid-orbit; an idle orbit (`!isActive`) freezes it too. The two gates also disagree (pre-existing, noted in `StripPhaserRenderer` KDoc) | MINOR | **FIXED 2026-08-28**: both cores' `prepareBlock` moved above the depth gate; the katalyst outer gate is DELETED and the ONE remaining gate is `Phaser.MIN_ACTIVE_DEPTH = 0.01` (the historical bus threshold — the existing `KatalystPhaserEffectSpec` caught an attempt to lower it to 0.0 in the full-suite run and the 0.01 contract was kept). Residual named Class 2: an INACTIVE orbit still freezes the LFO (`processEffects` early-returns) — the sweep's time base is orbit-active time. **COMPLETED in review round 1**, which caught that the fix was INERT in production: `applyBusEffects` wrote `rate = voice.phaser.rate` unconditionally and a no-phaser lease owner defaults to rate 0.0 — an LFO advancing at rate 0 is frozen all the same. Kernel params (rate/center/sweep/floor/feedback) are now written only by an owner with `depth >= Phaser.MIN_ACTIVE_DEPTH`; depth is always the owner's; the retained rate keeps the sweep on its own timeline across handoffs, mirroring the delay's retained drain config. Round 2 refinements: the kernel gate reads the STORED setter-coerced depth so the two gates can never disagree (a NaN depth previously handed the orbit the previous owner's ENTIRE phaser), and a fast path (`rate == 0 && gated && !engaged`) keeps the unconditional clock from taxing phaser-less orbits — provably equivalent, since a zero rate cannot move the phase and alpha is recomputed on the next engaged block. Guards: `PhaserClockSpec` bus rows + `OrbitBusPipelineSpec` kernel-retention and NaN-gate rows, mutations killed |
| D3 | Orbit delay ring clock is GATED: `KatalystDelayEffect.process` short-circuits at `delayTimeSeconds < 0.01`, and `DelayLine.process` is the only place `writePos` advances. The lease owner rewrites `delayTimeSeconds` EVERY block (`Cylinder:139`) and `VoiceFactory:159` defaults it to 0.0 — so ANY no-delay voice taking the orbit lease freezes the ring MID-TAIL. `delayHasTail()` (`Cylinder:279`) requires `delayTimeSeconds > 0.001`, so the frozen tail is also INVISIBLE to the cleanup scan while the orbit stays active. When a delay voice reclaims the lease, the stale tail resumes verbatim, displaced by the (block-quantised, framing-dependent) freeze length — echoes that pause, then reappear minutes later. One-orbit `bd.delay()` interleaved with plain `hh` is the common reach. Coordinator-verified at every site | MAJOR | **FIXED 2026-08-28 (maintainer's design, decided in-session)**: `KatalystDelayEffect` gained an Active/Draining/Off lifecycle. An off-config never reaches the DSP (`configure` flips the flag; the DRAIN runs on the retained last-active time/fb/cap), the tail keeps MIXING OUT on its own timeline (echoes complete on schedule; live sends are discarded — the owner said off), and a closed-form SAMPLE-counted countdown (`DelayLine.drainSamplesUntilSilent(peak)`: `ceil(ln(threshold/peak)/ln(abs(fb))) + 1` periods at hasTail's own 1e-5, with `peak` measured by `tapWindowPeakAbs()` — round 4 shrank that measurement from the whole ring to the `delayInt + 2` samples the tap can still REACH: older content is overwritten before the tap arrives, so the whole-ring scan both cost most of a block budget per off-transition and inflated the drain by up to 10 s of history) says when the ring is provably inaudible. Then ONE terminal `reset()` (makes the ring literally all-zero, killing the pre-drain regions a longer future delaytime could tap — the resurrection guard proved this reachable) and a true short-circuit. `abs(fb) >= 1.0` returns POSITIVE_INFINITY: self-oscillation is the authored sound and never auto-drains (raw; escape = a new owner with a tame delay, or orbit teardown — an EMPTY self-osc ring still deactivates — review round 1 caught the first implementation hard-wiring `Draining -> true` when that state was reachable with an empty ring (a leak of one whole `PlaybackEngine` per stop through `anyActive`/`hasOwnSound`/`isIdle`); round 3 moved the protection to the CONFIGURE door (a silent tap window goes straight to Off, so the infinite countdown never starts — `OrbitCleanupTest` guards it end-to-end), and round 4 made the invariant the code: `Draining -> true` BY CONSTRUCTION (entered only with a supra-threshold tap window), the false direction mutation-killed; round 5 corrected the round-4 proof — at high fb the countdown outlasts a full ring revolution, so a whole-ring scan COULD answer false near the end: the arm is CONSERVATIVE by up to one delay period, never wrong in the tail-cutting direction. Round 5 also restored factory DSP params in the effect's reset() (the setters drop non-finite writes, so a NaN param from the next orbit life would otherwise inherit a dead life's value — the old resetBusEffects zeroed them, the first lifecycle version did not). The hasTail conservativeness proof is scoped to fb <= 1, the sub-threshold bloom-back corner is accepted raw. Round 1 also hardened the countdown bound with `DelayLine.capHighWater`; round 2 refined it to WRITE-time cap tracking, and round 3 replaced the static bound entirely with the MEASURED ring peak (`ringPeakAbs()`, one O(ring) scan at the off-transition — the accepted teardown cost class): the countdown now starts from what the ring actually holds, so a barely-used delay drains in seconds instead of the saturated worst case (which had kept a stopped playback's engine alive for the full theoretical drain, a stop-path regression vs the old instant-freeing gate), an already-silent ring — the empty self-osc case included — goes straight to Off, and the cap drops out of the math (the measured peak already reflects it). The countdown also decrements by the same CLAMPED frame count the DSP processed (round 2). **DECIDED (maintainer 2026-08-29): the charged `|fb| >= 1` pin is INTENTIONAL** — it is the guitar-feedback instrument: you build a real feedback and RELEASE it by sending a note with low feedback (a tame-delay owner takes the lease and the drain takes over). No orbit-side post-stop bound wanted). Guards: `KatalystDelayEffectSpec` (5 drain rows incl. drain == active-with-silent-send bit-identity, no-resurrection sweep, fb >= 1 drone, sample-counted framing row) + `DelayLineSpec` formula pin; 6 mutations killed |
| D4 | `PhaserCore.prepareBlock` samples the LFO WAVEFORM at block boundaries and lerps α across the block: effective LFO Nyquist is `fs/(2*blockFrames)` (~187 Hz at 48k/128). Above it the sweep ALIASES at the block rate — a structurally different sweep per block size (I2), and `rate` is a raw user param on all three doors. KDoc bounds the error only for its assumed regime (<=10 Hz, <=512 frames) | MINOR | **DONE 2026-08-28 (doc-only)**: bound named at `PhaserCore` (the source of truth) and the ignitor door's user KDoc; `rate` stays unclamped (raw Motor) |
| D5 | `PhaserIgnitor` bypass leaves the allpass cascade dirty (`z1[]`, `lastOutput` keep last-live-block samples) — the sibling `ShimmerIgnitor` in the SAME file documents the opposite policy (C4.1: state cleared on bypass entry). Resume transient at an alignment-dependent block boundary | MINOR | **FIXED 2026-08-28**: C4.1 clear-on-bypass-entry now on BOTH phaser doors (`PhaserIgnitor` stateDirty latch, bus `Phaser` engaged latch) — one policy per file; `PhaserCore.reset()` has callers now (D9). Mutations killed on both doors |
| D6 | `ShimmerIgnitor` bypass clear is INCOMPLETE: the C4.1 wipe clears ring/grains/feedback/LPF but not `samplesUntilNextGrain`, `nextIntervalIdx`, `writePos`. The bypass entry lands on the (framing-dependent) block grid, so after resume EVERY grain onset is shifted by the difference — and if a grain fire falls inside the differing window, the pitch round-robin differs by one for the rest of the note: the cloud opens on a FIFTH instead of an octave depending on where blocks fell (I2+I4, plainly audible) | MAJOR | **FIXED 2026-08-28**, and the fix had to GROW: `samplesUntilNextGrain = 0`, `nextIntervalIdx = 0` AND `writePos = 0` — the widened guard failed on the first (writePos-keeping) fix and exposed that the surviving write head is SEAM-VISIBLE: a grain's lookback wraps the ring end at the ABSOLUTE index, so a block-quantised surviving writePos misaligns the resumed cloud's warmup against the seam. Guard `ShimmerSchedulerSpec` ("gap resumes exactly like a first engagement"), 4 mutations killed. TWO harness lessons for the ledger: (a) the first guard was VACUOUS — after a clear every grain plays zeros until the write head refills its lookback, and the first AUDIBLE wet arrives only when the fifth-pitched grain wraps the seam into written content (~block 61 at 44.1k/128), so a comparison window that ends earlier compares dry against dry; the guard's own mutation check caught it (the O1 lesson repeating); (b) same again one level deeper for the zero-length probe (first placed at block 45 — still inside the silent warmup) |
| D7 | Zero-length window + MODULATED `wet`: `blockStartValue` returns the deterministic 0.0 (the E5 fix) but shimmer consumes it as a PREDICATE — a length-0 window reads as "bypass" and wipes the whole grain engine. Terminal-only today (harmless: nothing renders after it); DESTRUCTIVE if B1 ever makes zero-length mid-note/first windows reachable. `PhaserIgnitor` takes the same false branch but (after D1) would merely skip a length-0 advance — inert | note | **FIXED 2026-08-28** on both doors (`stateDirty && ctx.length > 0`): a window that renders nothing decides nothing. Mutation-killed on both (the shimmer kill needed the probe INSIDE the audible-wet region, see D6 lesson (b)) |
| D8 | Shimmer's Class 2 knobs are UNNAMED: `wet`/`feedback`/`tone`/`dryFloor` block-start reads drive an `lpfA` coefficient switch and a wet/dry gain staircase (step rate = block rate), and the KDoc says nothing — while the phaser KDoc names "read once per block" and the bus phaser names its zipper explicitly | MINOR | **DONE 2026-08-28 (doc-only)**: shimmer KDoc names the four control-rate knobs, the LPF coefficient switch, and the sample-anchored grain machinery |
| D9 | `PhaserCore.reset()` has ZERO callers and `Phaser` exposes no reset at all — `Cylinder.resetBusEffects()` (the "reused orbit starts clean" contract) resets body/vowel/delay/reverb but can only zero the phaser's depth/floor; allpass state and LFO phase survive orbit reuse (~4 samples of memory, negligible — but the contract says otherwise and the method that would satisfy it is dead) | note | **DONE 2026-08-28/29**: `Phaser.reset()` added (clears both cascades + the engaged latch; LFO phase survives — the BYPASS path needs it) and wired into `resetBusEffects`; round 2 added `zeroLfoPhase()` there too (a TEARDOWN must not carry a sweep position that is "blocks the previous life stayed active x rate", a cleanup-schedule artifact) and caught that the WIRING lines were unkilled mutations — the orbit-reuse clean-slate guard (`OrbitBusPipelineSpec`, bit-identical vs a fresh cylinder) plus the inaudibly-charged-ring teardown guard (`OrbitCleanupTest`) now kill all three wiring drops. Round 3 then caught the fix HALF-DONE: teardown zeroed the phase but kept the dead owner's RATE, so the sweep free-ran through the next life's phaser-less stretch and re-engaged mid-sweep by a cleanup-schedule-dependent offset (and the fast path never fired for that orbit again). `Phaser.resetForReuse()` now restores full factory state (cascade, latch, phase, kernel params) — the retained-kernel rule is scoped to owner handoffs WITHIN one orbit life; the reuse guard gained the phaser-less interlude that makes the stale rate observable, and a rate-0 static-notch guard kills the fast path's load-bearing `!engaged` term. That last guard's first formulation also corrected a design misunderstanding: `hasTail()` scans the WHOLE ring, so a charged drain keeps the orbit alive until the countdown's own terminal reset (old copies sit at full amplitude until the head wraps) — the reachable resetBusEffects case is an ACTIVE ring that only ever held sub-threshold content |
| D10 | `DelayLine` time changes move the read tap INSTANTANEOUSLY once per block (`delayInt`/`alpha` hoisted per process call, no crossfade/ramp); the class KDoc's "smooth modulation (no zipper)" promises more than the code does (the fractional read removes QUANTISATION zipper only). Reachable only at a lease handoff today (times are per-voice constants) | note | **DONE 2026-08-28 (doc-only)**: `DelayLine` KDoc now says the tap is resolved once per process() call and JUMPS on a change, no crossfade — the old "no zipper" claim is scoped to the integer-quantisation zipper the fractional read actually removes |
| D11 | Orbit tail/deactivation grace is counted in BLOCKS x round-robin: `silentBlocksBeforeTailCheck = 10` and `tryDeactivate` visits ONE cylinder per block, so the wall-clock grace is `10 x allocatedCylinders x blockFrames` — 53 ms at 2 orbits / 128 / 48k, ~6.8 s at 256 orbits. No audio is cut (the scan itself protects tails), but the clear frame moves with block size AND orbit count — and it bounds how long a D3 frozen ring persists. `PlaybackEngine:44-45` already shows the seconds-derived pattern | note | **NAMED 2026-08-28/29 (doc-only)**: Class 2 comment at the `Cylinder` declaration spelling out the `silentBlocksBeforeTailCheck x allocatedCylinders x blockFrames` wall-clock grace, and (round 4) that everything a TEARDOWN does rides this schedule — including the phaser's clean-slate sweep restart, so a sparse phaser pattern whose gaps straddle the grace at one block size but not another re-enters the sweep differently. Points at the `PlaybackEngine` seconds-derived pattern if it ever needs pinning |
| D12 | `DelayLine.process` wrap split handles exactly ONE wrap (`firstChunkLen = min(length, bufferSize - writePos)`), so `length > bufferSize` writes past the ring. Unreachable at 128 — but the smallest master ring is `MIN_TIME_FX + 0.05s` ~ 2880 frames at 48k, and block size is a tone parameter: a host with `blockFrames > 2880` overruns on the shortest master delay | note | **FIXED 2026-08-28**: the wrap split is a while-loop over chunks — any `length` wraps correctly, no size assumption left in the pointer logic. Bit-identical for the single-wrap case |
| D13 | E8 continuation, first STATEFUL-EFFECT exposure: a shimmer shared across a `detune` boundary (`let s = ...shimmer(...); s + s.detune(12)`) hits the `MemoizingIgnitor` with two `freqHz` values per block — the key splits and the inner advances `2 x length` per `length` frames: the grain clock runs at 2x real time (24 grains/s instead of 12, lookback halved). Framing-INVARIANT (I2 holds — the ratio is 2x at every block size) but violates the contiguous-advance contract | note | **DECIDED (maintainer 2026-08-29): drop `freqHz` from the memo key for effect nodes** (they only forward it, never derive state from it) — not yet implemented, scheduled with the reverb drain adoption as the next batch |
| D14 | Bus params land on the BLOCK grid: `SendRenderer` runs on the voice's partial first block and `getOrInit -> updateFromVoice -> applyBusEffects` reconfigures the whole orbit for that ENTIRE block, while `KatalystContext` has no offset — the new lease owner's delay/reverb/phaser settings apply to the `ctx.offset` samples BEFORE its own onset, i.e. to the previous owner's still-decaying tail; the transition frame moves with alignment (I4). Inherent to a block-continuous bus with a per-voice trigger | note | **NAMED 2026-08-28 (doc-only)**: KDoc on `Cylinder.updateFromVoice` records block-granular bus params as a named Class 2 knob (a new owner's settings also govern the offset samples before its onset) |

**Found by review round 1, OUT of this class (filed for the modulation-effects round):**
`TremoloIgnitor` (`IgnitorEffects:526,536-546`) has the exact D1/D2 shape — its `phase` advances
only inside the engaged loop, so a modulated `depth` dipping to 0 freezes the pulse clock
block-quantisedly. Params already tick unconditionally; only the phase advance is gated. Same fix
shape as D1 when that class is audited; not fixed here to keep the class boundary.

**Next step (decided by the maintainer, "concrete first, abstraction next"):** `KatalystReverbEffect`
has the SAME gate shape (`roomFade == null && roomSize < 0.01` returns above `reverb.process`, the
only comb advance) — the drain lifecycle generalizes to it as its own follow-up commit. The reverb
case is strictly easier: comb feedback is structurally < 1 (roomSize normalized in VoiceFactory),
so there is NO self-oscillation sentinel, and its buffers are ~40x smaller. The adoption fixes the
GATE shape only; the Freeverb internals still get their own audit round.

**Also recorded (off-lens, not framing):** `ShimmerIgnitor` allocates a fixed `AudioBuffer(96_000)`
(~768 KB) per voice on the graph-build path, which runs on the audio thread via `scheduler.process`
— constant-sized so not this bug class, but it belongs on the warehouse-pool radar. And the
`applyBusEffects` KDoc claim "nothing leaks from a previous owner" is contradicted by D3: a frozen
ring is exactly a deferred leak.

**Class calls for the P2 catalogue:** `DelayLine` core, `MasterChain` delay stage, `MasterBus`
crossfade, shimmer grain machinery (live path), `SendRenderer` indexing = **Class 1**. Everything
else in scope = **Class 2** with knobs as named above (per-block param reads; PhaserCore's
block-boundary LFO sampling with lerped alpha; bus-side per-block config application; block-counted
tail grace). `StripPhaserRenderer` is Class 2 solely through PhaserCore's alpha knob — all five of
its own params are voice constants.

## Order of work — one thing at a time

Two tracks, independent by construction (that is the point of the A/B split). Track A never waits
for Track B.

**Track A (DSP, under the assumed contract):**

**P0. Harness + the four invariants** — **BUILT 2026-08-28: `BlockFramingInvarianceSpec`.**
Two drivers (the real `VoiceFactory` -> `Voice.render` framing, and a raw `IgniteContext` loop for
ragged sequences and runtime-only chains); non-round durations so gate end and breakpoints land
mid-block everywhere; sweeps onset {1,37,76,127}, sizes {64,37} and a ragged sequence.
Results: `Adsr`, `Sine`, `WhiteNoise`, `Pluck` and (since the E1 fix, 2026-08-28) `fm with
envelope` are **bit-identical** across all of it (maxDiff exactly 0.0 — the Class 1 claim holds
with no tolerance at all for these five). Acceptance met at build time: E1 and E3 both reproduced
RED under their correct assertions. E3 remains a PINNED defect tripwire that goes red the moment
the defect is fixed, forcing the flip to the correct form written above the pin; E1's pin was
flipped and graduated when E1 was fixed. Mutation-checked: re-introducing instance 1 (the `IgniteRenderer` offset bug) turns
the harness red, i.e. it would have caught the guitar-knocks bug; a wrong-pid vacuousness mutation is
also caught.

**P1. Triage the three siblings still on the 2026-08-07 deferred list** (two are audible), so this
sweep starts from a known board rather than rediscovering them a third time.

**P2. Exhaustive sweep** over all 78 node types with block-constant parameters. Triage into
fix / block-rate-by-design / maintainer decision. Do not fix during triage; produce the list first.

**P3. Fix, one node per commit**, each with the failing case turned into a permanent guard and
mutation-checked. Gradual is the point.

**Track B (scheduler, delivers the guarantee):**

**B1. Startup protocol** so the first notes are never late (rough sketch above; own design pass).
**B2. Flip admission to hard-drop** + per-playback dropped-voice counter; delete `oldestAllowedSec`,
the sample floor, and the two E4 code paths' reachability concern. B2 never lands before B1.

**Track A continued:**

**P4. The strip renderers**, which have their own copy of the same arithmetic: `EnvelopeRenderer:86`,
`PitchEnvelopeRenderer:30` (both already use `blockStart + offset`), `FilterModRenderer`, `FmRenderer`,
`SendRenderer`, and `Voice.render` itself.

**P5. The sample path** end to end, given instance 2.

## Settled (2026-08-28), recorded so they are not re-opened

- **Block size must be treated as non-constant.** It is 128 in the browser only because the render
  quantum is handed to the worklet; other platforms may choose it. Nothing may be implemented against
  128. The tone consequences of changing it are understood and accepted — target A, not B.
- **Sub-sample onset accuracy is NOT required.** Flooring the start position to a SAMPLE is fine
  (`VoiceFactory:88` already does exactly that); flooring it to a BLOCK is the bug. (An earlier
  revision accepted a late-voice exception here; superseded 2026-08-28 by the no-late-voices rule.)
- **No late voices, ever (2026-08-28).** The two problems are split: the scheduler guarantees the
  contract, the DSP assumes it. Late voices are dropped and counted per playback; the startup race is
  fixed on the scheduler side first. See "Enforced at ONE place".

## Related

- `docs/tasks/ignitor-envelope-ownership.md` — where instance 1 surfaced.
- `IgniteOnsetOffsetSpec` — the guard for instance 1, and the template for the harness.
- `VcaOffTeardownSpec` — the teardown-side equivalent, already sweeping fractional and short spans.
