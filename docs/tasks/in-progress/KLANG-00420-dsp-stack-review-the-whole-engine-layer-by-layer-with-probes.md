# KLANG-00420: DSP stack review: the whole engine, layer by layer, with probes that stay

> **Status (2026-11-10): IN PROGRESS.** Started: the three small probes first (hot-path lint, dead-knob finder, stage probe).
>
> **Part of:** KLANG-00353 (The Motor: correct at the edges)

## Why this exists

The redesign of the signal flow is finished (KLANG-00210, KLANG-00245, KLANG-00279, KLANG-00243, KLANG-00290, all
archived). The engine has its final shape for V1, so this is the moment to read all of it once, with fresh eyes and
with measurements, before the tutorial quarter and the launch build on it. The parity audit (KLANG-00417) did this
for the DSL surfaces and found false KDoc, dead knobs and drifted defaults; the engine side has had no such sweep.

The review FINDS and RANKS. It fixes nothing: the maintainer picks which findings become tickets.

## What is reviewed (in signal order)

1. **The wire**: `VoiceData`, the codecs, `KlangCommLink`; how a bad value is read.
2. **Voice creation and lifetime**: `VoiceFactory`, `VoiceScheduler`, `Voice`; scheduling, the state machine, cut,
   cull, the teardown fade.
3. **The sources**: oscillators, super oscillators, noise, plucks, the sample player.
4. **The per-note stages** of `classic()`: filters, envelopes, crush, coarse, distort, tremolo, the pitch nodes, fm,
   oversampling.
5. **The Ignitor build**: the optimizer, the build cache, shared nodes, the gate.
6. **The voice channel**: gain, pan, the mix into the cylinder.
7. **The Katalyst chain and its hosts**: every stage, knob glide, bank crossfade, chain swap, drain, tail release,
   ducking.
8. **The master and the house stage**: DC blockers, limiter, clip, the 16-bit edges.
9. **The engine shell**: `PlaybackEngineDispatcher`, `PlaybackEngine` phases, the clock, diagnostics, the worklet
   entry.

## What is looked for

- **The math**: coefficient formulas, stability near Nyquist and at extreme Q, envelope edges and off-by-one frames
  at block borders, phase wrap, the pan law, interpolation in the sample player and the delay lines.
- **Sound defects**: clicks (a switch, swap or jump without a fade or glide), aliasing, zipper noise, DC build-up,
  denormals in feedback paths.
- **Block-framing invariance**: anything "once per block" (KLANG-00156 owns this property; the review feeds it).
- **Numerical safety**: every feedback loop and divisor against NaN, infinity and huge values; can one bad voice
  poison a cylinder or the master for good (`audio/ref/numerical-safety.md`).
- **Real-time safety**: allocation in the hot path, boxed types, lambdas that capture a `var`, growing maps
  (`audio/ref/performance.md`).
- **Lifecycle and leaks**: reset on reuse, clean pooled objects, per-playback state that dies with its playback.
- **Consistency**: one law implemented twice with a difference, duplicated constants, KDoc that says what the code
  does not do.
- **Dead and half-wired things**: a slot nobody reads, a branch no input reaches.
- **Test truth**: what each stage's specs really pin; where only the corpus covers a stage; where a mutant would
  survive.
- **Performance**: see its own section.
- **Listening tasks**: questions of taste (the reverb's quality, a filter's character) are listed apart, for the
  maintainer's ears; the review does not judge them.

## The probes (tools built for the review, kept after it)

