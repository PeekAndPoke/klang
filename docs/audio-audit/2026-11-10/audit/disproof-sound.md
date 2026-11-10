# Disproof: sound

Second reader of the "sound" cluster of the engine audit (KLANG-00420): is what the engine plays correct. Every
number below that is called "measured" comes from the engine through a reproduction named with its test; the rest is
read and says so.

**What I ran**

- Three new test files, test code only, gated by `KLANG_PROBE=audit` (a normal `jvmTest` skips them):
  - `audio_be/src/jvmTest/kotlin/audit/AuditSoundRepro.kt`: the sources (L3-01 twice, L3-02, L3-03 with L3-04, L3-05,
    L3-06, L3-14): 7 tests.
  - `audio_be/src/jvmTest/kotlin/audit/AuditSoundStagesRepro.kt`: the stages (L4-02 b and c, L2-01, L4-03, L7a-01 with
    L7a-02, L7b-02, L7b-03, L7b-04, L7b-05, L5-01): 10 tests.
  - `audio_be/src/jvmTest/kotlin/audit/_audit_sound_kit.kt`: what the probes do not have: a pitch estimate of a
    decaying tone by two independent methods (a zero-padded FFT peak, and a heterodyne phase slope), the aliasing meter's
    sum split by band (its total checked against `measureAliasing` row by row), two small renderers. Everything else is
    the probes' own: `measureAliasing`, the FFT and window, `measureClick`, `renderFramedVoice`, `ignitorProbeStage`,
    `katalystProbeStage`, `katalystOwnerChangeStage`, `sampleVoiceProbeStage`, `CylinderSwapRig`.
- Commands, each under the lock (`JAVA_HOME` 21.0.12-amzn):
  `KLANG_PROBE=audit env KLANG_LOCK_TIMEOUT=7200 console/with-build-lock.sh python3 console/tools/test-digest/test_digest.py run --root /opt/dev/peekandpoke/klang --own-package io.peekandpoke.klang --expect AuditSoundRepro -- ./gradlew :audio_be:jvmTest --rerun --tests io.peekandpoke.klang.audio_be.audit.AuditSoundRepro`,
  and the same for `AuditSoundStagesRepro`. The final run of both after the last edit: test-digest "AuditSoundRepro RAN
  7 tests, 0 failed" and "AuditSoundStagesRepro RAN 10 tests, 0 failed", exit 0. Four earlier runs while building (1, 7,
  10, 10 and 10 tests) were green too; the numbers did not move between runs except the one timing (L4-03).
- The tables they wrote: `.build-claude/audio_be/probes/audit-sound.txt` and `.build-claude/audio_be/probes/audit-sound-stages.txt`.

**What I only read**: the two decoders and the sample-rate resolution of both platforms and the CLI (L3-05 b, L7b-05);
the built-in songs, for every reach; the vowel table and its KDoc; the compressor's header; ledger KLANG-00156 (E3) and
the E3 pin in `BlockFramingInvarianceSpec`; `teardown_fade.kt`'s KDoc; the cull in `Voice.renderStages`; the delay's
`Off.enter` and `configure`. I did not run JS (Kotlin/JS timing for L4-03 is open).

| Finding | Auditor | Verdict | Now | One line |
|---|---|---|---|---|
| L3-01 | critical | CONFIRMED | critical | Measured in the engine by two methods agreeing within 0.001 cent there: the default pluck is -15.75 cents at A4 and -36.73 at C6; brightness 1 reads 0.00 |
| L4-02 | major | CONFIRMED, SMALLER | minor | Mechanism, a stale ledger line and SEVEN songs confirmed; the per-note variation is -40 to -63 dB re the peak in the songs' own shapes; the grid fix at N=128 is invariant but no more accurate |
| L2-01 | major | CONFIRMED | major | The engine is the auditor's model to two digits: -49.6 dB left on the last frame at the default release, -23.4 at 5 ms; the probe's clean sine was a zero crossing |
| L4-03 | major | CONFIRMED, SMALLER | minor | First subnormal at 0.812 s, they reach the cylinder; the held voice renders 27 to 39 percent slower on the JVM, about 0.5 us per block; inaudible |
| L3-02 | major | CONFIRMED, SMALLER | major | A two-point PolyBLEP is 22 to 29 dB cleaner below 5 kHz but only 6 to 8 dB below 20 kHz; +12 semitones of pitch stage costs the saw 20 dB below 5 kHz |
| L3-03 | major | CONFIRMED, SMALLER | minor | -5.4 to -4.4 dB by construction, a documented single-sample spike nobody uses; the proposed triangle kernel reaches -12.5 dB, not -20 |
| L3-04 | minor | CONFIRMED | minor | Ratio -1: 4778 of 4800 samples at 1.0; the built-in impulse under `fm` depth 440 Hz at 220 Hz: 1624 nonzero samples per 0.1 s |
| L3-05 | major | CONFIRMED, SMALLER | minor | The auditor's numbers reproduce exactly; a four-point kernel gains 11 to 47 dB on transpositions but only 1 to 15 dB on rate conversion |
| L3-06 | minor | CONFIRMED | minor | After 1.9 s the mean is 0.08 to 1.84 times the AC level (pluck), 0.27 to 4.26 (superpluck) |
| L3-14 | minor | CONFIRMED | minor | The last frame of a one-shot is never played; `loop(1)` writes a zero at frames 7, 15, 23 ... |
| L7a-01 | major | CONFIRMED | major | Delay switched on under a held tone: kink 120.6 at 250 ms, burst -22.8 dB |
| L7a-02 | major | CONFIRMED | major | A swap into a delay reads exactly like switching it on; the room's first return is at 25.3 ms, inside the 60 ms ramp |
| L7b-02 | minor | CONFIRMED, SMALLER | minor | Widths 10 to 22 Hz measured; the swing is 17.8 dB at wet 1 floor 0, 2.1 dB at the defaults |
| L7b-03 | minor | CONFIRMED | minor | 4 kHz T60: 0.79 s at lowpass 0, 0.81 at 1000, 1.22 at 20000 |
| L7b-04 | minor | CONFIRMED | minor | Dead time 211 and 188 frames; release 24.5 ms to silence, 128.9 ms to -30 dBFS |
| L5-01 | major | CONFIRMED | major | An unset `hpf.freq` slot plays a 1 kHz highpass when optimized: -26.3 dB at 220 Hz, through the registry too |
| L7b-05 | minor | CONFIRMED | minor | At a 32 kHz engine: peak 8.8e307 and 49 non-finite samples; rates under 36 kHz are reachable offline |

