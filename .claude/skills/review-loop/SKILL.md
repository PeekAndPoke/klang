---
name: review-loop
description: Use when someone asks to review code changes, run a code review, apply review findings, or verify that tests actually test something (mutation check). Codifies the project review standard - reviews loop until a clean round, fixes get re-reviewed, new tests are mutation-checked.
---

## What This Skill Does

Codifies the two-part Klang quality standard (introduced 2026-08-02):

1. **The review loop** — a fix produced by a review is itself an unreviewed change. Reviews repeat until a clean round,
   a wall, or a needed user decision. One round is never enough.
2. **Mutation-checked tests** — a green test proves nothing until it has been RED for the right reason. Every new test
   must demonstrably be able to fail.

Apply this standard whenever reviewing changes or writing tests — including when the built-in
`/code-review` or `/simplify` produce findings (their output enters the same loop at step 3).

---

## Standard 1 — The Review Loop

### The loop

1. **Collect the change set** — the current diff (vs branch base, or the files just edited).
2. **Review round** — spawn **FRESH** reviewer agents (general-purpose), in parallel.
   **Round 1 is BLIND**: the task description, the change set, and the constraints list — nothing
   else.
   **Every consecutive round runs in TWO PHASES with the same fresh agent** (maintainer,
   2026-08-28: review FIRST, previous results AFTER):
   - *Phase 1 — review.* The agent gets the full current diff, the constraints, and the fix delta
     marked as the primary target — and deliberately NOT the previous round's findings. Pure fresh
     eyes on the current state.
   - *Phase 2 — reconcile.* Send the SAME agent the previous round's findings verbatim plus the
     triage of each (fix / reject+reason / parked). For every phase-1 finding that overlaps a
     settled one, the agent must either WITHDRAW it (the rejection reason stands) or STICK TO IT by
     naming what is factually wrong in the rejection reason. It also states, per previously-FIXED
     finding, whether the fix actually holds in the current diff.
   The coordinator triages the reconciled result. Fresh-eyes value and settled-stays-settled,
   without anchoring the review itself.
    - **Coding reviewer** — ALWAYS (prompt template below).
    - **Audio-engineer reviewer** — when the change touches `audio_be` / `audio_bridge` /
      `audio_fe` / `audio_jsworklet` / sprudel voice data / any DSP or wire path (prompt template below).
3. **Triage every finding** into exactly one of:
    - **fix** — apply it. **Only CRITICAL and MAJOR findings feed the loop.** MINORs are collected
      and either applied once as a single batch WITHOUT a re-review round, or handed to the user
      as a list — they never trigger another round on their own;
    - **reject** — with a stated reason (philosophy rejections must name the rule: raw-Motor no-clamping, reverb
      `ANTI_DENORMAL` exception, documented HPF bias, …);
    - **user-decision** — park it for the user (design fork, tradeoff, by-ear sound question).
4. **Apply the fixes**; run the affected tests (Gradle sequentially — see Gotchas). New tests written here fall under
   Standard 2.
5. **If a CRITICAL/MAJOR fix was applied → go to 2** in the two-phase shape. MINOR-only rounds do
   not loop.

### Termination — the loop stops ONLY on

- **Clean round** — a round returns zero CRITICAL/MAJOR findings → done, report (remaining
  MINORs go to the user as a batch list with a recommendation each).
- **User decision needed** — STOP, present the parked decision (s) crisply, wait. Do not guess.
- **Wall** — no progress: a finding oscillates between rounds, reviewers contradict each other, or a fix is impossible
  without breaking something else → STOP, present the state honestly.
- **Safety valve** — **2 rounds** without a clean round: STOP and consult the maintainer with the
  open findings (maintainer instruction, 2026-08-28; was 5, which let fix-churn feed itself).

### Rules

- **Fresh agents every round.** A reviewer that saw round N is anchored for round N+1 — never reuse one across rounds.
  (Fresh AGENT, informed PROMPT: the round-context pack of step 2 travels to the new agent.)
- **Comment/KDoc findings only when the text is factually WRONG** (would mislead the next reader) —
  never for completeness or style. Prose churn is the documented failure mode of this loop: each
  round's fixes write new text, the next round critiques the new text, and the loop feeds itself.
- **Scope by risk.** The full two-reviewer loop is for changes touching DSP/production code.
  Test-only or doc-only portions get ONE reviewer or none — mutation checks (Standard 2) already
  guard tests harder than a reviewer can.