| # | Probe | What it answers | Kind |
|---|-------|-----------------|------|
| 1 | Stage probe | One stage alone under the standard signals (impulse, step, sweep, silence, full-scale noise, a NaN, a huge value): peak, DC, tail length, finite output, back to exact silence | measurement |
| 2 | Aliasing meter | Energy off the harmonics of a high sine, per source and shaper | measurement |
| 3 | Click detector | The largest sample-to-sample jump, relative to the signal, while one knob jumps or one stage switches; over every knob and switch | measurement |
| 4 | Block-framing checker | The same notes with the onset shifted by 1 to 127 frames, outputs aligned and compared (KLANG-00156's harness, if it builds one first, is this probe) | measurement |
| 5 | Hot-path lint | A static scan of `audio_be` render code for boxed types, allocations, capturing lambdas, `fun interface` Ignitors | static |
| 6 | Dead-knob finder | Every slot key a DSL writes has an engine reader, and the reverse | static |
| 7 | Stage cost table | Nanoseconds per sample for each source and stage alone, JVM and browser | performance |

Order: 5, 6 and 1 first (small, serve most layers), then 7, then 2 to 4 (an FFT and alignment logic; each gets its
own review). Before building one, check what exists: `runSongBenchmark` (song-level timing, the corpus), the
specs of KLANG-00156, `GuitarClickHuntTest`, `audio/ref/verification.md`.

## Performance

Measure before reading. In this order:

1. The stage cost table (probe 7), JVM and browser; the browser ranking is the one that counts.
2. One heavy corpus song under a profiler (JFR on the JVM, the Chrome profiler on the worklet build).
3. An allocation profile of a long JVM render: zero bytes from render code in steady state is the expectation.
4. A read of the generated JS of the five hottest loops (boxing, `ObjectRef`, range checks, Long emulation).

Candidates, by usual payoff: per-sample work that changes per block only; stages that run while contributing nothing
(a wet of 0, a silent voice not yet culled, a cylinder without input); work repeated across voices that could be
shared; loop shape (several passes that could be one, branches inside a per-sample loop); cheaper approximations
(each one a sound decision, so it goes to the maintainer, never applied silently). Every candidate is reported with
its measured share of the total. Hard performance work itself stays after launch (`docs/tasks/_priorities.md`);
this review only produces the ranked list.

## Probes and the build

Two tiers, by the "two kinds of check" rule:

- **A probe is a measurement, not a gate.** It runs on request and prints a table. It is never part of `check`:
  a full sweep is slow, and a number without an agreed threshold cannot be red or green.
- **A finding that is fixed leaves a spec behind.** When the review finds a click on a knob and it is fixed, the
  fix lands with an ordinary spec that calls the probe's measuring function for THAT knob with a threshold (a check
  for the future, mutation-checked). So the build grows one cheap, exact guard per real defect, not a broad noisy
  gate.
- **The static probes (5, 6) can become build checks at once**, as soon as their current findings are fixed or
  listed as waivers with a reason: they are fast and deterministic. The dead-knob finder is the natural sibling of
  the parity specs.
