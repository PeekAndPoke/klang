# Layer 7a: the Katalyst chain, its knobs and its hosts

Audit of 2026-11-10 (KLANG-00420), read-only. Paths are relative to the repo root; `be/` stands for
`audio_be/src/commonMain/kotlin/` and `br/` for `audio_bridge/src/commonMain/kotlin/`.

## What I read

In full (code, with the KDoc where a claim depended on it):

- `be/KnobGlide.kt`, `be/Crossfade.kt`, `be/TailRelease.kt`, `be/ChainSwap.kt`, `be/ChainHost.kt`,
  `be/utils/linear_crossfade.kt`, `be/utils/same_knob.kt`, `be/utils/phase_wrap.kt` (the two wraps),
  `be/utils/numerical_safety.kt` (`safeDiv`, `safeOut`), `be/ignitor/_arithmetic_laws.kt` (the laws the stages share).
- `be/katalyst/`: `KatalystChain`, `KatalystChainBuilder`, `KatalystContext`, `KatalystEffect`, `KatalystSlots`
  (`KatalystKnob`), `KatalystSlotWriters`, `KatalystKnobProgram`, `KatalystRegistry`, `KatalystArithmeticEffect`,
  `KatalystGainEffect`, `KatalystParallelEffect`, `KatalystRegionEffect`, `KatalystBusLadder`, `CompressorKnobMove`,
  and the lifecycle half (states, `configure`, glides, rents) of `KatalystDelayEffect`, `KatalystReverbEffect` and
  `KatalystCompressorEffect`.
- `be/warehouse/`: `SizedBuffers`, `ReverbUnits`, `ResourceWarehouse`, `CylinderUnits`.
- `be/effects/TailCeiling.kt`; the host side of `be/cylinders/Cylinder.kt` and `be/master/MasterBus.kt`.
- `br/KatalystDsl.kt` (the stage types and `classic`), `br/KatalystDslSlots.kt`, `br/Kat.kt`, `br/BandsBuilders.kt`,
  and `blend`, `parallel`, `min`, `max` in `br/KatalystBuilders.kt`.
- `audio/CLAUDE.md`, `.claude/skills/review-loop/audio-constraints.md`, `audio/ref/katalyst.md`,
  `audio/ref/knob-glide.md`, the leads in KLANG-00420, and the layer's rows of `stage-cost.txt`, `clicks.txt`,
  `hot-path-lint.txt`.

Skimmed only: `be/effects/Compressor.kt` (the knob setters, `process`, `processGliding`, `processLookahead`,
`lookaheadStep`, `resizeSmoothing`), `be/effects/Reverb.kt` (the comb tuning and the constants), the off paths of
`KatalystResonatorEffect`, `KatalystFilterSwap`, `KatalystPhaserEffect`, `Phaser.process`, `KatalystDuckEffect.configure`.
`audio/MEMORY.md` by a search for this layer's terms, not line by line. `audio/ref/off-values.md` in part (most rows
are the voice's). `audio/ref/effect-state-machines.md` by its headings.

## What I did not

- The stages' DSP (layer 7b): `DelayLine`, `Reverb`, `Compressor`, `Ducking`, `ResonatorBank`, `EqCore`,
  `PhaserCore`, `DistortionCore`, `Oversampler`, `PerlinNoise.fbm`, `BerlinNoise.fbm`.
- The specs, except by their row titles (about 28k lines for this layer). Every statement under "Test truth" comes
  from titles and a few targeted reads. No mutant was run; every "would survive" is a prediction.
- `br/KatalystKnobDsl.kt`, `br/KatalystDslIdentity.kt`, the wire codec, the sprudel and KlangScript doors,
  `HouseStage`. Nothing was measured on Kotlin/JS. Gradle was not run, so every finding is a reading of the code.

## The probes' leads in this layer

