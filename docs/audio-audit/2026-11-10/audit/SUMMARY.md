# The engine audit of 2026-11: every finding, with its verdict

120 findings from ten layer audits (`layer-*.md`, read by Fable 5.2, no engine code run), then a disproof pass by a
second model (`disproof-*.md`, Opus 5.5 at max effort) that challenged 69 of them and measured in the engine wherever
a claim could be measured. This file is the index: one row per finding, generated from those reports. Nothing here is
fixed; the maintainer picks what becomes a ticket.

- Challenged: 69 of 120. Disproved: 0. Smaller than claimed: 18. Larger: 4.
- "Not challenged" rows carry the auditor's own severity and confidence only: read, not measured.
- The reproductions are specs that run only with `KLANG_PROBE=audit` (`audio_be/src/jvmTest/kotlin/audit/`,
  `sprudel/src/jvmTest/kotlin/audit/`, `klang/src/jvmTest/kotlin/audit/`); a fix turns its row into a guard.

| Severity (after the disproof pass) | Findings | of them challenged |
|---|---|---|
| critical | 2 | 2 |
| major | 16 | 16 |
| minor | 73 | 44 |
| note | 29 | 7 |

## Critical

| Id | Tag | Finding | Verdict | The measurement, or the auditor's claim |
|---|---|---|---|---|
| L3-01 | bug / sound | The pluck is flat: the loop filter's delay is not taken out of the delay line | Confirmed | Measured in the engine by two methods agreeing within 0.001 cent there: the default pluck is -15.75 cents at A4 and -36.73 at C6; brightness 1 reads 0.00 |
| L7b-01 | safety | One NaN on the side chain makes the duck's gain NaN until the duck's life ends | Confirmed | one NaN on a duck's source silences every playback until the ducked cylinder's notes end; a swap onto a ducking chain carries it, onto a plain chain ends it; the fix is right |

## Major

| Id | Tag | Finding | Verdict | The measurement, or the auditor's claim |
|---|---|---|---|---|
| L1-01 | safety | A NaN `pan` is the one typed wire number read raw: the voice writes NaN into its cylinder | Confirmed | `pan("NaN")`, `pan(Math.sqrt(-1))`, `pan(pow(-1)).pan(mul(0))` all put NaN on the wire; the whole output of every playback is exact zero while the voice lives |
| L1-02 | safety | A non-finite `freqHz` on a sample voice makes every sample after the first NaN | Confirmed, larger | a NaN pitch on a sample voice measured as described; the Karplus family (`pluck`, `string`, `superpluck`, `ks`) does the same, so `note("NaN").s("pluck")` silences everything too |
| L2-01 | sound | A voice that ends in its own envelope is cut on the de-click's lag: a step at the last frame that grows as the release gets shorter | Confirmed | The engine is the auditor's model to two digits: -49.6 dB left on the last frame at the default release, -23.4 at 5 ms; the probe's clean sine was a zero crossing |
| L2-04 | safety | A gate that ends before the onset can hand the tree a NEGATIVE window length for one block | Confirmed, larger (was minor) | a crash of all audio from a pattern: `note("c3*8").s("saw").pregain(0).legato(-0.001).adsr(release = 0)` throws `IllegalArgumentException` out of the render |
| L3-02 | sound / doc | The "band-limited" saw and square use the weakest member of the BLEP family, and the KDoc's reason is untrue | Confirmed, smaller | A two-point PolyBLEP is 22 to 29 dB cleaner below 5 kHz but only 6 to 8 dB below 20 kHz; +12 semitones of pitch stage costs the saw 20 dB below 5 kHz |
| L4-09 | realtime | Shimmer allocates its 96000-sample ring at every voice build, on the audio thread | Confirmed | Shimmer at wet 0: 774,520 B and 71 us per note; 16 in one block 1.6 ms median, 4.8 ms max |
| L5-01 | bug | A fused filter loses its gate: an unset cutoff plays a 1 kHz filter | Confirmed | An unset `hpf.freq` slot plays a 1 kHz highpass when optimized: -26.3 dB at 220 Hz, through the registry too |
| L5-02 | bug | The inline instrument's name is structural, the build shares by identity: an edit that only changes the sharing is not heard | Confirmed | One name (`ignitor-0`) for two trees whose renders differ by 3 dB RMS and in waveform |
| L6-01 | safety | Nothing stops a non-finite sample between a voice and the house limiter, and the duck turns one into a bus that is NaN for good | Confirmed | measured end to end: no guard between a voice and the house limiter; the duck turns it into silence of everything |
| L6-02 | bug | A NaN `pan` makes the voice's whole output NaN on both channels; `gain` is guarded twice, `pan` nowhere | Confirmed | same defect; its "0 divided by 0" route is wrong (`div` returns null for a zero divisor), the other routes stand |
| L7a-01 | sound | A delay or a reverb switched on while the bus sounds feeds the unit a step | Confirmed | Delay switched on under a held tone: kink 120.6 at 250 ms, burst -22.8 dB |
| L7a-02 | sound | A chain swap feeds the arriving chain's room and delay at full level from the first block | Confirmed | A swap into a delay reads exactly like switching it on; the room's first return is at 25.3 ms, inside the 60 ms ramp |
| L7b-06 | realtime | `DelayLine.reset` zeroes the whole ring in one block, whatever its size | Confirmed | 10 s delay: 571 us to clear, 953 us where the drain end and the deactivation share a block; a missed block from about a 32 s delay |
| L8-03 | safety, realtime | An exception inside the render block ends the audio for good on both platforms, and nobody is told | Confirmed | no catch on either host; a throw from wire-legal song input now exists (L2-04) |
| L9-01 | safety | One exception anywhere in a command or a block ends all audio for the session, on both hosts | Confirmed | same; the throw leaves `renderBlock` (measured), and the throwing voice stays in the active list |
| L9-02 | bug | A `ScheduleVoices` that arrives after its playback's `Cleanup` revives the playback: late ghost notes and an engine that never ends | Confirmed | Both halves reproduced: `[Cleanup, ScheduleVoices]` on the JVM dispatchers, the straggler starts 40 s after the stop, the engine never ends; the browser cannot reach it today |

