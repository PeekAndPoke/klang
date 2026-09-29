# Audio: how an engine change is proven

The method the audio work settled on during phase 3 and the Katalyst steps (2026-09), kept because each rule
below was paid for once. The review standard itself (review until a clean round, mutation-check every new test)
is `/review-loop`; this file is the audio-specific half. The stories behind the rules are in
`audio/ref/memory-history.md`.

## An identity claim

- **Render the corpus, both sides, in raw doubles.** The built-in songs plus the frozen songs and pieces
  (`src/jvmMain/kotlin/FrozenSongs.kt`, `FrozenPieces.kt`), 256 cycles, 48 kHz, the wall-clock seeds pinned
  (`timeOfDay`, `sinOfDay`, `sinOfDay2`, `timeOfNight`, `sinOfNight` to `pure(0.5)`), HEAD in a throwaway
  `git worktree` against the tree. Compare doubles, not a 16-bit WAV: a 16-bit render cannot see a sub-LSB change,
  so a bit-level claim needs a spec, not the corpus.
- **Render from the repo root.** The JVM sample bank loads and writes `./cache` relative to where it runs
  (`audio_fe/src/jvmMain/kotlin/samples.kt`), and a Gradle `-p <tree>` run needs ABSOLUTE song paths. The CLI render
  plays samples; the `:jvmTest` helper `renderSong` has no sample bank, so a sample-fed orbit is silent there.
- **Every identity run needs an engagement control**: a deliberate mutant that must move the rows the change can
  reach. A green run alone is not evidence. Predict WHICH rows move before running, and read each song's
  arrangement and imports to do it: a grep finds what is DEFINED, not what PLAYS (a song may define an instrument
  its arrangement never plays; another reaches one through `import`).
- **Attribute every difference.** Strip the suspected calls from both sides (e.g. every `.phaser(...)`) and show
  the rest is bit-identical, or log per orbit which event differs.
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
