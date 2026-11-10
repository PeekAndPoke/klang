# Fixing the audit's findings: the brief every fixer reads

The engine audit (KLANG-00420) left 129 findings, each assigned to one of nine tickets (KLANG-00425 to KLANG-00433).
The maintainer's decision (2026-11-10): "create tickets and fix all of the findings. Sound changes are fine, for
example for the pluck." You fix ONE ticket. Another model reviews your work afterwards, and it will read the diff
against the findings one by one.

## Read first

1. Your ticket (find it: `python3 -I console/tools/ticket/ticket.py find KLANG-004nn`): the table of findings.
2. For each finding: its section in `docs/audio-audit/2026-11-10/audit/layer-*.md` (the trace, the proposed fix
   shape) AND its section in the `disproof-*.md` named in the ticket's row (the measurement, the corrected reach, the
   remarks "On the proposed fix": where the two disagree, the disproof report is the measured one). Then the
   reproduction test that carries the finding's id in its name (`src/jvmTest/kotlin/audit/` in `audio_be`, `sprudel`
   or `klang`).
3. `audio/CLAUDE.md` (the engine rules), `audio/MEMORY.md`, `.claude/skills/review-loop/audio-constraints.md`, the
   `audio/ref/*.md` page of the area, `.claude/skills/code-style/SKILL.md`, `.claude/skills/dsl-design/SKILL.md` when
   a door or a knob changes, `audio/ref/verification.md` (how an engine change is proven; the probes).

## How a finding is fixed

- The smallest change that makes the finding untrue, in the place the report traced it to. No refactoring on the
  way, no new abstraction, no "while I am here". Complexity is the enemy (a stone rule of this project): if a fix
  wants build-time magic, a new module, clever indirection or a wide rewrite, STOP on that finding and put it under
  "Decide" in your report with the options.
- The engine rules hold: no allocation, boxing, capturing lambda or exception in render code; no
  Long/Char/Byte/Short in `audio_be`, `audio_bridge`, the wire codecs; block size stays 128; braces always; named
  arguments where they could be swapped; a NaN-guard comment where a comparison relies on NaN's behaviour; IIR state
  is flushed.
- The Motor stays raw (stone): do NOT add a safety clamp to an audio parameter. A fix for a non-finite INPUT follows
  the existing contract in `audio/ref/numerical-safety.md`: a non-finite value a pattern writes reads as UNSET, at the
  one place that field is read (the way `gain` is already guarded), compared and stored as the SUBSTITUTED value.
- Sound changes are allowed where the finding is about the sound (the maintainer said so). Every other fix must leave
  the corpus render identical. Say for each fix which kind it is.
- A finding whose right fix is a matter of taste, of a public door's shape, or of a rule the project has not decided
  (for example whether an allocation once per note in render code is allowed) is NOT forced: implement the option the
  reports recommend only if it is small and clearly within the rules above; otherwise list it under "Decide" with the
  options and your recommendation, and go on.
- Docs follow the code in the same change: the `audio/ref` page, the KDoc, `audio/MEMORY.md` (update the section IN
  PLACE and add ONE History line; never append an essay), the tutorial or song text if a door's behaviour changed.
  A deliberate exception gets a KDoc that says which rule it departs from and why.

## Tests

- Every fix gets an ORDINARY spec that fails without it (in the module's normal test folder, beside the specs of that
  class, not in `audit/`). Take the measurement from the finding's reproduction and assert the fixed value. Then
  delete that finding's test from the `audit/` reproduction file (when a reproduction file is empty, delete the
  file); a reproduction of a finding you put under "Decide" stays.
- Mutation-check every new spec with `console/tools/mutate` (one campaign per batch of fixes): the mutant is the fix
  taken out or bent; it must be KILLED for the expected reason. Report the list.
- A spec the audit found to PIN a defect as intended (the reports name them) is changed to pin the fixed behaviour,
  and you say so.

## Proof

- The module suites that your change can reach, JVM and JS where the code is common
  (`:audio_be:jvmTest`, `:audio_bridge:jvmTest`, `:sprudel:jvmTest`, `:klang:jvmTest`, the matching `jsTest` /
  `jsBrowserTest`: `.claude/skills/console-tools/SKILL.md`, "klang's test tasks"), read with test-digest.
- The corpus: `./gradlew runSongBenchmark --args=corpus` (through the lock). Expected: identical, except the songs a
  deliberate sound change moves. Name every song that moved and why; a song that moved without a reason is a bug in
  your fix.
- Where a probe measures the fixed thing, rerun that probe (`KLANG_PROBE=<name>`, the commands are in
  `audio/ref/verification.md`, "The probes") and quote the row before and after.
- If a field of a wire type changes, the wire schema hash pin in
  `audio_bridge/src/jsTest/kotlin/WireCodecRoundTripSpec.kt` moves: update it and say so.

## Gradle (strict)

Only through the lock, one build at a time, never in parallel, no sub-agents:
  export JAVA_HOME=/opt/dev/sdkman/candidates/java/21.0.12-amzn
  console/with-build-lock.sh ./gradlew <task> [--tests 'full.qualified.SpecName']
Long runs in the background as the console-tools skill says. Never read or edit `~/.gradle/gradle.properties`; do not
touch `gradle.properties` in the repo.

## Hard rules

- No commit, no staging, no stash, no reset, no `git checkout -- <file>`, no branch switch, no `rm -r`, no network.
  The coordinator stages and commits after the review. You may delete single files you are told to delete above.
- Do not edit: `CLAUDE.md` files, skills, hooks, settings, the audit's reports (`docs/audio-audit/`), other tickets,
  `/opt/dev/peekandpoke/ultra/`. In your own ticket you may fill "Decide" and add notes; do not change its status.
- No em or en dashes in anything you write (code comments, KDoc, docs, test names). Kotlin files carry the repo's
  copyright header.
- Other sessions work in this checkout. A file you did not change is not yours to "tidy"; if the tree does not
  compile because of someone else's edit, stop and report it.
- Report honestly: a finding you could not fix, a test you could not make fail, a suite you did not run.

## Your report (your final message; it is all the coordinator sees)

1. Per finding: FIXED / DECIDE / NOT FIXED, the files changed, one sentence on the change, the guarding spec's name,
   and whether it changes the sound.
2. Every path you created, changed or deleted (the coordinator stages exactly these).
3. The mutation list with verdicts.
4. What test-digest said for each suite, and the corpus line (which songs moved, and why).
5. Probe rows before and after, where a probe measures the fix.
6. The "Decide" items with options and your recommendation.
7. Anything you saw on the way that is wrong and not in your ticket (do not fix it; name it).