### L3-01

**Verdict**: CONFIRMED.

**What I checked**: `KarplusString.kt:134-169`: the read at `wp - dl`, the one-pole `lp += alpha * (sample - lp)`, the
allpass, the write-back; nothing takes the filters' delay out of `dl`. `:183` (`baseDelayOf = sampleRate / freqHz`),
`:189`, `:195`. Both nodes feed `baseDelayOf` of the note (`Ignitors.kt`, `KarplusStrongIgnitor.generate` and
`SuperKarplusStrongIgnitor.computeBaseDelays`; the unison detune is 0 at one voice). Brightness default 0.5
(`IgnitorDsl.kt:251`). No escape: no ticket on pluck tuning (`ticket find pluck`, `karplus`, `tuning`), nothing in
`audio/MEMORY.md`. The project's own knowhow states the compensation the engine does not apply
(`docs/knowhow/production-tricks/sound-design.md:308`: the line shortened by the loop filter's delay).

**What I measured**: test "L3-01: the pluck's pitch through the built-in voice, by brightness and stiffness, two methods".
The registered `pluck` and `superpluck` (voices 1) through `VoiceFactory` and `Voice.render`, analog 0, 2 s, 48 kHz.
Method A: FFT peak (7-term Blackman-Harris, zero-padded 16 times, parabola). Method B: heterodyne phase slope. The
controls (the sine voice, and a test-made sine decaying with a 5 ms time constant) read 0.0000 cents at all six notes
(one 0.05 cent outlier, method B on the 110 Hz decaying sine). Cents off the note; A and B agree within 0.1 cent except
where marked, and within 0.001 cent at brightness 0.5 and 1.0 up to C6:

| Note | b 0.1 | b 0.2 | b 0.5 (default) | b 1.0 | b 0.5, stiffness 0.01 | b 0.5, stiffness 0.5 | b 0.5, stiffness 0.99 | b 1.0, stiffness 0.01 |
|---|---|---|---|---|---|---|---|---|
| 110 | -35.14 | -15.77 | -3.96 | 0.00 | -7.88 | -6.34 | -5.30 | -3.92 |
| 220 | -68.49 | -31.27 | -7.91 | 0.00 | -15.71 | -12.64 | -10.58 | -7.84 |
| 440 (A4) | -127.90 | -61.06 | **-15.75** | 0.00 | -31.25 | -25.16 | -21.05 | -15.64 |
| 880 | -215 to -218 (A, B 3.4 apart) | -114.33 | -31.08 | 0.00 | -61.69 | -49.71 | -41.60 | -31.14 |
| 1046.5 (C6) | -244 to -247 (3.0 apart) | -132.2 | **-36.73** | -0.01 | -72.96 | -58.80 | -49.20 | -36.98 |
| 2093 | no pitch: the note dies in a few periods | -218 to -220 (2.2 apart) | -69.70 | -0.05 | -140.33 | -113.00 | -94.25 | -73.22 |

The superpluck at one voice reads the same values to 0.01 cent wherever the pitch is defined (in the dying cases,
brightness 0.1 from 880 Hz up, the two nodes' readings differ by up to 2 cents, as the two methods do). The auditor's replica
(default: -4.0, -7.9, -15.7, -31.1, -36.7, -69.7; the brightness 0.2, stiffness 0.01 and 0.5 columns) is the engine to
within 0.1 cent in every cell of their table but one (brightness 0.2 at 2093 Hz, -218.5, between my two methods' -219.7
and -217.5). An analytic model of the loop's phase delay (in the test, not the engine) lands within 0.1 cent at
brightness 0.5 and 1.0 up to C6 and within 2 cents in the dark and high cases, so the loop filters' delay is the whole
mechanism. Stiffness steps the pitch down by
almost one more sample at any value above 0 and then sharpens as it grows, as claimed.

Test "L3-01: the pluck's pitch at the settings built-in songs use": IrishLament's `fingerpick` (brightness 0.45, layered
with a sine AT the note) is -6.5, -12.9 and -25.5 cents at D3, D4 and D5, so the pluck beats against the instrument's
own sine layer at 0.55, 2.2 and 8.6 Hz; the `contrabass` (brightness 0.25, stiffness 0.05) is -10.4, -15.6 and -20.8
cents at D2, A2 and D3; the default pluck is -9.4, -18.7 and -36.7 cents at C4, C5 and C6.

**Reach**: as stated, and in the corpus: `Sakura` (`koto = Ign.pluck()`), `Greensleeves` (the lute), `DrunkenSailor`,
`IrishLament` (two instruments).

**On the proposed fix**: right in kind, but `(1 - alpha) / alpha` is the delay at DC, and at low brightness it is far
off at every pitch, not only in the top octave: at the floor `alpha` 0.01 it is 99 samples while the one-pole's phase
delay at A4 is about 24. The compensation has to be the one-pole's and the allpass's phase delay at the fundamental,
recomputed whenever brightness or stiffness moves (both are read per block) and kept above the line's 2-sample floor. It
moves every pluck in the corpus sharp by the amounts above: a sound change for the maintainer's ear.

**Severity now**: critical. A built-in instrument at its defaults is out of tune in ordinary use (16 cents at A4 beats
audibly against any other source), and in the corpus it beats against its own layers.

### L4-02

**Verdict**: CONFIRMED, SMALLER.

**What I checked**: `IgnitorFilters.kt:346-362` (`prepareEnvelopeSweep`: the cutoff at `pos` and at `pos + length`),
`SvfCoeffSweep.kt:65-91`, the per-sample adds of the loops; `IgnitorDslRuntime.kt` `filterEnvDef` and `slotLayerDepth`
(a written stage knob alone fills the depth); sprudel's `lpf(env = ...)` writes `lpf.env` (`lang_filters_lpf.kt`,
`ClassicSlotParamsSpec`). Ledger KLANG-00156 line 268: E3 is "MAJOR (latent: no production door passes an env here)"
with the instruction "decide before any door wires an env"; `classic()` wired it (phase 3 step 5) and the ledger was not
updated. The pin `BlockFramingInvarianceSpec.kt:428` still pins the defect.

