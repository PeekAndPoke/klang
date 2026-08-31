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

Filed independently on 2026-08-21 from a code read alone, before this audit existed:
`docs/tasks-archive/2026-08/20260831-voice-elapsed-frames-offset-mismatch.md`.

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
| E2 | `fmModIgnitor` early returns skip `modulator.generate`, freezing its phase for whole blocks; `VibratoModIgnitor` same shape | MINOR | **FIXED 2026-08-28**: modulator/LFO advance unconditionally; phase-continuity guards in `PitchModFactoriesSpec` (a depth gap no longer freezes the phase). **Accepted costs (round-2 review, own record on request):** a structurally constant `depth = 0` fm now renders its discarded modulator every block, and the modulator's drift init consumes **3** per-voice rng draws it previously never took (`analogDriftGaussian` = 2 × nextDouble + one nextInt; corrected from 2 in the oscillator audit) — within-voice draw-order shift only. Note the unconditional-at-analog-0 construction holds only for the `initAnalogDrift` callers (sine/impulse/pluck/superpluck); `WaveIgnitor` and `DetunedStackIgnitor` guard on `amt > 0` and draw nothing. A safe skip would need a LITERAL-constant test — `isBlockConstant` is NOT sufficient, its value may legally change between blocks. **Accepted residual:** a nested fm whose OUTER ratio is modulated to <= 0 sends `freqHz * ratio <= 0` to the inner node, which then takes the note-less bypass and freezes its modulator for those blocks — exotic by construction, recorded at the bypass comment. Post-freq-param addendum (2026-08-30): a general MODULATED `Fm.freq` expression crossing <= 0 takes the same bypass-freeze shape (the freq expression itself still advances — it is the gate input — while modulator/params freeze); sign stability holds only for the constant-valued forms (default/Constant/Param) |
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
| D13 | E8 continuation, first STATEFUL-EFFECT exposure: a shimmer shared across a `detune` boundary (`let s = ...shimmer(...); s + s.detune(12)`) hits the `MemoizingIgnitor` with two `freqHz` values per block — the key splits and the inner advances `2 x length` per `length` frames: the grain clock runs at 2x real time (24 grains/s instead of 12, lookback halved). Framing-INVARIANT (I2 holds — the ratio is 2x at every block size) but violates the contiguous-advance contract. A shared bare OSCILLATOR across the same boundary is equally broken (one phase accumulator advanced by both renders — both arms buzz at block rate) | note | **REDESIGNED with the maintainer 2026-08-30 (supersedes the 2026-08-29 "drop freqHz from the memo key" decision) — build-time fork at the detune boundary + identity fold.** The design round first pinned the SEMANTICS: (1) `s + s.detune(12)` always produces s overlaid with the INSTRUMENT transposed up, no matter what s is (transposed instance, never audio pitch-shifting); (2) detune affects ONLY musical frequencies — and that separation already exists STRUCTURALLY: every oscillator's freq param defaults to the `Freq` leaf ("read the freqHz argument" = the note), while `Osc.sine(5)` is `Constant(5.0)` and ignores the argument, so `DetuneIgnitor`'s freq-argument multiply already transposes exactly the Freq-derived pitches — LFO sweeps stay put, keytracked params (`Osc.freq()*4` cutoff) follow the detuned arm, note-relative LFOs transpose as authored. THE RUNTIME IS CORRECT AND STAYS UNTOUCHED; only the sharing is broken. Rejected on the way (do not relitigate): dropping freqHz from the memo key (shared detune becomes a unison — detune silently inert, violates anchor 1); runtime state reconstruction from a frame counter (the second caller needs a full parallel state history, not a rewind; snapshot/restore = block-rate phase snapping; per-freq state forks are unbounded under modulated ratios + audio-thread allocation); bubbling detune as a phaseMod like vibrato (violates anchor 2 twice: `applyMod` wraps EVERY source — binary ops forward the mod to both arms — so a spine `Osc.sine(5)` LFO would transpose, and params would read the un-detuned note, breaking keytracking). THE FIX: `IgnitorBuildCache`'s identity key grows a third component — the DETUNE CONTEXT, the chain of enclosing `IgnitorDsl.Detune` nodes, threaded through `buildIgnitor` like `accumulatedMod`. Two references to one node under different detune contexts build independent instances (the exact mirror of the existing "different mod chains -> independent Ignitors" rule), so `s + s.detune(12)` is two honest instances and the two-freq collision becomes UNCONSTRUCTIBLE through the DSL door: sharing never crosses a detune boundary, and within one instance freq is single-valued per window (freqHz stays in the memo key, now purely defensive). Plus the maintainer's IDENTITY FOLD, which is semantically load-bearing, not perf: at the Detune build arm, walk the inner subtree — if it contains NO musical-frequency consumer (no `Freq` leaf incl. oscillator defaults, no pitched sample node), the detune folds to identity: build inner in the UNCHANGED context (stays shared) and skip the wrapper. Two noise instances draw different random streams, so folding is what preserves "detune is a pitch op; noise has no pitch" — `n + n.detune(12)` stays the same noise doubled (+6 dB), not two decorrelated noises. Costs and scope: a subtree referenced under k detune contexts builds k times (the honest cost of the overlay, same as authoring it k times); the Kotlin raw door keeps the existing stance (hand-built graphs own their sharing); closes the detune half of E8 (the fm half was already structurally closed — modulator and carrier build under different mod identities). Observed but NOT changed here, filed for the modulation round: the phaseMod family itself lacks the musical/absolute separation — a spine `Osc.sine(5)` inside a vibrato'd subtree gets wobbled today. **IMPLEMENTED 2026-08-30** (review round 1: 2 fresh Opus reviewers, 2x3 MAJORs converging on the same three, all fixed): `IgnitorBuildCache` key = (node, mod, detune context) with the context SNAPSHOT taken before the scan (the entry files under the context it was looked up in); `DetuneContext` is a fieldless identity token; the fold predicate `usesMusicalFreq` lives ON the cache, identity-MEMOIZED per build (a shared-`let` diamond would otherwise walk exponentially — the optimizer's seen-set precedent), resolves Variants through the one shared `pick()` the build dispatch also uses, answers a nested Detune from its INNER only (a folded detune's semitones is dead code), and — SUPERSEDED SAME DAY by the maintainer's de-special-casing — originally classified `IgnitorDsl.Fm` as a musical-freq consumer unconditionally (round 1 had found `fmModIgnitor` consuming the freq ARGUMENT with no Freq leaf). The Fm arm is now DELETED: `IgnitorDsl.Fm` gained `freq: IgnitorDsl = Freq` (appended, wire-safe — the KSP schema hash covers evolution) and the runtime reads the RESOLVED value everywhere (bypass via `!(f > 0.0)` NaN-guard form, all six param anchors, `modulator.generate(f x ratio)`, `safeDiv(f)`), so freq-dependence is structural and the walker is pure Freq-leaf detection. SEMANTIC FLIP, intended per the anchors: absolute-freq FM under detune is now IMMUNE (previously the index and modulator shifted — FM was the one oscillator-like node whose absolute authoring could not opt out). The remaining freqHz-argument consumers in the package: the Freq leaf, the two detune multiplies, the memo key — the full exception list now lives in `IgnitorDslWalk`'s KDoc as the sweep baseline. Doors DELIBERATELY do not expose `freq` (maintainer decision 2026-08-30): a hidden internal, raw-door-only — the default is the semantics, and the dual-surface rule is satisfied by declaring the surface intentionally closed. Parity note: the sprudel strip door (`FmRenderer:49`) still divides by the note freq — out of D13 scope, now out of parity with the ignitor Fm, filed. Bonus record: an absolute `Fm.freq` coinciding with the note (ratio 1) turns the E8 modulator-vs-spine key split into a memo HIT — correct single-advance for that authoring. Freq-param campaign: 7/7 mutations killed (childNodes drop — also proven LIVE by an in-session lost-edit accident that the WalkSpec corpus caught instantly, exactly its stated purpose; modulator/safeDiv/param-anchors/bypass at the argument; runtime-arm drop; bypass form regressed) — two first-run masks taught the absolute-modulator lesson: a default-freq modulator driven at fm-freq 0 or NaN renders silence and hides an engaged mutant behind the all-1.0 oracle. The wrap site reuses a folded detune's child memo instead of stacking a second one. HONEST BEHAVIOR RECORDS (all intended, none silent): (1) the fold is a CHANGE, not a preservation — old `n + n.detune(x)` split the memo key and drew two fresh noise streams per block (decorrelated, sigma*sqrt2); the fold makes it the one noise doubled, ~+3 dB louder and mono-coherent, which is exactly `n + n`; (2) `s.detune(12) + s.detune(12)` (two structurally-equal Detune nodes) now builds two instances instead of one-shared-at-the-same-freq — inaudible for plain sines (phase-0 identical), a slow chorus at analog > 0, ~+3 dB-decorrelated for stochastic content inside a FORKED subtree (a bare-noise double-detune FOLDS both nodes instead — +6 dB coherent, same as record (1)); consistent with the engine's authoring model (the cache keys by ===; the optimizer's own KDoc: two structurally equal nodes are independent oscillators); (3) a folded detune no longer BUILDS its semitones expression, so a stochastic semitones (`.detune(Osc.crackle()...)`) shifts the voice's build-time draw sequence — the fold's own oracle ("as if the detune wasn't written") demands it; (4) a forked super-oscillator serves the per-orbit phase pool twice, shifting pool state for later voices (no shipped song shares a super-osc under a detune). Sound-preservation sweep: all 15 shipped `.detune(` sites (14 songs + IgnitorDefaults; a 16th grep hit is a comment) wrap freshly-authored oscillators — every song and doc patch builds bit-identically (same instances, same rng order). Guards: `DetuneForkSpec` 8 rows — shared-vs-authored-twice bit-identity (both orderings: the detune-FIRST row is what pins the context POP, every other shape is blind to a dropped restore), the stateful-effect headline (shimmer grain clocks fork), the semitones-outside-the-scope INEQUALITY oracle (a noise shared between the semitones expression and the plain arm stays ONE instance), the fold's doubled-noise identity, Variants-resolve-before-fold (both indices), the freq-argument-multiply characterization (labelled: passes pre-D13), and the d+d dedup row armed with analog 0.9 (at analog 0 two phase-0 sines are bit-identical and the row was blind). Round 2 (2 fresh reviewers): zero code defects — every round-1 fix traced clean (the Fm arm restores round-0 exactly for absolute-FM; the wrap-site alias is behavior-neutral and the ONLY arm that can return a bare memo; the snapshot is pure defense; the predicate's un-memoized bypass arms are bounded, out-degree <= 1). One MAJOR was a TEST GAP: the Fm arm shipped unguarded — closed then, and RESHAPED by the freq-param change into three rows (default-freq FM transposes via the structural Freq leaf; absolute-freq FM is ARGUMENT-independent — the first cut of that row was a tautology, review round 1 of the freq change caught it; absolute FM inside a forked subtree reads the param not the argument, with a default-freq modulator and Freq-bearing depth pinning the modulator and param anchors) plus two `PitchModSafetyTest` rows pinning the bypass anchor both ways; with the code clean and the gap closed by the mutation protocol itself, the loop terminates here. Round-2 additions to the honest records: (5) the fold protects pitch-free subtrees at the detune ROOT only — a pitch-free island INSIDE a forked subtree is duplicated with it and decorrelates ("the instrument transposed is a second instrument"; pre-D13 already decorrelated that shape through the split key); (6) the fork's per-instance build cost has real magnitudes — a second shimmer is ~750 KiB of ring on the audio thread at note-on (warehouse-pool radar); (7) the forcing function for the next freq-argument consumer: `childNodes`' KDoc now sends every new node author to `usesMusicalFreq`, whose own KDoc records the conservative false-positive direction (a Freq leaf inside a wave/super param slot resolves to actualFreq, so the fork can trigger where the detune provably cannot reach — never loses a detune). Mutation campaign: 12 mutations, 12 killed by designated rows (push/pop each direction, fold/fork both ways, the Fm arm, walker Variants arm + pick divergence, ctx dropped from the key, semitones scope, nested-Detune arm, memo poisoning, cache-never-hits) across the final `DetuneForkSpec` (13 rows after the freq-param reshape); two first-run survivors taught the twin-oracle lesson — a mutant that warps BOTH sides of a shared-vs-authored-twice comparison identically is invisible to it, so always-fold is designated to the Variants-free characterization rows and the Variants index-1 half now compares against a Variants-free reference. The wrap-site alias and the getOrPut snapshot are recorded as UNGUARDABLE (behavior-neutral by construction — both round-2 reviewers). Pre-existing, filed not fixed: the fm MODULATOR-vs-spine share still splits the memo key (`let m = ...; x.fm(m,...) + m` — the "fm half structurally closed" claim covered only modulator-vs-carrier; E8 remains real, and the memo's freqHz component stays LOAD-BEARING for it — KDoc corrected), and the build-time release tail resolves at the UN-detuned `cache.freqHz` while the runtime envelope reads post-detune freq (a freq-relative release inside a detuned subtree sizes voice lifetime from the wrong pitch; the fork's per-scope build pass is where a detuned freqHz could be threaded when fixed) |
| D14 | Bus params land on the BLOCK grid: `SendRenderer` runs on the voice's partial first block and `getOrInit -> updateFromVoice -> applyBusEffects` reconfigures the whole orbit for that ENTIRE block, while `KatalystContext` has no offset — the new lease owner's delay/reverb/phaser settings apply to the `ctx.offset` samples BEFORE its own onset, i.e. to the previous owner's still-decaying tail; the transition frame moves with alignment (I4). Inherent to a block-continuous bus with a per-voice trigger | note | **NAMED 2026-08-28 (doc-only)**: KDoc on `Cylinder.updateFromVoice` records block-granular bus params as a named Class 2 knob (a new owner's settings also govern the offset samples before its onset) |

