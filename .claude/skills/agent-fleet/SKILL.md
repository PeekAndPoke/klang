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
| Review rounds 2, 3+           | `high`, `xhigh` via the `reviewer-*` agent definitions (`/review-loop`, the effort ladder); never `max` (maintainer, 2026-09-28) |

## Where the dials live

- **Agent tool**: set the `model` parameter per call. There is no per-call effort override — effort comes from the agent
  definition. `fork`-type agents always inherit the parent model; don't set
  `model` on them. Klang pins effort through `.claude/agents/reviewer-high.md` and `reviewer-xhigh.md`
  (added 2026-09-18 for the review ladder; `reviewer-max.md` deleted 2026-09-28, the ladder stops at xhigh).
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
console/with-build-lock.sh python3 console/tools/mutate/mutate.py run mutants.json   # a whole mutation campaign
```

The script finds the main checkout's lock from any worktree through git's common dir (since 2026-10-19), so the
same call works everywhere (`/console-tools`).

**For a mutation check the critical section is `mutate → build → restore`, not just the build**: one `mutate` campaign.
The lock file itself records its holder (`pid`, `since`, `cmd`), and a waiter that times out prints it. What is
half-done lives in the tickets in `docs/tasks/in-progress/` (the advisory `BUILD-LOCK.md` was retired at the rules
retro, 2026-10-23; its old handover notes stay in `.claude/build-lock-log.md`, history only).

### Rules for the fan-out

- **Default: the coordinator owns the build.** Workers read, analyse and propose; the coordinator builds once,
  afterwards. This is the right shape for review and analysis fan-outs.
- **Say it in the worker prompt**: *"Do NOT run Gradle or any build command. Report what should be run; the coordinator
  runs it."* A worker that ignores this still cannot race — the lock is real — but it will block for up to 15 minutes
  and look like a stall.
- **Workers make no git writes, reviewers delete nothing** (say it in every brief; written down at the rules retro,
  2026-10-23): the coordinator stages and commits by path after the review, so nothing unreviewed lands, and a reviewer
  that deletes can take a worker's evidence with it.
- **Only ONE owner mutates production code, ever.** A lock around the build does not fix this: two workers mutating
  `audio_be` read *each other's* edits and both draw wrong verdicts. Mutation campaigns are inherently serial and belong
  to a single owner.
- If a worker genuinely must build, give exactly one worker that permission, and require the wrapper.
- **Reviewers may build** (maintainer, 2026-10-04: "reviewers can run gradle but they need to respect the build-lock"),
  so a doubt is settled by a run instead of a guess. Always through `console/with-build-lock.sh`, one unquoted
  `--tests` FQCN per run. A reviewer never edits production code to fix anything; to TEST a doubt by a mutation it
  runs it as one `mutate` campaign in ONE lock call (byte backup, hash-verified restore, never git) and reports
  the mutant and its verdict. If the safety check refuses that call, the
  reviewer names the mutant and the worker runs it (maintainer, 2026-10-16; `/review-loop`, "A refused mutant goes to
  the worker"). Round-1 pairs run in parallel and
  take the lock in turn: a reviewer that waits for the lock is not stalled.
- **Counting tests: `test-digest`, never `rm -rf`** (2026-10-10: the pitch step 4 and step 5 workers each ran
  `rm -rf <module>/build/test-results/<task>` to get a clean count, against their brief). test-digest counts only this
  run's XML, so nothing needs clearing; if you must, `find <dir> -name '*.xml' -delete`. Put this line in every brief
  that asks for test counts.
- **Cleanup names the worker's OWN folder, never the scratchpad root** (2026-10-10: the partials worker ran
  `find <scratchpad> -type f -delete` on the session scratchpad and emptied every earlier agent's harness, the
  review snapshots and the V8 cost bundles; nothing in the repo was lost). Give each worker its own subfolder in the
  brief (`<scratchpad>/<task>/`) and say: delete only inside it.
- **Deletes name literal paths, never a variable** (2026-10-10: the harness worker ran an `rm -f` through a variable
  path inside its own folder). A variable that is empty or wrong turns a delete into one on the wrong folder, and a
  literal path is what the reader of the command can check.

## Concurrency & fan-out safety

A failed agent writes nothing, and a worker's own sub-fleet dies with it; the rules below keep a fan-out
recoverable. How many agents run at once is not capped: the old cap of 2 to 3 heavy agents was retired at the
rules retro (2026-10-23, MC-00021), since the stalls it was written for were never shown to come from concurrency
and the build lock is the real guard.

- **Workers must not fan out.** A sub-agent that spawns its own sub-fleet compounds load invisibly and, if it dies,
  orphans its children — their finished results are discarded with the parent. When a worker's task might tempt it to
  delegate, tell it explicitly in the prompt: *"Do NOT spawn sub-agents. Work sequentially yourself; read files in small
  batches."* Keep fan-out one level deep: the coordinator fans out, workers do not.
- **On failure, retry the one agent** — synchronously, sub-fan-out forbidden — rather than relaunching the whole fleet.
  Check what already landed on disk first; partial work may survive.
- **No two workers edit the same file.** Klang fan-outs are usually review/analysis, so this rarely binds; when it
  would, either partition by file or use `isolation: "worktree"`.
- **Stage by explicit path, never by directory** (guardrail, 2026-10-02). The working tree is shared with the
  maintainer and other sessions: `git add -A docs/tasks` once swept another session's new task file and its uncommitted
  skill edits into a coordinator's commit. Name each file you changed; before committing, read `git diff --cached
  --stat` and check every line of it is yours. Never `git restore --staged .` to undo: it also clears other people's
  staging (an intent-to-add mark was lost that way); unstage your own paths one by one. Commit with the paths named
  (`git commit -- <paths>`), so a file someone else stages in the meantime stays out. And never undo with
  `git reset HEAD~1` while others commit: the same day, another session committed in the three seconds between a
  commit and its reset, and the reset took back THEIR commit (restored from the reflog). Fix a commit with a follow-up
  commit; look at `git log` first if anything must be rewound. Never `git stash` in the shared tree either: it takes back
  everyone's uncommitted work for as long as it is stashed (a writer did it for a second on 2026-10-04; nothing was
  lost). Compare against a commit with `git show <rev>:<path>` instead.
- **Never switch branches in the shared checkout** (guardrail, 2026-10-21). `git switch` rewrites the working tree to
  the other branch: the coordinator committed a ticket on a branch, switched back to main, and the next worker spent
  its first edits on files that had lost that ticket's code (a three-way merge saved them). In the main checkout,
  commit on the checked-out branch by explicit paths (`git add <paths>`, `git commit -- <paths>`); the check runs on
  that commit in `klang-worktrees/measure` (`/project-layout`, Repo notes), and a red check is fixed by a follow-up
  commit, never by rewinding.

## IntelliJ's tools: read-only, plus Optimize Imports (maintainer, 2026-10-20 and 2026-10-23) *(provisional)*

The `mcp__idea__*` tools query IntelliJ's index, with types and references resolved. The read-only ones are allowed:
symbol and structural search, `analyze_calls`, the PSI tree, `get_file_problems`, `run_inspection_kts` and the other
lookups. They answer "every caller", "every writer of this field" or "every case of this `when`" in one query, where
grep only comes close. Write and build actions stay off (`apply_patch`, `rename_refactoring`, `reformat_file`,
`apply_quick_fix`, `build_project`, `execute_run_configuration`, `execute_terminal_command`): a build there bypasses
the build lock, and an edit lands in the maintainer's open editor. When the same case keeps coming up where one would
help (a rename refactoring, say), bring it to the maintainer.

**One write action is allowed: Optimize Imports** (maintainer, 2026-10-23, relayed by Mission Control), through
`invoke_ide_action` with `actionId: "OptimizeImports"` and the files in `filePaths`. The safeguards: klang's absolute
`projectPath` on every call, checked with `get_repositories` first; afterwards `git diff` shows only import lines;
the main checkout only; only where the maintainer expects it. The permission allows every IDE action, so keeping to
this one is on us. Until the meeting of 2026-10-30 each use gets a row in KLANG-00329.

- **The index is the open checkout**, the maintainer's working copy with its unsaved edits, never a worktree. A table
  built from a query says which commit it reflects.
- **After a branch switch or a merge, wait for indexing.** A partial result does not say it is partial.
- **Blind spots:** songs and tutorials are KlangScript inside Kotlin strings, so who calls a door from a song still
  needs grep. KSP-generated code is only as fresh as the last build.
- **The coordinator runs the queries, a few per task**, and puts the results in the brief. A project-wide query stalls
  the IDE the maintainer is typing in, so workers don't run queries in parallel. For a reviewer the results are facts
  (callers, writers), not a verdict.

## Rules of thumb

- **A rename or doc-sweep brief says how to treat quotations** (2026-10-04, after a sweep rewrote a maintainer's
  quote): words inside quotation marks attributed to a person stay verbatim. To bring their names up to date, drop the
  quotation marks and paraphrase, keeping the date. Dated records (History lines, decisions, ledger rows) keep their
  words as well.
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
  `.claude/skills/review-loop/audio-constraints.md` has the audio-backend list.

## Notes

- This skill governs model/effort selection and fan-out safety only. Whether to fan out at all is governed by the
  Agent/Workflow tool rules (workflows require explicit user opt-in); the coordinator spawns workers and reviewers as
  its normal work (the old "do not call the Agent tool unless the user requested it" was retired at the rules retro,
  2026-10-23).
- Quality is the goal; cheap tiers are a means to afford more coverage, not an end. A wrong answer from Haiku is more
  expensive than a right answer from Opus.

## Changelog

- **2026-10-23**: IntelliJ's Optimize Imports allowed, the first write action (maintainer, relayed by Mission Control,
  after Ultra used it with success); every other write action stays off until the meeting of 2026-10-30.
- **2026-10-23**: rules retro (Mission Control's MC-00017), klang's cleanup: the advisory `BUILD-LOCK.md` retired (the
  lock file records its holder), the "do not call the Agent tool unless asked" note retired, "workers make no git
  writes, reviewers delete nothing" written down. Then MC-00021: the "world-class" opening line of every brief
  and the cap of 2 to 3 heavy agents retired, their sections deleted; the defect-density ledger that measured the
  line is closed (it showed no failure the line prevents).
- **2026-10-20**: IntelliJ's tools allowed, read-only (maintainer, in the coordinator's session, after Mission Control
  asked the projects). An earlier session instruction against them dated from when they were still buggy; write
  actions are decided one by one when a case for one comes up.
- **2026-09-20**: the defect-density table moved to `defect-density-ledger.md`, the sibling of
  this file (maintainer: reading the skill should not carry the whole record). Nothing about the
  measure changed; the skill keeps the policy, the ledger keeps the evidence. Observations on the
  opening line stay here, dated, and the numbers they cite live there.
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
