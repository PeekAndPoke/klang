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
2. **Review round** — spawn **FRESH** reviewer agents (general-purpose), in parallel:
    - **Coding reviewer** — ALWAYS (prompt template below).
    - **Audio-engineer reviewer** — when the change touches `audio_be` / `audio_bridge` /
      `audio_fe` / `audio_jsworklet` / sprudel voice data / any DSP or wire path (prompt template below).
3. **Triage every finding** into exactly one of:
    - **fix** — apply it;
    - **reject** — with a stated reason (philosophy rejections must name the rule: raw-Motör no-clamping, reverb
      `ANTI_DENORMAL` exception, documented HPF bias, …);
    - **user-decision** — park it for the user (design fork, tradeoff, by-ear sound question).
4. **Apply the fixes**; run the affected tests (Gradle sequentially — see Gotchas). New tests written here fall under
   Standard 2.
5. **If any fix was applied → go to 2.** The next round reviews the *current* state — fixes from the previous round are
   unreviewed changes. Tell reviewers the round number and where the latest delta is, but give them the full current
   diff.

### Termination — the loop stops ONLY on

- **Clean round** — a round returns zero actionable findings → done, report.
- **User decision needed** — STOP, present the parked decision (s) crisply, wait. Do not guess.
- **Wall** — no progress: a finding oscillates between rounds, reviewers contradict each other, or a fix is impossible
  without breaking something else → STOP, present the state honestly.
- **Safety valve** — 5 rounds without a clean round counts as a wall.

### Rules

- **Fresh agents every round.** A reviewer that saw round N is anchored for round N+1 — never reuse one across rounds.
- **Never silently drop a finding.** Every finding ends as fix / reject+reason / user-decision.
- **Final report** lists: rounds run; per round the findings and their outcomes; the parked user decisions on top.

### Reviewer prompt templates

Coding reviewer (fill the brackets, attach the diff):

> You are a fresh-eyes code reviewer for the Klang project — round [N]; prior rounds fixed
> [summary]; focus especially on [latest delta]. Review the attached diff for: correctness,
> hidden regressions, API consistency, missing test coverage, convention adherence (project
> code-style: braces always, no FQCN, no `Long`/boxed types in audio paths, exhaustive `when`,
> NaN-guard comments). Return a numbered findings list — severity (CRITICAL/MAJOR/MINOR),
> `file:line`, and a concrete failure scenario each. "NO FINDINGS" is a valid answer; do not pad.

Audio-engineer reviewer:

> You are a fresh-eyes audio/DSP reviewer for the Klang project — round [N]; focus on
> [latest delta]. Review the attached diff for: numerical stability (NaN guards, denormal
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

### Scope

- **Mandatory** for: new specs, regression guards, and tests written as review-loop fixes.
- **Not** a retrofit mandate for the existing suite — mutation-check old tests opportunistically when a change touches
  them.

### Why this exists

The project has shipped toothless guards before: `vowelFloor()` was a silent no-op (the live path never received the
value — caught only by a later review), and Triangle `flankSamples` was proven a no-op only by a render-effect guard.
Mutation checking is the antidote: it tests the test.

---

## Gotchas

- **Gradle: never run two builds concurrently** — corrupts the sprudel KSP cache; recover with
  `:sprudel:clean`.
- Single spec: `./gradlew :module:jvmTest --tests fully.qualified.SpecName` — UNQUOTED FQCN, no wildcards
  (quoted/wildcard filters match nothing).
- Don't fuss over whitespace/blank-line findings — codefactor.io auto-fixes formatting.
