# Block-framing invariance — every voice must sound the same wherever the blocks fall

> **Status (2026-08-28): PLAN, not started.** Opened after the second instance of the same bug class
> (`IgniteRenderer.kt:36`, found 2026-08-27 via `.adsrOff()`; the maintainer recalls an earlier one in
> the sample path). This is an audit workstream, deliberately incremental: build the harness, run it,
> then fix one node at a time.

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

### Confirmed instance 2 — the sample path

The maintainer recalls an equivalent bug already fixed in the sample renderer. **I could not locate it
in `git log`** — whoever picks this up should find it first and record the commit here, because its
shape tells us which other paths to suspect and whether a guard was left behind.

## The invariants

These are mechanical and apply to every node without case-by-case reasoning. That is the point: the
catalogue is 78 DSL node types over 33 runtime classes, and hand-auditing them is exactly how the
first two got missed.

**I1 — Onset alignment.** For a voice starting at frame `S`, the samples relative to its own start
must be identical for every `S mod blockFrames`. Sweep `S` over `0..blockFrames-1`.

**I2 — Block-split invariance.** The same voice rendered with different block sizes (128, 64, 37,
ragged) must be **bit-identical**. This is the strongest of the three and subsumes most of I1.

**I3 — Teardown alignment.** Same as I1 for the end frame: sweep `endFrame mod blockFrames`.

**I4 — Transition placement.** For any node with a gate-relative state change, sweep the gate end
across every offset in a block and assert the transition lands at the same sample relative to the
note. This is the one that catches failure mode (b), and I1/I2 catch it only by luck — a transition
that snaps to a block boundary is invisible if the test never places one mid-block.

## The exemption that makes this tractable

**Control-rate parameter reads are block-rate BY DESIGN.** `Ignitors.readParam` /
`Ignitor.blockStartValue` sample a parameter once per block and hold it. So under I2, a node whose
parameter is a *varying signal* legitimately produces different output at a different block size.
That is a feature (see the `controlRateValueOrNull` contract), not a bug.

Without this distinction the sweep drowns in false positives. So:

- **The exhaustive sweep uses BLOCK-CONSTANT parameters.** Every node must pass I1–I4 there, with no
  exceptions. A failure is a real bug.
- **Varying parameters are a separate, smaller pass** with a weaker property: the difference between
  framings must be bounded by the parameter's own per-block step, not zero. Document the block-rate
  behaviour per node rather than "fixing" it.
- Anything that turns out to be genuinely block-quantised and *audible* is a design question for the
  maintainer, not something to silently smooth.

Second-order effects to expect and classify rather than chase:

- **`MemoizingIgnitor`** keys on `(voiceElapsedFrames, offset, length, freqHz)`, so a block split
  changes the key and forces recompute. Correct, and invisible if the node is otherwise sound.
- **Stateful RNG** (whitenoise, dust, crackle, supersaw jitter): draw order must depend only on the
  number of samples produced. I2 catches a node that draws `buffer.size` instead of `length` — which
  is exactly the kind of slip worth finding.
- **Delay-line nodes** (shimmer, phaser, pluck): write/read positions must advance by `length`.

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

**P1. Locate the earlier sample-path fix** and record it above.

**P2. Exhaustive sweep** over all 78 node types with block-constant parameters. Triage into
fix / block-rate-by-design / maintainer decision. Do not fix during triage; produce the list first.

**P3. Fix, one node per commit**, each with the failing case turned into a permanent guard and
mutation-checked. Gradual is the point.

**P4. The strip renderers**, which have their own copy of the same arithmetic: `EnvelopeRenderer:86`,
`PitchEnvelopeRenderer:30` (both already use `blockStart + offset`), `FilterModRenderer`, `FmRenderer`,
`SendRenderer`, and `Voice.render` itself.

**P5. The sample path** end to end, given instance 2.

## Two things to decide before P2

- **Is `blockFrames` allowed to vary at runtime?** Today it is pinned at 128 everywhere (see the
  block-size parity note in the project memory). If it can never vary in production, I2 is a
  *design* property we test rather than a shipping risk, and I1/I3/I4 carry the real weight. Worth
  settling, because it changes how loudly a pure-I2 failure should be treated.
- **How much onset jitter is acceptable at all?** `VoiceFactory:88` floors `startFrame`, so onsets are
  already quantised to whole samples. If sub-sample onset accuracy is ever wanted, several exactness
  arguments in the envelope code (`renderGate`'s endpoint, `releaseProgressDenom`) rest on integral
  frames and would need revisiting together.

## Related

- `docs/tasks/ignitor-envelope-ownership.md` — where instance 1 surfaced.
- `IgniteOnsetOffsetSpec` — the guard for instance 1, and the template for the harness.
- `VcaOffTeardownSpec` — the teardown-side equivalent, already sweeping fractional and short spans.