**What I measured**:

(a) The songs. Both named songs do play filter envelopes: `IrishLamentTechno.kt:49` (the bass, `env = 15.9, attack =
0.005`, in `core` from the first cycle), `:55`, `:62`, `:74`, `:102`, `:138` and five more in `darkBuild`;
`StrangerThings.kt:42` (`lpf(attack = 0.008, decay = 5.0, sustain = 0.2, release = 0.1)` and `lpf(freq = 1400, env =
perlin...)` on the melody, cycles 64 to 256). The reach is wider than two songs: `ATruthWorthLyingFor` (5 voices, one through the fill),
`DrunkenSailor:27` (a stage knob alone, so the slot-layer fill's 7 semitones), `SoundOfTheSea` (3), `Tetris` (5 lines),
`TetrisRemix` (4): seven built-in songs. Most of those voices also run `analog > 0` (`IrishLamentTechno`'s `.analog(3.0)`
on the whole stack, the `StrangerThings` melody at 15, `Tetris` 2.5 and 3.5, `TetrisRemix` 3.5), whose filter drift steps
per block by design, so those filters follow the block grid whatever the envelope does.

(b) Audibility, test "L4-02 (b): two onsets one frame apart under a filter envelope, the classic voice": the classic voice
through `VoiceFactory`, the same note at every onset k = 0..127 inside the block, aligned by the onset, analog 0. dB re
the note's peak (max) and re its RMS:

| Case | adjacent onsets k, k+1: max worst / median | adjacent: RMS worst | onset k against 0: max worst | k against 0: RMS worst |
|---|---|---|---|---|
| saw 110 Hz, lpf 300, env 24 st, the default times (a 0.01, d 0.1, s 1, r 0.1) | -48.7 / -51.4 | -73.5 | -19.7 | -44.3 |
| IrishLamentTechno's bass shape (saw 55 Hz, lpf 700, env 15.9, q 1.5, a .005 d .08 s .7) | -92.6 / -97.4 | -106.5 | -63.2 | -76.6 |
| Tetris's sub shape (tri 82.4 Hz, lpf 200, env 56.4, a .01 d .1 s 0) | -65.7 / -68.7 | -84.0 | -39.8 | -55.9 |
| TetrisRemix's sub shape (tri 82.4 Hz, lpf 300, env 48, a .005 d 1 s 0) | -75.4 / -81.5 | -97.5 | -50.5 | -69.5 |

Control: each voice without its envelope is identical at onsets 0 and 37 (difference 0.0). Two onsets one frame apart
differ by -49 dB re the peak at worst. Across the whole block (what the framing probe reads) the difference is -20 dB re
the peak and -44 dB RMS on a two-octave sweep in 10 ms, and -40 to -63 dB re the peak, -56 to -77 dB RMS, on the three
songs' own envelope shapes, concentrated in the attack.

(c) The fix shape, test "L4-02 (c): today's block law, the exact per-sample cutoff and a note-anchored grid, in test code
checked against the engine". The engine's lowpass emulated in test code from its own primitives (`EnvelopeCore`,
`filterEnvCutoff`, `computeSvfCoeffs`, the same per-sample adds) reproduces the engine bit for bit at all 128 onsets
(largest difference 0.0), so the variants below are the engine's arithmetic with other knots. Saw 110 Hz, lowpass 300 Hz,
env 24, the default times, 0.4 s:

- Today against the exact per-sample cutoff: worst -15.9 dB re the peak (median -23.0), RMS worst -41.9 dB.
- Grid N=128 plus the four breakpoints: -18.4 dB re the peak, -44.5 dB RMS against exact: no more accurate than today.
  N=64: -36.7 and -61.9. N=32: -42.8 and -67.2.
- Bit-invariant: yes, for a scheduled voice at analog 0 with a block-constant cutoff and q: knots and adds are a function
  of note frames only. Not for a realtime voice (its gate knot lands where the engine learned the gate), not at
  `analog > 0` (the drift is per block by design, and the saturating loops step `g` and `k`, which the grid would have to
  carry as well), not for an Ignitor signal as the cutoff (read per block).
- Cheaper: coefficient sets (a `tan` and a divide each) over this note: today 192, N=128 153 (20 percent fewer), N=64
  302, N=32 600 (3.1 times today, not "about twice"). On top, a loop split at every knot inside a block, and with knots
  anchored to the note that is almost every block.
- What it changes in existing songs: N=128 moves each note by median -44.2 dB RMS re the note (worst -39.7), median
  -18.7 dB re the peak (worst -14.0). Today's own spread between two onsets is median -49.1, worst -41.5 dB RMS. The fix
  moves a note by about as much as the block grid already does.

**Reach**: corrected to seven built-in songs and every `classic()` voice whose pattern writes a filter-envelope knob.

