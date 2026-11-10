# Disproof: safety

**What I ran.** Two reproduction specs, both gated by `KLANG_PROBE=audit` (disabled in a normal run), measuring and
asserting nothing about the engine:

- `audio_be/src/jvmTest/kotlin/audit/AuditSafetyRepro.kt`, 18 tests. Final run:
  `KLANG_PROBE=audit console/with-build-lock.sh python3 console/tools/test-digest/test_digest.py run ... --expect AuditSafetyRepro -- ./gradlew :audio_be:jvmTest --rerun --tests io.peekandpoke.klang.audio_be.audit.AuditSafetyRepro`.
  test-digest: "AuditSafetyRepro RAN 18 tests, 0 failed (:audio_be:jvmTest) ... exit 0". The lines are in
  `.build-claude/audio_be/probes/audit-safety.txt` (221 lines); the final run's text is identical to the run before it
  (the renders are deterministic).
- `sprudel/src/jvmTest/kotlin/audit/AuditSafetySprudelRepro.kt`, 3 tests: what a compiled pattern puts on the wire
  (`SprudelPattern.compile`, then `toVoiceData()` on every event, the call `KlangPatternScheduler.kt:677` makes) and
  end-to-end renders through `KlangOfflineRenderer` (the real scheduler, wire, dispatcher and house stage). Final run:
  the same command with `:sprudel:jvmTest` and `--expect AuditSafetySprudelRepro`; test-digest: "RAN 3 tests, 0 failed
  ... exit 0". Lines in `.build-claude/sprudel/probes/audit-safety-sprudel.txt`.
