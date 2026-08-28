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
| `oldestAllowedSec` = 5 blocks | late-voice drop window |
| MasterBus crossfade granularity | crossfade rate |
| every `readParam` / `blockStartValue` | control-rate modulation step rate |

So the goal is **A, not B**:

- **A (the target).** Structure is correct at any block size: onsets land on the right sample, state
  transitions land on the right sample, nothing renders silent, no step is introduced, no state is
  corrupted.
- **B (explicitly NOT the target).** Bit-identical output at any block size. That would mean
  re-deriving every row of the table above from `sampleRate`, and re-tuning analog drift by ear.
  Rejected: the tone dependence is deliberate.

**Accepted quantisation, by design:** a LATE voice floors to the block start
(`maxOf(startFrame, nowFrame)`). A voice cannot start in the past, and `nowFrame` is the floor. This
is the one place block-quantised onset is correct.

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
- **`IgnitorFilters` already lerps** its envelope between `sampleOffsetWithinBlock = 0` and `= length`
  (`:141-147`), so it degrades gracefully rather than stepping. Good precedent for what a Class 2 node
  should do when it cannot be sample-exact.

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

## Order of work — one thing at a time

**P0. Harness + the four invariants**, run over a hand-picked critical few: `Adsr`, one oscillator,
one noise source, one delay-line node. Proves the harness discriminates before scaling it. Expect it
to find something.

**P1. Triage the three siblings still on the 2026-08-07 deferred list** (two are audible), so this
sweep starts from a known board rather than rediscovering them a third time.

**P2. Exhaustive sweep** over all 78 node types with block-constant parameters. Triage into
fix / block-rate-by-design / maintainer decision. Do not fix during triage; produce the list first.

**P3. Fix, one node per commit**, each with the failing case turned into a permanent guard and
mutation-checked. Gradual is the point.

**P4. The strip renderers**, which have their own copy of the same arithmetic: `EnvelopeRenderer:86`,
`PitchEnvelopeRenderer:30` (both already use `blockStart + offset`), `FilterModRenderer`, `FmRenderer`,
`SendRenderer`, and `Voice.render` itself.

**P5. The sample path** end to end, given instance 2.

## Settled (2026-08-28), recorded so they are not re-opened

- **Block size must be treated as non-constant.** It is 128 in the browser only because the render
  quantum is handed to the worklet; other platforms may choose it. Nothing may be implemented against
  128. The tone consequences of changing it are understood and accepted — target A, not B.
- **Sub-sample onset accuracy is NOT required.** Flooring the start position to a SAMPLE is fine
  (`VoiceFactory:88` already does exactly that); flooring it to a BLOCK is the bug. The one accepted
  exception is a late voice, which floors to the block start because it cannot start in the past.

## Related

- `docs/tasks/ignitor-envelope-ownership.md` — where instance 1 surfaced.
- `IgniteOnsetOffsetSpec` — the guard for instance 1, and the template for the harness.
- `VcaOffTeardownSpec` — the teardown-side equivalent, already sweeping fractional and short spans.
