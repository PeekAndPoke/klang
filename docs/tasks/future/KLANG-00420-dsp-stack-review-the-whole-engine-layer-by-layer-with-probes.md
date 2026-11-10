# KLANG-00420: DSP stack review: the whole engine, layer by layer, with probes that stay

> **Status (2026-11-10): NOT SCHEDULED.** Asked for by the maintainer, 2026-11-10; the plan and its open decisions are below.
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

Still open: the reviewer models and effort for the layers.

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
