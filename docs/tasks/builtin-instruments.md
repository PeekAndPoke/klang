# Built-in instruments, and the end of the Pipeline DSL (phase 3)

Phase 3 of `../plans/signal-flow-redesign.md` (its section 5 states the goal and the rules). This
file is the task record: what the spike of 2026-09-20 found, what the maintainer has to decide, and
the step list. Written for a reader who has not read the code.

## 1. The goal in one paragraph

`sound("saw")` is not an oscillator, it is a subtractive synth voice with a saw in it. Phase 3 makes
that explicit: the built-in sounds become INSTRUMENTS written in the Ignitor DSL, registered once,
whose stages are today's voice pipeline in today's order, every stage gated on its slot. `.classic()`
is a plain function over the node type, shipped on both doors. A pattern fills slots, never adds
structure. `PipelineDsl`, the filter pipeline builder, `Cmd.RegisterPipeline`, `PipelineRegistry` and
the `pedal` preset retire; every voice door becomes an `oscp` alias; `VoiceData` is cut to the plan's
section 4.

## 2. The spike's headline (2026-09-20, all numbers MEASURED on the JVM)

- **The gate pays for itself several times over.** With it, a plain `sound("saw")` is 38 % CHEAPER
  than today (720 against 1161 ns per block per voice, 8 sustained voices through the real
  renderer); without it, 2.5 times more expensive (2913). The gate is worth about 2193 ns per block
  per voice, three times a whole voice today. There is no version of phase 3 that ships without it.
  The win is not the gate itself: `AdsrIgnitor` is cheaper than the strip's `EnvelopeRenderer`
  (115 against 497 ns per block per voice), mostly because the strip runs its de-click one-pole
  unconditionally. **If `classic()` has to switch that de-click on for identity, the win shrinks by
  an amount the spike did not measure.**
- **Today's number phase 3 must not regress: 9290 ns per block for 8 sustained saws.**
- **Four of seven stages are already bit-identical** between the strip and the tree: all four filters
  (the analog saturation branch and the `passes` cascade included), coarse, tremolo at neutral, and
  the ADSR at integral frame counts.
- **Three are not**, and each is a taste decision, not a bug: crush (a `floor` quantizer against a
  `round` one, up to 0.125 apart at amount 4), distort (`ShapeIgnitor` applies `softCap`, the strip
  deliberately does not, up to 0.121 apart), and the filter ENVELOPE (a 32-sample coefficient ramp
  then hold, against whole-block interpolation).
- **Seven capabilities of the strip have no expression in the Ignitor DSL at all** (section 4).
- All of it is JVM. The shipping backend is Kotlin/JS in an AudioWorklet, and the codebase's own
  notes say the two diverge on exactly this kind of loop. Re-run the voice probe as `IgnitorBenchmark`
  rows on JS before the step that lands `classic()`.

## 3. Decisions the maintainer owes before the ear-checkpoint steps

The identity-provable steps (1, 2, 3, 5 below) do not wait for these. Steps 4, 6, 7 and 10 do.

- **D1, the crush.** The strip's quantizer floors (an asymmetric quantizer with a DC bias, and its
  KDoc claims the asymmetry IS the classic audible character); the ignitor's rounds (symmetric, no
  DC bias, its KDoc defends that too). Up to 0.125 apart at amount 4. Port `floor` into the ignitor
  (identity for the songs, changes every authored `.crush()` in an ignitor), keep `round` (changes
  the songs that crush), or make it a shape knob.
- **D2, the distort.** `ShapeIgnitor` applies `softCap` per sample, `DistortionRenderer` does not,
  because the strip has its own downstream bounding stages. Drop the cap on the classic tail
  (identity, but a heavy-drive branch can then dominate a mix, which is what the cap exists for), or
  keep it and accept the change on every distorted voice.
- **D3, the filter envelope.** The strip computes it once per block and lets `setCutoff` ramp the
  coefficients over 32 samples and then hold; the ignitor computes it at block start and end and
  interpolates across the whole block. Same law, different sampling. Port the strip's shape for
  identity, or take the interpolated one (arguably better: the ignitor's KDoc says the per-block
  recompute left a 187 Hz stair-step) with an ear checkpoint on Der Schmetterling and Stranger
  Things.
- **D4, the `pedal` pipeline.** `DialogueWithTheStars` calls `.pipeline("pedal")`, which puts the VCA
  FIRST. It retires with `PipelineDsl` and has no `classic()` spelling. Ship a second named tail
  (a second word for one concept, which the rules register argues against), rewrite the song, or let
  it change.
- **D5, the frozen pieces.** `FrozenPieces` is captured verbatim and immutable except for door
  renames. Appending `.classic()` is not a rename, and without it those pieces lose their outer
  envelope. The maintainer's word is needed.

Minor, decidable inside their step: the ADSR's Int-against-Double frame counts and its sustain clamp
(up to 7.1e-3 on the gain, about 0.06 dB, on a fractional frame count); whether `classic()`'s
de-click may share the name `declickSeconds` with a slot whose default differs.

## 4. What is missing from the Ignitor DSL (the spike's map)