- **A new stage or source runs the probes before it lands** (a line for `/review-loop`'s audio role and
  `audio/CLAUDE.md`, with the maintainer's OK).
- **The full review recurs by occasion, not by calendar**: before a release line (V1, V1.5), and after a change of
  the engine's shape. With the probes kept, a repeat is mostly a rerun and a diff of the tables against the last run.

## Where the probes live, and how they are tracked

- **The measuring code** (probes 1 to 4, 7) needs the engine, so it is Kotlin beside the engine's tests:
  `audio_be/src/jvmTest/kotlin/probes/`, plain functions (`measureClick(...)`, `measureAliasing(...)`) that return
  data. The specs a fix leaves behind call the same functions, so probe and guard cannot drift apart.
- **The runner**: one spec-like entry per probe that prints its table, started on request through the build lock
  (the pattern of `GuitarClickHuntTest`), or one `runDspProbe --args=<probe>` task beside `runSongBenchmark`. To
  decide (below); no new module and no unusual Gradle wiring either way.
- **The static probes** (5, 6) are Python in `console/tools/<tool>/`, like `docs-lint`.
- **The register**: one table, "The probes", in `audio/ref/verification.md` (what each measures, how to run it,
  where its last table is). `/console-tools` gets one section that points there and documents the two Python
  tools; the root `CLAUDE.md` tool list gets one line. `/klangaudio-knowhow` already routes "proving identity" to
  `verification.md`, so every audio session finds them.
- **The results**: each run's tables under `docs/audio-audit/<date>/`, so a later run can be compared.

## Decided (maintainer, 2026-11-10)

1. **Depth: the full sweep**, all nine layers.
2. **The runner: test classes kept out of the normal run** (the simpler variant; no new Gradle task).
3. **All seven probes approved.**
4. **The register and the rule lines are written** (same day): `audio/ref/verification.md` "The probes", the
   engine rule in `audio/CLAUDE.md` (provisional), the repo note in `/console-tools`, the audio line in
   `/review-loop`, one line in the root `CLAUDE.md`.

5. **This is an audit, not a review loop, and it runs on Fable 5.2** (maintainer, 2026-11-10): "The review-loop is
   for reviewing newly written code. What we do here is an audit, so we would need a new ruleset for deep audit. For
   now you have the permission to work Fable 5.2 and if this works well we codify the rules once we are done with the
   audit." The reason: all the code was written and reviewed by Opus 5.5, so the investigation gets the most capable
   model and a reader who is not the author. `/review-loop`'s "no Fable" (2026-10-24) is untouched: it governs
   reviews of new code, and this permission holds for KLANG-00420 only.
   - The nine layer audits: Fable 5.2, read-only, one layer each.
   - The adversarial pass: Opus 5.5 at max effort (`reviewer-max`), a second model that tries to disprove each
     finding; what can be tested is settled by a mutant or a render.
   - The probes: written by Opus, read by Fable. The later fixes: written by Opus, reviewed by Fable, each its own
     ticket.
   - Budget: the probes first (cheap), the Fable sweep at the start of a budget week.

## Leads from the probes (2026-11-10, all UNVERIFIED; the audit confirms or disproves each)

The tables of this run: `docs/audio-audit/2026-11-10/` (`stage-probe`, `stage-cost`, `aliasing`, `clicks`,
`hot-path-lint` with `--guarded`, `dead-knobs` with `--list`; each as `.txt` and `.json`). The same on a page:
the Dev Tools' "Engine health" card (`console/dev-status/engine_health.py`).

**Stage probe** (165 knob sets, 8 signals; no stage made a subnormal, and non-finite output came from poison input only):

1. **Duck: one NaN on the sidechain latches the ducked bus to NaN for good**, both channels; an infinity recovers.
   `Ducking.stepGain`'s envelope is the suspect. The one "poisoned for good" case of the run.
2. **The analog (saturating) SVF latches at a huge DC after one sample of 1e30**: lowpass 1000 `analog` 1 stuck at
   7.83e27, highpass at 1.82e30, lowpass 23999 q 200 at 2.0e30, for the whole 10 s. The linear SVF decays.
3. Katalyst delay at its defaults never reaches exact zero after a step (a constant 1.44e-21, and 1.19e-36 after the
   NaN case): below the `flushState` floor of 1e-15, so is the ring flushed?
4. Katalyst reverb with a room: silence in, 9.1e-19 (size 5) or 7.5e-18 (size 10) out for ever. Probably the
   documented `ANTI_DENORMAL` exception; the question is what it costs downstream (`hasTail`, an idle engine).
5. Lowpass and highpass at 23999 Hz (the clamp's top): an impulse rings 0.55 to 0.65 s at q 0.707 and 9.99 s at
   q 200; the poles sit next to z = -1.
6. An infinity leaves the tremolo and the Katalyst arithmetic (`times`, `div`, `pow`) as ONE finite sample of 1e15
   (+306 dB); a NaN in the same place leaves as 0. Two answers to one question.
7. Katalyst `pow(-1)` on a silent bus outputs a constant 1e15; `div(1e-20)` peaks at 1e15. Raw by the Motor's rule,
   but what does the house limiter do with it?
8. Shimmer lets a NaN out a second time 0.183 s after the poison (its delay line), then is clean.
9. `coarse` holds an infinity for its whole hold (4800 samples at factor 4800) and passes no NaN at all.
10. Filters at q 200: +34 dB on a sweep at 1 kHz; at 20 Hz the tail outlives the 10 s window.
11. Shimmer (wet 1, feedback 0.95) and the eq band at 5 Hz q 200 keep DC deep into the silence.
12. The authored limiter at its defaults (no lookahead, 1 ms attack) passes a step at 1.00 through a -1 dB ceiling
    (by design uncompensated; worth one line in its KDoc if not there).
13. 16 passes of lowpass or highpass: the impulse response starts near 1e-38, under the Float normal range (matters
    only where a Float edge follows).

**Click detector** (217 rows, `clicks.txt`; the scale: a 50 ms glide reads kink 2.5 and burst -86 dB, a hard step
kink 3618 and -18 dB; fader, times, div, eq, body and vowel switches, compressor and distort on/off, delay and reverb
OFF read like the glide):

1. **Delay switched ON from off**: the feed snaps, so the first echo returns as a step 250 ms later (kink 115, -27.5
   dB). Reverb ON the same at 70.7 ms (kink 33, -54.5 dB). `knob-glide.md` says "from Off it snaps".
2. **A chain swap INTO a room**: kink 63 at 70.7 ms, -49 dB; the room's first output arrives after the 60 ms
   crossfade has ended.
3. **The `min` cap and `max` floor engage and release in one sample**: jump about 100, -12 dB, the hard-step class.
   As documented ("arrives at once"), so a decision, not a bug.
4. `compressor.attack` 0.1 to 0.003 applies at once (kink 17.8, -65 dB). The limiter with lookahead moves its
   threshold in about 8 ms where the compressor glides 40 ms: is the lookahead path's threshold glided?
5. **`adsr.on=0` fades a voice's end (the teardown) but not its START**: sine kink 69.5 (-52.6 dB), tri jump 109
   (-13.7 dB), supersine -26 dB, berlin -20 dB.
6. The supersine's default-adsr note end has a kink at its last frame (9.4, -68.5 dB); the sine is clean.
7. The `pow` exponent glide 1 to 2: kink 9, -65 dB. The cut and teardown fades read kink 10 to 20, -61 to -70 dB (by
   design, 4 ms).
8. `teardown_fade.kt`'s KDoc names about 3.3e-3 left on the last frame at a 50 ms release; the sine shows none
   (a stale KDoc?).

Not measured: sample voices' edges, a swap at the master, a Katalyst shape change, knobs that act on highs only (the
110 Hz tone hides them).

**Aliasing meter** (995 rows, `aliasing.txt`; non-harmonic energy in dB re the fundamental, floor about -165):

1. **The band-limited saw, ramp and supersaw are only about 10 dB better than the naive `zawtooth`**: -33.4 / -23.2
   / -15.9 dB at 110 / 1244.5 / 7044.4 Hz against -23.4 / -12.9 / -5.5. Square -25.5 and tri -26.3 at the top pitch.
2. The impulse has as much energy off its harmonics as on them at every pitch, 110 Hz included.
3. Sample playback off its own rate (a semitone up, linear interpolation): -105.9 at 110 Hz down to -30.2 at 7 kHz.
4. Shapers at 1x: rectify -0.9 and chebyshev -3.1 dB re their harmonics at 7 kHz, amount 0.5; at amount 2 every
   clipping shape sits at -10 to -12 dB, and fold, linearfold, sineshaper, rectify above 0.
5. Oversampling regions: the hard clip gains about 12 dB per doubling (-30.2 at 1x to -80.8 at 16x), the smooth soft
   shape 80 dB at 2x alone; smooth shapes bottom out at -120 to -130 dB (16x no better than 8x: the converters' own
   floor). Rectify at amount 2 never drops below +4.5 dB even at 16x.
6. **The doors' `Shape(Drive)` aliases far more at 1x than the fused `distort` node** at the same amount (soft -92.5
   against -138.4, fold -67.9 against -167.3 at 1244.5 Hz): two implementations of one sound?
