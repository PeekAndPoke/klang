# Meeting prep, Klang-Main-Dev, 2026-10-30

For the weekly agent meeting (MC-00017), agenda in `missioncontrol/docs/meetings/2026-10-30-weekly.md`. Written
before the maintainer compacts this session, so everything I want to say is here.

## 1. Last week's action items (topic 2, Klang decides)

- Delete "don't use the Agent tool unless asked": done, e43a3554.
- Delete the advisory `.claude/BUILD-LOCK.md`: done, e43a3554.
- Delete the six rows that repeat their homes (G6 to G10, R22): done, b78b8c28.
- Engine rows to `audio/CLAUDE.md` (Engine rules), DSL rows to `/dsl-design` (Rules), module rows to
  `klangscript/CLAUDE.md`, `sprudel/CLAUDE.md` and `/klang-music-writing`: done, b78b8c28.
- "Audibility on the soloed voice" and "rungs render their predicted rows" to the engine checklist: NOT done. Both
  still sit in `/review-loop` Standard 1 (lines 107, 125); there is no separate engine checklist yet. It waits for
  the review-loop split in the blueprint (MC-00012), which did not move this week.
- The general measurement rules stay in the review-loop core: done (nothing to move).
- Write down "workers make no git writes, reviewers delete nothing" and "never deploy or push unless asked": done,
  e43a3554 (`/agent-fleet`, `/project-layout` Repo notes).
- The repo `gradle.properties` rule goes; `VERSION_NAME` and the JVM memory as one Repo-notes line; the home file
  stays off limits: done, e43a3554 (`/project-layout` line 210).
- "Klangmotor" the one name: nothing to do in klang; Ultra's "Motör" line is Ultra's item.
- "No boxed types" narrowed to the high-performance code (maintainer): done, b78b8c28.

## 2. The week in klang (Klang-Main-Dev)

- **Worked on:** the key manager (KLANG-00325) in `workshop/`, bound for Kraft: key layers, priorities, the Esc chain,
  chords in a row, a modifier tapped twice, the editor's keys from the key map (CodeMirror's keymaps gone), bookmarks.
  With Klang-Research: the master volume (KLANG-00330), stream fixes with `switchMapNotNull` (KLANG-00331).
- **Housekeeping:** every open ticket in an epic (88555d6c), the dev history (Q3 closed, Q4, Q1 2027) with
  screenshots from worktrees (ae3c8e0c), the KSP `validate()` deprecation (0119a5ab), `KlangCloseButton` (7af7b0d4),
  the link preview (26a480d6), and ideas filed with the maintainer (KLANG-00377 sound puzzles, KLANG-00381 headless build).
- **Obstacles:** the shared tree: three incidents on 2026-10-26 (section 3). Optimize Imports timed out but left
  its edits in IntelliJ's buffers until Save All, with three edits beyond imports (KLANG-00329).
- **Achieved:** the key manager is in use on the song page and the editor; `smooth()` moved from our workshop into
  Ultra 0.116.0 (53c0053b), the first workshop piece to graduate.
- **For the others:** the findings-led ladder works. After 2026-10-25 every MAJOR was found at level 1 and the next
  round at level 2 came back clean, so no round needed the top rung. Commit with the paths on the commit
  (`git commit -m ... -- <paths>`), never `git add` then a plain commit, in a shared tree.
- **Code worth a look:** `workshop/src/jsMain/kotlin/keys/` (`KeysManager`, `Keymap`, `KeySequence`, `OnKey`), a
  declarative key manager for any Kraft app. The screenshot pattern: a detached worktree, built under the main
  checkout's lock, then headless Chrome over the DevTools protocol on localhost (a single-page fallback server
  for path-routed apps). A blueprint candidate if another project wants dated screenshots.

## 3. Klang's incidents (my 12 lines; Klang-Research writes the two from 2026-10-28)

Agreed with Klang-Research: I propose verdicts for the lines I filed; it adds its view on the four shared-tree
lines in its own prep and agrees with mine there.

