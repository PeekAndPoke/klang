---
name: project-layout
description: The standard layout of a peekandpoke project, for root files (README, CLAUDE.md, DEV-DIARY, CREDITS, AUTHORS) and the docs/ tree (plans, tasks, in-progress, future, archive, maintainer questions). Use when creating, moving, archiving or looking for a task, plan, diary entry or credit, and when setting up a new project.
---

# Project layout

The same layout in every project, so an agent that knows one repo can find its way in any of them.
The reference projects are **spross** and **klang**. The master copy of this skill lives in the nexus
(`/opt/dev/peekandpoke/missioncontrol/blueprints/skills/project-layout/`). A repo copies it to
`.claude/skills/project-layout/` and records its deviations under "Repo notes" at the end.

**A starting point.** A project deviates from anything here when its purpose and its work call for it, and
says so in a line under "Repo notes". It doesn't need the nexus's OK.

## Root files

| File | What it holds |
|---|---|
| `README.MD` | What the project is, for people. Build and run instructions. |
| `CLAUDE.md` | Agent rules, short: a rules register (`rule \| since \| where it is detailed`) and pointers. Details live in skills and docs, not here. |
| `SEED.md` | *(optional)* The founding plan: purpose, architecture, module layout, roadmap. The tickets in `docs/tasks/` grow out of it. |
| `DEV-DIARY.MD` | The running diary, **newest first**. One `### (yyyy-mm-dd) Title` entry per session or milestone, a few bullets: what changed, what was learned, what is next. |
| `CREDITS.MD` | Every person, paper, standard, algorithm, dataset or tool the project builds on, added **in the change that brings it in**. The source is also named in the code's KDoc. |
| `AUTHORS.MD` + `LICENSE` | The copyright holders, and the license. The license header goes on every source file. |
| `CHANGELOG.md` | Only for published libraries (ultra): one section per release. |

There is no `TODO.MD` and no task list in the README. Open work is a task file.

## docs/

```
docs/
  tasks/                     one ticket file per piece of work: <PREFIX>-<nnnnn>-<title>.md
    _next-ticket.txt         the counter: "next ticket: XYZ-00042" (written only by the ticket tool)
    in-progress/             the tickets being worked on RIGHT NOW (+ README.md: the ticket-file shape)
    future/                  plans and ideas: loose ideas, or work still to break into tickets
    _maintainer-questions.md open questions for the maintainer, numbered (Q1, Q2 ...); not tickets
    _priorities.md           (optional) the order of the work streams
  tasks-archive/
    <yyyy-mm>/<PREFIX>-<nnnnn>-<title>.md   finished tickets, in the month they closed
  research/                  research notes, reading, measurements that inform tickets
  history/                   (optional) <yyyy>-Q<n>.md: quarterly summaries of the diary
  housekeeping/              (optional) dated inventories and audits of the repo itself,
                             and the ticket migration record (<yyyymmdd>-ticket-migration.md)
```

There is no `docs/plans/`: a plan is a ticket too (maintainer, 2026-10-20). A project adds its own
folders beside these (klang: `blog/`, `benchmarks/`, `funding/` ...). The folders above keep their names
and meaning everywhere.

### Tickets

Every task and plan has a ticket number: the project's prefix and five digits (`SPROSS-`, `KLANG-`,
`ULTRA-`, `MC-` for the nexus; examples here use `XYZ-`, a prefix no project has, so `check` never
mistakes an example for a reference). The number never changes, so a reference
names the number and a moved file breaks nothing.

- **The `ticket` tool does every step** (`console/tools/ticket/`, see `/console-tools`): `new` takes
  the next number (safe with several sessions at once), `move` sets the status and puts the file in its
  folder, `find` prints where a ticket is (also by an old file name or code), `check` runs in the
  pre-commit and on every review.
- **The folder follows the status word:**

  | Folder | Status words |
  |---|---|
  | `docs/tasks/future/` | IDEA, NOT SCHEDULED |
  | `docs/tasks/` | DESIGNED, SCHEDULED, NOT STARTED, PAUSED |
  | `docs/tasks/in-progress/` | IN PROGRESS (and PAUSED while the whole project pauses, with `in-progress/NEXT-STEPS.md`) |
  | `docs/tasks-archive/<yyyy-mm>/` | DONE, WON'T IMPLEMENT |

