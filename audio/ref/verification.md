# Audio: how an engine change is proven

The method the audio work settled on during phase 3 and the Katalyst steps (2026-09), kept because each rule
below was paid for once. The review standard itself (review until a clean round, mutation-check every new test)
is `/review-loop`; this file is the audio-specific half. The stories behind the rules are in
`audio/ref/memory-history.md`.

## An identity claim

- **Render the corpus, both sides, in raw doubles.** The built-in songs plus the frozen songs and pieces
  (`src/jvmMain/kotlin/FrozenSongs.kt`, `FrozenPieces.kt`), 256 cycles, 48 kHz, the wall-clock seeds pinned
  (`timeOfDay`, `sineOfDay`, `timeOfNight`, `sineOfNight` to `pure(0.5)`), HEAD in a throwaway
  `git worktree` against the tree. Compare doubles, not a 16-bit WAV: a 16-bit render cannot see a sub-LSB change,
  so a bit-level claim needs a spec, not the corpus.
- **Render from the repo root.** The JVM sample bank loads and writes `./cache` relative to where it runs
  (`audio_fe/src/jvmMain/kotlin/samples.kt`), and a Gradle `-p <tree>` run needs ABSOLUTE song paths. The CLI render
  plays samples; the `:jvmTest` helper `renderSong` has no sample bank, so a sample-fed cylinder is silent there.
- **Every identity run needs an engagement control**: a deliberate mutant that must move the rows the change can
  reach. A green run alone is not evidence. Predict WHICH rows move before running, and read each song's
  arrangement and imports to do it: a grep finds what is DEFINED, not what PLAYS (a song may define an instrument
  its arrangement never plays; another reaches one through `import`).
- **Attribute every difference.** Strip the suspected calls from both sides (e.g. every `.phaser(...)`) and show
  the rest is bit-identical, or log per cylinder which event differs.
- **Draw order is part of the sound.** A change that moves which rng draw lands where moves every later draw of
  the voice (the rules: `audio/ref/voice-synthesis.md`, "The voice rng"). Name the draw-order move as the cause
  with a control that switches the draws off on both sides.

## A measurement

- **Establish the metric's floor on a static render first**, and it must sit tens of dB under the effect. Then
  VARY THE SOURCE and watch the floor: a floor that follows the source's top partial is the analysis filter's
  skirt, not a floor. Print the analysis filter's measured stopband next to the results.
- **Use a windowed-sinc FIR on the whole run**, taps cut off both ends, far longer for a 60 Hz band than for an
  8 kHz one. An FFT brickwall on a window reads its own edge artifact.
- **The click metrics used so far**: peak 0.7 ms RMS above 8 kHz and peak 20 ms RMS below 60 Hz, both re the
  signal RMS, on band-limited sources at 44.1 and 48 kHz, every knob swept both ways. Read each row against its own
  floor.
- **A case built to expose an artifact must be able to**: the change must not sit under a note onset, the source
  needs low partials, and the voice count must stay low; otherwise a "no" is not evidence.
- **Timings**: interleave the runs (HEAD, tree, HEAD, tree) and carry an untouched control row; quote ratios, not
  raw Node numbers (Node run-to-run spread reaches 28 percent on a row). `audio/ref/performance.md` has the
  benchmark tools.

## The probes

Measuring tools for the DSP stack, built for the engine review (KLANG-00420) and kept after it. This table is their
ONE register: a probe that is not listed here does not exist for the next session, so the change that adds a probe
adds its row. Status `planned` means the row is the approved proposal (maintainer, 2026-11-10), not code yet.

