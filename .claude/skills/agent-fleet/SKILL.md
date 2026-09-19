---
name: agent-fleet
description: Use when spawning sub-agents, launching multiple agents in parallel, fanning out tasks with the Agent tool, or writing Workflow scripts. Defines how to pick model and effort per sub-agent, and the Klang-specific safety rules for fan-out (Gradle serialization, worker sub-fan-out ban).
---

## What This Skill Does

> **Status: PROVISIONAL.** Ported from the sibling `ultra` project 2026-08-04 and adjusted for Klang.
> When a fleet run shows a mapping is wrong (a tier too weak for the task, or wastefully strong),
> propose a concrete adjustment to this file and apply it once the user approves. Log accepted
> changes in the changelog at the bottom. **The ultra copy is the upstream — when a rule changes on
> both sides, keep them in sync** (`/opt/dev/peekandpoke/ultra/.claude/skills/agent-fleet/SKILL.md`).

Defines how to assign models and effort levels when distributing work across sub-agents. Applies to every Agent tool
call and every Workflow `agent()` call. Invoke it (mentally or via `/agent-fleet`)
before launching any multi-agent fan-out.

**Klang skills that fan out and are governed by this one:** `/review-loop` (2+ fresh reviewers per round, looped),
`/six-hats` (5 concurrent perspective agents).

## Core principle

The main loop is the **coordinator**. It runs on the user-selected model and effort — never downgrade it, and keep final
synthesis and judgment in the coordinator rather than delegating them to a cheap agent. Sub-agents are **workers**: each
gets the cheapest model/effort tier that can do its task well.

## Model tiers

| Task type                                            | Model                  | Klang examples                                                                                                       |
|------------------------------------------------------|------------------------|----------------------------------------------------------------------------------------------------------------------|
| Coding, implementation, debugging, hard verification | `opus`                 | write/fix a spec, mutation-check a claim, fix a failing test, adversarially verify a DSP correctness claim           |
| Info retrieval, exploration, research, summarization | `sonnet`               | map a subsystem, find usages of a DSL op, inventory specs, summarize a diff or a task doc                            |
| Trivial mechanical work                              | `haiku`                | extract/list/count, grep sweeps, format conversion, high-volume simple scans                                         |
| Frontier reasoning                                   | inherit (omit `model`) | architecture judgment, by-ear/sound design tradeoffs, cross-cutting analysis the coordinator can't decompose further |

**Klang note — review rounds 3 and later run on the strongest tier.** Maintainer instruction
2026-09-05: when a `/review-loop` needs a third round, run the reviewers on `fable` (or inherit
when the session already is), not on `opus`. Rounds 1 and 2 follow the table.

**Klang note — DSP review is correctness-critical.** The audio-engineer reviewer in `/review-loop`
judges numerical stability, per-sample cost and click risk; run it at `opus` or inherit, never
`sonnet`. Same for anything touching `audio_be` hot paths.

## Effort tiers

| Stage                         | Effort                        |
|-------------------------------|-------------------------------|
| Mechanical / bulk stages      | `low`                         |
| Standard work                 | omit (inherit session effort) |
| Hardest verify / judge stages | `high` or `xhigh`             |
| Review rounds 2, 3, 4+        | `high`, `xhigh`, `max` via the `reviewer-*` agent definitions (`/review-loop`, the effort ladder) |

## Where the dials live

- **Agent tool**: set the `model` parameter per call. There is no per-call effort override — effort comes from the agent
  definition. `fork`-type agents always inherit the parent model; don't set
  `model` on them. Klang pins effort through `.claude/agents/reviewer-high.md`, `reviewer-xhigh.md`
  and `reviewer-max.md` (added 2026-09-18 for the review ladder).
- **Workflow `agent()`**: set both `model` and `effort` in the opts, per stage.
- **Custom agents** (`.claude/agents/*.md`): can pin model/effort in frontmatter; prefer that for agents whose task type
  never varies. Klang currently has one: `music-platform-strategist`
  (explicit-invocation only, never for coding).
