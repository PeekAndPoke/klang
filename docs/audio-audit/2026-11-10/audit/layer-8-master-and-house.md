# Layer 8: the master and the house stage

**What I read** (in full unless noted)

- `audio_be/src/commonMain/kotlin/HouseStage.kt`, `_pcm16_edge.kt`, `PlaybackGain.kt`, `StereoBuffer.kt`,
  `KlangAudioRenderer.kt`, `PlaybackEngineDispatcher.kt`, `PlaybackEngine.kt`, `master/MasterBus.kt`.
- `audio_be/src/commonMain/kotlin/effects/Compressor.kt` (all of it; the house limiter is this class on its lookahead
  path), `filters/LowPassHighPassFilters.kt` (the `DcBlocker` and `HOUSE_DC_BLOCK_COEFF` only),
  `utils/numerical_safety.kt` (the helpers), `utils/fast_math.kt` (`fastExp` and its range only).
- `audio_bridge/src/commonMain/kotlin/constants/MasterLimiterDefaults.kt`.
- `audio_be/src/jvmMain/kotlin/JvmAudioBackend.kt`, `klang/src/jvmMain/kotlin/index_jvm.kt` (the audio thread part),
  `klang/src/jvmMain/kotlin/cli/WavFileWriter.kt`.
- `audio_jsworklet/src/jsMain/kotlin/KlangAudioWorklet.kt`, `init.kt`, `audio_be/src/jsMain/kotlin/JsAudioBackend.kt`,
  `WorkletContract.kt`.
- Skimmed: `WarmupRunner.kt` (the tick and the reset), `OfflineBackend.kt` (render), `KlangOfflineRenderer.kt` (the
  latency lines), `PlaybackClock.kt` and `KlangPatternScheduler.kt` (the latency consumers and the `SetGain` senders),
  `Cylinders.kt` (the mix-out line only).
- Specs: `HouseStageSpec`, `Pcm16EdgeSpec` (the table), `LimiterLookaheadSpec` (titles, the non-finite case in full),
  `PlaybackGainSpec`, `MasterDefaultsSyncSpec`, `KlangAudioRendererSpec`, `PlaybackEngineDispatcherOrderSpec` (titles).
- Docs: `audio/CLAUDE.md`, `audio/MEMORY.md` (the flow, Master, House stage, the clock), `audio/ref/effects-mixing.md`,
  `audio/ref/architecture.md`, `audio-constraints.md`, the probe leads and the tables' rows for this layer.

**What I did not**