7. The Ignitor node and the Katalyst `distort` agree within 0.1 dB everywhere (one shared core).

Not measured: aliasing under modulation (fm, vibrato, glides), the detuned stacks, the JS platform.

**Stage cost table** (JVM, 218 rows, `stage-cost.txt`; net ns per sample, a plain sine is 2.24; no row allocates;
the off paths are off: phaser at wet 0, gain 1, `classic()` all off and reverb size 0 cost 0 to 0.2 ns):

1. `distort` shapes `exp` and `stompbox` cost 3.4 times more at amount 3 than at 0.5 (Katalyst 72.9 against 21.1);
   every other shape gets cheaper. Suspect: `fastExp2` falling back to `pow` beyond its range.
2. Vibrato adds 8.5 ns to a saw voice, as much as lowpass and adsr together (fm 4.6, penv 4.3, accelerate 1.8).
3. The adsr costs 4.1 to 4.5 ns on a held note, also at 0/0/1/0 where it is a constant 1.
4. `analog` on lowpass and highpass costs 4.3 times the filter (16.6 against 3.9).
5. Katalyst `pow` at 3 and at -1 costs 28.6 ns (a general power where two multiplies or a reciprocal would do);
   `abs` 7.5 ns, the dearest simple operator.
6. `bands` with both bands empty costs 30.3 ns (the crossover alone), more than a delay (25.2).
7. Lookahead: compressor 38.1 against 23.9, limiter 32.8 against 24.4. Delay with feedback 1.5: 34.7 against 25.2.
8. `distort:chebyshev` at amount 3 is bimodal between runs (12.1 or 16.7), a JIT mode flip.
9. For scale: body 58, reverb 35, vowel 37, 16 passes of lowpass 63, 16x oversampled hard distort 237 (1.1 percent
   of a core per bus), superpluck 5.8 ns per voice.

