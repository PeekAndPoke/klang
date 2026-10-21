---
name: console-tools
description: Mechanical tools in console/tools/ that replace judgment-by-inference for test runs, mutation checks, review rounds, landing a batch and docs hygiene. Use after any Gradle test run (test-digest), for every mutation check of /review-loop Standard 2 (mutate), when preparing a review round's diff (review-pack), when landing several reviewed branches together (stack-merge), before committing docs, task files, skills or MEMORY.md (docs-lint), and when adding or changing a tool there.
---

# console/tools

Tools that do mechanically what an agent would otherwise do by reading, judging or assembling by
hand. Prefer the tool over a throwaway snippet: the snippet is where today's mistakes came from.
The judgment stays with you: which spec must run, which mutant is meaningful, what the right reason
for a red is, which conflict is real.

## Convention

- Every tool lives in `console/tools/<tool>/`. The path is the same in every peekandpoke repo.
- A repo **copies** a tool and owns its copy. Nothing imports from another repo or from the shared
  drafts in `/opt/dev/peekandpoke/agent-tools/`. Adapting a copy is expected (the test command,
  local rules); keep the generic part recognisable so copies can be compared, and record every
  change in "Repo notes" below.
- A new tool starts as a proposal to the maintainer. Build it only once it's approved. If it's
  useful beyond this repo, offer it back as a draft in `/opt/dev/peekandpoke/agent-tools/`.
- Before trusting a newly copied or changed tool, do one real run in this repo's build and check its
  verdict by hand once.
- Standard-library Python or shell only. No dependencies. Run them with `python3`.

## klang: the lock, long runs

Gradle is a single-writer resource (`/agent-fleet`): every tool call that runs Gradle goes through
`console/with-build-lock.sh`, ONCE, around the whole call.

```bash
# one form everywhere; $LOCK below means this prefix, written out in the same command (shell state does not carry
# over between calls)
LOCK="env KLANG_LOCK_TIMEOUT=7200 console/with-build-lock.sh"
```

- The script finds the main checkout's lock through git's common dir (since 2026-10-19), so the same
  prefix works in the main checkout and in a worktree (`/project-layout`: work runs on the main checkout;
  a measurement worktree in `klang-worktrees/` shares the lock). Waiting for the lock is normal; others
  hold it for long stretches.
- A locked call can outlast the Bash tool's 10-minute foreground limit: run it in the background and
  wait for the notification. The harness may kill a background chain under memory pressure; for
  mutate, that is what `mutate.py restore` is for.
- Never rely on the shell's directory: pass `--root <tree>` (or `cd` in the same command); the klang
  copies run the test command in the root they scan.
- **Kotlin/JS browser runs could cross between projects and modules** (2026-10-19): both projects'
  Karma servers took the same port and one ChromeHeadless reported into the other's run. Fixed the same
  day with a fixed Karma port per module (`<module>/karma.config.d/karma-port.js`, klang 10031 to 10046).
  Keep passing `--own-package io.peekandpoke.klang` wherever test-digest reads JS results, as a safety
  net: a fresh suite outside it fails the digest (exit 2; the `Kotest` executor suite has no package and
  is not judged).
- docs-lint never builds and needs no lock. review-pack does not build either, but it snapshots the
  working tree: where anyone may run a mutation in that tree, take the snapshot inside a lock call
  (`/review-loop`, "Snapshot the working tree only inside a lock call").

## When to use which

| Situation | Tool |
|---|---|
| After any Gradle test run, before writing "green" or a test count anywhere | `test-digest` with `--expect` naming the specs that must have run |
| A new or changed test that `/review-loop` requires mutation-checked, or a reviewer testing a doubt by a mutant | `mutate` |
| Before committing docs, task files, skills or `MEMORY.md`; after moving a task file | `docs-lint` (plus `docs_lint_local.py`) |
| Preparing a review round's inputs (every round) | `review-pack` |
| Landing a batch of reviewed branches together | `stack-merge` |
| Creating, starting, pausing or finishing a task or plan; finding one; before committing docs | `ticket` |

## test-digest