- `ChainHost`, `ChainSwap`, `Crossfade`, `KatalystChain` and its stages (the master's chain machinery is the Katalyst
  layer's; I read only how `MasterBus` calls it). `TailRelease` (the first lines only). `_audio_context_wake.kt`,
  `JsAudioAnalyzer.kt`. The wire codec. The other master specs (`MasterBusTest`, `MasterOutputChainSpec` and so on).
- I ran nothing. Every duration below is computed by hand from the code's constants at 48 kHz, with the model stated,
  and is marked so. Each has a spec named that would settle it.

## The last line of defence: the answer

**What protects ears and speakers is the clip, and only the clip.** `clipSample` (`HouseStage.kt:163-171`) bounds every
output sample to `[-1, 1]`, and the limiter in front of it guarantees the clip never sees a non-finite value
(`Compressor.kt:604-605`, the ring stores `finiteOrZero`; the gain is built from a level that went through
`finiteOrZero` at `:706`, so it is finite and in `[0, 1]`). I found no path through `HouseStage.process` that writes a
sample outside `[-1, 1]` or a NaN. The Float32 copy (`KlangAudioWorklet.kt:192-201`) and `pcm16` keep that bound. So
the worst the engine can put out is digital full scale. It cannot put out more.

What the house stage does NOT do is keep a poison event short or keep it away from the rest of the mix:

| Input on the summed mix | What comes out (48 kHz, hand-computed) | State afterwards |
|---|---|---|
| a NaN sample | `DcBlocker` emits NaN for that sample and the next (`LowPassHighPassFilters.kt:467-471`: `out` is written raw, `xp = x` is kept raw, only `yc` is flushed); the limiter stores both as 0.0. Heard: two samples of exact zero, 5 ms later, hard edges. | clean two samples later. No latch. |
| an infinity sample | the same: `Inf`, then `x - Inf = -Inf`, both stored as 0.0. | clean two samples later. No latch. |
| one block (128) of NaN or infinity | 129 samples of exact zero (2.7 ms), hard edges on both sides, the gain untouched (the detector reads 0). | clean one sample after the block. |
| NaN on every block (one engine latched) | exact silence of the WHOLE mix, every playback, for as long as it lasts. No message. | clean two samples after it ends, as a hard step back in. |
| DC offset 1.0 (a step) | passes as 1.0 decaying with a 1000-sample time constant (21 ms); the limiter holds it at 0.896. Inaudible after about 0.15 s. | steady state: DC fully removed. |
| full-scale square | 0 dBFS is 1 dB over the threshold: -0.95 dB, out 0.896 (the probe's 0.896). At low pitch the DC blocker tilts the tops (55 Hz: the edge reaches about 1.35), and the limiter holds that too. | none. |
| ONE sample of 1e15 | +1.0 for one sample, then the DC blocker's own swing: `-1e15 + 0.999e15 = -1e12`, decaying at 417 dB/s. The 20:1 limiter leaves it above full scale, so the output sits on the -1.0 rail for about 0.46 to 0.53 s. | clean about 0.8 s after the sample. |
| one block of constant 1e15 | +1.0 for 128 samples, then -1.0 for about 0.55 to 0.63 s (the swing is -1.2e14), then a decay and the music fading back in. | clean about 0.9 to 1.0 s after the block. |
| constant 1e15 for good (`pow(-1)` on a silent bus) | +1.0 rail for about 0.60 to 0.67 s, then the DC is gone, and every playback's music is heard quantised to steps of 0.125. | never clean while it lasts (L8-02). |
| constant 1e30 (the analog SVF's latch) | rail for about 1.3 s, then exact silence of the whole mix for as long as it lasts. | the same. |

So: nothing latches in the house stage. The limiter's detector and gain survive NaN, infinity and any finite value. The
DC blockers recover from a non-finite sample in two samples. But they carry a HUGE FINITE sample for
`ln(value) / 0.001` samples (0.72 s for 1e15, 1.4 s for 1e30, 14.8 s for the largest double), and the clip turns that
tail into full-scale DC with the music gone. After one poison block of 1e15 the output is clean again after about one
second. Findings L8-01 and L8-02 give the lines.

## The probes' leads in this layer

| Lead | Verdict | Cause |
|---|---|---|
| Stage probe 7: `pow(-1)` on a silent bus is a constant 1e15: what does the house limiter do with it | CONFIRMED as a house-stage problem | The cylinder's mix joins the shared mix unguarded (`Cylinders.kt:161`, `StereoBuffer.addFrom`). The house DC blocker turns the step into a 0.72 s exponential (`LowPassHighPassFilters.kt:468`), the limiter at 20:1 leaves it above 0 dBFS (`Compressor.kt:709-710`), the clip rails. Then the constant absorbs the music (L8-01, L8-02). |
| Stage probe 6: an infinity leaves `times`/`div`/`pow`/tremolo as ONE sample of 1e15 | CONFIRMED as worse than the infinity itself at this stage | An infinity costs two zero samples here. The 1e15 it is turned into costs about half a second of full-scale DC (the table above). The upstream clamp makes the house stage's job harder, not easier. |
| Stage probe 2: analog SVF latched at 7.8e27 to 2e30 | NOT MINE (the latch); its effect here CONFIRMED | Above 2^53 (9e15) the constant absorbs every other signal completely: the whole mix is silent after the 1.3 s rail (L8-02). |
| Stage probe 1: duck latches to NaN for good | NOT MINE; effect here | Every playback is silent while that engine renders (the mix is shared, `PlaybackEngineDispatcher.kt:274-276`); `MasterBus.isAudible` reads NaN as silence (`MasterBus.kt:426`), so a stopped engine with a NaN master is disposed, which ends it. |
| Stage probe 12: the authored limiter passes a step at 1.00 | NOT MINE (Katalyst). At the house the 5 ms lookahead holds the same step at 0.896. | `Compressor.kt:591-611`. |
| Stage probe 13: values near 1e-38 at a Float edge | SUSPICION only | `DcBlocker` flushes its state, not its output (`:470-471`), so a tiny double reaches `toFloat()` unflushed. See Suspicions. |
| Stage probe 4: reverb emits 9e-19 for ever | NOT MINE; no effect here | The house detector takes the silence branch under 1e-10 (`Compressor.kt:708`); the master's keep-alive scans at 1e-5. |
| Click 4: "is the lookahead path's threshold glided?" | BY DESIGN for the authored limiter; does not apply to the house | `Compressor.processGliding` hands a lookahead instance to `process` on the knobs in force (`Compressor.kt:525-529`, KDoc `:520-522`): the step passes the hold, the release and the two boxes, about 5 ms. The house limiter's knobs are constants; no setter is ever called on it. |
| Stage cost 7: lookahead limiter 32.8 ns against 24.4 | CONFIRMED, small | One instance on the summed mix, not one per playback (`PlaybackEngineDispatcher.kt:56`, `:278`): about 0.16 percent of a core on the JVM. It also runs while nothing plays (performance table). |
| Hot-path lint: `PlaybackEngineDispatcher.kt:308`, `:321`, `:357` | CONFIRMED, periodic | L8-05. |
| Hot-path lint: `_pcm16_edge.kt:49`, `:59-62` byte types | BY DESIGN (the waiver is right) | The only callers are `JvmAudioBackend.kt:99` and `WavFileWriter.kt:80` (both JVM) and specs. `KlangOfflineRenderer` names it in a KDoc only. No Kotlin/JS render path reaches it. |
| Hot-path lint: `MasterBus.kt:382` `KatalystContext` | CONFIRMED, once per engine | Built in the first mastered render block of an engine, then cached (`:375-383`). |
| Framing | NOT MINE | The house stage keeps per-sample state only; nothing in it reads a block border. `PlaybackGain` ramps whole blocks by its stated law. |

## Findings

### L8-01 A huge finite sample on the mix becomes up to seconds of full-scale DC: the DC blockers carry it and the 20:1 limiter cannot bring it under the clip

- **Severity**: major
- **Tag**: safety, sound
- **Where**: `audio_be/src/commonMain/kotlin/filters/LowPassHighPassFilters.kt:461-475` (the carry),
  `audio_be/src/commonMain/kotlin/HouseStage.kt:103-104`, `:123-131` (the order),
  `audio_be/src/commonMain/kotlin/effects/Compressor.kt:706-710` (the static curve), `:734-747` (the release),
  `HouseStage.kt:163-171` (the clip).
- **What**: Input: the mix carries 1e15 on one block (128 frames), then 0. Step by step: (1) `DcBlocker`: `out = x - xp
  + 0.999 * yc`. Sample 0: 1e15. Samples 1 to 127: `1e15 * 0.999^k`, 0.88e15 at the end. Sample 128 (x back to 0):
  `0 - 1e15 + 0.999 * 0.88e15 = -1.2e14`. From there `yc` is a normal finite number, so `flushState` keeps it, and it
  decays by 0.999 per sample: -0.00869 dB per sample, 417 dB per second. (2) The limiter: 1.2e14 is +281.6 dBFS; the
  curve above the knee is `out = -1 + (in + 1) / 20` dB, so +13 dBFS. The gain is at most what the curve asks, so the
  delayed sample leaves at about 4.5. (3) `clipSample`: -1.0. The output stays on the rail until the DC blocker's tail
  has fallen to where the limiter brings it under 0 dBFS: +19 dBFS by the static curve, about +50 dBFS once the slow
  release (100 ms, `:734`) lags the rising gain. That is (281.6 - 50) / 417 to (281.6 - 19) / 417 = 0.55 to 0.63 s.
  (4) During all of it the gain is near 1e-13, so the music is gone; afterwards `slowGain` (about 4e-3 by then) climbs
  back with its 100 ms constant, about 0.3 s more. Expected of a safety stage: one bad block costs about one block.
  Actual: +1.0 for 2.7 ms, a 2.0 jump, -1.0 for more than half a second, then a fade in. One second in all.
  The general law: a value V rails for about `(dB(V) - 20..50) / 417` s per edge, and both edges count (the step up
  and the step down each start a tail). 1e30: 1.3 s. 1.8e308: 14.7 s. The DC blocker sits BEFORE the limiter by a
  deliberate decision (`HouseStage.kt:93-102`); that order is right for music and is what makes this case long.
- **Reach**: `Katalyst(k => k.pow(-1))` on a cylinder (probe row 619/621: a constant 1e15), `div(1e-20)`, an infinity
  through `times`/`div`/`pow`/tremolo (lead 6: one sample of 1e15, which is the 0.46 s case), the analog SVF latch
  (lead 2). All join the mix unguarded at `Cylinders.kt:161` and `PlaybackEngineDispatcher.kt:274-276`.
- **Settle it**: a `HouseStageSpec` case: 0.3 sine at 220 Hz, add 1e15 to one block, count output samples equal to
  +1.0 or -1.0 and the frames until the output is within 1 percent of the sine again. I expect about 27,000 rail
  samples and about 45,000 frames to recovery.
- **Confidence**: high for the mechanism (every line read), medium for the exact durations (hand-computed, the
  release lag estimated).

### L8-02 A huge constant on the mix absorbs every playback's music: steps of 0.125 at 1e15, exact silence above 9e15, and the DC blocker hides the cause

- **Severity**: major
- **Tag**: sound, safety
- **Where**: `audio_be/src/commonMain/kotlin/StereoBuffer.kt:39-49` (`addFrom`, the plain sum),
  `cylinders/Cylinders.kt:161`, `PlaybackEngineDispatcher.kt:274-276` (every engine into one mix),
  `LowPassHighPassFilters.kt:467-468` (the difference `x - xp`).
- **What**: Input: one cylinder holds a constant 1e15 (probe row 621), another cylinder or another playback plays
  music m in [-1, 1]. The sum `1e15 + m` is a double between 2^49 and 2^50, whose spacing is 0.125, so the sum is
  `1e15 + round(m to 0.125)`. After the rail of L8-01 has passed (0.6 to 0.7 s), the DC blocker removes the constant
  exactly (`x - xp`) and what is left is the music on a 17-level grid: about 4 bits, and every passage quieter than
  0.0625 is exact silence. With a constant above 2^53 (the SVF latch of lead 2 sits at 7.8e27 to 2e30, spacing
  1e12 or more) the music is absorbed completely: `x - xp` is 0, the output is exact zeros, for every playback, for
  as long as the constant lasts. Expected: one misbehaving cylinder is loud or rails; the rest keeps sounding.
  Actual: the rest is crushed or silent, the output meter shows a quiet or silent signal, and nothing reports why.
  When the constant ends, L8-01's rail follows once more.
- **Reach**: as L8-01, for any poison that stays (a stage that sounds on silence; a latched filter). Raw by the
  Motor's rule on the cylinder that asks for it. The finding is the collateral: other cylinders and other playbacks
  (a song under a tutorial example) share one mix and one house stage.
- **Settle it**: `HouseStage` fed `1e15 + 0.3 * sin` for 2 s: compare the last second with the sine. I expect the
  output to take only multiples of 0.125 minus a slow drift. Then the same with 1e17: I expect exact zeros.
- **Confidence**: high (IEEE arithmetic and four lines of code); the claim that the cylinder really holds the
  constant is the probe's, not traced by me.

### L8-03 An exception inside the render block ends the audio for good on both platforms, and nobody is told

- **Severity**: major
- **Tag**: safety, realtime
- **Where**: `audio_jsworklet/src/jsMain/kotlin/KlangAudioWorklet.kt:151-158`, `:178` (no `try` around the render),
  `audio_be/src/jsMain/kotlin/JsAudioBackend.kt:116-178` (no `onprocessorerror` on the node; `grep -rn processorerror`
  finds nothing in the repo), `audio_be/src/jvmMain/kotlin/JvmAudioBackend.kt:80-116` (the loop's `try` has only a
  `finally`), `PlaybackEngineDispatcher.kt:260-266` (the dispatcher's `finally` advances the clock and rethrows).
- **What**: Any exception thrown under `dispatcher.renderBlock` propagates out of `process()`. By the Web Audio
  specification a processor whose `process` throws is marked inactive: it outputs silence for the rest of its life
  and the node gets a `processorerror` event. Nothing listens for that event. The frontend sees only that the
  diagnostics stop: `PlaybackClock` runs at most its cap past the last report and then stands still. The user gets
  silence and a frozen highlight until a reload. On the JVM the same exception leaves the `while` loop, the `finally`
  drains and closes the line, and the backend coroutine fails. The dispatcher was written for a caller that survives
  (`renderBlock`'s `finally` says "an exception mid-block cannot leave now in the past"), but no caller does. Note
  the difference to the message handler: a throw in `port.onmessage` (`KlangAudioWorklet.kt:132-135`, for example
  the schema check's `error(...)`) loses that one command and the processor lives on.
- **Reach**: any throw in render code. I found none in this layer's own code. The engine rule forbids exceptions in
  hot paths, and this finding is what one costs when the rule is broken anywhere: `Compressor.kt:397-399` names one
  such path itself (`coerceIn` with an inverted range "throws IllegalArgumentException on the audio thread").
- **Settle it**: read the two call sites; or a worklet test (there are none, KLANG-00308 §2) that makes one voice
  throw and asserts the next block still renders and a feedback message went out.
- **Confidence**: high for the code (no catch, no listener); the browser behaviour is the specification's, not run.

### L8-04 One engine's non-finite output silences every playback, and its end is a hard step

- **Severity**: minor
- **Tag**: safety, sound
- **Where**: `PlaybackEngineDispatcher.kt:274-278`, `LowPassHighPassFilters.kt:467-471`, `Compressor.kt:604-605`,
  `:706`.
- **What**: Input: engine A's bus is NaN on every sample (the duck latch of lead 1, or any stage that latches), engine
  B plays music. `mix = A + B` is NaN. The DC blocker writes NaN, the limiter's ring stores 0.0, the output is exact
  zeros. The detector reads level 0, so the gain opens to 1. When A stops, the DC blocker restarts from zero state
  two samples later and B returns at full level in one sample: a click of the size of the signal. A single NaN sample
  is the small version: two zero samples with hard edges (a dropout click), not a fade. The house stage has no
  per-engine guard and no "poison seen" report, so the UI cannot say which playback did it.
- **Reach**: any stage that emits a non-finite value for more than a moment; by the probe run, the duck sidechain.
- **Settle it**: `PlaybackEngineDispatcherTest`: two engines, one with a bus forced to NaN; assert the other is heard.
  It will fail.
- **Confidence**: high.

### L8-05 The diagnostics message is built and posted on the audio thread fifty times a second, also on an idle backend

- **Severity**: minor
- **Tag**: realtime
- **Where**: `PlaybackEngineDispatcher.kt:292-336` (`:308` the list, `:321` the message, `:329` the warehouse
  snapshot), `:342-361` (`:357` one `CylinderState` per cylinder), `KlangAudioWorklet.kt:207-211` (encode and
  `postMessage` per feedback), `:53` and `PlaybackEngineDispatcher.kt:256`, `:293` (two `Date.now()` per block).
- **What**: Every 20 ms of audio (`DIAGNOSTICS_EVERY_SEC`, 7.5 blocks): an `ArrayList`, a `Diagnostics`, one
  `CylinderState` per cylinder of every rendering engine, then on the worklet the codec's envelope object and a
  structured clone. It runs whether or not anything plays (the comment at `:287-289` says so on purpose: it is the
  frontend's clock). Periodic, not per block, so it falls under the rule question the lint raised. Two smaller points
  in the same function: `Date.now()` has a 1 ms grain and a block is 2.67 ms, so `renderHeadroom` moves in steps of
  about 3.75 percent of a block per call before the average; and the two calls go through a `() -> Double` lambda,
  whose result is a heap number on V8 by the project's own rule (not measured).
- **Reach**: every backend, always.
- **Settle it**: the allocation check with no voice scheduled: bytes per block on an idle dispatcher.
- **Confidence**: high for the allocations; low for the heap numbers of `Date.now()`.

### L8-06 The 16-bit edge truncates toward zero and has no dither

- **Severity**: minor
- **Tag**: sound
- **Where**: `audio_be/src/commonMain/kotlin/_pcm16_edge.kt:33-41` (`(sample * PCM16_MAX).toInt()`).
- **What**: `toInt()` truncates toward zero, so the error always points at zero: a dead zone two counts wide around
  zero (everything in (-1/32767, 1/32767), under -90.3 dBFS, is 0), and an error of mean size half a count that
  follows the signal's sign, which is a square wave at the signal's own pitch near -96 dBFS plus the usual noise. A
  rounding quantiser has half the peak error and no sign term; with TPDF dither a tail fades into noise instead of
  cutting off. This is the law the engine had before the float output and the KDoc says it is kept bit for bit on
  purpose (`:23-28`), so it is a decision, listed here because the WAV writer is where published audio comes from.
  Also pinned and documented: -32767 is never written (a sample of exactly -1.0 goes to -32768, the next one up to
  -32766), and +1.0 is 32767 against -1.0 at -32768.
- **Reach**: the JVM player and every WAV render. Not the browser.
- **Settle it**: render a sine at -80 dBFS to WAV and look at the spectrum: odd harmonics that a dithered or rounded
  render does not have.
- **Confidence**: high.

### L8-07 The house DC pole is a fixed coefficient, so its corner moves with the sample rate, and the comment beside it is wrong

- **Severity**: minor
- **Tag**: consistency, doc
- **Where**: `LowPassHighPassFilters.kt:223-227` (`HOUSE_DC_BLOCK_COEFF = 0.999`), `HouseStage.kt:93`
  ("coefficient = 0.999 at 44.1k / ~7.6 Hz at 48k"), `:103-104`.
- **What**: The corner is `(1 - a) * fs / (2 * pi)`: 7.0 Hz at 44.1 kHz, 7.6 Hz at 48 kHz, 15.3 Hz at 96 kHz, 3.5 Hz
  at 22.05 kHz. The JVM backend offers 88.2 and 96 kHz (`klang/src/jvmMain/kotlin/index_jvm.kt:61`), the browser
  takes the configured rate. At 48 kHz a 40 Hz bass loses 0.15 dB and leads by 10.8 degrees (30 Hz: 0.27 dB, 14
  degrees); at 96 kHz the same note loses 0.6 dB. The limiter's times are in seconds; this one stage is in samples.
  The overshoot the comment at `HouseStage.kt:99-102` measured (+0.53 dB on a 55 Hz onset) changes with it. The
  comment at `:93` reads as if the coefficient were chosen per rate; it is one number.
- **Reach**: any device not at 48 kHz.
- **Settle it**: a spec that measures the -3 dB point of `HouseStage` at 44.1, 48 and 96 kHz.
- **Confidence**: high.

### L8-08 The output latency the UI's clock gets: the JVM reports none, and the browser leaves out an authored master's delay

- **Severity**: minor
- **Tag**: bug, consistency
- **Where**: `JsAudioBackend.kt:155-169` (`masterLat = HouseStage.HOUSE_LIMITER_LOOKAHEAD_SECONDS * 1000.0`),
  `PlaybackEngineDispatcher.kt:402-411` (`latencyFrames`: the house plus the longest master chain),
  `JvmAudioBackend.kt:68-73` (a 250 ms line buffer), `audio_bridge/.../infra/KlangCommLink.kt:275`
  (`outputLatencyMs` defaults to 0.0), `klang/src/commonMain/kotlin/PlaybackClock.kt:89`,
  `KlangPatternScheduler.kt:548`, `:568` (the consumers).
- **What**: The lookahead IS reported in the browser: base latency plus the guessed device latency plus 5 ms. Two
  gaps. (a) The dispatcher knows the true figure, `latencyFrames`, which includes a master chain with an authored
  lookahead (up to 50 ms) or an oversampling region; the browser side adds the house constant only, so with
  `master(Katalyst(k => k.limiter(lookahead = 0.05)))` every highlight fires 50 ms early. The audio side is
  uncompensated by decision; the visuals are a different question and nothing records a decision for them. The
  constant in seconds also differs from the real ring by up to one frame at 44.1 kHz (220 frames, not 220.5).
  (b) The JVM backend never fills `outputLatencyMs`, so it is 0.0, while the line holds up to 250 ms of rendered audio
  and the backend's timeline counts frames RENDERED: on the JVM the UI's time runs about 255 ms ahead of the sound.
  `line.open(format, bufferBytes)` may also grant another size than asked; `line.bufferSize` is never read.
- **Reach**: (a) any song with a latent master chain; (b) every JVM playback that shows time.
- **Settle it**: (a) a `Diagnostics` field carrying `dispatcher.latencyFrames`, asserted in `KlangPlaybackSyncSpec`;
  (b) print `outputLatencySec` on the JVM: it is 0.
- **Confidence**: high.

### L8-09 `PlaybackGain.retarget` ignores a new ramp time when the target is unchanged

- **Severity**: minor
- **Tag**: bug
- **Where**: `audio_be/src/commonMain/kotlin/PlaybackGain.kt:59-80` (`:71-73`).
- **What**: Input: `retarget(0.0, 2.0)` (a two-second fade out), then 0.5 s later `retarget(0.0, 0.05)` (the user wants
  it gone now). `span` is 19 blocks, not 0, so the `settle` branch is not taken; `gain == state[TARGET]` is true, so
  the call returns and the 2 s ramp runs on for 1.5 s more. The class KDoc says "a new target mid-ramp starts from
  where the gain IS and takes its full time again"; a same-target call with another time is neither a new target nor
  a no-op in the caller's eyes. Only `rampSec` 0 gets through (it settles before the comparison). `PlaybackGainSpec`
  has no case for it.
- **Reach**: `KlangPatternScheduler.setGain` (`:185`), whoever calls it twice with one target and two times.
- **Settle it**: the spec case above: assert the gain is 0 after 0.05 s.
- **Confidence**: high for the code; whether any caller does it today, not traced.

### L8-10 Dead and stale things at the edges

- **Severity**: note
- **Tag**: dead, doc
- **Where and what**:
  - `KlangAudioWorklet.kt:94`, `:162`: `isPlaying` is never written; the early return is unreachable.
  - `PlaybackEngineDispatcher.kt:272-273`: "Per-engine master gain in D6 will need a scratch buffer here": the bus and
    the gain exist since (`PlaybackEngine.kt:69`, `:109`).
  - `KlangAudioWorklet.kt:176-177`: names `KlangAudioRenderer` on the live path; it is the dispatcher.
  - `Compressor.kt:49-50`: points at `~/.claude/projects/.../memory/project_filter_review.md`, a home-directory memory
    file, against the stone rule that memory lives in the repo. A reader of the repo cannot follow it.
  - `audio/ref/effects-mixing.md:10-27`: the block is listed under `KlangAudioRenderer`, omits the DC blockers, the
    master bus, the playback gain and the 2 dB knee, and says "Master limiter" for what the code calls the house
    limiter. `audio/ref/architecture.md:57-61` has the DC blockers but also "master limiter", and no `PlaybackGain`
    in the flow; `:44` says the voice buffer is a `FloatArray`.
  - `HouseStage.kt:26-28`: "Behaviour is identical to the old inline post-chain" describes a state that is gone.
  - `KlangAudioWorklet.kt:172-173`, `:196`: with one output channel the right channel is dropped, not mixed down.
    `JsAudioBackend.kt:112-114` asks for two, so this is internal only.
- **Confidence**: high.

### L8-11 The offline render starts 5 ms late and the WAV length counter is an Int

- **Severity**: note
- **Tag**: consistency
- **Where**: `klang/src/commonMain/kotlin/KlangOfflineRenderer.kt:158-170`, `klang/src/jvmMain/kotlin/cli/WavFileWriter.kt:32`,
  `:82`, `:94`, `:100`.
- **What**: The render adds `latencyFrames` at the end so no music is cut, which is right, but the head is not
  trimmed: every WAV begins with 240 frames (220 at 44.1 kHz) of the limiter's silence, and with a latent master
  chain more. Two renders with different master chains are offset against each other by the difference. `dataBytes`
  is an `Int`: it wraps after 2 GB, about 3.1 hours of 48 kHz stereo, and the header would then carry a negative size.
- **Reach**: every WAV; the wrap only on a very long render.
- **Settle it**: read the first 300 frames of any render.
- **Confidence**: high.

## Performance candidates

| Where | What | Per | Measured cost | Expected gain | Risk to the sound |
|---|---|---|---|---|---|
| `Compressor.kt:708-710` (`lookaheadStep`) | `ln` on every sample above 1e-10, although the result is 1.0 for every level under the knee's start (-2 dBFS, 0.794). Compare the linear level against that constant first. | sample | the limiter stage is 32.8 ns on the JVM; `ln` is a large part of the 8.4 ns the lookahead path adds over the classic one, and of the classic path too | a few ns per sample on ordinary material, more on JS | none in principle (the curve returns exactly 0 dB there); the border sample may differ by an ulp of the comparison, so it wants a bit-identity run |
| `PlaybackEngineDispatcher.kt:278` | The house stage (two DC blockers, the limiter's deque and boxes, the clip) runs on every block, also with no engine and a drained ring. | sample, for ever | not in the table (the idle path has no `ln`, no `exp`) | the idle backend's whole cost in the browser, where the context runs while the page is open | low if the skip waits until the ring and the boxes hold only silence; the gain "settles a few ulps below 1.0" (`Compressor.kt:693-695`), so a skip must keep that state or accept an ulp |
| `PlaybackEngineDispatcher.kt:292-336` | Diagnostics built and posted 50 times a second while idle. | periodic | not measured | allocation and one `postMessage` per 20 ms | none; the frontend's clock runs on them, so idle would need a slower heartbeat, not none |
| `KlangAudioWorklet.kt:192-201` | `toFloat()` (`Math.fround`) before a store into a `Float32Array`, which rounds anyway. | sample | not measured | tiny (256 calls per block) | none |
| `PlaybackEngine.kt:211-213`, `MasterBus.kt:364-372` | A playback with a master chain runs the whole chain on silence while it is `Playing` (the output host never deactivates, plan risk R5, by design). | block | the chain's stages, for example reverb 35 ns per sample | the master's cost during rests | this is the decision that keeps a limiter envelope and a room across a gap; a change is a sound decision |
| `PlaybackGain.kt:99-107` | At a settled gain other than 1 (a song held at 0 under a code example) the engine renders everything and multiplies by the gain. | block | the whole engine | the muted song's cost | by design: the tails are measured before the gain (`PlaybackEngine.kt:133-135`) |

## Suspicions

- **Float subnormals at the worklet edge.** `DcBlocker` flushes `yc` but writes `out` raw (`LowPassHighPassFilters.kt:470-471`),
  and nothing after it flushes, so a double under 1.18e-38 becomes a subnormal `Float32` in Web Audio's graph (two
  analysers, a gain, the destination). Whether that costs anything depends on the browser's own denormal handling on
  its audio thread. What would settle it: a probe that logs the smallest non-zero `|out|` of a long tail at the
  dispatcher's output, and a profile of the analyser with such a buffer.
- **The box filters' running sums are never rebuilt** in the house limiter (`Compressor.kt:757`, `:764`: add the new,
  subtract the old, for the life of the backend). Rounding walks: I estimate under 1e-9 after twelve hours, so no
  sound. But after an L8-01 event the sums hold values 14 orders of magnitude apart, and what is left over when the
  small values leave again is a rounding residue of the large ones (about 1e-14 absolute by my estimate, so inaudible), which then stays in the sum for good.
  What would settle it: feed 1e15 for a block, then silence for a second, then read the gain: is it exactly what a
  fresh instance gives.
- **An engine created by `Cmd.SetGain` alone** (`PlaybackEngineDispatcher.kt:187-188`) is `Playing` with no voice and
  is never disposed unless a `Cleanup` follows. The frontend gates this (`KlangPatternScheduler.kt:170-187`), the
  backend does not. What would settle it: a dispatcher test sending `SetGain` to an unknown id, then rendering:
  `renderedEngineCountForTest` stays 1 for ever.
- **`contextFor`'s local function** (`KlangAudioWorklet.kt:103-149`) is said to allocate no closure per block. True if
  Kotlin/JS lifts a directly called local function; I did not read the generated bundle.

## Decisions worth revisiting

- **The house limiter is 20:1, not a brickwall, and the clip is hard.** By design, and the right order for music. The
  argument for one more look: this is the only stage between a raw Motor and the speakers, and its law leaves anything
  more than 19 dB over full scale to the clip. A second, final stage that is cheap and not a taste question would
  close L8-01 and L8-02 without touching the sound of any healthy mix: for example a per-engine check at the sum
  ("this engine's block peak is above 1e6 or not finite: drop the block, fade, tell the UI"), or the DC blocker's
  state held to a sane bound. The Motor stays raw; the house stage is not the Motor. Once, here, for the maintainer.
- **The DC blocker before the limiter** (`HouseStage.kt:93-102`) is what turns a one-sample event into a half-second
  one. The reasons given for the order are good. A guard on the blocker's input or state would keep the order and
  remove the tail.
- **Two playbacks sum without any level law** (`PlaybackEngineDispatcher.kt:274-276`): a song at full level plus a
  preview at full level is +6 dB into the limiter, which then takes about 5.7 dB off BOTH. `Cmd.SetGain` exists for
  the fade under an example. Whether an overlap should duck on its own is a product decision.
- **No dither and truncation at the 16-bit edge** (L8-06): kept for bit identity with the past. A WAV for publishing
  could round and dither without touching the live path.

## Listening tasks

- The house limiter under two playbacks: play a dense song and start a second one over it. Listen for the first one
  dipping and breathing (the dual release, 100 ms and 10 ms).
- The 5 ms attack on a hard kick over a sustained pad at a level that drives the limiter 6 dB: is the pre-duck (the
  gain closing before the hit) heard as a flinch.
- A low bass note (E1, 41 Hz) through the house stage against a bypass: the 7.6 Hz pole's phase lead and the +0.5 dB
  onset overshoot the code names.
- A mix driven 20 dB too hot: where the limiter hands over to the clip, is the change of character acceptable or
  should the limiter's law reach further.
- A long reverb tail rendered to WAV, played loud: the cut-off at -90 dBFS and the grain above it (L8-06).

## Test truth

- **Pinned well**: the clip's three branches and the NaN case (`Pcm16EdgeSpec`'s table), the 16-bit law against the
  retired rule, the byte layout; the limiter's ceiling at every smoothing length, two peaks in one window, the falling
  tone, the reset, a single infinity; the reported latency against a real impulse (`HouseStageSpec`); the gain ramp's
  landing, slope and law.
- **Not pinned anywhere in this layer**:
  - a huge FINITE value through `HouseStage` (L8-01, L8-02): every poison spec uses NaN or infinity, which are the
    harmless cases here;
  - how long the output takes to be clean after any poison;
  - two engines summing (level, and one poisoned engine against a healthy one);
  - the worklet: no test at all (the copy into `Float32Array`, the channel count, the warmup branch, an exception);
  - the JVM loop;
  - `PlaybackGain` with the same target and a new time (L8-09);
  - a swap at the master under the click detector (the probe says "not measured").
- **Mutations I expect to survive this layer's own specs** (the corpus renders may still catch some):
  - delete `dcBlockerR.process` (`HouseStage.kt:124`): `HouseStageSpec`'s DC case reads the left channel only;
  - `HOUSE_DC_BLOCK_COEFF` 0.999 to 0.995: no spec measures the house corner;
  - drop the two `dcBlocker*.reset()` in `HouseStage.reset` (`:112-113`): the reset spec looks at the ring;
  - in the worklet, write `renderBuffer.left` into both outputs;
  - in `emitDiagnostics`, report `cursorFrame` in place of `blockEndFrame` only where no sync spec runs (the sync
    spec should catch it; I did not check);
  - `PlaybackGain.retarget`: remove the `gain == state[TARGET]` return (`:71-73`): no case depends on it.

## For the audit ruleset

- The brief's poison list (1e15, NaN, infinity, DC, square) was the most useful part: the existing specs test only
  the non-finite cases, and the huge finite one is where the stage is weak. A ruleset should always name "huge but
  finite" apart from "non-finite".
- "No Gradle" and a short list of allowed commands left every duration here hand-computed. A small allowed scratch
  model (a script outside the repo) would have turned three "medium" confidences into "high" at no risk to the tree.
- Asking for the answer to one named question "plainly, with the lines" forced a table of input against output; that
  format found L8-02, which no single-file reading would have.
- The layer borders need one line each on shared classes: `Compressor` is the house limiter and also a Katalyst
  stage, and it was not clear whose it is. I read all of it and reported only what the house path runs.
- The probe leads were worth having, but all of them stop at a stage's own output. A lead should say what the NEXT
  stage does with the value; here that was the whole finding.
- The platform edges have no specs and no probe; an audit can only read them. The ruleset should say whether a
  browser run is ever part of an audit.
