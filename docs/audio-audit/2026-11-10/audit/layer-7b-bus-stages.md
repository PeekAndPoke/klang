# Layer 7b: the bus stages' DSP

Auditor's note on method: everything below is from reading the code and the probe tables. I ran no code and no
simulation (the brief's allowed list has none), so every number I give that is not quoted from a probe table is
hand arithmetic, shown so it can be checked.

**What I read** (whole file unless noted)

- `audio_be/src/commonMain/kotlin/effects/`: `Ducking.kt`, `Compressor.kt`, `DelayLine.kt`, `Reverb.kt`,
  `TailCeiling.kt`, `PhaserCore.kt`, `Phaser.kt`.
- `audio_be/src/commonMain/kotlin/filters/`: `ResonatorBank.kt`, `ResonatorTable.kt`, `ResonatorConfig.kt`,
  `WetDryMix.kt`, `EqCore.kt`, `EqSectionSpec.kt`, `LowPassHighPassFilters.kt` (the SVF coefficient helpers, the
  clamps, `bodyGain`, `vowelGain`, `DcBlocker`).
- `audio_be/src/commonMain/kotlin/katalyst/`: `KatalystDuckEffect.kt`, `KatalystCompressorEffect.kt`,
  `CompressorKnobMove.kt` (code, KDoc skipped), `KatalystDelayEffect.kt`, `KatalystReverbEffect.kt`,
  `KatalystPhaserEffect.kt` (code, most KDoc skipped), `KatalystResonatorEffect.kt`, `ResonatorTables.kt`,
  `KatalystEqEffect.kt`, `KatalystDistortEffect.kt`, the duck's writer in `KatalystSlotWriters.kt` (lines 531 to 608).
- `audio_be/.../DistortionCore.kt` (code), `ShapingFuncs.kt` (`softCap`, `softCapTo`, `expClip`, `stompBox` only),
  `utils/fast_math.kt` (`fastExp2`, `fastExp`), `utils/numerical_safety.kt`, `utils/time_constant.kt`,
  `utils/same_knob.kt`, `KnobGlide.retarget` and `advanceScaled` only.
- `audio_bridge/.../constants/BusEffectDefaults.kt`, `MasterLimiterDefaults.kt`, `BodyMaterials.kt`, `VowelBands.kt`,
  the `Compressor`, `Phaser`, `Reverb`, `Delay` KDocs of `KatalystDsl.kt`, `KatalystBuilders.limiter`.
- `audio/CLAUDE.md`, `audio-constraints.md`, `audio/ref/effects-mixing.md`, `audio/ref/numerical-safety.md`, the
  leads of KLANG-00420, the rows of my stages in `stage-probe.txt`, `stage-cost.txt`, `clicks.txt`,
  `hot-path-lint.txt`.
- `Cylinders.processAndMix` (the duck pass) and `Cylinder.isMixBufferSilent`, to place two findings.

**What I did not**

- `audio/MEMORY.md` (grepped for my stages only), `audio/ref/katalyst.md` (parts), `audio/ref/knob-glide.md` and
  `audio/ref/off-values.md` (the head and greps only), `docs/audio-backend-file-map.md`.
- `KatalystFilterSwap`, `KnobGlide` as a whole, `KatalystChain`, `KatalystChainBuilder`, the other slot writers,
  `ChainSwap`, the warehouse (`SizedBuffers`, `ReverbUnits`), `HouseStage`. They are layer 7a's or others'.
- The specs: I read the row titles of the `effects/` specs and of the body, vowel specs, the whole of the first
  three rows of `DuckingSpec` and the first five of `CompressorSpec`, and nothing of the `Katalyst*EffectSpec` bodies.
  The test truth section says where a claim rests on titles only.
- `framing.txt`, `aliasing.txt`, `dead-knobs.txt` beyond the summary in the ticket.
- The Kotlin/JS side of any cost. Every cost below is the JVM table's.

## The probes' leads in this layer

| Lead | Verdict | Cause |
|---|---|---|
| Stage probe 1: one NaN on the side chain latches the ducked bus, an infinity recovers | CONFIRMED | `Ducking.kt:134` to `146`, finding L7b-01 |
| Stage probe 3: the delay never reaches exact zero (1.44e-21, 1.19e-36) | CONFIRMED, harmless | `DelayLine` flushes nothing: the ring store (`DelayLine.kt:604`, `:680`) is `softCap` (identity below 0.95) and `nanGuard` only, so silence in leaves a geometric decay by the feedback per lap. 0.3^40 is 1.2e-21: the value is where a 0.25 s delay at feedback 0.3 stands after the probe's 10 s, not a constant. It goes on to the subnormal range after about 588 laps (147 s) and to exact zero about 31 laps later. Cost: note L7b-12 |
| Stage probe 4: the reverb outputs 9.1e-19 (size 5) or 7.5e-18 (size 10) on silence for ever | BY DESIGN | The documented `ANTI_DENORMAL` (`Reverb.kt:462`, `:478`, `:498`, `:513`; `audio-constraints.md`). I can derive both numbers: the comb's store settles at `e / ((1 - d)(1 - fb))` with e = 1e-18, d = 0.2, so its cell holds `fb` times that; eight combs sum; each all-pass adds 2e; times `FIXED_GAIN` 0.015. Size 5 (fb 0.84): (8 * 6.56e-18 + 8e-18) * 0.015 = 9.1e-19. Size 10 (fb 0.98): (8 * 6.1e-17 + 8e-18) * 0.015 = 7.5e-18. Downstream cost: nothing I can find. `Cylinder.isMixBufferSilent` and `TailCeiling` compare against 1e-5, the compressor reads anything under 1e-10 as its silence floor without a `ln`, and 1e-18 is a normal double. The KDoc's own figure for the bias (`Reverb.kt:655`, "about 2.8e-18") leaves the feedback loop out: the store holds 7.8e-18 at size 5 and 6.3e-17 at size 10 |
| Stage probe 5: 23999 Hz rings 0.55 s at q 0.707, 9.99 s at q 200 | CONFIRMED for the eq (the voice SVF is NOT MINE) | `clampSvfCutoff` (`LowPassHighPassFilters.kt:144`) tops out at `fs/2 - 1`. There `g = tan(pi * 23999 / 48000)` is 15279, and the bilinear map puts the pole pair at radius about `1 - 1/(Q g)`: 1 - 9.3e-5 at q 0.707 (a time constant of 0.22 s), 1 - 3.3e-7 at q 200 (64 s). The ring sits at Nyquist and is small (about `2k/g` of an impulse on the lowpass tap), but it holds the cylinder above the 1e-5 floor. See Decisions |
| Stage probe 11: the eq band at 5 Hz q 200 keeps DC | BY DESIGN (raw) | It is a 5 Hz ring, not DC: the time constant of an SVF is `Q / (pi f)`, 12.7 s here. The author asked for it |
| Stage probe 12: the default authored limiter passes a step at 1.00 through -1 dB | CONFIRMED, cause in L7b-04 | No lookahead, and the dB-domain follower starts at -100 dB: 3.9 ms pass before any reduction. Neither `KatalystBuilders.limiter` (`KatalystBuilders.kt:167`) nor the `Compressor` stage KDoc says that a limiter without lookahead is not a ceiling |
| Stage probe 6, 7, 8, 9, 10, 13 | NOT MINE | arithmetic stages, shimmer, coarse, the voice filters |
| Framing 2: the bus phaser depends on the block grid (-43.5 dB) | BY DESIGN | `PhaserCore.prepare` (`PhaserCore.kt:148` to `167`) advances the LFO by `rate * blockFrames` per block of the cylinder and interpolates alpha inside it; `Phaser.process` runs it unconditionally (`Phaser.kt:222`). Stated in both KDocs (ledger D2, D4) |
| Framing 3: the bus delay is identical at every offset | CONFIRMED | Tap, feedback ramp and crossfade are all per sample in `DelayLine` |
| Clicks 1: delay ON (kink 115 at 250 ms), reverb ON (kink 33 at 70.7 ms) | BY DESIGN | `Off.enter` resets the wet glide, so the first configure snaps the feed (`KatalystDelayEffect` and `KatalystReverbEffect` class KDocs). The room returns that edge late: 70.7 ms is 3394 samples, which is comb 7 (1694) plus the four all-pass lengths (605 + 480 + 371 + 244) at 48 kHz |
| Clicks 2: a swap into a room | NOT MINE (same cause as the row above, the swap is 7a's) | |
| Clicks 4: `compressor.attack` applies at once; the lookahead path's threshold is not glided | BY DESIGN, both | Attack and release are never glided (`KatalystCompressorEffect` KDoc). On a lookahead instance `Compressor.processGliding` falls through to `process` (`Compressor.kt:525`), which reads the knobs `applyKnobArgs` already stored; the hold, the release and the boxes smooth the step. The KDoc says so ("The knob move does not apply on this path") |
| Aliasing 7: Ignitor and Katalyst distort agree within 0.1 dB | CONFIRMED | One `DistortionCore`; the bus stage differs only in the DC pole (`HOUSE_DC_BLOCK_COEFF`) |
| Cost 1: `exp` and `stompbox` cost 3.4 times more at amount 3 | CONFIRMED | The drive is `10^(amount * 1.2)`, 3981 at amount 3. `expClip` and `stompBox` call `fastExp` (`ShapingFuncs.kt:164`, `:166`, `:308`, `:310`), whose fast range is `abs(x) < 22.18`; beyond it `fastExp2` returns `2.0.pow(x)` (`fast_math.kt:134`). At drive 3981 every sample above 0.0056 (0.0019 on stompbox's negative side) takes the `pow`. At amount 0.5 the drive is 3.98 and nothing leaves the fast range |
| Cost 7: lookahead 38.1 against 23.9 (compressor), 32.8 against 24.4 (limiter); delay feedback 1.5 34.7 against 25.2 | CONFIRMED | Lookahead adds per sample: the min-hold deque, one division (`held / slowGain`, `Compressor.kt:742`), two divisions by the box length (`:760`, `:768`), four ring accesses and two `finiteOrZero`. The delay at feedback 1.5 sits above 0.95 of the cap, so `softCap` takes its `fastTanh` branch on every sample |
| Cost 9: body 58, vowel 37 | CONFIRMED | 8 modes times 2 channels is 16 SVF bands (3.6 ns each); a vowel has 5 formants, 10 bands (3.7 ns each). No band in either catalogue is silent: the quietest body mode is -13 dB |
| Hot-path lint: `KatalystDuckEffect.kt:254` | CONFIRMED | L7b-07 |
| Hot-path lint: `KatalystDelayEffect.kt:221` (`DelayLine(...)`) | CONFIRMED, by design | Once per rent or grow of a ring; the warehouse's decision |

## Findings

### L7b-01 One NaN on the side chain makes the duck's gain NaN until the duck's life ends

- **Severity**: critical (by the brief's definition: a state that does not recover while the stage stays on; the
  reach needs a NaN source, see Reach)
- **Tag**: safety
- **Where**: `audio_be/src/commonMain/kotlin/effects/Ducking.kt:134` to `146` (`stepGain`), called at `:161` and `:206`.
- **What**: the level handed in is `max(abs(sidechainL[i]), abs(sidechainR[i]))`. `abs(NaN)` is NaN and
  `kotlin.math.max` returns NaN when either argument is NaN (JVM and JS), so one NaN on either channel gives a NaN
  level. Then, line 138, `min(1.0, NaN * 2.0)` is NaN and `targetGain` is NaN. Line 139 tests
  `targetGain < currentGain`: false for a NaN, so the release arm runs:
  `currentGain + releaseCoeff * (NaN - currentGain)` is NaN and is STORED in `currentGain`. From the next sample on
  the target is finite again, but `finite < NaN` is false too, so the release arm runs for ever on a NaN state.
  Both channels are multiplied by it (`:162`, `:163`), which is why the probe's clean right channel is NaN too.
  An infinity escapes because `min(1.0, Inf)` is 1.0: the target is the finite `1 - depth` and the state never sees
  the infinity. So the state has one door for a non-finite value, and it is the release arm's arithmetic.
- The gliding loop (`processStereoGliding`, `:206`) calls the same `stepGain` and latches the same way; there
  `weighted = 1 + w * (NaN - 1)` is NaN even at weight 0, so the 50 ms switch-off fade is NaN as well.
- What ends it: only a new `Ducking`. `KatalystDuckEffect.endLife` drops the instance when the stage is switched off
  and its fade lands, `reset` and `retire` drop it, and `takeOver` CARRIES the NaN envelope into the arriving chain
  of a swap (`KatalystDuckEffect.kt:197`). So "for good" means: for as long as the owner keeps the duck on.
  (`Cylinder.isMixBufferSilent` reads a NaN as silent, both compares being false, so a cylinder whose voices have
  all ended can still deactivate and reset. I did not trace that path further; it is 7a's.)
- **Reach**: `duck(cylinder = n, ...)` on any door, plus one non-finite sample in the SOURCE cylinder's mix after its
  chain. The damage crosses cylinders: a NaN on cylinder n poisons every cylinder that ducks from n, and those stay
  poisoned after n itself has recovered. Sources of such a sample are narrow by now (the voice guards of
  `numerical-safety.md`); one I can name in this layer is the phaser at a low sample rate (L7b-05).
- **The smallest correct fix** (described, not applied): make the level's saturation NaN-proof without a new compare,
  by replacing the `min` call with the compare it stands for, written so a NaN falls into the constant arm:
  `val x = sidechainLevel * SIDECHAIN_SENSITIVITY; val sat = if (x < 1.0) x else 1.0`. A NaN then reads as a full
  duck for that one sample, exactly what an infinity does today, and `targetGain` is finite for every input (the
  depth is finite on both paths: `depthValue` is coerced, the glide's `KnobGlide.retarget` drops a non-finite
  target). The alternative with the compressor's meaning ("a non-finite level is silence") is
  `sidechainLevel.finiteOrZero()` at the top, one more compare per sample; it would also change what an infinity
  does. Which of the two a NaN should mean is a decision (see Decisions). Either way add the row to `DuckingSpec`.
- **Settle it**: `DuckingSpec`: feed `processStereo` one block whose side chain has a NaN at frame 0, then a clean
  block; the second block's output must be finite. Red today. The stage probe's `duck ... nan` rows turn green.
- **Confidence**: high.

### L7b-02 The vowel tables use formant bandwidths (in Hz) as q, so every formant is about 10 to 35 Hz wide

- **Severity**: minor (it is every `vowel(...)`, but what is "right" here is partly for ears; see Listening)
- **Tag**: sound
- **Where**: `audio_bridge/src/commonMain/kotlin/VowelBands.kt:38` to `44` (the convention), the tables from `:163`;
  `audio_be/.../filters/LowPassHighPassFilters.kt:427` to `434` (`vowelGain`), `:242` (`VOWEL_TAME`).
- **What**: the frequency and dB columns are the classic sung-vowel formant table (the one in the Csound manual;
  soprano a: 800, 1150, 2900, 3900, 4950 Hz at 0, -6, -32, -20, -50 dB). In that table the third column is the
  formant's BANDWIDTH in Hz (soprano a: 80, 90, 120, 130, 140; bass a: 60, 70, 110, 120, 130). I state the source
  from memory and could not look it up (no network); what settles it without the source is physics: a sung
  formant is 50 to 150 Hz wide, a q of about 5 to 30. Here the column is the SVF's q: `b(600.0, 0.0, 60.0)`
  becomes a band at 600 Hz with q 60, 10 Hz wide (true: 60 Hz, q 10); the fifth soprano formant is 4950 / 140,
  35 Hz wide (true: 140 Hz). Every vowel of a register also carries the same third column, where the source has
  one per vowel, which fits a column copied from the `a` row.
- The consequence, traced: the band is unity-peak times `10^(db/20) * q * 0.05`, so F1 peaks at 3.0 (bass) to 4.0
  (soprano). A harmonic that falls on it is boosted by up to +12 dB before the blend; one 50 Hz away is down to
  `1 / sqrt(1 + q^2 (f/f0 - f0/f)^2)`: for bass `a`, a 110 Hz saw has harmonics at 550 and 660 Hz, each at 0.095
  of the peak, 0.29 after the gain (-10.8 dB), while a 150 Hz note puts a harmonic on 600 Hz and gets 3.0 (+9.5 dB).
  A 20 dB swing between two notes of one melody. With the table's q read as bandwidth (q 10) the 550 Hz harmonic
  would sit at 0.50 of the peak. The stage probe shows the same thing from the other side: `soprano:i` at wet 1,
  floor 0 passes full-scale white noise at -14.5 dB and the sweep at +2.2 dB.
- The decay time of such a band is `q / (pi f)`: 32 ms at 600 Hz, q 60. That is the ring the tables' KDoc mentions.
- **Reach**: every `vowel(...)`; the body's `croon` is not affected (its q are 8 to 22).
- **Settle it**: render a saw through `vowel("bass:a")`, wet 1, floor 0, at 110 Hz and at 150 Hz, and compare RMS:
  about 20 dB apart today. Then the listening task.
- **Confidence**: medium on the origin of the column, high on the widths and the arithmetic.

### L7b-03 `reverb.lowpass` is documented as a cutoff in Hz, and it is a linear damping amount

- **Severity**: minor
- **Tag**: consistency
- **Where**: `audio_be/.../effects/Reverb.kt:403` to `411` (the mapping), `:138` (KDoc: "a lowpass cutoff in Hz"),
  `audio_bridge/.../KatalystDsl.kt:373`, `audio/ref/effects-mixing.md:143` ("Tail damping cutoff in Hz").
- **What**: the code maps `damp = 1 - lowpass / nyquist`, clamped to 0..1, and `damping = damp * 0.4`, the
  coefficient of the comb's one-pole (`store = out * (1 - damping) + store * damping`). That one-pole's -3 dB point
  is at `cos w = (4d - 1 - d^2) / (2d)`. At `lowpass = 0`, the darkest setting, d is 0.4 and the corner is 7.55 kHz
  at 48 kHz. At `lowpass = 1000` d is 0.383 and the corner is about 7.3 kHz, not 1 kHz. From 13.7 kHz up
  (d below 0.172) the one-pole never reaches -3 dB at all. The default (unset) is d 0.2, a corner at 16.9 kHz; the
  KDoc calls it "a lowpass at half the Nyquist frequency" (`:577` to `580`).
- What an author hears: `reverb(lowpass = 500)` and `reverb(lowpass = 0)` are nearly the same room. Per lap the
  loop gain at 1 kHz is 0.99 of the undamped one at the darkest setting, and at 4 kHz 0.88, so at size 5 the 4 kHz
  tail is about 0.57 of the undamped length and the 1 kHz tail unchanged. The mapping also moves with the sample
  rate (it is a ratio to Nyquist feeding a per-sample pole).
- **Reach**: `reverb(lowpass = ...)` on every door.
- **Settle it**: render an impulse through the room at `lowpass` 0, 1000 and unset, and compare the decay of the
  1 kHz and the 4 kHz third-octave bands. Then either the words change ("damping, 0 is darkest") or the mapping does
  (a sound change, the maintainer's).
- **Confidence**: high.

### L7b-04 The compressor smooths the LEVEL in dB before the gain computer: a dead time on the attack and a release that both hang on the -100 dB floor

- **Severity**: minor (character rather than a defect, but undocumented, and it is the cause of lead 12)
- **Tag**: sound
- **Where**: `audio_be/.../effects/Compressor.kt:632` to `659` (`followEnvelope`), `:963` (`SILENCE_DB = -100`);
  the lookahead path's other law at `:734` to `:748`.
- **What**: `envelopeDb` is a one-pole towards the input level in dB, and silence reads as -100 dB. After a rest the
  envelope stands near -100. A 0 dBFS onset then climbs as `-100 * (1 - c)^n`, and the gain computer returns exactly
  0 dB of reduction until the envelope reaches `threshold - knee/2`. At the defaults (threshold -20, knee 6, attack
  3 ms, c = 6.92e-3) that takes `ln(100/23) / c` = 212 samples: 4.4 ms of the transient pass at full level, where a
  compressor that smooths the gain reduction (the textbook's log-domain detector after the gain computer) starts
  reducing on the first sample. For the limiter preset (threshold -1, knee 2, attack 1 ms) it is
  `ln(100/2) / 0.0206` = 188 samples, 3.9 ms at 0 dBFS through a "-1 dB" stage: the probe's step at 1.00.
  The dead time is `attack * ln((level + 100) / (level - threshold + knee/2))`, so it grows as the signal sits
  closer above the threshold.
- The release has the mirror property: when the input stops, the envelope falls towards -100 dB with the release
  time constant, so 15 dB of reduction at 0 dBFS are gone when the envelope passes -23 dB:
  `0.1 s * ln(100/77)` = 26 ms at `release = 0.1`. When the input only drops to -30 dB the same reduction takes
  146 ms. One knob value, a factor of 5 or more depending on what follows the peak.
- With a lookahead the SAME `release` knob is a different law: a one-pole on the linear gain plus a second one ten
  times faster (`:734` to `:748`). The KDoc of `attackSeconds` says the attack changes meaning with a lookahead; the
  release is not mentioned.
- **Reach**: every `compressor(...)` and `limiter(...)` without lookahead, after any gap of roughly the release time.
- **Settle it**: a spec that feeds 0.2 s of silence and then a 0 dBFS step at the defaults and counts the samples
  whose gain is exactly 1.0 (212 expected); a second with the level dropping to -30 dB against to silence.
- **Confidence**: high on the trace. That it should change is a decision (the file's header lists the linear-domain
  rewrite as deferred).

### L7b-05 The phaser's breakpoint is clamped to 18 kHz whatever the sample rate: below 36 kHz the all-pass can go unstable

- **Severity**: minor (the reach is a low engine rate)
- **Tag**: safety
- **Where**: `audio_be/.../effects/PhaserCore.kt:176` to `186` (`alphaAt`), `:251` (`MAX_MOD_FREQ_HZ = 18000.0`).
- **What**: `alpha = (t - 1) / (t + 1)` with `t = tan(pi * modFreq / fs)`. The all-pass
  `y = a x + s; s = x - a y` has its pole at `-a`, stable only for `abs(a) < 1`, which needs `modFreq < fs / 2`.
  The clamp is the absolute 18000 Hz. At fs 32000 and a breakpoint of 18000 Hz: t = tan(1.767) = -5.03 and
  alpha = (-6.03) / (-4.03) = 1.50. Each of the four stages then grows by 1.5 per sample; the state passes
  `Double.MAX_VALUE` within a few thousand samples, `flushState` zeroes it, and it grows again, so the stage emits
  bursts up to 1e308 and non-finite samples for as long as the LFO is above Nyquist. At fs 24000 and exactly
  18000 Hz `t + 1` is 0 and alpha is infinite.
- **Reach**: an engine at a rate below 36 kHz (`Reverb`'s KDoc names 22.05 kHz as the supported floor) with
  `phaser(center, sweep)` such that `center + sweep / 2` passes `fs / 2`. Not reachable at 44.1 or 48 kHz. The
  Ignitor `.phaser(...)` node shares the kernel.
- **Settle it**: `PhaserCoreLawSpec` at sampleRate 32000, center 18000, sweep 0, 1 s of noise: the output must stay
  bounded. A clamp relative to the rate (the SVF's `clampSvfCutoff` is the house form) would close it.
- **Confidence**: medium (traced, not run).

### L7b-06 `DelayLine.reset` zeroes the whole ring in one block, whatever its size

- **Severity**: major (unbounded work in render code, sized by a number the author writes)
- **Tag**: realtime
- **Where**: `audio_be/.../effects/DelayLine.kt:406` to `412` (`buffer.clear()`, both channels of the whole ring),
  called on the audio thread from `KatalystDelayEffect.kt:470` (the drain's last block), `:539` (a switch-off with a
  silent tap window) and `:760` (`reset`).
- **What**: rings are rented by class, doubling from the base, and never shrink. A `delay(time = 10)` needs 480000
  frames and holds a class of at least that (the next doubling of the base above it). When that delay finishes its
  drain, `Draining.process` calls `line.reset()`, which fills two arrays of the whole class with zeros inside one
  128-frame block: about 8 to 12 MB. `DelayLine.tapWindowPeakAbs`'s own KDoc records that a whole-ring SCAN at this
  point "cost most of a block budget" and was removed for that reason; the whole-ring WRITE stayed. The warehouse
  zeroes its idle rings in slices of a base ring per block for the same reason (`SizedBuffers.housekeep`), and
  `Reverb.resetPart` exists for large units.
- **Reach**: any delay time of several seconds, each time its tail ends (and at every cylinder reset).
- **Settle it**: time `DelayLine.reset()` on a ring for 10 s under the node production bundle against the 2.67 ms
  of a block. If it is small, this drops to a note.
- **Confidence**: medium (the path is traced; the time is not measured).

### L7b-07 A duck's life builds its `Ducking` on the audio thread

- **Severity**: minor
- **Tag**: realtime
- **Where**: `audio_be/.../katalyst/KatalystDuckEffect.kt:254` to `259`.
- **What**: every switch-on from off builds a `Ducking` (the object and its `DoubleArray(2)`) inside `configure`,
  which the chain's writer calls from the block. `endLife` drops it, so a pattern that switches the duck per event
  (`duck.depth` written 0 and above 0 in turn, or `duck.cylinder` set and unset) allocates per event. The compressor
  had the same shape and was changed to one instance built with the effect
  (`KatalystCompressorEffect`'s `instance` KDoc: "the ON edge used to build a `Compressor` on the audio thread").
- **Reach**: any patterned duck. It also bears on the lint's open rule question (per note or per change).
- **Settle it**: `KatalystSettingsAllocationSpec` with a duck toggled every block: bytes per block above zero today.
- **Confidence**: high.

### L7b-08 A chain of ducks depends on the order the cylinders were first used

- **Severity**: minor
- **Tag**: consistency
- **Where**: `audio_be/.../cylinders/Cylinders.kt:146` to `151`.
- **What**: the duck pass walks the rented cylinders in rent order and each `processDuck` scales that cylinder's
  `mixBuffer` in place. A later cylinder that names an earlier one as its side chain therefore reads the ALREADY
  ducked mix, and one that names a later cylinder reads the unducked mix. With bass ducked by kick and pad ducked by
  bass, the pad's reduction follows the ducked bass when the bass's cylinder was first used before the pad's, and
  the full bass otherwise. The same holds for a cylinder ducking from itself and another reading it. Deterministic
  for one song, but decided by which note came first, not by anything the author wrote.
- **Reach**: two `duck(...)` calls that form a chain. Possibly 7a's finding as well; I report it because the question
  on the duck asked for what lies beyond the latch.
- **Settle it**: a `Cylinders` spec with the three cylinders rented in two orders, comparing the pad's block.
- **Confidence**: high on the code; I did not check that `rented` is never reordered.

### L7b-09 `phaser(sweep = 0)` sweeps 1000 Hz, and a center at or below 0 becomes 1000 Hz

- **Severity**: minor
- **Tag**: consistency
- **Where**: `audio_be/.../katalyst/KatalystPhaserEffect.kt:248`, `:249`.
- **What**: `if (center > 0) center else PHASER_CENTER_HZ` and the same for the sweep. The house rule is that a
  NON-FINITE knob is unset; here 0 and every negative number are unset too. A sweep of 0, the natural spelling of
  "no sweep, a fixed notch", gives the default 1000 Hz sweep, while 0.001 gives none. The DSL KDoc says "width of
  the sweep around center, in Hz" and names no such rule; the kernel itself clamps the breakpoint to 100 Hz, so a
  small center needed no substitution.
- **Reach**: `phaser(sweep = 0)`, a pattern or modulator on `phaser.sweep` or `phaser.center` that touches 0 (the
  glide then heads for 1000 Hz for as long as the value is at or below 0).
- **Settle it**: `KatalystPhaserEffectSpec`: configure sweep 0 and read the `sweep` seam: 1000 today.
- **Confidence**: high.

### L7b-10 The docs of this layer say several things the code does not do

- **Severity**: note
- **Tag**: doc
- **Where and what**:
  - `audio/ref/effects-mixing.md:106`, `:107`: compressor `attack` and `release` "(ms)". They are seconds everywhere.
    `:124`: the delay is "Fixed-size" (rings are rented and grow) with "multi-tap mixing" (one tap, two during a
    crossfade); `:129`: feedback "(0-1)" (raw, above 1 by design).
  - `audio/ref/numerical-safety.md:107`: a NaN on the cylinder mix "latches the cylinder's effects for the rest of
    the playback". From the code, every stage of this layer but the duck recovers (L7b-01 and the table in Test
    truth). `:149`: "No outstanding gaps": the duck is one.
  - `Phaser.kt:98` to `101`: "patterning this knob ... steps the dry gain ... with no smoothing". Since 5c-9 the dry
    and wet coefficients ramp per sample (`Phaser.process`, `KatalystPhaserEffect.dryGlide`).
  - `Ducking.kt:216`, `217`: the 1 ms floor "matches `Compressor.updateCoefficients`", whose floor is 0.1 ms
    (`Compressor.MIN_TIME_SECONDS`).
  - `Compressor.kt:49`, `50`: deferred items "live in the user's memory at `~/.claude/projects/...`". The stone rule
    says memory lives in the repo.
  - `DelayLine.kt` class KDoc: "The ring buffer is therefore always bounded to +-1": to the cap. `DelayLine.cap`
    cites `ShapingFuncs.softCapTo`, whose rule for a cap at or below 0 is silence; the delay does not call it and
    reads such a cap as 1.0 (`DelayLine.kt:462`, `:509`). The door's KDoc names neither.
  - `Reverb.kt:423` ("a FINITE input can never drive the state non-finite") and `:174` ("allpass poison implies a
    poisoned comb cell first"): true below about 1e307 only, see Suspicions.
  - `Reverb.kt:655`: the bias figure, see the lead table.
  - `KatalystBuilders.limiter` and the `Compressor` stage KDoc: no line that a limiter without lookahead lets the
    first milliseconds through (lead 12 asked for it).
- **Confidence**: high.

### L7b-11 The body bank and the other inserts keep computing on exact silence while a later stage's tail holds the cylinder

- **Severity**: note
- **Tag**: performance
- **Where**: `audio_be/.../filters/ResonatorBank.kt:214` to `252` (`process`), and the same shape in `EqCore.process`,
  `Compressor.process`, `Phaser.process`.
- **What**: in the classic chain the body and the vowel run before the delay and the reverb. When the notes have
  ended, the cylinder stays active for the room's or the delay's tail, and the bank still runs its 16 bands on an
  input that is exactly 0.0 with states that `flushState` has brought to exactly 0.0 (the probe: `zero_s` 2.0 to
  3.5 s for a body). 58 ns per sample for nothing, for the length of every tail: at size 10 that is over ten
  seconds per phrase end.
- **Reach**: `body(...)` or `vowel(...)` with `reverb(...)` or `delay(...)` on one cylinder, the ordinary case.
- **Settle it**: count the bank's `runBand` calls over a render of one note with a body and a size 10 room.
- **Confidence**: high on the path; the saving is the table's 58 and 37 ns.

### L7b-12 The delay's ring passes through the subnormal range before it reaches zero

- **Severity**: note
- **Tag**: safety
- **Where**: `audio_be/.../effects/DelayLine.kt:604`, `:680`.
- **What**: lead 3's other half. The ring is the one recursive state in this layer with neither `flushState` nor a
  bias. On silent input it decays by `abs(feedback)` per lap; it is subnormal for the last `52 / log2(1/feedback)`
  laps before it underflows to 0 (about 31 laps at 0.3, 52 at 0.5, 340 at 0.9). It gets there only while the stage
  stays Active on an exactly silent mix, which needs a cylinder kept alive by something else for a long time
  (588 laps at feedback 0.3; with a short time and a low feedback, seconds). While it lasts, each sample costs a few
  subnormal operations in the delay and in whatever follows on the cylinder. The probe saw none in its 10 s windows.
  What 1.44e-21 itself costs downstream: nothing I can find (the same compares as for the reverb's bias).
- **Settle it**: a probe case with time 0.01, feedback 0.05 and 5 s of silence: the subnormal column goes non-zero.
- **Confidence**: high on the path, low on any real cost.

## Performance candidates

| Where | What | Rate | Measured | Expected gain | Risk to the sound |
|---|---|---|---|---|---|
| `ShapingFuncs.expClip`, `stompBox` | Return the saturated value when the argument is outside `fastExp`'s range instead of calling `pow` | per sample | 72.9 ns against 21.1 (Katalyst, amount 3) | about 50 ns at high drive | A SOUND decision, inaudible: the result changes by less than 2.4e-10 |
| `ResonatorBank.process` (and the eq, phaser) | Skip the block when the input block and every band state are exactly zero (L7b-11) | per block | body 58, vowel 37 ns | all of it, for every tail | None (a -0.0 input would stay -0.0) |
| `ResonatorBank.runBand` | Run a band's left and right in one loop, or a pair per pass | per sample | body 58, vowel 37 | perhaps 10 to 20 percent, a guess; needs the JS table | None, the same operations |
| `PhaserCore.prepare` | Both cores compute the same `alphaAt` twice per block (2 sin and 2 tan each): share one result between the channels and keep `alphaEnd` as the next `alphaStart` while center and sweep stand | per block | phaser 12.8 ns per sample in all | 3 of 4 sin and tan per block; the KDoc puts sin and tan at 50 to 150 ns on JS | None: the same inputs give the same bits |
| `Compressor.calculateGainReduction` | `1.0 / ratioValue - 1.0` (`:810`) and `kneeDb / 2.0` (`:822`) are computed per sample from fields; store them in `updateCoefficients` | per sample | compressor 23.9 ns | a division per sample if the JIT does not hoist it; unknown on V8 | None, bit for bit |
| `Compressor.lookaheadStep` | `boxASum / len`, `boxBSum / len`: multiply by a stored reciprocal | per sample | 38.1 and 32.8 ns | two divisions | Changes last bits, so the bit-identity rows move |
| `KatalystCompressorEffect.Off.process` (`:320`) | A lookahead stage that is off runs the whole detector and throws the gain away; switching on re-seeds the detector from the ring anyway (`:298`). A ring-only pass is equivalent | per sample | 38.1 ns | most of it, but only for a declared lookahead stage that is off, which is rare | None |
| `KatalystDelayEffect.Active.process` | Three passes over the block (scale the feed, `peakOf`, the line): fold the peak into the scaling pass | per sample | delay 25.2 ns | 1 to 2 ns | None |
| `Reverb.process` | Nothing to offer. The `ANTI_DENORMAL` exception stands | | 35.4 ns | | |

## Suspicions

- **A reverb fed a finite sample near 1e307 can hold an infinity in an all-pass for good.** The input guard rejects
  only non-finite samples. Eight comb outputs of 2.3e307 sum to an infinity, the all-pass stores it
  (`Reverb.kt:498`), `Inf * 0.5` never decays, and `-Inf + Inf` is NaN on the next visit. `combPeakAbs` scans combs
  only, on the stated ground that all-pass poison implies comb poison, which this path contradicts. No input I can
  name reaches 1e307 (the probe's 1e30 decays as designed), so this is a KDoc that over-claims, not a reach.
  Settle: a spec feeding 1e307 for 40 ms.
- **A modulated eq knob may render as back-to-back 50 ms crossfades between snapshot banks, each starting from zero
  state.** `KatalystEqEffect.configure` installs on every changed value and parks while a fade runs. I did not read
  `KatalystFilterSwap`, so I cannot say what a slow sweep of `eq.freq` sounds like. 7a's.
- **`Compressor.gainReductionDb` is a plain private function that takes four doubles per sample.** By the house rule
  on V8 that is a heap number per argument if it is not inlined. It is small and probably is. The JS table would show.
- **The comb lengths after scaling to 48 kHz share factors** (1694 and 1760 share 22, 1475 and 1760 share 5), more
  than at 44.1 kHz in places. Whether the room is more metallic at 48 kHz is for ears.
- **A relaxing compressor knob move with a very long `release` never lands** (`CompressorKnobMove.advance`: the
  fraction decays with the release, and the move lands only under 1e-7). Authored, I think, but the threshold then
  never reaches the value written.

## Decisions worth revisiting

- **What a non-finite side chain sample means to the duck**: a full duck for that sample (what an infinity is today,
  and the cheaper fix) or silence (what the compressor's detector does with both). L7b-01 needs one of them.
- **The SVF's top clamp at `fs/2 - 1`** (`clampSvfCutoff`). At that frequency a Butterworth section has a 0.22 s
  time constant at Nyquist and a q 200 section 64 s, which keeps a cylinder running for nothing. At 0.49 of the rate
  g is 32 instead of 15279 and the ring is gone, for a change of 2 percent in the highest cutoff. The Motor stays
  raw, so this is about where "the top" is, not about clamping an author.
- **The delay's soft cap acts on the feed too**, not only on what recirculates (`softCap(input + delayed * fb)`):
  at the default cap of 1.0 a feed above 0.95 is saturated in the echo even at feedback 0. The door describes the
  cap as "the level the recirculating signal saturates toward".
- **The compressor's level smoothing before the gain computer** (L7b-04), already listed as deferred in the file.
- **The duck's attack is per sample with no hold.** On a low, tonal side chain the gain follows the rectified wave
  down and recovers by `1 - exp(-T/2 / release)` between peaks: at 50 Hz and release 0.1 s that is 9.5 percent of
  the depth, a 100 Hz ripple on the ducked bus (at depth 1 the "muted" bus opens to 0.095 twice per cycle).

## Listening tasks

- **Vowel width** (L7b-02): a saw melody through `vowel("a")`, wet 1, floor 0, stepping by semitones. Listen for
  single notes that jump out or vanish. Compare with the third column divided into the frequency (q about 7 to 35).
- **Compressor after a gap** (L7b-04): a drum loop through `compressor()` at the defaults. Is the first 4 ms of each
  hit the punch you want, or a click before the clamp. The same with `limiter()` on a cylinder.
- **Duck on a bass side chain**: a held pad ducked at depth 1 by a 50 Hz sine. Listen for a 100 Hz buzz on the pad.
- **Reverb lowpass** (L7b-03): `reverb(lowpass = 200)` against unset. Is the darkest room dark enough.
- **The room at 48 kHz against 44.1 kHz**: a short click through size 10. Listen for a pitched, metallic ring.
- **The phaser at wet 0.5, floor 1**: it adds the wet on top (+3 dB) and its notches are 6 dB deep at most. Is that
  the phaser you expect at half depth.

## Test truth

Recovery from a bad sample, verified from the code for every recursive state of the layer (question 1):

| State | Guard | A NaN sample | An infinity |
|---|---|---|---|
| `Ducking.currentGain` | none | latches (L7b-01) | recovers |
| `Compressor.envelopeDb` (classic) | `finiteOrZero` on the level (`:641`) | read as silence | read as silence |
| Compressor lookahead: deque, slow and fast gain, boxes, the signal ring | `finiteOrZero` on the level and on both ring writes | read as silence, no bad output at all | the same |
| `DelayLine` ring | `softCap` then `nanGuard` on the store, both loops | stored as 0 | stored as the cap |
| Reverb combs, stores, all-passes | `finiteOrZero` on the two input taps | read as 0 | read as 0 |
| `PhaserCore.z1`, `lastOutput` | `finiteOrZero` on the input, `flushState` on every state | read as 0 | read as 0 |
| `ResonatorBank` and `EqCore` integrators | `flushState` on both | state zeroed, one bad output sample | the same |
| `DcBlocker` (distort) | `flushState` on `y`; `xPrev` holds the bad sample for one step | two bad samples | two |

Paths the stage probe's fixed knobs did not reach: the duck's gliding loop and its switch-off fade (same latch);
`takeOver` across a chain swap (carries the latch); the compressor's `processGliding` and its fade states (same
guards); the delay's crossfade loop (guarded like the plain one); a lookahead stage in Off and `reseedFromRing`;
the phaser below 36 kHz (L7b-05); samples near the double range in the reverb (Suspicions); a NaN in a KNOB (the
glides and setters drop it).

What the specs pin, as far as I read them:

- `DuckingSpec`: no row has a non-finite side chain sample. Every side chain level in it is 0.8 or more (0.1 only
  as the quieter channel), so the detector's slope is not pinned there: `SIDECHAIN_SENSITIVITY` 2.0 changed to 1.3
  would pass every row of that file. The thresholds are loose (`< 0.5`, `plusOrMinus 0.1`).
- `CompressorSpec`: the gain law is not pinned by this file. "reduces signal above threshold" asserts an average
  below the input level; "soft knee creates smooth transition" feeds 0.1 and asserts both averages below 0.15,
  which a compressor that does nothing passes. Deleting the knee arm of `gainReductionDb`, or changing its
  `2.0 * kneeDb` to `4.0 * kneeDb`, would stay green here. Whether `KatalystCompressorEffectSpec`,
  `CylinderCompressorSpec` or `LimiterLookaheadSpec` pin the curve against numbers I did not check; the effect spec's
  rows, by its KDoc, compare against a fresh bare `Compressor`, which pins nothing about the law.
- `ReverbNetworkLawSpec` pins the network bit for bit at fixed knobs, so the structure is held; nothing I saw pins
  what `lowpass` means in Hz (there is nothing to pin, L7b-03).
- `PhaserCoreLawSpec` and `PhaserFloorLawSpec` pin the kernel against its documented law at one sample rate.
- `DelayLineSpec` has the NaN row and the crossfade and ramp rows; no row for a cap at or below 0, none for the cost
  of `reset`.
- The vowel and body specs pin the switching and the NaN rule of the knobs; the tables' numbers are data.

## For the audit ruleset

- The probe tables were the best part of the brief. Being able to re-derive a table's number by hand from the code
  (the reverb's 9.1e-19, the 70.7 ms kink) is a strong check in both directions, and worth asking of every auditor.
- "Verify from the code why it recovers" produced a table, not a finding. Asking for that table outright, one row
  per recursive state, would make the layer reports comparable.
- Read-only without a calculator is a real limit for a DSP audit. Several findings here rest on arithmetic I would
  rather have run. Allow a scratch interpreter with no file access, or say that hand arithmetic is expected.
- The scope line between 7a and 7b (the stage's DSP against its switching) cut through most files. I reported two
  things that may be 7a's (L7b-08, the eq suspicion) rather than drop them; a ruleset should say which side reports
  a finding that sits on the line.
- The catalogues were worth a question of their own ("where do the numbers come from"). Data files do not get
  reviewed in a diff review, and L7b-02 has been there since the table arrived.
- I could not read all the specs. A brief that names the three or four specs that matter per layer would get a
  better test truth section than "its tests".