- A model of the saturating SVF loop in plain Python (the code's equations copied, in my scratchpad), used only to map
  where new finding N1 occurs before measuring it in the engine. Every N1 number below is from the engine.

Eight Gradle runs in all, each through the lock: four of audio_be (runs 1, 3, 5, 8) and four of sprudel (runs 2, 4,
6, 7), each adding rows as the measurements raised new questions. One incident: the scratchpad is shared by the
parallel challengers, and my run 2 log was overwritten by another challenger's run of the same file name; that run's verdict comes from its JUnit XML (3 tests,
0 failures, 13:13:43Z), and every number quoted here is from a later run whose test-digest verdict I read.

**What I only read.** The hosts: `KlangAudioWorklet.kt`, `JsAudioBackend.kt`, `JvmAudioBackend.kt`, the backend launch
in `KlangPlayer.kt:181-199`, and the Web Audio rule for a `process()` that throws (by the specification, not run in a
browser). The frontend paths that make start times and the epoch (L1-04, L1-05 reach). The stage-probe rows that L4-08
cites (`docs/audio-audit/2026-11-10/stage-probe.txt`).

## Verdicts

| id | auditor | verdict | now | one line |
|----|---------|---------|-----|----------|
| L1-01 | major | CONFIRMED | major | `pan("NaN")`, `pan(Math.sqrt(-1))`, `pan(pow(-1)).pan(mul(0))` all put NaN on the wire; the whole output of every playback is exact zero while the voice lives |
| L6-02 | major | CONFIRMED | major | same defect; its "0 divided by 0" route is wrong (`div` returns null for a zero divisor), the other routes stand |
| L1-02 | major | CONFIRMED, LARGER | major | a NaN pitch on a sample voice measured as described; the Karplus family (`pluck`, `string`, `superpluck`, `ks`) does the same, so `note("NaN").s("pluck")` silences everything too |
| L6-01 | major | CONFIRMED | major | measured end to end: no guard between a voice and the house limiter; the duck turns it into silence of everything |
| L7b-01 | critical | CONFIRMED | critical | one NaN on a duck's source silences every playback until the ducked cylinder's notes end; a swap onto a ducking chain carries it, onto a plain chain ends it; the fix is right |
| L8-04 | minor | CONFIRMED, SMALLER | minor | every playback is silenced (measured); the "hard step at its end" is wrong: the DC blocker restarts from zero state, the music re-enters with a 21 ms DC transient |
| L6-07 | minor | CONFIRMED, SMALLER | minor | a voice NaN from frame 0 lives to its end frame (measured); NaN mid-gate or in the release is culled, so two cases of three are bounded, not one |
| L4-01 | major | CONFIRMED, SMALLER | minor | the hold law matches the engine within 3 percent, but no ordinary signal reaches the slow region: it takes a transient 60 to 80 dB over full scale |
| L8-01 | major | CONFIRMED, SMALLER | minor | numbers confirmed (1e15 for one block: 26,783 rail samples, clean after 1.13 s); only extreme raw values put such a block on the mix |
| L8-02 | major | CONFIRMED, SMALLER | minor | 1e15 crushes every playback's music (error -15.4 dB), 1e17 gives exact zeros; same reach as L8-01 |
| L7a-03 | minor | CONFIRMED | minor | `pow(-1)` and `plus(0.2)` hold full level 20.36 s after the edit, above 1e-6 until 24.56 s |
| L4-11 | minor | CONFIRMED | minor | `drive` 257 and up: NaN on every exact-zero input sample (silence), infinity elsewhere; 256 is finite |
| L4-06 | minor | CONFIRMED | minor | one NaN in, two NaN out (frames 4800 and 13599, 0.183 s later) |
| L4-07 | minor | CONFIRMED | minor | one infinity in, `factor` infinities out (4 and 4800) |
| L4-08 | minor | CONFIRMED | minor | the rows I measured or read in the probe table match |
| L8-03 | major | CONFIRMED | major | no catch on either host; a throw from wire-legal song input now exists (L2-04) |
| L9-01 | major | CONFIRMED | major | same; the throw leaves `renderBlock` (measured), and the throwing voice stays in the active list |
| L2-04 | minor | CONFIRMED, LARGER | major | a crash of all audio from a pattern: `note("c3*8").s("saw").pregain(0).legato(-0.001).adsr(release = 0)` throws `IllegalArgumentException` out of the render |
| L1-04 | minor | CONFIRMED | minor | measured as described; no frontend path makes a NaN start |
| L1-05 | minor | CONFIRMED | minor | measured as described; internal only |

Counts: CONFIRMED 13, CONFIRMED LARGER 2, CONFIRMED SMALLER 5, DISPROVED 0, BY DESIGN 0, UNDECIDED 0.

## The key question: what a pattern puts on the wire

Measured (`AuditSafetySprudelRepro`, test 1; every row is one compiled pattern, cycle 0, through `toVoiceData()`):

| pattern | wire |
|---------|------|
| `note("c3").pan("NaN")` | pan NaN |
| `note("c3").pan("Infinity")` | pan Infinity (harmless: `coerceIn` gives 1) |
| `note("c3").pan("nan")` | pan unset (only the exact spelling parses, `Tones.kt:40`, `lang_helpers.kt:107-112`) |
| `note("c3").pan(Math.sqrt(-1))`, `note("c3").pan((-1) ** 0.5)` | pan NaN |
| `note("c3").pan("0").pan(div(0))` | pan 0.0: `SprudelVoiceValue.div` returns null for a zero divisor (`SprudelVoiceValue.kt:55`), so the field stays |
| `note("c3").pan("0 0.5".div(0))` | pan unset |
| `note("c3").pan("0").pan(pow(-1)).pan(mul(0))` | pan NaN (Infinity times 0; no NaN literal anywhere) |
| `note("c3").pan("-1").pan(pow(0.5))`, `.pan(log2())`, `pan(sine.range(-1, 0).pow(0.5))` | pan NaN |
| `note("c3").pan(0/0)` | does not compile: KlangScript refuses a division by zero |
| `s("bd").freq("NaN")`, `s("bd").freq(Math.sqrt(-1))`, `note("NaN")` | freqHz NaN |
| `note("-Infinity")`, `s("bd").freq(0)`, `note("c3").freq(mul(0))` | freqHz 0.0 |
| `note(-2000)` | freqHz 5.5e-50 |
| `note("c3").freq(div(0))`, `n("NaN").scale("c3:major")` | unchanged, finite |

So a NaN `pan` and a NaN `freqHz` reach the engine from a string atom, from the script's own math (`Math.sqrt`, `**`)
and from pattern arithmetic (`pow` of a negative value, `log2` of a negative value, infinity times 0). The sprudel
setters write them raw (`lang_dynamics_level.kt:258`, `lang_tonal_note.kt:362`), `toVoiceData` passes them
(`SprudelVoiceData.kt:915`, `:932`), and on the JVM the scheduler hands the same `VoiceData` to the dispatcher. The
browser crosses a structured clone, which keeps a NaN double; by reading only, not run in a browser.

## The duck, end to end

Measured (`AuditSafetyRepro`, test 1: the live dispatcher with its house stage, two playbacks; and the sprudel songs).
The song: a 110 Hz sine on cylinder 1 (onset 0.5 s, gate 0.1 s) with pan NaN; a 55 Hz saw bass on cylinder 0, a note
every 0.25 s, ducking from cylinder 1 at depth 0.8; a second playback plays a 440 Hz sine from 0 to 6 s.

- **What leaves the engine**: exact zeros on both channels, no NaN, no rail (the house limiter's ring stores the NaN as
  0.0, `Compressor.kt:604-605`). The second playback is silenced with it.
- **From when**: 0.505 s, the kick's onset plus the house limiter's 5 ms lookahead.
- **Until**: 3.064 s with the bass playing to 3.05 s (cylinder 0's NaN ends at 3.056 s, its last active block); 1.565 s
  with the bass stopping at 1.55 s; 1.554 s when the bass notes after 1.5 s carry no duck slots (the 50 ms weight
  fade); with nobody ducking, only the kick's own life (0.15 s). Two cylinders ducking from cylinder 1 both stay NaN
  until their own notes end (3.056 s and 3.059 s) while cylinder 1 itself is clean from 0.651 s on.
- **From a pattern**: `stack(note("c4*4").s("sine").cylinder(1).pan("<0.5 [0.5 0.5 NaN 0.5] 0.5 0.5>"),
  note("c2*8").s("saw").gain(0.5).duck(1, 0.8, 0.2))`, 4 cycles at cps 0.5: exact zero from 3.005 s to the end of the
  render (9.005 s); the control with pan 0.5 has none. With the bass stopping at 4 s: zero from 3.005 to 4.061 s, then
  the kicks are heard again. With the duck's depth falling to 0 at 4 s: zero until 4.056 s. With no NaN literal at all
  (`pan("0").pan(pow(-1)).pan(mul(0))` on that one kick): the same 6 s of silence.
- **What ends it**: the ducked cylinder's last voice (the cylinder deactivates, which resets the duck; `isMixBufferSilent`
  reads the NaN mix as silent, `Cylinder.kt:706-717`), the owner switching the duck off or its depth reaching 0 (the
  weight fade, then `KatalystDuckEffect.endLife`), or a chain swap onto a chain without a duck. Not a swap onto another
  ducking chain.

## Per finding

### L1-01

- **Verdict**: CONFIRMED.
- **What I checked**: `VoiceFactory.kt:363` (`pan = data.pan ?: 0.5`, the only reader of `data.pan` in `audio_be`);
  `voice_channel.kt:48-49`, `:86-87`, `:96-101`, `:127-136`. `coerceIn` returns a NaN unchanged. The rule it breaks is
  stated in `audio/ref/numerical-safety.md`, "Non-finite inputs" (a non-finite value a pattern writes reads as unset);
  `pan` is missing from that page's list of readers.
- **What I measured**: the wire table above. `AuditSafetySprudelRepro`, song A (`note("c3").s("sine").pan("NaN")`):
  the output is exact zero for the whole 2 s note and its release (2.905 s of zeros from 0.1 s; the control has RMS
  0.49 to 0.50 per half second). Dispatcher row "NaN pan, no duck at all": exact zeros for the kick's 0.15 s life, and
  the second playback with them.
- **Reach**: wider than the auditor's: the string atom, `Math.sqrt(-1)`, `**`, and pattern arithmetic without any NaN
  literal (`pow(-1)` then `mul(0)`, `pow(0.5)` or `log2()` of a negative pan).
- **On the proposed fix**: right. The factory is the one place (the realtime path builds through the same
  `makeVoice`), and `VoiceGainWireSpec`'s NaN rows are the pattern for the guard.
- **Severity now**: major.

### L6-02

- **Verdict**: CONFIRMED (one of its routes DISPROVED).
- **What I checked**: as L1-01. Its reach line names "0 divided by 0 in `SprudelVoiceValue.div`": `div` returns null for
  a zero divisor (`SprudelVoiceValue.kt:52-56`), and the mapper path leaves the field unchanged on null
  (`SprudelPattern.kt`, `_mapNumericField`).
- **What I measured**: `pan("0").pan(div(0))` arrives as 0.0, `pan("0 0.5".div(0))` as unset; the other routes as L1-01.
  The cull does not see a NaN pan: the peak is measured before the pan (`voice_channel.kt:61-65`), so the voice lives
  its full length (song A: the whole note).
- **Reach**: as L1-01.
- **On the proposed fix**: as L1-01; this finding duplicates it.
- **Severity now**: major.

### L1-02

- **Verdict**: CONFIRMED, LARGER.
- **What I checked**: `VoiceFactory.kt:177-181` (the ratio's `coerceIn` keeps a NaN); `SampleIgnitor.kt:87-110` (frame 0
  reads `pcm[0]`, then the playhead is NaN, `NaN.toInt()` is 0, `frac` is NaN). For the oscillators: `VoiceFactory.kt:144`
  hands `freqHz ?: 0.0` to the tree; `KarplusString.kt:183` (`(sampleRate / freqHz).coerceIn(2.0, 2499.0)` is NaN for a
  NaN pitch).
- **What I measured**: dispatcher, a 1 s sine sample at pitch 261.63 Hz: `freqHz` NaN gives NaN in cylinder 0's mix from
  0.099 to 0.552 s (the voice's whole life) and an exact-zero output for all 18,720 frames of 0.11 to 0.5 s; pitchHz 0
  with freqHz 0 the same; +Inf is fine (ratio 32); freqHz 0 on a normal sample is fine (ratio 1/32, peak 0.26); 440 Hz
  on a pitch-0 sample is fine. Every built-in oscillator with `freqHz` NaN: NaN in the mix from exactly four, `ks`,
  `pluck`, `string`, `superpluck` (2,560 NaN samples, every sample of the 20 blocks); none at +Inf, 0 or -220 Hz.
  End to end, `note("NaN").s("pluck")`: exact zero for the whole render. So `audio/MEMORY.md`'s statement that the
  sources gate a non-finite frequency to silence does not hold for the Karplus family.
- **Reach**: `s("bd").freq("NaN")`, `s("bd").freq(Math.sqrt(-1))`, `note("NaN").s(<any sample>)`, `note("NaN").s("pluck")`.
  The pitch-0 sample needs an index entry with a 0 Hz pitch (frontend data, internal).
- **On the proposed fix**: incomplete. A guard on the sample branch alone leaves the Karplus family; the factory's two
  reads of `data.freqHz` (`VoiceFactory.kt:112` and `:178`) are the place for all of them, plus a guard for a zero or
  non-finite `entry.pitchHz` in the ratio.
- **Severity now**: major.

### L6-01

- **Verdict**: CONFIRMED.
- **What I checked**: every step of its trace: `voice_channel.kt:89-102`; `Cylinders.kt:147-153` (the duck pass reads the
  source cylinder's mix in place), `:156-164` (`addFrom`); `Ducking.kt:134-145`; `Cylinder.kt:706-717`;
  `HouseStage.kt:121-132`; `Compressor.kt:604-605`; the DC blocker's state flush (`LowPassHighPassFilters.kt:461-475`,
  `numerical_safety.kt:91-95`: `flushState` maps NaN to 0.0, so the blocker does not latch).
- **What I measured**: the section "The duck, end to end". One line of its claim to correct: "without a duck the same
  NaN costs one or two zero samples" holds for ONE NaN sample (measured on the house stage alone: one NaN block gives
  129 zero frames and is clean 0.086 s later); a NaN pan costs the voice's whole life.
- **Reach**: the duck's source cylinder carrying one NaN: the pattern routes of L1-01 and L1-02, `drive` from 257 (L4-11),
  and the finite extreme knob values of N2. The `Inf * 0` route through the channel (an infinite voice sample on a
  hard-left pan) is IEEE arithmetic, read, not measured.
- **On the proposed fix**: the duck fix (L7b-01) ends the latch, the pan and pitch guards (L1-01, L1-02) close the
  measured pattern routes; what remains is the extreme-value sources of N2.
- **Severity now**: major.

### L7b-01

- **Verdict**: CONFIRMED.
- **What I checked**: `Ducking.kt:134-145` (the release arm stores the NaN), `:161`, `:206`; `KatalystDuckEffect.kt:178-207`
  (`takeOver` moves the instance; the finding cites `:197`, a comment line inside it), `:243` and `:390-396` (the
  switch-off fade, then `endLife`); `Cylinder.kt:863-893` (`handOverDuck`: a ducking arriving chain takes the envelope
  over, a plain one fades the leaving duck out); `Crossfade.kt:179-180` (that fade passes both taps through
  `finiteOrZero`).
- **What I measured**: CylinderSwapRig at 48 kHz: one NaN sidechain block leaves 40 of the next 40 blocks NaN; a swap
  onto another ducking chain is still NaN 600 blocks (1.6 s) after the request: the latch carries over. A swap onto a
  chain without a duck is finite from the first block after the request: the blend's sterilised taps end it at once.
  The crossing of cylinders and what ends the latch: section "The duck, end to end".
- **Reach**: as L6-01. The damage is the whole backend's output (every playback) for as long as the ducked cylinder plays;
  in a song whose bass loops, until the user stops it.
- **On the proposed fix**: right and complete for the latch. `if (x < 1.0) x else 1.0` equals `min(1.0, x)` for every
  non-NaN `x >= 0` including +Inf, so every render without a NaN stays bit-identical. The other inputs of `currentGain`
  are finite already: a non-finite depth switches the stage off at the writer (`KatalystSlotWriters.kt:591-595`), the
  constructor coerces an infinite one, and `calculateCoefficient` substitutes a non-finite release. It does not stop the
  source cylinder's own NaN reaching the mix; that needs L1-01's guard.
- **Severity now**: critical.

### L8-04

- **Verdict**: CONFIRMED, SMALLER.
- **What I checked**: `PlaybackEngineDispatcher.kt:269-283` (every engine renders into one mix at `:275`); the DC blocker (`LowPassHighPassFilters.kt:461-475`): after a
  NaN, `yc` is 0.0 (`flushState`) and `xp` is the first finite sample, so the next output is a first difference and the
  music returns with a DC transient of 1,000 samples (21 ms); the limiter's detector read 0 during the NaN, so its gain
  is open.
- **What I measured**: every playback silenced (the second playback's 440 Hz sine is exact zero for the whole latch).
  The largest first difference in the two blocks after each silence was 0.0122 to 0.0124, which is the 440 Hz sine's own
  slope (0.21 times 2 pi 440 / 48000 = 0.0121); in the one row where it was 0.5665 the bass saw resumed and that is the
  saw's own reset jump. So the END is soft. The START is the hard edge: the music drops to exact zero in one sample.
- **Reach**: as L6-01.
- **On the proposed fix**: none proposed; its "Settle it" spec would fail as it predicts.
- **Severity now**: minor.

### L6-07

- **Verdict**: CONFIRMED, SMALLER.
- **What I checked**: `voice_channel.kt:163-169`, `Voice.kt:204` (the `heard` latch), `:433`, `:475-486`.
- **What I measured**: gate 0.05 s, a 5 s tail, the default cull (0.05 s): NaN from frame 0 lives to 5.053 s (its end
  frame), not culled; 0.5 then NaN from mid-gate: culled at 0.101 s; NaN from 0.1 s into the release: culled at
  0.203 s; the controls (exact 0 from 0.1 s into the release: culled at 0.203 s; exact 0 from frame 0: lives to its
  end). The auditor's "bounded in only one of three cases" is wrong: two of three are culled, the mid-gate one at the
  release plus the window. The NaN-from-the-start voice behaves exactly like a never-heard silent voice, which the
  `heard` latch keeps on purpose.
- **Reach**: a voice whose tree is NaN from its first sample (L1-02's Karplus rows, `drive` 257 on a silent start).
- **On the proposed fix**: the comment is the thing to fix ("a voice never heard is never culled, a NaN one included").
- **Severity now**: minor.

### L4-01

- **Verdict**: CONFIRMED, SMALLER.
- **What I checked**: `IgnitorFilters.kt:243-250`, `:272-279`; `LowPassHighPassFilters.kt:203-205`, `:213`;
  `FilterHumanizationDefaults.kt:72`.
- **What I measured**: lowpass q 0.707, one sample of V then silence, frames until the output stays under 1e-3. At 1 kHz,
  analog 1: V = 1e3: 95 frames (the linear filter: 127); 1e4: 5,243 frames (0.11 s); 2e4: 81,730 (1.70 s); 3e4: 413,000
  (8.60 s); 1e5: not settled in 120 s. The first sample sets the state to about 0.12 V, so the auditor's law
  (state^4 / 4.0e8 samples) predicts 5,100, 81,600 and 413,000: it holds within 3 percent. At 8 kHz, analog 1: 1e3: 1,865
  frames; 1e4 and up: not settled in 120 s. Analog 0 settles in 166 frames or less at every V.
  The reach, which no auditor measured: 1 s of a sine at the cutoff or a naive saw at a third of it, levels 1, 4 and 16,
  q 0.707 to 200, cutoffs 1, 8 and 16 kHz, analog 1, 5 and 15, both modes (432 analog rows): no analog tail is longer
  than its linear twin by more than 0.1 ms (the saturation mostly shortens them, for example 0.80 s against 1.07 s at
  1 kHz, q 200), except the 24 rows at 16 kHz, q 200, analog 5 and 15, which never settle: new finding N1, a different
  mechanism. A sustained loud signal cannot reach the slow region because the damping it adds keeps the state small (by the model: about 20 at level 1, 80 at level 1,000); only a transient 60 to
  80 dB over full scale does (an impulse of 1e4 at 1 kHz), or a 1e15 sample from a `safeOut` clamp upstream.
- **Reach**: a transient of 1e4 or more into an analog lowpass or highpass. No built-in source with door values makes one
  (N2's battery).
- **On the proposed fix**: its "Settle it" spec (a 10 ms DC step of 1e4 at 1 kHz, analog 1, under 1e-3 after 1 s) is
  GREEN today: the step settles in 4,765 frames (0.099 s). A spec that shows the hold: one sample of 3e4 (8.6 s) or 1e5.
- **Severity now**: minor.

### L8-01

- **Verdict**: CONFIRMED, SMALLER.
- **What I checked**: `HouseStage.kt:93-104`, `:121-132`; `LowPassHighPassFilters.kt:461-475`; `Compressor.kt:706-710`;
  `HouseStage.kt:163-171`.
- **What I measured**: the house stage alone, a 0.3 sine at 220 Hz, one block (128 frames) of sine plus V at frame 4800,
  against the clean run. V = 1e15: 26,783 rail samples (the auditor: about 27,000), back within 0.003 of the clean run
  1.13 s after the first deviation (the auditor: about 45,000 frames, one second); 1e6: 7,276 rail samples, 0.69 s; 100:
  128 rail samples (the block itself), 0.41 s; 10: 27, 0.23 s; 2: none, 0.16 s; NaN or +Inf: 129 exact zeros, 0.086 s.
- **Reach**: a huge DC-like block on the mix. Every source I found or measured is an extreme raw value: a Katalyst `pow`
  at 0 or below on silence, `div` by a tiny constant, `plus` of a huge constant, an infinity turned into 1e15 by
  `safeOut`, the analog SVF hold of L4-01. The output stays bounded at full scale and recovers in about a second.
- **On the proposed fix**: none proposed; its "Settle it" numbers are confirmed.
- **Severity now**: minor.

### L8-02

- **Verdict**: CONFIRMED, SMALLER.
- **What I checked**: `StereoBuffer.kt:39-49`, `Cylinders.kt:161`, `PlaybackEngineDispatcher.kt:274-276`,
  `LowPassHighPassFilters.kt:467-468`.
- **What I measured**: a 0.3 sine plus a constant C from 0.5 s on, read over 3 to 4 s: C = 1e15: 28,519 rail samples
  (0.59 s), then the music with an error of -15.4 dB re the music (heavily crushed); C = 1e17: exact zeros for all 48,000
  frames of the last second; C = 1e6: an error of -196 dB after a 0.2 s rail.
- **Reach**: as L8-01, for a constant that stays (a `pow` at 0 or below on a silent bus, L7a-03's drain, a latched SVF).
  The collateral is real: one playback's raw choice crushes or silences every other playback.
- **On the proposed fix**: none proposed.
- **Severity now**: minor.

### L7a-03

- **Verdict**: CONFIRMED.
- **What I checked**: `ChainSwap.kt:855` (`MAX_DRAIN_SECONDS` 20); `KatalystChain.kt:477-499` (`sustainsItself` knows the
  case, the swap does not ask).
- **What I measured**: CylinderSwapRig at 48 kHz on silence: `pow(-1)` outputs -1e15, `plus(0.2)` outputs 0.2; after the
  request for a dry chain both stay at full level until 20.36 s and above 1e-6 until 24.56 s.
- **Reach**: as the finding. For `plus`, `min` and `max` the held value is a DC offset the house DC blocker removes in
  about 0.15 s; the harmful case is `pow` at 0 or below, whose 1e15 then crushes every playback for 20 s (L8-02).
- **On the proposed fix**: right ("a chain whose `sustainsItself()` is true goes to Releasing or Cutting at once").
- **Severity now**: minor.

### L4-11

- **Verdict**: CONFIRMED.
- **What I checked**: `IgnitorEffects.kt:119-123`.
- **What I measured**: the bare node over a 0.5 sine for 0.1 s, then 0.1 s of exact silence: amount 257 and 300 give 4,801
  NaN samples (frame 0 and the whole silence) and 4,799 infinite ones of 9,600; amount 256 is finite.
- **Reach**: an authored `drive` of 257 or more without a shaper. The NaN on silence feeds L6-01 and L7b-01.
- **On the proposed fix**: its spec row is right; a cheap fix is to pass the gain through `safeOut` (a NaN scrubs to 0, an
  infinity becomes 1e15), so 0 times the gain stays 0.
- **Severity now**: minor.

### L4-06

- **Verdict**: CONFIRMED.
- **What I checked**: `IgnitorEffects.kt:689-691`.
- **What I measured**: one NaN at frame 4800 into `shimmer` (wet 1 and 0.5): two non-finite outputs, at 4800 and 13599
  (0.183 s later), as the stage probe shows.
- **Reach**: as the finding.
- **On the proposed fix**: right (`nanGuard` at the ring write).
- **Severity now**: minor.

### L4-07

- **Verdict**: CONFIRMED.
- **What I checked**: `IgnitorEffects.kt:374`.
- **What I measured**: one +Inf into `classic()`'s coarse: factor 4 gives 4 infinite samples, factor 4800 gives 4,800.
- **Reach**: as the finding.
- **On the proposed fix**: right (`finiteOrZero` instead of `nanGuard`).
- **Severity now**: minor.

### L4-08

- **Verdict**: CONFIRMED.
- **What I checked**: each line it cites; the stage-probe rows for onepole (no non-finite out), the SVF (one), crush (NaN
  to 0.5 peak, infinity to 1.0), coarse, tremolo (infinity to 1e15), the distort shapes (finite).
- **What I measured**: the bare `Adsr`: a NaN or an infinity at frame 0, where its gain is exactly 0, both leave as NaN;
  an infinity at 4800 leaves raw. The bare `Drive(0.5)`: both raw. `DcBlocker(0.999)`: two non-finite outputs (frames 10
  and 11), clean in the next block. The rest as the probe table.
- **Reach**: internal.
- **On the proposed fix**: right (a decision first, then one table).
- **Severity now**: minor.

### L8-03

- **Verdict**: CONFIRMED.
- **What I checked**: `KlangAudioWorklet.kt:132-135`, `:151-158`, `:178` (no try); `JsAudioBackend.kt` (no
  `onprocessorerror`; a repo-wide search for `processorerror` finds only the audit layer files and KLANG-00230, which
  recorded in 2026-09 that a throw stops the processor for good); `JvmAudioBackend.kt:80-116` (try with only a
  finally); `KlangPlayer.kt:197-199` (no restart); `PlaybackEngineDispatcher.kt:255-266`.
- **What I measured**: the throw site its reach needed exists (L2-04). Through the live dispatcher, a built-in `silence`
  voice and an authored `progress()` voice with a negative window each throw `IllegalArgumentException` out of
  `PlaybackEngineDispatcher.renderBlock` in their onset block. From a pattern through `KlangOfflineRenderer`, the render
  ends 0.248 s in.
- **Reach**: a pattern (L2-04). Also the uploader's `Sample.Chunk` with an offset past the announced size: measured
  `ArrayIndexOutOfBoundsException` at `SampleStore.addSample:172` in `handle` (internal, not song input; on the JVM it
  ends the backend loop like a render throw).
- **On the proposed fix**: none proposed beyond the spec; see L9-01.
- **Severity now**: major.

### L9-01

- **Verdict**: CONFIRMED.
- **What I checked**: as L8-03, plus `retain_in_order.kt:22-39` (a throw in `keep` leaves the list at full size with
  stale entries) and `VoiceScheduler.kt:453-476`.
- **What I measured**: after the throw the song's engine still lists the throwing voice (active count 1); when the test
  calls `renderBlock` again, as a host with a catch would, the second playback is heard (peak 0.72 in blocks 13 to 39)
  and the throwing voice has ended. The hunt for other throws from wire-legal input found none: every Ignitor slot of
  every built-in (49 sounds, 35,829 renders at NaN, plus and minus infinity, plus and minus 1e308, -1, 0, 1e9 and
  1e-300), every Katalyst stage with each knob at odd values plus the integer arguments (884 runs, cylinder and master),
  the 27 cylinder slots, and 36 odd samples (sizes 0 to 2, sample rate 0 and negative, pitch NaN and -1, NaN in the PCM,
  inverted loops, every sample slot at extremes).
- **Reach**: L2-04's patterns.
- **On the proposed fix**: a host-level catch alone is not enough: the net must also take the throwing voice out of the
  list (a voice whose window stays negative would throw again each block) and repair the list `retainInOrder` leaves.
  L2-04's own guard removes the one throw site found.
- **Severity now**: major.

### L2-04

- **Verdict**: CONFIRMED, LARGER.
- **What I checked**: `VoiceFactory.kt:86-91`, `:341` (`endFrame = gateEndFrame + releaseSec * sampleRate`; nothing keeps
  it at or after the onset); `Voice.kt:391-409`, `:417-426`; the fills that take the window: `NoteClockIgnitors.kt:37`
  (`Progress` at a gate of 0 frames or less), `Ignitors.kt:2869` (`silence`), `Ignitor.kt:276` (an `Affine` over a
  block-constant input), `_arithmetic_laws.kt:178`, `:187` (two constant operands, or a constant 0 on the right),
  `Ignitors.kt:870` (a sine whose partials are all silent).
- **What I measured**: through `VoiceFactory.makeVoice` and `Voice.render`, 735 voices over the 43 built-ins and 6 authored
  trees, each with a negative first window: 72 throws, all `IllegalArgumentException(fromIndex > toIndex)`, from the
  built-in `silence` and authored trees over `progress()`, a constant, the `Freq` leaf and a sine with fundamental 0. End
  to end from patterns (`KlangOfflineRenderer`, cps 0.5): `note("c3*8").s("saw").pregain(0).legato(-0.001).adsr(release = 0)`
  throws at `AffineIgnitor.generate:276` 0.248 s in (any built-in with `pregain(0)`); `note("c3*8").s("silence").legato(-0.001).adsr(release = 0)`
  throws at `SilenceIgnitor.generate:2869`; `note("c3*8").sound(Ignitor.sine().mul(Ignitor.progress())).legato(-0.205)`
  throws at `ProgressIgnitor.generate:37` (a tree with no envelope keeps the 0.05 s default tail, so the gate must end
  between 0.05 s and 0.05 s plus the onset offset before the onset). The same songs without `adsr(release = 0)` or with
  `pregain` other than 0 render to the end.
- **Reach**: `legato` slightly negative with `adsr(release = 0)` on a voice whose tree fills its window: `pregain(0)` on
  any built-in, `s("silence")`, an authored `progress()`, a constant or `Freq` root. With `legato(-0.001)` on 0.25 s notes
  the window is -12 frames on the notes whose onset lies more than 12 frames into a block, about nine in ten.
- **On the proposed fix**: right, one line in `advance` or `renderStages`. A voice whose end frame is at or before its onset
  is never audible, so going `Done` at once is the simplest form.
- **Severity now**: major (a crash of all audio for the session from wire-legal song input).

### L1-04

- **Verdict**: CONFIRMED.
- **What I checked**: `VoiceScheduler.kt:72`, `:558-598`; `KlangMinHeap.kt:87-92`, `:104-116`.
- **What I measured**: one batch of starts 5.0, NaN, 9.0, 0.6: no voice is active at 0.7 s (RMS 0 over 0.62 to 0.78 s) and
  two are dropped by 5.5 s; without the NaN the 0.6 s voice plays (RMS 0.50) and none is dropped.
- **Reach**: internal only, confirmed: start times come from fixed-point cycles through the tempo map
  (`KlangPatternScheduler.kt:663-698`), `legato` is guarded (`SprudelPatternEvent.kt:49`), and a live tempo change drops
  a NaN (`KlangCodePlaybackCtrl.kt:150`, `if (newRpm > 0.0)`). A NaN initial tempo would make every start NaN (the floor
  in `KlangPatternScheduler.kt:213` is `coerceAtLeast`, which keeps a NaN); I found no path that sets one.
- **On the proposed fix**: right.
- **Severity now**: minor.

### L1-05

- **Verdict**: CONFIRMED.
- **What I checked**: `VoiceScheduler.kt:491-506`.
- **What I measured**: `playbackStartTime` NaN: the anchored epoch sent to the frontend is NaN and both voices are dropped;
  -Infinity: NaN, both dropped; +Infinity: the epoch is +Infinity, nothing is ever promoted and nothing counted as dropped;
  0.0: plays.
- **Reach**: internal only (`requestedEpochSec = clock.nowSec()`, `KlangPatternScheduler.kt:403`).
- **On the proposed fix**: right; the +Infinity case needs the same `isFinite`, not only the NaN one.
- **Severity now**: minor.

## New findings

### N1 The analog SVF oscillates for good near fs/3: q 200 and analog 5 at 16 kHz ring at full scale after the input stops

- **Severity**: minor
- **Tag**: sound, bug
- **Where**: `audio_be/src/commonMain/kotlin/filters/LowPassHighPassFilters.kt:203-205` (`diodePairResistanceApprox`),
  `:187` and `:191-193` (the KDoc: "Output is ≥ ~1.0 for all real x", "more damping, bounded resonance");
  `audio_be/src/commonMain/kotlin/ignitor/IgnitorFilters.kt:244-245`, `:273-274`.
- **What**: `P(x) - 1 = 0.05x + 0.185x^2 + ...` is negative for x between about -0.27 and 0 (smallest -0.00338 at
  x = -0.135, a state of -1.54), so `kEff = k + 2 * driveScale * (P - 1)` falls by up to `0.00169 * analog` and goes
  below 0 once `analog * q` exceeds about 590: negative damping. Measured in the engine (one lowpass or highpass, 0.5 s
  of a 0.1 sine at the cutoff, then 10 s of silence, RMS of the last 0.5 s): q 200 analog 5: 0.941 at 15,990 Hz, 0.795
  at 16,000 Hz, 0 at 15,900, 16,010 and 16,100 Hz; analog 15: 1.13 to 1.37 from 15,900 to 16,010 Hz; q 100 needs analog
  10 (0.795), q 150 analog 10 (1.03), q 50 none up to analog 15; analog 0, 1 and 3 settle everywhere; 8, 12 and 20 kHz
  settle at every analog. At 44.1 kHz the same at 14,700 Hz (0.795): it follows the sample rate (fs/3), so it is the
  explicit, one-sample-late damping update, not an analog behaviour. White noise at 0.3 for 0.5 s starts it; a 0.05
  impulse, or a 110 Hz sine or saw at 0.5, do not. Inside a voice: a 0.1 s noise sample in a 1 s note through
  `lpf.freq` 16000, `lpf.q` 200, `analog` 5 rings at 16 kHz with amplitude 0.867 (RMS 0.61) for the rest of the note;
  with analog 0 the same stretch is silent. A pluck through `lpf(16000, 200).analog(5)` is 5.4 times louder in its
  first half second than with `analog(0)` (RMS 0.445 against 0.082).
- **Reach**: `s("hh").lpf(16000, 200).analog(5)` (or any source that falls silent inside its note) at 48 kHz; 14.7 kHz at
  44.1 kHz. The band is narrow, but 16000 is a round number and q 200 is the slot's top.
- **Settle it**: the N1 rows of `AuditSafetyRepro`. A fix candidate for the maintainer to weigh (a sound change only
  for states between about -3.1 and 0): floor `tCfb` at 0.
- **Confidence**: high.

### N2 Four finite extreme knob values put NaN, not a huge value, into a cylinder

- **Severity**: minor
- **Tag**: safety
- **Where**: the Karplus family's `feedback` (`ks`, `pluck`, `string`, `superpluck`), the perlin sources' `rate` (`perlin`,
  `perlinnoise`), the reverb's `wet` (the `reverb.wet` slot and an authored `reverb(wet = ...)`), and `drive` (L4-11).
- **What**: measured in the slot and chain batteries: `feedback` at plus or minus 1e308 gives NaN in the mix within the
  first 6 blocks (3 NaN blocks); `rate` at plus or minus 1e308 the same (2 blocks); `reverb.wet` at plus or minus 1e308 a
  non-finite cylinder in 110 of the run's 131 blocks, and (authored at 1e308) an exact-zero output for 13,307 frames.
  Raw values the Motor allows; the defect is that each becomes a NaN, which through L6-01 silences every playback and through L7b-01
  latches any duck listening to that cylinder. `numerical-safety.md` documents the Karplus divergence ("an authored
  decay > 1 diverges"), not the NaN it ends in, and not the other three.
- **Reach**: raw extreme values only (`ignp`, `katp` or an authored instrument or chain).
- **Settle it**: the "NaN source" and "non-finite" rows of `AuditSafetyRepro`.
- **Confidence**: high.

## For the audit ruleset

- A reach that runs through a door should be run through it: compiling the pattern took one line and settled what reading
  could not (the `div` route was wrong, the `pow`, `log2`, `**` and `Math.sqrt` routes were right).
- A "Settle it" spec should be run before it is written down as red: L4-01's hand law held to 3 percent, but its proposed
  spec would have passed today.
- Hunting throws by value found nothing in about 36,700 renders; the throw sites came from structure (a range API fed
  a window whose sign nothing guards). A hunt should list every call that takes a range or an index from the voice's
  window and ask what makes that window negative.
- Severity words like "major" for a safety stage should carry a measured reach row: the house-stage findings were sound
  in mechanism, but every input that triggers them is an extreme raw value.
- Parallel challengers share one scratchpad: give every log and copy a cluster prefix.