**Found by review round 1, OUT of this class (filed for the modulation-effects round):**
`TremoloIgnitor` (`IgnitorEffects:526,536-546`) has the exact D1/D2 shape — its `phase` advances
only inside the engaged loop, so a modulated `depth` dipping to 0 freezes the pulse clock
block-quantisedly. Params already tick unconditionally; only the phase advance is gated. Same fix
shape as D1 when that class is audited; not fixed here to keep the class boundary.

**Reverb drain adoption — DONE 2026-08-30 (the decided follow-up, own commit):**
`KatalystReverbEffect` had the SAME gate shape (`roomFade == null && roomSize < 0.01` above
`reverb.process`, the only comb advance) and the same frozen-tail class; it now owns the delay's
Active/Draining/Off lifecycle: `Cylinder.applyBusEffects` routes through `configure(...)` (an
off-config never reaches the DSP; the drain runs on the retained last-active params), the
countdown is `Reverb.drainSamplesUntilSilent(peak = combPeakAbs())` — for a comb the WHOLE buffer
is the tap window (every cell is future output), and comb feedback is structurally in [0.7, 0.98]
(`normalizeRoomSize` + the configure door's roomFade bound), so there is NO self-oscillating
lifecycle — then one terminal `Reverb.reset()` and a true short-circuit. Scope held: gate shape
only, the Freeverb internals keep their own audit round. The review loop earned its keep here —
round 2 REVERSED round 1's headline fix on measurement: (1) **`hasTail()`'s Draining arm is
`true` BY CONSTRUCTION, same as the delay's** — round 1 had flipped it to a live network scan,
arguing the fb-only countdown ignores damping and pins a stopped playback's engine ~20x too long
("a dark fb-0.98 room dies in ~1 s where the bound grants 20+"); round 2 disproved the number
with the filter itself: the comb damping LPF has UNITY DC gain, so a tail's low-frequency
component decays at exactly fb per revolution no matter how dark the room (damping darkens a
tail, it does not shorten it — `PlaybackEngine`'s own -96 dB/20 s note agrees), a scan beats the
countdown by ~one revolution (~37 ms) for LF-bearing content and ~30% only for purely-HF charges
(LPF impulse smearing), and the scan arm was additionally unpinnable (a `Draining -> true`
mutant passed the whole suite) and had silently defanged two other guard rows built for the
by-construction shape. Restored to the reference arm: O(1) cleanup polls, sibling symmetry, hold
bounded by the measured-peak countdown (the same span a live-ownered ring-out always held). The
full flip-flop is recorded in the `hasTail` KDoc so nobody relitigates it. (2) The door
hardened (rounds 1+2): `+Inf` roomSize read as Active while the setter dropped the write
(inheriting the previous owner's room), NaN roomLp inherited the previous owner's damping, the
roomFade AND roomSize 0..1 stability bounds now live INSIDE `configure` (one conversion, any
caller; production bit-identical via VoiceFactory's normalize), and the MASTER door's
`buildReverb` was incoherent on non-finite roomFade (gated on `isFinite` but wrote `coerceIn` —
+Inf became the LONGEST room while the orbit door read it as unset): both doors now agree,
non-finite fade = no override (parity row in `MasterOrbitReverbParitySpec`). Non-finite
countdowns reset immediately (the heal the old gate's takeover path provided) — and round 2
found the NaN half: `NaN > peak` is false, so a magnitude scan is BLIND to NaN cells and a
finite peak beside them would start a finite drain that pumps NaN into the mix; `combPeakAbs`
now reports ANY non-finite cell as +Inf (comb-only suffices: allpass poison implies comb poison
first). (3) The decay-bound KDoc took two rounds to get right: round 1's fix introduced a
dominant-root "damping stretches the loop" bullet that round 2 showed is real only for toy-sized
combs (`damping^N` is zero for every supported rate); the surviving truth is that the LPF store
carries across the off-transition bounded by `peak/|fb|`, which alone makes the `+1` spare
revolution LOAD-BEARING. Also recorded: terminal-reset/cleanup cuts quantified at the -100 dBFS
class (allpass exclusion safe in both directions; round 2 corrected round 1's single-stage
memory figure to the ~1.66k-sample series), always-reverb orbits verified bit-identical through
the new door for ALL finite params including the 0.01 boundary, never-reverb orbits
identical-and-cheap, and the one-time off-transition comb scan (~22k reads) inside the delay
round's accepted teardown cost class. Pre-existing bonus fix: a takeover with normalized
roomSize in (0.001, 0.01) used to freeze the network while the tail check still reported true,
pinning the orbit forever. INTENDED audible change: an orbit alternating reverb and dry voices
now rings its tail out instead of cutting it (maintainer ear pass before commit). Guards:
`KatalystReverbEffectSpec` (18 rows: drain bit-identity with garbage sends, countdown pins,
no-resurrection, mid-drain takeover, non-finite door, both room bounds at the door, Inf+NaN
poison heal, mismatched context, sample-counted framing), `ReverbStabilitySpec` formula/peak
pins, `OrbitCleanupTest`
inaudibly-charged clean-slate row (kills the resetBusEffects wiring drop, a network-only-reset
mutant AND an Active->true hasTail mutation; the delay sibling row gained the factory-param
assert as a retrofit), `OrbitBusPipelineSpec` drain-through-cleanup rewrite of the old
freeze-row, `MasterOrbitReverbParitySpec` non-finite-fade parity row. The old fresh-off spec row
was vacuous (single-block probe cannot see comb latency; delay sibling `KatalystDelayEffectSpec:35`
shares the weakness — retrofit opportunistically when touched), and round 2 put audibility
thresholds on the wet-presence rows (any-nonzero was satisfied by the ~1e-20 anti-denormal
residue). Round 3 (2 fresh reviewers, zero CRITICAL/MAJOR — the clean round) closed with a
polish batch: the roomSize door bound got its guard row, vacuous asserts were armed (the
cleanup row's voice now carries a roomFade; the parity row proves "unset", not fresh-default;
the NaN-heal pin uses the NaN-hardened `combPeakAbs`, since `hasTail(0.0)` is NaN-blind), and
prose numbers were corrected. OPEN FOR THE DELAY'S NEXT TOUCH: `DelayLine.tapWindowPeakAbs`
still has the NaN blindness fixed here (`NaN > peak` is false) — a NaN-poisoned ring drains
NaN into the mix for its finite countdown before the terminal reset heals it (bounded, unlike
the reverb's would-have-been case, so it was left for the sibling's own round). Also parked as
a maintainer taste call: `Reverb.damp`'s setter accepts finite values > 2.5 where the comb LPF
coefficient exceeds 1 and the network diverges (production never reaches it — the orbit path
never writes damp, the master clamps); a setter clamp would mirror normalizeRoomSize's
stability-bound reasoning, but that is a raw-Motor decision. Mutation campaign: 29 mutations,
29 killed by their designated rows (door gates both axes, non-finite matrix, off-path
write-through, live-send drain leak, double/hardcoded countdown ticks, clamp drop, both hasTail
constant arms, takeover block, terminal-reset drop, factory-param drops, both cylinder reset
wirings, formula slack/sentinel/channel drops, master fade door) — one initially SURVIVED:
`reverbHasTail() = false` in `tryDeactivate`, because the drain row only ever polled cleanup
under an AUDIBLE mix (the silence gate short-circuits before the tail check); the row gained a
cleared-mix probe and the mutant now dies. All restores byte-exact.

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

- `docs/tasks-archive/2026-08/20260831-ignitor-envelope-ownership.md` — where instance 1 surfaced.
- `IgniteOnsetOffsetSpec` — the guard for instance 1, and the template for the harness.
- `VcaOffTeardownSpec` — the teardown-side equivalent, already sweeping fractional and short spans.

### Modulation/waveshaper class — audited 2026-08-30 (two lenses: clocks/time-bases + state/memory/numerics) — FINDINGS ONLY, maintainer decisions pending

Scope: `distort`/`drive`/`shape`/`crush`/`coarse`/`tremolo`/`dcBlock` in `IgnitorEffects.kt`, the
shared `Oversampler` core, `DcBlocker`, `ScratchBuffers.oversample`, the DSL wiring, and the four
strip twins (`DistortionRenderer`, `CrushRenderer`, `CoarseRenderer`, `TremoloRenderer`).
Inventory honesty: NO bus/katalyst door and NO master door exists for anything in this class
(katalyst = Body/Compressor/Delay/Ducking/FilterSwap/Formant/Phaser/Reverb; master =
Gain/Limiter/Reverb/Delay + the always-on master DC blocker pair); `drive` and `shape` are
ignitor-only; `dcBlock` is a KOTLIN-RAW-ONLY surface (no DSL node, no wire name, no script
method, zero repo callers outside one spec — violates the dual-surface rule); `IgnitorDsl.Distort`
is DEAD on every live authoring door (both builders emit `Shape(Drive(...))`; reachable only via
legacy serialized trees / raw Kotlin); the ignitor door has NO oversample option for
`crush`/`coarse` while the strip does (`crushos`/`coarseos`); nothing in this class has a
`BlockFramingInvarianceSpec` row today.

Clean with NO findings (verified): `DriveIgnitor` stateless with a CONTINUOUS bypass
(`10^0 = 1`); `ShapeIgnitor` Class 1 with NO bypass at all (its DC blocker + oversampler advance
unconditionally — the D1-correct shape by construction; strongest green-list candidate);
`CrushIgnitor` stateless pointwise; `DcBlockIgnitor` window-exact Class 1; `DcBlocker` flushes
denormals per the house convention; the `Oversampler` core is Class 1 and framing-clean (advance
exactly `length x factor`, wrap-free FIR seams, zero-length no-op, no D12-style size assumption,
factor changes mid-note STRUCTURALLY impossible on every door); all four strip twins are immune
to the gate-freeze class (constructor-constant params + `FilterPipelineBuilder` omits inactive
stages); window arithmetic swept — zero `buffer.size`/`blockFrames` loops, zero voice-clock
reads in the class; every param read is `readParam` with the real freqHz (zero E5/O6 sites).

| # | finding | severity | disposition |
|---|---|---|---|
| W1 | `coarse` S&H bootstrap latch is BLOCK-indexed on both doors (`idx == 0 && counter == 0.0`, `IgnitorEffects:347`, `CoarseRenderer:71`): the counter re-crosses exactly 0.0 at note-relative sample `amount` for every POWER-OF-TWO amount, so when a block boundary lands there (1 onset in `blockFrames`) the hold grid re-anchors and is displaced for the REST of the note (I1+I2 with all-constant params — a Class 1 node that is not bit-identical). LIVE: `ATruthWorthLyingFor.kt:50` `.coarse(2)` (no coarseos) hits it; the other three shipped uses are safe by ACCIDENT (`coarseos` paths have no latch; amount 3 is float-inexact). Related quirk: the direct path's FIRST hold is `2 x amount` (bootstrap dips to -1.0) while the oversampled path holds `amount` from sample 0 | MAJOR (live) | **FIXED (maintainer: `counter = 1.0`, both doors)**. Round-1 corrections to this row's own claims: (a) the old latch consumed exactly `2 x amount` samples — a MULTIPLE of amount — so grid parity always survived and the old bug was a LOCAL `2 x amount`-sample displacement at 1-in-`blockFrames` onsets, not a rest-of-note shift; (b) the audible change: ATruthWorthLyingFor's coarse(2) differs at exactly samples {2,3} under a 0.6% exp-attack envelope (45 us, inaudible), but **Sakura's `.coarse(3)` shifts its hold grid for the WHOLE note** — the old float-miss made its first hold 7 samples (grid = 1 mod 3), the new bootstrap anchors at 0 mod 3, a permanent 1-sample sampling-phase rotation on a 6-voice sustained pad (character preserved: alias image PHASES move, magnitudes do not; any WAV regression diverges globally) — SIGNED OFF by the maintainer 2026-08-30 (commit as is; character preserved, grid now note-anchored). Guards: ModulationClockSpec grid rows + CoarseRendererSpec split/first-hold rows (probes made non-zero at sample 0 after round 1 caught the bootstrap-value mutant hiding behind the uninitialized lastValue), and coarse graduated into BlockFramingInvarianceSpec |
| W2 | `tremolo` LFO clock freezes through the `depth <= 0` bypass (pre-filed by the delay round, VERIFIED + narrowed: both `readParam`s already tick above the gate — only the phase advance is gated; error accumulates per wet-crossing, I2+I4). PLUS both doors hand-roll the wrap (`if (phase > TWO_PI) phase -= TWO_PI`): a non-finite rate makes phase NaN PERMANENTLY (no heal, voice outputs NaN for its life), negative rates never wrap, `phaseInc > TWO_PI` never normalizes — the house `wrapPhase` (20 sites elsewhere) fixes all three and is a PREREQUISITE for the bulk advance. Latent (script-door modulated depth; no shipped use; `IgnitorDefaults:251` advertises the recipe). Once fixed the tremolo is Class 1 for its waveform (per-sample sin, no PhaserCore-style block-rate Nyquist ceiling) | MAJOR (latent) | **FIXED (maintainer: both parts)**: bulk `phaseInc x ctx.length` advance in the bypass arm + `wrapPhase` on both doors (in-range bit-identical, 0.0 maxdiff over 2 s at 8 rates; the gap advance reassociates the float sum, ~4e-15 vs a free-running reference, the frozen-phase mutant diverges at 0.39). A non-finite rate now reads as phase 0 = a steady `1 - depth/2` gain (a level change, not silence, not NaN) and heals. Guards: ModulationClockSpec gap/NaN rows, TremoloRendererSpec (the strip door's first spec), tremolo graduated into BlockFramingInvarianceSpec (constant params only) |
| W3 | `coarse` S&H clock freezes through the `amt <= 1.0` bypass AND resumes emitting a stale `lastValue` (DC step up to full scale for a framing-dependent count). The bypass arm is REDUNDANT for amt in (0,1] (the engaged loop degenerates to a copy there) — load-bearing only for amt <= 0 and (by accident) NaN | MAJOR (latent) | **FIXED (maintainer: narrow guard)**: `!(amt > 0.0)` + isInfinite arm (the NaN-guard form catches NaN) + `invAmt = 1/amt.coerceAtLeast(1.0)` so (0,1] is a bounded-counter exact copy; crossings are CONTIGUOUS (the boundary sample carries the last held value once, the resume grid keeps the counter residual — ModulationClockSpec pins takes at 512 then 516 then the clean 5-grid, where the old code replayed input[255] as a DC step). RESIDUAL, stated: `amt <= 0` and non-finite still freeze the counter through their bypass (the clock rate is undefined there); non-finite HEALS on return, with the strip's nanGuard mirrored on the captured sample |
| W4 | `coarse` NaN amount LATCHES both doors permanently (NaN fails `<= 1.0`, poisons `counter`, both refresh predicates false forever -> frozen DC that never heals; `+Inf` behaves differently — infinite hold that DOES heal); ignitor door also lacks the strip's `nanGuard()` on the captured sample (a NaN input holds for up to `amount` samples there, zero on the strip). Low reach (arithmetic is safeOut-clamped; needs a NaN oscp override or raw Kotlin) | note | **SPLIT, both halves now DONE**: ignitor half via W3's non-finite arm (heals, guarded); STRIP half FIXED 2026-08-30 (maintainer: match the ignitor door) by widening `CoarseRenderer.render`'s existing degenerate return to the ignitor's exact form, `!(amount > 1.0) || amount.isInfinite()`. **The audit's own stated reach was wrong in BOTH directions, and the fix is the correction.** NaN could never reach this class at all — `FilterPipelineBuilder` gates on `amount > 1.0` and `NaN > 1.0` is false, so the stage is never built; the only non-gated constructor is the spec. What CAN reach it is +Inf (`Inf > 1.0` passes the gate; reachability verified, no incident on record and no shipped song passes a non-finite coarse), giving `increment = 1.0 / Inf = 0.0`: sample 0 captured, counter stuck at 0.0, frozen DC for the note's LIFE with no heal, because unlike the ignitor door's per-block `readParam` the strip amount is a per-note constant. So the real defect was never the NaN latch the row named; it was a DOOR-PARITY break (the parameter-parity rule) on a value the ignitor door already passed through. Sprudel does not coerce (`coarse = it?.asDoubleOrNull()`), so any pattern value evaluating to Infinity got there. Guard: `CoarseRendererSpec` bypass row over {+Inf, NaN} x {direct, oversampled}, probe starting at 0.01 so a frozen-DC mutant cannot hide behind an uninitialised `lastValue` (the W1 lesson); 3/3 mutations killed, one per arm of the guard (pre-W4 form, infinite arm dropped, NaN arm dropped) |
| W5 | legacy `DistortIgnitor` bypass returns before the oversampler AND the DC blocker (D5 shape: stale FIR lines + one-pole across the gap, plus a GROUP-DELAY jump of ~4-6.6 samples appearing/vanishing at a block-quantised instant) — but the node is DEAD on every live door (see inventory) | MINOR (latent, unreachable live) | **DELETED (maintainer)**: the fused DistortIgnitor is gone; the raw door and the wire node both build the documented `drive().shape()` equivalence (non-oversampled bit-identical, oversampled ulp-class; wire trees are never persisted, verified). Recorded nuance: an amount at/crossing 0 bypasses only the drive — the shaper keeps shaping at unity (tanh(1) = 0.76, -2.4 dB at full scale; the DSL door always behaved this way; the Double convenience still short-circuits a constant 0). Mapping guard in ModulationClockSpec |
| W6 | THREE bypass policies coexist in the one file the delay round ruled "one policy per file": phaser clears (D5), shimmer clears (C4.1), distort/coarse/tremolo keep-and-freeze silently. Coupled constraint: zero-length windows currently read every gate param as the E5 `0.0` and take bypass arms that do NOTHING — the moment any clear is added it needs the D7 `ctx.length > 0` guard | rule | **RESOLVED by the batch**: clock nodes advance unconditionally (tremolo), passthrough-degenerate guards are narrowed (coarse), the wet-engine nodes clear on bypass entry (phaser/shimmer, unchanged), and the third keep-stale policy died with DistortIgnitor |
| W7 | Numerics egress cluster: `drive` output is UNBOUNDED with no guard (`10^(amt*1.2)`, amt = 1e15 is legal safeOut output -> Inf/NaN into a filter unless a shape follows; `safeOut`/`softCap` exist); `dcBlock` is the one stage feeding an IIR with UNSTERILISED input (one NaN latches xPrev/y forever, no reset caller); and there is NO downstream backstop — a single voice NaN latches the MASTER DC blocker for the whole playback (`MasterStage:97-98`, reset only at warmup), which amplifies every NaN row in this class from per-note to per-playback | note (amplifier) | **DECIDED (maintainer): record only** — raw Motor stands per stage; the master-side NaN self-heal is FILED for the master's own audit round (one heal in one place) |
| W8 | Sharing exposure (E8 continuation): a shared instance re-rendered through the fm-MODULATOR-vs-spine key split double-advances `tremolo.phase` (LFO at 2x — the shimmer headline verbatim), `coarse.counter/lastValue`, the distort/shape oversampler+DC blocker (a genuine filtering error), and `dcBlock`; `drive`/`crush` immune by statelessness. Framing-invariant (the harness is structurally blind — O6's lesson) | note | RECORD against the already-filed E8/fm-modulator item; no new mechanism, five more named victims |
| W9 | Door-parity breaks (parameter-parity rule shape, all framing-invariant): the two crush doors use DIFFERENT quantizers (ignitor midtread `round` vs strip `floor` — both KDoc-argued, same param name, two sounds); ignitor `coarse` has no oversample knob while every shipped use is oversampled; ignitor distort/shape softCap their output, the strip deliberately does not | note | RECORD -> pipeline-coefficient-exposure / effect-scope-docs backlog; maintainer taste calls |
| W10 | SIX shipped sprudel functions are INERT end-to-end: `tremoloskew`/`tremskew`, `tremolophase`/`tremphase`, `tremoloshape`/`tremshape` (full KDoc + examples + aliases) travel wire -> `Voice.Tremolo` -> and are DROPPED (`FilterPipelineBuilder:71` builds `TremoloRenderer(rate, depth)` only; sine-only, phase 0 always). `LangTremoloSpec` tests the surface up to the wire and never past it. Also dead: `Voice.Coarse.lastCoarseValue`/`coarseCounter` (pre-renderer leftovers, zero readers) | MAJOR (surface honesty; framing-invariant, off-lens) | **FIXED (maintainer: implement all three)**: new `LfoShape.kt` (five waveforms, the OSCILLATOR vocabulary + its aliases — maintainer's call: no LFO-only names, so `rampup`/`rampdown` came OUT of the docs and the house's own falling `ramp` covers them); `TremoloRenderer` takes skew/startPhase/shape with NO defaults (a default is how W10 happened); `FilterPipelineBuilder` forwards all of it; `VoiceFactory`'s `* TWO_PI` moved to the renderer (one conversion site) and `Voice.Tremolo.currentPhase` + the dead `Voice.Coarse.lastCoarseValue`/`coarseCounter` are gone. THREE doc/engine contradictions resolved by maintainer decision: phase is in CYCLES not radians (the KDoc's `1.57` examples were 565 deg under the factory's own conversion), skew is `-1..+1` with 0 symmetric (the KDoc claimed 0.5 while the default was 0.0 — implementing it literally would have re-skewed every existing tremolo), and SAWTOOTH's duty is FLIPPED in `lfoDutyOf` so `+ skew` means "sits higher" on all five shapes (measured: without it, +0.6 gives mean level 0.65-0.80 on four shapes and 0.35 on the fifth). Bit-identical at the neutral settings by construction: the unskewed sine is evaluated on the RADIAN accumulator, never through the normalize round trip, which moves the argument by an ulp on ~12% of phases. Also fixed, all found by the review rounds: EVERY documented tremolo example was INAUDIBLE (rate and depth both default to 0, so the stage was never even built) — 40 in the skew/phase/shape families found by round 1, plus the 14 `tremolosync`/`tremolodepth` ones round 2 caught next door (a rate with no depth builds no stage; a depth at rate 0 is a static level drop, not a tremolo); the combined `tremolo(...)` addon door still said `skew (0-1)` / rate in "cycles per pattern cycle" (it is Hz), the tremolo editor tool offered a stale shape set with a triangle preview a quarter cycle out of phase, and `IgnitorDsl.Ramp`'s KDoc called it "ramp up" while the engine builds it at `polarity = -1.0`. Guards: TremoloRendererSpec 15 rows — the DrunkenSailor bit-identity row against an independently transcribed pre-W10 loop, per-shape landmarks, the skewed-SINE row (round 1: dropping the fast path's duty conjunct makes skew inert on the DEFAULT shape and survived all 11 rows), the cross-shape parity row, the duty-guard extremes, ragged block splits per shape, and a `buildFilterPipeline` seam row (round 1: transposing skew/startPhase at the call site compiled and passed everything else). OPEN follow-ups filed: the ignitor door keeps rate+depth (dual-surface parity, maintainer-scoped out of this round); the shared `SprudelWaveformEditor` bound to `tremoloshape` offers `noise` (silently sine) and lacks `ramp`; the tremolo editor commits skew but does not draw it |
| W11 | `Oversampler.reset()` has ZERO callers and its KDoc claims a cleanup/retrigger lifecycle that does not exist (D9 shape — but verified HARMLESS: every instance is per-voice, no reuse path) | note | **REWORDED** (function + class KDoc): kept for the pooled-instance future, zero callers today by design |
| W12 | Perf notes: `DriveIgnitor` runs `type.lowercase()` INSIDE generate() (the class's only render-path allocation; siblings parse at construction; the `when` has one implemented arm) — hoist; `ScratchBuffers.oversample(factor)` is a lazy per-block map lookup allocating once per (playback, factor) (16-32 KB) — warehouse-pool radar next to the shimmer ring | MINOR (perf) | **DONE**: the lowercase left the render path entirely (the param is a documented reserve, nothing stores it); the oversample scratch pool stays recorded on the warehouse radar |
| W13 | The phaseMod family's missing musical/absolute separation (pre-filed from D13): CONFIRMED but ROUTED OUT of this class — it is framing-INVARIANT (per-sample window-exact ratios, bit-identical at every alignment; no I1-I4 assertion can see it, the O6 trap). Reach precisely measured: IN = pitched sources on the SPINE of a modded subtree (every arithmetic operand forwards the mod); OUT = every PARAM slot (`noMod()`), the entire noise family (skip `applyMod`); shipped songs immune (Sakura's vibrato spine is perlin = noise-exempt). SECOND DOOR the pre-file missed: sprudel `.vibrato()` writes `ctx.phaseMod` directly from `IgniteRenderer` — wobbles absolute-freq spine oscillators inside custom ignitors with no ModApplyingIgnitor involved. And `usesMusicalFreq` is necessary but NOT sufficient (a vibrato must move `detune`d arms while arguably not a hand-rolled `x * Osc.sine(5)` — needs a per-SOURCE decision, not a per-subtree fold) | note (routing) | **FIXED 2026-08-31 (maintainer: absolute freq means absolute), both doors in one change.** An oscillator scales its phase increment by `ctx.phaseMod` whatever its frequency came from, so a fixed-pitch source on a modded spine was bent exactly like a note-pitched one — the asymmetry with detune, which multiplies the freq ARGUMENT and is therefore immune by construction (D13). Fix: `pitchedSource(freq, source)` in `buildRaw` gates every pitched arm on `cache.usesMusicalFreq(freq)` — D13's own predicate, asked of the oscillator's OWN freq slot rather than of a subtree, which is exactly the per-SOURCE question this row flagged as missing. Musical → unchanged; absolute → `ModBlockingIgnitor`, which nulls `ctx.phaseMod` around the inner generate and restores it. Blocking at the READ is what covers BOTH doors in one place: the ignitor door hands modulation down through `ModApplyingIgnitor`, but sprudel's `.vibrato()`/`.pitchEnvelope()`/`.accelerate()` write `ctx.phaseMod` once for the whole graph in `IgniteRenderer` with no wrapper involved. Cost: two field writes per generate call, nothing per sample, and the shielded oscillator then takes its own cheaper `phaseMod == null` loop. The filed claim that shipped songs are immune holds, but for a NARROWER reason than filed, and one instance came close: `DialogueWithTheStars`' nylon guitar pitch-envelopes a spine ending in `Osc.sine(180)` commented "Soundbox thump — fixed-pitch low body resonance", so that thump was starting 4.21 Hz sharp (0.4 st, ratio 1.02337 → 184.21 Hz) and settling over 40 ms; it now sits at 180 Hz as its comment always said. AUDIBLY MOOT (maintainer, confirmed at `DialogueWithTheStars.kt:122`): the song defines instruments but plays exactly one note, through a binding named `placeholder` — it is a work in progress, not a mix anyone hears. Recorded because the CODE SHAPE is the live one: a fixed-pitch body resonance summed into a pitch-modulated spine is ordinary instrument design, and the next song to do it for real would have hit the bug. Sakura IS immune, though not for the filed reason: its vibrato'd spine is sine+triangle (musical, correctly bent) plus two perlins (noise, already exempt), and every `Osc.sine(0.3)`-style LFO found in songs sits in a PARAM slot, which `noMod()` already exempted. Guards: `AbsoluteFreqPitchModSpec` 7 rows across both doors; 4/4 mutations killed. The campaign earned two of those rows: the barrier's missing-restore mutant survived until a row lined up BOTH the operand order (absolute half first, so a sibling is left to damage) AND the strip door (on the ignitor door `ctx.phaseMod` is null on entry, so the save/restore is a no-op and dropping it changes nothing). Note also that NO pre-existing test caught the behaviour change at all — nothing in the suite exercised an absolute-freq oscillator under a pitch mod |

**Class calls (provisional, pre-decision):** Class 1 = Drive, Shape, Crush, DcBlock, Oversampler
core, DcBlocker, all four strip twins (Coarse strip = oversampled path); Tremolo joins after W2.
Class 2 knobs are the per-block `readParam`s of every amount/rate/depth (all already KDoc-named —
D8 does not recur) plus the block-quantised placement of every bypass transition.
**W-batch campaign (2026-08-30):** 14 mutations — 13 killed by designated rows (both bootstrap
halves per door, the full old-code latch restore, gap-advance removal and off-by-one, both
doors' bare-wrap regressions, the widened guard, the isInfinite/NaN-guard arms, the captured-
sample nanGuard, the coerceAtLeast floor, the delegation's oversample stages) and 1 recorded
INERT-BY-CONSTRUCTION (the latch arm restored WITHOUT the 0.0 bootstrap is dead code — the 1.0
bootstrap's counter tops cycle {1.0, 0.25, 0.5, 0.75} and never touch 0.0; the realistic full
restore is killed twice over). Review round 1 (2 reviewers, exact IEEE simulations) found ONE
MAJOR each, converging: the W1 guard probes started at 0.0 — indistinguishable from the
uninitialized lastValue, so the bootstrap-value mutant passed everything (probes made non-zero);
and the Sakura `.coarse(3)` whole-note grid rotation (recorded at W1, awaiting the ear).

**Harness graduation queue (largely DONE by the batch):** `coarse` power-of-two onset sweep (pins W1 TODAY, pre-fix), `shape`
oversample=4 (strongest bit-identity candidate), tremolo modulated-depth (after W2), coarse
modulated-amount (after W3), crush audio-rate amount.

---

## The MASTER round (2026-08-31) — and the general fix W7 was pointing at

Opened as the last of the maintainer's three queued picks. Two analyzers (framing/lifecycle +
numerics/egress), then the maintainer redirected the fix: *"what would be the most general
solution? the fix in the master seems to be a patch not a full solution"*. That redirect is the
whole point of this round, and it was right.

**M2 / W7 — the CRITICAL, and why the master was the wrong place to fix it.** One non-finite
sample from any voice latched the master DC blockers; the limiter's own `isFinite` guards then
turned every NaN into `0.0`; output was normal for exactly `delayFrames` (240 samples, 5 ms)
while the lookahead ring drained real audio and then went to **digital silence, permanently, for
every playback** — one `MasterStage` is shared across the summed mix, and `MasterStage.reset()`
is called once per backend lifetime behind `WarmupRunner`'s `finished` gate. Recovery needed a
page reload. No click, no error; `Feedback.Diagnostics` kept reporting healthy headroom.
Three corrections to W7's own wording, all confirmed by both analyzers independently: the blast
radius is the **backend session**, not the playback; the trigger is **`+Inf` as well as NaN** and
ONE sample suffices (`x - Inf + a*Inf` = `-Inf + Inf` manufactures the NaN on the *next* sample);
and the symptom is **silence, not a click**. Also corrected, my own claim on the way in: the clip
ladder does map NaN to `Short.MIN_VALUE`, but it is unreachable there — `Compressor.kt:322`
guards the ring write, and its comment cites that very mapping as the reason.

**THE FIX (maintainer: "widen the helper, then close the exceptions").** The engine already had
the right hook in the right places: `flushDenormal` was called at 58 IIR carry sites across 8
files by house rule (`/code-style` #8). It only rejected denormals. Widened to reject
non-finite as well and **renamed `flushState`** (maintainer's call: one concept, one word — the
old name described one of its two jobs), so every IIR in the engine is now structurally unable
to latch. Recovery 1-2 samples (`DcBlocker` 2, `SVF` 1). **Bit-identical for finite audio** —
verified over 20k random samples through both topologies; only the rejected branch changed.
Shape: `abs` + two compares + a select, no branch on the data. `a <= Double.MAX_VALUE` rejects
Inf AND NaN in one compare where `isFinite()` is two tests and a JS call; `abs` folds the sign,
which is the ONE genuinely unpredictable bit in an audio loop and must never be branched on.

**Cost, measured, because the maintainer asked.** Interleaved A/B on `runSongBenchmark --args=ladders`,
alternating the helper body run-by-run (the first attempt ran all-baseline-then-all-after and
reported +2-3%, which was machine drift riding along with the change — a methodology error, not
a result): **+0.36% median, +0.38% minimum** on the filter-heavy GTR1 row, matching the
one-extra-compare prediction. The harness cannot RESOLVE 0.4% (n=4, ~35% spread), so the
defensible claim is "no measurable regression, direction and magnitude match the analysis".
The cheaper per-block-epilogue design (2 tests per block instead of per sample, at the cost of a
whole block of NaN instead of 1-2 samples) was therefore not needed.

**The exceptions the helper cannot reach, each closed on its own terms:**
- **Reverb** keeps `+ ANTI_DENORMAL` (converting its 24 stores/sample to the flush was measured
  at ~+11% and reverted 2026-05-19). Guarded at its two INPUT taps instead — equivalent and 12x
  cheaper, because the comb/allpass network is a stable linear system (|feedback| < 1 via
  `normalizeRoomSize`, damping in [0,1], coefficients guarded at configure), so a finite input
  can never drive the state non-finite.
- **`Compressor.envelopeStep`** (M3, MAJOR): the classic path had NO guard where its lookahead
  twin does, so one `+Inf` latched `envelopeDb` to NaN and the limiter returned exactly `1.0`
  forever — a brickwall silently degraded to a bit-exact pass-through. A NaN *sample* never did
  this (`NaN > SILENCE_LIN` is false); only `±Inf`.
- **`MasterBus.blendInto`** (M4, MAJOR): `Inf * 0.0` = NaN at the fade endpoints, injected from a
  chain contributing *nothing* yet. Both taps sterilised.
- **The delay ring** — CLOSED 2026-08-31 with a `nanGuard()` on the ring STORE. `softCap`
  already sterilises ±Inf (it saturates to ±1) but `softCap(NaN)` is NaN, and the ring
  RECIRCULATES, so one NaN never scrolled out: the orbit's delay was dead for the rest of its
  life. `flushState` could not reach it — the ring is FIR-shaped state, not an IIR carry.
  MEASURED rather than assumed, because a per-sample `isFinite` here was removed at +33% JVM /
  +30% JS (2026-05-22): the benchmark ladder had NO delay coverage at all, so a delay rung was
  added to LEAD (feedback 0.35, so the recirculating path is what is timed) and the guard priced
  by interleaved A/B. Result: **indistinguishable from zero** (the guarded runs came out
  nominally FASTER, which is noise — one compare cannot speed code up — but it bounds the cost
  far below the isFinite history). The lesson generalises: that +33% was the cost of a
  NON-INLINED stdlib call on a per-sample path, not of the test. `nanGuard` is one inline
  self-compare. Guards: `DelayLineSpec` row asserting the delay still ECHOES after a NaN (not
  merely that it is finite — a guard that zeroed the ring would pass a finiteness-only oracle);
  2/2 mutations killed, including placing the guard on the OUTPUT instead of the store, which
  looks equivalent and leaves the ring poisoned.

**M1 — MAJOR, live on five shipped songs, and nothing to do with NaN.** The FIRST master
application crossfaded the song's opening 60 ms up from **unmastered**: at playback start
`previous == null` and `current == unity`, so `MasterBus:216` took the ordinary `beginFade` path
and ramped `t` 0→1 over 2880 frames, while the master event and the first note promote in the
same `promoteScheduled` call. DerSchmetterling (`gain(2.6)`) opened 8.30 dB down and swelled;
Tetris / StrangerThings / ATruthWorthLyingFor (1.5) 3.52 dB; IrishLamentTechno (1.1) 0.83 dB.
**Already observed and worked around, never decided** — `MasterBusTest:123` read "the crossfade
means the first ~60 ms ramps in, hence a tolerance well below the nominal 2.0" and asserted
`* 1.5`. Fixed (maintainer: adopt at full weight when there is nothing to fade from); that
assertion is now the nominal `* 1.95` and is the guard.

The discriminator cost a round: `hasProcessed` set inside `MasterBus.process` was WRONG, and
`MasterBusTest` caught it — `PlaybackEngine.renderInto` takes a fast path that skips
`MasterBus.process` entirely while the bus is inactive, which is exactly the unmastered case, so
a MID-SONG first master (which genuinely wants the fade) would have been hard-cut over live
audio. Moved to `markRendered()`, called by the engine from BOTH exits of `renderInto`, after the
block's audio.

**Guards + campaign:** `DspUtilSpec` (new — the helper is tested directly because dropping its
denormal half is a CPU property with NO audible signature and survives every filter oracle in the
suite), `MasterBusAdoptionSpec` (new — the bus-level before/after-render contract),
`CompressorSpec` M3 row, `KatalystReverbEffectSpec` re-pointed, `MasterBusTest` M1 row.
**5 of 6 mutations killed**, restores byte-exact. RECORDED UNGUARDED, honestly: moving
`markRendered()` to before `scheduler.process` survives the end-to-end spec and I could not build
an oracle for it, so the placement was made STRUCTURAL instead (both exits, after the audio, with
the contract in its KDoc) rather than left as a load-bearing position in the middle of a method.
`blendInto`'s sterilise has no dedicated row either: end to end it is masked by the very
downstream guards it exists to protect — the same "UNGUARDABLE by construction" category this
ledger already uses for the D13 wrap-site alias.

**A shipped test pinned the old behaviour and had to be re-decided, not just updated:**
`EqCoreSpec > "BELL boost propagates Inf/NaN bare (no output clamp — legacy parity)"` asserted
"block 2: state is NaN, every sample must come out non-finite". The property it actually defends
is *no output clamp* (a `safeOut` scrub on the bell output must redden it); that half is intact
and sample 40 still leaves non-finite. The latch half was the decided change, and the row now
also pins that sample 41 is already clean — one-sample recovery, stated directly.
`KatalystReverbEffectSpec`'s Inf-poisoning row likewise had its ROUTE closed by the reverb input
guard; it was re-pointed at the route that stays open (a sustained `MAX_VALUE` send overflows a
comb cell after one delay revolution, ~1116 samples), which also proves the drain-heal is still
load-bearing rather than dead code. No non-finite guard prevents large-finite overflow.

**Master findings NOT addressed this round** (all recorded, none blocking): the authored
`limiter()`'s lookahead produces a **level dip, not the comb its KDoc promises** (-15.6 dB at the
50 ms max); authored-limiter latency is invisible to `MasterStage.latencyFrames`, so offline
renders TRUNCATE the tail, FE highlighting fires early, and a swap permanently splices the
timeline; `TAIL_CHECK_INTERVAL_BLOCKS` is an **unnamed Class 2 block-rate knob** — the twin of
D11, missed when D11 was written; the clip truncates rather than rounds (-90.3 vs -96.3 dBFS,
2-LSB dead-band at zero); `-32768/32767` overshoots the nominal range; nothing clears master
state on stop/start (safe only because playback ids are a monotonic counter — but
`createRealtimePlayback` reuses ids by name); `masterLatencyMs` and
`KlangAudioRenderer.resetPostChain()` have zero callers; the AUTHORED limiter has no DC blocker
ahead of it while the house one's KDoc calls that ordering "required rather than tidy"; ~2 String
allocations per cycle on the audio thread; a NaN-poisoned bus reads as SILENT to `hasActiveTail()`.
Off-lens but worth knowing: **`VoiceData.master` is inert on the entire realtime door**, so the
MIDI playground can never be mastered.