| Probe | What it answers | Home | Status |
|-------|-----------------|------|--------|
| Stage probe | One stage alone under the standard signals (impulse, step, sweep, silence, full-scale noise, a NaN, an infinity, 1e30): peak, DC, tail length, finite output, recovery, back to exact silence, subnormals. `measureStage(stage, signal)` over `katalystProbeStage(dsl)` or `ignitorProbeStage(tree, params)`; the catalogue is `stageProbeCases()` | `audio_be/src/jvmTest/kotlin/probes/` | built 2026-11-10; run: `KLANG_PROBE=stage` with `:audio_be:jvmTest --tests 'io.peekandpoke.klang.audio_be.probes.StageProbeRunner'` under the lock, table and JSON in `<build dir>/audio_be/probes/stage-probe.*` (or `KLANG_PROBE_OUT`) |
| Aliasing meter | Energy off the harmonics, per source and shaper at five pitches (110 to 7044.4 Hz; not 7040, whose folds land on harmonics): a 65536-frame 7-term Blackman-Harris window, floor about -165 dB re the fundamental; the total and, since KLANG-00430, the part below 5 kHz (`nonh<5k`, where an alias is heard: the total is dominated by the band under Nyquist). `measureAliasing(...)`; `analog` 0, sources bare; detuned stacks and plucks are marked not measurable. **The healthy limit** (maintainer, 2026-11-11): per source and shaper, the highest fundamental of a sweep (41 notes, 109 Hz to 14.2 kHz, `fs / (N + 0.382)`) up to which the non-harmonic power below 5 kHz stays under -60 dB re all the harmonics, at that note and every lower one (`measureAliasingLimits(...)`); the health page calls it healthy at or above 5 kHz | `audio_be/src/jvmTest/kotlin/probes/` | built 2026-11-10, the limit 2026-11-11; run: `KLANG_PROBE=aliasing` with `--tests '...probes.AliasingRunner'`, `aliasing.*` and `aliasing-limit.*` |
| Click detector | One knob change, switch, chain swap or voice edge under a 110 Hz tone: the jump and the kink (first and second difference) against the steady signal's, the burst above 5 kHz, the rise and settle time. Its controls set the scale: a 50 ms glide reads kink 2.5 and -86 dB, a hard step 3618 and -18 dB. `measureClick(...)`; a row with sensitivity under 3 is blind | `audio_be/src/jvmTest/kotlin/probes/` | built 2026-11-10; run: `KLANG_PROBE=clicks` with `--tests '...probes.ClickRunner'`, `clicks.*` |
| Block-framing checker | The same note through the production voice path with its onset, its gate close or a cut at each of the 128 offsets in a block, aligned and compared (the property of KLANG-00156): the largest difference re the peak, where it sits, whether it is confined to the onset or transition block or persists, and whether the voice drew the same random values. Classes: identical, rounding (under -200 dB), small (under -100 dB), audible. `measureFraming(...)`, the cases in `_framing_cases.kt` | `audio_be/src/jvmTest/kotlin/probes/` | built 2026-11-10; run: `KLANG_PROBE=framing` with `--tests '...probes.FramingRunner'`, `framing.*` |
| Stage cost table | Nanoseconds per sample for each source, stage and a few whole voices alone (218 rows): the median of 41 interleaved rounds, net of its control, as a share of real time and as a ratio to a plain sine; bytes per block. `measureCosts(...)`, the rows in `_stage_cost_cases.kt` | the shared half in `audio_be/src/commonTest/kotlin/probes/`, the JVM runner in `jvmTest`, the JS entry `runStageCostJs()` in `jsTest` | built 2026-11-10, both platforms; JVM run: `KLANG_PROBE=cost` with `--tests 'io.peekandpoke.klang.audio_be.probes.StageCostRunner'`, table and JSON in `<build dir>/audio_be/probes/stage-cost.*`. Under 10 percent between two runs is noise, and so is any row under 2 ns. JS: the production test bundle under node, five processes, ratios to the sine only (the commands are at the top of `stage-cost-js.txt`); never Karma's development bundle |
| Hot-path lint | A static scan of `audio_be` render code for boxed types, allocations, capturing lambdas, `fun interface` Ignitors; what counts as render code is the settings block at its top (`--list-hot` prints it) | `console/tools/hot-path-lint/` | built 2026-11-10; 30 findings open, none per block (KLANG-00420) |
| Dead-knob finder | Every slot key a door writes has an engine reader, and the reverse (`--list` prints the whole key table) | `console/tools/dead-knobs/` | built 2026-11-10; clean, 13 keys no door writes (info) |

How they are used (maintainer, 2026-11-10):

- **A probe is a measurement, not a gate.** Its measuring code is plain functions that return data
  (`measureClick(...)`); its runner is a test class that prints the table, kept out of the normal test run and
  started on request through the build lock. A probe never makes `check` red.
- **A finding that is fixed leaves a spec behind**: an ordinary spec that calls the probe's measuring function for
  THAT case with a threshold (a check for the future, mutation-checked). Probe and guard share the function, so they
  cannot drift apart.
- **The two static probes may become build checks** once their findings are fixed or waived with a reason.
- **A new stage or source runs the probes that apply to it before it lands**, and the review brief carries the
  tables (`/review-loop`).
- **The results have a page**: `python3 console/dev-status/engine_health.py` builds
  `src/jsMain/resources/klang-engine-health.html` (the Dev Tools' "Engine health" card) from, per probe, the newest
  run folder that holds its JSON (a rerun of one probe is a folder with that probe's files only), a light per item. A lead becomes confirmed, accepted, disproved or fixed by one entry in
  `docs/audio-audit/verdicts.json`; rebuild the page after a run or a verdict (`console/dev-status/README.md`).
- **Each full run's tables go to `docs/audio-audit/<date>/`** (text and JSON), so the next run is a comparison. The full review
  recurs by occasion (before a release line, after a change of the engine's shape), not by calendar.

## A test

- **One law, two hosts, two specs.** A parity spec over a SHARED core only tests the plumbing (a mutation of the
  core moves both sides and stays green); pin the law itself with an ORACLE written in the test
  (`StripLawCoresSpec`, `EnvelopeLawSpec`, `FilterSwapLaw`). An oracle built from the production primitive cannot
  test that primitive.
- **An engagement row for a parity row must fail under the substitution it guards**: "differs from the finite
  case" is satisfied by any substitution of one finite number.
- **A claim about which callers reach a function is a caller search**, not an inference. When a guard changes
  what reaches a leaf, instrument the leaf to throw and run the suites; a grep for literals misses loop variables.
- **A stateless rewrite of a stateful evaluator states the DOMAIN of its law**, not only its formula (the envelope
  evaluated at a gate before its onset extrapolated its attack to +120 dB).
- **A retyped node field breaks JS-only test sources too**: compile `compileTestKotlinJs` of every module before
  calling a retype migrated.