| Lead | Verdict | Cause |
|---|---|---|
| Clicks 1: delay switched ON returns its first echo as a step (kink 115, -27.5 dB at 250 ms); reverb ON the same (kink 33, -54.5 dB) | CONFIRMED | `KatalystDelayEffect.kt:360-363` (`Off.enter` resets the wet glide, which re-arms `snapNext`), `:686` (`retarget` then snaps, `KnobGlide.kt:151-155`), `:405` (the feed is the mix times the full wet from frame 0). The reverb: `KatalystReverbEffect.kt:265-268`, `:586`, `:309`. Finding L7a-01 |
| Clicks 2: a swap INTO a room, kink 63 at 70.7 ms | CONFIRMED, the stated cause is inexact | The arriving chain is fed the full mix from its first block and only its OUTPUT is weighted (`ChainSwap.kt:318-319`, `Crossfade.kt:252-277`); `Cylinder.kt:823` resets it, so its room's wet snaps as in lead 1. The room's FIRST return is not after the fade: the shortest comb is 1116 samples at 44.1 kHz, 25.3 ms (`Reverb.kt:88`), inside the 60 ms ramp and scaled by it. The 70.7 ms reading is a later return at full weight. Finding L7a-02 |
| Clicks 3: the cap and the floor engage and release in one sample | BY DESIGN | `KatalystArithmeticEffect.kt:146-150` (`settleAt` on the first operand after unset), `:132-135` (unset at once); the class KDoc and `audio/ref/katalyst.md` (the arithmetic row) say so. The probe's label is wrong: the `min` door builds the `Max` stage, a FLOOR, the `max` door the cap (`br/KatalystBuilders.kt:258`, `:264`). Reachable only through a slot whose value or default is non-finite |
| Clicks 4a: `compressor.attack` 0.1 to 0.003 applies at once (kink 17.8, -65 dB) | BY DESIGN, with a doc that overstates | `KatalystCompressorEffect.kt:507-509` hands the attack to `Compressor.applyKnobArgs` (`Compressor.kt:174-211`), which rebuilds the coefficient at once. The follower's state is continuous; what moves is its equilibrium, at the new 3 ms attack. `knob-glide.md:305` says attack and release "measured at the floor"; this direction reads 14 dB above the threshold glide's row. Listening task |
| Clicks 4b: the limiter with lookahead moves its threshold in about 8 ms | CONFIRMED: the lookahead path does not glide the three knobs | `Compressor.kt:524-528` (`processGliding` falls back to `process` when `delayFrames > 0`), so the fields `applyKnobArgs` wrote act at once and only the detector's hold and boxes shape the move. Finding L7a-04 |
| Clicks 7: the `pow` exponent glide 1 to 2 reads kink 9, -65 dB | DISPROVED as a click (medium confidence) | The exponent takes the same per-sample ramp as `times` (`KatalystArithmeticEffect.kt:277-279`, `:311-319`), which reads -86 dB. The difference is the law: `sign(x)|x|^2` adds harmonics to the 110 Hz tone, and the detector's band reads them arriving. Settle it with a control row: the same glide on a tone, against a render with the exponent held at 2 |
| Stage probe 3: the delay never reaches zero (a constant 1.44e-21) | NOT MINE, with an explanation to check | At the defaults (time 0.25, feedback 0.3) a 10 s window holds 40 laps, and 0.25 x 0.3^39 is 1.0e-21. The value is very likely the geometric tail at the window's end, not a constant. For the chain it is harmless either way: `hasTail` is a `TailCeiling` compare (`effects/TailCeiling.kt:104`), not a read of the ring |
| Stage probe 4: a room puts out 9.1e-19 for ever | BY DESIGN | `ANTI_DENORMAL` (`Reverb.kt:659`, `audio-constraints.md`). Downstream: the cylinder's silence scan, the swap's drain and the master all compare against `SILENCE_FLOOR` 1e-5 or `Reverb.TAIL_FLOOR` (`Cylinder.kt:706-717`, `ChainSwap.kt:494`, `KatalystReverbEffect.kt:325-326`), so the bias holds nothing open |
| Stage probe 6: an infinity leaves `times`, `div`, `pow` as 1e15, a NaN as 0 | BY DESIGN | `safeOut` (`utils/numerical_safety.kt:154-159`): NaN to 0, beyond the bound to the bound. `plus`, `minus`, `min`, `max`, `abs` pass both raw (`_arithmetic_laws.kt:46-48`, `:77-85`) |
| Stage probe 7: `pow(-1)` on silence is a constant 1e15; `div(1e-20)` peaks at 1e15 | BY DESIGN (raw) | `signedPow(0.0, -1.0)` is +Infinity, `safeOut` makes it 1e15 (`_arithmetic_laws.kt:72-74`); a `-0.0` sample gives -1e15. `safeDiv(1e-20)` is 1e-15 (`numerical_safety.kt:144-150`). What leaves this layer: every sample of the cylinder at +-1e15 from the first sample the exponent is below 0, and `sustainsItself` true (`KatalystArithmeticEffect.kt:184-200`). See L7a-03 for what a swap does with it |
| Cost 5: `pow` at 3 and -1 costs 28.6 ns; `abs` 7.5 ns | CONFIRMED | `powBlock` runs `Double.pow` per sample and channel whatever the exponent (`KatalystArithmeticEffect.kt:277-279`). `abs` is a compare and branch per sample (`:212-218`, `_arithmetic_laws.kt:85`); the probe's input is white noise, so the branch is mispredicted half the time. `min` and `max` read 5 ns for the same reason. Performance candidates |
| Cost 6: `bands` with both bands empty costs 30.3 ns | CONFIRMED | Two bands are four EQ sections, two lowpass and two highpass (`br/BandsBuilders.kt:167-200`), at about 7.5 ns each (the `eq` rows), plus the parallel's copies. On the bus every band carries the high side of every cut below it (`:173-175`), so the count grows with the square of the bands. Performance candidates |
| Cost 7: lookahead costs 38.1 against 23.9 | NOT MINE (7b), one related candidate here | A lookahead compressor that is switched OFF still runs the whole detector (`KatalystCompressorEffect.kt:308-323`). Performance candidates |
| Lint: `KatalystDelayEffect.kt:221` | CONFIRMED, per activation and per growth, never per block | `ensureRing` builds a `DelayLine` when the stage has no ring or the time outgrew it (`:198-231`), inside `configure`, which the owner's commit calls on the audio thread. L7a-07 |
| Lint: `KatalystKnobProgram.kt:382`, `:391` (`!!`) | CONFIRMED, harmless | The arrays are filled per modulator kind at compile (`:554-580`, `:636-637`), so the assert cannot fire. Once per block per noise |
| Dead knobs: `gain.gain` has no door | BY DESIGN | `br/KatalystDsl.kt` (the `classic` KDoc: reached with `katp("gain.gain", x)`) |

