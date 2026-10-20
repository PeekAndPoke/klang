# Console tools: what the always-loaded text can lose

Proposal only (Phase B). None of the files below was changed; Phase A touched only `console/tools/`,
`.claude/skills/console-tools/` and three broken links in current docs.
Every edit below was applied to copies by `condense/apply_proposal.py` (same folder), which asserts each old text
occurs exactly once and counts lines and bytes; `condense/before/` and `condense/after/` hold the copies, so
`diff -r condense/before condense/after` shows every proposed change at once.

## The honest headline

The tools replace **mechanics**, and the mechanics were short. The judgment around them (red for the right reason,
prefer the code under test, the scope tiers, the escape stories) stays. So the line count barely moves: 20 lines fewer
over the 16 files read (bytes flat), and `CLAUDE.md` itself grows by 7 lines for the new block. What does change is that four
hand protocols that failed before (restore from git, a `No tests found` read as a kill, `rm -rf` to get a clean
count, a reviewer's diff assembled by hand) become one command each, and the text that described them shrinks to a
pointer. Fewer words would mean dropping a reason or a trap, which this proposal does not do.

## 1. The `CLAUDE.md` addition

**Where:** at the end of `## Agents and skills` (after line 115, the last line of the file), as its second paragraph.
Not in the rules register: the tools are not rules, they serve rules that are already rows there ("Reviews loop
until a clean round", "Gradle is a single-writer resource", "No em-dashes"). Those rows keep their text; only the
Gradle row's parenthetical names `mutate` and its Detail column gains `/console-tools` (same line count).

```markdown
**Console tools** in `console/tools/`, how and when in `/console-tools`. Use them instead of a hand snippet:
- `test-digest`: what really ran; a spec counts only with XML from this run, `--expect` names the specs that must run.
- `mutate`: Standard 2 in one lock call; hash-verified byte restore, and red counts only for the expected reason.
- `review-pack`: each round's full diff and fix delta, tree untouched; in a shared tree it holds others' edits too.
- `docs-lint`: broken links, a task file in the wrong folder, a moved task's path left in docs or KDoc, dashes.
- `stack-merge`: reviewed branches onto a new `stack/<name>` branch; only pure insertions auto-resolved, main untouched.
```

The trap each line guards: test-digest, a cached or filtered run that looks green (UP-TO-DATE reads STALE); mutate,
a restore from git instead of the backup, and a red for the wrong reason (KILLED-OTHER, `No tests found`);
review-pack, a hand-built delta and a diff that carries someone else's uncommitted work; docs-lint, a move that leaves
paths behind (26 such KDoc mentions in code today); stack-merge, a hand merge that "resolves" a real conflict, or a
batch landed by moving main before the stack was tested.

A shorter alternative (3 lines, if the maintainer prefers fewer): one sentence naming the five tools with a few words
each and the pointer. The one-line-per-tool form is easier to scan, and it is what the brief asked for.

## 2. Passage by passage

Verdicts: **MOVE** into `/console-tools` (the text is there already), **REPLACE** with a pointer to the tool,
**KEEP** with the reason. "Judgment" means no tool covers it.

### `CLAUDE.md` (always loaded)

| Passage | Verdict | Proposed | Reason |
|---|---|---|---|
| End of "Agents and skills" | ADD | the block above (+7 lines) | the one place every session reads |
| Rules row "Reviews loop until a clean round; every new test is mutation-checked" | KEEP | | a rule; the tool serves it |
| Rules row "Gradle is a single-writer resource ... (a doubt tested by a mutation stays inside one lock call and is restored)" | REPLACE the parenthetical | "(a doubt tested by a mutant runs as one `mutate` campaign)"; Detail `/agent-fleet`, `/console-tools` | the mechanics move to the tool, the rule stays |
| Rules row "No em-dashes in user-facing text" | KEEP | | a rule; the check is in `/code-style` §22 (below) |
| Everything else | KEEP | | no tool covers it |

### Module `CLAUDE.md` files (loaded when working in the module)

| File, passage | Verdict | Proposed | Reason |
|---|---|---|---|
| `klangscript/CLAUDE.md` "Build & Test" (a 4-command code block) | REPLACE | `:klangscript:jvmTest` (fast), `:klangscript:jsTest` (browser), `:klangscript-libs:jvmTest` (the stdlib), `:klangscript:compileKotlinJvm` (compile only); through the build lock, read with test-digest (`/console-tools`). | the commands stay as names; the block taught a bare `./gradlew` that the stone-hard lock rule forbids |
| `audio/CLAUDE.md` "Build & Test" | REPLACE | `:audio_bridge:jvmTest`, `:audio_be:jvmTest`, `:audio_fe:jvmTest`; through the build lock, read with test-digest | same |
| `sprudel/CLAUDE.md` "Build & Test" | REPLACE | `:sprudel:jvmTest` (preferred, fast; one class: `--tests LangBpmSpec`, no quotes), `:sprudel:jsTest` (browser-specific only); through the build lock, read with test-digest | same; the no-quotes fact stays |
| `klangscript-libs/CLAUDE.md` "Build & Test" | REPLACE | the three tasks in one sentence, plus the lock and test-digest | same |
| Everything else in the four files | KEEP | | architecture and domain, no tool covers it |

### Module `MEMORY.md` files

| File | Verdict | Reason |
|---|---|---|
| `klangscript/MEMORY.md`, `sprudel/MEMORY.md` | KEEP all | no test, lock, mutation or docs mechanics in them (grepped: `test-results`, `--rerun`, `cmp`, `mutat`, `lock`, `--tests`, `xml`) |
| `audio/MEMORY.md` | KEEP all | its one test command (line 340, the `ClickHunt` run) is domain knowledge, already mirrored in `/console-tools` |
| The `## History` lists | KEEP, note for stack-merge | `audio/MEMORY.md` lists newest first ("One line per step, newest first"); `sprudel/` and `klangscript/MEMORY.md` list oldest first (their first entries are 2026-02). Two branches adding a line each are a pure insertion that stack-merge keeps in branch order, earlier first: right for sprudel and klangscript, the wrong way round for audio. The `/console-tools` stack-merge section says so; nothing to change in the files |

### `.claude/skills/review-loop/SKILL.md`

| Passage | Verdict | Proposed | Reason |
|---|---|---|---|
| Standard 1, step 1 "Collect the change set ... (vs branch base, or the files just edited)" | REPLACE | "with `review-pack` (`/console-tools`): `full.diff`, from round 2 on also `delta.diff`" | the tool builds exactly round 1's change set and the fix delta of the two-phase rounds |
| Standard 2, Protocol steps 1 to 5 (12 lines) | REPLACE the mechanics, KEEP the judgment | "Run it as a `mutate` campaign: its baseline is GREEN, each mutant is MUTATE and RED, its hash-verified restore and final run are RESTORE, its table is the REPORT. What stays yours:" then MUTATE (one targeted mutation, prefer the code under test, the test's inputs as fallback), RED for the right reason (`expect_message`; SURVIVED means toothless; KILLED-OTHER, KILLED-BY-COMPILE, NOT-RUN prove nothing), REPORT (the `mutation-checked:` line), and the takeover line as `mutate.py restore <backup dir>` (2026-09-25 kept) | gone: "verify with `git diff`", "`cmp` against the runner's backups", "revert exactly; run again" (the tool does them, with hashes). Kept: every judgment and the dated lesson |
| Standard 2, Scope (two tiers), What deserves a test, Why this exists | KEEP | | judgment |
| Standard 3, table row "tooling", its example (a `git diff \| grep -c` for the dash character) | REPLACE the example | "a tool in `console/tools/` (`/console-tools`) or a pre-commit grep" | the dash grep is `review-pack --dash-scan` now |
| Gotcha "`:<module>:jsBrowserTest --tests X` runs the WHOLE JS suite ... TEST-Kotest.xml" | MOVE | (in `/console-tools`, "klang's test tasks") | a test-task fact; test-digest reads the XML |
| Gotcha "Reviewers may run Gradle, under the lock ... (one lock call around mutate, build, restore; `cp` and `cmp`)" | REPLACE the parenthetical | "a mutant runs as one `mutate` campaign in one lock call" | mechanics |
| Gotcha "A refused mutant goes to the worker ... (back up, mutate, run, restore with `cp`, check with `cmp`)" | REPLACE the parenthetical, KEEP the rule | "each as a `mutate` campaign in one lock call" | the refusal rule is judgment and stays word for word |
| Gotcha "Snapshot the working tree only inside a lock call" | KEEP, add "(`review-pack` too)" | | review-pack snapshots the working tree; a concurrent mutant would land in its diff (found while adopting) |
| Gotcha "Single spec ... every expect-red runner must grep the log for `No tests found` ... print the failing test names" | REPLACE the second half | "Its `No tests found` exit once read as three spurious RED verdicts (2026-08-20); `mutate` and `test-digest` read it as NOT-RUN or MISSING, never as a kill." | the syntax facts (unquoted FQCN, one per run) stay; the hand check is the tool's |
| Gotcha "A `--tests` filter does not apply to a JS test task ..." | MOVE | (in `/console-tools`) | a test-task fact |
| Gotchas on the TeamCity line, scripted renames, refused actions, two concurrent builds, the continuous watcher, whitespace, generated batches, memory pressure | KEEP | | none is mechanics a tool performs |
| Reviewer templates, the effort ladder, termination, rules, changelog | KEEP | | judgment and history |

### `.claude/skills/agent-fleet/SKILL.md`

| Passage | Verdict | Proposed | Reason |
|---|---|---|---|
| "The lock", example `bash -c 'apply-mutation && ./gradlew ... ; restore-mutation'` | REPLACE | `console/with-build-lock.sh python3 console/tools/mutate/mutate.py run mutants.json` | the hand campaign is the tool now |
| (new, after the example) | ADD 2 lines | "From a worktree, set `KLANG_LOCK_FILE=/opt/dev/peekandpoke/klang/.claude/build.lock`: the script's default is its own checkout's file, which serializes against nobody." | a trap no text in the repo names today (found while adopting); better still, fix it in the script (section 4) and drop these lines |
| "For a mutation check the critical section is mutate, build, restore ... wrap all three" | KEEP the rule, REPLACE "wrap all three" | ": one `mutate` campaign" | |
| "Reviewers may build ... (back up the file, mutate, build and run, restore with `cp`, verify with `cmp`, never with git)" | REPLACE the parenthetical | "runs it as one `mutate` campaign in ONE lock call (byte backup, hash-verified restore, never git)" | mechanics |
| "Counting tests: clear old results with `find`, never `rm -rf`" | REPLACE, KEEP the incident | "Counting tests: `test-digest`, never `rm -rf` (2026-10-10 ...). test-digest counts only this run's XML, so nothing needs clearing; if you must, `find <dir> -name '*.xml' -delete`. Put this line in every brief that asks for test counts." | the incident and the brief line stay |
| Cleanup and delete rules, fan-out safety, tiers, the opening line, changelog | KEEP | | judgment and history |

### Other skills

| File, passage | Verdict | Proposed | Reason |
|---|---|---|---|
| `code-style` §22 "No em-dashes" | KEEP, ADD 1 line | "Check: `review-pack --dash-scan` (added lines, any file), `docs-lint --forbid-dashes` (new docs); `/console-tools`." | §22 describes no hand grep, so nothing to remove; the line says how to check |
| `sprudel-dev-knowhow`, `klangaudio-knowhow`, `klangscript-knowhow`: "Testing" code blocks | REPLACE | one sentence each, as for the module `CLAUDE.md` files, `sprudel-dev-knowhow` pointing on to `sprudel/ref/testing.md` | the same commands a third time, without the lock |
| `public-voice` line 97, the hype-word grep on `git diff -U0 main` | KEEP | | no tool covers it yet (see section 4) |
| `review-loop/escape-ledger.md` | KEEP all | | history; its tooling rows (2026-09-17 dashes, 2026-09-19 restore from git, 2026-09-25 stopped mid-run) are the evidence the tools answer |
| `klangscript/ref/testing-strategy.md`, `sprudel/ref/testing.md` | KEEP | | not always loaded; they could get the same one-line lock note when next touched |

## 3. Before and after

Lines and bytes, measured on copies with every edit above applied (`condense/apply_proposal.py`).

| File | Lines before | Lines after | Change | Bytes before | Bytes after |
|---|---:|---:|---:|---:|---:|
| `CLAUDE.md` | 115 | 122 | +7 | 18,122 | 18,817 |
| `klangscript/CLAUDE.md` | 57 | 53 | -4 | 3,273 | 3,229 |
| `audio/CLAUDE.md` | 92 | 89 | -3 | 7,646 | 7,679 |
| `sprudel/CLAUDE.md` | 41 | 38 | -3 | 3,936 | 3,886 |
| `klangscript-libs/CLAUDE.md` | 68 | 65 | -3 | 8,126 | 8,097 |
| `.claude/skills/review-loop/SKILL.md` | 378 | 369 | -9 | 29,713 | 28,920 |
| `.claude/skills/agent-fleet/SKILL.md` | 275 | 278 | +3 | 21,576 | 21,709 |
| `.claude/skills/code-style/SKILL.md` | 194 | 195 | +1 | 10,016 | 10,132 |
| `.claude/skills/sprudel-dev-knowhow/SKILL.md` | 42 | 39 | -3 | 2,572 | 2,541 |
| `.claude/skills/klangaudio-knowhow/SKILL.md` | 67 | 64 | -3 | 5,200 | 5,187 |
| `.claude/skills/klangscript-knowhow/SKILL.md` | 76 | 73 | -3 | 4,912 | 4,891 |
| `klangscript/MEMORY.md` | 175 | 175 | +0 | 15,664 | 15,664 |
| `sprudel/MEMORY.md` | 237 | 237 | +0 | 20,969 | 20,969 |
| `audio/MEMORY.md` | 775 | 775 | +0 | 83,774 | 83,774 |
| `.claude/skills/public-voice/SKILL.md` | 116 | 116 | +0 | 10,340 | 10,340 |
| `.claude/skills/review-loop/escape-ledger.md` | 93 | 93 | +0 | 47,603 | 47,603 |
| **total** | 2801 | 2781 | -20 | 293,442 | 293,438 |

The new skill `.claude/skills/console-tools/SKILL.md` is loaded on demand only (its description triggers it after a
Gradle test run, for a mutation check, a review round, or a docs commit).

## 4. Proposed next, not built

- **The lock script finds the main checkout itself.** `console/with-build-lock.sh` could default its lock to
  `$(git rev-parse --path-format=absolute --git-common-dir)/../.claude/build.lock`, so every worktree (a stack's
  included, and `console/full-check.sh` run inside one) shares the main lock without `KLANG_LOCK_FILE`. Then the two
  agent-fleet lines in section 2 and the prefix in the stack-merge command are not needed. One line in a
  stone-adjacent script, so it is the maintainer's call.
- **Fix the 26 stale task paths in code KDoc** (the docs-lint whole-repo run lists them: ten `docs/tasks/<name>.md`
  paths of tasks since archived, each with exactly one archived record, so the sweep is mechanical). A KDoc-only sweep, best as its own small change with the grep in the report.
- **The two task-place findings**: KLANG-00127 (sprudel test coverage and review) ("IN PROGRESS, user-paced",
  2026-06-30) and KLANG-00145 (tutorial curriculum) ("ACTIVE", 2026-08-31) sit outside `in-progress/`. Either the
  status line is stale or the file belongs in `in-progress/`; the maintainer knows which.
- **review-pack `--paths`**: limit the diff to named paths, for a review in the shared main checkout where other
  sessions have uncommitted work (the 2026-09-19 escape). Five lines; generic.
- **review-pack `--scan <regex>`**: the public-voice hype grep and the dash scan are the same mechanism (added lines
  matching a pattern); one option would retire the hand grep in `public-voice`.