- **References name the number, with a short title on first mention**: "XYZ-00042 (engine
  follow-ups)". A part of a ticket is "XYZ-00042 item 30" or "XYZ-00042 step 7". Never a path or a
  file name: `check` refuses those. Tools that store a path (e.g. `.claude/reviews/*.json`) may.
- **Commits name the ticket** they work on: `XYZ-00042: ...`. Then `git log --grep XYZ-00042` shows
  the whole history of a ticket.
- Maintainer questions (Q1, Q2 ...) are not tickets. They keep their own numbering.
- Old codes and names ("task 04i", `20261017-02-sun.md`) stay findable after a migration: each file's
  History says "numbered XYZ-00012 (was `<old path>`)", and `ticket find 04i` resolves it. Dated
  history (the diary, review records, ledgers) keeps its words.

### Task lifecycle

1. A new ticket: `ticket new "Title"` (an IDEA in `future/`) or `--status DESIGNED` (in `docs/tasks/`).
2. When work starts: `ticket move <ID> IN PROGRESS`. A paused ticket: `ticket move <ID> PAUSED`.
3. When it is done: `ticket move <ID> DONE` (or `WON'T IMPLEMENT`, a first-class outcome, with the
   reason in its History). It goes to `docs/tasks-archive/<yyyy-mm>/`.

One look at `in-progress/` answers "what is being worked on". The status line in each file says how far it is.

### Task file shape

0. **Title line**: `# <ID>: <title>`.
1. **Status line**: `> **Status (yyyy-mm-dd): <WORD>.**` plus one sentence on where it stands and
   what blocks it. `ticket move` writes it. The words:
   - before work starts: IDEA (not designed yet), DESIGNED (ready to schedule), SCHEDULED, NOT SCHEDULED,
     NOT STARTED
   - during: IN PROGRESS, PAUSED
   - at the end: DONE, or WON'T IMPLEMENT (a first-class outcome: the task is archived like a
     done one, with the reason in its History)
2. **Why this exists**: the goal in two or three sentences, plus the ticket or SEED section it serves.
3. **Depends on / unblocks**: the tickets before and after it, by number.
4. **Decide before implementing**: each open design question with a recommendation. An open
   question goes to `_maintainer-questions.md`. Never build on an unanswered question in silence.
5. **The work**: numbered steps, each small enough to review and commit on its own, each naming the
   files it touches and the tests it adds.
6. **Acceptance**: what must be true when it is done, as checks someone can run.
7. **Out of scope**: what is left for a later task.
8. **History**: one dated line per commit or decision, newest last.

A project may add sections, such as ultra's review ledger. It doesn't drop any of these.

### Maintainer questions

`docs/tasks/_maintainer-questions.md` collects questions in one place, numbered, so the maintainer can
answer "Q3: yes". An answered question moves into the task it belongs to, with the date and the
answer.

## Memory lives in the repo

- `<module>/MEMORY.md` says what is true in that module **now**, and stays short. A change updates
  its section in place and adds one History line. The narrative goes in the task record.
- Durable facts go in the repo: module MEMORY.md, task files, skills, CLAUDE.md. Not in the
  home-directory auto-memory. If a fact has no home, ask where it belongs.

## console/ and .claude/

- `console/`: scripts people and agents run. `console/tools/<tool>/` holds the shared mechanical
  tools (see `/console-tools`). The build lock wrapper (e.g. `console/with-build-lock.sh`) lives here too.
- `.claude/` holds **agent configuration and agent machinery**: `skills/`, `agents/`,
  `settings.local.json`, the build lock and its log, records written by tools (e.g. review records
  in `.claude/reviews/`). No tasks, plans or docs for people. No worktrees (below).

## Worktrees

**Each project decides how it uses worktrees** (maintainer, 2026-10-20) and writes its way down in Repo
notes, for example a measurement tree at a fixed commit for long simulations. The starting point:

- Work goes sequentially on the repo's own checkout. That is the one IntelliJ has open, so its index sees
  the work. The maintainer prefers "a steady sequential workload rather than spikes".
- A worktree lives in the sibling directory `<repo>-worktrees/<name>`, never inside the repo: inside, it
  puts a second copy of every class into the IDE's index. For the same reason, don't spawn sub-agents with
  `isolation: "worktree"` (Claude Code puts those under `.claude/worktrees/`).
- A finished worktree is removed together with its merged branch.

## Commits

Every commit an agent wrote or co-wrote carries a `Co-Authored-By:` trailer naming the model. A commit
the maintainer wrote alone carries none.

## Repo notes

<!-- Each repo records here where it deviates from this layout, and why. -->
klang's copy, taken 2026-10-20 (the blueprint as of that day).

- **Root:** no `SEED.md` (klang grew before the layout) and no `CHANGELOG.md` (an app, not a library).
  `CREDITS.MD` has a twin, the in-app Credits page (`src/jsMain/kotlin/pages/CreditsPage.kt`), kept in step
  (rules register). `tones/` stays MIT and never gets the AGPL header.
- **docs/, klang's own folders:** `audio-audit/`, `benchmarks/` (dated full-check and timing results, the
  allocation scoreboard), `blog/` (published posts; a copy ships in `src/jsMain/resources/blog/`), `funding/`,
  `history/`, `knowhow/`, `media/`, `notebooks/`, `strategy/`, `whitepaper/`, plus `docs/retired-names.md`.
  No `research/`: research notes live in the tickets and in `strategy/`.
- **docs/tasks/, klang's own files:** `_priorities.md` and `_v1-scope.md` (the order of the streams and the
  V1 list); `by-ear/` (listening notes, a README only, not a ticket folder); the checklist in `in-progress/`
  is a `skip:` line in the counter.
- **Tickets since 2026-10-20:** KLANG-00001 to KLANG-00320 numbered by date in one bulk migration (record:
  `docs/housekeeping/20261020-ticket-migration.md`). Older tickets keep their own shape; the status line
  was added at the migration (archived: DONE, or WON'T IMPLEMENT for three, dated by the old file name;
  the open ones dated 2026-10-20 with the word read from their own status text). New tickets follow the
  shape above.
- **Dated records keep their words** and were not swept: `DEV-DIARY.MD`, the escape and defect-density
  ledgers, `<module>/ref/memory-history.md`, `docs/benchmarks/`, `docs/funding/evidence/`, and the
  published pages (`docs/blog/`, `src/jsMain/resources/`), which may still name old task paths.
- **Memory:** each module's `MEMORY.md` says what is true now; its full dated record lives in
  `<module>/ref/memory-history.md` (since 2026-09-29).
- **Worktrees:** work goes sequentially on the main checkout. A full check or a long measurement may run in
  `/opt/dev/peekandpoke/klang-worktrees/measure`, detached at the commit being measured, so the maintainer
  can keep editing on the checkout: no commits and no edits there, it shares the main checkout's build lock
  (one build at a time), the coordinator copies its results to main, and it is removed after the run.

## Proposed upstream

<!-- Findings this repo sent back to Mission Control, not merged into the blueprint yet:
     - yyyy-mm-dd: <what, one line> (<where it happened>)
     Remove an item when an update arrives that contains it. -->

## Changes

<!-- Kept by the nexus, newest first. Tells a repo what an update brought. -->
- 2026-10-20: every blueprint is a starting point; a project deviates where its work needs it (maintainer). Worktrees: each project decides.
- 2026-10-20: ticket numbers: every task and plan is `<PREFIX>-<nnnnn>-<title>.md`, kept in its status's folder by the `ticket` tool; references name the number; no `docs/plans/` (maintainer).
- 2026-10-20: worktrees: none by default; when used, in the sibling `<repo>-worktrees/`, never `.claude/worktrees/` (maintainer).
- 2026-10-19: more status words (IDEA, DESIGNED, SCHEDULED, NOT SCHEDULED, WON'T IMPLEMENT); `.claude/` may hold tool-written records such as `.claude/reviews/` (both from spross).
- 2026-10-19: first version, spross and klang as the reference.
