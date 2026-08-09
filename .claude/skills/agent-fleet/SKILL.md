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

**Klang note — DSP review is correctness-critical.** The audio-engineer reviewer in `/review-loop`
judges numerical stability, per-sample cost and click risk; run it at `opus` or inherit, never
`sonnet`. Same for anything touching `audio_be` hot paths.

## Effort tiers

| Stage                         | Effort                        |
|-------------------------------|-------------------------------|
| Mechanical / bulk stages      | `low`                         |
| Standard work                 | omit (inherit session effort) |
| Hardest verify / judge stages | `high` or `xhigh`             |

## Where the dials live

- **Agent tool**: set the `model` parameter per call. There is no per-call effort override — effort comes from the agent
  definition. `fork`-type agents always inherit the parent model; don't set
  `model` on them.
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
`.claude/BUILD-LOCK.md` is the advisory half (holder + handover note across sessions); read it as its own step, never
chained into the build with `&&`.

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
  decisions (raw Motör no-clamping, reverb's `ANTI_DENORMAL` exception, the linear SVF, documented HPF bias). A reviewer
  without that list files findings that would make the engine worse. Paste the relevant constraint list into the
  prompt — `/review-loop` has templates, and
  `docs/tasks/audio-backend-audit.md` §7 has the audio-backend list.

## Notes

- This skill governs model/effort selection and fan-out safety only. Whether to fan out at all is governed by the
  Agent/Workflow tool rules (workflows require explicit user opt-in) and by the project rule *"Do not call the Agent
  tool unless the user requested it."*
- Quality is the goal; cheap tiers are a means to afford more coverage, not an end. A wrong answer from Haiku is more
  expensive than a right answer from Opus.

## Changelog

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