```bash
$LOCK python3 console/tools/test-digest/test_digest.py run --root <tree> --own-package io.peekandpoke.klang \
    --expect NumberExtensionsSpec,ClassicTailSpec -- ./gradlew :audio_bridge:jvmTest --rerun
python3 console/tools/test-digest/test_digest.py digest --root <tree> --since 2026-10-19T17:02:11Z \
    --log build.log --own-package io.peekandpoke.klang --expect FooSpec    # reads only, no lock
```

- **RAN** means the spec's XML `<testsuite timestamp>` lies inside this run, its file was written in
  this run (a test task that dies before rewriting its XML leaves the last run's file), and the log
  shows its task ran. A task Gradle skipped as UP-TO-DATE keeps the old XML and reads as **STALE** (klang does not
  enable the build cache, so FROM-CACHE does not occur). **MISSING** means no result at all: the wrong task, or a
  spec of the other platform.
- Give the log (`run` keeps it; for `digest`, pass `--log`). Back-to-back runs start within a second
  of the previous run's last suites, inside the timestamp tolerance. `--since` needs `Z`, an offset or
  epoch seconds (a bare local time is refused: this machine runs on CEST).
- `--expect` is how you prove the spec you care about ran. A `--tests` filter proves nothing: on the
  JVM a filter that matches nothing fails with "No tests found", and a JS task ignores it.
- Failing **case names come from the console**; the XML can attribute a failure to the wrong case.
  Counts and the failure messages come from the XML (klang's console prints the exception class only).
- No need to clear old XML before a run (the timestamps and the log decide). If you clear anyway:
  `find <module>/build/test-results/<task> -name '*.xml' -delete`, never `rm -rf`.
- Exit codes: 0 green, 1 failures, 2 expected spec missing or stale (or a foreign suite), 3 compile
  error, 4 other build failure, 5 nothing ran (no fresh result at all: an UP-TO-DATE task, a filter
  that matched nothing). Only 0 is green.

### klang's test tasks

| Task | Runs | XML |
|---|---|---|
| `:<module>:jvmTest` | kotest on the JVM (`commonTest` + `jvmTest`) | `<module>/build/test-results/jvmTest/` |
| `:<module>:jsTest` | an aggregate that runs `:<module>:jsBrowserTest` (ChromeHeadless through Karma) | `<module>/build/test-results/jsBrowserTest/` |
| `:jvmTest` | the root project's own specs (`src/jvmTest`) | `build/test-results/jvmTest/` |
| `:klangscript-ksp:test` | the KSP processor's specs (a plain JVM module); not part of `allTests` | `klangscript-ksp/build/test-results/test/` |

- `--rerun` goes on the task that runs the tests: `:m:jvmTest --rerun`, `:m:jsBrowserTest --rerun`.
  `:m:jsTest --rerun` reruns only the aggregate and leaves `jsBrowserTest` UP-TO-DATE (seen
  2026-10-19; test-digest reads it as STALE). Never `--rerun-tasks` (it reruns every task, KSP included).
- `--tests <Fqcn>`: JVM only, unquoted, no wildcards, one per run. `:m:jvmTest :m:jsTest --tests X` in
  one invocation fails; run the JS task unfiltered, on its own.
- A JS task's XML holds one extra suite, `Kotest`, with one "Executor[js, browser, ...]" case (and the
  printed output of every spec), so its count is one above the number of tests. JS suites all carry
  the same timestamp, the moment the results were written.
- `:audio_be:jvmTest` leaves out the `ClickHunt` tag unless `-Pkotest.tags=ClickHunt` is given.
- `audio-wire-codec-ksp` has no test sources.

## mutate

```bash
$LOCK python3 console/tools/mutate/mutate.py run mutants.json --root <tree> --logs <your scratch folder>
python3 console/tools/mutate/mutate.py restore <backup dir>    # only after a hard kill; the path is printed at start
```

```json
{
  "test_command": ["./gradlew", ":audio_bridge:jvmTest", "--rerun"],
  "mutants": [
    {"id": "kat-minus-dropped", "file": "audio_bridge/src/commonMain/kotlin/KatalystKnobDsl.kt",
     "old": "KatalystKnobDsl.Constant(-value)", "new": "KatalystKnobDsl.Constant(value)",
     "expect_spec": "NumberExtensionsSpec", "expect_message": "expected:<-1\\.0> but was:<1\\.0>"}
  ]
}
```

It is Standard 2's hand protocol as one command: the baseline is GREEN, each mutant is MUTATE and
RED, the hash-verified byte restore plus the final run is RESTORE, the result table is the REPORT.
What stays yours: the mutant that breaks the behaviour the test claims, and an `expect_message` that
only the right failure matches.

- Hold the build lock around the **whole campaign** (mutate → build → restore), never inside
  `test_command`: a nested lock waits for itself.
- Put `--rerun` on the test task in `test_command`. Without it an UP-TO-DATE task blocks the
  baseline. Don't use `--rerun-tasks`.
- A big module: add one `"--tests", "<Fqcn>"` pair to `test_command` (JVM only), or give a mutant its
  own `test_command`. A JS task always runs whole; slow, but correct.
- Always give `expect_message`, a regex matched against the console and the XML failure text. In klang
  only the XML carries the assertion message (the console prints `AssertionFailedError at File.kt:42`),
  so write it against kotest's text there. It is a regex, so escape it: for the message
  `value: expected:<-1.0> but was:<1.0>` write `expected:<-1\\.0> but was:<1\\.0>` (in JSON; Python's
  `re.escape` gives the plain form). Make it specific to the failure you expect, not just a word that
  any failure contains. It is what
  separates **KILLED** (right reason) from **KILLED-OTHER**. **KILLED-BY-COMPILE** and **NOT-RUN** prove
  nothing; fix the mutant or the command.
- `timeout_seconds` (spec-wide or per mutant, default 1800): a test run that hangs is stopped (Ctrl-C
  to Gradle, which cancels the build in the daemon, then SIGTERM, then SIGKILL) and reads **TIMED-OUT**,
  inconclusive: a mutant that makes a test loop forever.
- A **SURVIVED** mutant isn't automatically a missing test. The fix may be wrong, or the fixture may be
  unable to express the difference. Work out which before adding an assertion.
- The backup is a byte copy in the printed `mutate-<tree>-*` folder under `$TMPDIR` (removed after a
  verified restore), never git. A worker that takes over from a stopped one runs `mutate.py restore
  <that folder>` (it checks every hash) before it reads the code as the predecessor's work.
- Paste the result table into the report or the ledger: each row is a `mutation-checked:` line.

## docs-lint

```bash
python3 console/tools/docs-lint/docs_lint.py                     # whole repo: docs, and task paths in code KDoc
python3 console/tools/docs-lint/docs_lint.py docs .claude [--check-anchors]
python3 console/tools/docs-lint/docs_lint.py --forbid-dashes <new or rewritten .md files>
```

- It checks that relative links resolve, outside code, in every file git does not ignore (`tmp/` and
  the build dirs stay out). Repo rules go in `console/tools/docs-lint/docs_lint_local.py` as
  `checks(root, files)`.
- klang's rule there: **stale-path**, a `docs/tasks/...md`, `tasks-archive/...` or `docs/plans/...` path
  written as text (often in backticks, which the link check skips) that does not resolve. A whole-repo
  run (no paths, or `.`) also reads every `*.kt` and `*.kts` file. Since the ticket numbers a reference
  names the number, so a hit is an old path left behind. Dated history and published pages
  (`docs/tasks-archive/`, `docs/history/`, files named by a date, `ref/memory-history.md`,
  `DEV-DIARY.MD`, the ledgers, `docs/benchmarks/`, `docs/blog/`, `docs/funding/evidence/`,
  `src/jsMain/resources/`) keep their words and are skipped by stale-path; the generic link check still
  reads them. Whether a task file sits in the right folder is `ticket check`'s job.
- **Dashes** (`/code-style` §22): `--forbid-dashes` reports every line with an em or en dash in the
  files given, old lines included. Use it on files you create or rewrite; for edits use
  `review-pack --dash-scan` (added lines only, every file type, KDoc included). A whole-tree dash run
  counts the debt, it is not a to-do list (2026-10-19: about 6,100 lines in 302 files of `docs` and
  `.claude`, 3,300 of them in dated history).
- A whole-repo run is clean (0 problems, 2026-10-20), so every finding is new: fix it in the same change.
- A resolving link says nothing about whether the text is right. This is hygiene, not review.

## review-pack

```bash
python3 console/tools/review-pack/review_pack.py --worktree <tree> --dash-scan --out <scratch>/round-<n>
python3 console/tools/review-pack/review_pack.py --worktree <tree> list | clean
```

- Brief reviewers with `full.diff` (round 1) or `delta.diff` (later rounds, the fix delta that
  `/review-loop`'s two-phase rounds mark as the primary target), and give the stats line's file counts
  in the brief. If the counts look wrong, check the base before sending reviewers off.
- `full.diff` is the merge base to the working tree as it is: committed, uncommitted and untracked
  files. In the shared main checkout that includes OTHER sessions' uncommitted work (the 2026-09-19
  escape in `/review-loop`'s ledger): read `files.txt` against your own paths, or review from a worktree.
- A "merge base moved" warning means the delta includes upstream changes. Re-run round 1 rather than
  reviewing a mixed delta.
- The round refs live under `refs/review-pack/<branch>/` in the shared repository, visible from every
  worktree. `clean` after the change lands.

## stack-merge

```bash
# 1. stack, no tests yet (the stack worktree needs the sample cache first, see below)
python3 console/tools/stack-merge/stack_merge.py --branches wt/a,wt/b --name <name>
# 2. give the stack the main checkout's sample cache
ln -s /opt/dev/peekandpoke/klang/cache /opt/dev/peekandpoke/klang-worktrees/stack-<name>/cache
# 3. the full check in the stack worktree, read by test-digest (full-check.sh takes the lock per step itself)
python3 console/tools/test-digest/test_digest.py run --root <stack worktree> --own-package io.peekandpoke.klang \
    --expect FooSpec,BarSpec -- env KLANG_LOCK_TIMEOUT=7200 console/full-check.sh
```

`--test "<command>"` (with `--own-package`, `--expect`) stacks and tests in one call, for a test command
that needs nothing added to the fresh worktree; klang's full check needs the cache, hence the three steps.

- The result is a new `stack/<name>` branch in its own worktree, `/opt/dev/peekandpoke/klang-worktrees/stack-<name>`
  (the sibling directory, `/project-layout`). It cherry-picks each branch's commits in the given order; the branches, main and every
  other worktree stay as they were. It never fast-forwards main and never pushes: the report prints
  the `merge --ff-only` command, and landing is the coordinator's call.
- It only takes COMMITS: a branch's uncommitted work stays behind. Check each source worktree's
  `git status` first. A commit whose change the stack already holds (same patch: a branch built on an
  earlier one) is skipped and printed as `same`; one that only matches after an insertion was resolved
  (its import already kept) is skipped and printed as `empty`.
- A branch holding a MERGE commit (`git merge main` into it) is refused before anything is created: a
  merge commit's own changes cannot be cherry-picked and would be lost. Rebase that branch first.
- It auto-resolves only pure insertions (base side empty, not even a blank line; both kept, earlier
  branch first) that share no line with a letter or digit in it; a shared `import` line is kept once.
  Any other shared line (two new tests both starting with `@Test`) stops: one addition or two is a
  person's call. It prints each resolution. Read every printed resolution, and fix every numbering-clash warning on the stack before
  landing. Two klang habits always need that hand fix:
  - a new item in KLANG-00290 (engine follow ups) is numbered after the last item, so two
    branches both add the same number (the tool warns and never renumbers);
  - the `## History` list of `audio/MEMORY.md` is newest first, so of two lines added at its top the
    later-landed branch's belongs above; the tool keeps branch order (earlier first). `sprudel/` and
    `klangscript/MEMORY.md` list oldest first, where branch order is already right.
- A STOPPED run means a real conflict, and the stack is INCOMPLETE: never land it. The report lists what
  is on the stack and what is not (the stopped branch's remaining commits, the branches not started)
  and two recoveries: remove the stack and run again without that branch, or resolve by hand and
  cherry-pick the listed rest in order, then test. Never widen what the tool auto-resolves to get past it.
- The land line runs where the base branch is checked out (the main checkout), and fails if main moved;
  when main is checked out nowhere it is an `update-ref` guarded by main's sha.
- The test step: `console/full-check.sh` takes the lock per step itself, so it is NOT wrapped in another
  lock call (a nested call waits for itself); from the stack worktree its steps take the main
  checkout's lock by themselves. Its Gradle output goes to `build/full-check/<stamp>/` in the stack worktree, not to the
  console, so test-digest counts from the XML and the failing case names are in that folder's
  `tests.log`. A red corpus or allocation step with green tests reads as exit 4.
- A fresh stack worktree has none of klang's git-ignored inputs. Most are built on the fly (the Strudel
  bundle, the worklet), but the corpus step reads samples from `./cache` and otherwise fetches every one
  from the network, or goes red offline ("no ./cache and no network?"). Hence step 2 above, between
  stacking and testing (the symlink is the likely way; confirm it on the first run).
- After landing, the source branches still look unmerged (the stack holds copies of their commits,
  with `-x` lines); check `git cherry main <branch>` before deleting one.

## ticket

```
python3 -I console/tools/ticket/ticket.py new "Engine follow-ups" [--status DESIGNED] [--part-of XYZ-00041]
python3 -I console/tools/ticket/ticket.py move XYZ-00042 IN PROGRESS [--note "Started step 1."]
python3 -I console/tools/ticket/ticket.py find XYZ-00042   # or an old name or code: find KLANG-00290 (engine follow ups)
python3 -I console/tools/ticket/ticket.py list [--status "IN PROGRESS"] [--epic XYZ-00041 [--deep]]
python3 -I console/tools/ticket/ticket.py epics [--json]   # every epic with its children's counts
python3 -I console/tools/ticket/ticket.py check            # exit 1 on a problem
```

- **Never number or move a ticket file by hand.** `new` takes the number under a lock shared by every
  worktree and skips any number already used on any branch; `move` writes the status line, a History
  line and the file's new place in one step.
- **Write the number, never the path**: "XYZ-00042 (engine follow-ups)" on first mention, then
  "XYZ-00042". `check` reports a path or a file name, a number with no ticket, a file in the wrong
  folder for its status, and a counter behind the highest number.
- **Epics** (optional): a ticket in a group carries `> **Part of:** XYZ-00041 (short title)` after a bare `>`
  under its status line; `new --part-of` writes it, `move` keeps it. `check` refuses a ticket that is its own
  parent, a loop, a parent with no ticket file, and a short title sharing no word with the epic's title.
  Counts: closed (DONE, WON'T IMPLEMENT), open, paused, ideas.
- **Migrating a repo** happens once; klang did it in bulk on 2026-10-20 (`migrate`, `sweep`, `check`).
  The record `docs/housekeeping/20261020-ticket-migration.md` stays: `find` reads it.
- Files that aren't tickets: README.md, NEXT-STEPS.md, TEMPLATE.md, every `_*.md`, plus `skip: <glob>`
  lines in `docs/tasks/_next-ticket.txt`.

## Repo notes

klang's copies, adopted 2026-10-19 from the drafts in `/opt/dev/peekandpoke/agent-tools/`, rebased the same
day on the drafts' second version (Mission Control: our first fixes ported back, plus mutate's
`timeout_seconds` and TIMED-OUT, its backup removed after a failed baseline, review-pack's hunk-counting
dash scan, stack-merge's exit code by exception type, test-digest's foreign-suite check).

- **test-digest**, on top of the draft: (1) `:m:jsTest` is not listed beside `:m:jsBrowserTest` (an
  aggregate, klang only). (2) XML counts as fresh only if its file was also written in this run: a test
  task that died before rewriting its XML left the previous run's file, under 2 s older than the start,
  and a mutant read the last mutant's failure as its own KILLED (or the baseline's green as SURVIVED).
  (3) Exit 5 when nothing ran; it was 0, "green". (4) `--since` without a zone is refused (it was read
  as UTC on a CEST machine). (5) A running command is registered, so a signal handler can stop its
  process group (`stop_running`, used by mutate); `run` installs that handler itself, so a SIGINT or
  SIGTERM stops the test command too (round 2, 2026-10-20). (2) to (5) are generic. The draft's foreign check skips a suite
  without a package (`Kotest`): checked read-only on a real klang `audio_be` result dir, 505 suites,
  none flagged.
- **mutate**: `--logs` is created if missing; on SIGINT or SIGTERM it restores the files, then stops the
  test command's process group before exiting (it kept running, outside the lock, after mutate left).
  Generic.
- **docs-lint**: anchors of `*.MD` files are checked too (klang's `README.MD`). `docs_lint_local.py` is
  klang's own: task-place and stale-path (a `...` placeholder is not a path).
- **review-pack**: the blueprint's copy at 5473c0b (2026-10-21), unchanged. klang's `skip.txt` beside it leaves
  the maintainer's song edits (`src/commonMain/kotlin/builtinsongs/`) out of every round's diffs.
- **stack-merge**, on top of the draft: (1) the stack worktree goes to
  `<main checkout>-worktrees/stack-<name>`, the sibling directory (klang only; it was
  `.claude/worktrees/` until 2026-10-20). (2) A shared line in a pure insertion
  stops, except `import` lines (kept once): diff3 conflicts show whole blocks without git's trimming of
  common lines, so a branch built on an earlier one put a second `register(A)` on the stack, exit 0. (3)
  A commit already on the stack by patch is skipped (`rev-list --cherry-pick`). (4) A branch with a merge
  commit is refused up front (the merge's own edit was dropped, exit 0). (5) A stop reports what is and
  is not on the stack, with recoveries that cannot land half a stack. (6) The land line targets the
  checkout of the base branch, or a guarded `update-ref` (it targeted the checkout the tool ran from,
  which would fast-forward that branch). (7) A base made of blank lines is not empty; an unreadable
  conflicted file (rename/rename) is a refusal, not a crash. (8) `--own-package` passed to test-digest;
  a test run with nothing fresh is not green. (9) A commit that a resolution leaves empty is skipped as
  `empty` (it stopped with a git error, round 2). (2) to (9) are generic. Every one has a before/after repro
  on synthetic repos; not yet run against klang itself.
- The lock: `console/with-build-lock.sh` finds the main checkout's lock from any worktree (since
  2026-10-19), so no call needs `KLANG_LOCK_FILE` any more.
- **ticket**: the blueprint's copy at 61ccec7 (2026-10-21, epics), unchanged; its 18 tests pass here
  (`python3 test_ticket.py` in the tool's folder). Epics are adopted gradually (project-layout, Repo notes). klang's bulk migration, with what
  it swept and what it left alone: `docs/housekeeping/20261020-ticket-migration.md` and its commit
  (3c9a191d); the checklist in `in-progress/` is a `skip:` line.
- **docs-lint**: both files list what git does not ignore (`git ls-files -co --exclude-standard`), with
  the walk as the fallback outside git (generic). klang's `docs_lint_local.py` has one rule left,
  stale-path, which skips the migration's "(was `old path`)" records and the files the ticket sweep left
  alone; its task-place rule went with the ticket numbers (`ticket check`).

## Proposed upstream

<!-- Findings this repo sent back to Mission Control, not merged into the blueprint yet:
     - yyyy-mm-dd: <what, one line> (<where it happened>)
     Remove an item when an update arrives that contains it. -->

## Changes

<!-- Kept by the nexus, newest first. Tells a repo what an update brought. -->
- 2026-10-20: new tool `ticket` (ticket numbers for tasks and plans; see project-layout).
- 2026-10-19: back-channel sections added; masters now live in the nexus (missioncontrol/blueprints/tools/).