| Missing | Kind | Who needs it |
|---|---|---|
| The filter ENVELOPE (attack, decay, sustain, release, depth) on the four filter nodes | 5 knobs per filter | every song with `lpf(env = ...)` |
| `AnalogDrift` per filter, and the per-voice cutoff tolerance | node feature, per-voice rng draws | every song with `analog > 0` |
| `oversample` on crush and coarse | 1 knob each | Tetris, the frozen corpus |
| `skew`, `phase`, `shape` on tremolo | 2 knobs + 1 index slot | the tremolo door |
| `shape` and `oversample` on distort | 1 index slot + 1 knob | Tetris, the frozen corpus |
| `adsrOn`/`adsrOff` as a gate slot, and the three curves as slots | 1 gate + 3 index slots | every `.adsrOff()` instrument |
| A sample node kind (or a hand-built head with a `classic()` tail) | node kind | `sound("bd")` and Der Schmetterling's drums |

A string knob becomes a numeric INDEX slot, the way `body.material` already does.

**The order `classic()` must have** is today's strip order with the canonical filter sub-order from
`SprudelVoiceData.toVoiceData`: crush, coarse, distort, highpass, bandpass, notch, lowpass, tremolo,
adsr. The plan's sketch in section 5 lists the lowpass first, which is wrong and would change every
song with both a highpass and a lowpass at `analog > 0` (at analog 0 the filters commute).

## 5. Three things the plan says that the spike corrected

- **The pitch fields STAY on the wire.** The plan's section 4 lists a minimum with no pitch fields
  while section 5 says the pitch pipeline is untouched; both cannot hold. The spike verified the
  separation is real (`buildPitchPipeline` only ever writes `BlockContext.freqModBuffer`, the
  ignitor reads it as `phaseMod`, and the tree's own pitch mods COMPOSE with it on every Der
  Schmetterling voice today). Moving the pitch pipeline into the tree is its own later item.
- **The build cache needs no key change.** `IgnitorBuildCache` is created fresh per build, and a
  build is per note-on, so the gate's decision is constant for the cache's lifetime. What the plan
  feared would be a CROSS-VOICE cache, which does not exist. Drop it from the scope and add one spec
  pinning the invariant, so a future cross-voice cache cannot reintroduce the hazard silently.
- **Two off values in the plan are wrong.** Crush's off value is `< 1.0`, not 0 (the renderer itself
  bypasses below 2 levels). The ENVELOPE is inverted: today the VCA runs on EVERY voice with
  `AdsrDef.defaultSynth` when the pattern sets nothing, so `classic()`'s ADSR is built BY DEFAULT and
  gated off by an explicit `adsrOff` slot. Also add `mul(pregain)` at exactly 1.0 and `onepole` at or
  below 0 to the table.

## 6. Two things the factory knows today that only the build can know tomorrow

- **The cull rule.** `VoiceFactory` sets `VOICE_CULL_NEVER` when the tremolo depth is above 0,
  because a square tremolo at full depth is exact silence for half a cycle and the silence culler
  would kill the voice at its first off-half. Once the tremolo is a tree node the factory cannot see
  it: the build must report "this tree gates its own output", the shape `releaseTailSec` already has.
- **The teardown fade.** `EnvelopeRenderer.renderGate` is the `adsrOff()` path: a unity gate with a
  linear fade to exact zero over the last frames, which exists because an instrument's own envelope
  sits BEFORE its amp stages. Phase 3 removes the only stage that guarantees an amplitude ramp at the
  voice's end, and Der Schmetterling's lead, three guitars and Orchestertrommel all rely on it. The
  build reports whether it built the classic ADSR node, and the voice applies the teardown fade when
  it did not. Applying it unconditionally would multiply the last 5 ms of every built-in and break
  identity.

## 7. The open points of the plan's section 11, answered

- **The voice's lifetime** is largely already built: `BuiltIgnitor.releaseTailSec` exists and the
  factory already takes the larger of it and the resolved release. `null` means "no static answer"
  and the caller treats it as "contributes nothing", which is today's behaviour. What changes in
  phase 3 is the safety net, not the number: see the teardown fade above.
- **The placed `pregain` at unity costs 115 ns per block at node level and about 90 per voice**, 12.5
  percent of the phase-3 target. FOLD IT AWAY at build when the resolved value is exactly 1.0, and do
  it through the gate rather than as a special case in `mul`. The decisive argument is identity, not
  the cost: today's built-ins carry no `pregain`, so keeping a unity multiply is the bit change, not
  removing it. What it drops is a `safeOut` scrub that only fires on a sample that is already NaN or
  above 1e15, which a bare oscillator cannot produce and every downstream stage still guards.
- **`analog`'s readers disagree, and it is worse than recorded.** A NaN `analog` fails the
  `analog <= 0.0` test in `perVoiceCutoffOffsetMul`, so the multiplier is NaN, the cutoff is NaN, and
  `bilinearK`'s guard substitutes 1 kHz: a NaN `analog` silently retunes every filter on the voice to
  1 kHz. `AnalogDrift` and the saturation branch are NaN-safe by accident and not Infinity-safe. The
  oscillator's slot is clean. Close it in step 1 with one `takeIf { it.isFinite() }` at the one place
  the factory reads the bag, and the same for `oscParams["onepole"]`.

