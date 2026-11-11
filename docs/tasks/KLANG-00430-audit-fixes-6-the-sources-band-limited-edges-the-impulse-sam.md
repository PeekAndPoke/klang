# KLANG-00430: Audit fixes 6: the sources (band-limited edges, the impulse, sample interpolation and the last frame, the noises)

> **Status (2026-11-11): PAUSED.** The decisions pass of 2026-11-11 landed; what is left is named in the ticket.
>
> **Part of:** KLANG-00353 (The Motor: correct at the edges)

## Why this exists

The engine audit (KLANG-00420) found these. Every row is traced in `docs/audio-audit/2026-11-10/audit/` (the layer
report named by the id's layer, and the `disproof-*.md` that measured it); the index is `SUMMARY.md` there. The
maintainer's decision (2026-11-10): "create tickets and fix all of the findings. Sound changes are fine, for example
for the pluck."

## The findings

| Id | Severity now | Finding | Verdict and measurement |
|---|---|---|---|
| L3-02 | major | The "band-limited" saw and square use the weakest member of the BLEP family, and the KDoc's reason is untrue | Confirmed, smaller: A two-point PolyBLEP is 22 to 29 dB cleaner below 5 kHz but only 6 to 8 dB below 20 kHz; +12 semitones of pitch stage costs the saw 20 dB below 5 kHz |
| L3-03 | minor | The impulse aliases at -5.3 dB re its harmonics by construction | Confirmed, smaller: -5.4 to -4.4 dB by construction, a documented single-sample spike nobody uses; the proposed triangle kernel reaches -12.5 dB, not -20 |
| L3-04 | minor | The impulse outputs a run of 1.0 while its phase runs backwards | Confirmed: Ratio -1: 4778 of 4800 samples at 1.0; the built-in impulse under `fm` depth 440 Hz at 220 Hz: 1624 nonzero samples per 0.1 s |
| L3-05 | minor | Sample playback is two-point linear at every rate, and the JVM leaves the whole rate conversion to it | Confirmed, smaller: The auditor's numbers reproduce exactly; a four-point kernel gains 11 to 47 dB on transpositions but only 1 to 15 dB on rate conversion |
| L3-09 | (read only) | The perlin noise repeats every 256 lattice units: 1.78 s at `rate` 1 | not challenged; the auditor's reading in `layer-3-sources.md` |
| L3-10 | (read only) | Four noise colours are set per sample, so they follow the device's sample rate | not challenged; the auditor's reading in `layer-3-sources.md` |
| L3-11 | (read only) | The sources' levels differ by up to 20 dB, and a unison stack gets quieter with every voice | not challenged; the auditor's reading in `layer-3-sources.md` |
| L3-12 | (read only) | At an even voice count the stack has no centre voice, and only one of the two middle voices gets the reduced jitter | not challenged; the auditor's reading in `layer-3-sources.md` |
| L3-13 | minor | Stereo files: the browser keeps the left channel only, the JVM averages; the ref page says both | Confirmed: By reading, the stereo half of L9-07 |
| L3-14 | minor | The playhead never plays a sample's last frame, and a loop over the whole sample writes a zero at every pass | Confirmed: The last frame of a one-shot is never played; `loop(1)` writes a zero at frames 7, 15, 23 ... |
| L3-16 | (read only) | Two rng consumers in one voice make its noise depend on where the block borders fall | not challenged; the auditor's reading in `layer-3-sources.md` |
| L9-07 | minor | The browser and the JVM decode a sample differently, so the offline render of a sample song is not the live sound | Confirmed: By reading: left channel at 48 kHz against the average at the file's rate |

## The work

Per finding: read its report and its reproduction (`src/jvmTest/kotlin/audit/` in `audio_be`, `sprudel` or `klang`,
run with `KLANG_PROBE=audit`), fix it, and turn the reproduction's row into an ordinary spec that guards the fix
(mutation-checked). A finding that turns out to need a maintainer decision is not forced: it is listed under
"Decide" below with the options, and the rest of the ticket lands without it. How the fixes are made and reviewed:
`docs/audio-audit/2026-11-10/audit/FIX-BRIEF.md`.

## Decide

### L3-02 (landed, the maintainer may veto): the box costs about 0.7 ns per oscillator voice per sample

The band-limited saw and square are landed: the gain (22 to 29 dB less aliasing below 5 kHz, and 24 dB under an octave of
pitch modulation) is the finding's whole point, and the cost is a few compares per sample. But the source rows of the cost
probe rise by more than the 10 percent the ticket's brief names, so the number is here (JVM, `KLANG_PROBE=cost`, net ns
per sample, before against after; rows under 2 ns are within the probe's noise; JS in the notes):

| Row | before | after |
|---|---|---|
| saw (bare) | 1.08 | 1.78 |
| square (bare) | 1.28 | 1.99 |
| supersaw, 8 voices | 9.45 | 15.21 |
| supersquare, 8 voices | 10.98 | 15.69 |
| saw voice, default adsr | 5.93 | 6.52 |
| supersaw voice, lpf 1000 and adsr | 18.11 | 23.88 |
| tri, supertri, raw shapes | unchanged within 2.5 percent | |

Options:

1. Keep it (landed).
2. Take it back for the stacks only (`boxScale` 0 in `UnisonStackIgnitor`): the unison stacks alias as before, the mono
   oscillators stay band-limited. One line.
3. Take it back everywhere (`boxScale` 0 in both classes): the sound and the cost of before, bit for bit.

Recommendation: 1.

### L3-02: the square's flank floor (`PULSE_MIN_FLANK_SAMPLES`, 2 samples)

Through the box, the square's two-sample flank is a trapezoid kernel three samples wide: more rejection than a two-point
PolyBLEP (21 to 28 dB less below 5 kHz than before), and 0.7 dB more droop at 10 kHz than before (3.3 dB against 2.6). A
floor of 1 sample would make the square's kernel exactly the saw's, the two-point PolyBLEP: 1.3 dB at 10 kHz (brighter
than before by 1.3 dB) and 4.2 at 18 kHz (before 10.5). It is a constant tuned by ear ("the square is duller at the top
than the saw: is that intended?" is one of the audit's listening tasks).

Options: keep 2 (landed, the square a little duller than before); 1 (brighter than before, the saw's kernel). Recommendation:
a listening pair; without one, keep 2.

### L3-03 (accepted, documented): the impulse is a naive spike train

The impulse writes one whole sample of 1.0 per cycle, which folds about 23 percent of its power back from above Nyquist
(-5.3 dB re its harmonics at every pitch). It is documented as a single-sample spike, nobody uses it in the corpus or the
tutorials, and as an exciter an exact unit impulse is what a filter or comb wants (its impulse response, bit for bit). Now
said in the KDoc with the number.

Options:

1. Accept (landed: the KDoc says it).
2. Split the spike over the two samples round the true crossing (a triangle kernel): -12.7 dB, 7.5 dB better, the spike
   no longer a unit sample, the train no longer jittered between 436 and 437 samples.
3. A band-limited impulse train (a windowed sinc of several taps, or a sum of harmonics, as a separate node `blit`): the
   real cure, a new node.

Recommendation: 1 now, 3 if someone asks for a band-limited click train.

### L3-05, L9-07: the rate conversion of a file not at the engine's rate

The channel rule is the same on both hosts now (the mean). The rate conversion is not: the browser decodes every file at
48 kHz with its own resampler, the JVM keeps the file's rate and converts in the playhead with the cubic read (a 44.1 kHz
file's images of a 10 kHz tone -30.6 dB, from -20.4 with the linear read; a band-limiting converter removes them).

Options:

1. Keep and document (landed: `audio/ref/sample-management.md` says it).
2. Convert at decode on the JVM with a band-limiting resampler (a windowed-sinc converter in `audio_fe`'s JVM decoders, to
   the engine's rate): the offline render then matches the browser's in kind. A new piece of DSP, once per file.
3. Decode in the browser at the ENGINE's rate (`OfflineAudioContext(1, 1, engineRate)`): a browser engine not at 48 kHz
   (`resolveBestSampleRate` takes what the device gives) converts once instead of twice. Needs the engine's rate where the
   decoder is built (`audio_fe/src/jsMain/kotlin/samples.kt`).

Recommendation: 3 (small, one conversion fewer for every 44.1 kHz device), then 2 if the offline renders are to be the
browser's sound in the top octave.

### L3-07: a lossless read for the string

The cubic read leaves a smaller note dependence (the plucks 2 Hz apart at brightness 1: 438.4 Hz -11.6 dB against 440.4 Hz -2.0 dB above 5 kHz, from -65.8 against -3.6 in KLANG-00426's replica of the linear read). The lossless option
of KLANG-00426 (a first-order allpass for the fraction, with a state fix where the delay's whole part changes) stays open.
Recommendation: as KLANG-00426 said, only if a listening round asks for a lossless string at a fixed pitch.

### L3-09: perlin repeats every 256 lattice units

The gradient table has 256 entries, so the walk repeats every 1.78 s at `rate` 1 (0.18 s at `rate` 10). Said in the
source's KDoc and in `audio/ref/voice-synthesis.md`. `PerlinNoise` lives in `common` (the Katalyst perlin modulator reads
the same law), outside this ticket's modules.

Options: 1. keep and document (landed); 2. hash the lattice index (as berlin does) so the walk has no period: every perlin
voice and modulator renders another curve (same statistics); 3. a larger table (4096): the period becomes 28 s at rate 1,
the same curve over the first 256 units only if the table extends the old one.

Recommendation: 2, with the corpus moves named (every song with a perlin source or modulator).

### L3-10: four noise colours follow the device's rate

White's tilt, brown's leak, pink's Kellet filter and crackle's DC pole are per-sample coefficients: the corners move with
the device (brown 154 Hz at 48 kHz, 309 at 96). Said in the KDocs and the ref page.

Options: 1. keep and document (landed); 2. rate-scale white's and brown's single pole from its 48 kHz value
(`1 - (1 - c)^(48000 / fs)`): one line each, the corner fixed, the 48 kHz render moves by rounding only (a corpus hash
moves for every song with these noises); pink needs its 7 coefficients refitted per rate, crackle's map is per-sample
chaos by nature and stays. Recommendation: 2 for white and brown, pink and crackle documented.

### L3-11: the sources' levels, and a unison stack quieter with every voice

The stack's gains sum to 1 (a peak of 1 at any count), so `voices` is also a volume knob: 2 voices -3 dB, 8 voices
about -11 dB, 16 voices -12 dB re one. Brown noise sits 20 dB under white, pink 9 dB.

Options: 1. keep (the peak law, documented); 2. constant power (gains sum of squares 1): the level steady across counts,
peaks above 1 at coherent moments (the Motor is raw); 3. halfway (`1 / v^0.75`). For the noises, a level match of pink and
brown to white (a fixed gain each): every song with those noises moves by a constant.

Recommendation: a listening pair for 2 against 1 at 2, 8 and 16 voices; the noise gains left as they are (a song set its
levels by ear).

### L3-12: an even voice count has no centre voice

At the default 8 voices two middle voices beat at about +-1 cent, and only one of them gets the reduced jitter; "center
dominant" no longer describes `SUPERSAW_SIDE_ATTEN` 0.1.

Options: 1. an odd default (7 or 9): the centre voice the KDocs describe, every default stack moves; 2. at an even count,
give both middle voices the centre's jitter scale: a small change, every even stack's jitter moves; 3. document only.

Recommendation: 2 (the rule then holds at every count), and a listening pair for 1.

### L3-16 (accepted, documented): two per-sample rng consumers in one voice

White, brown, pink and dust draw from the voice's one stream per sample, node after node, so with two of them in a voice
their samples (not their statistics) depend on where the block borders fall. Said in `audio/ref/voice-synthesis.md`,
"The voice rng". The cure is a per-node generator seeded from the voice stream at build (the `AnalogDrift` pattern, an
inline xorshift): it also removes a virtual call per sample (about 1 ns on the JVM, 3 to 4 on JS), and every noise voice
renders other dice. Recommendation: do it with the noise cost work, as one deliberate sound change.

### Decided (maintainer, 2026-11-11)

- **L3-02 cost:** kept while the maintainer reads an explanation of band-limiting ("if so the additional cost is ok").
- **L3-02 flank floor:** a listening pair (2 samples against 1). Owed.
- **L3-03:** "yes accept".
- **L3-05, L9-07 rate conversion:** the maintainer asked for an explanation; open.
- **L3-07:** not built (KLANG-00426, "no listening pairs needed").
- **L3-09:** "ok fix the songs": option 2, hash the lattice; the songs that move are named. Owed.
- **L3-10:** "ok fix and document": white and brown rate-scaled, pink and crackle documented. Owed.
- **L3-11:** "the volume difference is fine, keep, no listening pair".
- **L3-12:** "yes as proposed for both center voices, no listening pair needed": option 2. Owed.
- **L3-16:** "yes fix together with the noise cost work".

### Decided, second round (maintainer, 2026-11-11)

- **L3-02 cost:** "band-limiting is a real good thing and we keep it."
- **L3-02 flank floor:** "let the square have the same edge width as the saw": `PULSE_MIN_FLANK_SAMPLES` 2 to 1, no
  listening pair. Every square and pulse gets brighter (1.3 dB down at 10 kHz, was 2.6 before the audit). Owed.
- **L3-05, L9-07:** "yes first now and follow-up ticket for the second": the browser decodes at the engine's rate
  (owed); the JVM converter is KLANG-00438.

## Notes from the work (2026-11-10)

The fixer's record. Before and after are the tree as found (`6c10fe418`) against the tree with these changes; the probes
ran on both through the lock (`KLANG_PROBE=aliasing`, `cost`, `clicks`, `audit`).

### Per finding

| Id | Outcome | What | Guard | Sound |
|---|---|---|---|---|
| L3-02 | FIXED | The saw, ramp, rounded pulse and the supersaw, superramp and supersquare stacks read their trapezoid through a centred box one sample's increment wide (`waveTrapezoidBoxed`, `WaveVoiceState.kt`): a residual at each corner within half a step, the step taken per sample (pitch modulation and drift included). For the saw's one-sample flyback that is exactly the two-point PolyBLEP's kernel; the square's two-sample flank gets a trapezoid kernel three samples wide. The triangle, the raw shapes (`zaw`, `zamp`, `pulze`), the supertri, the tremolo's LFO and the Katalyst knob LFOs stay the plain shape (`bandLimit = false`, the plain `waveTrapezoid` chosen at the call). The untrue KDoc ("a finite-slope edge is inherently band-limited") and the "no PolyBLEP" and "band-limited" words (`Ignitors.kt`, `IgnitorDefaults.kt`, `OscillatorTuning.kt`) say what the code does. The aliasing meter has a column for it (`nonh<5k`). | `BandLimitedSourcesSpec` (every trapezoid loop, and an octave of pitch modulation) | yes |
| L3-03 | ACCEPTED | The naive spike is the node's documented design and an exact unit impulse for an exciter; its aliasing (-5.3 dB re its harmonics) is now in the KDoc with the number. Options under "Decide". | the audit reproduction stays | no |
| L3-04 | FIXED | The impulse's plain loops spike on a drop of the phase of more than half a cycle (the phased loop's rule): a phase running backwards spikes nothing. Ratio -1 at 220 Hz: 4778 of 4800 samples at 1.0 before, 1 after (the start); the built-in `impulse` under `fm` depth 440 Hz: 1624 nonzero samples per 0.1 s before, 44 after. | `ExcitersTest`, "impulse - a phase running backwards spikes nothing" (all four plain loops) | no corpus move (forward below Nyquist the two rules are the same) |
| L3-05 | FIXED (the read) | The sample playhead reads with a four-point cubic Lagrange kernel (`lagrange4`, `SampleIgnitor.kt`). The rate-conversion half is L9-07's: "Decide". | `SampleIgnitorTest` (an oracle from the Lagrange product formula), `BandLimitedSourcesSpec` (the meter at 4186 Hz) | yes |
| L3-07 | FIXED (option 2 of KLANG-00426) | The Karplus string reads its line with the same cubic Lagrange kernel; `loopDelayOf` models the new read's phase and gain (the weights of the delay `1 + d` over four taps), so the tuning stays exact. The minimum read delay stays 2 (at a whole delay of 2 the newest tap is read with weight 0). | `KarplusStringSpec` (the read against the product formula; the pole oracle with the Lagrange read), `PluckReadSpec`, `PluckPitchSpec` green | yes |
| L3-09 | DECIDE (documented) | The period is in the perlin source's KDoc and in `audio/ref/voice-synthesis.md`; the fix lives in `common` and moves every perlin. | none (no code change) | no |
| L3-10 | DECIDE (documented) | The four per-sample coefficients are named in the KDocs and the ref page. | none | no |
| L3-11 | DECIDE (documented) | The stack's sum-to-one law and its 3 dB per doubling are in the ref page. | none | no |
| L3-12 | DECIDE | Options below; the "center-dominant" wording belongs to KLANG-00433 (L3-15). | none | no |
| L3-13 | FIXED | The browser decoder averages every channel, as the JVM decoders do (it kept channel 0, the left). The ref page said both; it says the one rule now. | `BrowserAudioDecoderSpec` (audio_fe, browser: stereo WAVs built in the test) | browser only (no corpus move) |
| L3-14 | FIXED | The read sees silence before frame 0 and after the last frame, so a one-shot plays its last frame and falls to 0 within one frame; while looping, a frame past the loop's end is the loop's start again and, after the first wrap, a frame before its start is its end. A loop over the whole sample writes no zero at its seam. | `SampleIgnitorTest` (the last frame, the seam both ways), `SamplePlayheadStartSpec` (the seam on a soundfont loop) | yes |
| L3-16 | ACCEPTED (documented) | Said in `audio/ref/voice-synthesis.md`, "The voice rng"; the cure is under "Decide". | none | no |
| L9-07 | FIXED (channels), DECIDE (rate) | The channel rule is one on both hosts; the rate conversion differs by design of the two decoders, which `audio/ref/sample-management.md` now says, with the numbers. | as L3-13 | browser only |

### Measurements

Aliasing meter, the bare sources at analog 0 (`nonh_dB` is the meter's total, `nonh<5k` the new column; the "before"
below 5 kHz is the audit reproduction's band split of the same render):

| Source, pitch | total before | total after | below 5 kHz before | below 5 kHz after |
|---|---|---|---|---|
| saw 110 | -33.4 | -39.5 | -56.7 | -78.8 |
| saw 1244.5 | -23.2 | -29.3 | -46.6 | -68.5 |
| saw 4186 | -17.0 | -22.5 | -44.3 | -71.6 |
| saw 7044.4 | -15.9 | -22.3 | -44.3 | -72.9 |
| square 110 | -44.8 | -54.0 | -59.9 | -82.0 |
| square 1244.5 | -34.5 | -43.4 | -49.3 | -70.6 |
| square 4186 | -28.6 | -37.1 | -47.7 | -75.2 |
| square 7044.4 | -25.5 | -35.4 | -44.2 | -72.5 |
| saw +12 semitones by `pitchModSemitones` or `accelerate`, heard at 1244.5 | -17.2 | -24.8 | -26.4 | -50.6 |
| saw +12 semitones, heard at 4186 | -11.4 | -18.5 | -22.0 | -49.2 |
| saw +24 semitones, heard at 4186 | -9.3 | -17.4 | -18.3 | -45.8 |
| sample a semitone up, 1244.5 | -61.1 | -108.0 | | -108.2 |
| sample a semitone up, 4186 | -39.8 | -65.9 | | -67.6 |
| sample a semitone up, 7044.4 | -30.2 | -47.8 | | -49.0 |
| 44.1 kHz file, 10 kHz tone (the JVM's rate conversion) | -20.4 | -30.6 | | |

The ramp, supersaw, superramp (voices 1) read as the saw; the supersquare as the square; tri, supertri, zawtooth, zamp,
pulze, sine and the impulse are unchanged to the bit. The saw's fundamental is unchanged to 0.01 dB below 2 kHz; the
price is the PolyBLEP's treble droop, 1.3 dB at 10 kHz (was 0.6) and 4.2 dB at 18 kHz (was 2.1); the square's 3.3 dB at
10 kHz (was 2.6). The sample's level loss on a transposed 7 kHz tone falls from 0.55 to 0.07 dB.

The pluck at brightness 1, energy above 5 kHz re the whole from 0.2 s (`PluckReadSpec`): 438.4 Hz -11.6 dB, 440.4 Hz -2.0
dB (the linear read: KLANG-00426's replica -65.8 against -3.6; the mutant below reads the engine's own).

Click probe: no new click; the saw, square and ramp rows keep jump and kink at or under 1.0 of the steady signal's (the
largest second difference only moved to another place); their steady high band is 1.3 to 1.9 dB lower.

Cost (`KLANG_PROBE=cost`, JVM, net ns per sample, the median of 41 rounds; before is the tree as found, after the final
tree; rows under 2 ns sit in the probe's noise, and two runs of the same tree differ by up to 10 percent, by 25 percent on
the supersaw in one of four runs):

| Row | before | after |
|---|---|---|
| saw (bare) | 1.08 | 1.78 |
| square (bare) | 1.28 | 1.99 |
| supersaw, 8 voices (default) | 9.45 | 15.21 |
| supersquare, 8 voices | 10.98 | 15.69 |
| saw voice, default adsr | 5.93 | 6.52 (+10 percent) |
| saw voice, lpf 1000 and adsr | 9.83 | 10.50 (+7 percent) |
| supersaw voice, lpf 1000 and adsr, 8 voices | 18.11 | 23.88 (+32 percent) |
| saw voice, accelerate 12 st | 3.01 | 3.78 |
| sample playhead, a semitone up | 1.61 | 2.86 |
| sample instrument, default adsr, a semitone up | 6.45 | 7.67 (+19 percent) |
| pluck (bare) | 4.40 | 5.19 |
| superpluck, 8 strings | 47.61 | 44.34 (the conditional wraps of the cubic read replace three `%`) |
| tri, supertri, zawtooth, zamp, pulze, impulse, sine | unchanged within 2.5 percent | |

Kotlin/JS (`runStageCostJs()`, the production test bundle under node, three processes, ratio to the plain sine; the
"before" column is the audit's own run of 2026-11-10 under another load, not a same-session run of this tree's parent,
which was not made): saw 0.45 to 0.85, square 0.55 to 0.96, supersaw 8 voices 4.73 to 7.58, supersaw voice 7.77 to
10.69, saw voice with adsr 1.92 to 2.47, sample playhead 0.91 to 1.57, sample instrument 2.43 to 3.06, pluck 1.77 to
2.07, superpluck 14.21 to 17.25. Rows this change did not touch moved too (the impulse 0.85 to 1.00, the triangle 0.49
to 0.67, the supertri 5.19 to 6.41), so part of each JS move is the other run's conditions; a same-session pair is needed
before quoting a JS percentage.

The box costs about 0.7 ns per oscillator voice per sample on the JVM, the cubic sample read about 1.3 ns per sample
voice: well past the 10 percent the ticket's brief names on the bare source rows, 7 to 32 percent on whole voices. Landed
because the gain is the finding (see "Decide" for the way back, one line per class).

### The corpus, before against after

Run once before the first edit and once after (`runSongBenchmark --args=corpus`, through the lock), with an RMS line per
song added for the after runs only (a temporary print in `CorpusCheck`, restored byte for byte). Each move is attributed
by a run with one fix taken out: the box off (`boxScale` 0), the string's read and `loopDelayOf` as before, the sample
read as before. A run with all three taken out renders all 18 rows bit for bit as the before run: nothing else in this
change moves a song (L3-04 and the browser decoder included). No song was edited between the runs.

| Song | Moved by | Peak before, after | RMS before, after (dB) |
|---|---|---|---|
| A Synth Worth Lying For | L3-02 box, L3-05, L3-14 sample read | 0.7555, 0.7555 | -13.975, -13.979 (-0.004) |
| Der Schmetterling | L3-02 box, L3-05, L3-14 sample read | 0.7525, 0.7525 | -15.570, -15.568 (+0.002) |
| Die Kirschblüte | L3-07 pluck read | 0.7468, 0.7467 | -20.445, -20.423 (+0.022) |
| Drunken Synthlor | L3-07 pluck read, L3-05, L3-14 sample read | 0.8334, 0.8459 | -19.856, -19.752 (+0.104) |
| Gestrandet | L3-05, L3-14 sample read | 0.0694, 0.0694 | -44.211, -44.211 (+0.000) |
| Greensleeves Chant | L3-07 pluck read | 0.4526, 0.4530 | -24.047, -24.040 (+0.006) |
| Kokon | L3-02 box, L3-05, L3-14 sample read | 0.7447, 0.7438 | -18.266, -18.265 (+0.000) |
| Remix: Echo um Echo | L3-02 box, L3-05, L3-14 sample read | 0.4881, 0.4950 | -25.180, -25.156 (+0.024) |
| Seltsamere Dinge | L3-02 box, L3-05, L3-14 sample read | 0.7456, 0.7455 | -17.126, -17.135 (-0.009) |
| Smalltown Synth | L3-02 box, L3-05, L3-14 sample read | 0.9108, 0.9110 | -15.264, -15.261 (+0.003) |
| Stein um Stein | L3-02 box, L3-05, L3-14 sample read | 0.7606, 0.7605 | -16.129, -16.129 (+0.000) |
| Synthsturm | L3-02 box | 0.8271, 0.8272 | -18.562, -18.563 (-0.000) |
| The Synthsale Piper's Farewell | L3-02 box, L3-07 pluck read | 0.3490, 0.3498 | -27.848, -27.840 (+0.008) |
| The Synthsale Piper's Last Rave | L3-02 box, L3-05, L3-14 sample read | 0.8952, 0.8958 | -15.613, -15.609 (+0.004) |
| frozen derSchmetterling_2026_11_03 | L3-02 box, L3-05, L3-14 sample read | 0.7446, 0.7446 | -16.916, -16.914 (+0.002) |
| frozen piece derSchmetterling_2026_09_16 | L3-02 box, L3-05, L3-14 sample read | 0.8994, 0.8994 | -18.394, -18.393 (+0.001) |
| frozen strangerThings_2026_11_03 | L3-02 box, L3-05, L3-14 sample read | 0.7456, 0.7455 | -17.126, -17.135 (-0.009) |

All moves are under 0.11 dB RMS. The largest: Drunken Synthlor (+0.10 dB, its pluck `sndPluck` and its samples), Remix:
Echo um Echo (peak +0.6 dB, its sample read across a loop seam). Final Synthasy VII Prelude did not move.

Pinned renders regenerated for the sound change: `BuiltInVoiceMatrixSpec` (36 of 86 rows: every saw, square, ramp,
pulse, their stacks, the Karplus family and the built-ins made of them; both stiffness pins), `PhasePoolBypassGoldenSpec`
(the 12 supersaw, superramp and supersquare weighted sums; supertri and supersine unchanged within the tolerance, the
rng stream untouched), `SuperStackDriftSpreadSpec` (22 of 256 samples, at the flybacks). Changed to the fixed behaviour:
`SampleIgnitorTest` (the last frame is played; the half-frame values are the cubic's), `OscillatorPhaseSpec` (the
first sample of the saw, ramp and square reads the corner through the box: -0.746, +0.746, -0.875 at 750 Hz),
`SamplePlayheadStartSpec` (the frame on the seam reads across it), `KarplusStringSpec` (the pole oracle models the cubic
read). The tremolo and Katalyst knob oracles build their Ignitor with `bandLimit = false`, as those LFOs are.

### Mutation campaign

One campaign (`console/tools/mutate`, under the lock), each mutant the fix taken out or bent; all 19 KILLED for the
expected reason (the message matched), every file restored byte for byte, the final run green.

| Mutant | Verdict | Spec |
|---|---|---|
| box-wave-off | KILLED | BandLimitedSourcesSpec |
| box-stack-off | KILLED | BandLimitedSourcesSpec |
| box-unmodulated-step | KILLED | BandLimitedSourcesSpec |
| box-pwm-loop-off | KILLED | BandLimitedSourcesSpec |
| box-phased-loop-off | KILLED | BandLimitedSourcesSpec |
| box-stack-phased-off | KILLED | BandLimitedSourcesSpec |
| box-residual-fall-sign | KILLED | BandLimitedSourcesSpec |
| box-near-check | KILLED | BandLimitedSourcesSpec |
| impulse-rule-drift-pm | KILLED | ExcitersTest |
| impulse-rule-plain | KILLED | ExcitersTest |
| sample-linear-read | KILLED | SampleIgnitorTest |
| sample-linear-read-aliasing | KILLED | BandLimitedSourcesSpec |
| sample-no-seam-forward | KILLED | SampleIgnitorTest |
| sample-no-seam-backward | KILLED | SampleIgnitorTest |
| sample-last-frame-silent | KILLED | SampleIgnitorTest |
| karplus-linear-read | KILLED | KarplusStringSpec |
| karplus-linear-read-brightness | KILLED | PluckReadSpec |
| karplus-model-linear | KILLED | KarplusStringSpec |
| browser-left-channel-only | KILLED | BrowserAudioDecoderSpec |

### Review round 1 (2026-11-11)

- The loop seam's moves in `SampleIgnitor.edgeFrame` are one integer division each, of non-negative numbers only: after
  a wrap, a playhead that a negative phaseMod carries far below a soundfont loop's start used to step one loop length at
  a time (600 steps per frame for a 100-frame loop at frame 60000, four frames per sample). A frame below -1 can only be
  an overflowed index and reads as silence first. The forward move is the same arithmetic as before (no corpus move:
  the forward case gives the same frame). Guard: `SampleIgnitorTest`, "a playhead carried far below a wrapped loop's
  start reads the loop's own frames". Mutants: the backward count off by one (KILLED, the expected 59994.25 missed),
  the backward move dropped (KILLED), the forward count one short (KILLED by the seam rows); the first run of the
  off-by-one mutant read KILLED-OTHER only because its expected-message pattern was wrong (it named 6000x), rerun alone
  with the right pattern: KILLED.
- A same-session Kotlin/JS cost pair (one pair, three node processes each, the production test bundle, ratio to the
  sine): before is this tree with the box switched off (`boxScale` 0) and `SampleIgnitor.kt` and `KarplusString.kt` as
  at the parent, after is this tree. Clean for the sample and the string: sample playhead a semitone up 0.89 to 1.58
  (+78 percent), sample instrument with adsr 2.37 to 3.09 (+30), pluck 1.76 to 2.01 (+14), superpluck 8 strings 14.53 to
  17.07 (+17). For the oscillators the before still holds the new loop shape, so it reads the box itself, not the
  parent: saw 0.78 to 0.88 (+12), saw voice with adsr 2.25 to 2.41 (+7), supersaw 8 voices 5.00 to 7.77 (+56), supersaw
  voice with lpf and adsr 7.95 to 10.56 (+33), supersquare 8 voices 5.51 to 8.16 (+48). Rows whose code path is the same
  in both moved by up to 26 percent between the two builds (tri 0.85 to 0.67, pulze 0.89 to 0.65), so under 1 x sine
  only the large moves mean anything. A parent build of the oscillator files needs a second pair (the specs that name
  `bandLimit` would need their parent versions too); not made, as the coordinator's limit was one pair.

### Seen on the way, not fixed

- `AnalogDrift` at a step rate of 10 per second (a 4410-frame block at 44.1 kHz) seeds non-finite coefficients and the
  drift loops stand still (found through a test harness; the engine's block is 128 frames, so its rate is at least 344).
- `audio/ref/sample-management.md` still shows `MonoSamplePcm.pcm: FloatArray` and `decode(bytes, mimeType)`, and
  `SampleIgnitor`'s KDoc says "Perlin noise drift": KLANG-00433 (L3-15) owns those texts. The one stale path in
  `audio/ref/voice-synthesis.md` ("live in `ignitor/OscillatorTuning.kt`") was in a row this change rewrote, and is fixed.


## Decisions pass (2026-11-11)

### What was built

| Id | What | Guard (mutation-checked) | Sound |
|---|---|---|---|
| L3-02 flank | `PULSE_MIN_FLANK_SAMPLES` 2 to 1: the square's edge is the saw's, its kernel the two-point PolyBLEP's. Every reader follows the one constant: the rounded pulse and square, the supersquare family, the Katalyst square knob LFO (its oracle `handSquare` and two written-out values moved), the triangles (their open flanks are wider than the floor at every pitch below 12 kHz, so they do not move there; the supertri rows moved by L3-12) | `BandLimitedSourcesSpec` (the square's droop at 11 kHz within 0.3 dB of the saw's: -1.4 and -1.2 dB); `OscillatorPhaseSpec` (the square's first sample -0.875 to -0.75) and `KatalystKnobModulatorSpec` (the rise at half and a quarter: 0 and -0.5) moved to the new edge | yes |
| L3-09 | `PerlinNoise` (`common`) hashes the lattice index with `BerlinNoise`'s Int finalizer: no period. One `nextInt` for the seed (it drew 255 for its table). The Katalyst perlin knob's clock wrapped at the old period of 256 cells; it wraps at `NOISE_CLOCK_SPAN` (2^23, the berlin's) now, or it would bring the period back with a step. `PerlinNoise` is also sprudel's `perlin` pattern signal (`lang_continuous_waves.kt`), so that curve changes too | `PerlinNoiseSpec` (no repeat at 256, 512, 1024, 65536; one draw; an oracle in 64-bit arithmetic on both hosts; the RMS and mean of the 16 equal gradients through the fade); `KatalystKnobModulatorSpec`'s three period rows moved to the new span | yes, every perlin curve |
| L3-10 | White's tilt and brown's leak are given at 48 kHz and scaled to the block's rate (`NOISE_COEFF_REFERENCE_RATE`): the pole to the power `48000 / fs`, `1 - 0.85^(48000/fs)` for the tilt, `(1 + k)^(48000/fs) - 1` for the leak. One `pow` per block. At 48 kHz NOT bit-identical: `1 - (1 - 0.15)` and `1.02 - 1` round off by an ulp, so a song with brown or a tilted white moves by rounding. Pink and crackle documented in their KDocs and the ref page | `NoiseRateScaleSpec` (the node's output against the recurrence with the scaled pole at 44.1, 48 and 96 kHz; the time constant in seconds the same at every rate) | rounding at 48 kHz; the corner at other rates |
| L3-12 | At an even count both middle voices (`(v - 1) / 2`, `v / 2`) take the centre's jitter scale. The "center-dominant" wording made true where it was not: "center-weighted", and the side attenuation's 1 is "falling to 0 at the outermost voices", not "only the centre voice" (the constants, the node and builder KDocs, both doors, `superSawVoiceGains`, the ref page) | `UnisonCenterJitterSpec` (a supersine at spread 0 with a scripted stream against the gain law written out, 2 to 9 voices; its first draws left the upper middle voice at a draw of 0.5, where scale does not matter, and a mutant survived: the draws were fixed and it is KILLED) | yes, every even stack |

Pinned renders regenerated, row by change: `BuiltInVoiceMatrixSpec` `square`, `sqr`, `pulse`, `sgbuzz` (the flank),
`supersaw`, `superramp`, `supersine`, `supertri` (L3-12), `supersquare`, `supersqr`, `superpulse` (both), `brownnoise`,
`brown` (L3-10 rounding), `perlinnoise`, `perlin` (L3-09), and the Karplus rows (KLANG-00426, L3-08), both rows each;
`PhasePoolBypassGoldenSpec` the 8-voice entries of every stack (L3-12) and every `superSquare` entry (the flank), the
11-voice saw, ramp, triangle and sine entries kept (within the tolerance).

### Probes, before against after (the tree as found against this pass, JVM)

Aliasing meter, the bare square (the square node, `pulze` at duty 0.5 and the supersquare at one voice read alike):

| Pitch | harmonics re fund. | non-harmonic total | below 5 kHz |
|---|---|---|---|
| 110 | -6.5 to -6.4 | -54.0 to -42.5 | -82.0 to -81.6 |
| 1244.5 | -8.2 to -7.5 | -43.4 to -33.1 | -70.6 to -70.3 |
| 2637 | -10.7 to -8.9 | -40.0 to -32.0 | -70.2 to -70.1 |
| 4186 | -14.3 to -10.7 | -37.1 to -28.8 | -75.2 to -75.5 |
| 7044.4 | -28.5 to -14.9 | -35.4 to -32.7 | -72.5 to -73.2 |

The square is brighter (its top harmonics up to 13.6 dB), and its total off-harmonic energy rises with the top band it
lets through, all of it above 5 kHz, where the box puts the saw's too; below 5 kHz, where an alias is heard, it is where
it was. No other source row moved (perlin's rows are by design).

Click probe: no new click. The square voice's rows keep jump and kink at or under 1.0 of the steady signal's at the gate
end and the cut; the note-start kink ratio reads 650 to 1155 (the onset row of every oscillator reads in the hundreds,
the supersaw 663 on both trees); the stacks' rows move within their seeds; the perlin rows move with the curve.

Cost (`net_ns`, the median of 41 rounds; the whole after run ran 10 to 20 percent faster on rows this pass does not
touch, the saw voice included, so the ratio to the sine is the number to read): perlin 2.83 to 3.20 ns (x sine 1.18 to
1.44, about +22 percent: the hash's multiplies against two table reads); brown 3.76 to 3.65 (x sine 1.57 to 1.64, the
`pow` per block, within the probe's noise); white bare unchanged (its tilt is bypassed at color 0); the square 2.14 to
1.70 and the stacks 10 to 20 percent lower, no voice row higher. No voice row rose by more than 10 percent.

### The corpus

The corpus, my own before (the tree as found, `5e39f1c12`, before the first edit) against after (`runSongBenchmark
--args=corpus`, 48 kHz, a temporary RMS print in a throwaway worktree's `CorpusCheck`); no song text changed between the
runs. Each move is attributed by five more runs, each with ONE change of the pass taken out (the pick, the flank, the
perlin hash, the noise rate scaling, the jitter rule): a change is named where taking it out moves the song's hash. Songs not listed did not move (A Synth Worth Lying For, Final Synthasy VII Prelude).
"perlin" is the engine's perlin source and Katalyst knob and sprudel's `perlin` signal together (one class); "noise" is
the 48 kHz rounding of brown's leak and white's tilt.

| Song | Moved by | Peak before, after | RMS before, after (dB) |
|---|---|---|---|
| Der Schmetterling | perlin (alone) | 0.7525, 0.7534 | -15.568, -15.568 (+0.000) |
| Die Kirschblüte | pick, perlin, noise | 0.7467, 0.7651 | -20.423, -20.182 (+0.241) |
| Drunken Synthlor | pick (alone) | 0.8459, 0.8806 | -19.752, -18.926 (+0.826) |
| Gestrandet | perlin, noise | 0.0694, 0.0750 | -44.211, -43.574 (+0.637) |
| Greensleeves Chant | pick, perlin, noise | 0.4530, 0.4758 | -24.040, -23.917 (+0.123) |
| Kokon | perlin (alone) | 0.7438, 0.7447 | -18.265, -18.211 (+0.054) |
| Remix: Echo um Echo | flank, noise, jitter | 0.4950, 0.4933 | -25.156, -25.153 (+0.003) |
| Seltsamere Dinge | flank, perlin, noise | 0.7455, 0.7455 | -17.135, -17.134 (+0.001) |
| Smalltown Synth | jitter (alone) | 0.9110, 0.9109 | -15.261, -15.261 (+0.000) |
| Stein um Stein | perlin, jitter | 0.7605, 0.7608 | -16.129, -16.129 (+0.000) |
| Synthsturm | flank (alone) | 0.8272, 0.8282 | -18.563, -18.563 (+0.000) |
| The Synthsale Piper's Farewell | pick, perlin, noise | 0.3498, 0.3545 | -27.840, -27.582 (+0.258) |
| The Synthsale Piper's Last Rave | flank, noise, jitter | 0.8958, 0.8953 | -15.609, -15.608 (+0.001) |
| frozen derSchmetterling_2026_11_03 | perlin (alone) | 0.7446, 0.7451 | -16.914, -16.911 (+0.003) |
| frozen piece derSchmetterling_2026_09_16 | perlin (alone) | 0.8994, 0.8993 | -18.393, -18.393 (+0.000) |
| frozen strangerThings_2026_11_03 | flank, perlin, noise | 0.7455, 0.7455 | -17.135, -17.134 (+0.001) |

The largest: Drunken Synthlor +0.83 dB RMS (its `sndPluck` at the default pick: the whole string carries noise now) and
Gestrandet +0.64 dB. The shimmer ring and the drive read move no song (none has a shimmer or a non-finite drive).

## Acceptance

- Every row above is fixed with a guarding spec, or listed under "Decide" with its reason.
- The suites are green, the corpus render is identical except where a fix changes the sound on purpose, and each such
  change is named in this ticket with the songs it moves.
- The engine health page shows the fixed items green after a new probe run.

## History

- 2026-11-10: created.
- 2026-11-10: IN PROGRESS. Started.
- 2026-11-11: PAUSED. Landed; decisions pending with the maintainer (cost of the box and the cubic read, rate conversion, L3-03, L3-09 to L3-12, L3-16).
- 2026-11-11: IN PROGRESS. Decisions pass: building what the maintainer decided on 2026-11-11.
- 2026-11-11: PAUSED. The decisions pass of 2026-11-11 landed; what is left is named in the ticket.