**On the proposed fix**: option 3 removes the framing dependence at analog 0 and is slightly cheaper at N=128, but buys
no accuracy; accuracy needs N=64 or less (1.6 times today's coefficient work and more). Given (b), closing E3 by a
recorded decision (fix or accept) is the part that cannot wait; the ledger asked for it before any door wired an env.

**Severity now**: minor. The mechanism, the reach and the stale ledger line are real, but the per-note variation stays at
-40 dB re the peak or below in the corpus's own envelope shapes (-20 dB only on a synthetic two-octave 10 ms sweep), and
today's deviation from the exact curve is part of what was tuned by ear.

### L2-01

**Verdict**: CONFIRMED.

**What I checked**: `VoiceFactory.kt:302-303` and `:316` (lifetime = the release, no teardown fade when the tree ends in
its envelope), `EnvelopeCore.kt:188-202` (the one-pole), `classic()`'s constant `declick`, the cull (`Voice.kt:433`,
`:475-486`: only in the release, and the release's output stays above the floor to its end, so it does not end the voice
earlier). Partly known: `teardown_fade.kt:38-39` names "~3.3e-3 on the last frame at a 50 ms release"; nothing names
the short releases, and no decision accepts them.

**What I measured**: test "L2-01: the gain left on a voice's last frame, by release and curve, and the sine's
zero-crossing". A DC source through `classic()` (the output is the applied gain): the last nonzero frame is exactly
`floor(release * sr) - 1` after the gate, the frame after it is 0, and the gain left there is:

| Release | exponential (default) | linear |
|---|---|---|
| 200 ms | 7.9e-4 (-62.1 dB) | 4.95e-3 (-46.1 dB) |
| 50 ms (default) | 3.31e-3 (-49.6 dB) | 1.98e-2 (-34.1 dB) |
| 20 ms | 9.16e-3 (-40.8 dB) | 4.95e-2 (-26.1 dB) |
| 10 ms | 2.23e-2 (-33.0 dB) | 9.92e-2 (-20.1 dB) |
| 5 ms | 6.77e-2 (-23.4 dB) | 0.197 (-14.1 dB) |
| 2 ms | 0.271 (-11.4 dB) | 0.431 (-7.3 dB) |

The auditor's model is the engine to two digits in every cell. The zero-crossing claim holds: the click probe's sine
(110 Hz, onset at frame 24101, gate 2 s, default release) ends at 5e-5 of its peak (-86.4 dB; the detector reads kink 1.0
and -102.4 dB, the probe's "clean" row); with the gate a quarter period later (+109 frames) it ends at -3.31e-3 (-49.6
dB), kink 16.0 on the last frame, burst -65.2 dB. At a 5 ms release: -34.0 and -23.8 dB, kink 99 and 312, burst -49.3
and -39.4 dB (the scale: a one-block ramp reads 29 and -60.5 dB, a hard step 3618 and -18 dB).

**Reach**: as stated. Short releases are ordinary in the corpus (0.02 to 0.05 s in `IrishLamentTechno`, 0.04 s hats).

**On the proposed fix**: the mutant `needsTeardown = true` is the right engagement test. The click probe's voice rows
should get gates at several phases, as its Katalyst rows have instants, or this class stays invisible (New findings, S-01).

**Severity now**: major.

### L4-03

**Verdict**: CONFIRMED, SMALLER.

**What I checked**: `EnvelopeCore.kt:193-201` (no flush), the gain multiply in `IgnitorEnvelopes.kt`, the cull
(`Voice.kt:433`: never during the gate), so the state is reached on any sustain-0 note held longer than attack plus decay
plus 0.7 s.

**What I measured**: test "L4-03: subnormals from classic()'s de-click at sustain 0, the stage alone and through the
voice into the cylinder". `Sample.classic()` on a 1.0 step, `adsr(0.01, 0.1, 0, 0.05)`, 3 s: 105025 subnormal samples,
the first at frame 38975 (0.812 s; the auditor predicted about 0.82 s). The gain is 1.3e-42 at 0.2 s, 3.5e-303 at
0.8 s, and stuck at 1.2e-322 from 1.0 s on, as predicted. A 110 Hz saw voice's cylinder mix holds 210528 subnormal
samples (both channels) from 0.811 s on: they reach the cylinder. Cost on this JVM: a 20 s held saw voice renders in 13.9
to 16.9 ms at sustain 0 against 10.0 to 13.2 ms at sustain 1e-9, the median of 9 interleaved rounds in each of three runs
(ratios 1.27, 1.39, 1.29): about 0.5 us more per block, about 0.02 percent of a block's real-time budget per held voice.

**Reach**: as stated.

**On the proposed fix**: `flushState()` in `EnvelopeDeclick.next` is right. It is not bit-identical (a gain under 1e-15
becomes 0), but the change is below -300 dB.

**Severity now**: minor. Inaudible, and the measured cost is small on the JVM; it does break the engine rule "flush IIR
state", so it is worth the one-line fix. Kotlin/JS not measured.

### L3-02

**Verdict**: CONFIRMED, SMALLER (the size of the PolyBLEP's gain depends on where the band ends; the pitch-modulation
gap is confirmed in full).

**What I checked**: `WaveVoiceState.kt:52-76`, the `waveTrapezoid` KDoc ("No PolyBLEP: a finite-slope edge is
inherently band-limited"), `Ignitors.kt:957-965` (`rf = (flankSamples * dt).coerceAtMost(shapeMax)` with
`dt = actualFreq / sr`, before any `phaseMod` or drift).

**What I measured**: test "L3-02: saw and square, non-harmonic energy by band against a two-point PolyBLEP, and the
flank under upward pitch modulation". The aliasing meter's method with the non-harmonic power split by band; the split's
total equals `measureAliasing`'s at every pitch (-33.4, -23.2, -20.6, -17.0, -15.9). Engine saw against a two-point
PolyBLEP saw in test code, dB re the fundamental:

| Pitch | below 5 kHz | below 15 kHz | below 20 kHz | share of the total above 20 kHz |
|---|---|---|---|---|
| 110 | -56.7 / -78.8 | -41.3 / -52.5 | -36.7 / -44.7 | 51 percent |
| 1244.5 | -46.6 / -68.5 | -31.0 / -41.9 | -26.3 / -34.2 | 50 percent |
| 2637 | -43.4 / -65.9 | -27.8 / -38.7 | -22.8 / -30.5 | 41 percent |
| 4186 | -44.3 / -71.6 | -24.2 / -34.2 | -20.4 / -27.9 | 54 percent |
| 7044.4 | -44.3 / -72.9 | -22.6 / -32.9 | -16.1 / -22.3 | 2 percent |

The engine's saw columns are the auditor's to 0.1 dB. A two-point PolyBLEP is 22 to 29 dB cleaner below 5 kHz and 10 to
11 dB below 15 kHz, but only 6 to 8 dB below 20 kHz: the auditor's "10 to 25 dB in the audible band" holds for a band
that ends at 15 kHz. "The meter's total is dominated by aliases above 20 kHz" is overstated: above 20 kHz holds about half
of it (41 to 54 percent; above 15 kHz over 80 percent at 1244.5 Hz). The square (2-sample flank) against a two-point
PolyBLEP square: 21 to 29 dB cleaner below 5 kHz, 7 to 11 dB below 15 kHz, and below 20 kHz 0.2 to 2.5 dB up to
4186 Hz (7.2 dB at 7044.4 Hz).

The pitch-modulation part, same test: a saw raised +12 semitones by `pitchModSemitones`, or by `accelerate` (held after
a 0.1 s gate; both read identically), against the same saw played at that pitch: at 1244.5 Hz below 5 kHz -26.4 against
-46.6 dB (20 dB worse; the raw `zawtooth` reads -20.5), below the fundamental -32.5 against -65.8. +24 semitones at
4186 Hz: -18.3 against -44.3 (`zawtooth` -15.6). Under an upward pitch stage the saw is nearly raw.

**Reach**: as stated, and every upward `accelerate`, `penv`, `vib` and `fm` over these sources.

**On the proposed fix**: a PolyBLEP fed the per-sample increment (with `phaseMod` and drift) closes the modulation gap,
which is the strongest part. For the square, a two-point PolyBLEP brings 0.2 to 2.5 dB below 20 kHz up to 4186 Hz, so its treble cost
must be weighed against a gain that exists mostly below 15 kHz. The doc part stands.

**Severity now**: major (the band-limiting the code claims is lost under any upward pitch stage, measured; the KDoc's
reason is untrue).

### L3-03

**Verdict**: CONFIRMED, SMALLER.

**What I checked**: `Ignitors.kt:1338-1363`. The node is documented as a naive spike: "Emits a single-sample spike per
cycle" (`IgnitorDsl.Impulse`), "outputs 1.0 once per cycle" (`Ignitors.impulse`); nothing claims band-limiting.

**What I measured**: test "L3-03 and L3-04: the impulse's aliasing against a triangle kernel, and a backward increment".
Non-harmonic energy re all harmonics: engine -5.4, -5.3, -4.4, -4.8 dB at 110, 1244.5, 4186, 7044.4 Hz; a test replica
of the engine's rule reads the same to 0.1 dB. The proposed fix, the spike split over the two samples round the crossing
by its fraction: -12.7, -12.9, -11.3, -12.6 dB. The auditor's "about -20 dB at once" is off by about 7 dB: a triangle
kernel leaves about 5 percent of the power out of band (-12.8 dB), not 1 percent.

**Reach**: corrected: no built-in song and no tutorial uses `impulse` (grep).

**On the proposed fix**: a two-sample split buys about 7.5 dB. A real improvement needs a longer band-limiting kernel (a
windowed sinc of several taps) or a sum of harmonics.

**Severity now**: minor (documented naive design, no use in the corpus).

### L3-04

**Verdict**: CONFIRMED.

**What I checked**: the plain loops' rule `phase < lastPhase` (`Ignitors.kt:1338`, `:1346`, `:1356`, `:1363`);
`renderPhased`'s KDoc names the backward case as a difference between the two loops, not as a defect; the FM law
`1 + mod * depth / freq` (`PitchModFactories.kt`, `FmModIgnitor`), and sprudel's `fm` depth is in Hz (`SvdGroups.kt:96`).

**What I measured**: same test. `pitchMod(mod = -2.0)` (ratio -1) at 220 Hz: 4778 of 4800 samples nonzero in 0.1 s
(forward controls: ratio 1.5, 33; ratio 0.5, 11). The built-in `impulse` voice at 220 Hz with `fm.depth` 440 Hz: 1624
nonzero samples in 0.1 s; with depth 110 Hz: 22.

**Reach**: `s("impulse")` with an `fm` depth above the note's frequency, from sprudel.

**On the proposed fix**: the spec row the auditor names is the right guard.

**Severity now**: minor.

### L3-05

**Verdict**: CONFIRMED, SMALLER.

**What I checked**: `SampleIgnitor.kt:63-79`, `:90-106`; `VoiceFactory.kt:181`
(`rate = sample.sampleRate / sampleRate * pitchRatio * speed`). For (b), read only: `BrowserAudioDecoder.kt:32` decodes
in an `OfflineAudioContext(1, 1, 48000)` and hands over that context's rate; every JVM path keeps the file's own rate
(`JvmWavDecoder`, `JvmMp3Decoder:49` `header.frequency()`, `JvmGenericAudioDecoder` `base.sampleRate`).