- **Agent type**: prefer `Explore` for read-only search fan-out (it reads excerpts, not whole files — cheaper and it
  won't edit anything). Use `general-purpose` when the worker must run commands or write files.

## Decision procedure

1. Before launching, classify each sub-task into a tier using the tables above.
2. Set `model` (and `effort`, where available) explicitly on every call — don't let a whole fleet silently inherit the
   session model.
3. When launching a fleet, state the mapping in one line (e.g. "3 Sonnet finders + 1 Opus verifier") so the user can
   correct it — that feedback is how this provisional policy improves.
4. Decide **who owns the build** before launching (see below). Say it in the same line.

## ⚠️ Gradle is a single-writer resource — this is the Klang rule

Two failure modes, and the second is the expensive one:

1. **Two concurrent Gradle invocations corrupt the sprudel KSP cache** (recovery: `:sprudel:clean`; symptoms surface
   later as stale-class or IR-lowering errors somewhere unrelated).
2. **A build that races an edit produces a wrong answer.** If one agent restores a mutation while another runs a test,
   the second agent's verdict is about code it never chose — and a green that should have been red looks exactly like a
   toothless test. No error is raised. This silently poisons an audit ledger.

### The lock

`console/with-build-lock.sh` takes an exclusive `flock` on `.claude/build.lock` and exits **75** if it cannot get it
(default wait: 900 s, `KLANG_LOCK_TIMEOUT`).

```bash
console/with-build-lock.sh ./gradlew :audio_be:jvmTest --tests some.Fqcn
console/with-build-lock.sh bash -c 'apply-mutation && ./gradlew ... ; restore-mutation'
```

**For a mutation check the critical section is `mutate → build → restore`, not just the build** — wrap all three.
`.claude/BUILD-LOCK.md` is the advisory half: the holder record plus one row per workstream that is uncommitted right
now. Read it as its own step, never chained into the build with `&&`. It is coordination only, not a ledger, so keep it
short: a row dies when its work is committed, and old handover notes go to `.claude/build-lock-log.md`, which nobody
reads to take the lock.

### Rules for the fan-out

- **Default: the coordinator owns the build.** Workers read, analyse and propose; the coordinator builds once,
  afterwards. This is the right shape for review and analysis fan-outs.
- **Say it in the worker prompt**: *"Do NOT run Gradle or any build command. Report what should be run; the coordinator
  runs it."* A worker that ignores this still cannot race — the lock is real — but it will block for up to 15 minutes
  and look like a stall.
- **Only ONE owner mutates production code, ever.** A lock around the build does not fix this: two workers mutating
  `audio_be` read *each other's* edits and both draw wrong verdicts. Mutation campaigns are inherently serial and belong
  to a single owner.
- If a worker genuinely must build, give exactly one worker that permission, and require the wrapper.

## Concurrency & fan-out safety

Cost isn't the only failure mode — in one upstream run, several heavy agents launched at once **stalled or disconnected
mid-response**, and a failed agent writes nothing. **Causation is not proven:** that run also happened over a flaky
network connection, which could equally explain the stalls. So treat the limits below as prudent defaults, not hard
evidence-backed ceilings — they cost little and remove one variable. If future runs show heavy concurrency is reliable,
**broaden it back in small increments** (e.g. 2–3 → 4 → 5 heavy agents) and note what held.

- **Workers must not fan out.** A sub-agent that spawns its own sub-fleet compounds load invisibly and, if it dies,
  orphans its children — their finished results are discarded with the parent. When a worker's task might tempt it to
  delegate, tell it explicitly in the prompt: *"Do NOT spawn sub-agents. Work sequentially yourself; read files in small
  batches."* Keep fan-out one level deep: the coordinator fans out, workers do not.
- **Cap concurrent heavy-tier agents at ~2–3.** `opus`/inherit-tier agents are the ones that stall under simultaneous
  load. `sonnet`/`haiku` workers parallelise fine (run 5–6+). If a phase needs many heavy agents, batch them or run the
  heaviest synchronously (`run_in_background: false`) so a stall surfaces immediately instead of after a watchdog
  timeout.
- **On failure, retry the one agent** — synchronously, sub-fan-out forbidden — rather than relaunching the whole fleet.
  Check what already landed on disk first; partial work may survive.
- **No two workers edit the same file.** Klang fan-outs are usually review/analysis, so this rarely binds; when it
  would, either partition by file or use `isolation: "worktree"`.

## Rules of thumb

- **Fan-out multiplies cost.** For large sweeps (~10+ agents), use cheap finders (`sonnet`/`haiku`)
  feeding a narrow, expensive verify stage (`opus`, high effort) — not an expensive model on every item.
- **When unsure between tiers:** tier up for correctness-critical work, tier down for volume/coverage work.
- **Escalate, don't accept.** If a cheap agent returns a weak or suspect result, re-run that one task a tier up instead
  of patching around bad output.
- **Don't use `haiku`** for anything whose output the coordinator can't cheaply sanity-check.
- **Give workers the constraints, not just the task.** Klang carries a large body of *deliberate*
  decisions (raw Motor no-clamping, reverb's `ANTI_DENORMAL` exception, the linear SVF, documented HPF bias). A reviewer
  without that list files findings that would make the engine worse. Paste the relevant constraint list into the
  prompt — `/review-loop` has templates, and
  `docs/tasks/audio-backend-audit.md` §7 has the audio-backend list.

## Every brief opens with the bar: world-class, not average (maintainer, 2026-09-18, under observation)

Every agent in the fleet, whatever its role (coder, reviewer, audio engineer, tester, strategist,
writer), is told at the top of its brief that it is world-class at that role, and the coordinator
says the same about itself ("you are a great coder, and I am a great manager"). This is not
politeness.

**Why:** the training data these models are built on holds work of every quality, roughly normally
distributed, and an agent that is not told otherwise reaches for the middle of that distribution:
the ordinary fix, the test that restates the implementation, the review that files whitespace.
Klang works in the upper percentiles, and the opening line is what tells the agent which part of
its training to draw on. It has been observed to matter: the briefs written this way came back
with mutation-checked tests, named the decisions the brief had not settled, and refused to
paper over an asymmetry they found.

**How:** one sentence, first line of the prompt, naming the role, and right after it the other
half of the bar, from the top of `CLAUDE.md` (maintainer, 2026-09-18): mistakes are fine, that is
why the review loop exists; hiding a mistake or brushing over one is what would hurt us. An agent
that has been told it is world-class must also be told that a reported weakness in its own work
is part of being world-class here, or the first line alone invites the arrogance the caveat
below warns about. Then the constraints and the task. The bar is also stated for the work itself where it helps ("byte-identical is the
acceptance", "only CRITICAL and MAJOR force a round, so be precise about severity"), so the agent
knows what excellent looks like here rather than in general.

**Status: a theory, not a proven rule.** Two caveats from the maintainer. It might tip an agent
into arrogance instead. And it might do nothing: every sub-agent already reads `CLAUDE.md`, whose
first lines state the same bar ("We write exceptional software"), and the harness may frame the
role in its own system prompt, which the coordinator cannot see; the line in the brief is then a
second or third statement of the same thing, and a null result in the table below is the likely
outcome. If the density does not move, the line goes and `CLAUDE.md`'s opening stays the one
place the bar is stated. Signs that it did, to watch for in every report: a finding dismissed without
a scenario, a mutation check skipped or reported as "obviously red", a claim of byte identity
without the hash, a deliberate engine exception "corrected", a brief's scope widened because the
agent knew better, or a report that argues with the reviewer instead of answering the finding.
Signs that it worked: decisions the brief did not settle are named and reasoned, weak tests are
called weak by their own author, an asymmetry is reported rather than papered over. Record what
you see in the changelog below with the date; after a few steps the maintainer decides whether
the line stays, changes, or goes.

**The numbers to watch** (maintainer's hypothesis: fewer review rounds and fewer CRITICAL/MAJOR
findings per step). The measure is **defect density**: CRITICAL plus MAJOR findings across all
rounds, the same defect found by two reviewers counted once, per 1000 changed PRODUCTION lines
(insertions plus deletions in the step's commits, excluding test sources, `docs/`, `*.md` and
`.claude/`; `git show --numstat <commit>` and sum). Rounds to clean beside it, test lines as
context. One row per step, filled by the coordinator when the step commits. Confounds to keep in
mind when reading it: step size, and the review process itself maturing over the same period.

| step (Katalyst work) | opening line | prod lines | test lines | rounds to clean | CRIT+MAJOR | per kLoC | note |
|---|---|---|---|---|---|---|---|
| 1, wire model | no | 1599 | 1439 | 3 | not counted | | classic defaults, the per-block poll allocating |
| 2, cylinder from classic | no | 932 | 613 | 2 | not counted | | |
| 3a, declared chains | no | 1073 | 1179 | 2 | not counted | | two-probe coercion |
| 3b, crossfade | yes | 1049 | 1162 | 3 | not counted | | unramped sends, duck across a swap, late takeover |
| 3c, tables to audio_bridge | yes | 790 | 99 | 1 | 0 | 0.0 | |
| 5a, orbit param state | yes | 1636 | 6561 | 2 | 2 | 1.2 | content-equal classic voice-driven; per-door map allocation |
| 4, eq and gain | yes | 692 | 1149 | 1 | 0 | 0.0 | clean on round 1 |
| 5a-2, replace and index slots | yes | 1594 combined with 5a-3 (one commit, shared files) | 1851 combined | 2 | 1 | 2.5 combined, 1.3 counting code only | the same MAJOR from both reviewers (classic wet default a set 0.0) |
| 5a-3, ParamBag and the fill | yes | see above | see above | 4 (ladder: high, high, xhigh, max) | 3 | see above | two of the three were the COORDINATOR's rule text (the sends' gate mis-stated, then the phaser missing from the corrected list); the third was inherited from step 5a (the duck filled on any knob) and found by a door-by-door table on the xhigh tier. None came from the implementer's code. Round 2 at high found what round 1 missed only in text; the table on xhigh found the code defect, so the ladder earned its cost once |
| NaN guard on the born-with body and vowel, plus the gain door | yes | 89 | 630 | 1 | 0 | 0.0 | clean on round 1; the implementer measured what the brief had guessed wrong (a nullable Double compares by IEEE rules on both targets) and reported that its own first version of a row was green under a mutation it should have caught |
| signal-flow 2a, first attempt (unconsumed rule, tree analysis) | yes | discarded, never committed | discarded | 3 rounds, never clean | 3 rounds of MAJORs in one mechanism | n/a | not an implementation failure: the DESIGN required code that predicts another walk's outcome, and it drifted three times (Variants, skipped arms, the work cap). Both top-tier reviewers recommended deleting the mechanism; the maintainer replaced the model. Counted here so the table does not flatter the theory by omission |
| signal-flow 2, levels on the wire (velocity folded at the wire, `postgain` retired, song migration) | yes | 442 (214 added, 228 removed), songs included | 661 (435 new) | 2 | 1 | 2.3 | the one MAJOR was a test row that compared the engine against itself; zero defects in production code and zero in a 60-line hand migration of song levels that two reviewers re-derived by table. The implementer's own one-off fixture caught its one migration error before review and the report said so unprompted. Two further round 1 "findings" were the coordinator's brief slip (a reviewer not told about the maintainer's uncommitted edits), not counted |
| signal-flow 2, the slot and the bus fader (`pregain`, the general leaf guard, classic's unity `gain.gain`) | yes | 545 (477 added, 68 removed) | 1447 (1071 new) | 3 (ladder: opus, high, xhigh) | 3 | 5.5 | none of the three was in the new production code, which two blind reviewers called clean in round 1. All three were AROUND it: the general guard silently emptied three pre-existing test rows (two found in round 1, the third in round 2 because its value sat in a loop variable that a grep cannot see; a tripwire at the guard then proved there was no fourth), and five doc sites kept stating an invariant the step had deliberately broken. The implementer reported, unprompted, that its own first engagement row stayed green under the mutation it was written for, and rebuilt it. Reading for the theory: the opening line did not prevent defects of reach (what else does this change make untrue?), which is a briefing problem, and it coincided again with candid self-reports |
| Katalyst 5b-1, the born-with chain becomes slot-driven (the voice-driven writers deleted) | yes | 891 (470 added, 421 removed) | 1683 changed | 2 (ladder: opus, high) | 2 | 2.2 | one was in the new production code and the coordinator had ACCEPTED it before review: gating the two sends on `wet > 0` let an orbit's owner silence another voice's room, against the door's own documented promise; the audio reviewer overruled the acceptance and HEAD's touched rule was carried over. The other was a guard outside the diff, made vacuous by the step's own shortcut. Before review, the coordinator sent the implementer back for value edges and a one-off whole-corpus render (18 songs, 256 cycles, bit-identical), which is what made round 1 about the contract and not about hunting differences. The implementer's self-reports again included its own errors unprompted (a toothless first row, a wrong first observable, a restore from git that lost an edit) |
| Katalyst 5c-1, the delay as a state machine (the template) | yes | 144 (executable lines of the effect, unchanged after round 1) | 700 (two specs, four new permanent rows) | 4 (ladder: opus, high, xhigh, max) | 5 | n/a | ZERO in the implementer's code: every CRITICAL-or-MAJOR of rounds 1 to 3 was either a missing permanent guard (1) or the COORDINATOR's plan text (4: the delay's lessons stated as general laws, then mechanisms for other effects written as settled without their files open). The code was clean in all four rounds and no executable line changed after round 1. Cost lesson: rounds 3 and 4 on the top tier were spent reviewing plan prose; a plan written by REMOVING claims would have been clean a round earlier |
| Knob glide pilot on the orbit reverb | yes | 222 added (303 before round 1 removed surplus) | 543 | 2 (opus, then ONE high reviewer for the removal batch) | 0 | 0.0 | clean in both rounds. The value came from the SURPLUS question added to the reviewer template that day: the audio reviewer MEASURED the problem and showed the damping glide was inaudible surplus; the step shrank by a quarter. The implementer's pilot log (12 lessons) is the main deliverable for the next five effects |
| Katalyst 5c-2, the orbit reverb as a state machine | yes | about 150 executable lines moved, none changed in behaviour | 2 new permanent rows plus an identity spec | 1 (opus, two reviewers) | 0 | 0.0 | clean on round 1: the SECOND conversion of a template that took four rounds took one. The template, the plan written by removing claims, and the implementer answering the plan's three questions in the effect's own terms carried it |
| Katalyst 5c-3, body and vowel on one resonator bank | yes | 97 filter lines replace 180 (net 85 fewer) | a new bank spec, two adapted | 1 (opus, two reviewers) | 0 | 0.0 | clean on round 1; the step made the code SMALLER and the implementer declined a host merge that would have added complexity. The audio reviewer's notes for the later vowel morph (per-sample gain ramp, removing the Q coupling, measuring the 32-sample staircase) were worth more than the review itself |
| Katalyst 5c-4, the filter swap as a state machine (first commit) | yes | 63 executable lines became 126 (the plan's shape; judged justified) | an identity spec, 4 new rows | 1 (opus, two reviewers) | 0 | 0.0 | clean on round 1. The step's most useful output was for the NEXT commit: the EQ's bank reuse and the asynchronous Off, which turned into an open maintainer decision (a queued swap against N banks) |
| Katalyst 5b-2, insert-style sends (a sound change) | yes | send plumbing 40 code lines fewer; delay tap crossfade and per-sample feedback ramp about 160 more | 2 new specs, many rewritten | 2 (opus, high; two reviewers each) | 1 | about 5 | one MAJOR, new in the step: on a chain swap the leaving room lost the delay's echoes in one sample (a click-class edge), found by reasoning in the blind audio round, measured at -67 dB and fixed to the -93 dB floor. The implementer's own measurements drove two build decisions (the tap crossfade, the per-sample feedback ramp) and one found an older defect (a deactivated orbit's stale mix residue). The survey of the step before had undercounted mixed-wet orbits |

Counts for steps 1 to 3b were not recorded per severity at the time; from here on the
coordinator records them in the step's commit message ("N review rounds, C critical, M major")
so the table can be rebuilt from `git log`.

## Notes

- This skill governs model/effort selection and fan-out safety only. Whether to fan out at all is governed by the
  Agent/Workflow tool rules (workflows require explicit user opt-in) and by the project rule *"Do not call the Agent
  tool unless the user requested it."*
- Quality is the goal; cheap tiers are a means to afford more coverage, not an end. A wrong answer from Haiku is more
  expensive than a right answer from Opus.

## Changelog

- **2026-09-18**, first observations on the opening line (steps 5a-2, 5a-3): no arrogance signal
  in any of nine agent reports; both implementers named unsettled decisions, called their own
  weak tests weak, and reported "no red mutation available" twice rather than claim one. The
  reviewers stated the weakness of their own reasoning when asked to. Density did not drop
  (2.5 per kLoC against 1.2 and 0.0 before), but three of the four MAJORs were text or inherited,
  so the number says nothing about the line yet.
- **2026-09-18**: "Every brief opens with the bar": each agent is told it is world-class at its
  role, with the maintainer's reason (the training distribution is average; we work in its upper
  percentiles).

- **2026-07-17** *(upstream `ultra`)* — Initial version. Opus↔coding and Sonnet↔retrieval mapping set by the user.
  Haiku/inherit tiers, effort table, escalation rule, and cheap-finders-expensive- verifier pattern proposed by Claude;
  not yet validated in practice.
- **2026-07-17** *(upstream)* — First validation run (SaaS-foundation deep scan): 3 Sonnet scouts + 1 Opus deep-diver +
  coordinator synthesis. Mapping held: Sonnet inventories were sufficient; the Opus deep-dive earned its tier. No
  adjustments needed.
- **2026-07-20** *(upstream)* — Fan-out safety rules added. Trigger: a task-file deepening run launched 6 workers at
  once, one of them (`inherit`-tier) spawned its own sub-fleet; 4 of the ~8 total agents stalled/disconnected and wrote
  nothing, including two orphaned grandchildren whose completed reports were discarded with their dead parent. Recovery
  (one synchronous retry with sub-fan-out forbidden) worked cleanly. **Caveat — not a proven failure mode:** the run
  also happened over a flaky network connection; the sample is one run. What did clearly hold: the tier mapping, and
  that worker sub-fan-out orphans results on failure regardless of root cause.
- **2026-08-04** — **Ported to Klang.** Added: the Gradle single-writer rule (concurrent builds corrupt the sprudel KSP
  cache — the coordinator owns the build by default; mutation-checking is inherently serial because it edits shared
  production files); DSP review pinned to `opus`/inherit;
  `Explore` as the preferred read-only worker type; the "give workers the constraints" rule; the list of Klang skills
  this governs (`/review-loop`, `/six-hats`).
- **2026-08-04** — **Lock added, at the user's instruction**, during the first Klang fleet run (audio-backend audit,
  `voices/` pilot: 5 Sonnet plan-writers + coordinator executing mutations). Advisory-only proved not enough for this
  workload: the worker instruction "do not run Gradle" is unverifiable from the coordinator, and the failure it prevents
  is *silent* — a raced build returns a plausible verdict rather than an error. So Klang gets a real `flock`
  (`console/with-build-lock.sh`, verified to refuse a concurrent holder with exit 75) in addition to ultra's advisory
  `BUILD-LOCK.md`. Also learned: wrapping only the build is insufficient for mutation work — the critical section is
  `mutate → build → restore`.
