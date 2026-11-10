# The disproof pass: the brief every challenger reads

Ten auditors read Klang's audio engine layer by layer (KLANG-00420) and reported 128 findings in
`docs/audio-audit/2026-11-10/audit/layer-*.md`. They ran NO engine code: every finding is a reading, hand arithmetic
or a stand-alone model. You are the second reader, a different model on purpose. Your job is to try to DISPROVE the
findings you are given, and to settle by measurement whatever can be measured. A finding that survives you is one
the maintainer can turn into a ticket; one that does not saves a wasted fix. Both results are equally good work.

## Read first

1. `docs/audio-audit/2026-11-10/audit/BRIEF.md` (what the auditors were asked, and their report format).
2. `audio/CLAUDE.md`, `audio/MEMORY.md`, `.claude/skills/review-loop/audio-constraints.md`, and
   `audio/ref/verification.md` (how an engine claim is proven here; "The probes" lists the measuring code you can
   reuse: `audio_be/src/commonTest/kotlin/probes/` and `audio_be/src/jvmTest/kotlin/probes/`).
3. The full text of each finding assigned to you, in its layer file, and the code it names.

## For each assigned finding

1. Check the trace: open every line it cites. Is the code what the finding says? Is the claimed input reachable from
   the door it names (a sprudel call, an Ignitor tree, a Katalyst chain)? Is there a guard upstream or downstream the
   auditor missed? Is it already known, by design or recorded (`audio/MEMORY.md`, the constraints file, KDoc, a
   ticket: `python3 -I console/tools/ticket/ticket.py find <words>`)? Is the severity right for the reach?
2. Where it can be measured, MEASURE it in the real engine: write a reproduction (below). A number from the engine
   beats any argument, the auditor's or yours. Compare your number with the auditor's claim and say how far they are
   apart.
3. Give a verdict:
   - **CONFIRMED** (reproduced in the engine, or the trace holds and you found no escape), with the measured numbers;
   - **CONFIRMED, SMALLER** or **CONFIRMED, LARGER** (real, but the size, the reach or the severity is different:
     say the corrected one);
   - **DISPROVED** (with the line or the measurement that breaks it);
   - **BY DESIGN** (with the place that says so);
   - **UNDECIDED** (what would settle it, and why you could not).
   Also correct the auditor's proposed fix where you see it is wrong or incomplete, in one or two sentences. Do not
   design fixes beyond that, and FIX NOTHING.

## Reproductions

- Put them in `audio_be/src/jvmTest/kotlin/audit/` (package `io.peekandpoke.klang.audio_be.audit`), one Kotest spec
  per cluster of findings, named `Audit<Cluster>Repro.kt`. Reuse the probes' helpers and the existing specs' ways of
  building a voice, a chain, a scheduler; do not duplicate a helper that exists. If a finding lives in another module
  (sprudel, audio_bridge, the frontend), put its reproduction in that module's test folder under `audit/` the same
  way.
- Gate every reproduction like the probes: its tests are enabled only when the environment variable
  `KLANG_PROBE=audit` is set, so the normal test run does not execute them. Each test PRINTS what it measured and
  writes a line per finding to a text file (`<build dir>/audio_be/probes/audit-<cluster>.txt`, the probes'
  `probeOutputFile` helper). It asserts nothing about the engine's values: it is a measurement that a later fix turns
  into a guard. Name the finding id in each test's name (`"L3-01: the pluck's pitch at A4, C6, by brightness"`).
- A reproduction that needs engine code to change, or visibility widened, is not written: say so and leave the
  finding UNDECIDED or argued from the code.

## Gradle (strict)

Only through the lock, one build at a time; other challengers share it, so a call may wait:
  export JAVA_HOME=/opt/dev/sdkman/candidates/java/21.0.12-amzn
  KLANG_PROBE=audit console/with-build-lock.sh ./gradlew :audio_be:jvmTest --tests 'io.peekandpoke.klang.audio_be.audit.<Spec>'
Full qualified names in `--tests`. Read `.claude/skills/console-tools/SKILL.md` ("klang: the lock, long runs",
"test-digest"); verify what ran with test-digest; run long calls in the background as that skill says. Batch your
work so you build few times: write several reproductions, then run. Never read or edit `~/.gradle/gradle.properties`;
do not touch `gradle.properties` in the repo.

## Hard rules

- Engine and frontend code under any `src/*Main` must NOT change. You create only: reproduction specs under an
  `audit/` test folder, and your ONE report file. No commit, no staging, no stash, no reset, no branch switch, no
  `rm -r`, no network, no sub-agents. Other agents work in this checkout: files you did not create are not yours.
- No em or en dashes in what you write. Kotlin files carry the repo's copyright header (copy a neighbour's). Follow
  `.claude/skills/code-style/SKILL.md` (braces always, named arguments where they could be swapped, no
  Long/Char/Byte/Short).
- The Motor stays raw (a stone rule): a missing clamp on an audio parameter is not a defect. A non-finite value, a
  state that never recovers, an allocation in render code, a crash, a broken stated contract are.
- Be exact about what you ran and what you only read. "I measured X" needs the test's name and the number.

## Your report

ONE file, `docs/audio-audit/2026-11-10/audit/disproof-<cluster>.md`:

1. `# Disproof: <cluster>` then what you ran (specs, commands, what test-digest said) and what you only read.
2. A table: finding id, the auditor's severity, your verdict, your severity, one line.
3. Per finding: `### <id>` with **Verdict**, **What I checked** (lines), **What I measured** (the test, the numbers,
   against the auditor's claim), **Reach** (corrected if needed), **On the proposed fix**, **Severity now**.
4. `## New findings` (what you found on the way that no auditor reported; same format as the auditors').
5. `## For the audit ruleset`: three to six lines on what made findings easy or hard to challenge.

Your final message to the coordinator: the path of your file, the paths of the reproduction files, the count per
verdict, and each finding whose verdict or severity CHANGED, in one sentence each. Nothing else.