## Minor

| Id | Tag | Finding | Verdict | The measurement, or the auditor's claim |
|---|---|---|---|---|
| L1-03 | bug | "Announce once" and "the fork dies with the engine" contradict each other when a playback is started again | Confirmed | No app path restarts a playback object; the library API does |
| L1-04 | safety | One NaN `startTime` in the queue can hide voices pushed after it until they are late, and they are dropped | Confirmed | measured as described; no frontend path makes a NaN start |
| L1-05 | safety | A non-finite `playbackStartTime` on a playback's first voice makes the epoch NaN: every voice is dropped | Confirmed | measured as described; internal only |
| L2-02 | bug (block framing) | A cut kills a victim that has not rendered yet, even when the victim's onset lies before the cutter's: whether a note sounds depends on the block border | Confirmed | 64 frames apart: 0 of 64 victim frames in one block, 63 of 64 across a border |
| L2-03 | bug / consistency | The seconds round trip places a block-aligned onset one frame BEFORE the cursor: about half of all live realtime notes and first notes start with `voiceElapsedFrames` = 1 | Confirmed | 49.9 % of block-aligned stamps one frame early at a Unix start; nothing audible follows |
| L2-06 | realtime | The whole voice build runs inside the render call, in the block that holds the note's onset | Confirmed | 16 default saws starting together: 513 us (19 %); one isolated note's onset block 60 us |
| L2-07 | leak / lifetime | Nothing bounds a stopped playback's voices: a long gate or a long release keeps sounding, and keeps the engine, after stop | Confirmed | 60 s gate: full level 29 s after the stop, engine kept to 60.08 s; release 1e6 lives 11.6 days |
| L2-09 | realtime / performance | No voice limit, and `replaceVoices` does quadratic work with deep compares on the audio thread | Confirmed, larger (was note) | Same measurement as L9-10 |
| L3-03 | sound | The impulse aliases at -5.3 dB re its harmonics by construction | Confirmed, smaller (was major) | -5.4 to -4.4 dB by construction, a documented single-sample spike nobody uses; the proposed triangle kernel reaches -12.5 dB, not -20 |
| L3-04 | bug | The impulse outputs a run of 1.0 while its phase runs backwards | Confirmed | Ratio -1: 4778 of 4800 samples at 1.0; the built-in impulse under `fm` depth 440 Hz at 220 Hz: 1624 nonzero samples per 0.1 s |
| L3-05 | sound / consistency | Sample playback is two-point linear at every rate, and the JVM leaves the whole rate conversion to it | Confirmed, smaller (was major) | The auditor's numbers reproduce exactly; a four-point kernel gains 11 to 47 dB on transpositions but only 1 to 15 dB on rate conversion |
| L3-06 | sound | A pluck carries a random DC offset that outlives its tone | Confirmed | After 1.9 s the mean is 0.08 to 1.84 times the AC level (pluck), 0.27 to 4.26 (superpluck) |
| L3-13 | consistency / doc | Stereo files: the browser keeps the left channel only, the JVM averages; the ref page says both | Confirmed | By reading, the stereo half of L9-07 |
| L3-14 | bug | The playhead never plays a sample's last frame, and a loop over the whole sample writes a zero at every pass | Confirmed | The last frame of a one-shot is never played; `loop(1)` writes a zero at frames 7, 15, 23 ... |
| L4-01 | bug, safety | The saturating SVF stops decaying once its state is large: more damping means a slower decay | Confirmed, smaller (was major) | the hold law matches the engine within 3 percent, but no ordinary signal reaches the slow region: it takes a transient 60 to 80 dB over full scale |
| L4-02 | bug, sound | The filter envelope is read at a block's two ends, and every sprudel filter envelope reaches it | Confirmed, smaller (was major) | Mechanism, a stale ledger line and SEVEN songs confirmed; the per-note variation is -40 to -63 dB re the peak in the songs' own shapes; the grid fix at N=128 is invariant but no more accurate |
| L4-03 | performance, safety | The de-click smoother has no flush: an envelope at sustain 0 turns the voice's gain into a subnormal | Confirmed, smaller (was major) | First subnormal at 0.812 s, they reach the cylinder; the held voice renders 27 to 39 percent slower on the JVM, about 0.5 us per block; inaudible |
| L4-06 | safety | Shimmer stores a NaN in its grain ring and plays it again | Confirmed | one NaN in, two NaN out (frames 4800 and 13599, 0.183 s later) |
| L4-07 | safety, consistency | `coarse` turns one infinite sample into `factor` infinite samples | Confirmed | one infinity in, `factor` infinities out (4 and 4800) |
| L4-08 | consistency, doc | Five answers to one bad sample: the layer has no rule for a non-finite SAMPLE outside the arithmetic nodes | Confirmed | the rows I measured or read in the probe table match |
| L4-11 | safety | The bare `drive` node emits infinity and NaN from finite input | Confirmed | `drive` 257 and up: NaN on every exact-zero input sample (silence), infinity elsewhere; 256 is finite |
| L5-03 | realtime | Large per-note buffers are allocated at note-on and never pooled (shimmer ring, pluck rings) | Confirmed | Pluck 26 KB, superpluck 167 KB, shimmer 775 KB per note, never pooled |
| L5-04 | performance | A gated-off stage still pays the whole build path: 5 to 10 us and several KB per note for a tail that builds one stage | Confirmed | The gated-off tail is 11 of the 12 us and 3.6 of the 5.8 KB of a default saw build |
| L5-06 | bug | A release written over `Freq` under a detune: the lifetime is read at the note, the envelope runs at the detuned pitch | Confirmed | Under detune(-12) the voice is cut at -14.7 dB in mid-release |
| L5-07 | bug | Inside a region a shared node repeats one block for ever once the region's frame counter saturates | Confirmed | A shared body node freezes into a 128-frame loop at saturation; the auditor's example (the input read twice) does not |
| L6-03 | realtime | A cylinder's deactivation zeroes its delay ring and its reverb network inside the render block | Confirmed, smaller (was major) | The deactivation clear costs 32 us (0.25 s delay) and 70 us (reverb), 1 to 3 % of a block; only long delays are expensive (L7b-06) |
| L6-05 | leak | With more than `maxCylinders` rented cylinders, the ones past index 254 are never visited by the cleanup | Confirmed | 256 cylinders: 1 never cleaned; 509: 254 never cleaned; negative ids reachable |
| L6-07 | doc | A voice that is NaN from its first sample is never culled, and the channel's KDoc says the opposite | Confirmed, smaller | a voice NaN from frame 0 lives to its end frame (measured); NaN mid-gate or in the release is culled, so two cases of three are bounded, not one |
| L7a-03 | sound | A leaving chain that sounds on silence stays at full level for 20 s after the edit that removed it | Confirmed | `pow(-1)` and `plus(0.2)` hold full level 20.36 s after the edit, above 1e-6 until 24.56 s |
| L7a-05 | bug, lifecycle | A `parallel` pad ring is not part of the tail question | Confirmed | By reading: a pad without twins never reports a tail |
| L7b-02 | sound | The vowel tables use formant bandwidths (in Hz) as q, so every formant is about 10 to 35 Hz wide | Confirmed, smaller | Widths 10 to 22 Hz measured; the swing is 17.8 dB at wet 1 floor 0, 2.1 dB at the defaults |
| L7b-03 | consistency | `reverb.lowpass` is documented as a cutoff in Hz, and it is a linear damping amount | Confirmed | 4 kHz T60: 0.79 s at lowpass 0, 0.81 at 1000, 1.22 at 20000 |
| L7b-04 | sound | The compressor smooths the LEVEL in dB before the gain computer: a dead time on the attack and a release that both hang on the -100 dB floor | Confirmed | Dead time 211 and 188 frames; release 24.5 ms to silence, 128.9 ms to -30 dBFS |
| L7b-05 | safety | The phaser's breakpoint is clamped to 18 kHz whatever the sample rate: below 36 kHz the all-pass can go unstable | Confirmed | At a 32 kHz engine: peak 8.8e307 and 49 non-finite samples; rates under 36 kHz are reachable offline |
| L8-01 | safety, sound | A huge finite sample on the mix becomes up to seconds of full-scale DC: the DC blockers carry it and the 20:1 limiter cannot bring it under the clip | Confirmed, smaller (was major) | numbers confirmed (1e15 for one block: 26,783 rail samples, clean after 1.13 s); only extreme raw values put such a block on the mix |
| L8-02 | sound, safety | A huge constant on the mix absorbs every playback's music: steps of 0.125 at 1e15, exact silence above 9e15, and the DC blocker hides the cause | Confirmed, smaller (was major) | 1e15 crushes every playback's music (error -15.4 dB), 1e17 gives exact zeros; same reach as L8-01 |
| L8-04 | safety, sound | One engine's non-finite output silences every playback, and its end is a hard step | Confirmed, smaller | every playback is silenced (measured); the "hard step at its end" is wrong: the DC blocker restarts from zero state, the music re-enters with a 21 ms DC transient |
| L8-08 | bug, consistency | The output latency the UI's clock gets: the JVM reports none, and the browser leaves out an authored master's delay | Confirmed | By reading: the master's latency is not carried; the JVM reports 0 |
| L9-03 | bug, consistency | The live timeline is counted in Unix-epoch seconds: half of the on-grid onsets land one frame early, a start's first voice among them | Confirmed | A playback's dyadic-grid notes are either all early or all exact, half the playbacks each |
| L9-04 | bug | A playback object started again after its stop: the gain can be lost and its inline instruments are never announced again | Confirmed | Measured: the restart's voices land at gain 1.0 after 0.3 was asked |
| L9-05 | leak | Every synth voice's sound and note is sent to the backend as a sample that was not found: a process-wide map that grows per edit, on both sides, missing from the debt list | Confirmed | 100 synth notes: 100 `NotFound` commands, 100 backend store entries, 100 preloader entries |
| L9-06 | leak, performance | In the browser the backend keeps one full PCM copy per requested NOTE, as doubles, and nothing ever evicts | Confirmed | By reading: one PCM per requested note in the worklet |
| L9-07 | consistency, sound | The browser and the JVM decode a sample differently, so the offline render of a sample song is not the live sound | Confirmed | By reading: left channel at 48 kHz against the average at the file's rate |
| L9-10 | realtime (per command, per edit) | Work on the audio thread at command time has no bound: the optimizer, chain builds, the replace dedup, and any number of voices | Confirmed, larger | `ReplaceVoices` is O(incoming x active) in the real shape; 9.2 ms and 13.6 MB at 1000 x 1000 on the JVM |
| L1-07 | consistency | The schema hash does not cover an enum's entries, the slot-key vocabulary, the catalogue index spaces or the codec's own format | not challenged |  |
| L1-08 | test | The round-trip spec never crosses a structured clone, several wire shapes have no row, and the receiving side has no test | not challenged |  |
| L1-09 | doc | The reference pages and several KDocs describe a wire that is not the one in the code | not challenged |  |
| L2-05 | sound (a decision is needed) | With the envelope off the engine guards a voice's end and not its start | not challenged |  |
| L3-07 | sound | The pluck's linear-interpolated read makes the damping of the highs depend on the note | not challenged |  |
| L3-08 | doc / sound | `pickPosition` sets the burst's length, not a pick position, and moves the level by about 10 dB | not challenged |  |
| L3-09 | sound | The perlin noise repeats every 256 lattice units: 1.78 s at `rate` 1 | not challenged |  |
| L3-10 | consistency | Four noise colours are set per sample, so they follow the device's sample rate | not challenged |  |
| L3-12 | consistency / test | At an even voice count the stack has no centre voice, and only one of the two middle voices gets the reduced jitter | not challenged |  |
| L4-04 | performance | `exp` and `stompbox` call `pow` per sample once the drive pushes the argument past 22 | not challenged |  |
| L4-05 | sound, consistency | The doors' soft cap is the alias source: a near-hard knee from 0.95 to 1.10 after every `Shape` | not challenged |  |
| L4-12 | doc | Comments that say what the code does not do | not challenged |  |
| L5-05 | consistency | A pitch node bends a musical oscillator in a SOURCE's parameter position, and a shared one depends on who renders first | not challenged |  |
| L5-08 | sound | A knob that reads a lifted LFO inside a region reads the upsampler's start-up at the first block, and pays a whole conversion per read | not challenged |  |
| L6-04 | consistency | The duck's sidechain is another cylinder's mix before OR after that cylinder's own duck, by rent order | not challenged |  |
| L6-08 | doc | Three ref pages and two KDocs describe a cylinder manager that no longer exists | not challenged |  |
| L7a-04 | sound, doc | With an authored lookahead the three gain-computer knobs do not glide, and the docs do not say so where the law is stated | not challenged |  |
| L7a-06 | realtime, doc | The tail scans run on every switch-off, not only in a swap's drain | not challenged |  |
| L7a-07 | realtime | The delay ring has no size bound, and its allocation and its cleaning run in `configure` | not challenged |  |
| L7a-08 | doc | Ref pages and KDoc that say what the code does not do | not challenged |  |
| L7b-08 | consistency | A chain of ducks depends on the order the cylinders were first used | not challenged |  |
| L7b-09 | consistency | `phaser(sweep = 0)` sweeps 1000 Hz, and a center at or below 0 becomes 1000 Hz | not challenged |  |
| L8-06 | sound | The 16-bit edge truncates toward zero and has no dither | not challenged |  |
| L8-07 | consistency, doc | The house DC pole is a fixed coefficient, so its corner moves with the sample rate, and the comment beside it is wrong | not challenged |  |
| L8-09 | bug | `PlaybackGain.retarget` ignores a new ramp time when the target is unchanged | not challenged |  |
| L9-09 | realtime (per sample chunk), doc | A sample upload costs the audio thread one megabyte clone and one megabyte copy per chunk, and the whole PCM allocation at the first chunk; the comment reasons with half the size | not challenged |  |
| L9-11 | consistency, doc | The "dropped" and "soundless" voice counts fall back to 0 at every stop; the KDoc calls the counters monotone; the stats snapshot's version is a sum that can stand still | not challenged |  |
| L9-12 | performance, test | The warm-up runs every Ignitor node kind, and on the bus side only the six born-with stages | not challenged |  |
| L9-13 | bug (JVM, frontend) | `Samples`' two caches are plain maps written from several threads on the JVM | not challenged |  |