- **Never silently drop a finding.** Every finding ends as fix / reject+reason / user-decision.
- **Final report** lists: rounds run; per round the findings and their outcomes; the parked user decisions on top.

### Reviewer prompt templates

Coding reviewer (fill the brackets, attach the diff):

> You are a fresh-eyes code reviewer for the Klang project — round [N]; focus especially on
> [the fix delta since the previous round]. (Prior-round findings are deliberately withheld until
> the reconcile phase.) Review the attached diff for: correctness,
> hidden regressions, API consistency, missing test coverage, convention adherence (project
> code-style: braces always, no FQCN, no `Long`/boxed types in audio paths, exhaustive `when`,
> NaN-guard comments). Return a numbered findings list — severity (CRITICAL/MAJOR/MINOR),
> `file:line`, and a concrete failure scenario each. "NO FINDINGS" is a valid answer; do not pad.

Audio-engineer reviewer:

> You are a fresh-eyes audio/DSP reviewer for the Klang project — round [N]; focus on
> [the fix delta since the previous round]. (Prior-round findings arrive in the reconcile phase.) Review the attached diff for: numerical stability (NaN guards, denormal
> handling per house convention), per-sample cost in hot paths (no allocation, no boxing),
> sound preservation (defaults must be behavior-identical), click/zipper risk on parameter
> changes (ramps/crossfades), cycle-boundary correctness. House philosophy: the engine is
> intentionally raw — do NOT propose safety clamps on user-facing params; coerce only where the
> project already coerces. Return findings as severity + `file:line` + failure scenario.
> "NO FINDINGS" is a valid answer; do not pad.

---

## Standard 2 — Mutation-check every new test

A green test proves nothing until it has been RED for the right reason.

### Protocol (per new test)

1. **GREEN** — write the test, run it, confirm it passes.
2. **MUTATE** — introduce ONE targeted mutation that should break the tested behavior. **Prefer mutating the code under
   test** (flip an operator, off-by-one a constant, drop a term, swap a branch); mutating the test's inputs is the
   fallback when the code can't be safely touched.
3. **RED** — run the test. It MUST fail. If it stays green, the test is toothless → fix the test and repeat from 1.
4. **RESTORE** — revert the mutation exactly; run again → green. **Verify with `git diff` that only the intended change
   remains. NEVER leave a mutation behind.**
5. **REPORT** one line per test: `mutation-checked: <what was mutated> → red ✓`

### Scope — two tiers (maintainer, 2026-08-28)

Mutation checking pays where the test's ORACLE IS INDIRECT (you cannot tell from reading the test
whether the assertion binds the behaviour) or where production failure is SILENT. Where the
assertion IS the specification, readable one-to-one, the check is near-tautological.

- **MANDATORY** (full protocol): tests in `audio_be`, `audio_bridge`, the wire codecs and their
  round-trips, the sprudel pattern/timing core (queryArc, event structure, CycleTime, scheduling),
  and **all KSP processors** (`klangscript-ksp`, `sprudel-ksp`, `audio-wire-codec-ksp` — a wrong
  processor emits silently wrong GENERATED code). Plus, regardless of module: every regression
  guard born from a real bug, and every threshold/metric-based assertion (rms, d2, tolerance
  bands) — thresholds are where self-deception hides.
- **LIGHT** (one targeted mutation, or none, at judgment): direct-oracle surface tests
  (value-in → field-out DSL plumbing, klangscript registration/intel), UI, docs, tooling.
  The one exception worth keeping: when a surface test claims to cover a SPECIFIC door among
  several, do the single deletion-mutation of that door (this caught a test asserting the right
  value through the wrong door while the claimed door was deletable).
- **Universal, both tiers:** a test must never derive its expected value or threshold from the
  same constant or expression it guards (self-reference produced the worst survivor: a bound that
  followed a 60× widening of the constant under test). The restore discipline is unchanged.
- **Not** a retrofit mandate for the existing suite — mutation-check old tests opportunistically
  when a change touches them.

### What deserves a test at all (maintainer, 2026-08-28)

- **No value-echo tests.** A test that restates a constant (`preset.x shouldBe 0.05`) is a
  change-detector, not a guard: it fails only on intentional edits and cannot tell a good one from
  a bad one. Where a value matters, guard the BEHAVIOUR it buys (render the thing, assert the
  audible property) — and only where the stakes warrant it.