**What I measured**: test "L3-05: sample playback, the engine's two-point linear read against four-point kernels, and a
file off the engine's rate". Non-harmonic energy re the tone, dB, and the engine's level loss on the tone:

| Row | engine (linear) | 4-point Hermite | 4-point Lagrange | engine level loss |
|---|---|---|---|---|
| 1244.5 Hz, a semitone up | -61.1 | -87.5 | -108.0 | 0.02 dB |
| 4186 Hz, a semitone up | -39.8 | -55.3 | -65.9 | 0.19 dB |
| 7044.4 Hz, a semitone up | -30.2 | -40.9 | -47.8 | 0.55 dB |
| 44.1 kHz file, 5 kHz tone | -33.9 | -46.5 | | 0.37 dB |
| 44.1 kHz file, 10 kHz tone | -20.4 | -26.4 | | 1.50 dB |
| 44.1 kHz file, 15 kHz tone | -11.1 | -13.8 | | 3.44 dB |
| 22.05 kHz file, 2 kHz tone | -38.0 | -52.7 | | 0.24 dB |
| 22.05 kHz file, 5 kHz tone | -20.4 | -26.4 | | 1.50 dB |
| 22.05 kHz file, 9 kHz tone | -6.2 | -7.6 | | 5.05 dB |

The auditor's rows reproduce exactly (the 15 kHz loss is 3.4 dB, not 3.1). The 44.1 kHz, 10 kHz row through a real
sample voice (`VoiceFactory`) reads the same -20.4 dB: the factory folds the file's rate into the playhead.

**Reach**: as stated.

**On the proposed fix**: a four-point kernel gains 11 to 47 dB on transpositions above 1 kHz but only 1.4 to 14.7 dB on
rate conversion (2.7 dB for a 15 kHz tone of a 44.1 kHz file), because there the images are the file's own, which only a
band-limiting converter removes. For (b), converting at decode on the JVM, as the browser does, removes the JVM-only half.

**Severity now**: minor. The images are a quality level of a simple design, and each is measured re a single partial,
which in a tonal sample is itself weak up there. Part (b), an offline render whose top octave is not the browser's, is the
half worth a ticket.

### L3-06

**Verdict**: CONFIRMED.

**What I checked**: `KarplusString.kt:52-60` (the burst's mean is not removed), `:145` (the one-pole has unity gain at
DC, so DC decays by `feedback` alone).

**What I measured**: test "L3-06: the pluck's mean against its AC level, 8 voice seeds". The bare `pluck` at 220 Hz:
the mean over the first 0.1 s runs from -0.040 to +0.026 (-28 dBFS at worst); over 1.9 to 2.0 s it is 0.19 times that
(0.996 to the 418th) and |mean| over AC RMS is 0.08 to 1.84 (two seeds above 1). `superpluck` (8 strings): 0.27 to 4.26
(two above 3). The auditor's replica (0.21 to 1.25) is the same order.

**Reach** and **On the proposed fix**: as stated; removing the burst's mean in `excite` is a sound change (same draws,
other samples).

**Severity now**: minor.

### L3-14

**Verdict**: CONFIRMED.

**What I checked**: `SampleIgnitor.kt:72`, `:99` (`if (base >= pcmMax) 0.0`); `VoiceFactory`'s explicit loop without
`begin`/`end` is `[0, pcmSize)`; `SampleIgnitorTest.kt:59` pins the one-shot behaviour; the door exists: sprudel's
`loop(flag)` (`lang_sample_loop.kt:50`, its KDoc example `s("pad").loop(1)`), writing the slot `loop`.

**What I measured**: test "L3-14: the playhead's last frame and a whole-sample loop through the sample voice": through
`VoiceFactory`, adsr off, rate 1, an 8-frame PCM. One shot over 0.1 to 0.8 plays 0.1 to 0.7 and then 0 at frame 7: the
0.8 is never played. `loop(1)` over a constant 0.5 writes a zero at frames 7, 15, 23, ..., 63: one zero per pass.

**Reach**: no built-in song or tutorial calls `loop` (grep); the door's own example does.

**On the proposed fix**: the spec the auditor describes reproduces it (frames 7 and 15 are 0); a loop's last read should
interpolate towards `loopStart`, as the auditor says.