## Note

| Id | Tag | Finding | Verdict | The measurement, or the auditor's claim |
|---|---|---|---|---|
| L1-06 | realtime | The diagnostics message is built, encoded and posted on the audio thread about 47 times a second | Confirmed, smaller (was minor) | 104 to 352 B and under 1.3 us per report on the JVM, 47 reports a second |
| L5-09 | realtime | Per-note allocations in render at the first block (the lint's leads, with their reach) | Confirmed, smaller (was minor) | First-block allocation: 80 B for a region voice, 0 B for the others measured |
| L5-10 | leak | Registered inline instruments are never dropped while a playback lives, and a noise instrument gets a new name at every evaluation | Confirmed | By reading |
| L6-06 | realtime | The duck builds a new `Ducking` on the audio thread at the start of every life | Confirmed, smaller (was minor) | One small `Ducking` (and its `knobArgs` array) per life |
| L7b-07 | realtime | A duck's life builds its `Ducking` on the audio thread | Confirmed, smaller (was minor) | Same as L6-06 |
| L8-05 | realtime | The diagnostics message is built and posted on the audio thread fifty times a second, also on an idle backend | Confirmed, smaller (was minor) | Same measurement as L1-06 |
| L9-08 | realtime (periodic) | The diagnostics are built, encoded and cloned on the audio thread about 47 times a second, and they are the clock | Confirmed, smaller (was minor) | Same measurement as L1-06 |
| L1-10 | consistency | A slot map does not arrive the same on JS and on the JVM for two kinds of key | not challenged |  |
| L1-11 | safety | Both rings drop a message when full and no sender reads the result | not challenged |  |
| L2-08 | doc / dead | Docs and comments that say what the code does not do | not challenged |  |
| L3-11 | sound / consistency | The sources' levels differ by up to 20 dB, and a unison stack gets quieter with every voice | not challenged |  |
| L3-15 | doc | Texts that say what the code does not do | not challenged |  |
| L3-16 | consistency | Two rng consumers in one voice make its noise depend on where the block borders fall | not challenged |  |
| L4-10 | sound | The cutoff clamp sits 1 Hz under Nyquist, where the filter is a resonator at Nyquist | not challenged |  |
| L5-11 | consistency | The gate reads leaves only: the full table, both directions | not challenged |  |
| L5-12 | dead / doc | Dead code and stale statements in this layer | not challenged |  |
| L5-13 | test | What the parity specs do not reach | not challenged |  |
| L6-09 | sound | Hard right is not exact: `cos(PI / 2)` leaves 6.1e-17 of the voice in the left channel | not challenged |  |
| L6-10 | consistency | The solo ramp's last sample is `from + (to - from)`, not `to`, and the two mix paths round differently | not challenged |  |
| L7a-09 | performance | A switched-off lookahead compressor runs its whole detector | not challenged |  |
| L7a-10 | test | The noise seeds follow the builder's line order, which no row pins | not challenged |  |
| L7b-10 | doc | The docs of this layer say several things the code does not do | not challenged |  |
| L7b-11 | performance | The body bank and the other inserts keep computing on exact silence while a later stage's tail holds the cylinder | not challenged |  |
| L7b-12 | safety | The delay's ring passes through the subnormal range before it reaches zero | not challenged |  |
| L8-10 | dead, doc | Dead and stale things at the edges | not challenged |  |
| L8-11 | consistency | The offline render starts 5 ms late and the WAV length counter is an Int | not challenged |  |
| L9-14 | dead | Dead and half-wired things in the shell | not challenged |  |
| L9-15 | doc | The ref pages and KDocs of this layer that say what the code does not | not challenged |  |
| L9-16 | consistency | The render headroom is timed with `Date.now()` in the worklet | not challenged |  |

## New findings of the disproof pass

Found while challenging, reported in the `disproof-*.md` files under "New findings" (not challenged in turn):

- **N-R1** (disproof-realtime.md): The first activation of a long delay allocates and zero-fills its ring inside the onset block
- **N-R2** (disproof-realtime.md): The drain end of a long delay clears its ring twice in one block, after a switch-off that scans it
- **N-R3** (disproof-realtime.md): A stop during the start's sample preload is swallowed: the scheduler then starts and sends its first cycle
- **N-R4** (disproof-realtime.md): `updatePattern`'s sample prefetch does nothing once the look-ahead has run
- **N1** (disproof-safety.md): The analog SVF oscillates for good near fs/3: q 200 and analog 5 at 16 kHz ring at full scale after the input stops
- **N2** (disproof-safety.md): Four finite extreme knob values put NaN, not a huge value, into a cylinder
- **S-01** (disproof-sound.md): The click table's sine note-end row is blind by construction
- **S-02** (disproof-sound.md): The compressor's detector reads a very quiet input as quieter than silence
- **S-03** (disproof-sound.md): The vowel table names no source
