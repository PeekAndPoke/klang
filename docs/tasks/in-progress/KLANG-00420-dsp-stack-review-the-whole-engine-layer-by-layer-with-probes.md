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

The tables of this run: `docs/audio-audit/2026-11-10/` (`stage-probe`, `stage-cost`, `aliasing`, `clicks`, `framing`,
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

**Block-framing checker** (217 cases at all 128 offsets, `framing.txt`; 182 identical, 4 rounding, 31 audible):

1. **The filter envelope of `classic()` depends on where the block borders fall, audibly** (`lpf` / `hpf` / `bpf` /
   `notch` with `env`): up to -15.6 dB re the peak at the default envelope times (worst at the attack's end), -10.8 dB
   with segments shorter than a block, already -48 dB at an offset of one frame; after the gate a release shorter
   than a block lands up to 0.18 off, 6 to 14 frames late (failure mode b). The law is KLANG-00156's E3 (the cutoff
   envelope is evaluated at a block's two ends), which the ledger calls "latent: no production door passes an env
   here". **That reach statement is out of date**: every sprudel `lpf(env = ...)` reaches it. The one finding of this
   probe that is neither identical nor by design.
2. By design, now with sizes: analog drift at `analog` 1 (the lanes step on the absolute block grid; saw -29.2 dB at
   offset 127, linear in the offset, 18 cases with the analog filters) and the bus phaser (-43.5 dB, the cylinder's
   clock, D2 and D4).
3. Identical at every offset: all 23 sources bare and as voices at `analog` 0, every `classic()` stage without an
   envelope, every pitch stage (accelerate rounding only, about 1e-12 where the ledger's E7 says 5.3e-15), cuts, the
   sample voice, the bus delay (the echoes move with the note sample for sample). No voice wrote before its onset, and
   every voice drew the same random values at every offset.

Not covered: signal-rate knobs, Katalyst modulators, a voice joining a running cylinder, several voices at once.

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

**The Kotlin/JS half and a real song** (`stage-cost-js.txt`, `stage-cost-compare.txt`, `song-profile-jvm.txt`; node
22, production bundle, the median of five processes; a plain sine is 4.07 ns on JS against 2.24 on the JVM):

1. In the heaviest song (Der Schmetterling) the reverb is 21.7 percent of the render, about 24 single reverbs' worth
   where the song writes six: do idle rooms ever stop (their output never reaches exact zero)? The eq is 17.9
   percent (cheap alone, multiplied by voices, oversampling and count), the shaper's DC blocker 6.0, the half-band
   oversampler 11.1, the plumbing nodes `MemoizingIgnitor` 5.8 and `AffineIgnitor` 1.9 (more than the filter).
2. In Seltsamere Dinge the filter is 29.3 percent, the body 19.7, the adsr 12.1, the unison stack 11.0.
3. Relatively dearer on JS: Katalyst `pow 3` (24 sines against 12.8), linearfold, sineshaper, fold, coarse, the
   berlin and pink noises, the reverb (20.8 against 15.8). Relatively cheaper: the analog filter, the body, superpluck.
4. V8 allocates per block in steady state where the JVM allocates nothing: the partial bank 48 B, the Ignitor phaser
   32 B, the fm voice 16 B, shimmer 16 to 32 B.
5. The voice build is 93 percent of the song's allocation: about 30 KB per note start, with `buildRaw`'s captured
   locals among the sources. Der Schmetterling's peak block took three times its deadline (a build in the block, or
   the harness's GC).

No node song profile: the song benchmark has no node mode (`song-profile-js.txt` sets out three routes).

**Hot-path lint** (30 findings, none per block): the once-per-note allocations in render code (`PhasePool.kt` 206 to
480, `DriftLanes.kt:238`, and with `--guarded` `OversampleRegion.kt:158`, `Ignitors.kt:923` and `:2950`,
`KatalystDuckEffect.kt:254`), the diagnostics message built on the audio thread (`PlaybackEngineDispatcher.kt:308`),
the byte types at the 16-bit edge (`_pcm16_edge.kt`, a waiver). A RULE QUESTION for the maintainer: the engine rule
names "per sample or per block"; it does not say whether once per note inside `generate`, or periodically on the audio
thread, is allowed. About 11 findings hang on the answer.

**Dead-knob finder** (clean): 13 keys no door writes, reachable by a raw `ignp` / `katp` only (`rate`, `octaves`,
`persistence` of B9; `bipolar`; `declick`; `gain.gain`, deliberate; `eqhz`, `eqq`, `eqdb`, `eqlp`, `divisor` of the
demo sounds; `warm`, `offset` of the warm-up).

## The audit's result (2026-11-10)

The index of every finding with its verdict: `docs/audio-audit/2026-11-10/audit/SUMMARY.md`. The layer reports
(`layer-*.md`, ten auditors on Fable 5.2, read-only), the disproof reports (`disproof-*.md`, three challengers on Opus
5.5 at max effort, measuring in the engine) and both briefs are in the same folder.

- 120 findings. 69 challenged (every critical and major one, and the minors of the same chains): 0 disproved, 18
  smaller than claimed, 4 larger. After the pass: 2 critical, 16 major, 73 minor, 29 notes; 51 minors and notes were
  not challenged and carry the auditor's reading only. 9 new findings came out of the pass itself.
- Critical: L3-01 (the pluck is flat: 15.75 cents at A4, 36.73 at C6 at the default brightness, measured by two
  methods; four built-in songs use plucks) and L7b-01 (one NaN on a duck's side chain latches its gain; with L1-01 a
  pattern reaches it: `pan("NaN")`).
- A crash from a pattern (L2-04, major after the pass): `note("c3*8").s("saw").pregain(0).legato(-0.001)
  .adsr(release = 0)` throws out of the render call, and neither host catches it (L8-03, L9-01): all audio ends until
  a reload.
- Downgraded by measuring: the filter envelope's framing dependence (L4-02: -40 to -63 dB re the peak in the seven
  songs that use it), the saturating filter's hold (L4-01: needs a transient 60 to 80 dB over full scale), the house
  stage's seconds of full-scale DC (L8-01, L8-02: same reach), the sustain-0 subnormals (L4-03), the deactivation
  clear for ordinary delays (L6-03), the diagnostics' cost on the JVM.
- The reproductions stay as specs that run only with `KLANG_PROBE=audit` (`audio_be`, `sprudel` and `klang`, each
  under `src/jvmTest/kotlin/audit/`): the fix of a finding turns its row into a guard.
- The first verdicts are in `docs/audio-audit/verdicts.json` and on the engine health page.

Open for the maintainer: which findings become tickets; the rule question (is an allocation once per note in render
code allowed); the decisions the reports list under "Decisions worth revisiting" and "Listening tasks".

## After the audit

Codify a ruleset for deep audits if this worked (its own skill or a section; a maintainer decision): what an audit
is against a review, which model reads, how findings are disproved, what it leaves behind. Notes for it are
collected here while the audit runs.

Notes so far (each report also ends in a section "For the audit ruleset"):

- Probes first, readers second: every auditor started from measured leads and had to confirm, disprove or mark each
  one. The leads that were wrong (the "stale KDoc", the reverb holding an engine) were caught by reading.
- Read-only auditors that run nothing, then a second model that must measure: no finding was disproved, but 22 of 69
  changed size. The auditors were right about mechanisms and often wrong about reach; severity needs the measurement.
- A reach statement ("which door gets there") was the most valuable field and the one most often corrected.
- Two auditors finding the same line from different layers (the unguarded `pan`) was the strongest early signal.
- The coordinator miscounted the findings once (128 for 120) by adding the auditors' own totals: generate the index
  from the reports.
- Reproductions kept as gated specs give every fix its guard for free.

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

### Decided (maintainer, 2026-11-11)

Two rule questions of the audit:

- **An allocation once per note in render code:** "if we are able to avoid it or reduce it through the warehouse we
  should do it." Not forbidden; reduced where the warehouse can hold the buffer (see KLANG-00429, L5-03).
- **The aliasing verdict on the health page:** "Judge below 5 kHz. Maybe we can also flip it and measure / display up
  until which frequency the aliasing is healthy (-60 dB) and if this is below 5kHz it is healthy." Built in decisions
  pass C (below): the meter reports the frequency up to which the aliasing stays under -60 dB, and the page judges by it.

## Decisions pass C (2026-11-11): the aliasing verdict

What the decision of 2026-11-11 ("Judge below 5 kHz. Maybe we can also flip it and measure / display up until which
frequency the aliasing is healthy (-60 dB)") became.

- **BUILT: the healthy limit.** The aliasing runner sweeps every source and shaper the table can judge (the measured
  rows and the controls) up 41 fundamentals, 109 Hz to 14.2 kHz, `fs / (N + 0.382)` for whole N (about a sixth of an
  octave apart; the fraction keeps the folds off the harmonic bands, the 7040 Hz trap of the table). At each note the
  level is the non-harmonic power BELOW 5 kHz re the power of all the harmonic bands (the fundamental's included, so a
  rectifier reads right). The limit is the highest swept note up to which that level stays under -60 dB at that note
  and every lower one; a case leaves the sweep at its first unhealthy note. `measureAliasingLimits(...)` in
  `audio_be/src/jvmTest/kotlin/probes/_aliasing_limit.kt`, its KDoc the definition; the reading's new field
  `nonHarmonicBelow5kReHarmonicsDb`. Written beside the table as `aliasing-limit.txt` and `.json`.
- **The page judges by it:** an aliasing item is green when its limit is at or above 5 kHz, yellow below; its value is
  the limit ("clean to 6.50 kHz", ">= 14.19 kHz" when nothing failed up to the top), and a second table shows the
  sweep. An older run without the limit keeps the old rule. `console/dev-status/engine_health.py` now reads, per
  probe, the newest run folder that holds its JSON, so a rerun of one probe is a folder of its own:
  `docs/audio-audit/2026-11-11/` holds the aliasing table and the limit only, the other probes still come from
  2026-11-10 (the page names the run per probe). Page rebuilt.
- **The run** (49.9 s for the sweep): 181 rows swept, 48 healthy (3 of them the controls), 133 below 5 kHz (most are
  shapers at 1x, which fold a driven sine's harmonics); the by-design and not-measurable rows are not swept. Sources: `sine`, `supersine` voices 1, the partial bank and
  the sample playhead at rate 1 clean to the top; `saw`, `square`, `ramp`, the square node and their one-voice stacks
  6.50 kHz (at 7.52 kHz -57.9 dB); the playhead a semitone up (cubic) 5.12 kHz; `tri` and `supertri` 0.99 kHz (no
  PolyBLEP, accepted); `pulze`, `zawtooth`, `zamp`, `impulse` fail at the lowest note (-25 to -35 dB, naive by
  design). Shapers: the smooth shapes at amount 0.5 inside `oversampleBy(4)` are clean to the top, `soft` at 1x and
  amount 0.5 is clean to 4.62 kHz, at amount 2 to 0.12 kHz.
- Guard: `AliasingMeterSpec`, two rows (the sweep's pitches; a test-made tone whose alias below 5 kHz crosses -60 dB at
  3162 Hz and a part at 20 kHz that must not count: the limit is the last swept note under the crossing, a pure sine is
  clean to the top). Mutants: the threshold 30 dB lower, the band test counting every bin, the fraction 0.5 (see
  KLANG-00432's pass C for the campaign table).
- **Not built:** tests for `engine_health.py` (it has none; the rule is plain and the page was checked by a rebuild and
  a parse of its script).

## Closing measurement run (2026-11-11)

Every probe rerun on the tree after the decisions passes A, B1, B2 and C (HEAD 25786c971, clean), against the run of
2026-11-10. The files: `docs/audio-audit/2026-11-11/` (`stage-probe`, `stage-cost`, `stage-cost-js` with
`stage-cost-compare`, `clicks`, `framing`, `hot-path-lint`, `dead-knobs`, `corpus-check.txt`, `song-benchmark-jvm.md`;
the reproductions' output in `audit/`). The aliasing files of pass C stay: a rerun after later edits to the meter and
the shapers gave byte-identical JSON. The verdicts are updated and the health page is rebuilt (no red item left).

**What changed state** (before is 2026-11-10):

- Stage probe (165 knob sets): red 2 to 0, yellow 47 to 39. Fixed: the duck's NaN latch (L7b-01, finite again after
  one sample), the coarse hold of an infinity (L4-07), the shimmer's second NaN (L4-06), the delay at its defaults now
  ends on an exact zero (was 1.44e-21). The q 200 filters at 23999 Hz sit on the 0.49 fs clamp now (L4-10): their
  impulse, step and noise tails end, only the 1e30 row keeps one. Unchanged and accepted (L4-01): the analog lowpass at
  1 kHz holds 7.83e27 after 1e30, the highpass 5.86e10, the lowpass at 23999 q 200 2e30.
- Clicks (218 rows, one new): yellow 29 to 24. Fixed: delay ON kink 114.8 to 1.50 (burst -27.5 to -95.6 dB), reverb ON
  32.8 to 1.04 (-54.5 to -119.9), the swap into a room 63.1 to 1.05 (-49.1 to -114.4), the new swap into a delay 1.99
  (-89.5), `compressor.attack` 0.1 to 0.003 17.8 to 1.21, the supersine and perlinnoise note ends at the default adsr
  9.42 and 7.32 to under 1 (L2-01). **New lead:** `reverb.lowpass` 1000 to unset, kink 1.31 to 6.84 and burst -95.0 to
  -68.6 dB, 49.75 ms after the change (the glide's end; the knob became a real cutoff in KLANG-00428). Moved: the sine's
  teardown row 10.0 to 25.2 (S-01 moved its gate off a zero crossing), the pluck and superpluck starts (retuned, L3-01),
  the perlinnoise cut 5.75 to 11.4. The `min` and `max` door rows are relabelled, their numbers identical.
- Framing (218 cases, one new): identical 182 to 195, audible 31 to 19. All twelve filter-envelope cases (L4-02) are
  identical at every offset (were -10.5 to -22.6 dB re the peak). The analog-drift rows (by design) moved 1 to 5 dB.
  Still the one yellow: the white-noise cut draws a different count of random values at 127 offsets (as on 2026-11-10).
- Cost, JVM (sine 2.24 to 2.27 ns; median row unchanged): cheaper `exp` and `stompbox` at amount 3, 5.4 to 5.7 times
  (L4-04), the doors' `drive+shape` 0.41 and 0.55 (L4-05), the default compressor 0.71 (L7b-04); dearer the
  super stacks 1.47 to 1.61 times and the supersaw voice 1.31 (L3-02's box, kept by decision), the sample playhead 1.8 to
  1.9 (L3-05's cubic read), `distort:hard` at amount 3 1.27 to 1.29 (not explained by a ticket). No row allocates per
  block. Kotlin/JS (sine 4.07 to 3.74 ns, median row 1.03): the same moves, the bare saw, ramp and square 1.8 to 2.1
  times; JS only, tri, zawtooth and zamp 1.4 times (rows under one sine). V8's per-block bytes are unchanged (the
  partial bank 48, the Ignitor phaser 32, the fm voice and shimmer 16, and the duck 192 B, which the 2026-11-10 list
  missed).
- Hot-path lint: 66 to 67 findings plus 2 waived (the host's catch, KLANG-00425). New: `ResourceWarehouse.stockDelayRings`
  (`arrayOfNulls`; warm-up only, its KDoc says not for render code, so the lint's scope is wrong there) and a guarded
  array in `SuperKarplusStrongIgnitor.generate`; gone: `Ducking(...)` in `KatalystDuckEffect.configure`. Dead knobs:
  unchanged.

**The audit reproductions** (`KLANG_PROBE=audit`, all 19 tests ran):

| Row | Now | State |
|---|---|---|
| L6-03, L7b-06 the deactivation of a 10 s delay | 571 to 72 us; drain end with deactivation 953 to 72 us; `DelayLine.reset` 0.1 us | fixed (KLANG-00428); the reverb's reset 56 us, accepted |
| N-R1 the first activation of a 10 s delay | 2315 us (87 % of the block), 12.4 MB; was 1573 to 1781 us | open: the 60 s bound and warm rings of 1 s and 2 s are built (KLANG-00428), a 10 s ring is not warmed |
| L4-09 shimmer at wet 0, one build | 774,520 B and 71 us to 2,272 B and 2.65 us | fixed (KLANG-00429) |
| L5-04 the default saw's build | 12.2 us and 5,784 B to 3.50 us and 2,456 B | fixed (KLANG-00429) |
| L5-03 per-note rings | pluck 26,040 to 22,760 B, superpluck 166,816 to 163,584 B per build | open: the shimmer ring is sized (KLANG-00429); the Karplus rental is planned with the maintainer |
| L5-09 a region voice's first block | 80 B | unchanged by decision (accepted, KLANG-00429) |
| L2-06 16 notes in one block | default saw 513 to 221 us (19 to 8 %), every stage 1056 to 663, superpluck max 2639 to 1301, shimmer max 4850 to 384; one isolated note 60 to 11 us | unchanged by decision (option (c), KLANG-00427): the build stays in the render call, its cost is lower |
| L1-06, L8-05, L9-08 the diagnostics | 128 to 376 B and 0.02 to 0.34 us per report (was 104 to 352 B) | unchanged by decision (D4, KLANG-00431); the 24 B are B2's three counts |
| L9-02, L9-04 the backend halves | a straggler revives a stopped engine; a restart's voices land at gain 1.0 | unchanged by decision (D1, KLANG-00431); the frontend halves are fixed |
| L8-03, L9-01 the hunt | 0 throws in 35,829 slot renders and 884 chain runs | fixed (KLANG-00425); its L6-01 lines (12 Karplus and perlin knobs at 1e308, `reverb.wet` 1e308) unchanged by decision (L6-01 (a), N2) |
| L4-01 the saturating SVF | the same numbers (1e4 holds 0.11 s, 2e4 1.70 s, 8 kHz from 1e4 not settled) | unchanged by decision (accepted, KLANG-00432) |
| N1 near fs/3 (both repros) | the 16 kHz q 200 analog 5 and 15 tails settle in 0.15 to 0.17 s; the sprudel pluck ends on exact zeros | fixed (KLANG-00432) |
| L8-02 a huge constant on the mix | the same: 1e15 crushes the music (-15.4 dB), 1e17 silences it | still open for the constant; the count of option (c) is built |
| L4-11, L4-08 | the same (drive 257 gives NaN; one bad sample's answers) | unchanged by decision (N2, the rule of L4-08; KLANG-00432) |
| L3-03 the impulse | -5.4 to -4.4 dB | unchanged by decision (accepted, KLANG-00430) |
| L3-05, L9-07 a file off the engine's rate (JVM) | 44.1 kHz file: 5, 10, 15 kHz -33.9, -20.4, -11.1 to -54.8, -30.6, -15.9 dB | still open: the JVM converter is KLANG-00438 (the browser half is built) |
| L4-02 (b) onsets one frame apart | -inf everywhere (was -40 to -63 dB in the songs' shapes) | fixed (KLANG-00432, pass C) |
| L4-02 (c) the grid emulation | its emulation of the old law no longer equals the engine (0.16 apart) | stale since pass C: the row measures a law the engine no longer has |
| L7b-02 the vowel widths | 10 to 22 Hz, swing 17.8 and 2.1 dB | still open (KLANG-00428, the vowel table) |

**What the longer voices cost** (L2-01's 36 ms settle, B1's note-off at stop): neither tool shows it directly. The stop
changes nothing an offline render plays (the renderer never stops a playback early) and the cost probe has no stop row;
its effect is fewer voices after a stop, not more. The settle has no switch to compare against, and the 2026-11-10 song
run ran under JFR, so its RTF is not comparable. The song benchmark today: Der Schmetterling 3521 onsets, 11.0 voices
(was 11.0), medRTF 0.117; Seltsamere Dinge 595 onsets, 16.0 voices (was 15.0), medRTF 0.046. An estimate from those
counts: 36 ms more per note is at most 14 percent more voice time for Der Schmetterling (3521 notes in 83.5 s at 11
voices) and 1.5 percent for Seltsamere Dinge; a settling voice renders its whole tree, so that is also its bound on
voice cost.

**The corpus** (`runSongBenchmark --args=corpus`, samples from the cache, not accepted): RED against the reference of
2026-11-06, which predates every audit fix. All 18 rows differ: the three frozen texts, two unedited live songs (Final
Synthasy VII Prelude, Drunken Synthlor) and the 13 live songs edited since (information). The tickets name the
deliberate sound changes (KLANG-00426, 00427, 00430, 00432, among them L3-01, L2-01, L2-03, L3-02, L3-05, L4-02,
L4-05); this run does not attribute rows to fixes.

## After the closing run (2026-11-11): the `reverb.lowpass` click, the stale L4-02 reproductions

**The click is fixed at its cause.** `reverb.lowpass` 1000 to unset read kink 1.31 and a burst at -95.0 dB on
2026-11-10 and 6.84 and -68.6 dB in the closing run. The change that moved it is KLANG-00428's second pass
(8b4506c0d, "reverb.lowpass is a cutoff in Hz"), found by reading the damping law at both commits: before it a set
`lowpass` was a linear damping amount, `(1 - fc / Nyquist) * 0.4`, so 1000 Hz at 48 kHz was a comb pole of 0.383
against the unset 0.2; since it the pole is the one-pole's own for the corner, 0.877 at 1000 Hz. The comb's store
`y = (1 - p) x + p y'` keeps its value across a pole jump but not its lag at the tone (about 0.10 rad at 110 Hz under
0.877, 0.004 under 0.2), so the jump bends every comb's recirculating signal, and the bend comes out one comb length
later, staggered over the combs (the kink 49.75 ms after the change). The standing law said the damping does not
glide, "measured inaudible under the old law, not measured again" (`audio/ref/katalyst.md`): under the new law it no
longer held, so this is not a wrong old reading but a law whose condition fell away.

The fix, in the engine's existing terms: the damping glides like the size, a COEFFICIENT knob (`KnobGlide`, per
block, over the glide time, one block when the knob is modulated, the first configure out of Off snapping), on the
comb damping's POLE, the coefficient the store is linear in. `KatalystReverbEffect.dampingGlide` holds its value on
the unit while it moves (`Reverb.holdDamping`); the block it lands in runs on the unit's own law again, the same
number bit for bit, so a room whose `lowpass` never moves renders as before. While a pole is held the tail accounting
counts the lag of the larger of the held pole and its target, which bounds every lap until the glide lands (a drain
that starts mid-glide towards a dark room). No clamp. The click rows now: 1000 to unset kink 1.29, burst -92.6 dB;
unset to 1000 kink 1.27, burst -94.4 dB (were 1.90 and -86.2): the worst of the probe's four instants and both
channels, read by `ReverbLowpassClickSpec` with the probe's own cases and function. No corpus song moves: all 18
renders are bit identical with this and KLANG-00429's pooling applied (the render there), so no song changes a
cylinder's `lowpass` while its room sounds.

Guards: `ReverbLowpassClickSpec` (jvmTest, the probe rows with a threshold: kink under 2, burst under -86 dB, under the
probe's own 50 ms glide control), `KatalystReverbDampingGlideSpec` (the pole's linear glide by hand, three moves; the
gliding room against a bare network held on hand-computed poles; a still `lowpass` never holds a pole, bit for bit the
bare network; a modulated one lands at once; the drain mid-glide counts the darker room's lag; reset forgets the
glide), and a row in `KatalystModulatedStagesSpec` (a modulated cutoff holds no pole). Mutants, all KILLED for the
expected reason (one campaign with KLANG-00429's pooling): the effect not holding the pole (the click rows, kink back
over 2), `Reverb.process` ignoring a held pole (after the oracle was made independent of `holdDamping`, see KLANG-00429),
the lag bound taken from the held pole alone, the way into Off keeping a held pole, the chain builder not passing a
modulated lowpass. Docs: `audio/ref/katalyst.md`, `audio/ref/knob-glide.md`,
`audio/ref/effects-mixing.md`.

**The L4-02 reproductions are deleted** (`AuditSoundStagesRepro`): (b), the onsets one frame apart, measured a fixed
finding (identical at every offset since pass C), and (c), the grid emulation, emulated a law the engine no longer
has. With them went what only they used: the file's `renderTreeAtOffset`, `sweptLowpass` and `diffDb`, and
`AuditSoundKit.renderVoice` and `AuditSoundKit.peak` (`_audit_sound_kit.kt`); the file's KDoc says what is left in it
(L7b-02, the vowel). The framing cases' known text for a filter envelope (`KNOWN_E3` in `_framing_cases.kt`, the
KLANG-00156 E3 quote with its "latent: no production door passes an env here") is gone: those rows are identical at
every offset, and the quote described the block law pass C retired.

## History

- 2026-11-10: created.
- 2026-11-10: IN PROGRESS. Started: the three small probes first (hot-path lint, dead-knob finder, stage probe).
- 2026-11-10: all seven probes built (the cost table on the JVM only), the first run in `docs/audio-audit/2026-11-10/`, the engine health page. Next: the Kotlin/JS half of the cost table and the profiles, then the nine-layer audit on Fable 5.2 (held for the start of a budget week) and the adversarial pass.
- 2026-11-10: the audit ran (ten layers, 120 findings) and the disproof pass (69 challenged, 0 disproved); the index is `docs/audio-audit/2026-11-10/audit/SUMMARY.md`. Waiting for the maintainer to pick the tickets.
- 2026-11-11: decisions pass C: the aliasing meter's healthy limit, judged on the health page.
- 2026-11-11: closing measurement run: every probe and reproduction rerun into `docs/audio-audit/2026-11-11/`, verdicts updated, page rebuilt; the corpus is red against the pre-audit reference, not accepted.
- 2026-11-11: after the closing run: the `reverb.lowpass` click fixed (the damping glides on its pole), the stale L4-02 reproductions deleted.