## 8. The migration risk, and it is bigger than the plan states

Not only the doors. **Today the strip's VCA runs on EVERY voice, including every authored ignitor,
with `AdsrDef.defaultSynth` when the pattern set nothing.** So every authored instrument in every
song is double-enveloped today: its own in-tree envelope and then the strip VCA on top. Remove the
strip and the outer envelope vanishes whether or not the pattern ever touched a door. Appending
`.classic()` restores it exactly, because its ADSR defaults are `defaultSynth` and the two envelopes
are bit-equal at those values (measured).

**Needs `.classic()` appended** (an authored instrument, with or without doors on it): A Truth Worth
Lying For (its guitar, plus `analog = 10`), Der Schmetterling (marimba, the three guitars, bass,
granCassa, and its SAMPLE drums), Sakura (kick, sub, pad, koto, shaku, rim, brush), Sandsturm (all
eight), Irish Lament (fingerpick, contrabass, blockfloete), Greensleeves (lute, bass), Dialogue With
The Stars (the placeholder guitar), and the frozen Der Schmetterling 2026-09-16.

**Safe, built-in sounds and samples only:** Drunken Sailor, Final Fantasy 7 Prelude, Irish Lament
Techno, Smalltown Boy, Sound Of The Sea, Stranger Things, Tetris, Tetris Remix, and both frozen
songs. Safe CONDITIONAL on the built-ins being byte-identical, which is what D1 to D3 decide: those
songs exercise every one of the three red stages between them.

**The one that `.classic()` cannot fix: `analog > 0` plus a pattern filter.** The per-voice cutoff
tolerance and the `AnalogDrift` lane are drawn from the voice's rng BEFORE the exciter build, so
moving the filters into the tree shifts the rng DRAW ORDER, which changes every noise source and
every supersaw jitter on the same voice. At `analog == 0` no draw is consumed and identity holds. At
`analog > 0` it does not, which affects A Truth Worth Lying For, Stranger Things, Sakura, Tetris,
Final Fantasy 7 Prelude, Irish Lament Techno and both frozen songs.

## 9. The step list

One review loop and one commit each. Steps 1, 2, 3 and 5 are provably identity-preserving and run
unattended; steps 4, 6, 7 and 10 each need a listening checkpoint.

| # | Step | Identity | The risk |
|---|---|---|---|
| 1 | The bag guard: `takeIf { isFinite() }` on `analog` and `onepole` | provable, no song writes a non-finite one | none; the line deletes itself later |
| 2 | The gate alone (`controlRateValueOrNull` in `buildRaw`), with the off-value table as ONE list | today's built-ins have no slotted stages, so nothing is gated; a spec compares gated-off against inner in raw bits | a knob subtree that draws rng would shift the stream: restrict the query to `Param` and `Constant` leaves. Add the cross-voice-cache invariant spec |
| 3 | The missing knobs of section 4, every default preserving today's tree bit for bit | the spike's five probes become the specs; door parity per knob | the biggest step by volume; consider 3a filters, 3b waveshapers, 3c envelope. The filter build's rng draw order must reproduce the factory's exactly |
| 4 | D1 and D2 landed | by ear | the checkpoint is the gate |
| 5 | `classic()` on both doors, in the order of section 4 | a one-voice render per door, each slot written in turn | the order: write it once, in one place |
| 6 | The built-ins re-registered, the strip off for them | THE step: minimal renders per built-in per door, plus the whole-corpus render | the teardown fade and the cull rule must land here or the corpus clicks and drops tremolo voices. Re-run the benchmark against 9290 ns |
| 7 | The sample instrument | needs a JS or in-memory PCM harness: the jvm renderer has no sample bank | its own safety net |
| 8 | The doors become `oscp` aliases (about 50 to 60 functions) | door-parity specs, the wire golden regenerated | mechanical but wide; one door group at a time |
| 9 | `VoiceData` cut, `PipelineDsl` retired | compile-time, the golden regenerated | irreversible: only after 6 and 7 are ear-confirmed |
| 10 | The songs migrated with `.classic()` | per-song render against HEAD | D5 |
| 11 | The editor's unknown-slot diagnostic | UI, no audio | none |

## 10. What the spike could not settle

Whether `classic()` can be byte-identical at all (D1 to D3 decide it); which side moves on the ADSR
frame counts; the JS numbers; the de-click name collision; the `pedal` tail; how the per-voice filter
tolerance and the `AnalogDrift` lane should be expressed on a filter node (per-voice rng draws that
no knob can carry, and the draw order has to match); and whether the filter-envelope difference is
audible (read from the code, not probed; the probe is a small addition to the spike's filter harness
and belongs first in step 3).

The spike's five probes are archived in the session scratchpad at `phase3-spike/`; each is a Kotest
file that drops into `audio_be/src/jvmTest/kotlin/ignitor/`.
