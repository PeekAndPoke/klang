# The engine audit of 2026-11: the brief every layer auditor reads

You audit ONE layer of Klang's audio engine (KLANG-00420). The code was written and reviewed by another model over
months; you are the first reader who is not its author. This is an audit, not a review of a diff: you read what is
there, with measurements in hand, and you report what is wrong, risky, slow or untrue. You fix nothing.

## Read first

1. `audio/CLAUDE.md` (the module map, the engine rules, the deliberate exceptions) and `audio/MEMORY.md` (what is true
   now, the laws and constants in force).
2. `docs/audio-backend-file-map.md` (where every class lives).
3. The `audio/ref/*.md` page(s) your layer's brief names. They state the INTENDED behaviour; a difference between a
   ref page, a KDoc and the code is a finding in itself.
4. `.claude/skills/review-loop/audio-constraints.md`: the deliberate exceptions a reader must not "fix".
5. The leads of the probes: the section "Leads from the probes" in `docs/tasks/in-progress/KLANG-00420-*.md`, and the
   tables in `docs/audio-audit/2026-11-10/` (`stage-probe`, `stage-cost`, `aliasing`, `clicks`, `framing`,
   `hot-path-lint`, `dead-knobs`, each as `.txt` and `.json`; the legend is at the top of each `.txt`). Every lead
   that touches your layer must be CONFIRMED with the cause in the code (file and line, the mechanism), DISPROVED
   (why the probe misread), or marked BY DESIGN with the place that says so. Do not repeat a lead as a finding
   without having read the code that causes it.
6. Then the code of your layer, all of it, including its tests (what do they really pin?).

## What to look for

- **The math**: coefficient formulas, stability near Nyquist and at extreme Q, envelope segment edges, off-by-one
  frames at block borders, phase wrap, the pan law, interpolation, gain staging, units (seconds, never cycles; Hz;
  dB against linear).
- **Sound defects**: a click (a switch, a swap, a jump without a fade or glide), aliasing, zipper noise, DC, a
  denormal path, a tail that never ends, a level surprise.
- **Block-framing dependence**: anything decided "once per block" that should be per sample, or at a block border
  that should be at a sample (`docs/tasks/KLANG-00156-block-framing-invariance.md` has the two failure modes and
  the ledger of known cases).
- **Numerical safety** (`audio/ref/numerical-safety.md`): every feedback loop, divisor, `log`, `sqrt`, `pow`, table
  index and cast against NaN, infinity, huge and negative input; can one bad value poison a voice, a cylinder or the
  master FOR GOOD; is a non-finite input compared raw against a cached value (self-unequal, never settles).
- **Real-time safety** (`audio/ref/performance.md`): allocation, boxing, a capturing lambda, an exception, a growing
  map, unbounded work in render code. Say whether it is per sample, per block, per note, per change or periodic.
- **Lifecycle and leaks**: state not reset on reuse, a pooled object returned dirty, per-playback state that outlives
  its playback, a voice or a tail that can live for ever, an order-of-operations hazard between threads or blocks.
- **Consistency**: one law implemented twice with a difference, a constant duplicated instead of shared, a default
  that differs between two places, a KDoc or ref page that says what the code does not do.
- **Dead and half-wired things**: a parameter nobody reads, a branch no input reaches, a flag never set.
- **Test truth**: what the layer's specs really pin; a behaviour only the corpus render covers; a test that would
  stay green under an obvious mutation of the code it names (say which mutation).
- **Performance**: per-sample work that changes per block only; a stage that runs while contributing nothing; work
  repeated per voice that could be shared; several passes that could be one; a branch inside a per-sample loop; an
  exact function where an approximation is inaudible (that one is a SOUND decision: mark it so). Give every
  candidate its measured cost from `stage-cost.txt` where there is one. Kotlin/JS is the target that matters most.
- **Listening tasks**: a question of taste (is this reverb good, is this filter's character right) is not a finding.
  List it apart, as something the maintainer's ears must judge, with what to listen for.

## Rules

- READ-ONLY. You may run `python3 -I console/tools/hot-path-lint/hot_path_lint.py` and
  `python3 -I console/tools/dead-knobs/dead_knobs.py`, `grep`, `git log`, `git show`, `git blame`. You may NOT run
  Gradle, edit or create any file except your ONE report file, commit, stage, stash, reset, switch branches, delete
  anything, or use the network. Never read `~/.gradle/gradle.properties`. Do not spawn sub-agents.
- The Motor stays raw is a STONE rule: a missing safety clamp on an audio parameter is NOT a finding. A user who asks
  for a self-oscillating filter gets one. What IS a finding: a non-finite value, a state that never recovers, an
  allocation, a crash, a contract the code itself states and breaks.
- A deliberate exception listed in `.claude/skills/review-loop/audio-constraints.md` or marked by design in `audio/MEMORY.md` is not a finding;
  if you think the decision is wrong, put it under "Decisions worth revisiting" with your argument, once.
- Precision over volume. A finding you did not trace through the code to a concrete input and a concrete wrong
  output is a SUSPICION: list it under that heading, with what would settle it. Ten traced findings are worth more
  than fifty guesses. Say what you did NOT read.
- Another reader will try to DISPROVE every finding you report. Give them what they need: the exact lines, the input,
  the expected and the actual result, and the cheapest test or measurement that would settle it (a spec to write, a
  probe case to add, a mutant).
- No em or en dashes in what you write. No hype. Plain sentences.

## Your report

Write ONE file: `docs/audio-audit/2026-11-10/audit/layer-<n>-<name>.md` (the name is in your brief). Structure:

1. `# Layer <n>: <name>` then **What I read** (files, with a note where you only skimmed) and **What I did not**.
2. `## The probes' leads in this layer`: one row per lead: CONFIRMED / DISPROVED / BY DESIGN / NOT MINE, the cause
   with file and line.
3. `## Findings`: each as
   `### L<n>-<nn> <one line>` then
   - **Severity**: critical (wrong sound or a crash in ordinary use, or damage that does not recover) / major (wrong
     in a reachable case, or a real-time violation) / minor / note.
   - **Tag**: bug / sound / safety / realtime / leak / performance / consistency / dead / test / doc.
   - **Where**: `path:line` (every place).
   - **What**: the claim, traced: the input, what the code does step by step, the wrong result.
   - **Reach**: which door or song shape gets there (a sprudel call, an Ignitor tree, a Katalyst chain), or
     "internal only".
   - **Settle it**: the cheapest test, probe case or mutant that confirms or refutes it.
   - **Confidence**: high (traced end to end) / medium / low.
4. `## Performance candidates`: a table: where, what, per sample / block / note, measured cost if known, expected
   gain (rough), risk to the sound.
5. `## Suspicions` (not traced), `## Decisions worth revisiting`, `## Listening tasks`, `## Test truth` (what is
   pinned, what is not, mutations that would survive).
6. `## For the audit ruleset`: three to six lines on what worked and what did not in this brief, for the deep-audit
   rules the project will write afterwards.

Your final message to the coordinator: the path of your file, the counts per severity, and your five most important
findings in two sentences each. Nothing else.
