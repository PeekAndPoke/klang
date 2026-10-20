# Next steps (2026-10-20, after the pause)

The pause after v0.7.2 ended the same day for one work package (maintainer): both parked branches on `main`, then
the ticket numbers. This note is the queue, in order. The note of the pause itself is in git history.

## Where things stand

- `main` holds everything: v0.7.2 (tagged 2026-10-20), Ultra 0.114.0, the console tools (`c90a19f0`) and engine
  alloc batch 2 (`1ade10ef`), both merged before their review rounds (maintainer, 2026-10-20). The full check of
  `1ade10ef`: tests GREEN (18,296), corpus 3 of 3 frozen identical, allocation 80 of 80. No branch but `main`, no
  worktree.
- Every task and plan is a ticket since 2026-10-20: KLANG-00001 to KLANG-00320, `/project-layout`. The open
  tickets' status words were read from their own status text; the maintainer may correct any with `ticket move`.
- Work runs sequentially on the main checkout; a full check may run in `klang-worktrees/measure`
  (`/project-layout`, Repo notes).

## Queue (Klang-Main-Dev)

1. **Console tools: done 2026-10-20.** Review round 2 (reviewer-high): 1 MAJOR (docs-lint read the git-ignored
   `tmp/`) and 2 MINORs, fixed in 8d9fa057; every round-1 fix holds. Two rounds without a clean one: the safety
   valve, a round 3 or not is the maintainer's call. The approved condense is applied (the `CLAUDE.md` tool block
   with `ticket`, shorter pointers elsewhere); the skill `mission-control-link` is in.
2. **Engine alloc batch 2: done 2026-10-21.** Review round 1 clean (code and audio), the MINOR batch applied, the
   mutation campaign 7 of 7 KILLED (KLANG-00290, item 45's last paragraph).
3. **Master follow-up 8, option (a)**, decided 2026-10-19: the offline render counts the master chain's latency
   (KLANG-00137 (master dsl followups) §8). It moves two corpus rows (Der Schmetterling, Kokon), accepted with the
   change.
4. Then the order in KLANG-00290, its first paragraph. 10c and 10d wait for the FM round.
5. **Left of the docs-lint findings:** the ticket sweep replaced the stale task paths, and the two task-place
   findings now carry PAUSED in `docs/tasks/` (KLANG-00127, KLANG-00145). Left: the `_priorities.md` tremolo entry
   (tools review round 1, MINOR 17).
6. **Mission Control's review-loop RFC**: one answer to its draft
   (`/opt/dev/peekandpoke/missioncontrol/blueprints/skills/review-loop/SKILL.md`). The tier names are agreed
   (strict / standard / light). Nothing is adopted until the maintainer decides. Our two Kraft pitfalls come back
   with Mission Control's merged `kraft-knowhow`.
7. **The lexer accepts an unclosed block comment** ([`klangscript/MEMORY.md`](../../../klangscript/MEMORY.md), Open
   threads): a fix to make.

## Katalyst stream (Klang-Research)

Unchanged since 2026-10-13: its task is closed and archived, and what is left waits for a song that asks for it
(KLANG-00303 (katalyst knob followups)).

## Open with the maintainer

- An FM round together: KLANG-00305 (fm revisit), now with the open sites of engine follow-ups 10c and 10d.
- Completion, left open: a local that shadows a library name shows two rows; a local bound to an arrow shows no type;
  after a named argument the list still shows locals and library names; a new line inside a multi-line call offers
  no names until a comma. Records: KLANG-00319 (editor local symbol completion) and KLANG-00130 (klangscript named
  args docs polish), "Open, for the maintainer".
- sprudel's own typed Kotlin door (its doors take `PatternLike = Any`): needs its own design and a home
  (KLANG-00314 (kotlin typed doors), "Left after the rollout").
- The stdlib's `hashCode(dynamic)` closure (every Kotlin/JS `HashMap` operation allocates on V8) is noted for an
  upstream report, not filed (KLANG-00290, item 2).
- `docs/tasks/in-progress/codemirror-bump-checklist.md` (a hand-test checklist for the Ultra 0.110 bump, 2026-10-18):
  still wanted, or done?