**Severity now**: minor (a tick per pass, the size of the sample's last frame).

### L7a-01

**Verdict**: CONFIRMED.

**What I checked**: `KatalystDelayEffect.kt:360-363` (`Off.enter` resets the wet glide, which re-arms its snap),
`:676-686` (only a modulated time starts the wet from 0; otherwise `retarget` snaps), `KnobGlide.kt:128`, `:151`.

**What I measured**: test "L7a-01 and L7a-02: a delay switched on, a swap into a room and into a delay, and the room's
first return". The classic chain under the click probe's 110 Hz tone, owner change {} to delay wet .5, time .25,
feedback .3, worst of 4 instants: kink 120.6 at 250.0 ms, jump 30.2, burst -22.8 dB (the probe's wet .25 row: 114.85,
-27.5 dB). For scale: a one-block ramp reads 29.3 and -60.5 dB, a hard step 3618 and -18 dB.

**Reach**: as stated.

**On the proposed fix**: the compressor's `fresh` flag is the right model; as the auditor says, the arriving owner's own
first echo then loses its attack, which is a sound decision.

**Severity now**: major.

### L7a-02

**Verdict**: CONFIRMED.

**What I checked**: the swap's ramp is 60 ms and linear (`Crossfade.XFADE_SECONDS`, `rampUpAndAdd`), the arriving chain is
reset and fed the whole mix.

**What I measured**: same test, through `CylinderSwapRig`. The swap from the classic chain to a delay (wet .5, time .25,
feedback .3): kink 120.6 at 250.0 ms, burst -22.8 dB, identical to switching the delay on (the auditor predicted "the -27
dB class"). The swap into a room: 63.1 at 70.7 ms, -49.1 dB (the click table's row, reproduced). The control swap to
`gain 0.5`: 1.8 and -91.0 dB. The room's first return on an impulse comes at frame 1214, 25.3 ms, inside the 60 ms ramp;
its strongest early return is at 70.7 ms, after it: the auditor's correction of the lead's timing holds.

**Reach**: as stated.

**On the proposed fix**: the auditor proposes the probe row and a spec; this test is both, measured.

**Severity now**: major.

### L7b-02

**Verdict**: CONFIRMED, SMALLER.

**What I checked**: `VowelBands.kt:38-44` (the Band KDoc: "legacy Q-peak convention", "Vowel tables typically use 60 to
130"), the tables (`bass:a` q 60, 70, 110, 120, 130; `soprano:a` q 80, 90, 120, 130, 140, the same column in every
vowel of a register), `LowPassHighPassFilters.kt:427-434` (`vowelGain = 10^(db/20) * q * VOWEL_TAME`), the
`VOWEL_TAME` KDoc ("tuned with high Q (80-140)"). Where the numbers come from: the code says only "the classic sung-vowel
tables"; no source is named, and `CREDITS.MD` credits none. The rows match, as far as I can recall without the network,
the Csound manual's formant table, whose third column is a bandwidth in Hz; the code treats it as a Q on purpose and asks
that it not be cleaned up.

**What I measured**: test "L7b-02: the vowel's formant widths from its impulse response, and a saw's level by note". The
vowel stage's impulse response (classic chain, wet 1, floor 0), -3 dB widths: `bass:a` 10.0, 14.8, 20.1, 20.1, 22.3 Hz
against freq / q 10.0, 14.9, 20.5, 20.4, 21.2, F1's peak +9.5 dB; `soprano:a` 10.0, 12.7, 24.2, 28.8 Hz against 10.0,
12.8, 24.2, 30.0, F1 +12.0 dB (its 4950 Hz band, 25 dB under its neighbour, reads 111 Hz there, the neighbour's skirt).
A band-limited saw through `bass:a`, A2 to A3: at wet 1, floor 0 the level runs from -22.1 dB (110 Hz) to -4.3 dB
(150 Hz), 17.8 dB, and 12.0 dB over the chromatic notes alone. At the defaults (wet .5, floor .2): 2.1 dB, and 0.7 dB
over the chromatic notes.

**Reach**: as stated, every `vowel(...)`; at the defaults the effect is small.

**On the proposed fix**: reading the column as a bandwidth (q = freq / bandwidth) changes every vowel, and `vowelGain`'s
fold and `VOWEL_TAME` would have to be tuned again with it: a listening task, as the auditor says.

**Severity now**: minor (the narrow bands are real; at the defaults the floor and the dry part hide them).

### L7b-03

**Verdict**: CONFIRMED.

**What I checked**: `Reverb.kt:403-411` (`damp = 1 - lowpass / nyquist`, `damping = damp * 0.4`), its KDoc ("HF damping
cutoff in Hz"), `effects-mixing.md:143`.

**What I measured**: test "L7b-03: the room's decay time by band at lowpass 0, 1000, 20000 and unset". Size 5, wet 1, T60
in seconds:

| lowpass | 1 kHz | 4 kHz | 8 kHz |
|---|---|---|---|
| 0 | 1.28 | 0.79 | 0.43 |
| 1000 | 1.29 | 0.81 | 0.45 |
| 5000 | 1.31 | 0.89 | 0.58 |
| 20000 | 1.36 | 1.22 | 1.08 |
| unset | 1.34 | 1.07 | 0.74 |

`lowpass` 1000 is `lowpass` 0 within 0.02 s in every band, and it shortens the 4 kHz tail by a third against 20000 where
a 1 kHz cutoff would end it. Songs write it as a cutoff (`StrangerThings` 8000, `SoundOfTheSea` 12500).

**Reach**: as stated.

**On the proposed fix**: either the words or the mapping, as the auditor says; a new mapping moves the two songs above.

**Severity now**: minor.

### L7b-04

**Verdict**: CONFIRMED.

**What I checked**: `Compressor.kt:632-659` (`followEnvelope`), `SILENCE_DB` -100, `SILENCE_LIN` 1e-10, the attack and
release blend. The header's deferred "linear-domain rewrite" is about its cost (two `exp`/`ln` per sample), not about
this behaviour.

**What I measured**: test "L7b-04: the compressor's dead time after silence and its release by what follows". A 0 dBFS
DC step after 0.2 s of silence: the gain stays exactly 1.0 for 211 frames (4.40 ms) at the defaults and 188 frames
(3.92 ms) for the limiter preset (the auditor: 212 and 188). Release at the defaults: within 0.1 dB of unity 24.5 ms
after the input stops, 128.9 ms after it drops to -30 dBFS (the auditor: 26 and 146 ms for the whole reduction).

**Reach**: as stated.

**On the proposed fix**: none proposed beyond the two specs, which this test reproduces.

**Severity now**: minor.

### L5-01

**Verdict**: CONFIRMED.

**What I checked**: `IgnitorDslOptimizer.kt` `asFusibleSections` (the cutoff is never looked at), the `Eq` arm without
the gate, `clampSvfCutoff(NaN)` = 1000.0, the `Slots` KDoc example at `IgnitorDsl.kt:244`.

**What I measured**: test "L5-01: a filter on an unset slot, unoptimized against optimized, and through the registry".
With no slot written, the optimized tree's root is an `Eq`: `Sine().lowpass(Slots.lpf.freq)` keeps its level at 220 Hz
but its waveform differs by up to 0.314 (the 1 kHz filter's phase) and loses 2.0 dB at 880 Hz;
`Sine().highpass(Slots.hpf.freq)` loses 26.3 dB at 220 Hz and 4.3 dB at 880 Hz; `Saw().lowpass(...)` 0.7 and 3.9 dB. With
the slot written (500 Hz) the two are identical (0.0). Through the registry (which optimizes at `register`) against the
same tree with `.optimizer(0)`: the same -2.0, -26.3 and -4.3 dB. The one-row spec the auditor names would be red (0.31
against a parity of 1e-12).

**Reach**: as stated: no corpus song; the KDoc's own example; any `Ignitor.param(name, NaN)` cutoff.

**On the proposed fix**: the auditor's one spec row is the right guard; the fusion has to keep the filter's gate (an
unset cutoff builds no section).

**Severity now**: major.

### L7b-05

**Verdict**: CONFIRMED.

**What I checked**: `PhaserCore.kt:176-186`, `MAX_MOD_FREQ_HZ` 18000. Which rates the engine accepts (read): nothing
checks the rate; the CLI's `render --sample-rate` takes any integer (`RenderWavCommand.kt:44`), so does
`KlangOfflineRenderer(sampleRate = ...)` (its own spec renders at 8000); the JVM player takes the supported line nearest
the asked rate from {asked, 44100, 48000, 88200, 96000, 22050, 16000} (`index_jvm.kt:59-77`); the browser asks 48000 and
falls back to its own default rate when that context cannot be made (`index_js.kt:42-69`). `Reverb`'s KDoc names 22050 Hz
as its design floor.

**What I measured**: test "L7b-05: the phaser's breakpoint clamp at a 32 kHz and a 48 kHz engine".
`phaser(rate .5, wet .5, center 15000, sweep 6000)` on 1 s of noise: at 32 kHz a peak of 8.8e307, 36627 samples above 10
and 49 non-finite samples; at 44.1 and 48 kHz a peak of 2.1 and nothing above 10.

**Reach**: corrected: offline renders at a rate under 36 kHz (the CLI option, the offline renderer), and a live JVM
player only when the sound card refuses 48000, 44100 and 88200 Hz.

**On the proposed fix**: a clamp relative to the rate, the form of `clampSvfCutoff`, closes it, as the auditor says.

**Severity now**: minor (an offline render at a low rate reaches it; live use rarely).

## New findings

### S-01 The click table's sine note-end row is blind by construction

- **Severity**: minor
- **Tag**: test
- **Where**: `audio_be/src/jvmTest/kotlin/probes/_click_cases.kt` (`CLICK_VOICE_START`, `CLICK_VOICE_GATE`,
  `CLICK_TONE_HZ`), `clicks.txt` row 133.
- **What**: 110 Hz times (2 s gate + 0.05 s release) is 225.5 cycles, so every voice that starts at phase 0 ends on a zero
  crossing. The sine row reads clean (kink 0.95, -102.4 dB) while the same voice with the gate a quarter period later reads
  kink 16.0 and -65.2 dB (measured, test "L2-01 ..."). The voice rows are one render at one phase; the Katalyst rows already
  take four instants.
- **Reach**: every reading of the voice note-end rows of the click table.
- **Settle it**: render each voice row at four gate phases and keep the worst, as the Katalyst rows do.
- **Confidence**: high.

### S-02 The compressor's detector reads a very quiet input as quieter than silence

- **Severity**: note
- **Tag**: consistency
- **Where**: `audio_be/src/commonMain/kotlin/effects/Compressor.kt:644-648`, `:963-964`.
- **What**: an input level above `SILENCE_LIN` (1e-10) is converted to dB, down to -200 dB, while silence reads
  `SILENCE_DB` (-100). A signal at -120 dBFS therefore drives the envelope 20 dB below what silence does, and the release
  after a loud passage heads further down when a quiet tail follows than when silence does. Read, not measured; the effect
  on the sound is tiny.
- **Reach**: every compressor and limiter without lookahead.
- **Settle it**: floor the converted level at `SILENCE_DB`, or set `SILENCE_LIN` to 1e-5; a spec feeding -120 dBFS
  against silence after a loud passage.
- **Confidence**: high (read).

### S-03 The vowel table names no source

- **Severity**: note
- **Tag**: doc
- **Where**: `audio_bridge/src/commonMain/kotlin/VowelBands.kt` (the class KDoc: "the classic sung-vowel tables"),
  `CREDITS.MD`.
- **What**: the table is taken from a published formant table (it matches the Csound manual's, as far as I can recall),
  but neither the KDoc nor `CREDITS.MD` says so, and its third column's original meaning (a bandwidth in Hz) is what
  L7b-02 turns on. The provisional credits rule asks for the source in the change that brings it in.
- **Reach**: documentation only.
- **Settle it**: look the source up and name it in the KDoc and the credits.
- **Confidence**: medium (the source from memory).

## For the audit ruleset

- Every replica that an auditor had validated against probe rows was the engine to 0.1 cent or 0.1 dB when I measured
  it (the pluck, the saw, the sample rows, the release table): a validated replica is good evidence for a mechanism.
- What the replicas did not give was the size in a musical case: L4-02 and L7b-02 were rated on a worst case that the
  corpus's own settings or the defaults shrink by 20 to 40 dB. A finding rated on audibility should carry one musical
  case, measured.
- Two fix estimates were off (the impulse's triangle kernel by 7 dB; the PolyBLEP's "audible band" gain depends on
  whether the band ends at 15 or 20 kHz). An estimate of a fix's gain should name its band and come with a test-code
  reference.
- Counting the corpus moved two severities (no song uses `impulse`; seven songs, not two, use filter envelopes): the
  reach should be a grep result, not a recollection.
- Measuring through `renderFramedVoice` and `katalystOwnerChangeStage` made most reproductions a few lines; emulating a
  stage in test code and proving the emulation bit-identical to the engine (L4-02 c) let a proposed fix be measured
  without touching the engine.