## Findings

### L7a-01 A delay or a reverb switched on while the bus sounds feeds the unit a step

- **Severity**: major
- **Tag**: sound
- **Where**: `be/katalyst/KatalystDelayEffect.kt:360-363`, `:682-686`, `:405`; `be/katalyst/KatalystReverbEffect.kt:265-268`,
  `:581-586`, `:309`; `be/KnobGlide.kt:151-155`.
- **What**: Voice A sustains on a cylinder without a delay. Voice B, with `delay(wet .25, time .25)`, starts and
  becomes the owner. `configure` finds the stage in `Off`. Every door into `Off` runs `wetGlide.reset()`, which sets
  `snapNext`. So `wetGlide.retarget(wet)` snaps, and the block's `advanceScaled` sees `end == from` and multiplies the
  whole block by the full wet. The ring therefore receives A's tone starting at full level at frame 0 of that block.
  One delay time later the output steps by `wet` times A's level. The probe read kink 115 and -27.5 dB at 250 ms; a
  hard gain step reads -18 dB. The reverb is the same through its combs.
  The stage already has the remedy for one case: with a modulated time or size, `wetGlide.startFrom(0.0)` runs on
  every return to `Active` (`:682-684`, `:581-583`), and a return from `Draining` glides up from 0 because the fade left
  the glide at 0 without a reset.