The Kotlin/JS half is open: the worker's smallest honest route is to move the platform-free half of the probes to
`audio_be/src/commonTest` and run the production test bundle under node (never Karma's development bundle). The JVM
ranking is not the browser's.

**Hot-path lint** (30 findings, none per block): the once-per-note allocations in render code (`PhasePool.kt` 206 to
480, `DriftLanes.kt:238`, and with `--guarded` `OversampleRegion.kt:158`, `Ignitors.kt:923` and `:2950`,
`KatalystDuckEffect.kt:254`), the diagnostics message built on the audio thread (`PlaybackEngineDispatcher.kt:308`),
the byte types at the 16-bit edge (`_pcm16_edge.kt`, a waiver). A RULE QUESTION for the maintainer: the engine rule
names "per sample or per block"; it does not say whether once per note inside `generate`, or periodically on the audio
thread, is allowed. About 11 findings hang on the answer.

**Dead-knob finder** (clean): 13 keys no door writes, reachable by a raw `ignp` / `katp` only (`rate`, `octaves`,
`persistence` of B9; `bipolar`; `declick`; `gain.gain`, deliberate; `eqhz`, `eqq`, `eqdb`, `eqlp`, `divisor` of the
demo sounds; `warm`, `offset` of the warm-up).

## After the audit

Codify a ruleset for deep audits if this worked (its own skill or a section; a maintainer decision): what an audit
is against a review, which model reads, how findings are disproved, what it leaves behind. Notes for it are
collected here while the audit runs.

## Decide before starting (the list as proposed)

1. **Depth**: the full sweep (about 10 to 12 agents, a real share of a week's budget), or first only the layers
   the redesign changed most (2, 5, 7).
2. **The runner**: probe specs excluded from the normal test run, or a `runDspProbe` task.
3. **The new tools need the maintainer's approval** (`/console-tools`: a new tool starts as a proposal): the table
   above is that proposal.
4. **Reviewer models and effort** for the layers (`/review-loop`'s ladder; no Fable reviewers, as before).
5. The skill and `CLAUDE.md` lines named above (config changes need the OK).

## The work

1. Probes 5, 6, 1; each reviewed and tried on one known case (a mutant or a known defect it must find).
2. The cost table and the profiles.
3. Probes 2 to 4.
4. The review: one reviewer per layer, read-only, in parallel, each with its files, its `audio/ref` page, the
   checklist and the probe tables; then an adversarial pass that tries to disprove every finding (a mutant or a
   small render where a doubt can be tested).
5. The report: one ranked list, each finding with file and line, a concrete failure scenario and a tag (bug, sound
   risk, performance, debt, doc), plus the listening tasks. The maintainer picks the tickets.
6. The register and the result tables in place.

## Acceptance

- Every layer has a written review, and every finding survived the adversarial pass or is marked unconfirmed.
- The probes run with one command each and are in the register.
- No engine code changed except what a probe needed to be callable.

## History

- 2026-11-10: created.
- 2026-11-10: IN PROGRESS. Started: the three small probes first (hot-path lint, dead-knob finder, stage probe).
