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
- **A rule with a closed list is reviewed by table** (2026-09-18, ledger): when a change lands or
  edits a rule that enumerates cases (which door is in which list, which stage has which gate),
  the reviewer brief asks for a table of EVERY case against the rule's clauses, read from the
  code. Three rounds of prose review missed what one table found in a pass.
- **A cached config is reviewed for the non-finite input** (2026-09-18, ledger): for every
  `configure` / `update` on the audio thread that compares an incoming value against a stored one
  and rebuilds on a mismatch, the audio reviewer answers: what happens when the incoming value is
  NaN (self-unequal, so a raw compare never settles), what is STORED afterwards, and does the
  mismatch branch allocate. The rule for the code: compare and store SUBSTITUTED values, never the
  raw input. A change that touches one such stage asks the reviewer to sweep the others.
- **Every byte-identity claim names its render** (2026-09-18, ledger): made on the final tree,
  exercising the changed path. A render that predates the last edit, or a song that never calls
  the changed door, backs nothing.
- **The effort ladder: every round that is not clean escalates one level, up to max**
  (maintainer, 2026-09-18). The Agent tool has no per-call effort dial, so the levels are agent
  definitions in `.claude/agents/` with model and effort pinned:

  | round | reviewers | how to spawn |
  |---|---|---|
  | 1 (blind) | `opus`, session effort | `subagent_type: general-purpose`, `model: opus` |
  | 2 | `opus`, high | `subagent_type: reviewer-high` |
  | 3 | strongest tier, xhigh | `subagent_type: reviewer-xhigh` (the 2026-09-05 "round 3 on the strongest tier" rule, now with the effort) |
  | 4 and later | strongest tier, max | `subagent_type: reviewer-max` (the safety valve has already fired; the maintainer is in the loop) |

  Why a ladder and not max from the start: a round that is not clean means the previous tier
  missed something or the fix delta introduced something, and both call for more scrutiny of a
  SMALLER target (the delta), so the extra effort is spent where it pays. Round 1 is the wide
  net at the ordinary tier. The implementer of a fix round stays at the session effort unless the
  round found a CRITICAL, then it is briefed on `opus` with the reviewer-high definition's
  discipline restated in the prompt. Watched together with the "opening line" table in
  `/agent-fleet`: if round 2 at high finds what round 1 at session effort missed, the ladder
  earns its cost; if it never does, round 1 can start higher and the ladder shortens.
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

The project has shipped toothless guards before: `vowel(floor = ...)` was a silent no-op (the live path never received the
value — caught only by a later review), and Triangle `flankSamples` was proven a no-op only by a render-effect guard.
Mutation checking is the antidote: it tests the test.

---

## Standard 3: every escape closes one hole (deterministic self-improvement, maintainer, 2026-09-18)

The loop improves itself without experiments. The unit is not a rate over many rounds (the sample
stays too small to estimate one) but a single escaped defect: every CRITICAL or MAJOR that a
review round finds is something the stage before it let through, and each one is classified and
closed ONCE, deterministically, so the same class cannot escape the same way again.

**Classify each CRITICAL/MAJOR by what would have caught it earlier**, and make that thing exist:

| class | what let it through | the fix to the process |
|---|---|---|
| brief | the implementer was not told a constraint or a decided rule | a line in the brief template of `/agent-fleet` or in the rules register |
| checklist | the reviewer template does not ask the question | an item in the `/dsl-design` or `/review-loop` checklist, with the failing scenario as its example |
| test | no test class could see it (a mutation would have stayed green) | a mandatory test pattern for that kind of change (a parity render, a defaults-sync spec, an identity spec) |
| design | it should have been decided before implementing | a "decide before implementing" line in the task doc's step template |
| tooling | a mechanical check would have caught it | a pre-commit grep or a script (`git diff | grep -c '—'`) |