- **What a feed fade-in would need**: a way to tell "the bus has run since this stage's reset" from "this is the
  life's first block". `Off.process` is empty and `Off.enter` re-arms the snap, so today every activation looks like
  the first. The compressor carries exactly that flag (`fresh`, `KatalystCompressorEffect.kt:223`, `:292`, `:585`:
  cleared by the first `process`, set by `reset`). The same flag on the delay and the reverb, set false in the
  effect's `process` in any state and true in `reset` and `release`, would let line 682 read "modulated, or not
  fresh". The first note of a cylinder would still snap, so songs whose sends are on from the first block would not
  move. The price is a sound decision: the new owner's own first 50 ms would reach the ring at a rising level, so
  its echo loses attack. A one-block ramp (the modulated knobs' length) keeps most of the attack; the control row
  "one-block gain ramp" reads -60.5 dB against -18 for the step.
- **Reach**: any pattern that writes `delay` or `reverb` on some notes of a cylinder and not on others, or two
  patterns on one cylinder of which one has the send.
- **Settle it**: a row in `KatalystSwitchOffFadeSpec`'s style: a cylinder with a held tone, the owner changes from
  `{}` to the delay's four slots, and the largest sample-to-sample step of the output one delay time later is
  compared with the tone's own largest step. Today it is about `wet` times the tone's amplitude.
- **Confidence**: high for the mechanism; the remedy is a sketch.

### L7a-02 A chain swap feeds the arriving chain's room and delay at full level from the first block

- **Severity**: major
- **Tag**: sound
- **Where**: `be/ChainSwap.kt:313-319`; `be/Crossfade.kt:252-277` (the arriving chain's OUTPUT is weighted, its
  input is not); `be/cylinders/Cylinder.kt:823` (`next.reset()` re-arms every snap); `be/master/MasterBus.kt:337`.
- **What**: The swap law ramps the leaving chain's input down and the arriving chain's output up. The arriving chain
  processes the whole mix from block 0, and its delay or reverb has just been reset, so its wet snaps (L7a-01) and the
  unit is fed a signal that starts as a step. Whatever the unit returns later than the 60 ms ramp comes back at
  weight 1 with that step in it: a delay's first echo always (the gate is 10 ms, typical times are 125 to 500 ms),
  a room's second and later comb laps. The probe read kink 63, -49 dB for a room; a swap into a delay was not
  measured and should read like lead 1 (-27 dB class), because nothing of the ramp is left when the echo returns.
  The lead's sentence "the room's first output arrives after the crossfade has ended" does not match the comb
  lengths (25.3 to 36.7 ms at 44.1 kHz); those first returns are inside the ramp.
- **Reach**: every live edit of a `katalyst(...)` or `master(...)` chain that adds a delay or a reverb while sound
  is on the bus.
- **Settle it**: the click detector's swap row with `classic -> delay wet .5 time .25`; and a spec that renders a
  swap into a delay under a held tone and bounds the step at the first echo.
- **Confidence**: high for the delay case (traced), medium for which comb lap gives the 70.7 ms reading.

### L7a-03 A leaving chain that sounds on silence stays at full level for 20 s after the edit that removed it

- **Severity**: minor (rare stage shapes; an ordinary edit otherwise)
- **Tag**: sound
- **Where**: `be/ChainSwap.kt:290-304` (the fade's end: `rings` is true), `:494-510` (the drain ends only when the
  output is under `SILENCE_FLOOR`, else at `maxDrainFrames`), `be/TailRelease.kt:43`, `:88-92`.
- **What**: A chain holds `plus(0.2)`, a floor above 0, a cap below 0 or `pow` at 0 or below. The author removes the
  stage. The new chain fades in over 60 ms. The old chain's input is now silence, but its output is the constant, so
  the scan after the fade keeps it, `Draining` adds it at full weight for `MAX_DRAIN_SECONDS`, and `Releasing` then
  fades it over 4.5 s. The chain already knows its case (`KatalystChain.sustainsItself`, `KatalystChain.kt:477-499`,
  extended to the arithmetic stages on 2026-11-05 for the stopped playback); the swap does not ask. For `pow(-1)`
  the constant is 1e15, held 20 s after the edit meant to stop it, and the release's floor is relative (-90 dB), so
  the retire steps from about 3e10 to 0. Only a second edit cuts it (0.15 s). A delay at feedback 1 or above takes
  the same path by the maintainer's decision (i) of 2026-09-28; for an offset that cannot decay the hold has no
  ring-out to protect.
- **Reach**: `katalyst(k => k.plus(...))`, `.min(x)` with x above 0, `.max(x)` with x below 0, `.pow(e)` with e at 0
  or below, then an edit.
- **Settle it**: `ChainSwapCapSpec` with a `plus(0.5)` chain swapped for an empty one: the cylinder's output is 0.5
  for 20 s. A fix candidate to test against it: at the fade's end, a chain whose `sustainsItself()` is true goes to
  `Releasing` or `Cutting` at once.
- **Confidence**: high (read end to end).

### L7a-04 With an authored lookahead the three gain-computer knobs do not glide, and the docs do not say so where the law is stated

- **Severity**: minor
- **Tag**: sound, doc
- **Where**: `be/effects/Compressor.kt:524-528`, `:174-211`, `:388-412` (`resizeSmoothing`, `MIN_BOX_TAPS` 4);
  `be/katalyst/KatalystCompressorEffect.kt:537-574`, `:404-405`; `audio/ref/katalyst.md:72`.
- **What**: `configure` writes threshold, ratio and knee into the instance at once (`applyKnobArgs`) and starts a
  `CompressorKnobMove`. `compress` advances the move and calls `processGliding`, which on an instance with a ring
  returns into `process`, the lookahead loop, on the fields. The move's values are computed every block and used by
  nobody. A threshold that falls therefore reaches the gain as fast as the detector lets it: the hold takes the new
  minimum at once, the fast branch follows down at once, and the two boxes spread it over the attack time, floored
  at two boxes of 4 taps. With `limiter(lookahead .005, attack 0)` and `katp("compressor.threshold", -30)` after -1
  the gain falls by up to 29 dB within about 8 samples. `Compressor.processGliding`'s KDoc states the fallback and
  calls it safe; the law table in `katalyst.md` says "glide per sample as one move" without the exception, and
  `knob-glide.md:326` speaks only of the release direction.
- **Reach**: a `compressor` or `limiter` with a `lookahead` and a slot or a pattern on threshold, ratio or knee, on a
  cylinder with changing owners.
- **Settle it**: the click detector's limiter row at `attack 0` and at `lookahead 0.001`; if it reads in the
  hard-step class, glide the fields per block for a lookahead instance (the boxes then smooth 19 small steps).
- **Confidence**: high that the glide is skipped, medium on the size of the edge (not rendered).

### L7a-05 A `parallel` pad ring is not part of the tail question

- **Severity**: minor
- **Tag**: bug, lifecycle
- **Where**: `be/katalyst/KatalystParallelEffect.kt:176-187` (`holdFrames` is the twins' only; with no twin
  `hasTail()` is always false), `:137-145`, `:217-228`; the askers: `be/cylinders/Cylinder.kt:648-704`,
  `be/ChainSwap.kt:494-499`, `be/master/MasterBus.kt:402-413`.
- **What**: A branch without the lookahead that another branch has gets a delay ring of up to 2400 frames (50 ms at
  48 kHz). Take `parallel([delay(wet 1, time .25, feedback 0)], [compressor(lookahead .05)])` and a 5 ms click. The
  echo enters the pad at t + 250 ms and leaves it at t + 300 ms. The delay's ceiling reports a tail until the end of
  the window after the one the click fell into, which is anywhere from t + 255 to t + 505 ms. The compressor's quiet
  counter ran out at t + 55 ms. If the ceiling ends before t + 300 ms, nothing reports a tail while the echo sits in
  the pad and the mix is silent: a draining swap retires the chain in that block, the cylinder deactivates within 10
  blocks and `reset` clears the pad, the master reports not ringing. The echo is lost or cut. For a click that falls
  late in a ceiling window the chance is about (50 ms minus the sound's length) over 250 ms. A sound longer than the
  pad is safe (the output is audible while the pad empties), and so is a delay with feedback (its last echoes are
  under the floor before the ceiling ends).
- **Reach**: `parallel` or `bands` with a lookahead compressor or limiter in one branch and a delay near feedback 0
  in another, short sounds.
- **Settle it**: `KatalystParallelEffectSpec`: that chain, an impulse placed 240 ms into a ceiling window, then
  `hasTail()` asked every block until the echo has left; it must stay true until then. The remedy mirrors the
  compressor's `quietFrames`: count quiet frames of the branch's output up to the ring's length.
- **Confidence**: medium (traced by reading, not rendered).

### L7a-06 The tail scans run on every switch-off, not only in a swap's drain

- **Severity**: minor
- **Tag**: realtime, doc
- **Where**: `be/katalyst/KatalystDelayEffect.kt:467`, `:516`; `be/katalyst/KatalystReverbEffect.kt:391`;
  `.claude/skills/review-loop/audio-constraints.md:37`.
- **What**: The constraints list says `Reverb.combPeakAbs()` and `DelayLine.tapWindowPeakAbs()` are scans that "only
  a chain swap's one-off drain measurement calls". `leave` calls them at the end of every wet fade, so once per
  switch-off: on a cylinder whose owners alternate between a voice with a room and one without, about 28k cells
  every note pair, on the audio thread, in one block. For a delay the scan is as long as the tap window. Per change,
  bounded, likely tens of microseconds, but it is unbudgeted work the list says does not exist.
- **Reach**: the same patterns as L7a-01.
- **Settle it**: a counter on the two scans in a spec that alternates owners; the cost from a `stage-cost` row
  "reverb, owner toggles every 8 blocks".
- **Confidence**: high.

### L7a-07 The delay ring has no size bound, and its allocation and its cleaning run in `configure`

- **Severity**: minor
- **Tag**: realtime
- **Where**: `be/katalyst/KatalystDelayEffect.kt:163-231`, `be/warehouse/SizedBuffers.kt:121-131`, `:142-223`,
  `:345-349`.
- **What**: `ensureRing` asks for `ceil(time * sampleRate) + 64` frames. The ladder is open-ended by design
  (`SizedBuffers` KDoc, rule 1: "ask for an hour, get an hour or null"), the only stop is an allocation failure caught
  as `null`. `delay.time` written as 250 (milliseconds meant) asks for 12 million frames, 192 MB, allocated on the
  audio thread inside the owner's commit. If it succeeds, the old ring's history is copied and a `DelayLine` object
  is made (`:221`); if a dirty shelf ring of that class is rented instead, it is cleared in one go (`take`,
  `:216-219`). Every other large unit has a resource bound stated as such (`Compressor.MAX_LOOKAHEAD_SECONDS`, "a
  resource bound, not a taste clamp"; the region's factor; `coercePasses`). Smaller per-change allocations on the
  same paths: an `Idle` wrapper per give-back (`SizedBuffers.kt:240`, `ReverbUnits.kt:230`), a whole chain built on
  a cylinder's landing (`ChainHost.kt:230`, by design, 7 to 80 microseconds on the JVM by `build_us`),
  `name.lowercase()` and a capturing lambda in `ChainHost.kt:137`, `:273`.
- **Reach**: a raw `katp("delay.time", big)` for certain; I did not read whether the sprudel door bounds it.
- **Settle it**: `LazyRingSpec` with time 250 and the injectable `allocate` recording the request; then the
  maintainer's decision on a named bound for the delay time as a resource (not as a taste).
- **Confidence**: high for the path, low for how long the stall is (not measured).

### L7a-08 Ref pages and KDoc that say what the code does not do

- **Severity**: minor
- **Tag**: doc
- **Where and what**:
  1. `audio/ref/katalyst.md:52` "A non-finite knob is the declared OFF state". Not for +Infinity:
     `reverb.size` +Infinity is the largest room (`Reverb.normalizeSize`, pinned by `KatalystSlotResolverSpec`
     "off for NaN and the largest room for +Infinity"), while `delay.time` +Infinity is off
     (`KatalystDelayEffect.kt:664`); `duck.depth` +Infinity declares the duck (`KatalystSlotWriters.kt:595`), builds
     it at depth 1 the first time and keeps the PREVIOUS owner's depth afterwards (`Ducking.applyKnobArgs` drops a
     non-finite value).
  2. `audio/ref/katalyst.md:188` "compares and stores the SUBSTITUTED values, never the raw input". The delay, the
     reverb, the phaser and the compressor store the RAW arriving values and compare with `sameKnob`, which treats
     two NaN as equal (`KatalystDelayEffect.kt:650-699`, `KatalystReverbEffect.kt:554-591`,
     `KatalystCompressorEffect.kt:495-514`). It works; the rule as written describes another mechanism.
  3. The law table (`katalyst.md:70`) lists the delay's `time`, `feedback` and `wet`. `delay.cap` applies at once
     (`KatalystDelayEffect.kt:676`) and has no entry.
  4. `br/KatalystDsl.kt:576`: an arithmetic operand is "a `Constant` or a `Param`, read once per block". Since the
     knob expressions it may hold a modulator and is ramped per sample.
  5. `audio/ref/knob-glide.md:305`: attack and release "measured at the floor"; the probe's attack row is not (lead
     4a above).
  6. `clicks.txt` rows 123 to 126 call the `min` door's stage "a cap" and the `max` door's "a floor"; it is the
     other way round.
- **Reach**: readers and the next stage's author.
- **Settle it**: read the cited lines.
- **Confidence**: high.

### L7a-09 A switched-off lookahead compressor runs its whole detector

- **Severity**: note
- **Tag**: performance
- **Where**: `be/katalyst/KatalystCompressorEffect.kt:308-323`.
- **What**: `Off.process` on a latent instance calls `processLookahead` to keep the delay, which runs `lookaheadStep`
  per sample (a logarithm, the hold deque, two releases, two boxes) and throws the gain away. `switchOn` re-seeds the
  detector from the ring anyway (`:297-299`, `reseedFromRing`), so the off state needs only the ring's read and
  write.
- **Reach**: a `compressor(lookahead)` whose five knobs are slots and unset on some owners.
- **Settle it**: a `stage-cost` row "compressor lookahead, all knobs unset".
- **Confidence**: high for the path; the gain is the difference between rows 12 and a plain delay ring.

### L7a-10 The noise seeds follow the builder's line order, which no row pins

- **Severity**: note
- **Tag**: test
- **Where**: `be/katalyst/KatalystChainBuilder.kt:258-262` (phaser: `wet` before `rate`, where the stage declares
  `rate` first), `:205-208`, `:232-239`; `be/katalyst/KatalystKnobProgram.kt:559`, `:573`.
- **What**: The n-th noise of a chain is seeded `base + n`, and n is the order of the `knob(...)` calls. Reordering
  two lines in a stage's arm changes which walk each noise knob gets, and so every render with two noises in one
  stage. `KatalystKnobModulatorSpec` pins that noises differ and that a branch counts; from its titles nothing pins
  which knob gets which ordinal.
- **Settle it**: swap `knob(stage.wet)` and `knob(stage.rate)` in the phaser arm; I predict a green run.
- **Confidence**: medium (titles only).

## Performance candidates

| Where | What | Rate | Measured | Expected gain | Risk to the sound |
|---|---|---|---|---|---|
| `KatalystArithmeticEffect.kt:277-279` | `pow` settled at 2, 3, -1, 0.5: multiplies, a reciprocal, a square root instead of the general power | per sample | 28.6 ns at 3 and -1, 14.3 at 0.5 | to about 2 ns | A SOUND decision in the last bit: `x*x*x` rounds differently from `pow(x, 3)`, and the stage is pinned bit for bit to the Ignitor's node, so both would change together |
| `KatalystArithmeticEffect.kt:228-237` | an operand settled at the identity (an unset `Param`: `pow` 1, `times` 1, `div` 1, `plus` 0) still runs the loop; the fader skips unity (`KatalystGainEffect.kt:154-159`) | per sample | not measured at the identity; `pow` up to 28 ns | the stage's whole cost while unset | `times`, `div` and `pow` at identity still scrub a non-finite SAMPLE through `safeOut`; skipping loses that |
| `KatalystArithmeticEffect.kt:212-218`, `_arithmetic_laws.kt:77-85` | `abs`, `min`, `max` as a compare and branch per sample | per sample | 7.5, 5.1, 5.0 ns on white noise (the worst case for the branch) | to under 1 ns with the intrinsic | `abs(-0.0)` would become `+0.0`; `min` and `max` on a NaN keep the operand today. Shared laws: change both hosts or neither |
| `br/BandsBuilders.kt:167-200` | every bus band carries the high side of every cut below it: 4 sections at 2 bands, 21 at 4, 91 at 8 | per sample | 30.3 ns at 2 bands; about 7.5 ns per section | a split tree needs 15 at 4 bands, 49 at 8 | none to the sound (the same sections); it needs a stage of its own, so weigh it against "complexity is the enemy" |
| `KatalystCompressorEffect.kt:308-323` | L7a-09 | per sample | 33 to 38 ns while engaged | most of it while off | none |
| `KatalystDelayEffect.kt:467`, `KatalystReverbEffect.kt:391` | L7a-06, the scan at every switch-off | per change | not measured | a measured ceiling instead of a scan | the drain countdown would be less tight |
| `KatalystChain.kt:265-271` | `applyParams` calls every writer's `apply` on every block with an owner; each `configure` compares 3 to 5 doubles and returns | per block | about 50 ns per stage and block (`MEMORY.md`) | about 0.4 ns per sample on `classic`; not worth a mechanism | none |
| `ChainHost.kt:270-285` | eviction takes the oldest BUILT chain, not the least recently used; a ninth chain rebuilds a chain that is in rotation, on the audio thread at a cylinder | per change | build 7 to 80 microseconds | none in ordinary use | a rebuilt chain's modulator clocks start again |

The off paths of `KatalystDsl.classic`, read stage by stage (the cost table has no row for the bus chain as a
whole; its "`classic()` all off" row is the voice's): body and vowel `KatalystFilterSwap.Off.process` is empty
(`KatalystFilterSwap.kt:184`); delay and reverb `Off.process` is empty (`KatalystDelayEffect.kt:369`,
`KatalystReverbEffect.kt:274`); the phaser returns at rate 0 (`Phaser.kt:214`) and, once any owner has set a rate,
pays two sines and two tangents per core per block for the unconditional LFO clock (by design, ledger D2); the
compressor without lookahead returns (`KatalystCompressorEffect.kt:309-311`); the fader at 1 only advances its glide
(`KatalystGainEffect.kt:154-159`); the duck returns without a `Ducking` (`KatalystDuckEffect.kt:335`). The code
agrees with the table.

## The knob laws as the code has them

| Knob | Law | Where |
|---|---|---|
| `gain.gain` | per sample from the block's end, 17 or 19 blocks, exact landing; non-finite reads as 1 | `KatalystGainEffect.kt:153-167`, `KatalystSlotWriters.kt:463` |
| `plus`, `minus`, `times`, `pow` operand | per sample, the law taken at each sample; non-finite reads as the identity and is glided to | `KatalystArithmeticEffect.kt:127-154`, `:294-320` |
| `div` operand | its reciprocal glides, a multiply while moving, the true division once settled | `:139-141`, `:261-275` |
| `min`, `max` operand | glides between two finite values; arrives at once after unset, lets go at once | `:132-150` |
| delay `wet` | per sample; snaps out of Off; glides from 0 out of Draining and for a modulated time | `KatalystDelayEffect.kt:405`, `:682-686` |
| delay `feedback` | per block here, the per-sample ramp is the line's | `:677`, `:774-780` |
| delay `time` | no glide; the line crossfades taps | `:674` |
| delay `cap` | at once | `:676` |
| reverb `wet` | as the delay's | `KatalystReverbEffect.kt:309`, `:581-586` |
| reverb `size` | per block on the normalized axis | `:576`, `:696-702` |
| reverb `lowpass` | at once | `:575` |
| compressor threshold, ratio, knee | one move per sample without lookahead; at once with lookahead (L7a-04) | `CompressorKnobMove.kt`, `Compressor.kt:524-528` |
| compressor attack, release | at once | `Compressor.kt:174-211` |
| a knob holding a modulator | the same laws over one block | `KnobGlide.kt:80-84` |

The helper itself is as its KDoc says: whole blocks (`round(0.05 * rate / 128)`), the value interpolated from the
start and equal to the target on the last block, a retarget mid-glide restarts from the value in force, a
non-finite target is dropped before any store (`KnobGlide.kt:146-165`, `:177-188`). I found no knob that compares a
raw non-finite value against a cache in a way that never settles.

## Suspicions

- **A chain that lands late gets no owner settings.** `Cylinder.ownerParams` is kept for two blocks
  (`Cylinder.kt:519-527`). A request parked behind a cut lands about 0.2 s later through `pollPending`; if the
  requesting note's gate has closed by then, `beginFade` configures the arriving chain with `null`
  (`Cylinder.kt:828`, `:845`) and the note's release runs through the chain's authored defaults. Settle it: a
  staccato note requesting a swap during a drain, with a slot the chain reads. For the cylinder's auditor.
- **A named chain registered again with new content is not picked up.** `ChainHost.request` returns when the name
  equals the one in service (`:130-134`), and `chainFor` hands back the cached chain for the key (`:226`). Inline
  chains get a name from their content, so they are safe. I found no production door that registers a user's name
  twice; if one exists, this is a finding.
- **The resolve cache is by map identity** (`KatalystChain.kt:290`). Correct while a voice's `katalystParams` is
  never mutated after it is handed over and never reused for another voice. I did not read the codec or the
  scheduler for that.
- **The delay's 1.44e-21** (lead 3): if the ring really is not flushed, a delay that stays Active on a silent,
  held cylinder reaches subnormals after about 590 laps at feedback 0.3. For 7b.
- **`KatalystNestedChainsWriter` keeps the last owner's map** (`KatalystSlotWriters.kt:622-625`) until the next
  resolve or reset: one small map per `parallel` or region outlives its voice. Bounded.

## Decisions worth revisiting

- **Modulator clocks belong to the chain instance.** Every live edit builds a new chain, so every `Kat.sine` starts
  again at phase 0 and every noise at its seed position, under the 60 ms crossfade (`KatalystKnobProgram.kt:551`,
  `:564`, `:614`). A slow sweep restarts at each edit. A clock taken from the engine's block count would carry
  across a swap and would also make two cylinders' LFOs of the same rate agree. Related: the n-th noise is seeded by
  its position alone, so the first `Kat.perlin` of every cylinder and of the master walks the same sequence.
- **The capped drain for a chain that sustains itself** (decision (i), 2026-09-28): see L7a-03. The 20 s were chosen
  for rooms and long echoes; a chain that `sustainsItself()` has no ring-out, and a delay at feedback above 1 keeps
  growing for those 20 s.
- **The open ring ladder** (L7a-07): the one large unit without a resource bound.
- **"From Off it snaps"** (L7a-01, L7a-02): the rule protects the first block of a cylinder; applied to every later
  activation it is the loudest click the detector found in this layer.

## Listening tasks

- A held pad and a snare with `delay` on one cylinder: the pad's edge in the first echo today, against a one-block
  and a 50 ms feed fade-in. Listen for the click, and for how much of the snare's attack the echo keeps.
- An edit that adds a delay, and one that adds a room, under a held chord.
- `delay.time` driven by `Kat.sine`: the tap hops every 50 ms with a crossfade and never bends pitch. Is that what
  an author expects from a modulated time?
- A slow `Kat.sine` on a filter or the fader while the chain is edited: the sweep's restart at each edit.
- `compressor.attack` patterned between 0.1 and 0.003 on a loud bass; a limiter with lookahead whose threshold is
  patterned, at attack 0.
- `pow` with the exponent patterned between 1 and 2: confirm that what the detector read is the tone changing and
  not a click.

## Test truth

Pinned, by the rows' titles: the glide's length, landing, retarget, snap and non-finite wall (`KnobGlideSpec`); the
release recurrence against the closed form within 1e-10 and its floor (`TailReleaseLawSpec`); the swap's refusals,
the cut's shape and timing, the cap and the release, a room draining to its own end, state identity
(`ChainSwapSpec`, `ChainSwapCutSpec`, `ChainSwapCapSpec`, `ChainSwapStateIdentitySpec`); every switch-off fading
(`KatalystSwitchOffFadeSpec`); the arithmetic laws against the Ignitor, the divisor's reciprocal glide, the unset
bound (`KatalystArithmeticEffectSpec`); branch alignment, twins and their tail (`KatalystParallelEffectSpec`); the
modulators against hand laws and the Ignitor's streams, the clock wraps, the fixed seed, "run moves no clock"
(`KatalystKnobModulatorSpec`); the slot rules knob by knob (`KatalystSlotResolverSpec`); the rings and units
(`LazyRingSpec`, `LazyReverbSpec`, `ResourceWarehouseSpec`).

Not pinned, as far as the titles show:

- A send switched ON on a sounding bus (L7a-01). The rows start from silence or pin the snap as the wanted
  behaviour.
- A swap INTO a delay or a room judged at the first return (L7a-02). `CylinderChainCrossfadeSpec` judges the ramp.
- A leaving chain that sounds on silence (L7a-03): `ChainSwapCapSpec` uses a delay at feedback 1.
- The pad ring's content at the tail question (L7a-05). Mutation that survives by construction: `Pad.hasTail()`
  returning `false` whenever there is no twin is today's code.
- A knob move on a lookahead instance (L7a-04).
- The noise ordinals' assignment to knobs (L7a-10).
- The victim of `ChainHost.evictIfNeeded`: predicted to survive replacing "first" by "last" in `:273`.

All predictions; none was run.

## For the audit ruleset

- The click table was the best lead-giver for this layer: each row named a knob and a time, and the time pointed at
  the mechanism (one delay time, one comb lap). A lead should carry the rendered configuration in full; "into a
  room" needed the comb lengths to see that its stated cause was off.
- The brief's question list per layer worked better than the generic checklist. Most findings came from asking
  "which state re-arms the snap" and "who asks the tail question about this memory", not from scanning for NaN.
- Reading 28k lines of specs was not possible inside one pass. Give the auditor a generated list of row titles per
  spec, or split "test truth" into its own pass with mutants allowed under the build lock.
- No Gradle means every timing claim is arithmetic. A small sanctioned render tool (one chain, one input, print the
  samples around an instant) would have turned three medium confidences into high ones.
- Layer borders need an owner for the seams: the host half of `Cylinder` and `MasterBus`, and the lifecycle half of
  the delay, reverb and compressor stages, sit between 7a, 7b and the cylinder layer. I read them; the brief should
  say who does.
- Ask each auditor for the law table read from the code (as above). Writing it found the missing `delay.cap` row and
  the lookahead exception faster than comparing prose.