- **Coverage findings are judged case by case.** Sometimes the code is expressive enough that a
  test adds nothing. A reviewer finding of the form "X is untested" must name a failure the test
  would catch that READING THE CODE cannot; otherwise it is rejected without ceremony.

### Why this exists

The project has shipped toothless guards before: `vowelFloor()` was a silent no-op (the live path never received the
value — caught only by a later review), and Triangle `flankSamples` was proven a no-op only by a render-effect guard.
Mutation checking is the antidote: it tests the test.

---

## Gotchas

- **Gradle: never run two builds concurrently** — corrupts the sprudel KSP cache; recover with
  `:sprudel:clean`.
- Single spec: `./gradlew :module:jvmTest --tests fully.qualified.SpecName` — UNQUOTED FQCN, no wildcards
  (quoted/wildcard filters match nothing). **One `--tests` per run**: chaining several was seen
  to match nothing at all (2026-07-03, 2026-09-06). In a mutate-then-expect-red script that
  `No tests found` exit is indistinguishable from a real kill and produced three spurious RED
  verdicts (2026-08-20): every expect-red runner must grep the log for `No tests found` and
  treat it as a script error, and print the failing test names (an empty list on a "red" is the tell).
- Before a JS/frontend build, check for a running frontend auto-compile watcher (`pgrep -f
  jsBrowserDevelopmentRun` or similar). The maintainer often has one open; the build lock cannot
  serialize against it. Report instead of building when one is running.
- Don't fuss over whitespace/blank-line findings — codefactor.io auto-fixes formatting.
- **Generated batches: review the prose, trust the structure.** Across 88 script-generated accessor
  objects (2026-09-07) the reviewers found zero read/update or parameter slips; every finding was
  in the KDoc, the examples, or a claim about the engine. Point the reviewer at meaning
  (engine gates, units, sign, direction), and let the specs and mutation checks cover structure.
- **Background Gradle chains get killed under memory pressure.** The harness stops a background
  command when the machine runs low; a foreground run of the same chain survives. Before a long
  chain, stop the project's own Kotlin compile daemon (the one whose marker file says
  `klangengine`, 4 to 5 GB when warm); Gradle respawns it. Never `pkill -f` a pattern that also
  matches your own shell's command line.

## Changelog

- **2026-08-28** — Loop tightened after the envelope-ownership review ran 5 rounds. Evidence both
  ways, recorded honestly: the loop's catches were decisive (the IgniteRenderer onset bug behind
  the guitar knocks, the teardown-fade off-by-one, the shared-envelope leak — all reviewer finds),
  but rounds 3-5 were largely prose churn on text the previous round's fixes had just written, plus
  re-litigation of settled rejections. Changes, per the maintainer: round 1 blind; every later
  round carries the previous findings + triage + fix delta; only CRITICAL/MAJOR loop (MINORs batch
  or go to the user); comment findings only when factually wrong; scope by risk; safety valve cut
  from 5 rounds to 2, then consult. Order refined same day: consecutive rounds are TWO-PHASE —
  the agent reviews first WITHOUT the previous findings, then reconciles against them (withdraw, or
  stick to its judgement by naming what is factually wrong in the rejection reason). Review first,
  context after: fresh eyes stay fresh, and settled findings still stay settled.
- **2026-09-07** — Evidence for the two-phase reconcile from the accessor sweep (four batches, eight
  rounds): the reconcile phase twice proved a triage REASON factually wrong (a file the triage said
  did not exist, an engine claim the triage repeated), and each time the correction mattered.
  Reviewers also caught an inverted engine direction (ducking) that a fix had introduced. Keep the
  phase; it is where triage errors surface.
- **2026-08-28** — Standard 2 scope split into MANDATORY (core: audio_be/audio_bridge/wire/sprudel
  timing core/KSP processors, regression guards and threshold assertions anywhere) and LIGHT
  (direct-oracle surface, UI/docs), per the maintainer; plus two testing principles: no value-echo
  tests, and coverage findings judged case by case ("sometimes the code is expressive enough that
  a test will not add any value"). First loop run under the tightened rules (the E1/E2/E10 change
  set) converged in exactly 2 rounds with zero CRITICAL/MAJOR in round 2 — the two-phase reconcile
  produced clean withdraw/stick verdicts and no re-litigation.