**The signal that a rule failed is recurrence, not a rate.** If a finding of a class that already
has a rule escapes again, the rule's TEXT failed (it was not where the agent read, or it did not
name the scenario), and it is rewritten, not re-stated. One recurrence is enough to act on; that is
what makes the loop deterministic at n = 1.

**What stays a judgement call, set by the maintainer and revisited on evidence:** the effort
ladder, the opening line, the safety valve. The recurrence ledger is the evidence: when round 1
keeps letting through classes that already have a rule, the problem is the tier of round 1, not
the rules.

**The ledger** (one row per escape, appended when the step commits; the classes above):

| date | step | the escape | class | what it changed |
|---|---|---|---|---|
| 2026-09-17 | Katalyst 2, 3c | em-dashes in moved lines reached two commits | tooling | `git diff \| grep '^+' \| grep -c '—'` before every commit |
| 2026-09-17 | Katalyst 3a | the single NaN probe was defeated by `safeOut` | test | the two-probe coercion and its spec |
| 2026-09-18 | Katalyst 3b | the outgoing chain's sends were unramped; the duck was not carried across a swap | checklist | the audio reviewer template asks "what happens to every send and to the envelope state during the swap" |
| 2026-09-18 | Katalyst 5a | `Katalyst(k => k.classic())` content-equal to classic resolved to the voice-driven chain, `katp` inert | design | the rule "only the born-with chain is voice-driven", decided before 5a's fix round |
| 2026-09-18 | Katalyst 5a | per-door map allocation on the query path | checklist | "allocation per event on the query path" in the coding reviewer template |
| 2026-09-18 | Katalyst 5a-2 | classic's `body.wet` was a SET 0.0, so a material-only `body("wood")` on a declared chain was dry | design + checklist | the compound-door fill rule (rules register, `/dsl-design` §4, checklist 11) and the defaults-sync spec's unset family |
| 2026-09-18 | Katalyst 5a-3, round 1 | the coordinator's rule text named `wet` as the gate of the sends and said the gate is never invented, which the blueprint `reverb(size = 4)` contradicts; the next implementer would have deleted the send fill | brief (the rule author did not read the blueprint before writing the rule) | the register row and `/dsl-design` §4 state the gate per stage kind (name knob vs any knob); rule: a rule that names a blueprint is checked against the blueprint's own spec before it lands |
| 2026-09-18 | Katalyst 5a-3, round 1 | the fills handed `setOrDefault` the voice field, so a `katp` between two calls of the same door was overwritten | checklist | `/dsl-design` checklist 12: the value handed to `setOrDefault` is what this call named, never a field an earlier fill wrote |
| 2026-09-18 | Katalyst 5a-3, round 2 | the corrected rule text listed the any-knob stages as "the sends and the compressor" and omitted the phaser, which the same round brought under the rule; an implementer reading the closed list would delete the phaser fill | recurrence of the round 1 text escape, one round later (the rule enumerated stages without saying the list was closed, so an addition elsewhere in the same change did not update it) | §4 now marks both lists CLOSED and complete and says a new bus door joins one of them in the same change; checklist 11 repeats it. Lesson for the rule author: an enumeration in a rule is a contract, write "closed" or do not enumerate |
| 2026-09-18 | Katalyst 5a-3, round 3 | the duck door filled its companions on ANY knob (since step 5a), while the rule and the body door say a name-knob stage fills only when its name knob is named; audible on a custom chain (`duck(attack = 0.3)` wrote `duck.depth = 0.0` over a chain-authored 0.8); two guards in one spec encoded opposite readings of the same clause | checklist (no review had checked every door against the rule; the strongest-tier reviewer built a door-by-door table against the two closed lists and found it at once) | the duck fills only when THIS call named an orbit; `/review-loop`: when a change lands a rule with a closed list, the reviewer brief asks for the door-by-door table |
| 2026-09-18 | Katalyst 5a-3, rounds 1 to 3 | the same rule text escaped three times in one step, each correction reaching only some of a dozen copies (register, skill, `ParamBag`, the classic KDoc, two constants headers, seven door KDocs, `MEMORY.md`) | structural (a rule restated at every site that obeys it cannot be corrected atomically) | `/dsl-design` §4 is the ONE home of the rule's text; every other site states only its own facts and points there; enumerations of "which stage is in which list" are never copied |
| 2026-09-18 | Katalyst 5a-3, round 3 | byte-identity claims rested on renders that did not exercise the change: neither frozen song calls the phaser, and one render predated the last batch | test (the evidence audit: which render, made when, backs which claim) | before a step commits, every byte-identity claim names a render made on the final tree that exercises the changed door; a changed door no frozen song calls gets a before/after render of a song that does |
| 2026-09-18 | found in Katalyst 5a-3 round 3, fixed as its own step | a NaN body or vowel wet from the wire made `KatalystBodyEffect.configure`'s compare against its stored mix true forever: two filter banks allocated per block on the audio thread, the crossfade never completing, and the body inaudible; the nullable floor had the same defect (a `Double?` compare is IEEE on both the JVM and Kotlin/JS, measured) | checklist (pre-existing since the effect was written; no review had asked what a cached config does with a non-finite input) | both effects substitute at the entry of `configure` and compare and store the substituted values; the gain stage got the same guard at its door; the rule "a cached config is reviewed for the non-finite input" above; the first sweep found every other stage safe |
| 2026-09-18 | Katalyst 5a-3, round 1 | `(a == b) shouldBe false` recurred in a new spec one round after it was retired at three sites | recurrence (the rule lived only in a round's findings, nowhere an author reads) | `/code-style` §23: `shouldNotBe`, except on a boxed NaN where the raw form is deliberate |

## Gotchas

- **A scripted rename must know what a word is.** A door name that is also an English word (`voices`,
  `spread`, `vibrato`, `compressor`, `body`) rewritten by a bare regex lands in KDoc prose, Lexikon
  strings, tutorial text and even a Kotlin function name (batch G, 2026-09-07: 215 prose sites).
  Rewrite reads only in a call context (an argument, a `.mul(` chain) and let a reviewer grep the
  dotted paths afterwards; the compiler cannot tell prose from code inside a string.
- **Gradle: never run two builds concurrently** — corrupts the sprudel KSP cache; recover with
  `:sprudel:clean`.
- Single spec: `./gradlew :module:jvmTest --tests fully.qualified.SpecName` — UNQUOTED FQCN, no wildcards
  (quoted/wildcard filters match nothing). **One `--tests` per run**: chaining several was seen
  to match nothing at all (2026-07-03, 2026-09-06). In a mutate-then-expect-red script that
  `No tests found` exit is indistinguishable from a real kill and produced three spurious RED
  verdicts (2026-08-20): every expect-red runner must grep the log for `No tests found` and
  treat it as a script error, and print the failing test names (an empty list on a "red" is the tell).
- **A frontend watcher blocks Gradle only in CONTINUOUS mode** (maintainer, 2026-09-09). What the
  build lock cannot serialize against is a build that keeps rebuilding on its own, which means
  `-t` or `--continuous`. A plain `jsBrowserDevelopmentRun` is not that, so it does not block you.
  Check `ps aux | grep gradle | grep -- "-t \|--continuous"`, not the bare process name. The old
  rule read the process name alone and stalled a whole round of work for nothing.
- **A `--tests` filter does not apply to a JS test task the way it does on the JVM** (2026-09-17):
  `:audio_bridge:jvmTest :audio_bridge:jsTest --tests <Fqcn>` in one invocation fails. Filter the JVM
  task; run the JS task unfiltered, on its own.
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

- **2026-09-18**: the effort ladder per round (round 2 `reviewer-high`, round 3 `reviewer-xhigh`,
  round 4 and later `reviewer-max`, agent definitions in `.claude/agents/`).

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

