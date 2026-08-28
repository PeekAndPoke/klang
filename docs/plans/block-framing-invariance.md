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
| E1 | FM depth envelope held flat per block, no interpolation (`PitchModFactories:262-267`); LIVE on the registered `sgbell` preset: first block of every FM note has zero FM, peak depth never produced, per-hit head length 1..blockFrames | CRITICAL | **FIX** (per-sample evaluation; the env is analytic and sample-addressable) |
| E2 | `fmModIgnitor` early returns skip `modulator.generate`, freezing its phase for whole blocks; `VibratoModIgnitor` same shape | MINOR | **FIX**, fold into the E1 commit |
| E3 | SVF cutoff-env chord straightens knees; segment shorter than a block erased; peak height alignment- and block-size-dependent (`IgnitorFilters:140-171`) | MAJOR (latent: no production door passes an env here; benchmarks/tests only) | **PIN with a spec, decide before any door wires an env**. Fix shape if taken: split the chord at breakpoints in `(E, E+length)` |
| E4 | `AdsrIgnitor` takes `releaseStartLevel` from history; first rendered sample past gate end renders the note silent (`IgnitorEnvelopes:104-108`) + identical latent twin in strip `EnvelopeRenderer:95` | MAJOR | **CLOSED BY PROBLEM B** (unreachable once no-late-voices lands); no DSP edit |
| E5 | `blockStartValue` reads stale scratch when `ctx.length == 0` (`Ignitor.kt:89-91`); reachable via small `legato` | MINOR | **FIX small** (guard the fallback) |
| E6 | Five dead block-start-only accessors on `IgniteContext` (`voiceProgress`, `isInRelease`, `releaseProgress`, ...), zero callers, shaped exactly like instance 1 | MINOR | **DELETE** (compiler-enforced) |
| E7 | `accelerateModIgnitor`: per-block-anchored multiplicative recurrence differs by 1-2 ulp between block sizes | note | **HARNESS RULE**: recurrence-based Class 1 nodes get a relative tolerance (~1e-12) + an endpoint pin, never raw bit-identity |
| E8 | `MemoizingIgnitor` re-runs a stateful shared inner when two consumers use different `freqHz` in one block (fm, detune); double-advance distance = `length` | MINOR | **RECORD**, assess in the P2 sweep (exposure in this class: only opt-in `declickSeconds`) |
| E9 | Harness lesson from E1/E3: an onset-only sweep sees NONE of this | rule | **P0 must sweep `gateEndFrame mod blockFrames` and an interior breakpoint (`attackFrames mod blockFrames`) from the first commit** |

## Order of work — one thing at a time

Two tracks, independent by construction (that is the point of the A/B split). Track A never waits
for Track B.

**Track A (DSP, under the assumed contract):**

**P0. Harness + the four invariants** — **BUILT 2026-08-28: `BlockFramingInvarianceSpec`.**
Two drivers (the real `VoiceFactory` -> `Voice.render` framing, and a raw `IgniteContext` loop for
ragged sequences and runtime-only chains); non-round durations so gate end and breakpoints land
mid-block everywhere; sweeps onset {1,37,76,127}, sizes {64,37} and a ragged sequence.
Results: `Adsr`, `Sine`, `WhiteNoise`, `Pluck` are **bit-identical** across all of it (maxDiff
exactly 0.0 — the Class 1 claim holds with no tolerance at all for these four). Acceptance met:
E1 and E3 both reproduce RED under their correct assertions; they are committed as PINNED defect
tripwires that go red the moment the defect is fixed, forcing the flip to the correct form (written
above each pin). Mutation-checked: re-introducing instance 1 (the `IgniteRenderer` offset bug) turns
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