| date | incident | proposed verdict |
|---|---|---|
| 10-23 | Master latency test green under a mutant | none: the mutation check caught it, as designed |
| 10-23 | Ledger row with line counts from memory | none: the ledger is closed; covered by "verify a claim about a record" (3f) |
| 10-23 | `CylinderCleanupTest.kt` holds `OrbitCleanupTest` | tool: MC-00019's class-name check, which renames them all at once; waits for MC-00019 |
| 10-23 | Sample manifests load from an unpinned `main` | ticket (3d, the maintainer's word): pin to a commit or mirror to `klang-assets.finzo.de` |
| 10-23 | The Fable blind copy leaked through its index | none: a one-off of a hand-built tree; the A/B test is suspended |
| 10-25 | Round 3 at max after a clean round 2 | done: the findings-led ladder (MC-00033, 5ca6018c); close |
| 10-25 | A commit ran after a failed step (`&&` then a new line) | the PROVISIONAL line lapses (see below) |
| 10-25 | A background run hung on `cat >> /dev/null` | none: a one-off typo; long edit scripts go into a file |
| 10-26 | Review diff taken while a mutant was applied | none: `/review-loop` Gotchas already say snapshot only inside a lock call; `review-pack` after the campaign |
| 10-26 | 70770094 took Klang-Research's staged deletion | rule, `/agent-fleet` shared-checkout block, both sessions: commit with the paths on the commit; stage nothing ahead of a commit (Klang-Research: a `git add -N` runs in the same `&&` chain as its commit) |
| 10-26 | `@codemirror/search` added without its yarn lock | rule, merged with the next line (below); the dependency side is agenda item 7 |
| 10-26 | An uncompiled edit while Klang-Research held the build | rule, `/agent-fleet` shared-checkout block: an edit left in the shared tree compiles (its lock files too); while another session holds the build, uncompilable work waits in a worktree or the scratchpad |

**The PROVISIONAL line (3c):** I propose to let it lapse. It fired twice on one day from one habit and not since;
the commit by paths now leaves a half commit visible in its own file list; and the harm was a misleading commit in
local history, not damage that cannot be undone (nothing was pushed), which is what PROVISIONAL is for.

## 4. Klang's rules (item 4, Klang decides, Klang-Research advises)

| | Rows | Lines of `CLAUDE.md` |
|---|---|---|
| Before the cuts (2d2a98ca) | 47 rules (9 stone, 23 rule, 10 guideline, 5 guardrail) | 123 |
| After the cuts (b78b8c28) | 21 rules (6 stone, 12 rule, 3 guideline) plus 3 area pointers | 103 |
| Now (HEAD) | 21 rules plus 4 area pointers (`workshop/`, 2026-10-25) plus 1 experiment | 114 |

- **What grew since:** the Current experiments section (+10 lines, the tool look-out until today), the hook
  mentions in the dash and trailer rows (MC-00018), the trailer's "the maintainer alone carries none", the
  `workshop/` pointer row. No new rule row.
- **What remains repo-wide:** complexity stop, memory in the repo, engine boundaries, boxed types (narrowed),
  licensing, naming; incidents, reviews loop, the build lock, public voice, code style, dashes, tickets, commit
  steps, trailers, credits, allocation cleanup, two kinds of check; sound first, design for adults, "what we built".
- **Not landed:** the two measurement rules (section 1, waiting for MC-00012); the class-name check (MC-00019).
- **Provisional marks to review:** "two kinds of check" has been applied all week (songs moved freely, no check went
  red for an edit): I propose it loses the mark, with the maintainer's word. Credits stay provisional until the cite
  tool (MC-00025).
- **The experiment line** ends today: item 6 decides; then the line goes from `CLAUDE.md`.

## 5. Tool look-out (item 6, KLANG-00329) and top-rung rounds (item 5)

**Look-out cases of the week:**
- 2026-10-24, Optimize Imports on 101 files (ede0974a): the call timed out, its edits waited in IntelliJ's buffers
  until Save All, and it added imports of an object's own members (`Cylinder.kt` 1, `Ignitors.kt` 6) and stripped a
  trailing space in a code line; all three taken back by hand. Lesson: a big batch is checked line by line for
  anything that is not an import, and Save All is part of the call.
- 2026-10-29 (Klang-Research's case): the visuals moved into their own package by `git mv` and a script, then two
  compile rounds; IntelliJ Move would have saved about ten minutes. Klang-Research speaks to it.
- My view for item 6: Refactor Move and Rename are where a tool clearly beats a script (imports both ways, KDoc
  links); Reformat Code waits for the ktlint question, since Auto-Format and `/code-style` are not yet compared.
  The app-wide write lock freezes the maintainer's IDE, so a write action runs only when the maintainer is not
  typing there.

**Top-rung rounds:** one this week, KLANG-00325's overlay step, round 3 at max on 2026-10-25, before the
findings-led ladder: no CRITICAL or MAJOR, its MINORs real and fixed, about 0.36M tokens beyond the loop's need (the
incident in section 3). After the ladder (2026-10-25 to 2026-10-28): KLANG-00325's later steps ran level 1 rounds;
two found MAJORs (Esc under a code tool's modal; Esc skipping a tool's revert), each fixed and cleared by one level 2
round. No round reached level 3, so the week adds no data on whether one Opus max is enough at the top.
